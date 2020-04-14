/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.messaging.connectors.kafka;

import java.io.Closeable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * This is an implementation of {@link org.reactivestreams.Publisher} that read events from
 * Kafka and push them downstream to one subscriber.
 * Configurable by Helidon {@link io.helidon.config.Config Config},
 *
 * @param <K> Key type
 * @param <V> Value type
 * @see io.helidon.config.Config
 */
class KafkaPublisher<K, V> implements Publisher<KafkaMessage<K, V>>, Closeable {

    private static final Logger LOGGER = Logger.getLogger(KafkaPublisher.class.getName());
    private static final String POLL_TIMEOUT = "poll.timeout";
    private static final String PERIOD_EXECUTIONS = "period.executions";
    private static final String ENABLE_AUTOCOMMIT = "enable.auto.commit";
    private static final String ACK_TIMEOUT = "ack.timeout.millis";
    private static final String LIMIT_NO_ACK = "limit.no.ack";
    private final Lock taskLock = new ReentrantLock();
    private final Queue<ConsumerRecord<K, V>> backPressureBuffer = new LinkedList<>();
    private final Map<TopicPartition, List<KafkaMessage<K, V>>> pendingCommits = new HashMap<>();
    private final PartitionsAssignedLatch partitionsAssignedLatch = new PartitionsAssignedLatch();
    private final ScheduledExecutorService scheduler;
    private final Consumer<K, V> kafkaConsumer;
    private final AtomicLong requests = new AtomicLong();
    private final EmittingPublisher<KafkaMessage<K, V>> emiter =
            new EmittingPublisher<>(requested -> requests.addAndGet(requested));
    private final List<String> topics;
    private final long periodExecutions;
    private final long pollTimeout;
    private final boolean autoCommit;
    private final long ackTimeout;
    private final int limitNoAck;

    private KafkaPublisher(ScheduledExecutorService scheduler, Consumer<K, V> kafkaConsumer,
            List<String> topics, long pollTimeout, long periodExecutions,
            boolean autoCommit, long ackTimeout, int limitNoAck) {
        this.scheduler = scheduler;
        this.kafkaConsumer = kafkaConsumer;
        this.topics = topics;
        this.periodExecutions = periodExecutions;
        this.pollTimeout = pollTimeout;
        this.autoCommit = autoCommit;
        this.ackTimeout = ackTimeout;
        this.limitNoAck = limitNoAck;
    }

