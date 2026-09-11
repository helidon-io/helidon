# Helidon Messaging

Helidon Messaging provides typed logical channels, immutable message envelopes, bounded execution, and a transport
connector SPI. Applications can assemble the channel graph declaratively with Helidon Service Registry code generation
or imperatively with `MessagingGraph`.

The default delivery and settlement contract is synchronous and at least once. For each delivery, required outputs are
invoked sequentially, and outputs completed before a later failure are not rolled back. Handlers and other
side-effecting code must therefore tolerate duplicate delivery. The `DROP` failure disposition explicitly opts into
discarding an exhausted incoming delivery.

The messaging API is a Preview feature.

## Declarative API

The declarative API builds a messaging graph from Service Registry services, annotated methods, named emitters, and
connector configuration.

### Dependencies and code generation

Add the messaging runtime. Its version is normally managed by the Helidon application parent or BOM. The processor
and plugin examples below use `${helidon.version}`; define that property when it is not inherited from a Helidon
application parent. Add the configuration parser used by the application as a regular dependency:

```xml
<dependencies>
    <dependency>
        <groupId>io.helidon.messaging</groupId>
        <artifactId>helidon-messaging</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.config</groupId>
        <artifactId>helidon-config-yaml</artifactId>
    </dependency>
</dependencies>
```

Concrete Kafka, JMS, and Pulsar connector implementations are in the
[Helidon Extensions repository](https://github.com/helidon-io/helidon-extensions) and are consumed separately once
they are built against this core API. Their artifacts and connector-specific configuration documentation are versioned
there. The core runtime discovers connector providers through the Service Registry.

Configure the Helidon annotation-processor bundle on the compiler annotation processor path. The bundle includes the
Service Registry and declarative code generators, including messaging:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.helidon.bundles</groupId>
                <artifactId>helidon-bundles-apt</artifactId>
                <version>${helidon.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

To generate the `ApplicationBinding` used below, also run the Service Registry application generator:

```xml
<plugin>
    <groupId>io.helidon.service</groupId>
    <artifactId>helidon-service-maven-plugin</artifactId>
    <version>${helidon.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>create-application</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

JPMS applications require the runtime module, each selected configuration parser module, and the selected connector
modules. For example, an application using YAML requires:

```java
requires io.helidon.config.yaml;
requires io.helidon.messaging;
```

Add the connector module required by the chosen extension. Keep the YAML dependency on the compile path because
generated `ApplicationBinding` code references its parser. The code generator is a build-time dependency and does not
need a `requires` directive.

### Start the application

Generate an application binding and start the Service Registry. The registry discovers the generated messaging
registrations and connector providers, validates the complete topology, and starts the graph:

```java
@Service.GenerateBinding
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        ServiceRegistryManager.start(ApplicationBinding.create());
    }
}
```

### Receive and process messages

A receiving method must belong to a concrete Service Registry service:

```java
@Service.Singleton
final class OrderHandler {
    @Messaging.ReceiveFrom("orders")
    void receive(Order order) {
        // Process one order.
    }
}
```

The primary method parameter can expose one of three views of a delivery:

| Parameter | Invocation |
| --- | --- |
| `T` | Once for each message, with its payload. |
| `Message<T>` | Once for each message, with its immutable envelope and portable headers. |
| `MessageBatch<T>` | Once for the complete, ordered delivery batch. |

Connector-specific immutable `Message` subtypes, such as the Kafka and JMS message types, can also be declared for
messages originating from that connector. A default message emitted locally does not satisfy a handler that requires
one of those subtypes. In a multi-parameter method, a payload is identified with `@Messaging.Entity`. This explicit
marker selects the payload view even when the parameter type implements `Message`; only an unannotated `Message<T>` or
connector-specific subtype selects the envelope view. Other parameters use `@Messaging.HeaderParam`. Header names are
exact and case-sensitive:

| Parameter type | Selected value | When absent |
| --- | --- | --- |
| `String` | Last value, which must be `MessageHeaderValue.TextValue` | Delivery fails |
| `Optional<String>` | Last value, which must be text when present | `Optional.empty()` |
| `MessageHeaderValue` | Last value of any kind | Delivery fails |
| `Optional<MessageHeaderValue>` | Last value of any kind | `Optional.empty()` |
| `List<MessageHeaderValue>` | Immutable list of all matching values in message-entry order | Empty list |

An explicit `MessageHeaderValue.NullValue` is present data, including as
`Optional.of(MessageHeaderValue.NullValue.create())`. Header values are never converted automatically. A handler can
declare at most one header parameter for each exact name:

```java
@Messaging.ReceiveFrom("orders")
void receive(@Messaging.Entity Order order,
             @Messaging.HeaderParam("tenant") String tenant,
             @Messaging.HeaderParam("trace-id") Optional<String> traceId,
             @Messaging.HeaderParam("attempt") MessageHeaderValue attempt,
             @Messaging.HeaderParam("routing") Optional<MessageHeaderValue> routing,
             @Messaging.HeaderParam("tag") List<MessageHeaderValue> tags) {
    // Process one order and its selected headers.
}
```

Use `@Messaging.SendTo` for a synchronous one-to-one processor:

```java
@Messaging.ReceiveFrom("orders")
@Messaging.SendTo("validated-orders")
Message<Order> validate(Message<Order> incoming) {
    return Message.builder(validate(incoming.entity()))
            .header("trace-id", incoming.header("trace-id").orElse("unknown"))
            .build();
}
```

A processor can return a payload or a `Message<T>`. A payload is wrapped in a new message without the input headers;
return a message envelope when headers must be retained or changed. A return type that implements `Message` is always
treated as an envelope; wrap it in an outer `Message<P>` to produce a message-valued payload `P`. Terminal receivers
and batch handlers return `void`, and a batch handler cannot use `@Messaging.SendTo`. Asynchronous return types and
reactive publishers are not supported.

Several services can receive from the same channel. Each receiver is a required output, so the delivery succeeds only
after all of them succeed. One service cannot declare two receivers for the same channel.

### Create messages and batches

`Message<T>` contains a required, non-null payload and immutable, ordered portable headers:

```java
Message<Order> message = Message.builder(order)
        .header("trace-id", traceId)
        .header("tenant", tenant)
        .addHeader("tag", "first")
        .addHeader("tag", "second")
        .header("attempt", MessageHeaderValue.IntegerValue.create(3))
        .build();
