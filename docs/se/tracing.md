# Tracing

## Overview

Distributed tracing is a critical feature of microservice based applications, since it traces workflow both within a service and across multiple services. This provides insight to sequence and timing data for specific blocks of work, which helps you identify performance and operational issues. Helidon includes support for distributed tracing through its own API, backed by either [OpenTelemetry][opentelemetry], [Jaeger](https://www.jaegertracing.io/), or [Zipkin](https://zipkin.io/). Tracing is integrated with WebServer and Security.

> [!NOTE]
> As OpenTelemetry has subsumed [OpenTracing](https://opentracing.io), Helidon support for OpenTracing is deprecated and will likely be removed in a future release.

## Maven Coordinates

To enable Helidon Tracing, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../managing-dependencies.md)).

```xml [pom.xml]
<dependencies>
  <dependency>
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing</artifactId>
  </dependency>
  <dependency>
    <groupId>io.helidon.webserver.observe</groupId>
    <artifactId>helidon-webserver-observe-tracing</artifactId>
  </dependency>
</dependencies>
```

- Helidon tracing dependency.
- Observability dependencies for tracing.

To transmit tracing data from your service to a backend, you need to add a tracing provider to your project.

For Jaeger:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.tracing.providers</groupId>
  <artifactId>helidon-tracing-providers-jaeger</artifactId>
  <scope>runtime</scope>
</dependency>
```

For Zipkin:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.tracing.providers</groupId>
  <artifactId>helidon-tracing-providers-zipkin</artifactId>
  <scope>runtime</scope>
</dependency>
```

For OpenTelemetry:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.tracing.providers</groupId>
  <artifactId>helidon-tracing-providers-opentelemetry</artifactId>
  <scope>runtime</scope>
</dependency>
```

For OpenTracing (deprecated):

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.tracing.providers</groupId>
  <artifactId>helidon-tracing-providers-opentracing</artifactId>
</dependency>
```

## Usage

This section explains a few concepts that you need to understand before you get started with tracing.

- In the context of this document, a *service* is synonymous with an application.
- A *span* is the basic unit of work done within a single service, on a single host. Every span has a name, starting timestamp, and duration. For example, the work done by a REST endpoint is a span. A span is associated to a single service, but its descendants can belong to different services and hosts.
- A *trace* contains a collection of spans from one or more services, running on one or more hosts. For example, if you trace a service endpoint that calls another service, then the trace would contain spans from both services. Within a trace, spans are organized as a directed acyclic graph (DAG) and can belong to multiple services, running on multiple hosts.
- *Baggage* is a collection of key-value pairs associated with a span.
- *Span context* captures data about a span not related to its duration, such as the tracer ID, the span ID, and baggage.

Support for specific tracers is abstracted. Your application can depend on the Helidon abstraction layer and provide a specific tracer implementation as a Java `ServiceLoader` service. Helidon provides such an implementation for:

- OpenTracing tracers, either using the `GlobalTracer`, provider resolver approach, or explicitly using Zipkin tracer
- OpenTelemetry tracers, either using the global OpenTelemetry instance, or explicitly using Jaeger tracer

### Setup WebServer

Configuring Tracer:

```java
Tracer tracer = TracerBuilder.create("helidon") 
        .build();

WebServer.builder()
        .addFeature(ObserveFeature.builder()
                            .addObserver(TracingObserver.create(tracer)) 
                            .build())
        .build()
        .start();
```

- Create a `Tracer`.
- Add an observability feature using the created `Tracer`.

### Creating Custom Spans

To create a custom span from tracer:

```java
Span span = tracer.spanBuilder("name") 
        .tag("key", "value")
        .start();

try { 
    // do some work
    span.end();
} catch (Throwable t) { 
    span.end(t);
}
```

- Create span from tracer.
- Do some work and end span.
- End span with exception.

### Handling Baggage

Your application can set and read baggage associated with a [`Span`][span]. The `Span.baggage()` method returns a [`WritableBaggage`][writablebaggage] instance.

Further, Helidon also provides read-only access to baggage linked to a [`SpanContext`][spancontext]. For example, HTTP headers can convey trace ID, span ID, and baggage information and Helidon puts such information into a `SpanContext`. Your code can create a `SpanContext` from other sources as well. The `SpanContext.baggage()` method returns a read-only [`Baggage`][baggage] instance.

The Javadoc for the types describes how to get and set baggage entries, get all the baggage keys, and check whether a baggage key exists in the baggage.

