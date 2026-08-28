<!--@frontmatter
description: "Helidon Tracing Support"
navigation:
  icon: i-lucide-activity
-->
# Tracing

## Overview

Distributed tracing is a critical feature of microservice based applications,
since it traces workflow both within a service and across multiple services.
This provides insight to sequence and timing data for specific blocks of work,
which helps you identify performance and operational issues. Helidon includes
support for distributed tracing through its own API, backed by
[OpenTelemetry][opentelemetry]. Tracing is integrated with WebServer and
Security.

## Maven Coordinates

To enable Helidon Tracing, add the following dependency to your project’s
`pom.xml` (see [Managing Dependencies](../dependency-management.md)).

<!--@mdc ::code-callout -->
```xml [pom.xml]
<dependencies>
    <dependency>
        <groupId>io.helidon.tracing</groupId>
        <artifactId>helidon-tracing</artifactId>    <!-- (1) -->
    </dependency>
    <dependency>
        <groupId>io.helidon.webserver.observe</groupId>
        <artifactId>helidon-webserver-observe-tracing</artifactId> <!-- (2) -->
    </dependency>
    <dependency>
        <groupId>io.helidon.tracing.providers</groupId>
        <artifactId>helidon-tracing-providers-opentelemetry</artifactId> <!-- (3) -->
        <scope>runtime</scope>
    </dependency>
</dependencies>
```
1. Helidon tracing dependency.
2. Observability dependencies for tracing.
3. OpenTelemetry tracing provider.
<!--@mdc :: -->

## Usage

This section explains a few concepts that you need to understand before you get
started with tracing.

- In the context of this document, a *service* is synonymous with an
  application.
- A *span* is the basic unit of work done within a single service, on a single
  host. Every span has a name, starting timestamp, and duration. For example,
  the work done by a REST endpoint is a span. A span is associated to a single
  service, but its descendants can belong to different services and hosts.
- A *trace* contains a collection of spans from one or more services, running on
  one or more hosts. For example, if you trace a service endpoint that calls
  another service, then the trace would contain spans from both services. Within
  a trace, spans are organized as a directed acyclic graph (DAG) and can belong
  to multiple services, running on multiple hosts.
- *Baggage* is a collection of key-value pairs associated with a span.
- *Span context* captures data about a span not related to its duration, such as
  the tracer ID, the span ID, and baggage.

Support for specific tracers is abstracted. Your application can depend on the
Helidon abstraction layer and provide a specific tracer implementation as a
Java `ServiceLoader` service. Helidon provides OpenTelemetry tracers configured
from Helidon tracing settings.

### Migrating from Global Tracer APIs

Helidon 27 removes the deprecated static APIs for assigning or retrieving the
Helidon global tracer. The OpenTelemetry helper methods which assigned a
Helidon global tracer, such as `HelidonOpenTelemetry.global(...)` and
`OpenTelemetryTracerProvider.globalTracer(...)`, are also removed. Application
code should obtain the application `Tracer` from the Helidon service registry,
using either declarative injection or `Services.get(Tracer.class)`.

If an application prepares its own tracing runtime, register the manually
prepared `OpenTelemetry` and `Tracer` instances with `Services.set(...)` before
any application or Helidon component requests either contract from the service
registry. Once a registry singleton is resolved, replacing it is not supported.

Some OpenTelemetry instrumentation reads `GlobalOpenTelemetry` directly. For
that interop case, configure `telemetry.global: true` or `tracing.global: true`
so Helidon publishes the selected application `OpenTelemetry` instance to the
OpenTelemetry global during service-registry ownership startup. This must happen
before any other code initializes `GlobalOpenTelemetry`, because OpenTelemetry
allows the JVM-wide global to be assigned only once. If an OpenTelemetry global
already exists, Helidon leaves it unchanged and uses the existing global as the
application `OpenTelemetry` instance in the service registry.

### WebServer Setup

Configuring Tracer:

