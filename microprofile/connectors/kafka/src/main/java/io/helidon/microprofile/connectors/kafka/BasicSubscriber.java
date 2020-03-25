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

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
/**
 * Reactive streams subscriber implementation.
 *
 * @param <T> kafka record value type
 */
class BasicSubscriber<T> implements Subscriber<Message<T>> {

    private static final Logger LOGGER = Logger.getLogger(BasicSubscriber.class.getName());
    private final long backpresure;
    private final AtomicLong backpressureCounter = new AtomicLong();
    private final BasicKafkaProducer<?, T> producer;
    private Subscription subscription;

    BasicSubscriber(BasicKafkaProducer<?, T> producer, long backpresure){
        this.backpresure = backpresure;
        this.producer = producer;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(backpresure);
    }

    @Override
    public void onNext(Message<T> message) {
        producer.produceAsync(message.getPayload());
        message.ack();
        if (backpressureCounter.incrementAndGet() == backpresure) {
            backpressureCounter.set(0);
            subscription.request(backpresure);
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

}
