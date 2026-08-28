<!--@frontmatter
description: "Helidon Micrometer metrics provider"
-->
# Micrometer Metrics

## Overview

The Helidon Micrometer provider backs the neutral
[Helidon metrics API](metrics.md) and configured metrics publishers. It
provides:

- a Micrometer-backed Helidon meter registry for application-specific metrics
- the standard `/observe/metrics` endpoint, exposed by the metrics observer
- configuration for the Prometheus and OTLP metrics publishers.

Application code which needs direct Micrometer API access can unwrap the
Micrometer registry from Helidon's service-owned meter registry.

## Maven Coordinates

To use the metrics observer and Micrometer API, add the following dependencies
to your project's `pom.xml` (see [Managing
Dependencies](../../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.observe</groupId>
  <artifactId>helidon-webserver-observe-metrics</artifactId>
</dependency>
<dependency>
  <groupId>io.helidon.metrics.providers</groupId>
  <artifactId>helidon-metrics-providers-micrometer</artifactId>
</dependency>
```

The first dependency adds the metrics observer and the `/observe/metrics`
endpoint. It includes Helidon's Micrometer-based metrics implementation at
runtime. The second dependency makes the Micrometer API available when
compiling application code which uses Micrometer types directly, as the
examples on this page do.

## Usage

Application code can create, look up, and update meters programmatically using
the Micrometer `MeterRegistry` API. The [Micrometer concepts
documentation][micrometer-concepts] provides an introduction to Micrometer's
interfaces and classes.

> [!IMPORTANT]
> Helidon does not use Micrometer's process-wide `Metrics.globalRegistry` as
> its backing registry. Applications upgrading from earlier Helidon versions
> which registered meters with `Metrics.globalRegistry` must switch to
> Helidon's service-owned registry; otherwise, Helidon's configured metrics
> publishers do not expose those meters. Obtain Helidon's
> `io.helidon.metrics.api.MeterRegistry` using injection or
> `Services.get(io.helidon.metrics.api.MeterRegistry.class)` and invoke
> `unwrap(io.micrometer.core.instrument.MeterRegistry.class)`.

The following example obtains Helidon's service-owned Micrometer registry and
passes it to an HTTP service.

### Register a MeterRegistry-backed Service with the Web Server

<!--@mdc ::code-callout -->
```java
MeterRegistry registry = Services.get(io.helidon.metrics.api.MeterRegistry.class)
        .unwrap(MeterRegistry.class); // <1>
MyService myService = new MyService(registry); // <2>

WebServer.builder()
        .routing(r -> r.register("/myapp", myService)) // <3>
        .build();
```
1. Acquire Helidon's service-owned meter registry and unwrap the Micrometer
   registry which backs it.
2. Create the service using that registry.
3. Register the service with the web server.
<!--@mdc :: -->

### Create and Update Meters in Your Application Service

<!--@mdc ::code-callout -->
```java
class MyService implements HttpService {

    final Counter requestCounter;

    MyService(MeterRegistry registry) {
        requestCounter = registry.counter("allRequests"); // <1>
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .any(this::countRequests) // <2>
                .get("/", this::myGet);
    }

    void countRequests(ServerRequest request, ServerResponse response) {
        requestCounter.increment(); // <3>
        response.next();
    }

    void myGet(ServerRequest request, ServerResponse response) {
        response.send("OK");
    }
}
```
1. Create a counter in the Micrometer meter registry.
2. Route each request through the counter handler.
3. Increment the counter and continue request processing.
<!--@mdc :: -->

## Configuration

Helidon's Micrometer support is exposed through the Micrometer-backed metrics
publishers. Configure publishers under `metrics.publishers` or under
`server.features.observe.observers.metrics.publishers` for observer-specific
settings. If you do not configure any publishers explicitly, Helidon infers a
Prometheus publisher.

### Metrics Endpoint

Helidon's metrics observer exposes the service-owned registry through a REST
endpoint, by default at `/observe/metrics`. See the
[Helidon Metrics documentation](metrics.md#metrics-endpoint) for supported
response formats and other endpoint behavior.

Override the observer endpoint using its builder or the
`server.features.observe.observers.metrics.endpoint` configuration key:

```yaml [application.yaml]
server:
  features:
    observe:
      observers:
        metrics:
          endpoint: my-metrics
```

### Publisher Configuration

Configure the supported publishers by name:

```yaml [application.yaml]
metrics:
  publishers:
    prometheus:
    otlp:
      url: http://localhost:4318/v1/metrics
```

### Prometheus Publisher Configuration Options

<!--@include ../../config/io.helidon.metrics.providers.micrometer.PrometheusPublisher.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Prometheus publisher configuration options][prometheus-config].
<!--/include-->

### OTLP Publisher Configuration Options

<!--@include ../../config/io.helidon.metrics.providers.micrometer.OtlpPublisher.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [OTLP publisher configuration options][otlp-config].
<!--/include-->

## Additional Information

The [Micrometer website](https://micrometer.io) describes the project and links
to further documentation.

[micrometer-concepts]: https://docs.micrometer.io/micrometer/reference/concepts
[prometheus-config]: ../../config/io.helidon.metrics.providers.micrometer.PrometheusPublisher.md#configuration-options
[otlp-config]: ../../config/io.helidon.metrics.providers.micrometer.OtlpPublisher.md#configuration-options
