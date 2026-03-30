# Reactive Messaging

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Usage](#usage)
  - [Channel](#channel)
  - [Processor](#processor)
  - [Message](#message)
  - [Connectors](#connectors)
    - [Kafka Connector](#kafka-connector)
    - [JMS Connector](#jms-connector)
    - [AQ Connector](#aq-connector)
- [Configuration](#configuration)
- [Reference](#reference)

## Overview

Asynchronous messaging is a commonly used form of communication in the world of microservices. While it is possible to start building your reactive streams directly by combining operators and connecting them to reactive APIs, with Helidon SE Reactive Messaging, you can now use prepared tools for repetitive use case scenarios .

## Maven Coordinates

To enable Reactive Messaging, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.messaging</groupId>
    <artifactId>helidon-messaging</artifactId>
</dependency>
```

## Usage

Connecting your streams to external services usually requires a lot of boilerplate code for configuration handling, backpressure propagation, acknowledgement and more.

In Helidon there is a system of connectors, emitters and means to orchestrate these tasks called **Reactive Messaging**. It’s basically an API for connecting and configuring connectors and emitters with your reactive streams through [Channels](#channel).

Reactive Messaging relates to [MicroProfile Reactive Messaging](../mp/reactivemessaging/introduction.md) as the making of connectors and configuring them can be a repetitive task that ultimately leads to the same results. Helidon SE Reactive Messaging supports the very same configuration format for connectors as its MicroProfile counterpart does. Also, MP Connectors are reusable in Helidon SE Messaging with some limitations such as there is no CDI in Helidon SE. All [Messaging connectors](#messaging-connector) in Helidon are made to be universally usable by Helidon MP and SE.

### Channel

A channel is a named pair of `Publisher` and `Subscriber`. Channels can be connected together by [processors](#processor). Registering a `Publisher` or `Subscriber` for a channel can be done by Messaging API, or configured implicitly using registered [connectors](#connectors) to generate the `Publisher` or `Subscriber`.

*Example of simple channel:*

``` java
Channel<String> channel1 = Channel.create("channel1");
Messaging.builder()
        .publisher(channel1, Multi.just("message 1", "message 2")
                .map(Message::of))
        .listener(channel1, s -> System.out.println("Intecepted message " + s))
        .build()
        .start();
```

### Processor

Processor is a typical reactive processor acting as a `Subscriber` to upstream and as a `Publisher` to downstream. In terms of reactive messaging, it is able to connect two [channels](#channel) to one reactive stream.

*Example of processor usage:*

``` java
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
```

### Message

Reactive Messaging in Helidon SE uses the same concept of message wrapping as MicroProfile messaging. The only notable difference is that SE Messaging does almost no implicit or automatic acknowledgement due to *no magic* philosophy of Helidon SE.

The only exception to this are the variants of the methods `Messaging.Builder#listener` and `Messaging.Builder#processor` configured with consumer or function parameters which will conveniently unwrap the payload for you. Once the payload is automatically unwrapped, it is not possible to do a manual acknowledgement, therefore an implicit acknowledgement is executed before the callback.

### Connectors

Connectors are used to connect [channels](#channel) to external sources. To make the [creation and usage of connectors](#messaging-connector) as easy and versatile as possible, Helidon SE Messaging uses the same API for connectors that [MicroProfile Reactive Messaging](../mp/reactivemessaging/introduction.md) does. This allows connectors to be used in both flavors of Helidon with one limitation which is that the connector has to be able to work without CDI.

Examples of versatile connectors in Helidon include the following:

- [Kafka connector](#kafka-connector)
- [JMS connector](#jms-connector)
- [AQ Connector](#aq-connector)

#### Messaging Connector

A connector for Reactive Messaging is a factory that produces Publishers and Subscribers for Channels in Reactive Messaging. Messaging connector is just an implementation of `IncomingConnectorFactory`, `OutgoingConnectorFactory` or both.

*Example connector `example-connector`:*

``` java
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
```

*Example of channel to connector mapping config:*

``` yaml
mp.messaging.outgoing.to-connector-channel.connector: example-connector
mp.messaging.incoming.from-connector-channel.connector: example-connector
```

*Example producing to connector:*

``` java
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
```

*Example consuming from connector:*

``` java
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
```

##### Configuration for Messaging Connector

A messaging connector in Helidon SE can be configured explicitly by API or implicitly by config following the notation of [MicroProfile Reactive Messaging](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-1.0/microprofile-reactive-messaging-spec.html#_configuration).

Configuration that is supplied to connector by the Messaging implementation must include two mandatory attributes:

- `channel-name` which is the name of the channel that has the connector configured as Publisher or Subscriber, or `Channel.create('name-of-channel')` in case of explicit configuration or `mp.messaging.incoming.name-of-channel.connector: connector-name` in case of implicit config
- `connector` name of the connector `@Connector("connector-name")`

*Example connector accessing configuration:*

``` java
@Connector("example-connector")
public class ExampleConnector implements IncomingConnectorFactory {

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(Config config) {

        String firstPropValue = config.getValue("first-test-prop", String.class); 
        String secondPropValue = config.getValue("second-test-prop", String.class);

        return ReactiveStreams.of(firstPropValue, secondPropValue)
                .map(Message::of);
    }
}
```

- Config context is merged from channel and connector contexts

###### Explicit Config for Messaging Connector

An explicit config for channel’s publisher is possible with `Channel.Builder#publisherConfig(Config config)` and for a subscriber with the `Channel.Builder#subscriberConfig(Config config)`. The supplied [Helidon Config](config/introduction.md) is merged with the mandatory attributes and any implicit configuration found. The resulting configuration is then served to the Connector.

*Example consuming from Kafka connector with explicit config:*

``` java
String kafkaServer = config.get("app.kafka.bootstrap.servers").asString().get();
String topic = config.get("app.kafka.topic").asString().get();

Channel<String> fromKafka = Channel.<String>builder()  
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

KafkaConnector kafkaConnector = KafkaConnector.create(); 

Messaging messaging = Messaging.builder()
        .connector(kafkaConnector)
        .listener(fromKafka, payload -> {
            System.out.println("Kafka says: " + payload);
        })
        .build()
        .start();
```

- Prepare channel for connecting kafka connector with specific publisher configuration → listener,
- Channel → connector mapping is automatic when using `KafkaConnector.configBuilder()`
- Prepare Kafka connector, can be used by any channel

###### Implicit Config for Messaging Connector

Implicit config without any hard-coding is possible with [Helidon Config](config/introduction.md) following notation of [MicroProfile Reactive Messaging](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-1.0/microprofile-reactive-messaging-spec.html#_configuration).

*Example of channel to connector mapping config with custom properties:*

``` yaml
mp.messaging.incoming.from-connector-channel.connector: example-connector
mp.messaging.incoming.from-connector-channel.first-test-prop: foo
mp.messaging.connector.example-connector.second-test-prop: bar
```

- Channel → Connector mapping
- Channel configuration properties
- Connector configuration properties

*Example consuming from connector:*

``` java
Messaging.builder()
        .config(config)
        .connector(new ExampleConnector())
        .listener(Channel.create("from-connector-channel"),
                  s -> System.out.println("Consuming: " + s))
        .build()
        .start();

// > Consuming: foo
// > Consuming: bar
```

#### Re-usability in MP Messaging

As the API is the same for [MicroProfile Reactive Messaging](../mp/reactivemessaging/introduction.md) connectors, all that is needed to make connector work in both ways is annotating it with `@ApplicationScoped`. Such connector is treated as a bean in Helidon MP.

For specific information about creating messaging connectors for Helidon MP visit [MicroProfile Reactive Messaging](../mp/reactivemessaging/introduction.md).

#### Kafka Connector

*Maven dependency*

``` xml
<dependency>
    <groupId>io.helidon.messaging.kafka</groupId>
    <artifactId>helidon-messaging-kafka</artifactId>
</dependency>
```

##### Reactive Kafka Connector

Connecting streams to Kafka with Reactive Messaging couldn’t be easier.

##### Explicit Config with Config Builder for Kafka Connector

*Example of consuming from Kafka:*

``` java
String kafkaServer = config.get("app.kafka.bootstrap.servers").asString().get();
String topic = config.get("app.kafka.topic").asString().get();

Channel<String> fromKafka = Channel.<String>builder() 
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

KafkaConnector kafkaConnector = KafkaConnector.create(); 
Messaging messaging = Messaging.builder()
        .connector(kafkaConnector)
        .listener(fromKafka, payload -> {
            System.out.println("Kafka says: " + payload);
        })
        .build()
        .start();
```

- Prepare a channel for connecting kafka connector with specific publisher configuration → listener
- Channel → connector mapping is automatic when using KafkaConnector.configBuilder()
- Prepare Kafka connector, can be used by any channel

*Example of producing to Kafka:*

``` java
String kafkaServer = config.get("app.kafka.bootstrap.servers").asString().get();
String topic = config.get("app.kafka.topic").asString().get();

Channel<String> toKafka = Channel.<String>builder()  
        .subscriberConfig(KafkaConnector.configBuilder()
                                  .bootstrapServers(kafkaServer)
                                  .topic(topic)
                                  .keySerializer(StringSerializer.class)
                                  .valueSerializer(StringSerializer.class)
                                  .build())
        .build();

KafkaConnector kafkaConnector = KafkaConnector.create(); 

Messaging messaging = Messaging.builder()
        .publisher(toKafka, Multi.just("test1", "test2").map(Message::of))
        .connector(kafkaConnector)
        .build()
        .start();
```

- Prepare a channel for connecting kafka connector with specific publisher configuration → listener
- Channel → connector mapping is automatic when using KafkaConnector.configBuilder()
- Prepare Kafka connector, can be used by any channel

##### Implicit Helidon Config for Kafka Connector

*Example of connector config:*

``` yaml
mp.messaging:

  incoming.from-kafka:
    connector: helidon-kafka
    topic: messaging-test-topic-1
    auto.offset.reset: latest 
    enable.auto.commit: true
    group.id: example-group-id

  outgoing.to-kafka:
    connector: helidon-kafka
    topic: messaging-test-topic-1

  connector:
    helidon-kafka:
      bootstrap.servers: localhost:9092 
      key.serializer: org.apache.kafka.common.serialization.StringSerializer
      value.serializer: org.apache.kafka.common.serialization.StringSerializer
      key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value.deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

- Kafka client consumer’s property auto.offset.reset configuration for `from-kafka` channel only
- Kafka client’s property [bootstrap.servers](https://kafka.apache.org/28/documentation.html#consumerconfigs_bootstrap.servers) configuration for all channels using the connector

*Example of consuming from Kafka:*

``` java
Channel<String> fromKafka = Channel.create("from-kafka");

KafkaConnector kafkaConnector = KafkaConnector.create(); 

Messaging messaging = Messaging.builder()
        .config(config)
        .connector(kafkaConnector)
        .listener(fromKafka, payload -> {
            System.out.println("Kafka says: " + payload);
        })
        .build()
        .start();
```

- Prepare Kafka connector, can be used by any channel

*Example of producing to Kafka:*

``` java
Channel<String> toKafka = Channel.create("to-kafka");

KafkaConnector kafkaConnector = KafkaConnector.create(); 

Messaging messaging = Messaging.builder()
        .config(config)
        .publisher(toKafka, Multi.just("test1", "test2").map(Message::of))
        .connector(kafkaConnector)
        .build()
        .start();
```

- Prepare Kafka connector, can be used by any channel

Don’t forget to check out the examples with pre-configured Kafka docker image, for easy testing:

- <https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/messaging>

#### JMS Connector

*Maven dependency*

``` xml
<dependency>
    <groupId>io.helidon.messaging.jms</groupId>
    <artifactId>helidon-messaging-jms</artifactId>
</dependency>
```

##### Reactive JMS Connector

Connecting streams to JMS with Reactive Messaging couldn’t be easier.

##### Explicit Config with Config Builder for JMS Connector

*Example of consuming from JMS:*

``` java
Channel<String> fromJms = Channel.<String>builder() 
        .name("from-jms")
        .publisherConfig(JmsConnector.configBuilder()
                                 .jndiInitialFactory(ActiveMQInitialContextFactory.class)
                                 .jndiProviderUrl("tcp://127.0.0.1:61616")
                                 .type(Type.QUEUE)
                                 .destination("se-example-queue-1")
                                 .build())
        .build();

JmsConnector jmsConnector = JmsConnector.create(); 

Messaging messaging = Messaging.builder()
        .connector(jmsConnector)
        .listener(fromJms, payload -> {
            System.out.println("Jms says: " + payload);
        })
        .build()
        .start();
```

- Prepare a channel for connecting jms connector with specific publisher configuration → listener
- Channel → connector mapping is automatic when using JmsConnector.configBuilder()
- Prepare JMS connector, can be used by any channel

*Example of producing to JMS:*

``` java
Channel<String> toJms = Channel.<String>builder()  
        .subscriberConfig(JmsConnector.configBuilder()
                                  .jndiInitialFactory(ActiveMQInitialContextFactory.class)
                                  .jndiProviderUrl("tcp://127.0.0.1:61616")
                                  .type(Type.QUEUE)
                                  .destination("se-example-queue-1")
                                  .build()
        ).build();

JmsConnector jmsConnector = JmsConnector.create(); 

Messaging messaging = Messaging.builder()
        .publisher(toJms, Multi.just("test1", "test2").map(Message::of))
        .connector(jmsConnector)
        .build()
        .start();
```

- Prepare a channel for connecting jms connector with specific publisher configuration → listener
- Channel → connector mapping is automatic when using JmsConnector.configBuilder()
- Prepare JMS connector, can be used by any channel

##### Implicit Helidon Config for JMS Connector

*Example of connector config:*

``` yaml
mp.messaging:

  incoming.from-jms:
    connector: helidon-jms
    destination: se-example-queue-1
    session-group-id: session-group-1
    type: queue

  outgoing.to-jms:
    connector: helidon-jms
    destination: se-example-queue-1
    type: queue

  connector:
    helidon-jms:
      jndi:
        jms-factory: ConnectionFactory
        env-properties:
          java.naming.factory.initial: org.apache.activemq.jndi.ActiveMQInitialContextFactory
          java.naming.provider.url: tcp://127.0.0.1:61616
```

*Example of consuming from JMS:*

``` java
Channel<String> fromJms = Channel.create("from-jms");

JmsConnector jmsConnector = JmsConnector.create(); 

Messaging messaging = Messaging.builder()
        .config(config)
        .connector(jmsConnector)
        .listener(fromJms, payload -> {
            System.out.println("Jms says: " + payload);
        })
        .build()
        .start();
```

- Prepare JMS connector, can be used by any channel

*Example of producing to JMS:*

``` java
Channel<String> toJms = Channel.create("to-jms");

JmsConnector jmsConnector = JmsConnector.create(); 

Messaging messaging = Messaging.builder()
        .config(config)
        .publisher(toJms, Multi.just("test1", "test2").map(Message::of))
        .connector(jmsConnector)
        .build()
        .start();
```

- Prepare JMS connector, can be used by any channel

Don’t forget to check out the examples with pre-configured ActiveMQ docker image, for easy testing:

- [Helidon Messaging Examples](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/messaging)

#### AQ Connector

*Maven dependency*

``` xml
<dependency>
    <groupId>io.helidon.messaging.aq</groupId>
    <artifactId>helidon-messaging-aq</artifactId>
</dependency>
```

##### Reactive Oracle AQ Connector

##### Sending and Receiving

*Example of producing to and consuming from Oracle AQ:*

``` java
PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource(); 
pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
pds.setURL(jdbcUrl);
pds.setUser("frank");
pds.setPassword("frank");
AqConnector connector = AqConnector.builder() 
        .dataSource("test-ds", pds)
        .build();
Channel<String> toAq = Channel.<String>builder() 
        .name("toAq")
        .subscriberConfig(AqConnector.configBuilder()
                                  .queue("example_queue_1")
                                  .dataSource("test-ds")
                                  .build())
        .build();

Channel<String> fromAq = Channel.<String>builder() 
        .name("fromAq")
        .publisherConfig(AqConnector.configBuilder()
                                 .queue("example_queue_1")
                                 .dataSource("test-ds")
                                 .build())
        .build();

Messaging.builder() 
        .connector(connector)
        .publisher(toAq,
                   Multi.just("Hello", "world", "from", "Oracle", "DB!")
                           .map(Message::of)) 
        .listener(fromAq, s -> System.out.println("Message received: " + s)) 
        .build()
        .start();
```

- Prepare Oracle UCP
- Setup AQ connector and provide datasource with an identifier `test-ds`
- Setup channel for sending messages to queue `example_queue_1` with datasource `test-ds`
- Setup channel for receiving messages from queue `example_queue_1` with datasource `test-ds`
- Register connector and channels
- Add a publisher for several test messages to publish them to `example_queue_1` immediately
- Subscribe callback for any message coming from `example_queue_1`

## Configuration

- [Configuration for Messaging Connector](#configuration-for-messaging-connector)
- [Explicit Configuration with Config Builder for Kafka Connector](#explicit-config-with-config-builder-for-kafka-connector)
- [Implicit Helidon Configuration for Kafka Connector](#implicit-helidon-config-for-kafka-connector)
- [Explicit Configuration with Config Builder for JMS Connector](#explicit-config-with-config-builder-for-jms-connector)
- [Implicit Helidon Configuration for JMS Connector](#implicit-helidon-config-for-jms-connector)

## Reference

- [MicroProfile Reactive Messaging Specification](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html)
- [MicroProfile Reactive Messaging on GitHub](https://github.com/eclipse/microprofile-reactive-messaging)
- [Helidon Messaging Examples](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/messaging)
