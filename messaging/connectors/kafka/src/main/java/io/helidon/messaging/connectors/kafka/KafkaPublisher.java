/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.reactive.EmittingPublisher;
import io.helidon.config.Config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * This is an implementation of {@link org.reactivestreams.Publisher} that read messages from
 * Kafka and push them downstream to one subscriber.
 * Configurable by Helidon {@link io.helidon.config.Config Config},
 *
 * @param <K> Key type
 * @param <V> Value type
 * @see io.helidon.config.Config
 */
public class KafkaPublisher<K, V> implements Publisher<KafkaMessage<K, V>> {

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
    private final AtomicLong requests = new AtomicLong();
    private final EmittingPublisher<KafkaMessage<K, V>> emitter;
    private final List<String> topics;
    private final long periodExecutions;
    private final long pollTimeout;
    private final boolean autoCommit;
    private final long ackTimeout;
    private final int limitNoAck;
    private final Supplier<Consumer<K, V>> consumerSupplier;

    private Consumer<K, V> kafkaConsumer;
    private boolean stopped;

    private KafkaPublisher(ScheduledExecutorService scheduler, Supplier<Consumer<K, V>> consumerSupplier,
            List<String> topics, long pollTimeout, long periodExecutions, boolean autoCommit,
            long ackTimeout, int limitNoAck) {
        this.scheduler = scheduler;
        this.topics = topics;
        this.periodExecutions = periodExecutions;
        this.pollTimeout = pollTimeout;
        this.autoCommit = autoCommit;
        this.ackTimeout = ackTimeout;
        this.limitNoAck = limitNoAck;
        this.consumerSupplier = consumerSupplier;
        emitter = EmittingPublisher.create();
        emitter.onRequest((n, t) -> requests.updateAndGet(r -> Long.MAX_VALUE - r > n ? n + r : Long.MAX_VALUE));
    }

