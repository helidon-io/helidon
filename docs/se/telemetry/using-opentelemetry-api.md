# Using OpenTelemetry Directly in Helidon SE

## Overview

For developers of Helidon services, OpenTelemetry’s components are—​in all respects—​just like any other group of libraries a developer might use from an application. That said, OpenTelemetry is very feature-rich, comprising several libraries and involving considerable flexibility in setting up its runtime behavior. As a result, developers sometimes find it challenging to get the correct combination of dependencies and the right runtime behavior.

Helidon SE offers robust built-in and automatic support of OpenTelemetry that is particularly well suited for applications which use the Helidon neutral APIs for metrics and tracing. You can use Helidon configuration to control the OpenTelemetry runtime environment: exporters, tracing processors, metrics readers, etc.

Even so, some developers might want to use the OpenTelemetry APIs directly from a Helidon SE service.

You have several options in approaching such a project.

### Using the Helidon OpenTelemetry Integration

You can use the [Helidon OpenTelemetry integration](open-telemetry.md) *and also* use the OpenTelemetry API directly.

Helidon’s integration prepares the OpenTelemetry `GlobalOpenTelemetry` instance according to the `telemetry` section in your Helidon configuration. Helidon also makes this instance available via Helidon declarative’s `@Service.Inject` and programmatically with `Services.get(OpenTelemetry.class)`. Your code then uses the global `OpenTelemetry` instance—​obtained in any of these ways—​as the entry point to the OpenTelemetry API to work with metrics and spans.

