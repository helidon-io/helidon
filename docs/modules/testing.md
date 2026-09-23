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
| `Router.RouterBuilder<?>`  | `@ServerTest`, `@RoutingTest` |           |                                                   |
| `ListenerConfig.Builder`   | `@ServerTest`                 |           |                                                   |
| `WebSocketRouting.Builder` | `@ServerTest`, `@RoutingTest` | websocket |                                                   |

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
| `Http3Client`      | x       | `@ServerTest`  | http3     | HTTP/3 client for TLS-enabled listeners                                                    |
| `Http3LowLevelClient` | x    | `@ServerTest`  | http3     | Low-level HTTP/3 protocol test client                                                      |
| `DirectClient`     | x       | `@RoutingTest` |           | Implements `Http1Client` API                                                               |
| `WsClient`         | x       | `@ServerTest`  | websocket |                                                                                            |
| `DirectWsClient`   | x       | `@RoutingTest` | websocket | Implements `WsClient` API                                                                  |

Extensions can enhance the features for the module
`helidon-testing-junit5-webserver` to support additional protocols.

## Examples

You can create the following test to validate that the server returns the
correct response:

Basic Helidon test framework usage:

<!--@mdc ::code-callout -->
```java
@ServerTest // <1>
class MyServerTest {

    final Http1Client client;

    MyServerTest(Http1Client client) { // <2>
        this.client = client;
    }

    @SetUpRoute // <3>
    static void routing(HttpRouting.Builder builder) {
        Main.routing(builder);
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
framework uses this method to inject the configured routing to the subject of
testing – in the current case, the `Quickstart` application.

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
@RoutingTest // <1>
class MyRoutingTest {

    final Http1Client client;

    MyRoutingTest(DirectClient client) { // <2>
        this.client = client;
    }

    @SetUpRoute // <3>
    static void routing(HttpRouting.Builder builder) {
        Main.routing(builder);
    }

    @Test
    void testRootRoute() { // <4>
        try (Http1ClientResponse response = client
                .get("/greet")
                .request()) { // <5>
            JsonObject json = response.as(JsonObject.class); // <6>
            assertThat(json.getString("message"), is("Hello World!"));
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

### HTTP/3 Testing

Add the HTTP/3 testing extension to inject `Http3Client` or
`Http3LowLevelClient` into test constructors or methods:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.testing.junit5</groupId>
  <artifactId>helidon-webserver-testing-junit5-http3</artifactId>
  <scope>test</scope>
</dependency>
```

Use `@ServerTest` and configure the selected listener with TLS and
`Http3Config`. HTTP/3 clients require a running server and are not supported by
`@RoutingTest`; use `DirectClient` for routing tests that do not exercise the
HTTP/3 transport. `Http3Client` provides the usual request API, while
`Http3LowLevelClient` supports low-level response and QPACK assertions.

The following example uses two files that you provide in `src/test/resources`:

- `server-keystore.p12`: a PKCS12 store containing the server private key and
  certificate chain under the alias `server`, with passphrase `changeit`.
- `client-truststore.p12`: a PKCS12 store containing the server certificate or
  its issuing CA certificate, with passphrase `changeit`.

The server certificate must include `localhost` as a DNS Subject Alternative
Name. Injected clients connect to `https://localhost:<listener-port>/`.

```java
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http3.Http3Config;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http3.Http3ClientTls;
import io.helidon.webserver.testing.junit5.http3.Http3LowLevelClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
@Http3ClientTls(resource = "client-truststore.p12")
class MyHttp3ServerTest {
    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.protocolsDiscoverServices(false);
    }

    @SetUpRoute
    static void routing(HttpRules rules, ListenerConfig.Builder listener) {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .passphrase("changeit")
                        .keyAlias("server")
                        .certChainAlias("server")
                        .keystore(Resource.create("server-keystore.p12")))
                .build();

        listener.protocolsDiscoverServices(false)
                .tls(Tls.builder()
                        .privateKey(keys.privateKey().orElseThrow())
                        .privateKeyCertChain(keys.certChain())
                        .build())
                .addProtocol(Http1Config.create())
                .addProtocol(Http3Config.create());
        rules.get("/greet", (req, res) -> res.send("hello"));
    }

    @Test
    void testGreeting(Http3Client client, Http3LowLevelClient lowLevelClient) {
        try (Http3ClientResponse response = client.get("/greet").request()) {
            assertThat(response.status().code(), is(200));
            assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            assertThat(response.as(String.class), is("hello"));
        }
        assertThat(lowLevelClient.get("/greet").status(), is(200));
    }
}
```

