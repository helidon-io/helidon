/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.messaging.Stoppable;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorAttribute;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;

/**
 * Implementation of Kafka Connector as described in the MicroProfile Reactive Messaging Specification.
 */
@ApplicationScoped
@Connector(KafkaConnector.CONNECTOR_NAME)
@ConnectorAttribute(name = "bootstrap.servers",
        description = "A list of comma separated host:port pairs to use for "
                + "establishing the initial connection to the Kafka cluster.",
        mandatory = true,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = "key.deserializer",
        description = "Fully qualified name of key deserializer class that implements "
                + "the org.apache.kafka.common.serialization.Deserializer interface.",
        mandatory = true,
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "value.deserializer",
        description = "Fully qualified name of value deserializer class that implements "
                + "the org.apache.kafka.common.serialization.Deserializer interface.",
        mandatory = true,
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "key.serializer",
        description = "Fully qualified name of key serializer class that implements "
                + "the org.apache.kafka.common.serialization.Serializer interface.",
        mandatory = true,
        direction = ConnectorAttribute.Direction.OUTGOING,
        type = "string")
@ConnectorAttribute(name = "value.serializer",
        description = "Fully qualified name of value serializer class that implements "
                + "the org.apache.kafka.common.serialization.Serializer interface.",
        mandatory = true,
        direction = ConnectorAttribute.Direction.OUTGOING,
        type = "string")
@ConnectorAttribute(name = "topic",
        description = "Comma separated names of the topics to consume from.",
        mandatory = true,
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "topic",
        description = "Comma separated names of the topics to produce to.",
        mandatory = true,
        direction = ConnectorAttribute.Direction.OUTGOING,
        type = "string")
@ConnectorAttribute(name = "nack-dlq",
        description = "\"Dead Letter Queue\" topic name to send NACKED messages to, "
                + "other connection properties are going to be derived from consumer config.",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "nack-dlq.topic",
        description = "\"Dead Letter Queue\" topic name to send NACKED messages to.",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "nack-dlq.bootstrap.servers",
        description = "A list of comma separated host:port pairs to use for establishing "
                + "the initial connection to the Kafka cluster with the \"Dead Letter Queue\".",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "nack-dlq.key.serializer",
        description = "Fully qualified name of key deserializer class that implements "
                + "the org.apache.kafka.common.serialization.Serializer interface.",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "nack-dlq.value.serializer",
        description = "Fully qualified name of value deserializer class that implements "
                + "the org.apache.kafka.common.serialization.Serializer interface.",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "auto.offset.reset",
        description = "What to do when there is no initial offset in Kafka or if "
                + "the current offset does not exist any more on the server. Valid Values: [latest, earliest, none].",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = "poll.timeout",
        description = "The maximum time to block polling loop in milliseconds.",
        direction = ConnectorAttribute.Direction.INCOMING,
        defaultValue = "50",
        type = "long")
@ConnectorAttribute(name = "period.executions",
        description = "Period between successive executions of polling loop in milliseconds.",
        direction = ConnectorAttribute.Direction.INCOMING,
        defaultValue = "100",
        type = "long")
@ConnectorAttribute(name = "batch.size",
        description = "Producer will attempt to batch records together into fewer requests whenever "
                + "multiple records are being sent to the same partition.",
        direction = ConnectorAttribute.Direction.OUTGOING,
        defaultValue = "16384",
        type = "int")
@ConnectorAttribute(name = "acks",
        description = "The number of acknowledgments the producer requires "
                + "the leader to have received before considering a request complete. Valid Values: [all, -1, 0, 1].",
        direction = ConnectorAttribute.Direction.OUTGOING,
        defaultValue = "1",
        type = "string")
@ConnectorAttribute(name = "buffer.memory",
        description = "The total bytes of memory the producer can use to buffer records waiting to be sent to the server.",
        direction = ConnectorAttribute.Direction.OUTGOING,
        defaultValue = "33554432",
        type = "long")
@ConnectorAttribute(name = "compression.type",
        description = "The compression type for all data generated by the producer. "
                + "The default is none (i.e. no compression). Valid Values: [none, gzip, snappy, lz4, zstd].",
        direction = ConnectorAttribute.Direction.OUTGOING,
        defaultValue = "none",
        type = "string")
public class KafkaConnector implements IncomingConnectorFactory, OutgoingConnectorFactory, Stoppable {

    private static final System.Logger LOGGER = System.getLogger(KafkaConnector.class.getName());
    /**
     * Microprofile messaging Kafka connector name.
     */
    static final String CONNECTOR_NAME = "helidon-kafka";

    private final ScheduledExecutorService scheduler;
    private final Queue<KafkaPublisher<?, ?>> resources = new LinkedList<>();

    /**
     * Constructor to instance KafkaConnectorFactory.
     *
     * @param config Helidon {@link io.helidon.config.Config config}
     */
    @Inject
    KafkaConnector(Config config) {
        scheduler = ScheduledThreadPoolSupplier.builder()
                .threadNamePrefix("kafka-")
                .config(config)
                .build()
                .get();
    }

    /**
     * Called when container is terminated. If it is not running in a container it must be explicitly invoked
     * to terminate the messaging and release Kafka connections.
     *
     * @param event termination event
     */
    void terminate(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        stop();
    }

    /**
     * Gets the open resources for testing verification purposes.
     *
     * @return the opened resources
     */
    Queue<KafkaPublisher<?, ?>> resources() {
        return resources;
    }

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(org.eclipse.microprofile.config.Config config) {
        KafkaPublisher<Object, Object> publisher = KafkaPublisher.builder()
                .config(MpConfig.toHelidonConfig(config))
                .scheduler(scheduler)
                .build();
        LOGGER.log(Level.DEBUG, () -> String.format("Resource %s added", publisher));
        resources.add(publisher);
        return ReactiveStreams.fromPublisher(publisher);
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(org.eclipse.microprofile.config.Config config) {
        return ReactiveStreams.fromSubscriber(KafkaSubscriber.create(MpConfig.toHelidonConfig(config)));
    }

    /**
     * Creates a new instance of KafkaConnector with the required configuration.
     *
     * @param config Helidon {@link io.helidon.config.Config config}
     * @return the new instance
     */
    public static KafkaConnector create(Config config) {
        return new KafkaConnector(config);
    }

    /**
     * Creates a new instance of KafkaConnector with empty configuration.
     *
     * @return the new instance
     */
    public static KafkaConnector create() {
        return new KafkaConnector(Config.empty());
    }

    /**
     * Stops the KafkaConnector and all the jobs and resources related to it.
     */
    public void stop() {
        LOGGER.log(Level.DEBUG, () -> "Terminating KafkaConnector...");
        // Stops the scheduler first to make sure no new task will be triggered meanwhile consumers are closing
        scheduler.shutdown();
        List<Exception> failed = new LinkedList<>();
        KafkaPublisher<?, ?> resource;
        while ((resource = resources.poll()) != null) {
            try {
                resource.stop();
            } catch (Exception e) {
                // Continue closing
                failed.add(e);
            }
        }
        if (failed.isEmpty()) {
            LOGGER.log(Level.DEBUG, "KafkaConnector terminated successfuly");
        } else {
            // Inform about the errors
            failed.forEach(e -> LOGGER.log(Level.ERROR, "An error happened closing resource", e));
        }
    }

    /**
     * Custom config builder for Kafka connector.
     *
     * @return new Kafka specific config builder
     */
    public static KafkaConfigBuilder configBuilder() {
        return new KafkaConfigBuilder();
    }
}

