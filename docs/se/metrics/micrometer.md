# Micrometer Metrics

## Overview

> [!NOTE]
> Micrometer integration is deprecated beginning in Helidon 4.1 and is planned for removal in a future major release. Please use the [Helidon neutral metrics API](../../se/metrics/metrics.md).

Helidon SE simplifies how you can use Micrometer for application-specific metrics:

- The endpoint `/micrometer`: A configurable endpoint that exposes metrics according to which Micrometer meter registry responds to the HTTP request.
- The `MicrometerSupport` class: A convenience class for enrolling Micrometer meter registries your application creates explicitly or for selecting which built-in Micrometer meter registries to use.
- Configuration to tailor the Prometheus and other Micrometer meter registries.

In Helidon 4.4.0-SNAPSHOT, Micrometer support is separate from the Helidon SE metrics API and the built-in Helidon metrics.

## Maven Coordinates

To enable {feature-name}, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.integrations.micrometer</groupId>
    <artifactId>helidon-integrations-micrometer</artifactId>
</dependency>
```

Micrometer supports different types of meter registries which have different output styles and formats. Helidon provides built-in support for the Prometheus meter registry. To use other meter registry types, you will need to add dependencies for them to your `pom.xml` and, optionally, add code to your application or add configuration to set them up as you wish.

## Usage

Your application registers and updates Micrometer meters using annotations or direct use of the Micrometer API.

Your users retrieve Micrometer meters using an endpoint which Helidon creates automatically.

### Registering and Updating Meters

Your code can create, look up, and update metrics programmatically using the Micrometer `MeterRegistry` API. The [Micrometer concepts document](https://docs.micrometer.io/micrometer/reference/concepts) provides a good starting point for learning how to use Micrometer’s interfaces and classes.

### Accessing the Helidon Micrometer Endpoint

Your application can easily have Helidon create a REST endpoint which clients can access to retrieve Micrometer metrics, by default at the `/micrometer` endpoint.

Within Helidon, each type of meter registry is paired with some code that examines the incoming HTTP request to `/micrometer` and decides whether the request matches up with the associated meter registry. The first pairing that accepts the request returns the response. You will need to take advantage of this if your application uses additional meter registries beyond what Helidon automatically provides *and* you want those meter registries reflected in the output from the `/micrometer` REST endpoint.

## API

### The Helidon Micrometer API

Helidon provides no special API for dealing with Micrometer meters and meter registries beyond what Micrometer offers itself.

Helidon *does* give you an easy way to expose a REST endpoint to report the meters stored in the Micrometer meter registry. The [`MicrometerSupport`](https://javadoc.io/doc/io.micrometer/io/helidon/integrations/micrometer/MicrometerSupport.html) interface exposes static methods to directly create an instance of `MicrometerSupport` and to return a [`Builder`](https://javadoc.io/doc/io.micrometer/io/helidon/integrations/micrometer/MicrometerSupport.Builder.html) instance so your code can fine-tune how the REST service behaves.

## Configuration

You can configure the Helidon Micrometer REST service as you can other built-in Helidon services by adding configuration settings under the `micrometer` top-level key.

### Configuration options

By default, Helidon Micrometer integration exposes the `/micrometer` endpoint. You can override the path using the [`Builder`](https://javadoc.io/doc/io.micrometer/MicrometerSupport.Builder.html) or the `micrometer.web-context` configuration key.

*Overriding the default Micrometer path*

``` yaml
micrometer:
  web-context: my-micrometer
```

## Examples

Helidon SE includes an [example application](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/micrometer/se) which uses Micrometer support.

The rest of this section takes you through the process of changing your application to use Helidon SE integration with Micrometer:

1.  Register an instance of [`MicrometerSupport`](https://javadoc.io/doc/io.micrometer/io/helidon/integrations/micrometer/MicrometerSupport.html) with the web server.
2.  Create meters using the meter registry managed by Helidon’s `MicrometerSupport` and then update and query those meters.

### Register an Instance of MicrometerSupport with the Web Server

*Initialize Micrometer support*

``` java
MicrometerFeature micrometerFeature = MicrometerFeature.create(); 

HttpRouting.builder()
        .addFeature(micrometerFeature) 
        .register("/myapp", new MyService(micrometerFeature.registry())) 
        .build();
```

- Create the `MicrometerSupport` instance, using the default built-in Prometheus meter registry.
- Register the `MicrometerSupport` instance as a service; by default, `MicrometerSupport` exposes the endpoint as `/micrometer`.
- Pass the `MicrometerSupport` object’s meter registry to your service for use in creating and updating meters.

### Create and Update Meters in Your Application Service

*Define and use a `Counter`*

``` java
class MyService implements HttpService {

    final Counter requestCounter;

    MyService(MeterRegistry registry) {
        requestCounter = registry.counter("allRequests"); 
    }

    @Override
    public void routing(HttpRules rules) {
        rules
                .any(this::countRequests) 
                .get("/", this::myGet);
    }

    void countRequests(ServerRequest request, ServerResponse response) {
        requestCounter.increment(); 
        response.next();
    }

