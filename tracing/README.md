Distributed Tracing
---

Both Helidon SE and Helidon MP use Opentracing API for tracing
events.

_Eclipse Microprofile Opentracing specification is not yet implemented_

# Usage
## Usage In Helidon MP
To use tracing in MP, simply depend on the `helidon-microprofile-tracing` module to automate
setup of tracing, and add your favorite tracer implementation to the classpath (currently Zipkin is the only
favorite tracer implemented, though SPI is available).

Example of pom.xml dependencies:
```xml
<dependency>
    <!-- general support for tracing -->
    <groupId>io.helidon.microprofile.tracing</groupId>
    <artifactId>helidon-microprofile-tracing</artifactId>
</dependency>
<dependency>
    <!-- Zipkin tracer implementation -->
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing-zipkin</artifactId>
</dependency>
```

To customize configuration of zipkin, see `ZipkinTracerBuilder` javadoc. Basics:
```yaml
tracing:
  # required
  service: "service-name"
  # default is "localhost"
  host: "zipkin"
  # default is 9411
  port: 9411
```

## Usage in Helidon SE
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
    <artifactId>helidon-tracing-zipkin</artifactId>
</dependency>
```

code using config:
```java
return ServerConfiguration.builder()
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

### Usage of Jersey client outside of Helidon MP
Jersey client can be used (maybe standalone, or in Helidon SE) and traced.
Simple add `helidon-tracing-jersey-client` as a dependency and correctly configure
the request properties. The client expects Tracer to be configured (see zipkin examples above)
 
Note that in Helidon MP, this would work automatically with no additional configuration if you 
carry out the steps described in "Usage in Helidon MP".

```xml
<dependency>
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing-jersey-client</artifactId>
</dependency>
```

And in code:
```java
response = webTarget
            .request()
            // tracer information - not required if global tracer should be used
            .property(ClientTracingFilter.TRACER_PROPERTY_NAME, tracer)
            // the threadContext tracing span context to be used as a parent for outbound request
            // if not provided a new span with no parent would be created
            .property(ClientTracingFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME, spanContext)
            .get();
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

## Module `helidon-tracing-jersey-client`
Integration with Jersey client to add tracing support
 for outbound requests.
 
The client is registered automatically with Jersey (e.g. no need to add any filter).

There are two modes of usage:
1. Use within a Jersey server (requires `helidon-tracing-jersey` with a registered filter, or `helidon-microprofile-tracing`)
2. Standalone use

### Use within Jersey server
The server filters create a context for the client, so as long as the client is invoked
within the same thread, no additional configuration is required - just use 
client as usual and tracing will be propagated and added for outbound call.

### Standalone use
In case the Jersey client is used on its own, the tracing filter cannot obtain 
information needed for outbound call and it has to be provided.

Example of use:
```java
response = webTarget
            .request()
            // tracer information - not required if global tracer should be used
            .property(ClientTracingFilter.TRACER_PROPERTY_NAME, tracer)
            // the threadContext tracing span context to be used as a parent for outbound request
            // if not provided a new span with no parent would be created
            .property(ClientTracingFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME, spanContext)
            .get();
``` 

## Module `helidon-tracing-jersey`
Integration with "pure" Jersey server. This module should not be directly used when using
Helidon MP, use `helidon-microprofile-tracing` instead.

To configure tracing with Jersey, add `TracingFilter` to your application/resource configuration.
The tracing filter will start a new span for each jersey call and register context for client calls.
`helidon-tracing-jersey-client` is a transitive dependency of this module.

## Module `helidon-microprofile-tracing`
Provides automated integration with Helidon MP, including automated configuration of
tracer and of server-side filters that register context for client calls and trace each 
request (unless explicitly disabled using configuration)
This module is located in /microprofile/tracing

## Module `helidon-tracing-zipkin`
Integration with Zipkin (https://zipkin.io/). Easiest approach is to use a docker image
`zipkin` that, by default, runs on the expected hostname and port.

When you add this module to classpath, the SPI is automatically picked up and Zipkin tracer 
would be used.

See class `ZipkinTracerBuilder` for documentation of supported configuration options. The classes
in this module should not be used directly, use `helidon-tracing` module API instead (unless
you want to create a hard source code dependency on Zipkin tracer).