```

`Message.builder()` and `Message.builder(payload)` return the generated `MessageConfig.Builder<T>`.
Use `build()` to create an immutable message, or `buildPrototype()` to retain its construction options as a
`MessageConfig<T>` snapshot. Its `headers()` and `localMetadata()` accessors expose an immutable list and map,
respectively. The generated builder accumulates these collections without copying them on each entry update;
snapshots are taken when building. The payload is retained by reference; header and local-metadata changes on the
builder do not affect previously built messages or snapshots. Connector-specific implementations can continue to
implement `Message<T>` directly.

Whole-value `headers(...)` and `localMetadata(...)` setters replace the respective collection, including overloads
accepting `MessageHeaders`, `MessageMetadata`, or their builder callbacks and suppliers. `from(...)` follows the
standard generated collection semantics: existing explicit header entries are retained and incoming entries are
appended; metadata maps are merged with incoming values replacing matching keys. When copying another builder,
its untouched collection defaults do not overwrite explicitly modified destination collections. Use a fresh builder
such as `MessageConfig.builder(snapshot)` for an independent copy of a prototype.

Use `Message.create(order)` when no headers are needed. `header` replaces all values with the same exact,
case-sensitive name, while `addHeader` appends a duplicate-preserving entry. `MessageHeaders.entries()` is the
authoritative globally ordered representation. Explicit `first`, `last`, and `all` lookups avoid imposing one
transport's duplicate semantics on another; `valuesByName()` is only a derived grouped view and loses cross-name
ordering. Name lookups share a lazily built immutable index; forwarding or iterating the ordered entries does not
build it. The closed `MessageHeaderValue` model supports null, text, immutable binary, boolean, integer, decimal,
32/64-bit floating point, timestamp, UUID, and opaque connector-encoded values. `Message.header(name)` remains a
last-valued text convenience and never stringifies a typed value. `MessageHeader.create(name, value)` accepts the
corresponding Java types directly, including boxed integral and floating-point values; use the `MessageHeaderValue`
overload for explicit null and opaque connector-encoded values.

`localMetadata()` is a separate, immutable, exact-name map for values that follow the message envelope only inside the
current process. It uses the same closed `MessageHeaderValue` value model, but it is single-valued and is never exposed through
`@Messaging.HeaderParam` or generically mapped by an outgoing connector:

```java
Message<Order> local = Message.builder(order)
        .localMetadata("application.processing.started", MessageHeaderValue.TimestampValue.create(Instant.now()))
        .build();
```

A new message starts with empty local metadata unless its builder or implementation explicitly copies a snapshot. To
publish a value, the application must explicitly redact and bound it and then add it as a portable header or payload
field.

`MessageHeaders` and `MessageMetadata` are sealed read-only interfaces. Sealing prevents lambda implementations, which
cannot provide the required value equality, and keeps immutable snapshots and metadata-safe `toString()` behavior
under Helidon control. Their factories and builders are the only construction path.

Every delivery is a non-empty, ordered `MessageBatch<T>`. Payload and message receivers are called once per item,
while a batch receiver is called once for the whole delivery:

```java
MessageBatch<Order> batch = MessageBatch.create(List.of(firstMessage, secondMessage));
```

`payloads()` initializes an immutable payload snapshot lazily. Calls that overlap before publication may each read every
message entity; message accessors must therefore support repeated concurrent invocation and return stable values.
The first successfully published snapshot is retained and returned to every successful caller. A failed candidate is not
retained, so a later call can retry.

A batch is a delivery and performance boundary, not necessarily a transport transaction. When batch delivery fails,
`BatchDeliveryException` describes each item as `SUCCEEDED`, `FAILED`, `NOT_ATTEMPTED`, or `INDETERMINATE`.

### Emit messages

Inject a generated `Emitter<T>` with exactly one named channel qualifier and a concrete payload type. Raw, wildcard,
and unresolved generic emitter payload types are rejected during code generation:

```java
@Service.Singleton
final class OrderPublisher {
    private final Emitter<Order> orders;

    @Service.Inject
    OrderPublisher(@Service.Named("orders") Emitter<Order> orders) {
        this.orders = orders;
    }

    void publish(Order order) {
        orders.emit(order);
    }

    void publish(Message<Order> order) {
        orders.emit(order);
    }

    void publish(MessageBatch<Order> orders) {
        this.orders.emit(orders);
    }
}
```

`emit` is overloaded for payloads, message envelopes, and batches. Java selects the overload from the declared emitter
and argument types, not from the argument's runtime class. When the message overload is selected, connector-specific
message subtypes retain their metadata; when the batch overload is selected, the supplied batch remains one delivery.
If a `Message` or `MessageBatch` object is itself the intended payload and a structural overload would otherwise apply,
make the nesting explicit with an outer message, for example `emitter.emit(Message.create(messagePayload))`. All three
overloads reject null; an uncast null literal is ambiguous among the overloads, so use a typed variable or cast when
validating null handling.

Emitter calls are synchronous. A successful return means every required local receiver, processor route, and outgoing
connector completed. The target channel must have at least one receiver or configured outgoing connector.

### Configure connectors

Declare named connector instances under `messaging.connector`, then reference those names from external sources under
`messaging.incoming` and external sinks under `messaging.outgoing`. Each connector retains its typed common
configuration and applies channel-specific overrides when creating a channel connection.

First-party connectors from the Helidon Extensions repository use the `helidon-` prefix so they remain distinguishable
from third-party providers. For example, an application using the JMS connector can configure:

```yaml
messaging:
  connector:
    primary:
      type: helidon-jms
      connection-factory: primary-jms

  incoming:
    orders:
      connector: primary
      destination: orders
      destination-type: QUEUE

  outgoing:
    validated-orders:
      connector: primary
      destination: validated-orders
      destination-type: QUEUE
```

The same connector declaration can use a list:

```yaml
messaging:
  connector:
    - name: primary
      type: helidon-jms
      connection-factory: primary-jms
```

`type` selects a `MessagingConnectorProvider`; the object key or list entry's `name` identifies the resulting
`MessagingConnector`. In object form, an omitted `type` defaults to the object key; in list form, an omitted `name`
defaults to `type`. Several named instances can use the same provider type. `MessagingConfig` resolves this connector
list using the Service Registry. Its `incoming` and `outgoing` maps contain `MessagingIncomingConfig` and
`MessagingOutgoingConfig` values from `io.helidon.messaging.spi`. Both extend `MessagingChannelConfig`, which carries
the required connector name, logical channel name, sparse execution overrides, and optional original configuration.
A channel node's `name`, when present, overrides its object key as the logical channel name. `channelName` is typed
metadata, not a configuration option: configuration derives it from the entry, while imperative builders supply it
explicitly.

The runtime uses typed `execution` and incoming `failure` settings, and passes the typed channel configuration to
the connector. The original configuration node is retained for connector setup adapters. The configured connector
applies its own typed common defaults; the runtime does not merge connector configuration trees or inject a direction.
The `failure` subtree belongs to an incoming channel and cannot be placed in connector-wide defaults.
See the selected connector's documentation in the Helidon Extensions repository for its complete configuration.

### Retry, drop, and dead-letter handling

`@Messaging.OnFailure` supplies a default policy for a configured incoming connector channel:

```java
@Messaging.ReceiveFrom("orders")
@Messaging.OnFailure(
        retryDelay = "PT0.25S",
        maxAttempts = 3,
        onExhausted = FailureDisposition.DEAD_LETTER,
        deadLetterChannel = "orders-dlq")
