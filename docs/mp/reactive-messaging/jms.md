# JMS Connector

## Overview

Connecting streams to JMS with Reactive Messaging couldn’t be easier.

## Maven Coordinates

To enable JMS Connector, add the following dependency to your project’s
`pom.xml` (see [Managing Dependencies](../../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.messaging.jms</groupId>
  <artifactId>helidon-messaging-jms</artifactId>
</dependency>
```

## Configuration

Connector name: `helidon-jms`

### Configuration options

<!--@include ../../config/io.helidon.messaging.connectors.jms.JmsConfigBuilder.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-messa].
<!--/include-->


> [!TIP]
> Besides the configuration options above, custom attributes can be passed over
> configuration.

Custom Attributes Examples

| Attribute               | Description                                                                                                                                                                                 |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `jndi.destination`      | JNDI destination identifier.                                                                                                                                                                |
| `jndi.env-properties`   | Environment properties used for creating initial context `java.naming.factory.initial`, `java.naming.provider.url` ...                                                                      |
| `producer.someproperty` | property with producer prefix is set to producer instance (for example WLS Unit-of-Order `WLMessageProducer.setUnitOfOrder("unit-1")` can be configured as `producer.unit-of-order=unit-1`) |

### Configured JMS factory

The simplest possible usage is looking up JMS ConnectionFactory in the naming
context.

Example of connector config:

```yaml
mp.messaging:

  incoming.from-jms:
    connector: helidon-jms
    destination: messaging-test-queue-1
    type: queue

  outgoing.to-jms:
    connector: helidon-jms
    destination: messaging-test-queue-1
    type: queue

  connector:
    helidon-jms:
      user: Gandalf
      password: mellon
      jndi:
        jms-factory: ConnectionFactory
        env-properties:
          java.naming:
            factory.initial: org.apache.activemq.jndi.ActiveMQInitialContextFactory
            provider.url: tcp://localhost:61616
```

### Injected JMS factory

In case you need more advanced setup, connector can work with injected factory
instance.

```java [Inject]
@Produces
@ApplicationScoped
@Named("active-mq-factory")
public ConnectionFactory connectionFactory() {
    return new ActiveMQConnectionFactory(config.get("jms.url").asString().get());
}
```

```yaml [Config]
jms:
  url: tcp://127.0.0.1:61616

mp:
  messaging:
    connector:
      helidon-jms:
        named-factory: active-mq-factory

    outgoing.to-jms:
      connector: helidon-jms
      session-group-id: order-connection-1
      destination: TESTQUEUE
      type: queue

    incoming.from-jms:
      connector: helidon-jms
      session-group-id: order-connection-1
      destination: TESTQUEUE
      type: queue
```

## Usage

### Consuming

Consuming one by one unwrapped value:

```java
@Incoming("from-jms")
public void consumeJms(String msg) {
    System.out.println("JMS says: " + msg);
}
```

Consuming one by one, manual ack:

```java
@Incoming("from-jms")
@Acknowledgment(Acknowledgment.Strategy.MANUAL)
public CompletionStage<Void> consumeJms(JmsMessage<String> msg) {
    System.out.println("JMS says: " + msg.getPayload());
    return msg.ack();
}
```

### Producing

Example of producing to JMS:

```java
@Outgoing("to-jms")
public PublisherBuilder<String> produceToJms() {
    return ReactiveStreams.of("test1", "test2");
}
```

Example of more advanced producing to JMS:

```java
@Outgoing("to-jms")
public PublisherBuilder<Message<String>> produceToJms() {
    return ReactiveStreams.of("test1", "test2")
            .map(s -> JmsMessage.builder(s)
                    .correlationId(UUID.randomUUID().toString())
                    .property("stringProp", "cool property")
                    .property("byteProp", 4)
                    .property("intProp", 5)
                    .onAck(() -> CompletableFuture.completedStage(null)
                            .thenRun(() -> System.out.println("Acked!")))
                    .build());
}
```

Example of even more advanced producing to JMS with custom mapper:

```java
@Outgoing("to-jms")
public PublisherBuilder<Message<String>> produceToJms() {
    return ReactiveStreams.of("test1", "test2")
            .map(s -> JmsMessage.builder(s)
                    .customMapper((p, session) -> {
                        TextMessage textMessage = session.createTextMessage(p);
                        textMessage.setStringProperty("custom-mapped-property", "XXX" + p);
                        return textMessage;
                    })
                    .build()
            );
}
```

[io-helidon-messa]: ../../config/io.helidon.messaging.connectors.jms.JmsConfigBuilder.md#configuration-options