    /**
     * Starts to consume events from Kafka to send them downstream till
     * {@link KafkaPublisher#close()} is invoked.
     * This execution runs in one thread that is triggered by the scheduler.
     */
    private void execute() {
        kafkaConsumer.subscribe(topics, partitionsAssignedLatch);
        // This thread reads from Kafka topics and push in kafkaBufferedEvents
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Need to lock to avoid onClose() is executed meanwhile task is running
                taskLock.lock();
                if (!scheduler.isShutdown() && !emiter.isTerminated()) {
                    int currentNoAck = currentNoAck();
                    if (currentNoAck < limitNoAck) {
                        if (backPressureBuffer.isEmpty()) {
                            try {
                                kafkaConsumer.poll(Duration.ofMillis(pollTimeout)).forEach(backPressureBuffer::add);
                            } catch (WakeupException e) {
                                LOGGER.fine(() -> "It was requested to stop polling from channel");
                            }
                        } else {
                            long totalToEmit = requests.get();
                            // Avoid index out bound exceptions
                            long eventsToEmit = Math.min(totalToEmit, backPressureBuffer.size());
                            for (long i = 0; i < eventsToEmit; i++) {
                                ConsumerRecord<K, V> cr = backPressureBuffer.poll();
                                CompletableFuture<Void> kafkaCommit = new CompletableFuture<>();
                                KafkaMessage<K, V> kafkaMessage = new KafkaMessage<>(cr, kafkaCommit, ackTimeout);
                                if (!autoCommit) {
                                    TopicPartition key = new TopicPartition(kafkaMessage.getPayload().topic(),
                                            kafkaMessage.getPayload().partition());
                                    pendingCommits.computeIfAbsent(key, k -> new LinkedList<>()).add(kafkaMessage);
                                } else {
                                    kafkaCommit.complete(null);
                                }
                                requests.decrementAndGet();
                                runInNewContext(() ->  emiter.emit(kafkaMessage));
                            }
                        }
                    } else {
                        throw new IllegalStateException(
                                String.format("Current pending %s acks has overflown the limit of %s ",
                                        currentNoAck, limitNoAck));
                    }
                }
                // Commit ACKs
                processACK();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "KafkaPublisher failed", e);
                emiter.fail(e);
            } finally {
                taskLock.unlock();
            }
        }, 0, periodExecutions, TimeUnit.MILLISECONDS);
    }

    private int currentNoAck() {
        return pendingCommits.values().stream().map(list -> list.size()).reduce((a, b) -> a + b).orElse(0);
    }

    /**
     * Process the ACKs only if enable.auto.commit is false.
     * This will search ACK events and it will commit them to Kafka.
     * Those events that are committed will complete KafkaMessage#ack().
     */
    private void processACK() {
        if (!autoCommit) {
            Map<TopicPartition, OffsetAndMetadata> offsets = new LinkedHashMap<>();
            List<KafkaMessage<K, V>> messagesToCommit = new LinkedList<>();
            // Commit highest offset + 1 of each partition that was ACK, and remove from pending
            for (Entry<TopicPartition, List<KafkaMessage<K, V>>> entry : pendingCommits.entrySet()) {
                // No need to sort it, offsets are consumed in order
                List<KafkaMessage<K, V>> byPartition = entry.getValue();
                Iterator<KafkaMessage<K, V>> iterator = byPartition.iterator();
                KafkaMessage<K, V> highest = null;
                while (iterator.hasNext()) {
                    KafkaMessage<K, V> element = iterator.next();
                    if (element.isAck()) {
                        messagesToCommit.add(element);
                        highest = element;
                        iterator.remove();
                    } else {
                        break;
                    }
                }
                if (highest != null) {
                    OffsetAndMetadata offset = new OffsetAndMetadata(highest.getPayload().offset() + 1);
                    LOGGER.fine(() -> String.format("Will commit %s %s", entry.getKey(), offset));
                    offsets.put(entry.getKey(), offset);
                }
            }
            if (!messagesToCommit.isEmpty()) {
                Optional<RuntimeException> exception = commitInKafka(offsets);
                messagesToCommit.stream().forEach(message -> {
                    exception.ifPresentOrElse(
                            ex -> message.kafkaCommit().completeExceptionally(ex),
                            () -> message.kafkaCommit().complete(null));
                });
            }
        }
    }

    private Optional<RuntimeException> commitInKafka(Map<TopicPartition, OffsetAndMetadata> offsets) {
        LOGGER.fine(() -> String.format("%s events to commit: ", offsets.size()));
        LOGGER.fine(() -> String.format("%s", offsets));
        try {
            kafkaConsumer.commitSync(offsets);
            LOGGER.fine(() -> "The commit was successful");
            return Optional.empty();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Unable to commit in Kafka " + offsets, e);
            return Optional.of(e);
        }
    }

    /**
     * Closes the connections to Kafka and stops to process new events.
     */
    @Override
    public void close() {
        // Stops pooling
        kafkaConsumer.wakeup();
        // Wait that current task finishes in case it is still running
        try {
            taskLock.lock();
            LOGGER.fine(() -> "Pending ACKs: " + pendingCommits.size());
            // Terminate waiting ACKs
            pendingCommits.values().stream().flatMap(List::stream)
            .forEach(message ->
            message.kafkaCommit().completeExceptionally(new TimeoutException("Aborted because KafkaPublisher is shutting down")));
            kafkaConsumer.close();
            emiter.complete();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Error closing KafkaPublisher", e);
            emiter.fail(e);
        } finally {
            taskLock.unlock();
        }
        LOGGER.fine(() -> "Closed");
    }

    //Move to messaging incoming connector
    protected void runInNewContext(Runnable runnable) {
        Context.Builder contextBuilder = Context.builder()
                .id(String.format("kafka-message-%s:", UUID.randomUUID().toString()));
        Contexts.context().ifPresent(contextBuilder::parent);
        Contexts.runInContext(contextBuilder.build(), runnable);
    }

    @Override
    public void subscribe(Subscriber<? super KafkaMessage<K, V>> subscriber) {
        emiter.subscribe(subscriber);
    }

    /**
     * Blocks current thread until partitions are assigned, since when is consumer effectively ready to receive.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @throws java.lang.InterruptedException        if the current thread is interrupted while waiting
     * @throws java.util.concurrent.TimeoutException if the timeout is reached
     */
    void waitForPartitionAssigment(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (!partitionsAssignedLatch.await(timeout, unit)) {
            throw new TimeoutException("Timeout for subscription reached");
        }
    }

    static <K, V> KafkaPublisherBuilder<K, V> builder(ScheduledExecutorService scheduler, Consumer<K, V> kafkaConsumer,
            List<String> topics) {
        return new KafkaPublisherBuilder<>(scheduler, kafkaConsumer, topics);
    }

    /**
     * Fluent API builder for {@link KafkaPublisher}.
     */
    static final class KafkaPublisherBuilder<K, V> implements io.helidon.common.Builder<KafkaPublisher<K, V>> {

        private long pollTimeout = 50L;
        private long periodExecutions = 100L;
        private boolean autoCommit = true;
        private long ackTimeout = Long.MAX_VALUE;
        private int limitNoAck = Integer.MAX_VALUE;
        private final List<String> topics;
        private final ScheduledExecutorService scheduler;
        private final Consumer<K, V> kafkaConsumer;

        private KafkaPublisherBuilder(ScheduledExecutorService scheduler, Consumer<K, V> kafkaConsumer,
                List<String> topics) {
            this.scheduler = scheduler;
            this.kafkaConsumer = kafkaConsumer;
            this.topics = topics;
        }

        KafkaPublisherBuilder<K, V> config(Config config) {
            config.get(POLL_TIMEOUT).asLong().ifPresent(this::pollTimeout);
            config.get(PERIOD_EXECUTIONS).asLong().ifPresent(this::periodExecutions);
            config.get(ENABLE_AUTOCOMMIT).asBoolean().ifPresent(this::autoCommit);
            config.get(ACK_TIMEOUT).asLong().ifPresent(this::ackTimeout);
            config.get(LIMIT_NO_ACK).asInt().ifPresent(this::limitNoAck);
            return this;
        }

        KafkaPublisherBuilder<K, V> periodExecutions(long periodExecutions) {
            this.periodExecutions = periodExecutions;
            return this;
        }

        KafkaPublisherBuilder<K, V> pollTimeout(long pollTimeout) {
            this.pollTimeout = pollTimeout;
            return this;
        }

        KafkaPublisherBuilder<K, V> autoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        KafkaPublisherBuilder<K, V> ackTimeout(long ackTimeout) {
            this.ackTimeout = ackTimeout;
            return this;
        }

        KafkaPublisherBuilder<K, V> limitNoAck(int limitNoAck) {
            this.limitNoAck = limitNoAck;
            return this;
        }

        @Override
        public KafkaPublisher<K, V> build() {
            if (topics.isEmpty()) {
                throw new IllegalArgumentException("The topic is a required value");
            }
            KafkaPublisher<K, V> publisher = new KafkaPublisher<>(scheduler, kafkaConsumer, topics,
                    pollTimeout, periodExecutions, autoCommit, ackTimeout, limitNoAck);
            publisher.execute();
            return publisher;
        }
    }

}
