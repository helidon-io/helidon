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

package io.helidon.microprofile.connectors.kafka;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
    private final Lock taskLock = new ReentrantLock();
    private final PartitionsAssignedLatch partitionsAssignedLatch = new PartitionsAssignedLatch();
    private final Queue<ConsumerRecord<K, V>> backPressureBuffer = new LinkedList<>();
    private final BlockingQueue<Entry<TopicPartition, OffsetAndMetadata>> pendingCommits =
            new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler;
    private final Consumer<K, V> kafkaConsumer;
    private final AtomicLong requests = new AtomicLong();
    private final EmittingPublisher<KafkaMessage<K, V>> emiter =
            new EmittingPublisher<>(requested -> requests.addAndGet(requested));
    private final List<String> topics;
    private final long periodExecutions;
    private final long pollTimeout;

    private KafkaPublisher(ScheduledExecutorService scheduler, Consumer<K, V> kafkaConsumer,
            List<String> topics, long pollTimeout, long periodExecutions) {
        this.scheduler = scheduler;
        this.kafkaConsumer = kafkaConsumer;
        this.topics = topics;
        this.periodExecutions = periodExecutions;
        this.pollTimeout = pollTimeout;
    }

    /**
     * Starts to consume events from Kafka to send them downstream till
     * {@link io.helidon.microprofile.connectors.kafka.KafkaPublisher#close()} is invoked.
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
                    if (backPressureBuffer.isEmpty()) {
                        try {
                            kafkaConsumer.poll(Duration.ofMillis(pollTimeout)).forEach(backPressureBuffer::add);
                        } catch (WakeupException e) {
                            LOGGER.fine("It was requested to stop polling from channel");
                        }
                    } else {
                        long totalToEmit = requests.get();
                        // Avoid index out bound exceptions
                        long eventsToEmit = Math.min(totalToEmit, backPressureBuffer.size());
                        for (long i = 0; i < eventsToEmit; i++) {
                            ConsumerRecord<K, V> cr = backPressureBuffer.poll();
                            // Unfortunately KafkaConsumer is not thread safe, so the commit must happen in this thread.
                            // KafkaMessage will notify ACK to this thread via Callback
                            KafkaMessage<K, V> kafkaMessage = new KafkaMessage<>(cr, entry -> pendingCommits.add(entry));
                            runInNewContext(() -> emiter.emit(kafkaMessage));
                            requests.decrementAndGet();
                        }
                        if (eventsToEmit != 0) {
                            LOGGER.fine(String.format("Emitted %s of %s. Buffer size: %s", eventsToEmit,
                                    totalToEmit, backPressureBuffer.size()));
                        }
                    }
                }
                // Commit ACKs
                Map<TopicPartition, OffsetAndMetadata> offsets = new LinkedHashMap<>();
                Entry<TopicPartition, OffsetAndMetadata> entry;
                while ((entry = pendingCommits.poll()) != null) {
                    offsets.put(entry.getKey(), entry.getValue());
                }
                kafkaConsumer.commitSync(offsets);
                if (!offsets.isEmpty()) {
                    LOGGER.fine(String.format("%s events were ACK: ", offsets.size()));
                }
            } catch (Exception e) {
                emiter.fail(e);
            } finally {
                taskLock.unlock();
            }
        }, 0, periodExecutions, TimeUnit.MILLISECONDS);
    }

    /**
     * Closes the connections to Kafka and stops to process new events.
     */
    @Override
    public void close() throws IOException {
        // Stops pooling
        kafkaConsumer.wakeup();
        // Wait that current task finishes in case it is still running
        try {
            taskLock.lock();
            kafkaConsumer.close();
            if (!pendingCommits.isEmpty()) {
                LOGGER.warning(pendingCommits.size() + " events were not commited to Kafka");
            }
            emiter.complete();
        } catch (RuntimeException e) {
            emiter.fail(e);
        } finally {
            taskLock.unlock();
        }
    }

    //Move to messaging incoming connector
    private void runInNewContext(Runnable runnable) {
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

    /**
     * Creates a new instance of ReactiveKafkaPublisher given a scheduler and the configuration and it starts to publish.
     *
     * Note: after creating a KafkaPublisher you must always
     * {@link io.helidon.microprofile.connectors.kafka.KafkaPublisher#close()} it to avoid resource leaks.
     *
     * @param <K> Key type
     * @param <V> Value type
     * @param scheduler It will trigger the task execution when
     * {@link io.helidon.microprofile.connectors.kafka.KafkaPublisher#execute()} is invoked
     * @param config With the KafkaPublisher required parameters
     * @return A new instance of ReactiveKafkaPublisher
     */
    static <K, V> KafkaPublisher<K, V> build(ScheduledExecutorService scheduler, Config config){
        Map<String, Object> kafkaConfig = HelidonToKafkaConfigParser.toMap(config);
        List<String> topics = HelidonToKafkaConfigParser.topicNameList(kafkaConfig);
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("The topic is a required configuration value");
        }
        Consumer<K, V> kafkaConsumer = new KafkaConsumer<>(kafkaConfig);
        long pollTimeout = config.get(POLL_TIMEOUT).asLong().orElse(50L);
        long periodExecutions = config.get(PERIOD_EXECUTIONS).asLong().orElse(100L);
        KafkaPublisher<K, V> publisher = new KafkaPublisher<>(scheduler, kafkaConsumer, topics, pollTimeout, periodExecutions);
        publisher.execute();
        return publisher;
    }
}
