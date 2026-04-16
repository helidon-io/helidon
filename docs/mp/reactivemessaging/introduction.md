# Reactive Messaging MP

## Overview

Reactive messaging offers a new way of processing messages that is different from the older method of using message-driven beans. One significant difference is that blocking is no longer the only way to apply backpressure to the message source.

Reactive messaging uses reactive streams as message channels so you can construct very effective pipelines for working with the messages or, if you prefer, you can continue to use older messaging methods. Like the message-driven beans, [MicroProfile Reactive Messaging](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html) uses CDI beans to produce, consume or process messages over Reactive Streams. These messaging beans are expected to be either `ApplicationScoped` or `Dependent` scoped. Messages are managed by methods annotated by `@Incoming` and `@Outgoing` and the invocation is always driven by message core - either at assembly time, or for every message coming from the stream.

## Maven Coordinates

To enable MicroProfile Reactive Messaging, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
   <groupId>io.helidon.microprofile.messaging</groupId>
   <artifactId>helidon-microprofile-messaging</artifactId>
</dependency>
```

To include health checks for Messaging add the following dependency:

``` xml
<dependency>
   <groupId>io.helidon.microprofile.messaging</groupId>
   <artifactId>helidon-microprofile-messaging-health</artifactId>
</dependency>
```

## Usage

- [Channels](#channels)
- [Emitter](#emitter)
- [Connector](#connector)
- [Message](#message)
- [Acknowledgement](#acknowledgement)
- [Health Check](#health-check)

### Channels

Reactive messaging uses named channels to connect one source (upstream) with one consumer (downstream). Each channel needs to have both ends connected otherwise the container cannot successfully start.

<figure>
<img src="../../images/msg/channel.svg" alt="Messaging Channel" />
</figure>

Channels can be connected either to [emitter](#emitter) (1), [producing method](#producing-method) (2) or [connector](#connector) (3) on the upstream side. And [injected publisher](#injected-publisher) (4), [consuming method](#consuming-method) (5) or [connector](#connector) (6) on the downstream.

#### Consuming Method

Consuming methods can be connected to the channel’s downstream to consume the message coming through the channel. The incoming annotation has one required attribute `value` that defines the channel name.

Consuming method can function in two ways:

- consume every message coming from the stream connected to the [channels](#channels) - invoked per each message
- prepare reactive stream’s subscriber and connect it to the channel - invoked only once during the channel construction

*Example consuming every message from channel `example-channel-2`:*

``` java
@Incoming("example-channel-2")
public void printMessage(String msg) {
    System.out.println("Just received message: " + msg);
}
```

*Example preparing reactive stream subscriber for channel `example-channel-1`:*

``` java
@Incoming("example-channel-2")
public Subscriber<String> printMessage() {
    return ReactiveStreams.<String>builder()
            .forEach(msg -> System.out.println("Just received message: " + msg))
            .build();
}
```

#### Injected Publisher

Directly injected publisher can be connected as a channel downstream, you can consume the data from the channel by subscribing to it.

Helidon can inject following types of publishers:

- `Publisher<PAYLOAD>` - Reactive streams publisher with unwrapped payload
- `Publisher<Message<PAYLOAD>>` - Reactive streams publisher with whole message
- `PublisherBuilder<PAYLOAD>` - MP Reactive streams operators publisher builder with unwrapped payload
- `PublisherBuilder<Message<PAYLOAD>>` - MP Reactive streams operators publisher builder with whole message
- `Flow.Publisher<PAYLOAD>` - JDK’s flow publisher with unwrapped payload
- `Flow.Publisher<Message<PAYLOAD>>` - JDK’s flow publisher with whole message
- `Multi<PAYLOAD>` - Helidon flow reactive operators with unwrapped payload
- `Multi<Message<PAYLOAD>>` - Helidon flow reactive operators with whole message

*Example of consuming payloads from channel `example-channel-1` with injected publisher:*

``` java
@Inject
public MyBean(@Channel("example-channel-1") Multi<String> multiChannel) {
    multiChannel
            .map(String::toUpperCase)
            .forEach(s -> System.out.println("Received " + s));
}
```

#### Producing Method

The annotation has one required attribute `value` that defines the [channel](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html#_channel) name.

The annotated messaging method can function in two ways:

- produce exactly one message to the stream connected to the [channel](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html#_channel)
- prepare reactive stream’s publisher and connect it to the [channel](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html#_channel)

*Example producing exactly one message to channel `example-channel-1`:*

``` java
@Outgoing("example-channel-1")
public String produceMessage() {
    return "foo";
}
```

*Example preparing reactive stream publisher publishing three messages to the channel `example-channel-1`:*

``` java
@Outgoing("example-channel-1")
public Publisher<String> printMessage() {
    return ReactiveStreams.of("foo", "bar", "baz").buildRs();
}
```

> [!WARNING]
> Messaging methods are not meant to be invoked directly!

### Emitter

To send messages from imperative code, you can inject a special channel source called an emitter. Emitter can serve only as an upstream, source of the messages, for messaging channel.

*Example of sending message from JAX-RS method to channel `example-channel-1`*

``` java
@Inject
@Channel("example-channel-1")
private Emitter<String> emitter;

