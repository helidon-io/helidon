# Helidon Testing

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Usage](#usage)
- [Examples](#examples)
- [Virtual Threads](#virtual-threads)
- [Service Registry](#service-registry)
- [Additional Information](#additional-information)
- [Reference](#reference)

## Overview

Helidon provides built-in test support for Helidon testing with JUnit 5.

## Maven Coordinates

To enable Helidon Testing Framework, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

``` xml
<dependency>
     <groupId>io.helidon.webserver.testing.junit5</groupId>
     <artifactId>helidon-webserver-testing-junit5</artifactId>
     <scope>test</scope>
</dependency>
```

## Usage

Helidon provides a rich set of extensions based on JUnit 5 for Helidon WebServer testing. Testing can be done with automatic server start-up, configuration, and shutdown. Testing can also be done without full server start-up with `DirectClient` when no real sockets are created.

## API

There are two main annotations that you can use to test Helidon WebServer.

- `@ServerTest` is an integration test annotation that starts the server (opens ports) and provides client injection pre-configured for the server port(s).
- `@RoutingTest` is a unit test annotation that does not start the server and does not open ports but provides a direct client (with the same API as the usual network client) to test routing.

The additional annotation `@Socket` can be used to qualify the injection of parameters into test constructors or methods, such as to obtain a client configured for the named socket.

The following table lists the supported types of parameters for the `@SetUpRoute` annotated methods. Such methods MUST be static and may have any name. The `@SetUpRoute` annotation has `value` with socket name (to customize the setup for a different socket).

- Parameter type - supported class of a parameter
- Annotation - which annotations support this parameter
- Modules - which webserver extension modules support this signature

| Parameter Type | Annotation | Modules | Notes |
|----|----|----|----|
| `HttpRouting.Builder` | `@ServerTest`, `@RoutingTest` |  |  |
| `HttpRules` | `@ServerTest`, `@RoutingTest` |  | Same as `HttpRouting.Builder`, only routing setup |
| `Router.RouterBuilder<?>` | `@ServerTest`, `@RoutingTest` |  |  |
| `SocketListener.Builder` | `@ServerTest` |  |  |
| `WebSocketRouting.Builder` | `@ServerTest`, `@RoutingTest` | websocket |  |

Parameters for the `@SetUpRoute` annotated methods.

In addition:

- a static method annotated with `@SetUpServer` can be defined for tests, which has a single parameter of [`WebServerConfig.Builder`](/apidocs/io.helidon.webserver/io/helidon/webserver/WebServerConfig.Builder.html).
- a static method annotated with `@SetUpFeatures` can be defined for tests, which returns `List<? extends ServerFeature>` to configure additional features, or update discovered features, feature discovery can be disabled using the annotation `` value()` ``

The following table lists the injectable types (through constructor or method injection).

- Type - type that can be injected
- Socket - if checked, you can use the `@Socket` annotation to obtain a value specific to that named socket
- Annotation - which annotations support this injection
- Modules - which WebServer extension modules support this injection
- Notes - additional details

|  |  |  |  |  |
|----|----|----|----|----|
| Type | Socket? | Annotation | Modules | Notes |
| `WebServer` |  | `@ServerTest` |  | Server instance (already started) |
| `URI` | x | `@ServerTest` |  | URI pointing to a port of the webserver |
| `SocketHttpClient` | x | `@ServerTest` |  | This client allows you to send anything in order to test for bad requests or other issues. |
| `Http1Client` | x | `@ServerTest` |  |  |
| `DirectClient` | x | `@RoutingTest` |  | Implements `Http1Client` API |
| `WsClient` | x | `@ServerTest` | websocket |  |
| `DirectWsClient` | x | `@RoutingTest` | websocket | Implements `WsClient` API |

Injectable types.

Extensions can enhance the features for the module `helidon-testing-junit5-webserver` to support additional protocols.

## Examples

You can create the following test to validate that the server returns the correct response:

*Basic Helidon test framework usage.*

``` java
@ServerTest 
class MyServerTest {

    final Http1Client client;

    MyServerTest(Http1Client client) { 
        this.client = client;
    }

    @SetUpRoute 
    static void routing(HttpRouting.Builder builder) {
        Main.routing(builder);
    }

    @Test
    void testRootRoute() { 
        try (Http1ClientResponse response = client
                .get("/greet")
                .request()) { 
            assertThat(response.status(), is(Status.OK_200)); 
        }
    }
}
```

- Use `@ServerTest` to trigger the testing framework.
- Inject `Http1Client` for the test.
- SetUp routing for the test.
- Regular `JUnit` test method.
- Call the `client` to obtain server response
- Perform the necessary assertions.

To trigger the framework to start and configure the server, annotate the testing class with the `@ServerTest` annotation.

In this test, the `Http1Client` client is used, which means that the framework will create, configure, and inject this object as a parameter to the constructor.

To set up routing, a static method annotated with `@SetUpRoute` is present. The framework uses this method to inject the configured routing to the subject of testing – in the current case, the `Quickstart` application.

As everything above is performed by the testing framework, regular unit tests can be done. After completing all tests, the testing framework will shut down the server.

### Routing Tests

If there is no need to set up and run a server, a `DirectClient` client can be used. It is a testing client that bypasses HTTP transport and directly invokes the router.

*Routing test using `@RoutingTest` and `DirectClient`.*

``` java
@RoutingTest 
class MyRoutingTest {

    final Http1Client client;

    MyRoutingTest(DirectClient client) { 
        this.client = client;
    }

    @SetUpRoute 
    static void routing(HttpRouting.Builder builder) {
        Main.routing(builder);
    }

    @Test
    void testRootRoute() { 
        try (Http1ClientResponse response = client
                .get("/greet")
                .request()) { 
            JsonObject json = response.as(JsonObject.class); 
            assertThat(json.getString("message"), is("Hello World!"));
        }
    }
}
```

- Use `@RoutingTest` to trigger the testing framework.
- Inject `DirectClient` for the test.
- SetUp routing for the test.
- A regular `JUnit` test method.
- Call the `client` to obtain server response.
- Perform the necessary assertions.

If only routing tests are required, this is a "lighter" way of testing because the framework will not configure and run the full Helidon server. This way, no real ports will be opened. All the communication will be done through `DirectClient`, which makes the tests very effective.

It is required to annotate the test class with the `@RoutingTest` annotation to trigger the server to do the configuration. Thus, it will inject the DirectClient client, which can then be used in unit tests.

Routing is configured the same way as in full server testing using the `@SetUpRoute` annotation.

## Virtual Threads

Helidon tests are able to detect Virtual Threads pinning. A situation when carrier thread is blocked in a way, that virtual thread scheduler can’t use it for scheduling of other virtual threads. This can happen for example when blocking native code is invoked, or prior to the JDK 24 when blocking IO operation happens in a synchronized block. Pinning can in some cases negatively affect application performance.

*Enable pinning detection*

``` java
@ServerTest(pinningDetection = true)
```

Pinning is considered as harmful when it takes longer than 20 milliseconds, that is also the default when detecting it within Helidon tests.

Pinning threshold can be changed with:

*Configure pinning threshold*

``` java
@ServerTest(pinningDetection = true, pinningThreshold = 50)
```

- Change pinning threshold from default(20) to 50 milliseconds.

When pinning is detected, test fails with stacktrace pointing to the line of code causing it.

## Service Registry

Tests that use `ServiceRegistry`, or that test components that needs access to it isolated from other test (such as when using `Services.get(Config.class)`), or that need instances from the `ServiceRegistry` injected as constructor or method parameters can do so by using our testing module.

The JUnit5 testing module ensures that a global service registry is created that will be unique for the test class, and that adds an extension that can provide parameters from the `ServiceRegistry`.

Required dependency:

``` xml
<dependency>
    <groupId>io.helidon.testing</groupId>
    <artifactId>helidon-testing-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

To add the extension to your test class, annotate the class with `@io.helidon.testing.junit5.Testing.Test`. In case you use one of the existing testing annotation for server or routing (`@ServerTest`, \`@RoutingTest), this is implied.

You can also use `@Service.Named` qualifier on such parameters to only inject the named instance(s).

## Additional Information

### WebSocket Testing

If WebSocket testing is required, there is an additional module for it. It is necessary to include the following Maven dependency to the Project’s pom file:

``` xml
<dependency>
    <groupId>io.helidon.testing.junit5</groupId>
    <artifactId>helidon-testing-junit5-websocket</artifactId>
    <scope>test</scope>
</dependency>
```

### WebSocket Testing Example

The WebSocket Testing extension adds support for routing configuration and injection of WebSocket related artifacts, such as WebSockets and DirectWsClient in Helidon unit tests.

*WebSocket sample test.*

``` java
@ServerTest
class WsSocketTest {

    static final ServerSideListener WS_LISTENER = new ServerSideListener();
    final WsClient wsClient; 

    WsSocketTest(WsClient wsClient) {
        this.wsClient = wsClient;
    }

    @SetUpRoute
    static void routing(WsRouting.Builder ws) { 
        ws.endpoint("/testWs", WS_LISTENER);
    }

    @Test
    void testWsEndpoint() { 
        ClientSideListener clientListener = new ClientSideListener();
        wsClient.connect("/testWs", clientListener); 
        assertThat(clientListener.message, is("ws")); 
    }
}
```

- Declare `WsClient` and later inject it in the constructor.
- Using @SetUpRoute, create WebSocket routing and assign a serverside listener.
- Test the WebSocket endpoint using the regular @Test annotation.
- Create and assign the clientside listener.
- Check if the received message is correct.

*`ClientSideListener` helper class.*

``` java
static class ClientSideListener implements WsListener {
    volatile String message;
    volatile Throwable error;

    @Override
    public void onOpen(WsSession session) { 
        session.send("hello", true);
    }

    @Override
    public void onMessage(WsSession session, String text, boolean last) { 
        message = text;
        session.close(WsCloseCodes.NORMAL_CLOSE, "End");
    }

    @Override
    public void onError(WsSession session, Throwable t) { 
        error = t;
    }
}
```

- Send "Hello" when a connection is opened.
- Save the message when received and close the connection.
- React on an error.

The WebSocket `ClientSideListener` is also a helper class that implements `WsListener` and is very straightforward:

*`ServerSideListener` helper class.*

``` java
static class ServerSideListener implements WsListener {
    volatile String message;

    @Override
    public void onMessage(WsSession session, String text, boolean last) { 
        message = text;
        session.send("ws", true);
    }
}
```

- Send "ws" on a received message.

The testing class should be annotated with `@RoutingTest` only if routing tests are required without real port opening. Instead of `WsClient`, use `DirectWsClient`.

## Reference

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
