# Span Lifecycle Callbacks

### Responding to Span Lifecycle Events

Applications and libraries can register listeners to be notified at several moments during the lifecycle of every Helidon span:

- Before a new span starts
- After a new span has started
- After a span ends
- After a span is activated (creating a new scope)
- After a scope is closed

The next sections explain how you can write and add a listener and what it can do. See the [`SpanListener`][spanlistener] Javadoc for more information.

#### Understanding What Listeners Do

A listener cannot affect the lifecycle of a span or scope it is notified about, but it can add tags and events and update the baggage associated with a span. Often a listener does additional work that does not change the span or scope such as logging a message.

When Helidon invokes the listener’s methods it passes proxies for the `Span.Builder`, `Span`, and `Scope` arguments. These proxies limit the access the listener has to the span builder, span, or scope, as summarized in the following table. If a listener method tries to invoke a forbidden operation, the proxy throws a [`SpanListener.ForbiddenOperationException`][spanlistener-for] and Helidon then logs a `WARNING` message describing the invalid operation invocation.

| Tracing type                                                                       | Changes allowed                                   |
|------------------------------------------------------------------------------------|---------------------------------------------------|
| [`Span.Builder`][span-builder] | Add tags                                          |
| [`Span`][span]                 | Retrieve and update baggage, add events, add tags |
| [`Scope`][scope]               | none                                              |

Summary of Permitted Operations on Proxies Passed to Listeners

The following tables list specifically what operations the proxies permit.

<!--@mdc ::table-collapse -->
| Method                | Purpose                                                     | OK? |
|-----------------------|-------------------------------------------------------------|-----|
| `build()`             | Starts the span.                                            | \-  |
| `end` methods         | Ends the span.                                              | \-  |
| `get()`               | Starts the span.                                            | \-  |
| `kind(Kind)`          | Sets the "kind" of span (server, client, internal, etc.)    | \-  |
| `parent(SpanContext)` | Sets the parent of the span to be created from the builder. | \-  |
| `start()`             | Starts the span.                                            | \-  |
| `start(Instant)`      | Starts the span.                                            | \-  |
| `tag` methods         | Add a tag to the builder before the span is built.          | ✓   |
| `unwrap(Class)`       | Cast the builder to the specified implementation type.      | ✓   |
<!--@mdc :: -->

> [!NOTE]
> Helidon returns the unwrapped object, not a proxy for it.

[`io.helidon.tracing.Span.Builder`][span-builder] Operations

| Method             | Purpose                                                     | OK? |
|--------------------|-------------------------------------------------------------|-----|
| `activate()`       | Makes the span "current", returning a `Scope`.              | \-  |
| `addEvent` methods | Associate a string (and optionally other info) with a span. | ✓   |
| `baggage()`        | Returns the `Baggage` instance associated with the span.    | ✓   |
| `context()`        | Returns the `SpanContext` associated with the span.         | ✓   |
| `status(Status)`   | Sets the status of the span.                                | \-  |
| any `tag` method   | Add a tag to the span.                                      | ✓   |
| `unwrap(Class)`    | Cast the span to the specified implementation type.         | ✓   |

> [!NOTE]
> Helidon returns the unwrapped object, not a proxy to it.

[`io.helidon.tracing.Span`][span] Operations

| Method       | Purpose                              | OK? |
|--------------|--------------------------------------|-----|
| `close()`    | Close the scope.                     | \-  |
| `isClosed()` | Reports whether the scope is closed. | ✓   |

[`io.helidon.tracing.Scope`][scope] Operations

| Method                   | Purpose                                                      | OK? |
|--------------------------|--------------------------------------------------------------|-----|
| `asParent(Span.Builder)` | Sets this context as the parent of a new span builder.       | ✓   |
| `baggage()`              | Returns `Baggage` instance associated with the span context. | ✓   |
| `spanId()`               | Returns the span ID.                                         | ✓   |
| `traceId()`              | Returns the trace ID.                                        | ✓   |

[`io.helidon.tracing.SpanContext`][io-helidon-traci] Operations

#### Adding a Listener

##### Explicitly Registering a Listener on a [`Tracer`][tracer]

Create a `SpanListener` instance and invoke the `Tracer#register(SpanListener)` method to make the listener known to that tracer.

##### Automatically Registering a Listener on all `Tracer` Instances

Helidon also uses Java service loading to locate listeners and register them automatically on all `Tracer` objects. Follow these steps to add a listener service provider.

1.  Implement the [`SpanListener`][spanlistener] interface.
2.  Declare your implementation as a service provider:
    1.  Create the file `META-INF/services/io.helidon.tracing.SpanListener` containing a line with the fully-qualified name of your class which implements `SpanListener`.
    2.  If your service has a `module-info.java` file add the following line to it:

        ``` java
        provides io.helidon.tracing.SpanListener with <your-implementation-class>;
        ```

The `SpanListener` interface declares default no-op implementations for all the methods, so your listener can implement only the methods it needs to.

Helidon invokes each listener’s methods in the following order:

| Method                                  | When invoked                                                                                                          |
|-----------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `starting(Span.Builder<?> spanBuilder)` | Just before a span is started from its builder.                                                                       |
| `started(Span span)`                    | Just after a span has started.                                                                                        |
| `activated(Span span, Scope scope)`     | After a span has been activated, creating a new scope. A given span might never be activated; it depends on the code. |
| `closed(Span span, Scope scope)`        | After a scope has been closed.                                                                                        |
| `ended(Span span)`                      | After a span has ended successfully.                                                                                  |
| `ended(Span span, Throwable t)`         | After a span has ended unsuccessfully.                                                                                |

Order in which Helidon Invokes Listener Methods

[spanlistener]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/SpanListener.html
[spanlistener-for]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/SpanListener.ForbiddenOperationException.html
[span-builder]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/Span.Builder.html
[span]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/Span.html
[scope]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/Scope.html
[io-helidon-traci]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/SpanContext.html
[tracer]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/Tracer.html
