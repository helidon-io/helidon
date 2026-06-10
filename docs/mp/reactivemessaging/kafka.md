# Kafka Connector

## Overview

Connecting streams to Kafka with Reactive Messaging is easy to do. There is a standard Kafka client behind the scenes, all the [producer][producer] and [consumer][consumer] configs can be propagated through messaging config.

## Maven Coordinates

To enable Reactive Kafka Connector, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../managing-dependencies.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.messaging.kafka</groupId>
  <artifactId>helidon-messaging-kafka</artifactId>
</dependency>
```

## Config

Example of connector config:

```yaml
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
- Kafka client’s property [bootstrap.servers][bootstrap-server] configuration for all channels using the connector

> [!TIP]
> Besides the following configuration options, any property from [consumer][consumer-2] or [producer][producer-2] configuration can be passed to the underlying Kafka client.

### Configuration options

<!--@include ../../config/io.helidon.messaging.connectors.kafka.KafkaConfigBuilder.md#configuration-options offset=1 -->
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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>value-deserializer</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Class</code>
</td>
<td>Deserializer class for value that implements the <code>org.apache.kafka.common.serialization.Deserializer</code> interface</td>
</tr>
<tr>
<td>
<code>group-id</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>A unique string that identifies the consumer group this consumer belongs to</td>
</tr>
<tr>
<td>
<a id="auto-offset-reset"></a>
<a href="io.helidon.messaging.connectors.kafka.KafkaConfigBuilder.AutoOffsetReset.md">
<code>auto-offset-reset</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="AutoOffsetReset">AutoOffsetReset</code>
</td>
<td>What to do when there is no initial offset in Kafka or if the current offset does not exist any more on the server (e.g</td>
</tr>
<tr>
<td>
<code>topic-pattern</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Pattern</code>
</td>
<td>Pattern for topic names to consume from</td>
</tr>
<tr>
<td>
<code>acks</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>The number of acknowledgments the producer requires the leader to have received before considering a request complete</td>
</tr>
<tr>
<td>
<code>bootstrap-servers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>A list of host/port pairs to use for establishing the initial connection to the Kafka cluster</td>
</tr>
<tr>
<td>
<code>key-serializer</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Class</code>
</td>
<td>Serializer class for key that implements the <code>org.apache.kafka.common.serialization.Serializer</code> interface</td>
</tr>
<tr>
<td>
<code>enable-auto-commit</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>If true the consumer's offset will be periodically committed in the background</td>
</tr>
<tr>
<td>
<code>batch-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>The producer will attempt to batch records together into fewer requests whenever multiple records are being sent to the same partition</td>
</tr>
<tr>
<td>
<code>dlq-topic</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Names of the "dead letter queue" topics to be used in case message is nacked</td>
</tr>
<tr>
<td>
<code>retries</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>Setting a value greater than zero will cause the client to resend any record whose send fails with a potentially transient error</td>
</tr>
<tr>
<td>
<code>buffer-memory</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>The total bytes of memory the producer can use to buffer records waiting to be sent to the server</td>
</tr>
<tr>
<td>
<code>poll-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>The maximum time to block polling loop in milliseconds</td>
</tr>
<tr>
<td>
<code>compression-type</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>The compression type for all data generated by the producer</td>
</tr>
<tr>
<td>
<code>topic</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Names of the topics to consume from</td>
</tr>
<tr>
<td>
<code>value-serializer</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Class</code>
</td>
<td>Serializer class for value that implements the <code>org.apache.kafka.common.serialization.Serializer</code> interface</td>
</tr>
<tr>
<td>
<code>period-executions</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>Period between successive executions of polling loop</td>
</tr>
<tr>
<td>
<code>key-deserializer</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Class</code>
</td>
<td>Deserializer class for key that implements the <code>org.apache.kafka.common.serialization.Deserializer</code> interface</td>
</tr>
</tbody>
</table>
<!--/include-->


## Consuming Messages

Example of consuming from Kafka:

```java
@Incoming("from-kafka")
public void consumeKafka(String msg) {
    System.out.println("Kafka says: " + msg);
}
```

## Producing Messages

Example of producing to Kafka:

```java
@Outgoing("to-kafka")
public PublisherBuilder<String> produceToKafka() {
    return ReactiveStreams.of("test1", "test2");
}
```

## NACK Strategy

|  |  |
|----|----|
| Strategy | Description |
| Kill channel | Nacked message sends error signal and causes channel failure so Messaging Health check can report it as DOWN |
| DLQ | Nacked messages are sent to specified dead-letter-queue |
| Log only | Nacked message is logged and channel continues normally |

### Kill channel

Default NACK strategy for Kafka connector. When

### Dead Letter Queue

Sends nacked messages to error topic, [DLQ][dlq] is well known pattern for dealing with unprocessed messages.

Helidon can derive connection settings for DLQ topic automatically if the error topic is present on the same Kafka cluster. Serializers are derived from deserializers used for consumption `org.apache.kafka.common.serialization.StringDeserializer` \> `org.apache.kafka.common.serialization.StringSerializer`. Note that the name of the error topic is needed only in this case.

Example of derived DLQ config:

```yaml
mp.messaging:
  incoming:
    my-channel:
      nack-dlq: dql_topic_name
```

If a custom connection is needed, then use the 'nack-dlq' key for all of the producer configuration.

Example of custom DLQ config:

```yaml
mp.messaging:
  incoming:
    my-channel:
      nack-dlq:
        topic: dql_topic_name
        bootstrap.servers: localhost:9092
        key.serializer: org.apache.kafka.common.serialization.StringSerializer
        value.serializer: org.apache.kafka.common.serialization.StringSerializer
```

### Log only

Only logs nacked messages and throws them away, offset is committed and channel continues normally consuming subsequent messages.

Example of log only enabled nack strategy:

```yaml
mp.messaging:
  incoming:
    my-channel:
      nack-log-only: true
```

## Examples

Don’t forget to check out the examples with pre-configured Kafka docker image, for easy testing:

- <https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/messaging>

[producer]: https://kafka.apache.org/28/documentation.html#producerconfigs
[consumer]: https://kafka.apache.org/28/documentation.html#consumerconfigs
[bootstrap-server]: https://kafka.apache.org/28/documentation.html#consumerconfigs_bootstrap.servers
[consumer-2]: https://kafka.apache.org/documentation/#consumerconfigs
[producer-2]: https://kafka.apache.org/documentation/#producerconfigs
[dlq]: https://en.wikipedia.org/wiki/Dead_letter_queue
