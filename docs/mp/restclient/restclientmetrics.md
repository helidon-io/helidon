# Rest Client Metrics

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Usage](#usage)
- [API](#api)
- [Configuration](#configuration)
- [Reference](#reference)

## Overview

Helidon supports MicroProfile REST Client metrics by registering metrics automatically when developers add MicroProfile Metrics annotations to REST client interfaces and methods.

MicroProfile neither mandates nor specifies how metrics and the REST client work together. Support in Helidon for metrics on REST clients uses the MicroProfile Metrics spec for inspiration where appropriate.

For more information about support for REST clients in Helidon see [REST Client](restclient.md).

## Maven Coordinates

To enable MicroProfile Rest Client Metrics, either add a dependency on the [helidon-microprofile bundle](../../mp/introduction/microprofile.md) or add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.microprofile.rest-client-metrics</groupId>
    <artifactId>helidon-microprofile-rest-client-metrics</artifactId>
</dependency>
```

## Usage

Add the MicroProfile Metrics `@Counted` and `@Timed` annotations to REST client interfaces and interface methods to trigger counting or timing, respectively, of REST client method invocations.

Helidon determines metric names according to the [MicroProfile Metrics naming convention](https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/microprofile-metrics-spec-5.1.1.html#annotated-naming-convention) and supports the following metrics naming features:

- absolute and relative names
- explicit and inferred names
- type-level annotations

When you place annotations at the type level of a REST client interface Helidon registers *different* metrics for each of the REST methods on the interface. This is the same behavior as in normal MicroProfile Metrics when you add metrics annotations at the type level.

When you use the annotations at the type level on a superinterface Helidon acts as if those annotations appear at the type-level of any REST client subinterface which extends the superinterface. In keeping with the naming conventions enforced by the MicroProfile Metrics TCK, relative metric names use the *subinterface* name not the declaring interface name.

(Note that the MicroProfile Metrics specification states that the *declaring* class name is used, while as written the MicroProfile Metrics TCK requires that implementations use the *subclass* name. For consistency the Helidon REST client metrics implementation follows the enforced metrics TCK behavior.)

### Understanding How and When Helidon Registers REST Client Metrics

Helidon registers the metrics associated with a REST client interface when that interface becomes known to Helidon as a REST client.

The [MicroProfile REST Client spec](https://download.eclipse.org/microprofile/microprofile-rest-client-3.0/microprofile-rest-client-spec-3.0.html#_microprofile_rest_client) describes how your application can inject a REST client interface or prepare it programmatically. Either action makes the REST client known to Helidon, at which time Helidon registers the metrics associated with that interface’s methods. As a result, depending on how your application works, REST client metrics might be registered well after your code initially starts up.

### Using REST Client Metrics in Standalone Clients vs. in Servers

Helidon registers and updates REST client metrics whether the REST client is standalone or is embedded in a Helidon server.

Helidon *does not* provide a `/metrics` endpoint for standalone clients, nor does it provide any built-in way to transmit metrics data from a client to a backend system. If needed, you can write your client code to access the application `MetricRegistry` and retrieve the REST client metrics Helidon has registered.

In contrast, when REST clients run inside Helidon servers the REST client metrics for REST clients known to Helidon appear in the `/metrics` output.

### Turning on Logging

Set `io.helidon.microprofile.restclientmetrics.level=DEBUG` in your logging settings to see some of the inner workings of the REST client metrics implementation.

During start-up the logging reports analysis of candidate REST client interfaces and the creation of metric registration entries, including the metric annotation and where Helidon found each.

When a REST client is made known to Helidon the logging reports the actual registration of the metrics derived from that REST client interface.

## API

Use the following annotations from `org.eclipse.microprofile.metrics.annotation` listed in the following table to trigger REST client metrics.

|            |                                                 |
|------------|-------------------------------------------------|
| Annotation | Description                                     |
| `@Counted` | Counts the invocations of a REST client method. |
| `@Timed`   | Times the invocations of a REST client method.  |

Type-level annotations trigger registration of separate metrics for each REST client method in the REST client interface.

## Configuration

Optional configuration options:

| key       | type   | default value | description                         |
|-----------|--------|---------------|-------------------------------------|
| `enabled` | string | `true`        | Whether to use REST client metrics. |

The `enabled` configuration setting allows developers to build REST client metrics into an application while permitting end users to disable the feature at their discretion.

## Examples

This example is similar to the [Helidon REST Client doc example](restclient.md#_examples) which starts with the [Helidon MP QuickStart example](../guides/quickstart.md).

This sample app adds a new resource which mimics the functionality of the `GreetResource` but delegates each incoming request to its counterpart on the `GreetResource` using a REST client interface for that `GreetResource`. In short, the example application delegates to itself. Of course no production application would operate this way, but this contrived situation helps illustrate how to use REST client metrics simply with a single runnable project.

To create this REST client metrics example follow these steps.

1.  Starting with the Helidon MP QuickStart example, add dependencies for both the Helidon REST client component and the Helidon REST client metrics component, as shown below.

    ``` xml
    <dependency>
        <groupId>io.helidon.microprofile.rest-client</groupId>
        <artifactId>helidon-microprofile-rest-client</artifactId>
    </dependency>
    ```

    ``` xml
    <dependency>
        <groupId>io.helidon.microprofile.rest-client-metrics</groupId>
        <artifactId>helidon-microprofile-rest-client-metrics</artifactId>
    </dependency>
    ```

2.  Add the following REST client interface which includes MicroProfile Metrics annotations to count and time various REST client method invocations.

    ``` java
    @Path("/greet")
    @Timed(name = "timedGreet", absolute = true) 
    public interface GreetRestClient {

        @Counted                            
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        GreetingMessage getDefaultMessage();

        @Path("/{name}")
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        GreetingMessage getMessage(@PathParam("name") String name);

        @Path("/greeting")
        @PUT
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Response updateGreeting(GreetingMessage message);
    }
    ```

    - Times all outbound method invocations using separate timers for each method.
    - Counts the number of times a request is sent to get the default greeting message.
3.  Add a new resource class, similar to the `GreetService` resource class, but which delegates all incoming requests using the REST client.

    ``` java
    @Path("/delegate")
    public class DelegatingResource {

        private static LazyValue<GreetRestClient> greetRestClient = LazyValue.create(DelegatingResource::prepareClient); 

        /**
         * Return a worldly greeting message.
         *
         * @return {@link GreetingMessage}
         */
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public GreetingMessage getDefaultMessage() {
            return greetRestClient.get().getDefaultMessage();           
        }

        /**
         * Return a greeting message using the name that was provided.
         *
         * @param name the name to greet
         * @return {@link GreetingMessage}
         */
        @Path("/{name}")
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public GreetingMessage getMessage(@PathParam("name") String name) {
            return greetRestClient.get().getMessage(name);
        }

        /**
         * Set the greeting to use in future messages.
         *
         * @param message JSON containing the new greeting
         * @return {@link jakarta.ws.rs.core.Response}
         */
        @Path("/greeting")
        @PUT
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response updateGreeting(GreetingMessage message) {
            return greetRestClient.get().updateGreeting(message);
        }

        private static GreetRestClient prepareClient() {            
            Config config = ConfigProvider.getConfig();
            String serverHost = config.getOptionalValue("server.host", String.class).orElse("localhost");
            String serverPort = config.getOptionalValue("server.port", String.class).orElse("8080");
            return RestClientBuilder.newBuilder()
                    .baseUri(URI.create("http://" + serverHost + ":" + serverPort))
                    .build(GreetRestClient.class);
        }
    }
    ```

    - Holds the prepared REST client for use by the delegating methods.
    - Prepares the REST client. The example shows only one of many ways of doing this step.
    - Each delegating method invokes the corresponding REST client method and returns the result from it.

      By default, resource classes such as `DelegatingResource` are instantiated for each incoming request, but generally a Helidon server making outbound requests reuses the client data structures and connections. To create and reuse only a single REST client instance this example resource uses the Helidon `LazyValue` utility class so even as the system creates multiple instances of `DelegatingResource` they all reuse the same REST client.

4.  Build and run the application.

    ``` bash
    mvn clean package
    java -jar target/helidon-quickstart-mp.jar
    ```

5.  Access the delegating endpoints.

    ``` bash
    curl http://localhost:8080/delegate
    curl http://localhost:8080/delegate
    curl http://localhost:8080/delegate/Joe
    ```

6.  Retrieve the application metrics for the `getDefaultMessage` operation.

    ``` bash
    curl 'http://localhost:8080/metrics?scope=application' | grep getDefault
    ```

7.  Look for two types of metrics:
    1.  Counter:

        ``` text
        # TYPE io_helidon_examples_quickstart_mp_GreetRestClient_getDefaultMessage_total counter
        io_helidon_examples_quickstart_mp_GreetRestClient_getDefaultMessage_total{mp_scope="application",} 2.0
        ```

        This is the counter resulting from the `@Counted` annotation on the `getDefaultMessage` method of the REST client interface. The name is relative to the annotated method’s class and is automatically set to the method name because neither `name` nor `absolute` were specified with the annotation.

    2.  Timer:

        ``` text
        # TYPE timedGreet_getDefaultMessage_seconds summary
        timedGreet_getDefaultMessage_seconds{mp_scope="application",quantile="0.5",} 0.003407872
        timedGreet_getDefaultMessage_seconds{mp_scope="application",quantile="0.75",} 0.092143616
        timedGreet_getDefaultMessage_seconds_count{mp_scope="application",} 2.0
        ```

        This excerpt shows the output for only one timer, but the full output includes timers for each method.

        The `@Timed` annotation at the type level triggers the registration of timers for each REST method in the REST client interface. The `name` setting overrides the default of the type name, and the `absolute` setting means the selected name *is not* relative to the fully-qualified class name.

## Reference

- [Helidon REST Client documentation](restclient.md)
- [MicroProfile RestClient specification](https://download.eclipse.org/microprofile/microprofile-rest-client-3.0/microprofile-rest-client-spec-3.0.html)
- [MicroProfile Metrics specification](https://download.eclipse.org/microprofile/microprofile-metrics-5.1.1/microprofile-metrics-spec-5.1.1.html)