<!--@mdc ::code-callout -->
```java
Tracer tracer = TracerBuilder.create("helidon") // <1>
        .build();

WebServer.builder()
        .addFeature(ObserveFeature.builder()
                            .addObserver(TracingObserver.create(tracer)) // <2>
                            .build())
        .build()
        .start();
```
1. Create a `Tracer`.
2. Add an observability feature using the created `Tracer`.
<!--@mdc :: -->

### Custom Spans

To create a custom span from tracer:

<!--@mdc ::code-callout -->
```java
Span span = tracer.spanBuilder("name") // <1>
        .tag("key", "value")
        .start();

try { // <2>
    // do some work
    span.end();
} catch (Throwable t) { // <3>
    span.end(t);
}
```
1. Create span from tracer.
2. Do some work and end span.
3. End span with exception.
<!--@mdc :: -->

## Handling Baggage

Your application can set and read baggage associated with a [`Span`][span]. The
`Span.baggage()` method returns a [`WritableBaggage`][writablebaggage] instance.

Further, Helidon also provides read-only access to baggage linked to a
[`SpanContext`][spancontext]. For example, HTTP headers can convey trace ID,
span ID, and baggage information and Helidon puts such information into a
`SpanContext`. Your code can create a `SpanContext` from other sources as well.
The `SpanContext.baggage()` method returns a read-only [`Baggage`][baggage]
instance.

The Javadoc for the types describes how to get and set baggage entries, get all
the baggage keys, and check whether a baggage key exists in the baggage.

## Span Lifecycle

<!--@include ../includes/tracing/common-callbacks.md#span-lifecycle offset=1 -->
See [Span Lifecycle Callbacks][span-lifecycle-c].
<!--/include-->

### OpenTelemetry Callbacks

To use lifecycle callbacks, applications should normally work with the Helidon
`Tracer`, `Span.Builder`, `Span`, and `Scope` types which automatically call
back to each registered `SpanListener`.

In some cases application code might want to use a reference to an OpenTelemetry
`Tracer` or `Span` *rather than* a reference to the Helidon counterpart but
still want to respond to lifecycle events as the OpenTelemetry object goes
through its lifecycle.

The [`HelidonOpenTelemetry`][helidonopentelem] type provides several methods
which enable callbacks for OpenTelemetry objects, as summarized in the following
table.

<table>
<thead>
<th>Method</th>
<th>Return Value</th>
</thead>
<tr>
<td>
<a href="https://helidon.io/docs/v27/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/HelidonOpenTelemetry.html#callbackEnabledFrom(io.helidon.tracing.Tracer)">
<code>Tracer callback<wbr>Enabled<wbr>From(<wbr>helidon<wbr>Tracer)</code>
</a>
</td>
<td>
Callback-enabled OpenTelemetry <code>Tracer</code> corresponding to the
specified Helidon <code>Tracer</code>
</td>
</tr>
<tr>
<td>
<a href="https://helidon.io/docs/v27/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/HelidonOpenTelemetry.html#callbackEnabledFrom(io.opentelemetry.api.trace.Tracer)">
<code>Tracer callback<wbr>Enabled<wbr>From(<wbr>otel<wbr>Tracer)</code>
</a>
</td>
<td>
Callback-enabled OpenTelemetry <code>Tracer</code> for the specified
OpenTelemetry <code>Tracer</code>
</td>
</tr>
<tr>
<td>
<a href="https://helidon.io/docs/v27/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/HelidonOpenTelemetry.html#callbackEnabledFrom(io.helidon.tracing.Span)">
<code>Span callback<wbr>Enabled<wbr>From(<wbr>helidon<wbr>Span)</code>
</a>
</td>
<td>
Callback-enabled OpenTelemetry <code>Span</code> corresponding to the specified
Helidon <code>Span</code>
</td>
</tr>
<tr>
<td>
<a href="https://helidon.io/docs/v27/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/HelidonOpenTelemetry.html#callbackEnabledFrom(io.opentelemetry.api.trace.Span)">
<code>Span callback<wbr>Enabled<wbr>From(<wbr>otel<wbr>Span)</code>
</a>
</td>
<td>
Callback-enabled OpenTelemetry <code>Span</code> for the specified OpenTelemetry
<code>Span</code>
</td>
</tr>
</table>