void receive(Order order) {
    throw new IllegalArgumentException("Invalid order");
}

@Messaging.ReceiveFrom("orders-dlq")
void deadLetter(DeadLetterMessage<Order> failed) {
    System.err.printf("Order from %s failed after %d attempts%n",
                      failed.sourceChannel(), failed.attempts());
}
```

The policy uses `io.helidon.faulttolerance.Retry`; messaging defaults to `Integer.MAX_VALUE` calls, a one-second
initial delay with factor-two exponential backoff, a one-minute maximum delay, and an overall
timeout of about 292 years. The annotation's `retryDelay` changes the initial delay, and a positive `maxAttempts`
changes the FT `calls`; zero retains the messaging call default. `DEAD_LETTER` requires a distinct logical target
channel with an actual output. The target is represented by the optional `DeadLetterConfig` nested in the failure
policy.

Configuration can use the FT retry keys `calls`, `delay`, `delay-factor`, `jitter`, `jitter-factor`, `max-delay`,
`overall-timeout`, and `enable-metrics`. Programmatic `FailurePolicy` declarations can additionally use `applyOn` and
`skipOn` on their FT retry configuration to select retryable throwables. Messaging retries `RuntimeException` by default
and never retries `Error` or `MessagingRejectedException`. The overall timeout continues across failed and deferred
subsets of the same retained delivery; partial success never resets its retry budget. For a structured batch failure,
throwable selection uses the aligned delivery exception's application cause and applies that decision to the current
failed subset.

`MessagingIncomingConfig.failure()` exposes `MessagingFailureConfig`, with optional `retry`, `onExhausted`, and
`deadLetter` values. When `retry` is present, it supplies a complete FT `Retry` instance: omitted retry options use FT
defaults of three calls, a 200-millisecond initial delay, and a one-second overall timeout. Retry options do not inherit
individual values from the declared policy or messaging defaults. When `retry` is absent, the runtime uses the handler's
declared `Retry`, or the shared messaging default described above if no handler policy is supplied.

The incoming configuration builder's `.failure(failure)` method accepts the same settings programmatically. The runtime
uses the supplied `Retry` instance directly:

```java
Retry retry = Retry.builder().calls(1).build();
MessagingFailureConfig failure = MessagingFailureConfig.builder()
        .retry(retry)
        .onExhausted(FailureDisposition.DROP)
        .build();
```

When constructing a complete `FailurePolicy` directly, pass the `Retry` instance to `FailurePolicy.Builder.retry`.

A dead-letter target must use the source payload type. Its local receivers must accept `DeadLetterMessage<T>` or a
compatible `Message<T>` envelope, and dead-letter routes cannot form cycles. These constraints are validated before
the messaging graph starts.

Portable dead-letter headers contain the source channel and attempt count. Failure type and message are stored in the
dead-letter envelope's `localMetadata()` under `FAILURE_TYPE_METADATA` and `FAILURE_MESSAGE_METADATA`; the convenience
accessors `failureType()` and `failureMessage()` read those values. The original message's other local metadata is
retained, but a connector never maps local metadata to the wire. Failure diagnostics may expose sensitive implementation
details or unbounded exception text. To publish them, a local dead-letter consumer must create a new message with
explicitly redacted and bounded application headers.

The policy belongs to the incoming channel and retained delivery, not only to the annotated method call. It covers
sibling receivers and downstream outputs reached by that delivery. If several receivers on one channel declare a
policy, their effective policies must agree.

Configuration selects the retry instance, exhaustion disposition, and dead-letter target independently:

```yaml
messaging:
  incoming:
    orders:
      connector: primary
      destination: orders
      destination-type: QUEUE
      failure:
        retry:
          calls: 1
        on-exhausted: DROP
```

This replaces the annotation's retry instance with a new FT `Retry` configured for one call; its other options use FT
defaults. It also replaces `DEAD_LETTER` with `DROP` and clears the inherited dead-letter configuration. Without either
an annotation or configuration, an incoming connector uses the shared messaging retry default described above and `FAIL`.
A mapping failure reported through `ConnectorDeliveryReservation.startFailed` is terminal after its initial attempt
because the runtime cannot repeat connector-owned transport mapping.

Exhaustion has these results:

- `FAIL` propagates the failure and leaves the transport delivery unsettled.
- `DROP` logs the failure and settles the transport delivery without forwarding it.
- `DEAD_LETTER` routes a `DeadLetterMessage<T>` with the original envelope, source channel, attempt count, and local
  failure diagnostics. The source is settled only after dead-letter delivery succeeds.

`@Messaging.OnFailure` does not retry calls made through a local `Emitter`; an emitter returns its delivery exception
directly.

### Configure execution limits

Messaging uses bounded admission rather than a Reactive Streams protocol. Global limits are configured under
`messaging`; connection-specific values under `messaging.incoming.<channel>.execution` or
`messaging.outgoing.<channel>.execution` override them. Channel overrides have no defaults: omitted values inherit
the global settings.

```yaml
messaging:
  queue-capacity: 0
  max-pending-admissions: 64
  max-pending-messages: 1024
  max-in-flight-messages: 1024
  admission-timeout: PT5S
  shutdown-timeout: PT10S

  incoming:
    orders:
      connector: primary
      destination: orders
      execution:
        queue-capacity: 32
```

If a logical channel appears in both maps, its incoming configuration supplies the execution overrides. Unconfigured
fields inherit the root settings; they do not fall back to the outgoing entry. A channel without either connection
configuration uses the root settings.

Admitted deliveries execute sequentially in FIFO order within each channel, so messaging methods handling that channel
are never invoked concurrently. Different channels have independent dispatchers, so deliveries on different channels
may execute at the same time. `shutdown-timeout` is configured only under `messaging` and applies only to shutdown or
failed-startup rollback; it does not bound connector startup or readiness. Configure transport connection
and startup limits on the connector. Capacity, timeout, cancellation, and shutdown admission failures are reported as
`MessagingRejectedException` with a typed reason.

Every delivery runs with a Helidon context. A local emitter captures the caller's active context, or creates a fresh
one when none is active. Connector and stream-source deliveries always receive a fresh context; connector deliveries
never inherit a context accidentally bound to the connector source thread. Synchronous processors, routes, handlers,
interceptors, retries, dead-letter routing, and outgoing sends retain the same delivery context.

Synchronous work moved to a directly created child thread retains delivery ancestry when that thread inherits
thread-local state. Its emissions use nested admission, so an emission back into any channel already on the active path
fails instead of waiting on the parent delivery. Existing executor workers, common-pool tasks, and threads that disable
inheritable state do not retain this ancestry and are indistinguishable from unrelated top-level callers. A handler
must not wait for such work to emit to a channel on the handler's active delivery path; without an admission timeout,
the parent and child could otherwise wait indefinitely.

For generated receiver and emitter examples, see `ChannelMessagingTypes.java` in the
[declarative messaging acceptance tests](../declarative/tests/messaging/).

## Imperative API

The imperative API builds and owns a typed messaging graph directly in Java. It uses the
`helidon-messaging` runtime dependency but does not require messaging code generation.
Both imperative builders and declarative registrations produce `MessagingConfig` and use the same graph assembly
and lifecycle. `MessagingGraph.builder()` returns a `MessagingConfig.Builder`, which can also load configuration with
`.config(config.get("messaging"))`.

During declarative setup, the configuration provider combines the root configuration, owning Service Registry, and
consumer and emitter registrations into a typed `MessagingConfig`. `ChannelRegistry` receives that configuration and
builds the graph with `config.build()`. Delivery and failure handling use typed objects rather than configuration-tree
lookups.

### Build and run a graph

```java
MessagingChannel<String> input = MessagingChannel.create("input", String.class);
MessagingChannel<String> output = MessagingChannel.create("output", String.class);

