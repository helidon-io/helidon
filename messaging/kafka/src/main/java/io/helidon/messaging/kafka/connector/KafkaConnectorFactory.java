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

package io.helidon.messaging.kafka.connector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.config.Config;
import io.helidon.messaging.kafka.SimpleKafkaConsumer;
import io.helidon.messaging.kafka.SimpleKafkaProducer;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Partial implementation of Connector as described in the MicroProfile Reactive Messaging Specification.
 */
@ApplicationScoped
@Connector(KafkaConnectorFactory.CONNECTOR_NAME)
public class KafkaConnectorFactory implements IncomingConnectorFactory, OutgoingConnectorFactory {

    /**
     * Microprofile messaging Kafka connector name.
     */
    public static final String CONNECTOR_NAME = "helidon-kafka";

    private List<SimpleKafkaConsumer<Object, Object>> consumers = new CopyOnWriteArrayList<>();
    private ThreadPoolSupplier threadPoolSupplier = null;

    /**
     * Called when container is terminated.
     *
     * @param event termination event
     */
    public void terminate(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        consumers.forEach(SimpleKafkaConsumer::close);
    }

    public List<SimpleKafkaConsumer<Object, Object>> getConsumers() {
        return consumers;
    }

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(org.eclipse.microprofile.config.Config config) {
        Config helidonConfig = (Config) config;
        SimpleKafkaConsumer<Object, Object> simpleKafkaConsumer = new SimpleKafkaConsumer<>(helidonConfig);
        consumers.add(simpleKafkaConsumer);
        return simpleKafkaConsumer.createPushPublisherBuilder(getThreadPoolSupplier(helidonConfig).get());
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(org.eclipse.microprofile.config.Config config) {
        Config helidonConfig = (Config) config;
        SimpleKafkaProducer<Object, Object> simpleKafkaProducer = new SimpleKafkaProducer<>(helidonConfig);
        return ReactiveStreams.fromSubscriber(new Subscriber<Message<?>>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Message<?> message) {
                //TODO: Future!!!
                simpleKafkaProducer.produce(message.getPayload());
                message.ack();
            }

            @Override
            public void onError(Throwable t) {
                //TODO properly propagate!!!
                throw new RuntimeException(t);
            }

            @Override
            public void onComplete() {
                simpleKafkaProducer.close();
            }
        });
    }

    private ThreadPoolSupplier getThreadPoolSupplier(Config config) {
        synchronized (this) {
            if (this.threadPoolSupplier != null) {
                return this.threadPoolSupplier;
            }
            this.threadPoolSupplier = ThreadPoolSupplier.create(config.get("executor-service"));
            return threadPoolSupplier;
        }
    }
}