    void myGet(ServerRequest request, ServerResponse response) {
        response.send("OK");
    }

}
```

- Use the Micrometer meter registry to create the request counter.
- Add routing for any request to invoke the method which counts requests by updating the counter.
- Update the counter and then delegate the rest of the request processing to the next handler in the chain.

The example above enrolls the built-in Prometheus meter registry with the default Prometheus registry configuration. You can change the default setup for built-in registries, and you can enroll other meter registries your application creates itself.

#### Overriding Defaults for Built-in Meter Registry Types

Unless you specify otherwise, Helidon uses defaults for any built-in Micrometer meter registry. For example, Helidon configures the built-in Prometheus registry using `PrometheusConfig.DEFAULT`.

You can override these defaults in either of two ways:

- Using the [`MicrometerSupport.Builder`](https://javadoc.io/doc/io.micrometer/io/helidon/integrations/micrometer/MicrometerSupport.Builder.html) class
- Using configuration

#### Using MicrometerSupport.Builder

Use the `MicrometerSupport.Builder` class to set up Micrometer support however your application needs.

The builder lets you:

- Provide your own Micrometer meter registry configuration that `MicrometerSupport` uses to create a built-in meter registry, or
- Instantiate a Micrometer meter registry yourself, configured however you want, and add it to the `MicrometerSupport` object’s collection of meter registries

*Overriding defaults for built-in meter registries using `MicrometerSupport.Builder`*

``` java
MeterRegistryFactory meterRegistryFactory = MeterRegistryFactory.builder()
        .enrollBuiltInRegistry(BuiltInRegistryType.PROMETHEUS, myPrometheusConfig) 
        .build();
MicrometerFeature micrometerFeature = MicrometerFeature.builder()
        .meterRegistryFactorySupplier(meterRegistryFactory)
        .build();
```

- Enroll the `PROMETHEUS` built-in registry type with your meter registry configuration.

#### Using Configuration

To use configuration to control the selection and behavior of Helidon’s built-in Micrometer meter registries, include in your configuration (such as `application.yaml`) a `micrometer.builtin-registries` section.

*Enroll Prometheus built-in meter registry using default configuration*

``` yaml
micrometer:
  builtin-registries:
    - type: prometheus
```

*Enroll Prometheus built-in meter registry with non-default configuration*

``` yaml
micrometer:
  builtin-registries:
    - type: prometheus
      prefix: myPrefix
```

Note that the first config example is equivalent to the default Helidon Micrometer behavior; Helidon by default supports the Prometheus meter registry.

The configuration keys that are valid for the `builtin-registries` child entries depend on the type of Micrometer meter registry. For example, support in Helidon for the [Prometheus meter registry](https://javadoc.io/doc/io.micrometer/micrometer-registry-prometheus/1.11.1/io/micrometer/prometheus/PrometheusConfig.html) respects the `prefix` configuration setting but other meter registries might not and might support other settings. Refer to the documentation for the meter registry you want to configure to find out what items apply to that registry type.

Helidon does not validate the configuration keys you specify for meter registries.

### Enrolling Other Micrometer Meter Registries

To create additional types of registries and enroll them with `MicrometerSupport`, you need to:

1.  Write a `Handler`  

    Each meter registry has its own way of producing output. Write your handler so that it has a reference to the meter registry it should use and so that its `accept` method sets the payload in the HTTP response using the registry’s mechanism for creating output.

2.  Write a `Function` which accepts a `ServerRequest` and returns an `Optional<Handler>`  

    Typically, the function examines the request—​the `Content-Type`, query parameters, etc.--to decide whether the corresponding handler should respond to the request. If so, your function should instantiate your `Handler` and return an `Optional.of(theHandlerInstance)`; otherwise, your function should return `Optional.empty()`.  

    When `MicrometerSupport` receives a request, it invokes the functions of all the enrolled registries, stopping as soon as one function provides a handler. `MicrometerSupport` then delegates to that handler to create and send the response.

3.  Pass the `Handler` and `Function` to the `MicrometerSupport.enrollRegistry` method to enroll them  

    *Creating and enrolling your own Micrometer meter registry*

``` java
    PrometheusMeterRegistry myRegistry = new PrometheusMeterRegistry(myPrometheusConfig); 
    MeterRegistryFactory meterRegistryFactory = MeterRegistryFactory.builder()
            .enrollRegistry(myRegistry, request -> {
                return request 
                        .headers()
                        .bestAccepted(MediaTypes.TEXT_PLAIN)
                        .map(mt -> (req, resp) -> resp.send(myRegistry.scrape())); 
            })
            .build();
    MicrometerFeature micrometerFeature = MicrometerFeature.builder()
            .meterRegistryFactorySupplier(meterRegistryFactory)
            .build();
    ```

    - Create the meter registry. This example uses a Prometheus registry, but it can be any extension of `MeterRegistry`.
    - Provide the function that checks if the [`ServerRequest`](/apidocs/io.helidon.webserver/io/helidon/webserver/http/ServerRequest.html)
    - A very simple in-line `Handler` that sets the response entity from the Prometheus registry’s `scrape()` method.

## Accessing the Helidon Micrometer Endpoint

Your application can easily have Helidon create a REST endpoint which clients can access to retrieve Micrometer metrics, by default at the `/micrometer` endpoint.

Within Helidon, each type of meter registry is paired with some code that examines the incoming HTTP request to `/micrometer` and decides whether the request matches up with the associated meter registry. The first pairing that accepts the request returns the response. You will need to take advantage of this if your application uses additional meter registries beyond what Helidon automatically provides *and* you want those meter registries reflected in the output from the `/micrometer` REST endpoint.

When `MicrometerSupport` receives a request at the endpoint, it looks for the first enrolled meter registry for which the corresponding `Function<ServerRequest, Optional<Handler>>` returns a non-empty `Handler`. Helidon invokes that `Handler` which must retrieve the metrics output from its meter registry and set and send the response. Note that the `Handler` which your function returns typically has a reference to the meter registry it will use in preparing the response.

# Additional Information

The [Micrometer website](https://micrometer.io) describes the project as a whole and has links to more information.
