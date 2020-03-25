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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;

/**
 * Implementation of Kafka Connector as described in the MicroProfile Reactive Messaging Specification.
 */
@ApplicationScoped
@Connector(KafkaConnectorFactory.CONNECTOR_NAME)
public class KafkaConnectorFactory implements IncomingConnectorFactory, OutgoingConnectorFactory {

    /**
     * Microprofile messaging Kafka connector name.
     */
    static final String CONNECTOR_NAME = "helidon-kafka";
    private static final Logger LOGGER = Logger.getLogger(KafkaConnectorFactory.class.getName());
    private static final String BACKPRESSURE_SIZE_KEY = "backpressure.size";
    private static final long BACKPRESSURE_SIZE_DEFAULT = 5;
    private final ScheduledExecutorService scheduler;
    private final Queue<Closeable> resourcesToClose = new LinkedList<>();

    /**
     * Constructor to instance KafkaConnectorFactory.
     *
     * @param config Helidon {@link io.helidon.config.Config config}
     */
    @Inject
    public KafkaConnectorFactory(Config config) {
        scheduler = ScheduledThreadPoolSupplier.builder().threadNamePrefix("kafka-")
        .config(config).build().get();
    }

    /**
     * Called when container is terminated.
     *
     * @param event termination event
     */
    void terminate(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        LOGGER.fine("Terminating KafkaConnectorFactory...");
        // Stops the scheduler first to make sure no new task will be triggered meanwhile consumers are closing
        scheduler.shutdown();
        List<Exception> failed = new LinkedList<>();
        Closeable closeable;
        while ((closeable = resourcesToClose.poll()) != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Continue closing
                failed.add(e);
            }
        }
        if (failed.isEmpty()) {
            LOGGER.fine("KafkaConnectorFactory terminated successfuly");
        } else {
            // Inform about the errors
            failed.forEach(e -> LOGGER.log(Level.SEVERE, "An error happened closing resource", e));
        }
    }

    /**
     * Gets the open resources for testing verification purposes.
     * @return the opened resources
     */
    Queue<Closeable> resources(){
        return resourcesToClose;
    }

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(org.eclipse.microprofile.config.Config config) {
        Config helidonConfig = (Config) config;
        BasicKafkaConsumer<Object, Object> basicKafkaConsumer = BasicKafkaConsumer.create(helidonConfig, scheduler);
        resourcesToClose.add(basicKafkaConsumer);
        return basicKafkaConsumer.createPushPublisherBuilder();
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(org.eclipse.microprofile.config.Config config) {
        Config helidonConfig = (Config) config;
        long backpressure = helidonConfig.get(BACKPRESSURE_SIZE_KEY).asLong().asOptional().orElse(BACKPRESSURE_SIZE_DEFAULT);
        BasicKafkaProducer<Object, Object> basicKafkaProducer = BasicKafkaProducer.create(helidonConfig);
        resourcesToClose.add(basicKafkaProducer);
        return ReactiveStreams.fromSubscriber(new BasicSubscriber<>(basicKafkaProducer, backpressure));
    }
}
