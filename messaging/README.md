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

Concrete File, Kafka, and JMS connector implementations remain in the
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
| `String` | Last value, which must be `HeaderValue.TextValue` | Delivery fails |
| `Optional<String>` | Last value, which must be text when present | `Optional.empty()` |
| `HeaderValue` | Last value of any kind | Delivery fails |
| `Optional<HeaderValue>` | Last value of any kind | `Optional.empty()` |
| `List<HeaderValue>` | Immutable list of all matching values in message-entry order | Empty list |

An explicit `HeaderValue.NullValue` is present data, including as `Optional.of(HeaderValue.nullValue())`. Header values
are never converted automatically. A handler can declare at most one header parameter for each exact name:

```java
@Messaging.ReceiveFrom("orders")
void receive(@Messaging.Entity Order order,
             @Messaging.HeaderParam("tenant") String tenant,
             @Messaging.HeaderParam("trace-id") Optional<String> traceId,
             @Messaging.HeaderParam("attempt") HeaderValue attempt,
             @Messaging.HeaderParam("routing") Optional<HeaderValue> routing,
             @Messaging.HeaderParam("tag") List<HeaderValue> tags) {
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
        .header("attempt", HeaderValue.integer(3))
        .build();
```

Use `Message.create(order)` when no headers are needed. `header` replaces all values with the same exact,
case-sensitive name, while `addHeader` appends a duplicate-preserving entry. `MessageHeaders.entries()` is the
authoritative globally ordered representation. Explicit `first`, `last`, and `all` lookups avoid imposing one
transport's duplicate semantics on another; `valuesByName()` is only a derived grouped view and loses cross-name
ordering. The closed `HeaderValue` model supports null, text, immutable binary, boolean, integer, decimal,
32/64-bit floating point, timestamp, UUID, and opaque connector-encoded values. `Message.header(name)` remains a
last-valued text convenience and never stringifies a typed value.

Every delivery is a non-empty, ordered `MessageBatch<T>`. Payload and message receivers are called once per item,
while a batch receiver is called once for the whole delivery:

```java
MessageBatch<Order> batch = MessageBatch.create(List.of(firstMessage, secondMessage));
```

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

Add external sources under `helidon.messaging.incoming` and external sinks under `helidon.messaging.outgoing`.
Connector-wide defaults under `helidon.messaging.connector.<type>` are overlaid by the corresponding channel values.

First-party connectors from the Helidon Extensions repository use the `helidon-` prefix so they remain distinguishable
from third-party providers. For example, an application using the JMS connector can configure:

```yaml
helidon:
  messaging:
    connector:
      helidon-jms:
        connection-factory: primary-jms

    incoming:
      orders:
        connector: helidon-jms
        destination: orders
        destination-type: QUEUE

    outgoing:
      validated-orders:
        connector: helidon-jms
        destination: validated-orders
        destination-type: QUEUE
```

Connector options other than `connector` are connector-specific. The `failure` subtree of an incoming channel is
portable messaging configuration; it is not a connector option and cannot be placed in connector-wide defaults. See
the selected connector's documentation in the Helidon Extensions repository for its complete configuration.

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
    System.err.printf("Order failed after %d attempts: %s%n",
                      failed.attempts(), failed.failureMessage());
}
```

`maxAttempts` includes the initial attempt. Zero means unlimited attempts and is valid only with `FAIL`. `DROP` and
`DEAD_LETTER` require a positive limit, and `DEAD_LETTER` also requires a distinct logical target channel with an
actual output.

A dead-letter target must use the source payload type. Its local receivers must accept `DeadLetterMessage<T>` or a
compatible `Message<T>` envelope, and dead-letter routes cannot form cycles. These constraints are validated before
the messaging graph starts.

The policy belongs to the incoming channel and retained delivery, not only to the annotated method call. It covers
sibling receivers and downstream outputs reached by that delivery. If several receivers on one channel declare a
policy, their effective policies must agree.

Configuration overrides annotation members independently:

```yaml
helidon:
  messaging:
    incoming:
      orders:
        connector: helidon-jms
        destination: orders
        destination-type: QUEUE
        failure:
          retry:
            max-attempts: 1
          on-exhausted: DROP
