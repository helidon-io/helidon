# Helidon SE 4.x Upgrade Guide

Helidon 4.x introduces significant changes to APIs and runtime behavior. Use this guide to help you understand the changes required to transition a Helidon SE 3.x application to Helidon 4.x.

## Significant Changes

The following sections describe the changes between Helidon 3.x and Helidon 4.x that can significantly impact your development process. Review them carefully.

You can also review the [Helidon repository CHANGELOG](https://github.com/helidon-io/helidon/blob/main/CHANGELOG.md) to see a detailed history of changes made to the project.

> [!NOTE]
> Helidon adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). As such, Helidon 4.x includes changes that are not backward compatible with Helidon 3.x.

> [!TIP]
> The [Helidon Examples repository](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/) is a good resource for understanding how things work in Helidon 4.x.

### Java SE Support

Helidon 4.x removes support for Java SE 17. You must use Java SE 21 or later. If you are using Helidon 4.3.0 or later, using Java SE 25 or later is recommended.

### Programming Paradigm Shift

In Helidon 4.x, Helidon SE moves from an asynchronous-style API to a blocking-style API that is optimized for use with virtual threads. Currently, there is no compatibility API available.

### New Web Server Implementation

Helidon 4.x introduces Helidon WebServer, a virtual threads-based web server implementation based on the JDK Project Loom. Helidon WebServer replaces Netty, the server implementation used in previous versions of Helidon.

You will need to update your existing Helidon SE 3.x code to use the new APIs but it is generally simpler to write and maintain code in Helidon SE 4.x than it was in previous versions.

Here is an example of the differences between Helidon SE 3.x and Helidon SE 4.x:

*Use Helidon 3.x to extract a JSON body from an HTTP request and do something*

``` java
request.content().as(JsonObject.class)
        .thenAccept(jo -> doSomething(jo, response));
```

*Use Helidon 4.x to extract a JSON body from an HTTP request and do something*

``` java
doSomething(request.content().as(JsonObject.class), response);
```

Learn more at [WebServer](../webserver/webserver.md).

### Server Startup

Starting a server in Helidon 4.x is much simpler than in previous versions because it no longer requires asynchronous programming.

In previous versions of Helidon, the server was started asynchronously and further server operations had to wait. For example:

*Start Helidon SE 3.x server*

``` java
static Single<WebServer> startServer() {
    Config config = Config.create();

    WebServer server = WebServer.builder(createRouting(config))
            .config(config.get("server"))
            .addMediaSupport(JsonpSupport.create())
            .build();

    Single<WebServer> webserver = server.start(); 

    webserver.thenAccept(ws -> { 
                System.out.println("WEB server is up! http://localhost:" + ws.port() + "/greet");
                ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
            })
            .exceptionallyAccept(t -> { 
                System.err.println("Startup failed: " + t.getMessage());
                t.printStackTrace(System.err);
            });

    return webserver;
}
```

- Server is started in an asynchronous way. A `Single` object is returned.
- Wait for the server to start and print the message in an asynchronous way.
- Gracefully handle exceptions if they occur during the initialization process.

In Helidon 4.x, you can create and configure a server and then wait for it to start. If any exceptions happen, they are handled the traditional way using available language constructions. For example:

*Start Helidon SE 4.x server*

``` java
public static void main(String[] args) {

    Config config = Config.global();

    WebServer server = WebServer.builder() 
            .config(config.get("server"))
            .routing(Main::routing)
            .build()
            .start(); 

    System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet"); 
}
```

- Configure the server.
- Start the server. No reactive objects returned.
- Print a message when the server is started.

### Additional Server Lifecycle Tasks

In Helidon 3.x, if you provided code to run after WebServer startup and after WebServer shutdown, you needed to use asynchronous constructs, like so:

*Helidon 3.x server lifecycle*

``` java
Single<WebServer> webserver = server.start();

webserver.thenAccept(ws -> {
            System.out.println("WEB server is up! http://localhost:" + ws.port() + "/greet");
            ws.whenShutdown().thenRun(() -> System.out.println("Helidon WebServer has stopped"));
        })
        .exceptionallyAccept(t -> {
            System.err.println("Startup failed: " + t.getMessage());
            t.printStackTrace(System.err);
        });
```

In Helidon 4.x, no special API is needed for post-server startup tasks since the server starts synchronously. Your `HttpService` can interpose on the server lifecycle by overriding the `beforeStart` and `afterStop` methods, like so:

*Helidon 4.x server lifecycle*

``` java
static class MyService implements HttpService {
    @Override
    public void beforeStart() {
        System.out.println("MyService: Helidon WebServer is starting!");
    }

    @Override
    public void afterStop() {
        System.out.println("MyService: Helidon WebServer has stopped.");
    }
```

