/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se;

import java.sql.SQLException;

import io.helidon.common.reactive.Multi;
import io.helidon.messaging.Channel;
import io.helidon.messaging.Messaging;
import io.helidon.messaging.connectors.aq.AqConnector;
import io.helidon.messaging.connectors.jms.JmsConnector;
import io.helidon.messaging.connectors.jms.Type;
import io.helidon.messaging.connectors.kafka.KafkaConfigBuilder;
import io.helidon.messaging.connectors.kafka.KafkaConnector;

import jakarta.websocket.Session;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.apache.activemq.jndi.ActiveMQInitialContextFactory;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;

@SuppressWarnings("ALL")
class MessagingSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        Channel<String> channel1 = Channel.create("channel1");
        Messaging.builder()
                .publisher(channel1, Multi.just("message 1", "message 2")
                        .map(Message::of))
                .listener(channel1, s -> System.out.println("Intecepted message " + s))
                .build()
                .start();
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        Channel<String> firstChannel = Channel.create("first-channel");
        Channel<String> secondChannel = Channel.create("second-channel");

        Messaging.builder()
                .publisher(secondChannel, Multi.just("test1", "test2", "test3")
                        .map(Message::of))
                .processor(secondChannel, firstChannel, ReactiveStreams.<Message<String>>builder()
                        .map(Message::getPayload)
                        .map(String::toUpperCase)
                        .map(Message::of))
                .subscriber(firstChannel, ReactiveStreams.<Message<String>>builder()
                        .peek(Message::ack)
                        .map(Message::getPayload)
                        .forEach(s -> System.out.println("Consuming message " + s)))
                .build()
                .start();
        // >Consuming message TEST1
        // >Consuming message TEST2
        // >Consuming message TEST3
        // end::snippet_2[]
    }

    // tag::snippet_3[]
    @Connector("example-connector")
    public class ExampleConnector implements IncomingConnectorFactory, OutgoingConnectorFactory {

        @Override
        public PublisherBuilder<? extends Message<?>> getPublisherBuilder(Config config) {
            return ReactiveStreams.of("foo", "bar")
                    .map(Message::of);
        }

        @Override
        public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(Config config) {
            return ReactiveStreams.<Message<?>>builder()
                    .map(Message::getPayload)
                    .forEach(o -> System.out.println("Connector says: " + o));
        }
    }
    // end::snippet_3[]

    void snippet_4(io.helidon.config.Config config) {
        // tag::snippet_4[]
        Messaging.builder()
                .config(config)
                .connector(new ExampleConnector())
                .publisher(Channel.create("to-connector-channel"),
                           ReactiveStreams.of("fee", "fie")
                                   .map(Message::of))
                .build()
                .start();

        // > Connector says: fee
        // > Connector says: fie
        // end::snippet_4[]
    }

    void snippet_5(io.helidon.config.Config config) {
        // tag::snippet_5[]
        Messaging.builder()
                .config(config)
                .connector(new ExampleConnector())
                .subscriber(Channel.create("from-connector-channel"),
                            ReactiveStreams.<Message<String>>builder()
                                    .peek(Message::ack)
                                    .map(Message::getPayload)
                                    .forEach(s -> System.out.println("Consuming: " + s)))
                .build()
                .start();

        // > Consuming: foo
        // > Consuming: bar
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        @Connector("example-connector")
        public class ExampleConnector implements IncomingConnectorFactory {

            @Override
            public PublisherBuilder<? extends Message<?>> getPublisherBuilder(Config config) {

                String firstPropValue = config.getValue("first-test-prop", String.class); // <1>
                String secondPropValue = config.getValue("second-test-prop", String.class);

                return ReactiveStreams.of(firstPropValue, secondPropValue)
                        .map(Message::of);
            }
        }
        // end::snippet_6[]
    }

    void snippet_7(io.helidon.config.Config config, Session session) {
        // tag::snippet_7[]
        String kafkaServer = config.get("app.kafka.bootstrap.servers").asString().get();
        String topic = config.get("app.kafka.topic").asString().get();

        Channel<String> fromKafka = Channel.<String>builder() // <1> <2>
                .name("from-kafka")
                .publisherConfig(KafkaConnector.configBuilder()
                                         .bootstrapServers(kafkaServer)
                                         .groupId("example-group-" + session.getId())
                                         .topic(topic)
                                         .autoOffsetReset(KafkaConfigBuilder.AutoOffsetReset.LATEST)
                                         .enableAutoCommit(true)
                                         .keyDeserializer(StringDeserializer.class)
                                         .valueDeserializer(StringDeserializer.class)
                                         .build())
                .build();

        KafkaConnector kafkaConnector = KafkaConnector.create(); // <3>

        Messaging messaging = Messaging.builder()
                .connector(kafkaConnector)
                .listener(fromKafka, payload -> {
                    System.out.println("Kafka says: " + payload);
                })
                .build()
                .start();
        // end::snippet_7[]
    }

    void snippet_8(io.helidon.config.Config config) {
        // tag::snippet_8[]
        Messaging.builder()
                .config(config)
                .connector(new ExampleConnector())
                .listener(Channel.create("from-connector-channel"),
                          s -> System.out.println("Consuming: " + s))
                .build()
                .start();

        // > Consuming: foo
        // > Consuming: bar
        // end::snippet_8[]
    }

    void snippet_9(io.helidon.config.Config config, Session session) {
        // tag::snippet_9[]
        String kafkaServer = config.get("app.kafka.bootstrap.servers").asString().get();
        String topic = config.get("app.kafka.topic").asString().get();

        Channel<String> fromKafka = Channel.<String>builder() // <1><2>
                .name("from-kafka")
                .publisherConfig(KafkaConnector.configBuilder()
                                         .bootstrapServers(kafkaServer)
                                         .groupId("example-group-" + session.getId())
                                         .topic(topic)
                                         .autoOffsetReset(KafkaConfigBuilder.AutoOffsetReset.LATEST)
                                         .enableAutoCommit(true)
                                         .keyDeserializer(StringDeserializer.class)
                                         .valueDeserializer(StringDeserializer.class)
                                         .build())
                .build();

        KafkaConnector kafkaConnector = KafkaConnector.create(); // <3>
        Messaging messaging = Messaging.builder()
                .connector(kafkaConnector)
                .listener(fromKafka, payload -> {
                    System.out.println("Kafka says: " + payload);
                })
                .build()
                .start();
        // end::snippet_9[]
    }

    void snippet_10(io.helidon.config.Config config) {
        // tag::snippet_10[]
        String kafkaServer = config.get("app.kafka.bootstrap.servers").asString().get();
        String topic = config.get("app.kafka.topic").asString().get();

        Channel<String> toKafka = Channel.<String>builder() // <1> <2>
                .subscriberConfig(KafkaConnector.configBuilder()
                                          .bootstrapServers(kafkaServer)
                                          .topic(topic)
                                          .keySerializer(StringSerializer.class)
                                          .valueSerializer(StringSerializer.class)
                                          .build())
                .build();

        KafkaConnector kafkaConnector = KafkaConnector.create(); // <3>

        Messaging messaging = Messaging.builder()
                .publisher(toKafka, Multi.just("test1", "test2").map(Message::of))
                .connector(kafkaConnector)
                .build()
                .start();
        // end::snippet_10[]
    }

    void snippet_11(io.helidon.config.Config config) {
        // tag::snippet_11[]
        Channel<String> fromKafka = Channel.create("from-kafka");

        KafkaConnector kafkaConnector = KafkaConnector.create(); // <1>

        Messaging messaging = Messaging.builder()
                .config(config)
                .connector(kafkaConnector)
                .listener(fromKafka, payload -> {
                    System.out.println("Kafka says: " + payload);
                })
                .build()
                .start();
        // end::snippet_11[]
    }

    void snippet_12(io.helidon.config.Config config) {
        // tag::snippet_12[]
        Channel<String> toKafka = Channel.create("to-kafka");

        KafkaConnector kafkaConnector = KafkaConnector.create(); // <1>

        Messaging messaging = Messaging.builder()
                .config(config)
                .publisher(toKafka, Multi.just("test1", "test2").map(Message::of))
                .connector(kafkaConnector)
                .build()
                .start();
        // end::snippet_12[]
    }

    void snippet_13() {
        // tag::snippet_13[]
        Channel<String> fromJms = Channel.<String>builder()// <1> <2>
                .name("from-jms")
                .publisherConfig(JmsConnector.configBuilder()
                                         .jndiInitialFactory(ActiveMQInitialContextFactory.class)
                                         .jndiProviderUrl("tcp://127.0.0.1:61616")
                                         .type(Type.QUEUE)
                                         .destination("se-example-queue-1")
                                         .build())
                .build();

        JmsConnector jmsConnector = JmsConnector.create(); // <3>

        Messaging messaging = Messaging.builder()
                .connector(jmsConnector)
                .listener(fromJms, payload -> {
                    System.out.println("Jms says: " + payload);
                })
                .build()
                .start();
        // end::snippet_13[]
    }

    void snippet_14() {
        // tag::snippet_14[]
        Channel<String> toJms = Channel.<String>builder() // <1> <2>
                .subscriberConfig(JmsConnector.configBuilder()
                                          .jndiInitialFactory(ActiveMQInitialContextFactory.class)
                                          .jndiProviderUrl("tcp://127.0.0.1:61616")
                                          .type(Type.QUEUE)
                                          .destination("se-example-queue-1")
                                          .build()
                ).build();

        JmsConnector jmsConnector = JmsConnector.create(); // <3>

        Messaging messaging = Messaging.builder()
                .publisher(toJms, Multi.just("test1", "test2").map(Message::of))
                .connector(jmsConnector)
                .build()
                .start();
        // end::snippet_14[]
    }

    void snippet_15(io.helidon.config.Config config) {
        // tag::snippet_15[]
        Channel<String> fromJms = Channel.create("from-jms");

        JmsConnector jmsConnector = JmsConnector.create(); // <1>

        Messaging messaging = Messaging.builder()
                .config(config)
                .connector(jmsConnector)
                .listener(fromJms, payload -> {
                    System.out.println("Jms says: " + payload);
                })
                .build()
                .start();
        // end::snippet_15[]
    }

    void snippet_16(io.helidon.config.Config config) {
        // tag::snippet_16[]
        Channel<String> toJms = Channel.create("to-jms");

        JmsConnector jmsConnector = JmsConnector.create(); // <1>

        Messaging messaging = Messaging.builder()
                .config(config)
                .publisher(toJms, Multi.just("test1", "test2").map(Message::of))
                .connector(jmsConnector)
                .build()
                .start();
        // end::snippet_16[]
    }

    void snippet_17() throws SQLException {
        // tag::snippet_17[]
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource(); // <1>
        pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pds.setURL(
                "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(Host=192.168.0.123)(Port=1521))(CONNECT_DATA=(SID=XE)))");
        pds.setUser("frank");
        pds.setPassword("frank");
        AqConnector seConn = AqConnector.builder() // <2>
                .dataSource("test-ds", pds)
                .build();
        Channel<String> toAq = Channel.<String>builder() // <3>
                .name("toAq")
                .subscriberConfig(AqConnector.configBuilder()
                                          .queue("example_queue_1")
                                          .dataSource("test-ds")
                                          .build())
                .build();

        Channel<String> fromAq = Channel.<String>builder() // <4>
                .name("fromAq")
                .publisherConfig(AqConnector.configBuilder()
                                         .queue("example_queue_1")
                                         .dataSource("test-ds")
                                         .build())
                .build();

        Messaging.builder() // <5>
                .connector(seConn)
                .publisher(toAq, Multi.just("Hello", "world", "from", "Oracle", "DB!").map(Message::of)) // <6>
                .listener(fromAq, s -> System.out.println("Message received: " + s)) // <7>
                .build()
                .start();
        // end::snippet_17[]
    }
}