MessagingConfig.Builder builder = MessagingGraph.builder()
        .channel(input)
        .channel(output)
        .messageProcessor(input, output, message ->
                Message.builder(message.entity().toUpperCase())
                        .header("trace-id", message.header("trace-id").orElse("unknown"))
                        .build())
        .messageSink(output, message -> System.out.println(message.entity()));

try (MessagingGraph graph = builder.build()) {
    graph.start();

    Emitter<String> emitter = graph.emitter(input);
    emitter.emit(Message.builder("hello")
                         .header("trace-id", "123")
                         .build());
}
```

`MessagingChannel<T>` is a typed logical channel handle. Create it with `MessagingChannel.create`, using `Class<T>` for
a simple payload type or `GenericType<T>` to retain a parameterized payload type. Register each handle with
`builder.channel(handle)` before adding sources, sinks, or routes that use it. `build()` freezes and validates the
topology, and `start()` must complete before an emitter can emit.

Use each messaging builder and its configuration to create one graph. Do not reuse either after a graph build attempt,
whether it succeeds or fails. You may take `buildPrototype()` snapshots before that attempt, but snapshots retain the
same streams and channel connections; they do not duplicate owned resources and must not be used to build additional
graphs.

### Build a topology

The builder supports these elements:

| Method | Purpose |
| --- | --- |
| `channel` | Register a typed `MessagingChannel<T>` handle. |
| `payloadSource` | Feed payloads from a graph-owned `Stream`. |
| `messageSource` | Feed message envelopes from a graph-owned `Stream`. |
| `route` | Forward a batch unchanged between channels of the same payload type. |
| `payloadProcessor` | Transform each payload; input headers are not propagated. |
| `messageProcessor` | Transform each message and explicitly control the resulting headers. |
| `payloadSink` | Consume each payload. |
| `messageSink` | Consume each message envelope. |
| `batchSink` | Consume a complete batch once. |
| `incomingChannel` | Add a graph-owned `IncomingChannel` as a source. |
| `outgoingChannel` | Add a graph-owned `OutgoingChannel` as a required output. |

Every channel must have at least one output. Synchronous routing cycles are rejected. A channel can have at most one
stream source, and downstream paths from distinct stream sources cannot converge.

The imperative builder accepts configured connectors and typed incoming and outgoing configurations.
The built graph exposes typed emitters for application-originated input and owns registered streams and channel
connections. It manages channel startup, incoming delivery admission, draining, and shutdown. Generated consumer
registrations contribute annotation-based `@Messaging.OnFailure` policies to the same graph.

For example, the Kafka extension provides typed configuration for both directions:

```java
KafkaConnector kafka = KafkaConnector.builder()
        .name("primary")
        .addBootstrapServer("localhost:9092")
        .build();

MessagingChannel<String> orders = MessagingChannel.create("orders", String.class);
MessagingChannel<String> outgoing = MessagingChannel.create("outgoing", String.class);

KafkaIncomingConfig ordersConfig = KafkaIncomingConfig.builder()
        .connector(kafka.name())
        .channelName(orders.name())
        .execution(execution -> execution.maxInFlightMessages(64))
        .topic("orders")
        .groupId("inventory-service")
        .build();
KafkaOutgoingConfig outgoingConfig = KafkaOutgoingConfig.builder()
        .connector(kafka.name())
        .channelName(outgoing.name())
        .topic("orders")
        .build();

MessagingConfig.Builder builder = MessagingGraph.builder()
        .channel(orders)
        .channel(outgoing)
        .addConnector(kafka)
        .incoming(Map.of(orders.name(), ordersConfig))
        .outgoing(Map.of(outgoing.name(), outgoingConfig))
        .messageSink(orders, message -> System.out.println(message.entity()));

try (MessagingGraph graph = builder.build()) {
    graph.start();
    graph.emitter(outgoing).emit(Message.create("new order"));
    // Keep the graph running for the application's lifetime.
}
```

The configured `KafkaConnector` supplies the shared bootstrap servers. The graph reads execution overrides from the
typed configurations, then asks the connector to create fresh channel connections whose resources belong to the graph.

### Emit batches

The same `Message<T>`, `MessageBatch<T>`, and `Emitter<T>` contracts are used by both APIs:

```java
MessageBatch<String> batch = MessageBatch.create(List.of(
        Message.create("first"),
        Message.builder("second").header("trace-id", "123").build()));

graph.emitter(input).emit(batch);
```

Payload and message-envelope overloads create singleton batches. The batch overload preserves the supplied delivery
boundary. All emitter calls wait for end-to-end completion. A partial or indeterminate failure throws
`BatchDeliveryException` with an outcome aligned to every original item.

### Configure execution and lifecycle

Configure graph-wide defaults directly on the graph builder:

```java
MessagingConfig.Builder builder = MessagingGraph.builder()
        .queueCapacity(32)
        .maxInFlightMessages(256)
        .shutdownTimeout(Duration.ofSeconds(10));