### Server Features and Media Support Discovery

In previous versions of Helidon, you had to explicitly register WebServer features (`register(MetricsSupport.create())`) and explicitly add media support (`addMediaSupport(JsonpSupport.create())`).

Helidon 4.x automatically discovers these components from the class path. You only need to add the dependencies to your `pom.xml` file and, optionally, add configuration to customize them.

If you want full control using the API, you still have that option.

For more information, see:

- [Observability Feature Support](../observability.md)
- [Media Types Support](../webserver/webserver.md#_media_types_support)

### Routing Configuration

In previous Helidon versions, the routing was configured as follows: services were created and assigned to the desired path. Observability and other features were created as usual Helidon `services`, available as part of the framework. User-defined services were also registered the same way. For example:

*Routing in Helidon SE 3.x server*

``` java
private static Routing createRouting(Config config) {

    MetricsSupport metrics = MetricsSupport.create(); 
    HealthSupport health = HealthSupport.builder()
            .addLiveness(HealthChecks.healthChecks())
            .build();

    GreetService greetService = new GreetService(config); 

    return Routing.builder()
            .register(health) 
            .register(metrics)
            .register("/greet", greetService) 
            .build();
}
```

- Create and configure `Metrics` and `Health` support.
- Create a regular Helidon Service.
- Register `Metrics` and `Health` support as Helidon Services.
- Register the regular Greeting service.

In Helidon 4.x, the Metrics and Health features are automatically discovered and, assuming you added the dependencies to your project, the routing is configured in the following way:

*Routing in Helidon SE 4.x server*

``` java
static void routing(HttpRouting.Builder routing) {
    routing.register("/greet", new GreetService()); 
}
```

- Register Greeting service as in previous versions of Helidon.

If you want to add these features to the server programmatically, you would use `WebServer.builder().addFeature()` method instead.

`Feature` encapsulates a set of endpoints, services and/or filters. It is similar to `HttpService` but gives more freedom in setup. The main difference is that a feature can add `Filters` and it cannot be registered on a path. Features are not registered immediately; each feature can order features according to their weight by defining a `Weight` or implementing `Weighted` . Higher-weighted features are registered first. This allows you to order features in a meaningful way, for example Context, then Tracing, then Security, and so on.

#### Adding Additional Routing Criteria

Helidon 4.x removes the `RequestPredicate` class, which in previous versions, was used to specify more routing criteria.

So, for example, if you used the following in Helidon 3.x:

*Helidon 3.x using `RequestPredicate`*

``` java
public abstract class RoutingHandlerResource<I, R> implements HttpService {

        protected Handler requestHandler(HttpRules rules, Method method, Handler applyHandler) {
            switch (method) {
            case PUT:
                return RequestPredicate.create()
                    .accepts(MediaType.APPLICATION_JSON)
                    .containsHeader(HttpHeaderField.AUTHORIZATION.headerName())
                    .hasContentType(ContentHeader.TYPE_JSON)
                    .thenApply(applyHandler);
            }
        }
}
```

Then, you would now use the following in Helidon 4.x:

*Routing without RequestPredicate in Helidon 4.x*

``` java
public abstract class RoutingHandlerResource<I, R> implements HttpService {

    protected Handler requestHandler(HttpRules rules, Method method, Handler applyHandler) {
        return switch (method.text()) {
            case "PUT" -> (req, res) -> {
                ServerRequestHeaders headers = req.headers();
                if (headers.isAccepted(MediaTypes.APPLICATION_JSON)
                    && headers.contains(HeaderNames.AUTHORIZATION)
                    && headers.contentType()
                            .filter(HttpMediaTypes.JSON_PREDICATE)
                            .isPresent()) {
                    applyHandler.handle(req, res);
                } else {
                    res.next();
                }
            };
            default -> (req, res) -> res.next();
        };
    }
}
```

### Services

Helidon 4.x introduces `HttpService` which you implement to process HTTP requests. To set up routing, you should now use the `routing(HttpRules rules)` method. It receives an `HttpRules` object with routes description.

Additionally, `ServerRequest` and `ServerResponse` are now in the `io.helidon.webserver.http` package and `Http.Status` is now `io.helidon.http.Status`.

> [!WARNING]
> These changes make Helidon 4.x incompatible with previous versions.

In previous versions, a service looked like this:

*Helidon SE 3.x Service*

``` java
public class GreetService implements Service {

    @Override
    public void update(Routing.Rules rules) { 
        rules
                .get("/", this::getDefaultMessageHandler)
                .get("/{name}", this::getMessageHandler)
                .put("/greeting", this::updateGreetingHandler);
    }

    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) { 
        sendResponse(response, "World");
    }

    // other methods omitted
}
```

- Use the `update()` method to set up routing.
- Handle a `Request` and return a `Response`.

In Helidon 4.x, the same service looks like this:

*Helidon SE 4.x Service*

``` java
public class GreetService implements HttpService { 

    @Override
    public void routing(HttpRules rules) { 
        rules.get("/", this::getDefaultMessageHandler)
                .get("/{name}", this::getMessageHandler)
                .put("/greeting", this::updateGreetingHandler);
    }

    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) { 
        sendResponse(response, "World");
    }

    private void getMessageHandler(ServerRequest request, ServerResponse response) {
        // ...
    }

    private void updateGreetingHandler(ServerRequest request, ServerResponse response) { 
        // ...
    }
}
```

- Implement `HttpService` for the `GreetingService`.
- Use `routing(HttpRules rules)` to set up routing.
- Handle a `Request` and return a `Response`.

Learn more about `HttpService` and `Routing` at [Helidon SE WebServer](../webserver/webserver.md).

## Other Changes

The following sections describe changes between Helidon 3.x and Helidon 4.x that may impact your development process.

### Media Support

Media support moved from the `io.helidon.media` Java package to `io.helidon.http.media` and has the following new dependency coordinates:

``` xml
<dependency>
    <groupId>io.helidon.http.media</groupId>
    <artifactId>helidon-http-media-jsonp</artifactId>
</dependency>

<dependency>
    <groupId>io.helidon.http.media</groupId>
    <artifactId>helidon-http-media-jsonb</artifactId>
</dependency>
```

In Helidon 4.x, media support is discovered by default, so you only need to add the dependency rather than explicitly adding media support using the `WebServer` builder.

However, media support no longer transitively brings the Jakarta EE API dependencies, so you will need to add those dependencies explicitly. For example:

``` xml
<dependency>
    <groupId>jakarta.json</groupId>
    <artifactId>jakarta.json-api</artifactId>
</dependency>
```

### Testing

Helidon 4.x adds a new testing framework for Helidon SE.

``` xml
<dependency>
     <groupId>io.helidon.webserver.testing.junit5</groupId>
     <artifactId>helidon-webserver-testing-junit5</artifactId>
     <scope>test</scope>
</dependency>
```

For more information, see [Helidon SE Testing](../testing.md).

### Observability

Observability features moved to different packages. For `Health` and `Metrics`, you should now use:

``` xml
<dependencies>
    <dependency>
        <groupId>io.helidon.webserver.observe</groupId>
        <artifactId>helidon-webserver-observe-health</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.webserver.observe</groupId>
        <artifactId>helidon-webserver-observe-metrics</artifactId>
    </dependency>
</dependencies>
```

Observability has new endpoints. See them at [hObservability](../observability.md).

For System Metrics, you should now use:

``` xml
<dependency>
    <groupId>io.helidon.metrics</groupId>
    <artifactId>helidon-metrics-system-meters</artifactId>
</dependency>
```

By default, Observability features are discovered automatically if you add the above dependencies. If you choose to add them programmatically (using `addFeature`), you must add the following dependency:

``` xml
<dependency>
    <groupId>io.helidon.webserver.observe</groupId>
    <artifactId>helidon-webserver-observe</artifactId>
</dependency>
```

Metrics has changed significantly in Helidon 4.x. For more information, see [Helidon SE Metrics](../metrics/metrics.md).

### Security

- Changed modules:
  - `helidon-security-integration-grpc` was removed
  - `helidon-security-integration-jersey` moved to the module `helidon-microprofile-security`
  - `helidon-security-integration-jersey-client` moved to the module `helidon-microprofile-security`
  - `helidon-security-integration-webserver` moved to the module `helidon-webserver-security`
- Significant class name changes:
  - `OidcSupport` was renamed to `OidcFeature`
  - `WebSecurity` was renamed to `SecurityFeature`
- Other:
  - `SynchronousProvider removed` - `SynchronousProvider` usage is no longer needed, since all security providers are synchronous.

### Global Configuration

Helidon 4.x adds global configuration, a singleton instance of the `Config` class, which is implicitly employed by certain Helidon components. Furthermore, it offers a handy approach for your application to access configuration information from any part of your code.

``` java
Config config = Config.global();
```

For more information, see [Helidon SE Config](../config/introduction.md).

### Logging

The class `LogConfig` moved to the `io.helidon.logging.common` Java package.

The Helidon console handler changed from `io.helidon.common.HelidonConsoleHandler` to `io.helidon.logging.jul.HelidonConsoleHandler`.

If you use this handler in your `logging.properties` file, you will need to update it and add the following dependency:

``` xml
<dependency>
    <groupId>io.helidon.logging</groupId>
    <artifactId>helidon-logging-jul</artifactId>
    <scope>runtime</scope>
</dependency>
```
