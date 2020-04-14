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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
/**
 * Reactive streams subscriber implementation.
 *
 * @param <T> kafka record value type
 */
class KafkaSubscriber<K, V> implements Subscriber<Message<V>> {

    private static final Logger LOGGER = Logger.getLogger(KafkaSubscriber.class.getName());
    private static final String BACKPRESSURE_SIZE_KEY = "backpressure.size";
    private final long backpressure;
    private final Producer<K, V> producer;
    private final List<String> topics;
    private final AtomicLong backpressureCounter = new AtomicLong();
    private Subscription subscription;

    private KafkaSubscriber(Producer<K, V> producer, List<String> topics, long backpressure){
        this.backpressure = backpressure;
        this.producer = producer;
        this.topics = topics;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (this.subscription == null) {
            this.subscription = subscription;
            this.subscription.request(backpressure);
        } else {
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
            ProducerRecord<K, V> record = new ProducerRecord<>(topic, message.getPayload());
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    subscription.cancel();
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
                    if (backpressureCounter.incrementAndGet() == backpressure) {
                        backpressureCounter.set(0);
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
        producer.close();
    }

    @Override
    public void onComplete() {
        LOGGER.fine("Subscriber has finished");
        producer.close();
    }

    static <K, V> KafkaSubscriberBuilder<K, V> builder(Producer<K, V> producer, List<String> topics) {
        return new KafkaSubscriberBuilder<>(producer, topics);
    }

    /**
     * Fluent API builder for {@link KafkaSubscriber}.
     */
    static final class KafkaSubscriberBuilder<K, V> implements io.helidon.common.Builder<KafkaSubscriber<K, V>> {

        private final Producer<K, V> producer;
        private final List<String> topics;
        private long backpressure = 5L;

        private KafkaSubscriberBuilder(Producer<K, V> producer, List<String> topics) {
            this.producer = producer;
            this.topics = topics;
        }

        @Override
        public KafkaSubscriber<K, V> build() {
            if (topics.isEmpty()) {
                throw new IllegalArgumentException("The topic is a required value");
            }
            return new KafkaSubscriber<>(producer, topics, backpressure);
        }

        KafkaSubscriberBuilder<K, V> config(Config config) {
            config.get(BACKPRESSURE_SIZE_KEY).asLong().ifPresent(this::backpressure);
            return this;
        }

        KafkaSubscriberBuilder<K, V> backpressure(long backpressure) {
            this.backpressure = backpressure;
            return this;
        }
    }

}