@PUT
@Path("/sendMessage")
@Consumes(MediaType.TEXT_PLAIN)
public Response sendMessage(final String payload) {
    emitter.send(payload);
    return Response.ok().build();
}
```

Emitters, as a source of messages for reactive channels, need to address possible backpressure from the downstream side of the channel. In case there is not enough demand from the downstream, you can configure a buffer size strategy using the `@OnOverflow` annotation. Additional overflow strategies are described below.

|  |  |
|----|----|
| Strategy | Description |
| BUFFER | Buffer unconsumed values until configured bufferSize is reached, when reached calling `Emitter.emit` throws `IllegalStateException`. Buffer size can be configured with `@OnOverflow` or with config key `mp.messaging.emitter.default-buffer-size`. Default value is `128`. |
| UNBOUNDED_BUFFER | Buffer unconsumed values until application runs out of memory. |
| THROW_EXCEPTION | Calling `Emitter.emit` throws `IllegalStateException` if there is not enough items requested by downstream. |
| DROP | If there is not enough items requested by downstream, emitted message is silently dropped. |
| FAIL | If there is not enough items requested by downstream, emitting message causes error signal being send to downstream. Whole channel is terminated. No other messages can be sent. |
| LATEST | Keeps only the latest item. Any previous unconsumed message is silently dropped. |
| NONE | Messages are sent to downstream even if there is no demand. Backpressure is effectively ignored. |

Overflow strategies

#### Processing Method

Such [methods](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html#_method_consuming_and_producing) acts as processors, consuming messages from one channel and producing to another.

<figure>
<img src="../../images/msg/processor.svg" alt="Processor method connecting two channels together" />
</figure>

Diagram shows how processing method (2) serves as a downstream to the `my-channel` (1) and an upstream to the `other-channel` (3), connecting them together.

Processing method can function in multiple ways:

- process every message
- prepare reactive stream’s processor and connect it between the channels
- on every message prepare new publisher(equivalent to `flatMap` operator)

*Example processing every message from channel `example-channel-1` to channel `example-channel-2`:*

``` java
@Incoming("example-channel-1")
@Outgoing("example-channel-2")
public String processMessage(String msg) {
    return msg.toUpperCase();
}
```

*Example preparing processor stream to be connected between channels `example-channel-1` and `example-channel-2`:*

``` java
@Incoming("example-channel-1")
@Outgoing("example-channel-2")
public Processor<String, String> processMessage() {
    return ReactiveStreams.<String>builder()
            .map(String::toUpperCase)
            .buildRs();
}
```

*Example processing every message from channel `example-channel-1` as stream to be flattened to channel `example-channel-2`:*

``` java
@Incoming("example-channel-1")
@Outgoing("example-channel-2")
public Publisher<String> processMessage(String msg) {
    return ReactiveStreams.of(msg.toUpperCase(), msg.toLowerCase()).buildRs();
}
```

### Connector

Messaging connector is an application-scoped bean that implements one or both of following interfaces:

- `IncomingConnectorFactory` - connector can create an upstream publisher to produce messages to a channel
- `OutgoingConnectorFactory` - connector can create a downstream subscriber to consume messages from a channel

*Example connector `example-connector`:*

``` java
@ApplicationScoped
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