`@Http3ClientTls` loads trusted X.509 certificates from a classpath keystore or
truststore named by its required `resource` attribute. Its `type` defaults to
`PKCS12` and its `passphrase` defaults to `changeit`. Class annotations are
inherited by subclasses. An annotation on an injected parameter overrides the
class annotation for that client. Without either annotation, the extension
trusts the selected listener's private-key certificate chain. If that chain is
unavailable, client injection requires an explicit `@Http3ClientTls` annotation.

Unqualified clients use the default listener. For a named listener, configure
it with `@SetUpRoute("custom")` and qualify each client parameter with
`@Socket("custom")`. The named listener also needs TLS and `Http3Config`.

The framework closes injected clients when their test scope ends: clients
created for a test method are closed after that test, and class-scoped clients,
such as parameters of `@BeforeAll`, are closed after all tests. You do not need
to close injected clients yourself. Close each `Http3ClientResponse`, as shown
in the example.

### WebSocket Testing

If WebSocket testing is required, there is an additional module for it. It is
necessary to include the following Maven dependency to the Project’s pom file:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.testing.junit5</groupId>
  <artifactId>helidon-testing-junit5-websocket</artifactId>
  <scope>test</scope>
</dependency>
```

### WebSocket Testing Example

The WebSocket Testing extension adds support for routing configuration and
injection of WebSocket related artifacts, such as WebSockets and DirectWsClient
in Helidon unit tests.

WebSocket sample test:

<!--@mdc ::code-callout -->
```java
@ServerTest
class WsSocketTest {

    static final ServerSideListener WS_LISTENER = new ServerSideListener();
    final WsClient wsClient; // <1>

    WsSocketTest(WsClient wsClient) {
        this.wsClient = wsClient;
    }

    @SetUpRoute
    static void routing(WsRouting.Builder ws) { // <2>
        ws.endpoint("/testWs", WS_LISTENER);
    }

    @Test
    void testWsEndpoint() { // <3>
        ClientSideListener clientListener = new ClientSideListener();
        wsClient.connect("/testWs", clientListener); // <4>
        assertThat(clientListener.message, is("ws")); // <5>
    }
}
```
1. Declare `WsClient` and later inject it in the constructor.
2. Using @SetUpRoute, create WebSocket routing and assign a serverside listener.
3. Test the WebSocket endpoint using the regular @Test annotation.
4. Create and assign the clientside listener.
5. Check if the received message is correct.
<!--@mdc :: -->

<!--@mdc ::code-callout -->
```java
static class ClientSideListener implements WsListener {
    volatile String message;
    volatile Throwable error;

    @Override
    public void onOpen(WsSession session) { // <1>
        session.send("hello", true);
    }

    @Override
    public void onMessage(WsSession session, String text, boolean last) { // <2>
        message = text;
        session.close(WsCloseCodes.NORMAL_CLOSE, "End");
    }

    @Override
    public void onError(WsSession session, Throwable t) { // <3>
        error = t;
    }
}
```
1. Send "Hello" when a connection is opened.
2. Save the message when received and close the connection.
3. React on an error.
<!--@mdc :: -->

The WebSocket `ClientSideListener` is also a helper class that implements
`WsListener` and is very straightforward:

ServerSideListener helper class:

<!--@mdc ::code-callout -->
```java
static class ServerSideListener implements WsListener {
    volatile String message;

    @Override
    public void onMessage(WsSession session, String text, boolean last) { // <1>
        message = text;
        session.send("ws", true);
    }
}
```
1. Send "ws" on a received message.
<!--@mdc :: -->

The testing class should be annotated with `@RoutingTest` only if routing tests
are required without real port opening. Instead of `WsClient`, use
`DirectWsClient`.

## Reference

- [JUnit 5 User Guide][junit-5-user-gui]

[webserverconfig]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/WebServerConfig.Builder.html
[junit-5-user-gui]: https://junit.org/junit5/docs/current/user-guide/