Enabling OpenTelemetry Objects for `SpanListener` Support

An OpenTelemetry object returned from a method on a callback-enabled object is
itself callback-enabled automatically. Specifically:

- `SpanBuilder` returned from `Tracer#spanBuilder(String)`.
- `Span` returned from `SpanBuilder#startSpan`.
- `Scope` returned from `Span#makeCurrent`.

Each callback-enabled object is a new instance of a *Helidon* object which
implements both the indicated OpenTelemetry interface and the Helidon
[`Wrapper`][wrapper] interface. These Helidon objects *do not* themselves
implement other OpenTelemetry interfaces. To do type checks and casts on
callback-enabled objects, invoke the `unwrap(Class<?>)` on a callback-enabled
object as shown in the following example.

```java
// Note that callbackEnabledSpan implements OpenTelemetry Span.
io.opentelemetry.api.trace.Span nativeOtelSpan = callbackEnabledSpan.unwrap(io.opentelemetry.api.trace.Span.class);
if (nativeOtelSpan instanceof ReadableSpan readableSpan) {
    // Work with the span as a ReadableSpan
}
```

Remember that operations on the `nativeOtelSpan` variable *do not* notify span
listeners of lifecycle changes.

## Configuration options

<!--@include ../config/io.helidon.tracing.Tracer.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-traci].
<!--/include-->

## Helidon Spans

The following table lists all spans traced by Helidon components:

<!--@mdc ::table-collapse -->
| component    | span name            | description                                                                                                                                                               |
|--------------|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `web-server` | `HTTP Request`       | The overall span of the Web Server from request initiation until response. |
| `web-server` | `content-read`       | Span for reading the request entity                                                                                                                                       |
| `web-server` | `content-write`      | Span for writing the response entity                                                                                                                                      |
| `security`   | `security`           | Processing of request security                                                                                                                                            |
| `security`   | `security:atn`       | Span for request authentication                                                                                                                                           |
| `security`   | `security:atz`       | Span for request authorization                                                                                                                                            |
| `security`   | `security:response`  | Processing of response security                                                                                                                                           |
| `security`   | `security:outbound`  | Processing of outbound security                                                                                                                                           |
<!--@mdc :: -->

Some of these spans `log` to the span. These log events can be (in most cases)
configured:

| span name           | log name           | configurable | enabled by default | description                                              |
|---------------------|--------------------|--------------|--------------------|----------------------------------------------------------|
| `HTTP Request`      | `handler.class`    | YES          | YES                | Each handler has its class and event logged              |
| `security`          | `status`           | YES          | YES                | Logs either "status: PROCEED" or "status: DENY"          |
| `security:atn`      | `security.user`    | YES          | NO                 | The username of the user if logged in                    |
| `security:atn`      | `security.service` | YES          | NO                 | The name of the service if logged in                     |
| `security:atn`      | `status`           | YES          | YES                | Logs the status of security response (such as `SUCCESS`) |
| `security:atz`      | `status`           | YES          | YES                | Logs the status of security response (such as `SUCCESS`) |
| `security:outbound` | `status`           | YES          | YES                | Logs the status of security response (such as `SUCCESS`) |

There are also tags that are set by Helidon components. These are not
configurable.

<!--@mdc ::table-collapse -->
| span name            | tag name           | description                                                                                       |
|----------------------|--------------------|---------------------------------------------------------------------------------------------------|
| `HTTP Request`       | `component`        | name of the component, `helidon-webserver`                                                        |
| `HTTP Request`       | `http.method`      | HTTP method of the request, such as `GET`, `POST`                                                 |
| `HTTP Request`       | `http.status_code` | HTTP status code of the response                                                                  |
| `HTTP Request`       | `http.url`         | The path of the request (for Helidon without protocol, host and port)                             |
| `HTTP Request`       | `error`            | If the request ends in error, this tag is set to `true`, usually accompanied by logs with details |
| `security`           | `security.id`      | ID of the security context created for this request (if security is used)                         |
<!--@mdc :: -->

