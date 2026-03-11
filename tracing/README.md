Distributed Tracing
---

Module `helidon-tracing` defines tracing API and SPI that is used throughout Helidon.
As we need to support both OpenTracing and OpenTelemetry tracing, this abstraction is required to keep tracing an integral part 
of Helidon.

Module `helidon-tracing-providers-opentracing` adds support for opentracing based tracers (such as Zipkin).
Module `helidon-tracing-providers-opentelemetry` adds support for opentelemetry based tracers (such as Jaeger).

# Usage

To use tracing in SE, add a dependency on `helidon-tracing` and a tracer
implementation and register tracer with server configuration.

pom.xml:
```xml
<dependency>
    <!-- to add tracer builder -->
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing</artifactId>
</dependency>
<dependency>
    <!-- to add zipkin support -->
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing-providers-zipkin</artifactId>
</dependency>
```

code using config:
```java
return WebServer.builder()
                .config(config.get("webserver"))
                .tracer(TracerBuilder.create(config.get("tracing"))
                                        .buildAndRegister())
                .build();
```

To customize configuration of zipkin, see `ZipkinTracerBuilder` javadoc. Basics:
```yaml
tracing:
  # required
  service: "service-name"
  # default is "localhost"
  host: "zipkin"
```

# Modules

## Module `helidon-tracing`
Contains an abstracted Builder for tracers and an SPI
to connect various tracer implementations.

Example:
```java
// create a tracer for service "myService"
Tracer tracer = TracerBuilder.create("myService")
    // running on host "zipkin" - probably in docker or k8s
    .collectorHost("zipkin")
    // build the tracer and register as global tracer
    .buildAndRegister();
```

Example using Config:
```java
// create a tracer from configuration
Tracer tracer = TracerBuilder.create(config.get("tracing"))    
    // build the tracer and register as global tracer
    .buildAndRegister();
```

and associated configuration:
```yaml
tracing:
  service: "myService"
  host: "zipkin"
```

## Module `helidon-tracing-providers-zipkin`
Integration with Zipkin (https://zipkin.io/). Easiest approach is to use a docker image
`zipkin` that, by default, runs on the expected hostname and port.

When you add this module to classpath, the SPI is automatically picked up and Zipkin tracer 
would be used.

See class `ZipkinTracerBuilder` for documentation of supported configuration options. The classes
in this module should not be used directly, use `helidon-tracing` module API instead (unless
you want to create a hard source code dependency on Zipkin tracer).
