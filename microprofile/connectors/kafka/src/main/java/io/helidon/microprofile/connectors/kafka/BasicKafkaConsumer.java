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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

/**
 * Basic Kafka consumer covering basic use-cases.
 * Configurable by Helidon {@link io.helidon.config.Config Config},
 * For more info about configuration see {@link HelidonToKafkaConfigParser}
 *
 * @param <K> Key type
 * @param <V> Value type
 * @see HelidonToKafkaConfigParser
 * @see io.helidon.config.Config
 */
class BasicKafkaConsumer<K, V> implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(BasicKafkaConsumer.class.getName());
    private static final String POLL_TIMEOUT = "poll.timeout";
    private static final String PERIOD_EXECUTIONS = "period.executions";
    private final Lock taskLock = new ReentrantLock();
    private final PartitionsAssignedLatch partitionsAssignedLatch = new PartitionsAssignedLatch();
    private final KafkaConsumer<K, V> consumer;
    private final ScheduledExecutorService scheduler;
    private final BasicPublisher<K, V> publisher;
    private final AtomicLong requests = new AtomicLong();
    private final BlockingQueue<Entry<TopicPartition, OffsetAndMetadata>> pendingCommits = new LinkedBlockingQueue<>();

    private BasicKafkaConsumer(KafkaConsumer<K, V> consumer, ScheduledExecutorService scheduler, List<String> topics,
            long pollTimeout, long periodExecutions) {
        this.consumer = consumer;
        this.scheduler = scheduler;
        this.publisher = new BasicPublisher<K, V>(Optional.of(requested -> requests.addAndGet(requested)), subscriber -> {
            LOGGER.fine("Subscribed to topics " + topics);
            consumer.subscribe(topics, partitionsAssignedLatch);
            scheduler.scheduleAtFixedRate(new BackPressureLayer(pollTimeout), 0,
                    periodExecutions, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * Kafka consumer created from {@link io.helidon.config.Config config}
     * see configuration {@link HelidonToKafkaConfigParser example}.
     *
     * @param config Helidon {@link io.helidon.config.Config config}
     * @param scheduler Helidon {@link java.util.concurrent.ScheduledExecutorService scheduler}
     */
    static <K, V> BasicKafkaConsumer<K, V> create(Config config, ScheduledExecutorService scheduler){
        Map<String, Object> kafkaConfig = HelidonToKafkaConfigParser.toMap(config);
        List<String> topics = HelidonToKafkaConfigParser.topicNameList(kafkaConfig);
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("The topic is a required configuration value");
        } else {
            long pollTimeout = config.get(POLL_TIMEOUT).asLong().asOptional().orElse(50L);
            long periodExecutions = config.get(PERIOD_EXECUTIONS).asLong().asOptional().orElse(100L);
            return new BasicKafkaConsumer<K, V>(new KafkaConsumer<>(kafkaConfig), scheduler, topics,
                    pollTimeout, periodExecutions);
        }
    }

    /**
     * Create publisher builder.
     *
     * @return {@link org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder}
     */
    PublisherBuilder<? extends Message<?>> createPushPublisherBuilder() {
        return ReactiveStreams.fromPublisher(publisher);
    }

    /**
     * Blocks current thread until partitions are assigned,
     * since when is consumer effectively ready to receive.
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
     * Close gracefully. Stops wakes possible blocked poll and close consumer.
     */
    @Override
    public void close() {
        // Stops pooling
        consumer.wakeup();
        // Wait that current task finishes in case it is still running
        try {
            taskLock.lock();
            consumer.close();
            if (!pendingCommits.isEmpty()) {
                LOGGER.warning(pendingCommits.size() + " events were not commited to Kafka");
            }
            if (!publisher.isCancelled()) {
                publisher.complete();
            }
        } catch (RuntimeException e) {
            publisher.fail(e);
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

    private final class BackPressureLayer implements Runnable {

        private final long pollTimeout;
        private final LinkedList<ConsumerRecord<K, V>> backPressureBuffer = new LinkedList<>();

        private BackPressureLayer(long pollTimeout) {
            this.pollTimeout = pollTimeout;
        }

        @Override
        public void run() {
            try {
                // Need to lock to avoid onClose() is executed meanwhile task is running
                taskLock.lock();
                if (!scheduler.isShutdown() && !publisher.isCancelled()) {
                    if (backPressureBuffer.isEmpty()) {
                        try {
                            consumer.poll(Duration.ofMillis(pollTimeout)).forEach(backPressureBuffer::add);
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
                            runInNewContext(() -> publisher.emit(kafkaMessage));
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
                consumer.commitSync(offsets);
                if (!offsets.isEmpty()) {
                    LOGGER.fine(String.format("%s events were ACK: ", offsets.size()));
                }
            } finally {
                taskLock.unlock();
            }
        }
    }

}