```

Set sparse `MessagingExecutionConfig` overrides on the typed incoming or outgoing configuration, as in the Kafka
example above. Register that configuration in the graph's corresponding map so the runtime can apply its admission
and message limits. Unconfigured fields inherit the graph defaults; if both directions name the same logical channel,
only the incoming entry supplies overrides. The shutdown timeout is configured only on the graph. Delivery remains
sequential within every channel, while different channels may execute concurrently.

Closing a running graph stops new external admission, drains admitted work, and closes graph-owned streams and
connections. A failed build also closes registered resources. Failures from asynchronous stream sources are reported
when the graph closes.

An imperative emission has the same at-least-once behavior as a declarative emission: for each delivery, outputs run
sequentially, the first failure prevents later outputs from running, and earlier outputs are not rolled back. Retrying
an unsuccessful or indeterminate delivery can therefore produce duplicates.

## Create a connector

A connector module contains a stateless `MessagingConnectorProvider`, a configured `MessagingConnector`, and one new
channel connection for every incoming or outgoing binding. The provider creates named connectors; each connector
retains common configuration and creates its supported channel directions. Types implemented or extended by connector
authors, including the common connector configuration prototype, are in the
exported `io.helidon.messaging.spi` package. Runtime-owned context and delivery handles supplied through those contracts
are in `io.helidon.messaging`; the examples below omit routine imports.

`MessagingConnectorProvider` is the Service Registry contract and extends `ConfiguredProvider<MessagingConnector>`.
`IncomingChannel` and `OutgoingChannel` extend the common `ChannelConnection` lifecycle contract. A named
`MessagingConnector` is a factory, not a channel lifecycle resource; the graph owns the connections attached to it.

### 1. Choose the connector identity and directions

Choose a non-blank connector type that is unique in the application. Helidon connectors use the `helidon-` prefix;
third-party connectors should use a similarly distinctive name. The examples below use `example-acme`.

- Override `MessagingConnector.incoming(MessagingIncomingConfig)` for a source.
- Override `MessagingConnector.outgoing(MessagingOutgoingConfig)` for a sink.
- Override both methods when the transport supports both directions.

The default directional methods return `Optional.empty()`; a connector must support at least one direction. The runtime
rejects duplicate configured connector names and rejects a binding when its selected connector does not support that
direction. Connector names and provider types serve different purposes: a type identifies the implementation, while a
name selects one configured instance of it.

### 2. Create the connector module

Add the core messaging, configuration, builder, and Service Registry APIs. Configuration metadata is optional at
runtime but should be present while compiling a typed connector configuration:

```xml
<dependencies>
    <dependency>
        <groupId>io.helidon.messaging</groupId>
        <artifactId>helidon-messaging</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.config</groupId>
        <artifactId>helidon-config</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.builder</groupId>
        <artifactId>helidon-builder-api</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.config.metadata</groupId>
        <artifactId>helidon-config-metadata</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>io.helidon.service</groupId>
        <artifactId>helidon-service-registry</artifactId>
    </dependency>
</dependencies>
```

Also add the transport client used by the connector.

Use the `helidon-bundles-apt` annotation-processor setup shown above. It generates the configuration prototype and the
Service Registry descriptor for the provider. A JPMS connector module using a generated public configuration typically
has this shape:

```java
module com.example.messaging.connector.acme {
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.config;
    requires transitive io.helidon.messaging;

    requires io.helidon.service.registry;
    requires com.acme.transport;

    requires static io.helidon.config.metadata;

    exports com.example.messaging.connector.acme;
}
```

The package-private provider is discovered using its generated Helidon Service Registry descriptor. Leave it out of
Java `ServiceLoader` configuration and `module-info.java` `provides` directives. A consuming application adds the
connector artifact and, when modular, requires the connector module.

### 3. Define and validate connector configuration

Extend `MessagingConnectorProviderConfig` to inherit the configured connector instance name and original configuration.
Use `Prototype.Factory` so the generated builder creates the connector:

```java
@Prototype.Blueprint
@Prototype.Configured(value = AcmeConnectorProvider.CONNECTOR_TYPE, root = false)
@Prototype.Provides(MessagingConnectorProvider.class)
interface AcmeConnectorConfigBlueprint extends MessagingConnectorProviderConfig, Prototype.Factory<AcmeConnector> {
    @Option.Required
    @Option.Configured
    String endpoint();
}
```

Define direction-specific configurations extending the corresponding SPI base. The base supplies `connector`,
`channelName`, `execution`, and the retained original `config`; do not redeclare `channelName` as configurable.
Incoming configurations also inherit typed `failure` settings from `MessagingIncomingConfig`.
Options overriding common connector values are optional and have no defaults. Collection options retain their
declared blueprint merge behavior.

```java
@Prototype.Blueprint
@Prototype.Configured
interface AcmeIncomingConfigBlueprint extends MessagingIncomingConfig {
    @Option.Configured
    Optional<String> endpoint();

    @Option.Required
    @Option.Configured
    String destination();
}

@Prototype.Blueprint
@Prototype.Configured
interface AcmeOutgoingConfigBlueprint extends MessagingOutgoingConfig {
    @Option.Configured
    Optional<String> endpoint();

    @Option.Required
    @Option.Configured
    String destination();
}
```

Builder code generation creates `AcmeConnectorConfig`, `AcmeIncomingConfig`, and `AcmeOutgoingConfig`. Add a builder
decorator or custom builder methods for validation involving several options, secrets, mutually exclusive values, or
normalized transport properties. Mark secret options with `@Option.Confidential`, copy mutable values defensively, and
keep them out of diagnostics and `toString()` output.

Setup builds `MessagingConfig` and resolves each named connector through its provider. Graph assembly uses that typed
configuration for each channel binding:

1. Selects the configured connector named by the channel's `connector` property.
2. Applies typed execution overrides and resolves incoming `MessagingFailureConfig` against the declared failure policy.
3. Uses the logical name carried by `channelName()` and retains the original configuration node.
4. Calls `incoming(MessagingIncomingConfig)` or `outgoing(MessagingOutgoingConfig)` with that typed configuration.

The connector accepts its concrete configuration directly, or reads connector-specific values from the retained node
and copies the typed base fields. It then applies common connector defaults. This adaptation stays inside the
directional factory; no separate public conversion step is needed. Creating a configured connector or channel connection
must not open transport connections, create delivery threads, or poll; those actions belong to channel startup.

### 4. Implement the stateless provider

Register the provider as a package-private Service Registry singleton. The connector and its configuration API remain
public. The provider creates a configured connector from the selected node and instance name:

```java
@Service.Singleton
final class AcmeConnectorProvider implements MessagingConnectorProvider {
    static final String CONNECTOR_TYPE = "example-acme";

    @Override
    public String configKey() {
        return CONNECTOR_TYPE;
    }

    @Override
    public AcmeConnector create(Config config, String name) {
        return AcmeConnector.builder()
                .config(config)
                .name(name)
                .build();
    }
}
```

The configured connector keeps the immutable prototype and handles both typed imperative configurations and retained
configuration nodes through the same directional factories:

```java
public final class AcmeConnector implements MessagingConnector, RuntimeType.Api<AcmeConnectorConfig> {
    private final AcmeConnectorConfig config;