You can also optionally have Helidon provide the tracing spans and metrics prescribed by the [OpenTelemetry semantic conventions](https://opentelemetry.io/docs/concepts/semantic-conventions/) simply by adding a dependency.

If you must use the OpenTelemetry API from your code, Helidon recommends this option. The [Helidon OpenTelemetry integration](open-telemetry.md) documentation explains how to use the Helidon integration.

### Using OpenTelemetry AutoConfiguration

The [OpenTelemetry auto-configure feature](https://opentelemetry.io/docs/languages/java/configuration/) allows you to control many operational aspects of OpenTelemetry using environment variables, Java system properties, or—​with a little extra code—​config files rather than writing your own explicit Java code to prepare the runtime. Then your code can use the OpenTelemetry API as needed to manage tracing spans or metrics.

This approach prepares the OpenTelemetry runtime using OpenTelemetry’s autoconfiguration instead of Helidon’s integration with OpenTelemetry, but from there the programmatic use of the OpenTelemetry API is essentially the same in those two options: obtain the global `OpenTelemetry` instance and then use it to create and update spans and metrics. In this option your code must use the `GlobalOpenTelemetry` type; Helidon cannot provide the global instance via injection or Helidon services look-up.

### Using Only the OpenTelemetry API

You can use the [OpenTelemetry API](https://github.com/open-telemetry/opentelemetry-java) exclusively (without auto-configure) to set up the OpenTelemetry runtime environment at start-up, then use the API to work with tracing spans or metrics.

Your code has full control—​and therefore full responsibility—​for preparing OpenTelemetry programmatically.

### Using the OpenTelemetry Java Agent with Helidon

OpenTelemetry offers a feature whereby it automatically instruments certain aspects of applications without requiring any changes to the application code. It relies on autoconfiguration to prepare the runtime, then intercepts HTTP requests to deal with spans and metrics.

A third-party contributor has developed a module that provides [automatic instrumentation for Helidon services](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/helidon-4.3/library).

## Building a Helidon SE Service with OpenTelemetry Autoconfiguration

The rest of this document outlines how to plan and develop a Helidon SE application that uses the OpenTelemetry libraries with OpenTelemetry auto-configuration. Developers who need to use the OpenTelemetry API but choose not to use the Helidon integration should find this the next most straightforward approach.

This page assumes readers know how to get started developing a Helidon application and focuses on what you need to know to use OpenTelemetry directly.

### Adding OpenTelemetry Dependencies

Assuming you already have a project started and have the relevant Helidon dependencies declared (for the webserver, health, etc.), you need to add the required OpenTelemetry components.

If you use a parent POM other than the Helidon SE application parent, include the OpenTelemetry BOM:

*Including the OpenTelemetry BOM*

```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.opentelemetry</groupId>
                <artifactId>opentelemetry-bom</artifactId>
                <version>1.58.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

See the [example app](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/telemetry/otel-auto-configure) which inspires the illustrations below.

Add the dependencies below. (You might be able to get away with fewer if you are willing to rely on transitive dependencies for components you use in your source code. The example shows them explicitly for completeness.)

*Dependencies for the OpenTelemetry API and Autoconfiguration*

```xml
<!-- OpenTelemetrySdkBuilder -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
</dependency>

<!-- Resource -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-common</artifactId>
</dependency>

<!-- Context -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-context</artifactId>
</dependency>

<!-- autoconfigure -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-extension-autoconfigure</artifactId>
</dependency>

<!-- Uses the JDK client for exporters rather than okhttp -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-sender-jdk</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Adds the OTLP exporter to the classpath for when the user configures the app to use the  'otlp' exporter. -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <scope>runtime</scope>
</dependency>
```

Note the runtime dependency for `io.opentelemetry:opentelemetry-exporter-otlp`. Make sure to add a dependency for any OpenTelemetry exporter you want your end users to be able to configure. OpenTelemetry detects a missing exporter dependency during start-up and throws an exception explaining which one to add.

### Set OpenTelemetry AutoConfigure Settings

OpenTelemetry directly supports Java system properties and environment variables for autoconfiguring the OpenTelemetry runtime. The [OpenTelemetry autoconfiguration documentation](https://opentelemetry.io/docs/languages/java/configuration/#environment-variables-and-system-properties) describes the settings it supports.

The following short list of system property settings is enough to get your service sending metrics and span data to a backend system such as Signoz running on the same system.

| System property | Value | Description |
|----|----|----|
| `otel.sdk.disabled` | `false` | Enables the OpenTelemetry SDK |
| `otel.service.name` | `my-helidon-service` | Name associated with telemetry sent from this service |
| `otel.exporter.otlp.endpoint` | `http://localhost:4318` | URL to which to transmit data |
| `otel.exporter.otlp.protocol` | `http/protobuf` | Export protocol (other choice for `otlp` is `grpc`) |

### Use the OpenTelemetry API from Your Code

Your service needs to do these basic steps:

1.  Create the `OpenTelemetry` object using autoconfigure.
2.  Create a meter and a tracer.
3.  Register and update metrics and create tracing spans.

Now for a bit of confusing terminology. In OpenTelemetry, a *meter* acts as a factory for creating metrics (counters, histograms, etc.). Similarly, a *tracer* serves the same purpose but for creating spans. The confusion for some is that, in Micrometer and some other metrics systems, the term *meter* is a general term that *encompasses* counters, timers, etc. rather than a factory which *creates* them.

#### Creating the `OpenTelemetry` object

*Creating a the global `OpenTelemetry` object using autoconfigure*

```java
OpenTelemetry otel = AutoConfiguredOpenTelemetrySdk.builder()
        .setResultAsGlobal()
        .build()
        .getOpenTelemetrySdk();
```

Your other code could use either the `otel` variable or invoke

```java
var globalOtel = GlobalOpenTelemetry.get();
```

to retrieve the previously-established global instance from anywhere in the application.

### Creating a Meter and a Tracer

```java
var meter = otel.getMeter("helidon-otel-example-app");
var tracer = otel.getTracer("helidon-otel-example-app");
```

The parameter to both is the *instrumentation scope*. Typical practice is for each application to have its own, single instrumentation scope, each library that deals with OpenTelemetry would have its own, and so forth. Ultimately, the instrumentation scope provides a way to group telemetry data logically by the software component that created them.

### Registering and Updating Metrics

```java
var myCounter = meter.counterBuilder("my-counter")
        .setDescription("An example counter")
        .build();
// ...
myCounter.add(1L);
```

Often one part of the code will register a metric and save a reference to it, then the request handling logic in another part of the code updates the metric using that saved reference.

### Creating and Managing Spans

```java
var mySpan = tracer.spanBuilder("my-span")
        .setSpanKind(SpanKind.SERVER)
        .startSpan();

try (Scope ignored = mySpan.makeCurrent()) {

    // Do work worth tracing.

    mySpan.setStatus(StatusCode.OK);
} catch (Throwable t) {
    mySpan.setStatus(StatusCode.ERROR);
} finally {
    mySpan.end();
}
```

Typically, application code creates and starts a span, activates it (makes it current), and then restores any previously-active span and ends the span, recording whether the span was successful.
