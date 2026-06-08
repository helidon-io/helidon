Distributed Tracing
---

Module `helidon-tracing` defines tracing API and SPI that is used throughout Helidon.
The API is backed by OpenTelemetry through the `helidon-tracing-providers-opentelemetry` provider.

Module `helidon-tracing-providers-opentelemetry` adds OpenTelemetry tracing support.

# Usage

To use tracing in SE, add a dependency on `helidon-tracing` and a tracer
implementation, then register the tracer with server configuration.

pom.xml:
```xml
<dependency>
    <!-- to add tracer builder -->
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing</artifactId>
</dependency>
<dependency>
    <!-- to add tracing observability support to WebServer -->
    <groupId>io.helidon.webserver.observe</groupId>
    <artifactId>helidon-webserver-observe-tracing</artifactId>
</dependency>
<dependency>
    <!-- to add OpenTelemetry support -->
    <groupId>io.helidon.tracing.providers</groupId>
    <artifactId>helidon-tracing-providers-opentelemetry</artifactId>
</dependency>
```

code using config:
```java
Tracer tracer = TracerBuilder.create(config.get("tracing"))
        .build();

return WebServer.builder()
        .config(config.get("webserver"))
        .addFeature(ObserveFeature.just(TracingObserver.create(tracer)))
        .build();
```

Basic tracing configuration:
```yaml
tracing:
  # required
  service: "service-name"
  # default is "localhost"
  host: "otel-collector"
```

# Modules

## Module `helidon-tracing`
Contains the API, an abstracted builder for tracers, and an SPI to connect tracer implementations.

Example:
```java
// create a tracer for service "myService"
Tracer tracer = TracerBuilder.create("myService")
    // running on host "otel-collector" - probably in docker or k8s
    .collectorHost("otel-collector")
    // build the tracer
    .build();
```

Example using Config:
```java
// create a tracer from configuration
Tracer tracer = TracerBuilder.create(config.get("tracing"))
    // build the tracer
    .build();
```

and associated configuration:
```yaml
tracing:
  service: "myService"
  host: "otel-collector"
```

## Module `helidon-tracing-providers-opentelemetry`
Integration with OpenTelemetry. When you add this module to the classpath, the tracing SPI picks up the
OpenTelemetry tracer provider automatically.