    private AcmeConnector(AcmeConnectorConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    public static AcmeConnector create(AcmeConnectorConfig config) {
        return new AcmeConnector(config);
    }

    public static AcmeConnectorConfig.Builder builder() {
        return AcmeConnectorConfig.builder();
    }

    @Override
    public AcmeConnectorConfig prototype() {
        return config;
    }

    @Override
    public String type() {
        return AcmeConnectorProvider.CONNECTOR_TYPE;
    }

    @Override
    public Optional<IncomingChannel> incoming(MessagingIncomingConfig channelConfig) {
        Objects.requireNonNull(channelConfig);
        AcmeIncomingConfig.Builder builder = AcmeIncomingConfig.builder();
        if (channelConfig instanceof AcmeIncomingConfig acmeConfig) {
            builder.from(acmeConfig);
        } else {
            channelConfig.config().ifPresent(builder::config);
            builder.from(channelConfig);
        }
        builder.endpoint(builder.endpoint().orElse(config.endpoint()));
        return Optional.of(new AcmeIncomingChannel(builder.build()));
    }

    @Override
    public Optional<OutgoingChannel> outgoing(MessagingOutgoingConfig channelConfig) {
        Objects.requireNonNull(channelConfig);
        AcmeOutgoingConfig.Builder builder = AcmeOutgoingConfig.builder();
        if (channelConfig instanceof AcmeOutgoingConfig acmeConfig) {
            builder.from(acmeConfig);
        } else {
            channelConfig.config().ifPresent(builder::config);
            builder.from(channelConfig);
        }
        builder.endpoint(builder.endpoint().orElse(config.endpoint()));
        return Optional.of(new AcmeOutgoingChannel(builder.build()));
    }
}
```

The provider may be called for several connectors and graphs. It remains stateless, and each configured connector can
create several independent channel connections. Return a fresh, unstarted connection from every successful channel
factory call and never reuse a transport client or channel lifecycle object between bindings.

### 5. Implement an incoming channel

The runtime invokes `IncomingChannel.run` once on a runtime-owned virtual thread. Establish enough transport state
to report readiness, call `context.awaitRunning()` exactly once, and acquire no delivery until it returns `true`.

Before polling, reading, or otherwise accepting a transport delivery, reserve runtime capacity. Keep the returned
delivery lease until both runtime processing and transport settlement finish. This outline uses transport-specific
placeholder types:

```java
final class AcmeIncomingChannel implements IncomingChannel {
    private final AcmeIncomingConfig config;
    private final AtomicBoolean runStarted = new AtomicBoolean();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AcmeIncomingLifecycle lifecycle = new AcmeIncomingLifecycle();
    private volatile AcmeConsumer consumer;
    private volatile Thread owner;

    AcmeIncomingChannel(AcmeIncomingConfig config) {
        this.config = config;
    }

    @Override
    public void run(IncomingConnectorContext context) {
        if (!runStarted.compareAndSet(false, true)) {
            throw new IllegalStateException("Connector can only be run once");
        }
        if (closed.get() || draining.get()) {
            return;
        }
        owner = Thread.currentThread();
        try {
            if (closed.get() || draining.get()) {
                return;
            }
            AcmeConsumer transport = AcmeConsumer.open(config);
            try (transport) {
                consumer = transport;
                if (closed.get()) {
                    transport.forceClose();
                    return;
                }
                if (!context.awaitRunning() || closed.get() || draining.get()) {
                    return;
                }
                int maxMessages = context.maxDeliveryMessages();
                if (maxMessages <= 0) {
                    throw new MessagingException("Delivery limit must be greater than zero");
                }

                while (!closed.get() && !draining.get()) {
                    try {
                        try (ConnectorDeliveryReservation reservation = context.reserveDelivery()) {
                            if (closed.get() || draining.get()) {
                                break;
                            }

                            AcmeTransportBatch transportBatch =
                                    transport.receive(maxMessages);
                            if (transportBatch.isEmpty()) {
                                continue;
                            }
                            if (closed.get() || draining.get()) {
                                transport.abandon(transportBatch);
                                break;
                            }

                            try {
                                deliverAndSettle(transport,
                                                 transportBatch,
                                                 reservation,
                                                 maxMessages,
                                                 context.channel());
                            } catch (MessagingException e) {
                                if (e.getCause() instanceof InterruptedException
                                        && (closed.get() || draining.get())) {
                                    break;
                                }
                                throw e;
                            }
                        }
                    } catch (MessagingRejectedException e) {
                        boolean lifecycleShutdown = e.reason() == MessagingRejectedException.Reason.SHUTDOWN
                                || ((closed.get() || draining.get())
                                && e.reason() == MessagingRejectedException.Reason.CANCELLED);
                        if (lifecycleShutdown) {
                            break;
                        }
                        throw e;
                    }
                }

                if (!closed.get()) {
                    transport.flushCheckpoint();
                }
            }
        } finally {
            consumer = null;
            owner = null;
        }
    }

    private void deliverAndSettle(AcmeConsumer transport,
                                  AcmeTransportBatch transportBatch,
                                  ConnectorDeliveryReservation reservation,
                                  int maxMessages,
                                  String channel) {
        MessageBatch<?> batch;
        try {
            batch = toMessageBatch(transportBatch);
            if (batch.size() > maxMessages) {
                throw new MessagingRejectedException(
                        channel,
                        MessagingRejectedException.Reason.OVERSIZED,
                        "Transport batch exceeds the runtime message limit");
            }
        } catch (RuntimeException | Error failure) {
            abandon(transport, transportBatch, failure);
            throw failure;
        }

        ConnectorDelivery delivery;
        try {
            // This helper closes the race between delivery start/publication and forceClose().
            delivery = lifecycle.startDelivery(reservation, batch, channel);
        } catch (RuntimeException | Error failure) {
            abandon(transport, transportBatch, failure);
            throw failure;
        }

        try {
            try (delivery) {
                try {
                    delivery.await();
                    // Commit only after runtime processing, retries, drop, or dead-letter handling succeeds.
                    transport.commit(transportBatch);
                } catch (RuntimeException | Error failure) {
                    if (failure instanceof MessagingException
                            && failure.getCause() instanceof InterruptedException) {
                        delivery.cancel();
                    }
                    abandon(transport, transportBatch, failure);
                    throw failure;
                }
            }
        } finally {
            lifecycle.deliveryFinished(delivery);
        }
    }

    private void abandon(AcmeConsumer transport,
                         AcmeTransportBatch transportBatch,
                         Throwable failure) {
        try {
            transport.abandon(transportBatch);
        } catch (RuntimeException | Error abandonFailure) {
            failure.addSuppressed(abandonFailure);
        }
    }

    @Override
    public void drain() {
        draining.set(true);
        lifecycle.drain();
        AcmeConsumer current = consumer;
        if (current != null) {
            // Wake acquisition only; do not interrupt active settlement or checkpointing.
            current.stopAcquisition();
        }
    }

    @Override
    public void forceClose() {
        closed.set(true);
        draining.set(true);
        lifecycle.forceClose();
        Thread currentOwner = owner;
        if (currentOwner != null) {
            currentOwner.interrupt();
        }
        AcmeConsumer current = consumer;
        if (current != null) {
            current.forceClose();
        }
    }

