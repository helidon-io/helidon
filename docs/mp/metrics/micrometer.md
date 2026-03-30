# Micrometer Support

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Usage](#usage)
- [API](#api)
- [Configuration](#configuration)
- [Examples](#examples)
- [Additional Information](#additional-information)

## Overview

> [!NOTE]
> Micrometer integration is deprecated beginning in Helidon 4.1 and is planned for removal in a future major release. Please use the [Helidon MicroProfile Metrics API implementation and annotations](../../mp/metrics/metrics.md).

Helidon MP simplifies how you can use Micrometer for application-specific metrics:

- The endpoint `/micrometer`: A configurable endpoint that exposes metrics according to which Micrometer meter registry responds to the HTTP request.
- The Micrometer annotations `@Timed` and `@Counted`.
- Configuration to tailor the Prometheus and other Micrometer meter registries.

In Helidon 4.4.0-SNAPSHOT, Micrometer support is separate from the Helidon MP metrics API and the built-in Helidon metrics.

## Maven Coordinates

To enable Micrometer support, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.integrations.micrometer</groupId>
    <artifactId>helidon-integrations-micrometer-cdi</artifactId>
</dependency>
```

Micrometer supports different types of meter registries which have different output styles and formats. Helidon provides built-in support for the Prometheus meter registry. To use other meter registry types, you will need to add dependencies for them to your `pom.xml` and, optionally, add configuration to set them up as you wish.

## Usage

Your application registers and updates Micrometer meters using annotations or direct use of the Micrometer API.

Your users retrieve Micrometer meters using an endpoint which Helidon creates automatically.

### Registering and Updating Meters

To use Micrometer support, you can simply add the Micrometer `@Timed` and `@Counted` annotations to methods in your application. Helidon automatically registers those meters with the Micrometer composite `MeterRegistry`.

In addition to annotating your methods, your code can create, look up, and update metrics programmatically using the Micrometer `MeterRegistry` API. The [Micrometer concepts document](https://docs.micrometer.io/micrometer/reference/concepts) provides a good starting point for learning how to use Micrometer’s interfaces and classes.

### Accessing the Helidon Micrometer Endpoint

Helidon MP Micrometer integration automatically creates a REST endpoint which clients can access to retrieve Micrometer metrics, by default at the `/micrometer` endpoint.

## API

To incorporate Micrometer metrics into your code, you will work with two APIs: a small one specific to Helidon, and the Micrometer API itself.

### The Helidon Micrometer API

Helidon automatically registers and updates meters associated with methods in your service where you add the Micrometer annotations.

If you want to use the Micrometer `MeterRegistry` directly from your own code, simply `@Inject` the `MeterRegistry` into one of your REST resource classes or any other bean which CDI recognizes. Helidon injects the same Micrometer `MeterRegistry` that it uses for handling Micrometer annotations you add to your code.

### The Micrometer API

Your code can create, look up, and update metrics programmatically using the Micrometer `MeterRegistry` API. The [Micrometer concepts document](https://docs.micrometer.io/micrometer/reference/concepts) provides a good starting point for learning how to use Micrometer’s interfaces and classes.

## Configuration

You can configure the Helidon Micrometer REST service as you can other built-in Helidon services by adding configuration settings under the `micrometer` top-level key.

### Configuration options

By default, Helidon Micrometer integration exposes the `/micrometer` endpoint. You can override the path using the `micrometer.web-context` configuration key.

*Overriding the default Micrometer path*

``` properties
micrometer.web-context=my-micrometer
```

## Examples

Helidon MP includes an [example application](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/micrometer/mp) which uses Micrometer support.

The examples below take you step-by-step through the process of enhancing the Helidon MP QuickStart application to track (by time and invocation count) all `GET` methods and to count all requests for a personalized greeting.

### Add Micrometer annotations

*Adding Micrometer annotations to JAX-RS resource `GET` methods*

``` java
private static final String PERSONALIZED_GETS_COUNTER_NAME = "personalizedGets";
private static final String PERSONALIZED_GETS_COUNTER_DESCRIPTION = "Counts personalized GET operations";
private static final String GETS_TIMER_NAME = "allGets";
private static final String GETS_TIMER_DESCRIPTION = "Tracks all GET operations";

@GET
@Produces(MediaType.APPLICATION_JSON)
@Timed(value = GETS_TIMER_NAME, description = GETS_TIMER_DESCRIPTION, histogram = true) 
public JsonObject getDefaultMessage() {
    return createResponse("World");
}

@Path("/{name}")
@GET
@Produces(MediaType.APPLICATION_JSON)
@Counted(value = PERSONALIZED_GETS_COUNTER_NAME, description = PERSONALIZED_GETS_COUNTER_DESCRIPTION) 
@Timed(value = GETS_TIMER_NAME, description = GETS_TIMER_DESCRIPTION, histogram = true) 
public JsonObject getMessage(@PathParam("name") String name) {
    return createResponse(name);
}
```

- Use `@Timed` to time and count both `GET` methods.
- Use `@Counted` to count the accesses to the `GET` method that returns a personalized greeting.

### Using the Helidon-provided Micrometer `MeterRegistry` from Code

In addition to annotating your methods, you can create, look up, and update metrics explicitly in your code.

Add the following injection to a bean:

*Inject the `MeterRegistry`*

``` java
@Inject
private MeterRegistry registry;
```

Helidon automatically injects a reference to the `MeterRegistry` it manages into your code. Your code can use the normal Micrometer API with this registry to create, find, update, and even delete meters.

#### Overriding Defaults for Built-in Meter Registry Types

Unless you specify otherwise, Helidon uses defaults for any built-in Micrometer meter registry. For example, Helidon configures the built-in Prometheus registry using `PrometheusConfig.DEFAULT`.

To use configuration to control the selection and behavior of Helidon’s built-in Micrometer meter registries, include in your configuration (such as `application.yaml`) a `micrometer.builtin-registries` section.

*Enroll Prometheus built-in meter registry using default configuration*

``` properties
micrometer.builtin-registries.0.type=prometheus
```

*Enroll Prometheus built-in meter registry with non-default configuration*

``` properties
micrometer.builtin-registries.0.type=prometheus
micrometer.builtin-registries.0.prefix=myPrefix
```

Note that the first config example is equivalent to the default Helidon Micrometer behavior; Helidon by default supports the Prometheus meter registry.

The configuration keys that are valid for the `builtin-registries` child entries depend on the type of Micrometer meter registry. For example, support in Helidon for the [Prometheus meter registry](https://javadoc.io/doc/io.micrometer/micrometer-registry-prometheus/1.11.1/io/micrometer/prometheus/PrometheusConfig.html) respects the `prefix` configuration setting but other meter registries might not and might support other settings. Refer to the documentation for the meter registry you want to configure to find out what items apply to that registry type.

Helidon does not validate the configuration keys you specify for meter registries.

## Additional Information

The [Micrometer website](https://micrometer.io) describes the project as a whole and has links to more information.
