# JMS Connector

## Overview

Connecting streams to JMS with Reactive Messaging couldn’t be easier.

## Maven Coordinates

To enable JMS Connector, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.messaging.jms</groupId>
    <artifactId>helidon-messaging-jms</artifactId>
</dependency>
```

## Configuration

Connector name: `helidon-jms`

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a4169a-acknowledge-mode"></span> [`acknowledge-mode`](../../config/io_helidon_messaging_connectors_jms_AcknowledgeMode.md) | `VALUE` | `i.h.m.c.j.AcknowledgeMode` | `AUTO_ACKNOWLEDGE` | JMS acknowledgement mode |
| <span id="a871ed-destination"></span> `destination` | `VALUE` | `String` |   | Queue or topic name |
| <span id="ad3b43-jndi-initial-context-properties"></span> `jndi-initial-context-properties` | `MAP` | `String` |   | Environment properties used for creating initial context java.naming.factory.initial, java.naming.provider.url |
| <span id="a0518f-jndi-initial-factory"></span> `jndi-initial-factory` | `VALUE` | `String` |   | JNDI initial factory |
| <span id="ac6c48-jndi-jms-factory"></span> `jndi-jms-factory` | `VALUE` | `String` |   | JNDI name of JMS factory |
| <span id="a9ed59-jndi-provider-url"></span> `jndi-provider-url` | `VALUE` | `String` |   | JNDI provider url |
| <span id="a81cf0-message-selector"></span> `message-selector` | `VALUE` | `String` |   | JMS API message selector expression based on a subset of the SQL92 |
| <span id="a7ace3-named-factory"></span> `named-factory` | `VALUE` | `String` |   | To select from manually configured `jakarta.jms.ConnectionFactory ConnectionFactories` over `JmsConnector.JmsConnectorBuilder#connectionFactory(String, jakarta.jms.ConnectionFactory) JmsConnectorBuilder#connectionFactory()` |
| <span id="a2d6e4-password"></span> `password` | `VALUE` | `String` |   | Password used for creating JMS connection |
| <span id="a1b10e-period-executions"></span> `period-executions` | `VALUE` | `Long` | `100` | Period for executing poll cycles in millis |
| <span id="ae0ce4-poll-timeout"></span> `poll-timeout` | `VALUE` | `Long` | `50` | Timeout for polling for next message in every poll cycle in millis |
| <span id="a08c22-queue"></span> `queue` | `VALUE` | `String` |   | Use supplied destination name and `Type#QUEUE QUEUE` as type |
| <span id="a0f3a9-session-group-id"></span> `session-group-id` | `VALUE` | `String` |   | When multiple channels share same session-group-id, they share same JMS session |
| <span id="ab90ce-topic"></span> `topic` | `VALUE` | `String` |   | Use supplied destination name and `Type#TOPIC TOPIC` as type |
| <span id="ac2dc3-transacted"></span> `transacted` | `VALUE` | `Boolean` | `false` | Indicates whether the session will use a local transaction |
| <span id="aef876-type"></span> [`type`](../../config/io_helidon_messaging_connectors_jms_Type.md) | `VALUE` | `i.h.m.c.j.Type` | `QUEUE` | Specify if connection is `Type#QUEUE queue` or `Type#TOPIC topic` |
| <span id="af0324-username"></span> `username` | `VALUE` | `String` |   | User name used for creating JMS connection |

> [!TIP]
> Besides the configuration options above, custom attributes can be passed over configuration.

|  |  |
|----|----|
| `jndi.destination` | JNDI destination identifier. |
| `jndi.env-properties` | Environment properties used for creating initial context `java.naming.factory.initial`, `java.naming.provider.url` …​ |
| `producer.someproperty` | property with producer prefix is set to producer instance (for example WLS Unit-of-Order `WLMessageProducer.setUnitOfOrder("unit-1")` can be configured as `producer.unit-of-order=unit-1`) |

Custom Attributes Examples

### Configured JMS factory

The simplest possible usage is looking up JMS ConnectionFactory in the naming context.

*Example of connector config:*

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

In case you need more advanced setup, connector can work with injected factory instance.

*Inject:*

```java
@Produces
@ApplicationScoped
@Named("active-mq-factory")
public ConnectionFactory connectionFactory() {
    return new ActiveMQConnectionFactory(config.get("jms.url").asString().get());
}
```

*Config:*

```yaml
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

*Consuming one by one unwrapped value:*

```java
@Incoming("from-jms")
public void consumeJms(String msg) {
    System.out.println("JMS says: " + msg);
}
```

*Consuming one by one, manual ack:*

```java
@Incoming("from-jms")
@Acknowledgment(Acknowledgment.Strategy.MANUAL)
public CompletionStage<Void> consumeJms(JmsMessage<String> msg) {
    System.out.println("JMS says: " + msg.getPayload());
    return msg.ack();
}
```

### Producing

*Example of producing to JMS:*

```java
@Outgoing("to-jms")
public PublisherBuilder<String> produceToJms() {
    return ReactiveStreams.of("test1", "test2");
}
```

*Example of more advanced producing to JMS:*

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

*Example of even more advanced producing to JMS with custom mapper:*

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