    @Override
    public void close() {
        forceClose();
    }
}
```

`AcmeIncomingLifecycle` in this outline is a connector-specific helper, not a core API. Its locked state machine must
atomically prevent new delivery starts during drain, record an in-progress `reservation.start(...)`, and publish the
returned `ConnectorDelivery`. Forced close marks the gate closed, interrupts an in-progress start, and cancels a
published delivery; a delivery that completes publication after close is cancelled before it is returned. The
`drain()`, `forceClose()`, and `deliveryFinished(...)` helper calls are non-throwing bookkeeping and unblock actions.

The placeholder `AcmeConsumer` lifecycle methods are assumed to be thread-safe and idempotent. A real connector must
serialize resource publication and cleanup so a resource published while close is in progress is closed exactly once.
`stopAcquisition()` must make `receive()` return normally or empty without disturbing active delivery settlement or
checkpointing. Forced cleanup must run every unblock action even when one fails, then aggregate and report failures.

`reserveDelivery()` blocks with bounded pending accounting. A transport that must keep polling for heartbeats should
use `tryReserveDelivery()` and perform maintenance when it returns empty. After acquiring data, it may retry
`reservation.tryStart(batch)` while retaining that exact transport delivery. Do not rebuild a retained batch between
attempts; the admission lease follows the original batch and subsets created with `MessageBatch.subset(...)`.
Repeated `tryReserveDelivery()` and `tryStart()` calls share one admission-timeout budget. Time spent acquiring the
transport data between reservation and start is excluded from that budget.

`ConnectorDelivery.await()` completes after the runtime's portable retry, drop, or dead-letter policy settles. If it
throws, do not acknowledge or commit the transport delivery. Either leave it available for transport redelivery or
apply a documented transport-specific negative acknowledgement. A connector that needs heartbeats while processing
can call timed `await(Duration)` and perform transport maintenance between waits.

If an `await` call is interrupted, the runtime restores the interrupt flag and throws `MessagingException` with the
`InterruptedException` as its cause; the connector still owns cancellation and transport settlement. Connector close
code invoked reentrantly from delivery processing must use `isCurrentThread()` to skip any connector-owned completion
wait that depends on the current delivery returning.

If transport-to-message mapping fails before dispatch, create a metadata-only envelope and call
`reservation.startFailed(batch, failure)`. A transport that must keep polling for heartbeats should instead retry
`reservation.tryStartFailed(batch, failure)` and perform maintenance whenever it returns empty. The runtime does not own
the native transport record or mapper, so it cannot repeat mapping. Bounded policies retain their configured
failure-attempt accounting. An unlimited policy treats the mapping failure as exhausted after its initial attempt so
`await()` always terminates; with `FAIL`, leave the transport delivery available for redelivery so the connector can map
it again.

When mapping fails before a safe payload can be retained, pass a connector-specific immutable metadata envelope only to
`startFailed` or `tryStartFailed`; its `entity()` may throw `MessagingException`. `MessageBatch` retains the envelope and
delivery lineage without reading the entity, while `payloads()` propagates the mapping failure if called. The runtime can
wrap the envelope in a `DeadLetterMessage` and route it to a local dead-letter envelope consumer; ordinary channel
emission still rejects it. An outgoing connector must either support this failed-mapping form or reject it before its
transport success point.

For a partially mapped native batch, pass a root-aligned `BatchDeliveryException`: use `FAILED` or `INDETERMINATE` for
unmappable items and `NOT_ATTEMPTED` for mapped siblings that have not reached application handlers. Never mark an
undispatched item `SUCCEEDED`. The runtime settles the failed subset first. Successful `DROP` or dead-letter settlement
then releases the `NOT_ATTEMPTED` subset into normal dispatch; `FAIL` or failed dead-letter routing terminates before
those deferred items run. Terminal batch outcomes remain aligned to the original retained batch so connectors can
settle native records by original index.

A normal return from `await()` may mean normal handler completion, `DROP`, or successful dead-letter delivery; all are
settled runtime outcomes and the complete source delivery may then be committed. The conservative outline above
abandons the complete transport batch on failure. A connector that supports partial settlement may inspect the terminal
`BatchDeliveryException`, which the runtime aligns to the original retained batch, and settle only `SUCCEEDED` items
while respecting transport ordering constraints such as contiguous committed prefixes. `FAILED`, `NOT_ATTEMPTED`, and
`INDETERMINATE` items remain unsettled. Treat an unstructured exception as indeterminate for the complete batch.

`ConnectorDelivery.close()` releases runtime admission capacity; it does not acknowledge the source. Closing it before
processing terminates requests cancellation, and capacity remains retained until processing actually stops. Commit,
acknowledge, negatively acknowledge, or abandon the transport delivery first, and only then close the lease.

`drain()` stops new acquisition but allows an acquired delivery to settle and checkpoint. `forceClose()` may run
concurrently with startup, readiness, polling, admission, or delivery processing and must promptly unblock all of them.
All close operations must be idempotent.

The runtime owns the incoming `run` virtual thread and delivery tasks. Do not create another executor for messaging
delivery. Transport libraries may still use their normal internal I/O threads. Different connector bindings and
channels can overlap, so shared native resources must be synchronized without coupling sibling connector lifecycles.

### 6. Implement an outgoing channel

`OutgoingChannel.start()` acquires binding-owned transport resources and returns only when sends can begin.
`sendBatch()` is synchronous: it must not return until the connector's documented external success point is reached.

```java
final class AcmeOutgoingChannel implements OutgoingChannel {
    private final AcmeOutgoingConfig config;
    private final AcmeLifecycle lifecycle = new AcmeLifecycle();

    AcmeOutgoingChannel(AcmeOutgoingConfig config) {
        this.config = config;
    }

    @Override
    public void start() {
        lifecycle.startInterruptibly(() -> AcmeProducer.open(config));
    }

    @Override
    public void sendBatch(MessageBatch<?> batch) {
        Objects.requireNonNull(batch);
        AcmeProducer current;
        try {
            current = lifecycle.beginSend();
        } catch (RuntimeException failure) {
            throw beforeAnyAttempt(batch, failure);
        }
        try {
            for (int i = 0; i < batch.size(); i++) {
                try {
                    // Await the transport acknowledgement or other documented success point.
                    current.sendAndAwait(batch.get(i));
                } catch (RuntimeException failure) {
                    throw afterItemFailure(batch, i, failure);
                }
            }
        } finally {
            lifecycle.endSend();
        }
    }

    private static BatchDeliveryException beforeAnyAttempt(MessageBatch<?> batch,
                                                            RuntimeException failure) {
        List<BatchItemOutcome> outcomes = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            outcomes.add(BatchItemOutcome.notAttempted(i));
        }
        return new BatchDeliveryException("Acme send failed before attempting the batch",
                                          failure,
                                          batch,
                                          outcomes);
    }

