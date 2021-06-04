/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
/**
 * Reactive streams subscriber implementation.
 * @param <K> kafka record key type
 * @param <V> kafka record value type
 */
public class KafkaSubscriber<K, V> implements Subscriber<Message<V>> {

    private static final Logger LOGGER = Logger.getLogger(KafkaSubscriber.class.getName());
    private static final String BACKPRESSURE_SIZE_KEY = "backpressure.size";

    private final long backpressure;
    private final Supplier<Producer<K, V>> producerSupplier;
    private final List<String> topics;
    private final AtomicLong backpressureCounter = new AtomicLong();

    private Subscription subscription;
    private Producer<K, V> kafkaProducer;

    private KafkaSubscriber(Supplier<Producer<K, V>> producerSupplier, List<String> topics, long backpressure){
        this.backpressure = backpressure;
        this.producerSupplier = producerSupplier;
        this.topics = topics;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        try {
            if (this.subscription == null) {
                this.kafkaProducer = producerSupplier.get();
                this.subscription = subscription;
                this.subscription.request(backpressure);
            } else {
                subscription.cancel();
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Cannot start the Kafka producer", e);
            subscription.cancel();
        }
    }

    @Override
    public void onNext(Message<V> message) {
        Objects.requireNonNull(message);
        List<CompletableFuture<Void>> futureList = new ArrayList<>(topics.size());
        for (String topic : topics) {
            CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            futureList.add(completableFuture);
            ProducerRecord<K, V> record;
            if (message instanceof KafkaMessage) {
                KafkaMessage<K, V> kafkaMessage = (KafkaMessage<K, V>) message;
                record = new ProducerRecord<>(
                        topic,
                        null,
                        null,
                        kafkaMessage.getKey().orElse(null),
                        kafkaMessage.getPayload(),
                        kafkaMessage.getHeaders());
            } else {
                record = new ProducerRecord<>(topic, message.getPayload());
            }
            kafkaProducer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    subscription.cancel();
                    LOGGER.log(Level.WARNING, "Error when sending kafka message to topic: " + topic, exception);
                    completableFuture.completeExceptionally(exception);
                } else {
                    completableFuture.complete(null);
                }
            });
        }
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]))
                .whenComplete((success, exception) -> {
                    if (exception == null) {
                        message.ack().whenComplete((a, b) -> {
                            // Atomically increment
                            // or reset backpressureCounter if incrementing would reach threshold
                            if (backpressureCounter.getAndUpdate(n -> ++n == backpressure ? 0 : n)
                                    >= backpressure - 1) {
                                // configured backpressure threshold reached
                                subscription.request(backpressure);
                            }
                        });
                    }
                });
    }

    @Override
    public void onError(Throwable t) {
        Objects.requireNonNull(t);
        LOGGER.log(Level.SEVERE, "The Kafka subscription has failed", t);
        kafkaProducer.close();
    }

    @Override
    public void onComplete() {
        LOGGER.fine(() -> "Subscriber has finished");
        kafkaProducer.close();
    }

    /**
     * A builder for KafkaSubscriber.
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
    public static <K, V> KafkaSubscriber<K, V> create(Config config) {
        return (KafkaSubscriber<K, V>) builder().config(config).build();
    }

    /**
     * Fluent API builder for {@link KafkaSubscriber}.
     * @param <K> Key type
     * @param <V> Value type
     */
    public static final class Builder<K, V> implements io.helidon.common.Builder<KafkaSubscriber<K, V>> {

        private Supplier<Producer<K, V>> producerSupplier;
        private List<String> topics;
        private long backpressure = 5L;

        private Builder() {
        }

        @Override
        public KafkaSubscriber<K, V> build() {
            if (Objects.isNull(topics) || topics.isEmpty()) {
                throw new IllegalArgumentException("The topic is a required value");
            }
            if (Objects.isNull(producerSupplier)) {
                throw new IllegalArgumentException("The producerSupplier is a required value");
            }
            return new KafkaSubscriber<>(producerSupplier, topics, backpressure);
        }

        /**
         * Load this builder from a configuration.
         *
         * @param config configuration to load from
         * @return updated builder instance
         */
        public Builder<K, V> config(Config config) {
            KafkaConfig kafkaConfig = KafkaConfig.create(config);
            producerSupplier(() -> new KafkaProducer<>(kafkaConfig.asMap()));
            topics(kafkaConfig.topics());
            config.get(BACKPRESSURE_SIZE_KEY).asLong().ifPresent(this::backpressure);
            return this;
        }

        /**
         * Defines how to instantiate the KafkaSubscriber. It will be invoked
         * in {@link KafkaSubscriber#onSubscribe(Subscription)}
         *
         * This is a mandatory parameter.
         *
         * @param producerSupplier set a supplier of a producer
         * @return updated builder instance
         */
        public Builder<K, V> producerSupplier(Supplier<Producer<K, V>> producerSupplier) {
            this.producerSupplier = producerSupplier;
            return this;
        }

        /**
         * Specifies the number of messages that are requested after processing them.
         *
         * The default value is 5.
         *
         * @param backpressure number of messages that are requested
         * @return updated builder instance
         */
        public Builder<K, V> backpressure(long backpressure) {
            this.backpressure = backpressure;
            return this;
        }

        /**
         * The list of topics the messages should be sent to.
         *
         * This is a mandatory parameter.
         *
         * @param topics list of topics
         * @return updated builder instance
         */
        public Builder<K, V> topics(List<String> topics) {
            this.topics = topics;
            return this;
        }
    }

}