```

This retains the annotation's retry delay, changes the total attempts to one, and replaces `DEAD_LETTER` with `DROP`.
The inherited dead-letter target is cleared. Without either an annotation or configuration, an incoming connector uses
a one-second retry delay, unlimited attempts, and `FAIL`.

Exhaustion has these results:

- `FAIL` propagates the failure and leaves the transport delivery unsettled.
- `DROP` logs the failure and settles the transport delivery without forwarding it.
- `DEAD_LETTER` routes a `DeadLetterMessage<T>` with the original envelope, source channel, attempt count, and failure
  details. The source is settled only after dead-letter delivery succeeds.

`@Messaging.OnFailure` does not retry calls made through a local `Emitter`; an emitter returns its delivery exception
directly.

### Configure execution limits

Messaging uses bounded admission rather than a Reactive Streams protocol. Global limits are configured under
`helidon.messaging.execution`; channel-specific values under `helidon.messaging.channel.<channel>.execution` override
them:

```yaml
helidon:
  messaging:
    execution:
      queue-capacity: 0
      max-pending-admissions: 64
      max-pending-messages: 1024
      max-in-flight-messages: 1024
      admission-timeout: PT5S
      shutdown-timeout: PT10S

    channel:
      orders:
        execution:
          queue-capacity: 32