    private static BatchDeliveryException afterItemFailure(MessageBatch<?> batch,
                                                            int failedIndex,
                                                            RuntimeException failure) {
        List<BatchItemOutcome> outcomes = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            if (i < failedIndex) {
                outcomes.add(BatchItemOutcome.succeeded(i));
            } else if (i == failedIndex) {
                outcomes.add(BatchItemOutcome.indeterminate(i, failure));
            } else {
                outcomes.add(BatchItemOutcome.notAttempted(i));
            }
        }
        return new BatchDeliveryException("Acme send failed at batch index " + failedIndex,
                                          failure,
                                          batch,
                                          outcomes);
    }

    @Override
    public void forceClose() {
        lifecycle.forceClose();
    }

    @Override
    public void close() {
        lifecycle.close();
    }
}
```

`AcmeLifecycle` in this outline is a connector-specific helper, not a core API. Implement it with a lock and a
one-shot `NEW`, `STARTING`, `READY`, `CLOSED` state machine. It must publish or close a resource atomically when close
races startup, track startup and active-send owners, and make `forceClose()` unblock all of them. Repeated or concurrent
close calls must have one cleanup owner. Run every unblock/cleanup action even when one fails, then aggregate and throw
the failures. `beginSend()` must reject non-ready state before any transport attempt. `endSend()` is non-throwing
bookkeeping so it cannot mask a primary delivery failure.

The example reports an interrupted or failed item as indeterminate because the transport may have accepted it before
throwing. Use `BatchItemOutcome.failed` only when the transport proves the item did not reach its success point. For a
failure before any external attempt, construct one `NOT_ATTEMPTED` outcome per item as shown above. When the complete
batch outcome is unknown, construct one `INDETERMINATE` outcome per item. Connector-private helpers may encode these
transport-specific rules, then create `BatchDeliveryException` with the message, primary cause, original batch, and
aligned outcomes.

Preserve the original transport failure as the cause. Make send and startup paths interruptible; `forceClose()` itself
must run promptly to completion and perform every unblock action. Make normal and forced cleanup safe when invoked
concurrently with an active send.

### 7. Map transport messages and enforce limits

- Treat `headers()` as the portable, wire-eligible data plane. `localMetadata()` belongs only to the in-process message
  envelope: it may be retained when the runtime derives another local envelope, but a connector must never generically
  map or serialize it. Publishing a local metadata value requires an explicit application decision to redact, bound,
  and promote it to a portable header.
- Convert each incoming transport record to an immutable `Message<T>` and copy portable headers into globally ordered
  `MessageHeader` entries. Preserve duplicate names, exact spelling, typed values, and immutable binary snapshots when
  the transport exposes them.
- Reject or translate a null transport payload before creating its ordinary message envelope. If no safe payload can be
  retained after mapping fails, preserve native metadata in an immutable connector-specific envelope whose `entity()`
  throws `MessagingException`, and pass it only to `startFailed`.
- Use a connector-specific immutable `Message<T>` subtype when applications need native keys, offsets, destinations,
  protocol-defined properties, or other metadata. `MessageHeaderValue.NativeValue` is an opaque encoded escape hatch for a
  non-portable application header, not a replacement for connector metadata. Document which locally emitted messages
  are accepted by handlers requiring the subtype.
- Outgoing mapping must accept ordinary core `Message` instances; treat a connector-specific subtype as an optional
  richer view rather than a required input.
- Bound every incoming batch by `context.maxDeliveryMessages()` before acquisition. This stable limit includes the
  source channel and every transitively reachable route; it does not promise immediate capacity while other deliveries
  are active. Keep byte, frame, record, and transport request limits in connector configuration; the core runtime
  performs message-count admission only.
- Preserve message order and the exact `MessageBatch` identity through settlement. Use retained subsets rather than
  rebuilding batches during partial failure handling.
- Document null, duplicate, ordering, encoding, native-header, unsupported-value, and payload-type conversion rules.
  Reject an unsupported outbound header unless the connector defines an explicit translation; never silently
  stringify, reorder, or drop it.

The runtime owns portable delivery retry, `DROP`, and dead-letter routing. The connector still owns transport
reconnection, polling, acknowledgements, commits, negative acknowledgements, and checkpoints.
Do not add an independent application-delivery retry loop on top of the runtime policy.

### 8. Configure and exercise the connector

Connector defaults and channel overrides can be combined as follows:

```yaml
messaging:
  connector:
    orders-broker:
      type: example-acme
      endpoint: https://broker.example

  incoming:
    orders:
      connector: orders-broker
      destination: orders-in

  outgoing:
    validated-orders:
      connector: orders-broker
      endpoint: https://outbound-broker.example
      destination: orders-out
```

`orders-broker` names the configured connector instance, while `type: example-acme` selects its provider. The incoming
`orders` channel inherits the connector's endpoint. The outgoing `validated-orders` channel overrides that endpoint
for its connection only. Each channel supplies its own required `destination`.

Add a generated receiver or named emitter for each configured channel, start the Service Registry application, and
verify that the provider is discovered through the Service Registry on both the class path and module path.

### 9. Test the connector contract

At minimum, cover:

- provider type selection, named connector uniqueness, object and list forms, supported directions, typed common
  defaults and channel overrides, typed channel-name metadata, retained source configuration, required values, secrets,
  defensive copying and redaction, and direction rejection;
- Service Registry discovery and fresh, resource-free channel connections from every directional factory call; also
  prove that providers and configured connectors are not lifecycle resources, provider-registry shutdown does not
  close an unattached factory-created connection, and an attached connection is closed by its owning graph;
- incoming readiness, reserve-before-acquire ordering, message-count bounds, empty polls, ordering, immutable message
  snapshots, globally ordered duplicate and typed headers, immutable binary snapshots, exact case-sensitive names,
  oversize post-acquisition rejection, and release of every unused reservation exactly once;
- successful processing followed by transport commit, failed processing without commit, redelivery, drop, dead-letter
  completion, commit or checkpoint failure, and retention of admission through commit, nack, or abandonment;
- `tryReserveDelivery()` saturation and shared repeated-attempt timeout exhaustion, repeated-`tryStart()` budgets,
  transport maintenance while delivery runs, and capacity retention after early cancellation until processing stops;
- outgoing startup, the documented send-completion point, interruption, partial and indeterminate batch outcomes;
  verify ordinary core messages and optional connector-specific message subtypes;
- aligned indexes for `SUCCEEDED`, `FAILED`, `NOT_ATTEMPTED`, and `INDETERMINATE`, preserved primary and suppressed
  causes;
- one-shot start/run, close before start/run, rejected restart, graceful drain, final checkpointing, blocked startup and
  transport calls, drain/force-close while receive returns a buffered batch, late resource publication during
  `forceClose()`, and one shared result from concurrent cleanup;
- simultaneous sibling bindings, including shared-target framing and ordering where needed, with no client, delivery,
  or lifecycle state leaking between them;
- a real-transport integration test for every supported direction, including service discovery on the module path and
  shutdown with no leaked threads or transport resources.

Finally, document connector-specific configuration and completion semantics, record third-party dependencies and
licenses, and run the connector repository's unit, integration, dependency, copyright, and style checks.
