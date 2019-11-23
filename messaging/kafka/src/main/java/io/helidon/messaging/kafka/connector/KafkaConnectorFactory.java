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
import io.helidon.microprofile.config.MpConfig;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;

/**
 * Partial implementation of Connector as described in the MicroProfile Reactive Messaging Specification.
 */
@ApplicationScoped
@Connector(KafkaConnectorFactory.CONNECTOR_NAME)
public class KafkaConnectorFactory implements IncomingConnectorFactory {

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
        Config helidonConfig = ((MpConfig) config).helidonConfig();
        SimpleKafkaConsumer<Object, Object> simpleKafkaConsumer = new SimpleKafkaConsumer<>(helidonConfig);
        consumers.add(simpleKafkaConsumer);
        return simpleKafkaConsumer.createPublisherBuilder(getThreadPoolSupplier(helidonConfig).get());
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