### Message

The Reactive Messaging [Message](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html#_message) class can be used to wrap or unwrap data items between methods and connectors. The message wrapping and unwrapping can be performed explicitly by using `org.eclipse.microprofile.reactive.messaging.Message#of(T)` or implicitly through the messaging core.

*Example of explicit and implicit wrapping and unwrapping*

``` java
@Outgoing("publisher-payload")
public PublisherBuilder<Integer> streamOfMessages() {
    return ReactiveStreams.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
}

@Incoming("publisher-payload")
@Outgoing("wrapped-message")
public Message<String> rewrapMessageManually(Message<Integer> message) {
    return Message.of(Integer.toString(message.getPayload()));
}

@Incoming("wrapped-message")
public void consumeImplicitlyUnwrappedMessage(String value) {
    System.out.println("Consuming message: " + value);
}
```

### Acknowledgement

Messages carry a callback for reception acknowledgement (ack) and negative acknowledgement (nack). An acknowledgement in messaging methods is possible manually by `org.eclipse.microprofile.reactive.messaging.Message#ack` or automatically according explicit or implicit acknowledgement strategy by the messaging core. Explicit strategy configuration is possible with `@Acknowledgment` annotation which has one required attribute `value` that expects the strategy type from enum `org.eclipse.microprofile.reactive.messaging.Acknowledgment.Strategy`. More information about supported signatures and implicit automatic acknowledgement can be found in specification [Message acknowledgement](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html#_message_acknowledgement).

|  |  |
|----|----|
| `@Acknowledgment(Acknowledgment.Strategy.NONE)` | No acknowledgment |
| `@Acknowledgment(Acknowledgment.Strategy.MANUAL)` | No automatic acknowledgment |
| `@Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)` | Ack automatically before method invocation or processing |
| `@Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)` | Ack automatically after method invocation or processing |

Acknowledgement strategies {#terms}

*Example of manual acknowledgment*

``` java
@Outgoing("consume-and-ack")
public Publisher<Message<String>> streamOfMessages() {
    return ReactiveStreams.of(Message.of("This is Payload", () -> {
        System.out.println("This particular message was acked!");
        return CompletableFuture.completedFuture(null);
    })).buildRs();
}

@Incoming("consume-and-ack")
@Acknowledgment(Acknowledgment.Strategy.MANUAL)
public CompletionStage<Void> receiveAndAckMessage(Message<String> msg) {
    return msg.ack(); 
}
```

- Calling ack() will print "This particular message was acked!" to System.out

*Example of manual acknowledgment*

``` java
    @Outgoing("consume-and-ack")
    public Publisher<Message<String>> streamOfMessages() {
        return ReactiveStreams.of(Message.of("This is Payload", () -> {
            System.out.println("This particular message was acked!");
            return CompletableFuture.completedFuture(null);
        })).buildRs();
    }

    @Incoming("consume-and-ack")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public CompletionStage<Void> receiveAndAckMessage(Message<String> msg) {
        return msg.ack(); 
    }
}
```

- Calling ack() will print "This particular message was acked!" to System.out

*Example of explicit pre-process acknowledgment*

``` java
@Outgoing("consume-and-ack")
public Publisher<Message<String>> streamOfMessages() {
    return ReactiveStreams.of(Message.of("This is Payload", () -> {
        System.out.println("This particular message was acked!");
        return CompletableFuture.completedFuture(null);
    })).buildRs();
}

/**
 * Prints to the console:
 * > This particular message was acked!
 * > Method invocation!
 */
@Incoming("consume-and-ack")
@Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
public CompletionStage<Void> receiveAndAckMessage(Message<String> msg) {
    System.out.println("Method invocation!");
    return CompletableFuture.completedFuture(null);
}
```

*Example of explicit post-process acknowledgment*

``` java
@Outgoing("consume-and-ack")
public Publisher<Message<String>> streamOfMessages() {
    return ReactiveStreams.of(Message.of("This is Payload", () -> {
        System.out.println("This particular message was acked!");
        return CompletableFuture.completedFuture(null);
    })).buildRs();
}

/**
 * Prints to the console:
 * > Method invocation!
 * > This particular message was acked!
 */
@Incoming("consume-and-ack")
@Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
public CompletionStage<Void> receiveAndAckMessage(Message<String> msg) {
    System.out.println("Method invocation!");
    return CompletableFuture.completedFuture(null);
}
```

### Health Check

Messaging in Helidon has built in health probes for liveness and readiness. To activate it add the [health check dependency](#maven-coordinates).

- Liveness - channel is considered UP until `cancel` or `onError` signal is intercepted on it.
- Readiness - channel is considered DOWN until `onSubscribe` signal is intercepted on it.

If you check your health endpoints `/health/live` and `/health/ready` you will discover every messaging channel to have its own probe.

``` json
{
    "name": "messaging",
    "state": "UP",
    "status": "UP",
    "data": {
        "my-channel-1": "UP",
        "my-channel-2": "UP"
    }
}
```

> [!CAUTION]
> Due to the nack support are exceptions thrown in messaging methods NOT translated to error and cancel signals implicitly anymore

## Configuration

The channel must be configured to use connector as its upstream or downstream.

*Example of channel to connector mapping config:*

``` yaml
mp.messaging.outgoing.to-connector-channel.connector: example-connector 
mp.messaging.incoming.from-connector-channel.connector: example-connector 
```

- Use connector `example-connector` as a downstream for channel `to-connector-channel` to consume the messages from the channel
- Use connector `example-connector` as an upstream for channel `to-connector-channel` to produce messages to the channel

*Example producing to connector:*

``` java
@Outgoing("to-connector-channel")
public Publisher<String> produce() {
    return ReactiveStreams.of("fee", "fie").buildRs();
}

// > Connector says: fee
// > Connector says: fie
```

*Example consuming from connector:*

``` java
@Incoming("from-connector-channel")
public void consume(String value) {
    System.out.println("Consuming: " + value);
}

// >Consuming:foo
// >Consuming:bar
```

When the connector constructs a publisher or subscriber for a given channel, it can access general connector configuration and channel-specific properties merged together with special synthetic property `channel-name`.

<figure>
<img src="../../images/msg/connector-config.svg" alt="Connector config" />
</figure>

Connector specific config (1) merged together with global connector config (2).

*Example connector accessing configuration:*

``` java
@ApplicationScoped
@Connector("example-connector")
public class ExampleConnector implements IncomingConnectorFactory {

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(final Config config) {

        String firstPropValue = config.getValue("channel-specific-prop", String.class); 
        String secondPropValue = config.getValue("connector-specific-prop", String.class);
        String channelName = config.getValue("channel-name", String.class); 

        return ReactiveStreams.of(firstPropValue, secondPropValue)
                .map(Message::of);
    }
}
```

- Config context is merged from channel and connector contexts
- Name of the channel requesting publisher as it’s upstream from this connector

*Example of channel to connector mapping config with custom properties:*

``` yaml
mp.messaging.incoming.from-connector-channel.connector: example-connector
mp.messaging.incoming.from-connector-channel.channel-specific-prop: foo
mp.messaging.connector.example-connector.connector-specific-prop: bar
```

- Channel → Connector mapping
- Channel configuration properties
- Connector configuration properties

*Example consuming from connector:*

``` java
@Incoming("from-connector-channel")
public void consume(String value) {
    System.out.println("Consuming: " + value);
}

// > Consuming: foo
// > Consuming: bar
```

## Reference

- [Helidon MicroProfile Reactive Messaging](https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.messaging/module-summary.html)
- [MicroProfile Reactive Messaging Specification](https://download.eclipse.org/microprofile/microprofile-reactive-messaging-3.0/microprofile-reactive-messaging-spec-3.0.html)
- [MicroProfile Reactive Messaging on GitHub](https://github.com/eclipse/microprofile-reactive-messaging)
