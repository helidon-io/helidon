<!--@frontmatter
description: "Helidon Testing Support"
navigation:
  icon: i-lucide-thumbs-up
-->
# Helidon Testing

## Overview

Helidon provides built-in test support for Helidon testing with JUnit 5.

## Maven Coordinates

To enable Helidon Testing Framework, add the following dependency to your
project’s `pom.xml` (see [Managing Dependencies](../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.testing.junit5</groupId>
  <artifactId>helidon-webserver-testing-junit5</artifactId>
  <scope>test</scope>
</dependency>
```

The examples below also use JUnit Jupiter and Hamcrest. Add them to the test
classpath, including the JUnit engine used to run the tests:

```xml [pom.xml]
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.hamcrest</groupId>
  <artifactId>hamcrest-all</artifactId>
  <scope>test</scope>
</dependency>
```

## Usage

Helidon provides a rich set of extensions based on JUnit 5 for Helidon WebServer
testing. Testing can be done with automatic server start-up, configuration, and
shutdown. Testing can also be done without full server start-up with
`DirectClient` when no real sockets are created.

## API

There are two main annotations that you can use to test Helidon WebServer.

- `@ServerTest` is an integration test annotation that starts the server (opens
  ports) and provides client injection pre-configured for the server port(s).
- `@RoutingTest` is a unit test annotation that does not start the server and
  does not open ports but provides a direct client (with the same API as the
  usual network client) to test routing.

The additional annotation `@Socket` can be used to qualify the injection of
parameters into test constructors or methods, such as to obtain a client
configured for the named socket.

The following table lists the supported types of parameters for the
`@SetUpRoute` annotated methods. Such methods MUST be static and may have any
name. The `@SetUpRoute` annotation has `value` with socket name (to customize
the setup for a different socket).

- Parameter type - supported class of a parameter
- Annotation - which annotations support this parameter
- Modules - which webserver extension modules support this signature

Parameters for the `@SetUpRoute` annotated methods.

| Parameter Type             | Annotation                    | Modules   | Notes                                             |
|----------------------------|-------------------------------|-----------|---------------------------------------------------|
| `HttpRouting.Builder`      | `@ServerTest`, `@RoutingTest` |           |                                                   |
| `HttpRules`                | `@ServerTest`, `@RoutingTest` |           | Same as `HttpRouting.Builder`, only routing setup |
| `Router.RouterBuilder<?>`  | `@ServerTest`                 |           |                                                   |
| `ListenerConfig.Builder`   | `@ServerTest`                 |           |                                                   |
| `WsRouting.Builder`        | `@ServerTest`, `@RoutingTest` | websocket |                                                   |
| `GrpcRouting.Builder`      | `@ServerTest`                 | grpc      |                                                   |

In addition:

- Static methods annotated with `@SetUpServer` can be defined for tests, which
  has a single parameter of type [`WebServerConfig.Builder`][webserverconfig].
- Static methods annotated with `@SetUpFeatures` can be defined for tests, which
  returns `List<? extends ServerFeature>` to configure additional features, or
  update discovered features, feature discovery can be disabled using the
  annotation value

The following table lists the injectable types (through constructor or method
injection).

- Type - type that can be injected
- Socket - if checked, you can use the `@Socket` annotation to obtain a value
  specific to that named socket
- Annotation - which annotations support this injection
- Modules - which WebServer extension modules support this injection
- Notes - additional details

Injectable types.

| Type               | Socket? | Annotation     | Modules   | Notes                                                                                      |
|--------------------|---------|----------------|-----------|--------------------------------------------------------------------------------------------|
| `WebServer`        |         | `@ServerTest`  |           | Server instance (already started)                                                          |
| `URI`              | x       | `@ServerTest`  |           | URI pointing to a port of the webserver                                                    |
| `SocketHttpClient` | x       | `@ServerTest`  |           | This client allows you to send anything in order to test for bad requests or other issues. |
| `Http1Client`      | x       | `@ServerTest`  |           |                                                                                            |
| `DirectClient`     | x       | `@RoutingTest` |           | Implements `Http1Client` API                                                               |
| `WsClient`         | x       | `@ServerTest`  | websocket |                                                                                            |
| `DirectWsClient`   | x       | `@RoutingTest` | websocket | Implements `WsClient` API                                                                  |
| `Http2Client`      | x       | `@ServerTest`  | http2     | HTTP/2 client                                                                              |
| `Http2TestClient`  | x       | `@ServerTest`  | http2     | Low-level HTTP/2 frame testing                                                              |
| `GrpcClient`       | x       | `@ServerTest`  | grpc      | gRPC client                                                                                |
| `JsonRpcClient`    | x       | `@ServerTest`  | jsonrpc   | JSON-RPC client                                                                            |

Extensions can enhance the features for the module
`helidon-webserver-testing-junit5` to support additional protocols. The extension
modules below are discovered automatically when present on the test classpath;
no additional JUnit annotation is needed. Each extension depends on the base
testing module and its protocol's server and client modules. HTTP/2, gRPC, and
JSON-RPC extensions support `@ServerTest`; WebSocket also supports `@RoutingTest`.

## Examples

You can create the following test to validate that the server returns the
correct response:

Basic Helidon test framework usage:

<!--@mdc ::code-callout -->
```java
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest // <1>
class MyServerTest {

    final Http1Client client;

    MyServerTest(Http1Client client) { // <2>
        this.client = client;
    }

    @SetUpRoute // <3>
    static void routing(HttpRouting.Builder builder) {
        builder.get("/greet", (req, res) -> res.send("Hello World!"));
    }

    @Test
    void testRootRoute() { // <4>
        try (Http1ClientResponse response = client
                .get("/greet")
                .request()) { // <5>
            assertThat(response.status(), is(Status.OK_200)); // <6>
        }
    }
}
```
1. Use `@ServerTest` to trigger the testing framework.
2. Inject `Http1Client` for the test.
3. SetUp routing for the test.
4. Regular `JUnit` test method.
5. Call the `client` to obtain server response
6. Perform the necessary assertions.
<!--@mdc :: -->

To trigger the framework to start and configure the server, annotate the testing
class with the `@ServerTest` annotation.

In this test, the `Http1Client` client is used, which means that the framework
will create, configure, and inject this object as a parameter to the
constructor.

To set up routing, a static method annotated with `@SetUpRoute` is present. The
framework passes the routing builder to this method, which registers the route
under test. An application can instead delegate to its own routing setup method.

As everything above is performed by the testing framework, regular unit tests
can be done. After completing all tests, the testing framework will shut down
the server.

### Routing Tests

If there is no need to set up and run a server, a `DirectClient` client can be
used. It is a testing client that bypasses HTTP transport and directly invokes
the router.

Routing test using @RoutingTest and DirectClient:

<!--@mdc ::code-callout{collapsed} -->
```java
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest // <1>
class MyRoutingTest {

    final Http1Client client;

    MyRoutingTest(DirectClient client) { // <2>
        this.client = client;
    }

    @SetUpRoute // <3>
    static void routing(HttpRouting.Builder builder) {
        builder.get("/greet", (req, res) -> res.send("Hello World!"));
    }

    @Test
    void testRootRoute() { // <4>
        try (Http1ClientResponse response = client
                .get("/greet")
                .request()) { // <5>
            assertThat(response.as(String.class), is("Hello World!")); // <6>
        }
    }
}
```
1. Use `@RoutingTest` to trigger the testing framework.
2. Inject `DirectClient` for the test.
3. SetUp routing for the test.
4. A regular `JUnit` test method.
5. Call the `client` to obtain server response.
6. Perform the necessary assertions.
<!--@mdc :: -->

If only routing tests are required, this is a "lighter" way of testing because
the framework will not configure and run the full Helidon server. This way, no
real ports will be opened. All the communication will be done through
`DirectClient`, which makes the tests very effective.

It is required to annotate the test class with the `@RoutingTest` annotation to
trigger the server to do the configuration. Thus, it will inject the
DirectClient client, which can then be used in unit tests.

Routing is configured the same way as in full server testing using the
`@SetUpRoute` annotation.

## Virtual Threads

Helidon tests can detect virtual thread pinning, which occurs when a virtual
thread blocks its carrier thread and prevents the scheduler from using it for
other virtual threads. This can happen when blocking native code is invoked.
Pinning can negatively affect application performance.

Enable pinning detection:

```java
@ServerTest(pinningDetection = true)
```

Pinning is considered as harmful when it takes longer than 20 milliseconds, that
is also the default when detecting it within Helidon tests.

Pinning threshold can be changed with:

Configure pinning threshold:

<!--@mdc ::code-callout -->
```java
@ServerTest(pinningDetection = true, pinningThreshold = 50)// <1>
```
1. Change pinning threshold from default(20) to 50 milliseconds.
<!--@mdc :: -->

When pinning is detected, test fails with stacktrace pointing to the line of
code causing it.

## Service Registry

Tests that use `ServiceRegistry`, or that test components that needs access to
it isolated from other test (such as when using `Services.get(Config.class)`),
or that need instances from the `ServiceRegistry` injected as constructor or
method parameters can do so by using our testing module.

The JUnit5 testing module ensures that a global service registry is created that
will be unique for the test class, and that adds an extension that can provide
parameters from the `ServiceRegistry`.

Required dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.testing</groupId>
  <artifactId>helidon-testing-junit5</artifactId>
  <scope>test</scope>
</dependency>
```

To add the extension to your test class, annotate the class with
`@io.helidon.testing.junit5.Testing.Test`. In case you use one of the existing
testing annotation for server or routing (`@ServerTest`, `@RoutingTest`), this
is implied.

You can also use `@Service.Named` qualifier on such parameters to only inject
the named instance(s).

## Additional Information

### WebSocket Testing

If WebSocket testing is required, there is an additional module for it. It is
necessary to include the following Maven dependency to the Project’s pom file:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.testing.junit5</groupId>
  <artifactId>helidon-webserver-testing-junit5-websocket</artifactId>
  <scope>test</scope>
</dependency>
```

Configure `WsRouting.Builder` with `@SetUpRoute` and inject `WsClient` into the
test. Connecting starts asynchronous communication, so wait for a response
before asserting its value:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.webclient.websocket.WsClient;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class WsSocketTest {
    @SetUpRoute
    static void routing(WsRouting.Builder ws) {
        ws.endpoint("/echo", new WsListener() {
            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                session.send(text, last);
            }
        });
    }

    @Test
    void echo(WsClient client) throws Exception {
        var message = new CompletableFuture<String>();
        client.connect("/echo", new WsListener() {
            private final StringBuilder text = new StringBuilder();

            @Override
            public void onOpen(WsSession session) {
                session.send("hello", true);
            }

            @Override
            public void onMessage(WsSession session, String fragment, boolean last) {
                text.append(fragment);
                if (last) {
                    session.close(WsCloseCodes.NORMAL_CLOSE, "Done");
                    message.complete(text.toString());
                }
            }

            @Override
            public void onError(WsSession session, Throwable error) {
                message.completeExceptionally(error);
            }
        });
        assertThat(message.get(10, TimeUnit.SECONDS), is("hello"));
    }
}
```

For WebSocket routing tests without opening ports, use `@RoutingTest` and inject
`DirectWsClient` from `io.helidon.webserver.testing.junit5.websocket`. The
extension also accepts `WsClient` parameters in routing tests and supplies a
direct client, so the example above can use `@RoutingTest` instead of `@ServerTest`.

### HTTP/2 Testing

Add the HTTP/2 extension:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.testing.junit5</groupId>
  <artifactId>helidon-webserver-testing-junit5-http2</artifactId>
  <scope>test</scope>
</dependency>
```

Inject `Http2Client` into a server test. Configure HTTP routes with
`HttpRouting.Builder` or `HttpRules`; `Http2Route` can restrict a route to HTTP/2:

```java
import io.helidon.http.Method;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class Http2ServerTest {
    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.route(Http2Route.route(Method.GET, "/greet", (req, res) -> res.send("hello")));
    }

    @Test
    void greet(Http2Client client) {
        assertThat(client.get("/greet").request(String.class).entity(), is("hello"));
    }
}
```

For frame-level protocol checks, inject `Http2TestClient` from
`io.helidon.webserver.testing.junit5.http2`. Close each `Http2TestConnection`
created by that client using try-with-resources. Both HTTP/2 clients accept
`@Socket` to select a named server socket.

### gRPC Testing

Add the gRPC extension:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.testing.junit5</groupId>
  <artifactId>helidon-webserver-testing-junit5-grpc</artifactId>
  <scope>test</scope>
</dependency>
```

Configure `GrpcRouting.Builder` with `@SetUpRoute` and inject `GrpcClient` into
the server test. The client provides a gRPC channel for generated stubs or
direct calls. This example defines a unary echo service using protobuf's
`StringValue`, without requiring generated application classes:

```java
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class GrpcServerTest {
    private static final MethodDescriptor<StringValue, StringValue> ECHO =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("Echo/echo")
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    @SetUpRoute
    static void routing(GrpcRouting.Builder routing) {
        routing.service(ServerServiceDefinition.builder("Echo")
                                .addMethod(ECHO, ServerCalls.asyncUnaryCall((request, observer) -> {
                                    observer.onNext(request);
                                    observer.onCompleted();
                                }))
                                .build());
    }

    @Test
    void echo(GrpcClient client) {
        StringValue response = ClientCalls.blockingUnaryCall(client.channel(),
                                                            ECHO,
                                                            CallOptions.DEFAULT,
                                                            StringValue.of("hello"));
        assertThat(response.getValue(), is("hello"));
    }
}
```

The injected `GrpcClient` targets the test server; use `@Socket` to select a
named socket. gRPC requires a running server and is not supported by
`@RoutingTest`.

### JSON-RPC Testing

Add the JSON-RPC extension:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.testing.junit5</groupId>
  <artifactId>helidon-webserver-testing-junit5-jsonrpc</artifactId>
  <scope>test</scope>
</dependency>
```

Register `JsonRpcRouting` on the HTTP routing builder and inject
`JsonRpcClient`. JSON-RPC does not add a separate `@SetUpRoute` parameter type:

```java
import io.helidon.json.JsonString;
import io.helidon.webclient.jsonrpc.JsonRpcClient;
import io.helidon.webclient.jsonrpc.JsonRpcClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.jsonrpc.JsonRpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class JsonRpcServerTest {
    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.register("/rpc", JsonRpcRouting.builder()
                .register("/echo", "echo", (req, res) -> res.result(JsonString.create("hello")).send())
                .build());
    }

    @Test
    void echo(JsonRpcClient client) {
        try (JsonRpcClientResponse response = client.rpcMethod("echo")
                .rpcId(1)
                .path("/rpc/echo")
                .submit()) {
            assertThat(response.error().isEmpty(), is(true));
            assertThat(response.result().orElseThrow().asJsonValue().asString().value(), is("hello"));
        }
    }
}
```

The injected `JsonRpcClient` supports `@Socket` for named sockets and requires
`@ServerTest`; it is not available with `@RoutingTest`.

## Reference

- [JUnit 5 User Guide][junit-5-user-gui]

[webserverconfig]: https://helidon.io/docs/core/v28/apidocs/io.helidon.webserver/io/helidon/webserver/WebServerConfig.Builder.html
[junit-5-user-gui]: https://junit.org/junit5/docs/current/user-guide/