    /**
     * Starts to consume events from Kafka to send them downstream till
     * {@link KafkaPublisher#stop()} is invoked.
     * A new KafkaConsumer will be instanced if it was not provided before.
     * This execution runs in one thread that is triggered by the scheduler.
     */
    private void start() {
        LOGGER.fine(() -> "KafkaPublisher starts to consume from Kafka");
        try {
            kafkaConsumer = consumerSupplier.get();
            kafkaConsumer.subscribe(topics, partitionsAssignedLatch);
            // This thread reads from Kafka topics and push in kafkaBufferedEvents
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    // Need to lock to avoid onClose() is executed meanwhile task is running
                    taskLock.lock();
                    if (!scheduler.isShutdown() && !(emitter.isCompleted() || emitter.isCancelled())) {
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
                                    runInNewContext(() ->  emitter.emit(kafkaMessage));
                                }
                            }
                        } else {
                            throw new IllegalStateException(
                                    String.format("Current pending %s acks has overflown the limit of %s ",
                                            currentNoAck, limitNoAck));
                        }
                    }
                    cleanResourcesIfTerminated(emitter.isCompleted() || emitter.isCancelled());
                    if (!stopped && !autoCommit) {
                        processACK();
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "KafkaPublisher failed", e);
                    emitter.fail(e);
                } finally {
                    taskLock.unlock();
                }
            }, 0, periodExecutions, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            partitionsAssignedLatch.countDown();
            // The failure must be emitted in a different thread or the rest of connectors
            // will fail because of MessagingException. It is preferable to notify error to the subscriber.
            scheduler.execute(() -> emitter.fail(e));
        }
    }

    private int currentNoAck() {
        return pendingCommits.values().stream().map(list -> list.size()).reduce((a, b) -> a + b).orElse(0);
    }

    /**
     * Process the ACKs.
     * This will search ACK events and it will commit them to Kafka.
     * Those events that are committed will complete KafkaMessage#ack().
     */
    private void processACK() {
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
            LOGGER.fine(() -> String.format("Offsets %s", offsets));
            try {
                kafkaConsumer.commitSync(offsets);
                messagesToCommit.stream().forEach(message -> message.kafkaCommit().complete(null));
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Unable to commit in Kafka " + offsets, e);
                messagesToCommit.stream().forEach(message -> message.kafkaCommit().completeExceptionally(e));
            }
        }
    }

    /**
     * Closes the opened resources to Kafka and completes exceptionally the pending {@link KafkaMessage#ack()}.
     *
     * It must be invoked after {@link ScheduledExecutorService} is shutdown.
     */
    public void stop() {
        if (kafkaConsumer != null) {
            // Stops pooling
            kafkaConsumer.wakeup();
            // Wait that current task finishes in case it is still running
            try {
                taskLock.lock();
                cleanResourcesIfTerminated(true);
                emitter.complete();
            } catch (RuntimeException e) {
                emitter.fail(e);
            } finally {
                taskLock.unlock();
            }
            LOGGER.fine(() -> "Closed");
        }
    }

    /**
     * Clean the resources when isTerminated = true only if they were not cleaned
     * before.
     *
     * @param isTerminated specify the publisher is terminated or not
     */
    private void cleanResourcesIfTerminated(boolean isTerminated) {
        if (!stopped && isTerminated) {
            stopped = true;
            LOGGER.fine(() -> "Pending ACKs: " + pendingCommits.size());
            // Terminate waiting ACKs
            pendingCommits.values().stream().flatMap(List::stream)
            .forEach(message -> message.kafkaCommit()
                    .completeExceptionally(new TimeoutException("Aborted because KafkaPublisher is terminated")));
            kafkaConsumer.close();
        }
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
        emitter.subscribe(FlowAdapters.toFlowSubscriber(subscriber));
        start();
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

    /**
     * A builder for KafkaPublisher.
     *
     * @param <K> Key type
     * @param <V> Value type
     * @return builder to create a new instance
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    /**
     * Load this builder from a configuration.
     *
     * @param <K> Key type
     * @param <V> Value type
     * @param config configuration to load from
     * @return updated builder instance
     */
    public static <K, V> KafkaPublisher<K, V> create(Config config) {
        return (KafkaPublisher<K, V>) builder().config(config).build();
    }

    /**
     * Fluent API builder for {@link KafkaPublisher}.
     * @param <K> Key type
     * @param <V> Value type
     */
    public static final class Builder<K, V> implements io.helidon.common.Builder<KafkaPublisher<K, V>> {

        private long pollTimeout = 50L;
        private long periodExecutions = 100L;
        private Boolean autoCommit;
        private long ackTimeout = Long.MAX_VALUE;
        private int limitNoAck = Integer.MAX_VALUE;
        private List<String> topics;
        private ScheduledExecutorService scheduler;
        private Supplier<Consumer<K, V>> consumerSupplier;

        private Builder() {
        }

        /**
         * Load this builder from a configuration.
         *
         * @param config configuration to load from
         * @return updated builder instance
         */
        public Builder<K, V> config(Config config) {
            KafkaConfig kafkaConfig = KafkaConfig.create(config);
            consumerSupplier(() -> new KafkaConsumer<>(kafkaConfig.asMap()));
            topics(kafkaConfig.topics());
            config.get(POLL_TIMEOUT).asLong().ifPresent(this::pollTimeout);
            config.get(PERIOD_EXECUTIONS).asLong().ifPresent(this::periodExecutions);
            config.get(ENABLE_AUTOCOMMIT).asBoolean().ifPresent(this::autoCommit);
            config.get(ACK_TIMEOUT).asLong().ifPresent(this::ackTimeout);
            config.get(LIMIT_NO_ACK).asInt().ifPresent(this::limitNoAck);
            return this;
        }

        /**
         * Defines how to instantiate the KafkaConsumer. It will be invoked
         * in {@link KafkaPublisher#subscribe(Subscriber)}
         *
         * This is a mandatory parameter.
         *
         * @param consumerSupplier
         * @return updated builder instance
         */
        public Builder<K, V> consumerSupplier(Supplier<Consumer<K, V>> consumerSupplier) {
            this.consumerSupplier = consumerSupplier;
            return this;
        }

        /**
         * The list of topics to subscribe to.
         *
         * This is a mandatory parameter.
         *
         * @param topics
         * @return updated builder instance
         */
        public Builder<K, V> topics(List<String> topics) {
            this.topics = topics;
            return this;
        }

        /**
         * Specify a scheduler that will read ad process messages coming from Kafka.
         * Is it intended that this scheduler is reused for other tasks.
         *
         * This is a mandatory parameter.
         *
         * @param scheduler
         * @return updated builder instance
         */
        public Builder<K, V> scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Specifies the period in milliseconds between successive scheduler executions.
         * The default value is 100 milliseconds.
         *
         * @param periodExecutions
         * @return updated builder instance
         */
        public Builder<K, V> periodExecutions(long periodExecutions) {
            this.periodExecutions = periodExecutions;
            return this;
        }

        /**
         * Specifies maximum time in milliseconds to block polling messages from Kafka.
         * The default value is 50 milliseconds.
         *
         * @param pollTimeout
         * @return updated builder instance
         */
        public Builder<K, V> pollTimeout(long pollTimeout) {
            this.pollTimeout = pollTimeout;
            return this;
        }

        /**
         * This flag defines the strategy of committing messages to Kafka.
         * When true, the messages are committed in the moment they are polled from Kafka.
         * When false, the messages are committed when {@link KafkaMessage#ack()} is invoked.
         *
         * This value is mandatory to be specified and it must be consistent with the value enable.auto.commit
         * in Kafka properties. Failing to do this will result the next scenarios:
         * - For autoCommit = true and enable.auto.commit = false, messages will never be committed in Kafka.
         * - For autoCommit = false and enable.auto.commit = true, all messages will be committed and
         * {@link KafkaMessage#ack()} will have no effect.
         *
         * @param autoCommit
         * @return updated builder instance
         */
        public Builder<K, V> autoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        /**
         * This value applies only when autoCommit is set to false.
         * It defines the maximum time in milliseconds that {@link KafkaMessage#ack()} will be waiting
         * for the commit in Kafka.
         *
         * The default value is Long.MAX_VALUE
         *
         * @param ackTimeout
         * @return updated builder instance
         */
        public Builder<K, V> ackTimeout(long ackTimeout) {
            this.ackTimeout = ackTimeout;
            return this;
        }

        /**
         * This value applies only when autoCommit is set to false.
         * It specifies the limit of messages waiting to be committed in Kafka.
         * If this value is overflown, the KafkaPublisher will notify a failure.
         *
         * The intention of this value is to fail gracefully when there are many pending commits,
         * instead of failing with OutOfMemoryError.
         *
         * @param limitNoAck
         * @return updated builder instance
         */
        public Builder<K, V> limitNoAck(int limitNoAck) {
            this.limitNoAck = limitNoAck;
            return this;
        }

        @Override
        public KafkaPublisher<K, V> build() {
            if (Objects.isNull(topics) || topics.isEmpty()) {
                throw new IllegalArgumentException("The topic is a required value");
            }
            if (Objects.isNull(autoCommit)) {
                String message =
                    String.format("The autoCommit is a required value and be equals to KafkaProperty %s", ENABLE_AUTOCOMMIT);
                throw new IllegalArgumentException(message);
            }
            if (Objects.isNull(scheduler)) {
                throw new IllegalArgumentException("The scheduler is a required value");
            }
            if (Objects.isNull(consumerSupplier)) {
                throw new IllegalArgumentException("The kafkaConsumerSupplier is a required value");
            }
            KafkaPublisher<K, V> publisher = new KafkaPublisher<>(scheduler, consumerSupplier, topics,
                    pollTimeout, periodExecutions, autoCommit, ackTimeout, limitNoAck);
            return publisher;
        }
    }

}
