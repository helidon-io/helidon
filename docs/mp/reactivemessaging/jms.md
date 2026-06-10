# JMS Connector

## Overview

Connecting streams to JMS with Reactive Messaging couldn’t be easier.

## Maven Coordinates

To enable JMS Connector, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../managing-dependencies.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.messaging.jms</groupId>
  <artifactId>helidon-messaging-jms</artifactId>
</dependency>
```

## Configuration

Connector name: `helidon-jms`

### Configuration options

<!--@include ../../config/io.helidon.messaging.connectors.jms.JmsConfigBuilder.md#configuration-options offset=1 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>message-selector</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>JMS API message selector expression based on a subset of the SQL92</td>
</tr>
<tr>
<td>
<code>named-factory</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>To select from manually configured <code>jakarta.jms.ConnectionFactory ConnectionFactories</code> over <code>JmsConnector.JmsConnectorBuilder#connectionFactory(String, jakarta.jms.ConnectionFactory) JmsConnectorBuilder#connectionFactory()</code></td>
</tr>
<tr>
<td>
<code>destination</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Queue or topic name</td>
</tr>
<tr>
<td>
<code>transacted</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Indicates whether the session will use a local transaction</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.messaging.connectors.jms.Type.md">
<code>type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Type</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">QUEUE</code>
</td>
<td>Specify if connection is <code>Type#QUEUE queue</code>  or <code>Type#TOPIC topic</code></td>
</tr>
<tr>
<td>
<code>jndi-initial-factory</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>JNDI initial factory</td>
</tr>
<tr>
<td>
<code>password</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Password used for creating JMS connection</td>
</tr>
<tr>
<td>
<code>poll-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">50</code>
</td>
<td>Timeout for polling for next message in every poll cycle in millis</td>
</tr>
<tr>
<td>
<code>jndi-jms-factory</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>JNDI name of JMS factory</td>
</tr>
<tr>
<td>
<code>topic</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use supplied destination name and <code>Type#TOPIC TOPIC</code> as type</td>
</tr>
<tr>
<td>
<a id="acknowledge-mode"></a>
<a href="io.helidon.messaging.connectors.jms.AcknowledgeMode.md">
<code>acknowledge-mode</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="AcknowledgeMode">AcknowledgeMode</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="AUTO_ACKNOWLEDGE">AUTO_ACKNOWLEDGE</code>
</td>
<td>JMS acknowledgement mode</td>
</tr>
<tr>
<td>
<code>jndi-initial-context-properties</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Environment properties used for creating initial context java.naming.factory.initial, java.naming.provider.url</td>
</tr>
<tr>
<td>
<code>jndi-provider-url</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>JNDI provider url</td>
</tr>
<tr>
<td>
<code>period-executions</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">100</code>
</td>
<td>Period for executing poll cycles in millis</td>
</tr>
<tr>
<td>
<code>queue</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use supplied destination name and <code>Type#QUEUE QUEUE</code> as type</td>
</tr>
<tr>
<td>
<code>session-group-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>When multiple channels share same session-group-id, they share same JMS session</td>
</tr>
<tr>
<td>
<code>username</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>User name used for creating JMS connection</td>
</tr>
</tbody>
</table>
<!--/include-->


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

In case you need more advanced setup, connector can work with injected factory instance.

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