### Configuration

Each component and its spans can be configured using Config. The traced
configuration has the following layers:

- `TracingConfig` - the overall configuration of traced components of Helidon
- `ComponentTracingConfig` - a component of Helidon that traces spans (such as
  `web-server` and `security`)
- `SpanTracingConfig` - a single traced span within a component (such as
  `security:atn`)
- `SpanLogTracingConfig` - a single log event on a span (such as `security.user`
  in span `security:atn`)

The components using tracing configuration use the `TracingConfigUtil`. This
uses the `io.helidon.common.Context` to retrieve current configuration.

#### Configuration Using Builder

Builder approach, example that disables a single span log event:

Configure tracing using a builder:

```java
TracingConfig.builder()
    .addComponent(ComponentTracingConfig.builder("web-server")
        .addSpan(SpanTracingConfig.builder("HTTP Request")
            .addSpanLog(SpanLogTracingConfig.builder("content-write")
                 .enabled(false)
                 .build())
            .build())
        .build())
    .build();
```

#### Configuration using Helidon Config

Tracing configuration can be defined in a config file.

Tracing configuration:

```yaml
tracing:
    components:
      web-server:
        spans:
          - name: "HTTP Request"
            logs:
              - name: "content-write"
                enabled: false
```

Use the configuration in web server:

<!--@mdc ::code-callout -->
```java
Tracer tracer = TracerBuilder.create(config.get("tracing")).build(); // <1>
server.addFeature(ObserveFeature.builder()
                          .addObserver(TracingObserver.create(tracer)) // <2>
                          .build());
```
1. Create `Tracer` using `TracerBuilder` from configuration.
2. Add the `Tracer` as an observability feature.
<!--@mdc :: -->

#### Path-based Configuration in Helidon WebServer

For Web Server we have path-based support for configuring tracing, in addition
to the configuration described above.

Configuration of path can use any path string supported by the WebServer. The
configuration itself has the same possibilities as traced configuration
described above. The path-specific configuration will be merged with global
configuration (path is the "newer" configuration, global is the "older")

Configuration in YAML:

```yaml
tracing:
  paths:
    - path: "/favicon.ico"
      enabled: false
    - path: "/metrics"
      enabled: false
    - path: "/health"
      enabled: false
    - path: "/greet"
      components:
        web-server:
          spans:
          - name: "content-read"
            new-name: "read"
            enabled: false
```

#### Renaming top level span using request properties

To have a nicer overview in search pane of a tracer, you can customize the
top-level span name using configuration.

Example:

Configuration in YAML:

```yaml
tracing:
  components:
    web-server:
      spans:
      - name: "HTTP Request"
        new-name: "HTTP %1$s %2$s"
```

This is supported ONLY for the span named "HTTP Request" on component
"web-server".

Parameters provided:

1.  Method - HTTP method
2.  Path - path of the request (such as `/greet`)
3.  Query - query of the request (maybe null)

## WebClient Propagation

Span propagation is supported with Helidon WebClient. Tracing propagation is
automatic as long as the current span context is available in Helidon Context
(which is automatic when running within a WebServer request).

```xml [pom.xml]
<dependencies>
  <dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient</artifactId>
  </dependency>
  <dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-tracing</artifactId>
  </dependency>
</dependencies>
```

Tracing propagation with Helidon WebClient:

```java
WebClient client = WebClient.builder()
        .addService(WebClientTracing.create())
        .build();

String response = client.get()
        .uri(uri)
        .requestEntity(String.class);
```


## OpenTelemetry Tracing

Helidon supports configuration of OpenTelemetry and OpenTelemetry tracing in two
primary ways: using tracing or using telemetry. This page describes support for
controlling OpenTelemetry tracing using the `tracing` config section and
[`OpenTelemetryTracerConfig` builder][opentelemetrycon]. Users typically adopt
this approach to ease migration from older tracing configuration to
OpenTelemetry.