```

Admitted deliveries execute sequentially in FIFO order within each channel, so messaging methods handling that channel
are never invoked concurrently. Different channels have independent dispatchers, so deliveries on different channels
may execute at the same time. `shutdown-timeout` is runtime-wide, cannot be overridden per channel, and applies only to
shutdown or failed-startup rollback; it does not bound connector startup or readiness. Configure transport connection
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

### Build and run a graph

```java
try (MessagingGraph.Builder builder = MessagingGraph.builder()) {
    MessagingChannel<String> input = builder.channel("input", String.class);
    MessagingChannel<String> output = builder.channel("output", String.class);

    builder.messageProcessor(input, output, message ->
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
}
```

`MessagingChannel<T>` is an opaque handle owned by its graph. Use `Class<T>` for a simple payload type or
`GenericType<T>` to retain a parameterized payload type. `build()` freezes and validates the topology, and `start()`
must complete before an emitter can emit.

### Assemble a topology

The builder supports these elements:

| Method | Purpose |
| --- | --- |
| `payloadSource` | Feed payloads from a builder/graph-owned `Stream`. |
| `messageSource` | Feed message envelopes from a builder/graph-owned `Stream`. |
| `route` | Forward a batch unchanged between channels of the same payload type. |
| `payloadProcessor` | Transform each payload; input headers are not propagated. |
| `messageProcessor` | Transform each message and explicitly control the resulting headers. |
| `payloadSink` | Consume each payload. |
| `messageSink` | Consume each message envelope. |
| `batchSink` | Consume a complete batch once. |
| `outgoingConnector` | Add a builder/graph-owned connector as a required output. |

Every channel must have at least one output. Synchronous routing cycles are rejected. A channel can have at most one
stream source, and downstream paths from distinct stream sources cannot converge.

The imperative builder registers streams as sources and can attach an `OutgoingConnector` directly. The built graph
exposes typed emitters for application-originated input. The builder owns registered streams and connectors until a
successful build transfers them to the graph. The builder does not currently expose an incoming-connector registration
method. Declarative connector configuration and `@Messaging.OnFailure` policies are not applied to an imperative graph.

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

Configure graph-wide defaults before declaring the first channel:

```java
MessagingExecutionConfig execution = MessagingExecutionConfig.builder()
        .queueCapacity(32)
        .maxInFlightMessages(256)
        .shutdownTimeout(Duration.ofSeconds(10))
        .build();

MessagingGraph.Builder builder = MessagingGraph.builder()
        .executionConfig(execution);
```

The `channel` overload that accepts a `GenericType<T>` and `MessagingExecutionConfig` supplies channel-specific
admission and message limits; the shutdown timeout remains graph-wide. Delivery remains sequential within every
channel, while different channels may execute concurrently.

Closing a running graph stops new external admission, drains admitted work, and closes graph-owned streams and
connectors. Closing an unbuilt builder releases resources already transferred to it. Failures from asynchronous stream
sources are reported when the graph closes.

An imperative emission has the same at-least-once behavior as a declarative emission: for each delivery, outputs run
sequentially, the first failure prevents later outputs from running, and earlier outputs are not rolled back. Retrying
an unsuccessful or indeterminate delivery can therefore produce duplicates.

## Create a connector

A connector module contains one stateless provider and one new lifecycle object for every configured incoming or
outgoing binding. The provider is shared; connector instances are not. Implement only the direction interfaces the
transport supports.

### 1. Choose the connector identity and directions

Choose a non-blank connector type that is unique in the application. Helidon connectors use the `helidon-` prefix;
third-party connectors should use a similarly distinctive name. The examples below use `example-acme`.

- Implement `IncomingConnectorProvider` for a source.
- Implement `OutgoingConnectorProvider` for a sink.
- Implement both interfaces on one provider when the transport supports both directions.

The runtime rejects duplicate provider types and rejects an incoming or outgoing binding when its selected provider
does not implement that direction.

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

The generated Service Registry descriptor performs provider discovery; do not add a Java `provides` directive for the
provider. A consuming application adds the connector artifact and, when modular, requires the connector module.

### 3. Define and validate connector configuration

Extending `ConnectorConfig` adds the runtime-provided connector type, direction, and channel name to the generated
configuration:

```java
@Prototype.Blueprint
@Prototype.Configured
interface AcmeConnectorConfigBlueprint extends ConnectorConfig {
    @Option.Required
    @Option.Configured
    String endpoint();

    @Option.Required
    @Option.Configured
    String destination();
}
```

Builder code generation creates `AcmeConnectorConfig` and its `create(Config)` factory. Add a builder decorator or
custom builder methods for validation that involves several options, secrets, mutually exclusive values, or normalized
transport properties. Mark secret options with `@Option.Confidential`, copy mutable values defensively, and keep them
out of diagnostics and `toString()` output.

For each binding, the runtime constructs the effective `Config` in this order:

1. Start with defaults under `helidon.messaging.connector.<connector-type>`.
2. Overlay the selected `incoming` or `outgoing` channel configuration.
3. Remove the portable `failure` subtree.
4. Add `channel-name`, `connector`, and `direction` (`INCOMING` or `OUTGOING`).

The provider should parse and validate this configuration, but it must not open connections, create delivery threads,
poll, or retain connector instances.

### 4. Implement the stateless provider

Register the provider as a Service Registry singleton. Return a fresh, unstarted connector from every successful
factory call:

```java
@Service.Singleton
public final class AcmeConnectorProvider
        implements IncomingConnectorProvider, OutgoingConnectorProvider {
    public static final String CONNECTOR_TYPE = "example-acme";

    @Override
    public String connectorType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public IncomingConnector createIncomingConnector(Config config) {
        AcmeConnectorConfig connectorConfig =
                AcmeConnectorConfig.create(Objects.requireNonNull(config));
        requireDirection(connectorConfig, ConnectorDirection.INCOMING);
        return new AcmeIncomingConnector(connectorConfig);
    }

    @Override
    public OutgoingConnector createOutgoingConnector(Config config) {
        AcmeConnectorConfig connectorConfig =
                AcmeConnectorConfig.create(Objects.requireNonNull(config));
        requireDirection(connectorConfig, ConnectorDirection.OUTGOING);
        return new AcmeOutgoingConnector(connectorConfig);
    }

    private static void requireDirection(AcmeConnectorConfig config,
                                         ConnectorDirection expected) {
        if (config.direction() != expected) {
            throw new IllegalArgumentException("Unexpected connector direction " + config.direction());
        }
    }
}
```

The provider may be called for several channels and graphs. It must remain stateless and must never reuse a transport
client or connector lifecycle object between bindings.

### 5. Implement an incoming connector

The runtime invokes `IncomingConnector.run` once on a runtime-owned virtual thread. Establish enough transport state
to report readiness, call `context.awaitRunning()` exactly once, and acquire no delivery until it returns `true`.

Before polling, reading, or otherwise accepting a transport delivery, reserve runtime capacity. Keep the returned
delivery lease until both runtime processing and transport settlement finish. This outline uses transport-specific
placeholder types:

```java
final class AcmeIncomingConnector implements IncomingConnector {
    private final AcmeConnectorConfig config;
    private final AtomicBoolean runStarted = new AtomicBoolean();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AcmeIncomingLifecycle lifecycle = new AcmeIncomingLifecycle();
    private volatile AcmeConsumer consumer;
    private volatile Thread owner;

    AcmeIncomingConnector(AcmeConnectorConfig config) {
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
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                if (closed.get() || draining.get()) {
                                    break;
                                }
                                throw new MessagingException("Incoming connector was interrupted", e);
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
                                  String channel) throws InterruptedException {
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
                } catch (InterruptedException e) {
                    delivery.cancel();
                    abandon(transport, transportBatch, e);
                    throw e;
                } catch (RuntimeException | Error failure) {
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

If transport-to-message mapping fails before dispatch, create a metadata-only envelope and call
`reservation.startFailed(batch, failure)`. The runtime does not own the native transport record or mapper, so it cannot
repeat mapping. Bounded policies retain their configured failure-attempt accounting. An unlimited policy treats the
mapping failure as exhausted after its initial attempt so `await()` always terminates; with `FAIL`, leave the transport
delivery available for redelivery so the connector can map it again.

For a partially mapped native batch, pass a root-aligned `BatchDeliveryException`: use `FAILED` or `INDETERMINATE` for
unmappable items and `NOT_ATTEMPTED` for mapped siblings that have not reached application handlers. Never mark an
undispatched item `SUCCEEDED`. The runtime settles the failed subset first. Successful `DROP` or dead-letter settlement
then releases the `NOT_ATTEMPTED` subset into normal dispatch; `FAIL` or failed dead-letter routing terminates before
those deferred items run. Terminal batch outcomes remain aligned to the original retained batch so connectors can
settle native records by original index.

A normal return from `await()` may mean normal handler completion, `DROP`, or successful dead-letter delivery; all are
settled runtime outcomes and the complete source delivery may then be committed. The conservative outline above
abandons the complete transport batch on failure. A connector that supports partial settlement may align a
`BatchDeliveryException` to the original batch and settle only `SUCCEEDED` items, while respecting transport ordering
constraints such as contiguous committed prefixes. `FAILED`, `NOT_ATTEMPTED`, and `INDETERMINATE` items remain
unsettled. Treat an unstructured exception as indeterminate for the complete batch.

`ConnectorDelivery.close()` releases runtime admission capacity; it does not acknowledge the source. Closing it before
processing terminates requests cancellation, and capacity remains retained until processing actually stops. Commit,
acknowledge, negatively acknowledge, or abandon the transport delivery first, and only then close the lease.

`drain()` stops new acquisition but allows an acquired delivery to settle and checkpoint. `forceClose()` may run
concurrently with startup, readiness, polling, admission, or delivery processing and must promptly unblock all of them.
All close operations must be idempotent.

The runtime owns the incoming `run` virtual thread and delivery tasks. Do not create another executor for messaging
delivery. Transport libraries may still use their normal internal I/O threads. Different connector bindings and
channels can overlap, so shared native resources must be synchronized without coupling sibling connector lifecycles.

### 6. Implement an outgoing connector

`OutgoingConnector.start()` acquires binding-owned transport resources and returns only when sends can begin.
`sendBatch()` is synchronous: it must not return until the connector's documented external success point is reached.

```java
final class AcmeOutgoingConnector implements OutgoingConnector {
    private final AcmeConnectorConfig config;
    private final AcmeLifecycle lifecycle = new AcmeLifecycle();

    AcmeOutgoingConnector(AcmeConnectorConfig config) {
        this.config = config;
    }

    @Override
    public void start() {
        lifecycle.startInterruptibly(() -> AcmeProducer.open(config));
    }

    @Override
    public BatchAtomicity batchAtomicity() {
        return BatchAtomicity.PER_MESSAGE;
    }

    @Override
    public void sendBatch(MessageBatch<?> batch) {
        Objects.requireNonNull(batch);
        AcmeProducer current;
        try {
            current = lifecycle.beginSend();
        } catch (RuntimeException failure) {
            throw BatchDeliveryException.notAttempted("Acme send", batch, failure);
        }
        try {
            for (int i = 0; i < batch.size(); i++) {
                try {
                    // Await the transport acknowledgement or other documented success point.
                    current.sendAndAwait(batch.get(i));
                } catch (RuntimeException failure) {
                    throw BatchDeliveryException.sequential("Acme send", batch, i, failure);
                }
            }
        } finally {
            lifecycle.endSend();
        }
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
throwing. Use `BatchItemOutcome.failed` only when the transport proves the item did not reach its success point. Use
`BatchDeliveryException.notAttempted` for failures before any external attempt and `indeterminate` when the complete
batch outcome is unknown. Return `BatchAtomicity.ATOMIC` only when the transport guarantees one all-or-none settlement
boundary for the complete batch.

Preserve the original transport failure as the cause. Make send and startup paths interruptible; `forceClose()` itself
must run promptly to completion and perform every unblock action. Make normal and forced cleanup safe when invoked
concurrently with an active send.

### 7. Map transport messages and enforce limits

- Convert each incoming transport record to an immutable `Message<T>` and copy portable headers into globally ordered
  `MessageHeader` entries. Preserve duplicate names, exact spelling, typed values, and immutable binary snapshots when
  the transport exposes them.
- Reject or translate a null transport payload before creating its message envelope; the core `Message` contract does
  not permit null payloads.
- Use a connector-specific immutable `Message<T>` subtype when applications need native keys, offsets, destinations,
  protocol-defined properties, or other metadata. `HeaderValue.NativeValue` is an opaque encoded escape hatch for a
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
helidon:
  messaging:
    connector:
      example-acme:
        endpoint: https://broker.example

    incoming:
      orders:
        connector: example-acme
        destination: orders-in

    outgoing:
      validated-orders:
        connector: example-acme
        destination: orders-out
```

Add a generated receiver or named emitter for each configured channel, start the Service Registry application, and
verify that the provider is discovered on both the class path and module path.

### 9. Test the connector contract

At minimum, cover:

- connector type uniqueness, supported directions, the effective defaults-plus-channel overlay, injected common fields,
  stripped `failure` keys, required values, secrets, defensive copying and redaction, and direction rejection;
- Service Registry discovery and fresh, resource-free connector instances from every provider factory call; also prove
  that the provider is not a lifecycle resource, provider-registry shutdown does not close an unattached factory-created
  connector, and an attached connector is closed by its owning graph;
- incoming readiness, reserve-before-acquire ordering, message-count bounds, empty polls, ordering, immutable message
  snapshots, globally ordered duplicate and typed headers, immutable binary snapshots, exact case-sensitive names,
  oversize post-acquisition rejection, and release of every unused reservation exactly once;
- successful processing followed by transport commit, failed processing without commit, redelivery, drop, dead-letter
  completion, commit or checkpoint failure, and retention of admission through commit, nack, or abandonment;
- `tryReserveDelivery()` saturation and shared repeated-attempt timeout exhaustion, repeated-`tryStart()` budgets,
  transport maintenance while delivery runs, and capacity retention after early cancellation until processing stops;
- outgoing startup, the documented send-completion point, interruption, partial and indeterminate batch outcomes, and
  the declared `BatchAtomicity`; verify ordinary core messages and optional connector-specific message subtypes;
- aligned indexes for `SUCCEEDED`, `FAILED`, `NOT_ATTEMPTED`, and `INDETERMINATE`, preserved primary and suppressed
  causes, and—when declaring `ATOMIC`—confirmed rollback versus ambiguous commit failure for the complete batch;
- one-shot start/run, close before start/run, rejected restart, graceful drain, final checkpointing, blocked startup and
  transport calls, drain/force-close while receive returns a buffered batch, late resource publication during
  `forceClose()`, and one shared result from concurrent cleanup;
- simultaneous sibling bindings, including shared-target framing and ordering where needed, with no client, delivery,
  or lifecycle state leaking between them;
- a real-transport integration test for every supported direction, including service discovery on the module path and
  shutdown with no leaked threads or transport resources.

Finally, document connector-specific configuration and completion semantics, record third-party dependencies and
licenses, and run the connector repository's unit, integration, dependency, copyright, and style checks.
