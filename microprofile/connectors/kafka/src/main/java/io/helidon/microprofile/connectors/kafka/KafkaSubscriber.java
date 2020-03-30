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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
/**
 * Reactive streams subscriber implementation.
 *
 * @param <T> kafka record value type
 */
class KafkaSubscriber<T> implements Subscriber<Message<T>> {

    private static final Logger LOGGER = Logger.getLogger(KafkaSubscriber.class.getName());
    private static final String BACKPRESSURE_SIZE_KEY = "backpressure.size";
    private static final long BACKPRESSURE_SIZE_DEFAULT = 5;
    private final long backpressure;
    private final AtomicLong backpressureCounter = new AtomicLong();
    private final BasicKafkaProducer<?, T> producer;
    private Subscription subscription;

    private KafkaSubscriber(BasicKafkaProducer<?, T> producer, long backpressure){
        this.backpressure = backpressure;
        this.producer = producer;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(backpressure);
    }

    @Override
    public void onNext(Message<T> message) {
        producer.produceAsync(message.getPayload());
        message.ack();
        if (backpressureCounter.incrementAndGet() == backpressure) {
            backpressureCounter.set(0);
            subscription.request(backpressure);
        }
    }

    @Override
    public void onError(Throwable t) {
        LOGGER.log(Level.SEVERE, "The Kafka subscription has failed", t);
        producer.close();
    }

    @Override
    public void onComplete() {
        LOGGER.fine("Subscriber has finished");
        producer.close();
    }

    /**
     * Creates a new instance of KafkaSubscriber given the configuration.
     * Note: Every new instance of this type opens Kafka resources and it will be opened
     * till onComplete() or onError() is invoked.
     *
     * @param <T> The type to push
     * @param config With the KafkaSubscriber required parameters
     * @return A new KafkaSubscriber instance
     */
    static <T> KafkaSubscriber<T> build(Config config) {
        Map<String, Object> kafkaConfig = HelidonToKafkaConfigParser.toMap(config);
        List<String> topics = HelidonToKafkaConfigParser.topicNameList(kafkaConfig);
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("The topic is a required configuration value");
        }
        long backpressure = config.get(BACKPRESSURE_SIZE_KEY).asLong().orElse(BACKPRESSURE_SIZE_DEFAULT);
        return new KafkaSubscriber<T>(new BasicKafkaProducer<>(topics, new KafkaProducer<>(kafkaConfig)), backpressure);
    }

}
