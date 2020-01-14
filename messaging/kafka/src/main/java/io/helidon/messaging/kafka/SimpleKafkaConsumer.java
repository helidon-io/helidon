/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.messaging.kafka;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.messaging.kafka.connector.KafkaMessage;
import io.helidon.messaging.kafka.connector.SimplePublisher;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Subscription;

/**
 * Simple Kafka consumer covering basic use-cases.
 * Configurable by Helidon {@link io.helidon.config.Config Config},
 * For more info about configuration see {@link KafkaConfigProperties}
 * <p>
 * Usage:
 * <pre>{@code
 *   try (SimpleKafkaConsumer<Long, String> c = new SimpleKafkaConsumer<>("test-channel", Config.create())) {
 *         c.consumeAsync(r -> System.out.println(r.value()));
 *   }
 * }</pre>
 *
 * @param <K> Key type
 * @param <V> Value type
 * @see KafkaConfigProperties
 * @see io.helidon.config.Config
 */
public class SimpleKafkaConsumer<K, V> implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(SimpleKafkaConsumer.class.getName());
    private final KafkaConfigProperties properties;

    private AtomicBoolean closed = new AtomicBoolean(false);
    private PartitionsAssignedLatch partitionsAssignedLatch = new PartitionsAssignedLatch();
    private String consumerId;
    private ExecutorService executorService;
    private ExecutorService externalExecutorService;
    private List<String> topicNameList;
    private KafkaConsumer<K, V> consumer;

    private final LinkedList<ConsumerRecord<K, V>> backPressureBuffer = new LinkedList<>();
    private ArrayList<CompletableFuture<Void>> ackFutures = new ArrayList<>();

    /**
     * Kafka consumer created from {@link io.helidon.config.Config config}
     * see configuration {@link KafkaConfigProperties example}.
     *
     * @param channelName key in configuration
     * @param config      Helidon {@link io.helidon.config.Config config}
     * @see KafkaConfigProperties
     * @see io.helidon.config.Config
     */
    public SimpleKafkaConsumer(String channelName, Config config) {
        this(channelName, config, null);
    }

    /**
     * Kafka consumer created from {@link io.helidon.config.Config config}
     * see configuration {@link KafkaConfigProperties example}.
     *
     * @param channelName     key in configuration
     * @param config          Helidon {@link io.helidon.config.Config config}
     * @param consumerGroupId Custom group.id, can be null, overrides group.id from configuration
     * @see KafkaConfigProperties
     * @see io.helidon.config.Config
     */
    public SimpleKafkaConsumer(String channelName, Config config, String consumerGroupId) {
        properties = new KafkaConfigProperties(config.get("mp.messaging.incoming").get(channelName));
        properties.setProperty(KafkaConfigProperties.GROUP_ID, getOrGenerateGroupId(consumerGroupId));
        this.topicNameList = properties.getTopicNameList();
        this.consumerId = channelName;
        consumer = new KafkaConsumer<>(properties);
    }

    /**
     * Kafka consumer created from {@link io.helidon.config.Config config}
     * see configuration {@link KafkaConfigProperties example}.
     *
     * @param config Helidon {@link io.helidon.config.Config config}
     */
    public SimpleKafkaConsumer(Config config) {
        properties = new KafkaConfigProperties(config);
        properties.setProperty(KafkaConfigProperties.GROUP_ID, getOrGenerateGroupId(null));
        this.topicNameList = properties.getTopicNameList();
        this.consumerId = null;
        consumer = new KafkaConsumer<>(properties);
    }

    /**
     * Execute supplied consumer for each received record.
     *
     * @param function to be executed for each received record
     * @return {@link java.util.concurrent.Future}
     */
    public Future<?> consumeAsync(Consumer<ConsumerRecord<K, V>> function) {
        return this.consumeAsync(Executors.newWorkStealingPool(), null, function);
    }

    /**
     * Execute supplied consumer by provided executor service for each received record.
     *
     * @param executorService Custom executor service used for spinning up polling thread and record consuming threads
     * @param customTopics    Can be null, list of topics appended to the list from configuration
     * @param function        Consumer method executed in new thread for each received record
     * @return The Future's get method will return null when consumer is closed
     */
    public Future<?> consumeAsync(ExecutorService executorService, List<String> customTopics,
                                  Consumer<ConsumerRecord<K, V>> function) {
        LOGGER.info(String.format("Initiating kafka consumer %s listening to topics: %s with groupId: %s",
                consumerId, topicNameList, properties.getProperty(KafkaConfigProperties.GROUP_ID)));

        List<String> mergedTopics = new ArrayList<>();
        mergedTopics.addAll(properties.getTopicNameList());
        mergedTopics.addAll(Optional.ofNullable(customTopics).orElse(Collections.emptyList()));

        if (mergedTopics.isEmpty()) {
            throw new InvalidKafkaConsumerState("No topic names provided in configuration or by parameter.");
        }

        validateConsumer();
        this.executorService = executorService;
        return executorService.submit(() -> {
            consumer.subscribe(mergedTopics, partitionsAssignedLatch);
            try {
                while (!closed.get()) {
                    ConsumerRecords<K, V> consumerRecords = consumer.poll(Duration.ofSeconds(5));
                    consumerRecords.forEach(cr -> executorService.execute(() -> function.accept(cr)));
                }
            } catch (WakeupException ex) {
                if (!closed.get()) {
                    throw ex;
                }
            } finally {
                LOGGER.info("Closing consumer" + consumerId);
                consumer.close();
            }
        });
    }

    /**
     * Create publisher builder.
     *
     * @param executorService {@link java.util.concurrent.ExecutorService}
     * @return {@link org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder}
     */
    public PublisherBuilder<? extends Message<?>> createPushPublisherBuilder(ExecutorService executorService) {
        validateConsumer();
        this.externalExecutorService = executorService;
        return ReactiveStreams.fromPublisher(new SimplePublisher<K, V>(subscriber -> {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    LOGGER.log(Level.FINE, "Pushing Kafka consumer doesn't support requests.");
                }

                @Override
                public void cancel() {
                    SimpleKafkaConsumer.this.close();
                }
            });
            externalExecutorService.submit(() -> {
                consumer.subscribe(topicNameList, partitionsAssignedLatch);
                try {
                    while (!closed.get()) {
                        synchronized (backPressureBuffer) {
                            waitForAcksAndPoll();
                            if (backPressureBuffer.isEmpty()) continue;
                            ConsumerRecord<K, V> cr = backPressureBuffer.poll();
                            KafkaMessage<K, V> kafkaMessage = new KafkaMessage<>(cr);
                            ackFutures.add(kafkaMessage.getAckFuture());
                            runInNewContext(() -> subscriber.onNext(kafkaMessage));
                        }
                    }
                } catch (WakeupException ex) {
                    if (!closed.get()) {
                        throw ex;
                    }
                } finally {
                    LOGGER.info("Closing consumer" + consumerId);
                    consumer.close();
                }
            });
        }));
    }

    /**
     * Naive impl of back pressure wise lazy poll.
     * Wait for the last batch of records to be acknowledged before commit and another poll.
     */
    private void waitForAcksAndPoll() {
        if (backPressureBuffer.isEmpty()) {
            try {
                if (!ackFutures.isEmpty()) {
                    CompletableFuture.allOf(ackFutures.toArray(new CompletableFuture[0])).get();
                    consumer.commitSync();
                }
                consumer.poll(Duration.ofSeconds(1)).forEach(backPressureBuffer::add);
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.SEVERE, "Error when waiting for all polled records acknowledgements.", e);
            }

        }
    }

    private void validateConsumer() {
        if (this.closed.get()) {
            throw new InvalidKafkaConsumerState("Invalid consumer state, already closed");
        }
        if (this.executorService != null) {
            throw new InvalidKafkaConsumerState("Invalid consumer state, already consuming");
        }
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
    public void waitForPartitionAssigment(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (!partitionsAssignedLatch.await(timeout, unit)) {
            throw new TimeoutException("Timeout for subscription reached");
        }
    }

    /**
     * Close consumer gracefully. Stops polling loop,
     * wakes possible blocked poll and shuts down executor service.
     */
    @Override
    public void close() {
        this.closed.set(true);
        this.consumer.wakeup();
        Optional.ofNullable(this.executorService).ifPresent(ExecutorService::shutdown);
    }

    /**
     * Use supplied customGroupId if not null
     * or take it from configuration if exist
     * or generate random in this order.
     *
     * @param customGroupId custom group.id, overrides group.id from configuration
     * @return returns or generate new groupId
     */
    protected String getOrGenerateGroupId(String customGroupId) {
        return Optional.ofNullable(customGroupId)
                .orElse(Optional.ofNullable(properties.getProperty(KafkaConfigProperties.GROUP_ID))
                        .orElse(UUID.randomUUID().toString()));
    }

    //Move to messaging incoming connector
    private void runInNewContext(Runnable runnable) {
        Context parentContext = Context.create();
        Context context = Context
                .builder()
                .parent(parentContext)
                .id(String.format("%s:message-%s", parentContext.id(), UUID.randomUUID().toString()))
                .build();
        Contexts.runInContext(context, runnable);
    }

}