That said, Helidon’s support for OpenTelemetry using *tracing* does not afford
as much control as do the Helidon *telemetry* settings. For example, using
OpenTelemetry `tracing` config you can choose either the OTLP gRPC span exporter
or the OTLP HTTP one; additional span exporters are available only using the
`telemetry` settings.

The [telemetry doc page][telemetry-doc-pa] describes how to use the Helidon
`telemetry` config section and the related builder to exert more control over
OpenTelemetry and OpenTelemetry tracing behavior.

> [!NOTE]
> If you provide settings under both `telemetry` and `tracing`, Helidon uses the
> `telemetry` settings. Specifying both does not confuse Helidon, but it might
> confuse users. Set `registered: false` under `tracing` to make explicit that
> the `tracing` section should not contribute the application-wide
> `OpenTelemetry` and `Tracer`.

Dependency for OpenTelemetry support using tracing:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.tracing.providers</groupId>
  <artifactId>helidon-tracing-providers-opentelemetry</artifactId>
</dependency>
```

### Configuration options

<!--@include ../config/io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracer.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-traci-4].
<!--/include-->

Example Helidon configuration for OpenTelemetry tracing:

<!--@mdc ::code-callout -->
```yaml [application.yaml]
tracing:
  service: helidon-otel-tracing-example # <1>
  global: true       # <2>
  int-tags:
    example: 1       # <3>
  tags:
    direction: north # <4>
```
1. Specifies the OpenTelemetry service name.
2. Indicates Helidon should publish the selected application `OpenTelemetry`
   instance to `GlobalOpenTelemetry` (defaults to `true`). Set this to `false`
   when application code or OpenTelemetry autoconfiguration controls the
   OpenTelemetry global.
3. Assigns an integer-valued tag `example` the value `1`.
4. Assigns a string-valued tag `direction` the value `north`.
<!--@mdc :: -->

Set `tracing.registered` to `false` only when the `tracing` section should not
contribute the application-wide `OpenTelemetry` and `Tracer` from the Helidon
service registry. OpenTelemetry globals are JVM-wide and can be assigned only
once, so any `tracing.global: true` publication must happen before any Java
agent, OpenTelemetry autoconfiguration, or application code initializes
`GlobalOpenTelemetry`. If an OpenTelemetry global already exists, Helidon leaves
it unchanged and uses the existing global as the application `OpenTelemetry`
instance in the service registry.

By default, Helidon tracing support for OpenTelemetry uses OpenTelemetry’s OTLP
gRPC exporter. Alternatively, you can choose to use OpenTelemetry’s HTTP
exporter using protobuf by setting `exporter-type` to `http/proto`. To use other
exporters OpenTelemetry offers, use the Helidon `telemetry` configuration
instead of `tracing`.

## Reference

- [OpenTelemetry API][opentelemetry]

[opentelemetry]: https://opentelemetry.io/docs/instrumentation/js/api/tracing/
[span]: https://helidon.io/docs/v27/apidocs/io.helidon.tracing/io/helidon/tracing/Span.html
[writablebaggage]: https://helidon.io/docs/v27/apidocs/io.helidon.tracing/io/helidon/tracing/WritableBaggage.html
[spancontext]: https://helidon.io/docs/v27/apidocs/io.helidon.tracing/io/helidon/tracing/SpanContext.html
[baggage]: https://helidon.io/docs/v27/apidocs/io.helidon.tracing/io/helidon/tracing/Baggage.html
[helidonopentelem]: https://helidon.io/docs/v27/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/HelidonOpenTelemetry.html
[wrapper]: https://helidon.io/docs/v27/apidocs/io.helidon.common/io/helidon/common/Wrapper.html
[opentelemetrycon]: https://helidon.io/docs/v27/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/OpenTelemetryTracerConfig.html
[telemetry-doc-pa]: telemetry/opentelemetry.md
[span-lifecycle-c]: ../includes/tracing/common-callbacks.md#span-lifecycle
[io-helidon-traci]: ../config/io.helidon.tracing.Tracer.md#configuration-options
[io-helidon-traci-4]: ../config/io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracer.md#configuration-options