<!--@include ../includes/tracing/common-callbacks.md#span-lifecycle-callbacks -->
See [Span Lifecycle Callbacks](../includes/tracing/common-callbacks.md#span-lifecycle-callbacks).
<!--/include-->

#### Lifecycle Callbacks with OpenTelemetry Types

To use lifecycle callbacks, applications should normally work with the Helidon `Tracer`, `Span.Builder`, `Span`, and `Scope` types which automatically call back to each registered `SpanListener`.

In some cases application code might want to use a reference to an OpenTelemetry `Tracer` or `Span` *rather than* a reference to the Helidon counterpart but still want to respond to lifecycle events as the OpenTelemetry object goes through its lifecycle.

The [`HelidonOpenTelemetry`][helidonopentelem] type provides several methods which enable callbacks for OpenTelemetry objects, as summarized in the following table.

| `HelidonOpenTelemetry` method                                                                                                                                                                                                | Return value                                                                             |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| [`Tracer callbackEnabledFrom(helidonTracer)`][tracer-callbacke]       | Callback-enabled OpenTelemetry `Tracer` corresponding to the specified Helidon `Tracer`. |
| [ `Tracer callbackEnabledFrom(otelTracer)`][tracer-callbacke-2] | Callback-enabled OpenTelemetry `Tracer` for the specified OpenTelemetry `Tracer`.        |
| [ `Span callbackEnabledFrom(helidonSpan)`][span-callbackena]            | Callback-enabled OpenTelemetry `Span` corresponding to the specified Helidon `Span`.     |
| [ `Span callbackEnabledFrom(otelSpan)`][span-callbackena-2]       | Callback-enabled OpenTelemetry `Span` for the specified OpenTelemetry `Span`.            |

Enabling OpenTelemetry Objects for `SpanListener` Support

An OpenTelemetry object returned from a method on a callback-enabled object is itself callback-enabled automatically. Specifically:

- `SpanBuilder` returned from `Tracer#spanBuilder(String)`.
- `Span` returned from `SpanBuilder#startSpan`.
- `Scope` returned from `Span#makeCurrent`.

Each callback-enabled object is a new instance of a *Helidon* object which implements both the indicated OpenTelemetry interface and the Helidon [`Wrapper`][wrapper] interface. These Helidon objects *do not* themselves implement other OpenTelemetry interfaces. To do type checks and casts on callback-enabled objects, invoke the `unwrap(Class<?>)` on a callback-enabled object as shown in the following example.

```java
// Note that callbackEnabledSpan implements OpenTelemetry Span.
io.opentelemetry.api.trace.Span nativeOtelSpan = callbackEnabledSpan.unwrap(io.opentelemetry.api.trace.Span.class);
if (nativeOtelSpan instanceof io.opentelemetry.sdk.trace.ReadableSpan readableSpan) {
    // Work with the span as a ReadableSpan
}
```

Remember that operations on the `nativeOtelSpan` variable *do not* notify span listeners of lifecycle changes.

## Helidon Spans

## Traced spans

The following table lists all spans traced by Helidon components:

<!--@mdc ::table-collapse -->
| component    | span name            | description                                                                                                                                                               |
|--------------|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `web-server` | `HTTP Request`       | The overall span of the Web Server from request initiation until response Note that in `Zipkin` the name is replaced with `jax-rs` span name if `jax-rs` tracing is used. |
| `web-server` | `content-read`       | Span for reading the request entity                                                                                                                                       |
| `web-server` | `content-write`      | Span for writing the response entity                                                                                                                                      |
| `security`   | `security`           | Processing of request security                                                                                                                                            |
| `security`   | `security:atn`       | Span for request authentication                                                                                                                                           |
| `security`   | `security:atz`       | Span for request authorization                                                                                                                                            |
| `security`   | `security:response`  | Processing of response security                                                                                                                                           |
| `security`   | `security:outbound`  | Processing of outbound security                                                                                                                                           |
| `jax-rs`     | A generated name     | Span for the resource method invocation, name is generated from class and method name                                                                                     |
| `jax-rs`     | `jersey-client-call` | Span for outbound client call                                                                                                                                             |
<!--@mdc :: -->

Some of these spans `log` to the span. These log events can be (in most cases) configured:

| span name           | log name           | configurable | enabled by default | description                                              |
|---------------------|--------------------|--------------|--------------------|----------------------------------------------------------|
| `HTTP Request`      | `handler.class`    | YES          | YES                | Each handler has its class and event logged              |
| `security`          | `status`           | YES          | YES                | Logs either "status: PROCEED" or "status: DENY"          |
| `security:atn`      | `security.user`    | YES          | NO                 | The username of the user if logged in                    |
| `security:atn`      | `security.service` | YES          | NO                 | The name of the service if logged in                     |
| `security:atn`      | `status`           | YES          | YES                | Logs the status of security response (such as `SUCCESS`) |
| `security:atz`      | `status`           | YES          | YES                | Logs the status of security response (such as `SUCCESS`) |
| `security:outbound` | `status`           | YES          | YES                | Logs the status of security response (such as `SUCCESS`) |

There are also tags that are set by Helidon components. These are not configurable.

<!--@mdc ::table-collapse -->
| span name            | tag name           | description                                                                                       |
|----------------------|--------------------|---------------------------------------------------------------------------------------------------|
| `HTTP Request`       | `component`        | name of the component - `helidon-webserver`, or `jaxrs` when using MP                             |
| `HTTP Request`       | `http.method`      | HTTP method of the request, such as `GET`, `POST`                                                 |
| `HTTP Request`       | `http.status_code` | HTTP status code of the response                                                                  |
| `HTTP Request`       | `http.url`         | The path of the request (for SE without protocol, host and port)                                  |
| `HTTP Request`       | `error`            | If the request ends in error, this tag is set to `true`, usually accompanied by logs with details |
| `security`           | `security.id`      | ID of the security context created for this request (if security is used)                         |
| `jersey-client-call` | `http.method`      | HTTP method of the client request                                                                 |
| `jersey-client-call` | `http.status_code` | HTTP status code of client response                                                               |
| `jersey-client-call` | `http.url`         | Full URL of the request (such as `http://localhost:8080/greet`)                                   |
<!--@mdc :: -->

## Configuration

The following configuration should be supported by all tracer implementations (if feasible)

## Configuration options

<!--@include ../config/io.helidon.tracing.Tracer.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options](../config/io.helidon.tracing.Tracer.md#configuration-options).
<!--/include-->


### Traced Spans Configuration

Each component and its spans can be configured using Config. The traced configuration has the following layers:

- `TracingConfig` - the overall configuration of traced components of Helidon
- `ComponentTracingConfig` - a component of Helidon that traces spans (such as `web-server`, `security`, `jax-rs`)
- `SpanTracingConfig` - a single traced span within a component (such as `security:atn`)
- `SpanLogTracingConfig` - a single log event on a span (such as `security.user` in span `security:atn`)

The components using tracing configuration use the `TracingConfigUtil`. This uses the `io.helidon.common.Context` to retrieve current configuration.

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

```java
Tracer tracer = TracerBuilder.create(config.get("tracing")).build(); 
server.addFeature(ObserveFeature.builder()
                          .addObserver(TracingObserver.create(tracer)) 
                          .build());
```

- Create `Tracer` using `TracerBuilder` from configuration.
- Add the `Tracer` as an observability feature.

#### Path-based Configuration in Helidon WebServer

For Web Server we have path-based support for configuring tracing, in addition to the configuration described above.

Configuration of path can use any path string supported by the WebServer. The configuration itself has the same possibilities as traced configuration described above. The path-specific configuration will be merged with global configuration (path is the "newer" configuration, global is the "older")

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

To have a nicer overview in search pane of a tracer, you can customize the top-level span name using configuration.

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

This is supported ONLY for the span named "HTTP Request" on component "web-server".

Parameters provided:

1.  Method - HTTP method
2.  Path - path of the request (such as '/greet')
3.  Query - query of the request (may be null)

## Additional Information

### WebClient Span Propagation

Span propagation is supported with Helidon WebClient. Tracing propagation is automatic as long as the current span context is available in Helidon Context (which is automatic when running within a WebServer request).

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

### Jaeger Tracing

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.tracing</groupId>
  <artifactId>helidon-tracing-providers-jaeger</artifactId>
</dependency>
```

## Configuring Jaeger

## Configuration options

<!--@include ../config/io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options](../config/io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.md#configuration-options).
<!--/include-->


The following is an example of a Jaeger configuration, specified in the YAML format.

```yaml
tracing:
    service: "helidon-full-http"
    protocol: "https"
    host: "jaeger"
    port: 14240
```

### Jaeger Tracing Metrics

As the [Jaeger Tracing](#jaeger-tracing) section describes, you can use Jaeger tracing in your Helidon application.

### Zipkin Tracing

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.tracing.providers</groupId>
  <artifactId>helidon-tracing-providers-zipkin</artifactId>
</dependency>
```

## Configuring Zipkin

## Configuration options

<!--@include ../config/io.helidon.tracing.providers.zipkin.ZipkinTracerBuilder.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options](../config/io.helidon.tracing.providers.zipkin.ZipkinTracerBuilder.md#configuration-options).
<!--/include-->


The following is an example of a Zipkin configuration, specified in the YAML format.

```yaml [application.yaml]
tracing:
  zipkin:
    service: "helidon-service"
    protocol: "https"
    host: "zipkin"
    port: 9987
    api-version: 1
    # this is the default path for API version 2
    path: "/api/v2/spans"
    tags:
      tag1: "tag1-value"
      tag2: "tag2-value"
    boolean-tags:
      tag3: true
      tag4: false
    int-tags:
      tag5: 145
      tag6: 741
```

Example of Zipkin trace:

<figure>
<img src="../images/webserver/zipkin.png" alt="Zipkin example" />
</figure>

### OpenTelemetry Tracing

Helidon supports configuration of OpenTelemetry and OpenTelemetry tracing in two primary ways: using tracing or using telemetry. This page describes support for controlling OpenTelemetry tracing using the `tracing` config section and [`OpenTelemetryConfig` builder][opentelemetrycon]. Users typically adopt this approach to ease migration from other tracing providers (such as Jaeger) to OpenTelemetry because the tracing settings supported for OpenTelemetry are very similar to those for Jaeger.

That said, Helidon’s support for OpenTelemetry using *tracing* does not afford as much control as do the Helidon *telemetry* settings. For example, using OpenTelemetry `tracing` config you can choose either the OTLP gRPC span exporter or the OTLP HTTP one; additional span exporters are available only using the `telemetry` settings.

The [telemetry doc page][telemetry-doc-pa] describes how to use the Helidon `telemetry` config section and the related builder to exert more control over OpenTelemetry and OpenTelemetry tracing behavior.

> [!NOTE]
> If you provide settings under both `telemetry` and `tracing`, Helidon uses the `telemetry` settings. Specifying both does not confuse Helidon but it might confuse users.

Dependency for OpenTelemetry support using tracing:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.tracing.providers</groupId>
  <artifactId>helidon-tracing-providers-opentelemetry</artifactId>
</dependency>
```

## Configuring OpenTelemetry Tracing

## Configuration options

<!--@include ../config/io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracer.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options](../config/io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracer.md#configuration-options).
<!--/include-->


Example Helidon configuration for OpenTelemetry tracing:

```yaml [application.yaml]
tracing:
  service: helidon-otel-tracing-example 
  global: false      
  int-tags:
    example: 1       
  tags:
    direction: north 
```

- Specifies the OpenTelemetry service name.
- Indicates the configured tracer *should not* be made the global tracer (defaults to `true`).
- Assigns an integer-valued tag `example` the value `1`.
- Assigns a string-valued tag `direction` the value `north`.

By default, Helidon tracing support for OpenTelemetry uses OpenTelemetry’s OTLP gRPC exporter. Alternatively, you can choose to use OpenTelemetry’s HTTP exporter using protobuf by setting `exporter-type` to `http/proto`. To use other exporters OpenTelemetry offers, use the Helidon `telemetry` configuration instead of `tracing`.

## Reference

- [OpenTelemetry API][opentelemetry]
- [Opentracing Project (now part of OpenTelemetry)](https://opentracing.io/)

[opentelemetry]: https://opentelemetry.io/docs/instrumentation/js/api/tracing/
[span]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/Span.html
[writablebaggage]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/WritableBaggage.html
[spancontext]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/SpanContext.html
[baggage]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/Baggage.html
[helidonopentelem]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/HelidonOpenTelemetry.html
[tracer-callbacke]: <https://helidon.io/docs/v4/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/HelidonOpenTelemetry.html#callbackEnabledFrom(io.helidon.tracing.Tracer)>
[tracer-callbacke-2]: <https://helidon.io/docs/v4/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/HelidonOpenTelemetry.html#callbackEnabledFrom(io.opentelemetry.api.trace.Tracer)>
[span-callbackena]: <https://helidon.io/docs/v4/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/HelidonOpenTelemetry.html#callbackEnabledFrom(io.helidon.tracing.Span)>
[span-callbackena-2]: <https://helidon.io/docs/v4/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/HelidonOpenTelemetry.html#callbackEnabledFrom(io.opentelemetry.api.trace.Span)>
[wrapper]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing/io/helidon/tracing/Wrapper.html
[opentelemetrycon]: https://helidon.io/docs/v4/apidocs/io.helidon.tracing.providers.opentelemetry/io/helidon/tracing/providers/opentelemetry/OpenTelemetryTracerConfig.html
[telemetry-doc-pa]: ../se/telemetry/open-telemetry.md
