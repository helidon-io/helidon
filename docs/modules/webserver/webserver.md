<!--@frontmatter
description: "Helidon WebServer"
-->
# WebServer

## Overview

WebServer provides an API for creating HTTP servers. It uses virtual threads and
can handle nearly unlimited concurrent requests.

## Maven Coordinates

To enable WebServer, add the following dependency to your project’s `pom.xml`
(see [Managing Dependencies](../../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver</artifactId>
</dependency>
```

## Configuration

You can configure the WebServer either programmatically or by the Helidon
configuration framework.

### Configuring the WebServer in Your Code

The easiest way to configure the WebServer is in your application code.

```java
WebServer.builder()
    .port(8080)
    .build()
    .start();
```

### Configuring the WebServer in a Configuration File

You can also define the configuration in a file.

WebServer configuration file `application.yaml`:

```yaml [application.yaml]
server:
  port: 8080
  host: "0.0.0.0"
```

Then, in your application code, load the configuration from that file.

WebServer initialization using the `application.yaml` file located on the
classpath:

<!--@mdc ::code-callout -->
```java [application.yaml]
Config config = Config.create(); // <1>
WebServer.builder()
        .config(config.get("server")); // <2>
```
1. `application.yaml` is a default configuration source loaded when YAML support
   is on classpath, so we can just use `Config.create()`
2. Server expects the configuration tree located on the node of `server`
<!--@mdc :: -->

### Configuring Listener Transport Bindings

Listener transport binding configuration is incubating. `server.bindings` is
one object keyed by binding type. The object key is the binding’s only identity;
list form and nested `type` or `name` properties are invalid. The stable
built-in keys are also available as `TransportBindingTypes.TCP` and
`TransportBindingTypes.UDS`.

Configuring another binding does not remove the default TCP binding. TCP
remains active unless `bindings.tcp.enabled` is explicitly set to `false`.

TCP and UDS listener bindings:

```yaml [application.yaml]
server:
  port: 8080
  bindings:
    uds:
      socket: "/var/run/helidon.sock"
      required: true
```

For a UDS-only listener, explicitly disable TCP:

```yaml [application.yaml]
server:
  bindings:
    tcp:
      enabled: false
    uds:
      socket: "/var/run/helidon.sock"
      required: true
```

The equivalent programmatic configuration uses the typed binding configuration
APIs:

```java
WebServer.builder()
        .addBinding(TcpTransportConfig.builder()
                            .enabled(false)
                            .build())
        .addBinding(UdsTransportConfig.builder()
                            .socket(UnixDomainSocketAddress.of("/var/run/helidon.sock"))
                            .required(true)
                            .build());
```

Port-capable bindings inherit the listener host and port. Configure another
listener when another host, port, or UDS path is required.

`max-connections` is shared by all connection-oriented bindings. A binding that
waits in `accept` reserves one permit; therefore, with a finite limit `L` and
`N` active bindings holding idle reservations, concentrated traffic has
worst-case immediately usable capacity `L - N + 1`. The shared limit does not
provide per-binding quotas or strict fairness, and configuration fails when
`L < N`.

Binding runtime, factory, provider, planning, context, TLS-selection, and
protocol-handoff contracts are internal Helidon integration APIs, not a
supported third-party transport SPI.

### Configuring TLS

Configure TLS either programmatically, or by the Helidon configuration
framework.

#### Configuring TLS in Your Code

To configure TLS in WebServer programmatically create your keystore
configuration and pass it to the WebServer builder.

```java
Tls tls = Tls.builder()
    .privateKey(pk -> pk
        .keystore(keys -> keys.keystore(it -> it.resourcePath("private-key.p12"))
            .passphrase("password".toCharArray())))
    .trust(trust -> trust
        .keystore(keys -> keys.keystore(it -> it.resourcePath("trust.p12"))))
    .build();

WebServer.builder()
    .tls(tls);
```

#### Configuring TLS Virtual Hosts with SNI

You can configure listener TLS virtual hosts selected by the TLS Server Name
Indication (SNI) host. Virtual hosts select TLS material only. All requests
still use the listener routing.

```java
Tls defaultTls = Tls.builder()
        .privateKey(pk -> pk
                .keystore(keys -> keys.keystore(it -> it.resourcePath("default-server.p12"))
                        .passphrase("password".toCharArray())))
        .build();
Tls apiTls = Tls.builder()
        .privateKey(pk -> pk
                .keystore(keys -> keys.keystore(it -> it.resourcePath("api-server.p12"))
                        .passphrase("password".toCharArray())))
        .build();

WebServer.builder()
        .tls(defaultTls)
        .addVirtualHost(virtualHost -> virtualHost
                .host("api.example.com")
                .tls(apiTls));
```

The listener default `tls` configuration is the fallback when the client omits
SNI or presents an unmatched SNI host. Exact DNS names and narrow wildcard
patterns such as `*.example.com` are supported. Wildcard patterns match one
left-most label only. If `sni.missing` is set to `REJECT`, the server rejects
connections without SNI. If `sni.unmatched` is set to `REJECT`, the server
aborts an unmatched SNI handshake with a TLS `unrecognized_name` alert.

By default, the server rejects HTTP requests with status `421 Misdirected
Request` when the request authority differs from the client-presented SNI host,
or when fallback TLS is used for an authority configured as a virtual host.

Handlers can inspect both the normalized SNI host requested by the client and
the configured virtual-host host that matched it. The requested host is client
supplied, so treat it as request data; the matched host comes from configuration
and is suitable for configuration-driven choices.

```java
rules.get("/template", (req, res) -> {
    String requestedHost = req.sniRequestedHost().orElse("default.example.com");
    String matchedHost = req.sniMatchedHost().orElse("default");

    res.send(templateFor(matchedHost, requestedHost));
});
```

WebServer SNI virtual-host configuration in `application.yaml`:

```yaml [application.yaml]
server:
  tls:
    private-key:
      keystore:
        passphrase: "password"
        resource:
          resource-path: "default-server.p12"
  virtual-hosts:
    - host: "api.example.com"
      tls:
        private-key:
          keystore:
            passphrase: "password"
            resource:
              resource-path: "api-server.p12"
    - host: "*.example.org"
      tls:
        private-key:
          keystore:
            passphrase: "password"
            resource:
              resource-path: "wildcard-server.p12"
  sni:
    missing: "FALLBACK"
    unmatched: "FALLBACK"
    authority-mismatch: "REJECT"
    fallback-authority: "REJECT"
```

SNI virtual hosts require listener TLS and the NIO socket-channel transport,
which is the default.

#### Reloading TLS Material

You can reload the key and trust material used by an already running TLS
listener. Build a `TlsMaterial` instance with the replacement private key and
certificate chain, trust material, or both, and pass it to the running
`WebServer`.

```java
TlsMaterial material = TlsMaterial.builder()
        .privateKey(pk -> pk
                .keystore(keys -> keys.keystore(it -> it.path(Paths.get("/etc/certs/server.p12")))
                        .passphrase("password".toCharArray())))
        .trust(trust -> trust
                .keystore(keys -> keys.keystore(it -> it.path(Paths.get("/etc/certs/trust.p12")))))
        .build();

server.reloadTls(material);
```

The same API can reload TLS material for a named socket by calling
`reloadTls(TlsMaterial, String)`.

TLS material reload depends on the configured TLS manager. TLS created from an
explicit `SSLContext` cannot be reloaded. TLS material reload does not replace
the listener, routing, SNI rules, application protocols, enabled protocols,
cipher suites, endpoint identification, client-auth mode, session settings, or
other TLS setup options. Existing connections continue to use their current TLS
state. New full TLS handshakes use the reloaded material. Resumed TLS sessions
can continue to use authentication state established before reload until the
session is not reused. The initial TLS setup must include the key or trust
manager that should be reloaded later; reload cannot add a manager that did not
exist when the listener started. Reload fails if the target socket is not
configured for TLS.

For SNI virtual hosts, reload the material for one configured virtual-host name.

```java
TlsMaterial apiMaterial = TlsMaterial.builder()
        .privateKey(pk -> pk
                .keystore(keys -> keys.keystore(it -> it.path(Paths.get("/etc/certs/api-server.p12")))
                        .passphrase("password".toCharArray())))
        .build();

server.reloadVirtualHostTls(apiMaterial, "api.example.com");
```

The named-socket overload `reloadVirtualHostTls(TlsMaterial, String, String)`
reloads a virtual host on a specific socket. The virtual host must already be
configured, otherwise reload fails. TLS reload does not add, remove, or remap
virtual hosts.

#### Configuring TLS in the Config File

It is also possible to configure TLS via the config file.

WebServer TLS configuration file `application.yaml`:

<!--@mdc ::code-callout -->
```yaml [application.yaml]
server:
  tls:
    #Truststore setup
    trust:
      keystore:
        passphrase: "password"
        trust-store: true
        resource:
          # load from classpath
          resource-path: "keystore.p12" # <1>
    # Keystore with private key and server certificate
    private-key:
      keystore:
        passphrase: "password"
        resource:
          # load from file system
          path: "/path/to/keystore.p12" # <2>
```
1. File loaded from classpath.
2. File loaded from file system.
<!--@mdc :: -->

Then, in your application code, load the configuration from that file.

WebServer initialization using the `application.yaml` file located on the
classpath:

<!--@mdc ::code-callout -->
```java [application.yaml]
Config config = Config.create(); // <1>
WebServer.builder()
    .config(config.get("server")); // <2>
```
1. `application.yaml` is a default configuration source loaded when YAML support
   is on classpath, so we can just use `Config.create()`
2. Server expects the configuration tree located on the node of `server`
<!--@mdc :: -->

Or you can only create WebServerTls instance based on the config file.

WebServerTls instance based on `application.yaml` file located on the classpath:

```java [application.yaml]
Config config = Config.create();
WebServer.builder()
    .tls(it -> it.config(config.get("server.tls")));
```

This can alternatively be configured with paths to PKCS#8 PEM files rather than
KeyStores:

WebServer TLS configuration file `application.yaml`:

```yaml [application.yaml]
server:
  tls:
    #Truststore setup
    trust:
      pem:
        certificates:
          resource:
            resource-path: "ca-bundle.pem"
    private-key:
      pem:
        key:
          resource:
            resource-path: "key.pem"
        cert-chain:
          resource:
            resource-path: "chain.pem"
```

#### Configuring TLS over Unix Domain Sockets

TLS can be combined with a Unix domain socket listener by configuring the
explicit `uds` transport binding with a socket path and configuring `tls` as
usual. Disable the default `tcp` binding when the listener should only bind the
Unix domain socket. Unix domain socket listeners use the NIO socket-channel
transport regardless of the `use-nio` setting. The setting must remain `true`,
which is the default, when listener TLS virtual hosts are configured.

Server-side UDS is no longer selected using `bind-address: "unix:..."`; that
form is rejected. Migrate the path to `bindings.uds.socket`. Because TCP is an
overlay default, disable it explicitly for a UDS-only listener.

WebServer TLS over Unix domain socket configuration in `application.yaml`:

```yaml [application.yaml]
server:
  bindings:
    tcp:
      enabled: false
    uds:
      socket: "/var/run/service.sock"
      required: true
  tls:
    private-key:
      keystore:
        passphrase: "password"
        resource:
          resource-path: "server.p12"
```

### Configuration Options

<!--@include ../../config/io.helidon.webserver.WebServer.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-webse].
<!--/include-->

## Routing

Routing lets you use request matching criteria to bind requests to a `handler`
that implements your custom business logic. Matching criteria include one or
more **HTTP Method(s)** and, optionally, a request **path matcher**.

### Routing Basics

Routing also supports *Error Routing* which binds Java `Throwable` to the
handling logic.

Configure HTTP request routing using `HttpRouting.Builder`.

Using HttpRouting.Builder to specify how HTTP requests are handled:

<!--@mdc ::code-callout -->
```java
WebServer.builder()
    .routing(it -> it
        .get("/hello", (req, res) -> res.send("Hello World!"))) // <1>
    .build(); // <2>
```
1. Handle all GETs to `/hello` path. Send the `Hello World!` string.
2. Create a server instance with the provided routing
<!--@mdc :: -->

### HTTP Method Routing

`HttpRouting.Builder` lets you specify how to handle each HTTP method. For
example:

<!--@mdc ::table-collapse -->
| HTTP Method        | HttpRouting.Builder example                                        |
|--------------------|--------------------------------------------------------------------|
| **GET**            | `.get(handler)`                                                    |
| **PUT**            | `.put(handler)`                                                    |
| **POST**           | `.post(handler)`                                                   |
| **HEAD**           | `.head(handler)`                                                   |
| **DELETE**         | `.delete(handler)`                                                 |
| **TRACE**          | `.trace(handler)`                                                  |
| **OPTIONS**        | `.options(handler)`                                                |
| *any method*       | `.any(handler)`                                                    |
| *multiple methods* | `.route(Method.predicate(Method.GET, Method.POST), path, handler)` |
| *custom method*    | `.route(Method.create("CUSTOM"), handler)`                         |
<!--@mdc :: -->

### Path Matcher Routing

You can combine HTTP method routing with request path matching.

```java
routing.post("/some/path", (req, res) -> { /* handler */ });
```

You can use **path pattern** instead of *path* with the following syntax:

- `/foo/bar/baz` - Exact path match against resolved path even with non-usual
  characters
- `/foo/*` - convenience method to match `/foo` or any subpath (but not
  `/foobar`)
- `/foo/{}/baz` - `{}` Unnamed regular expression segment `([^/]+)`
- `/foo/{var}/baz` - Named regular expression segment `([^/]+)`
- `/foo/{var:\d+}` - Named regular expression segment with a specified
  expression
- `/foo/{:\d+}` - Unnamed regular expression segment with a specified expression
- `/foo/{+var}` - Convenience shortcut for `{var:.+}`
- `/foo/{+}` - Convenience shortcut for unnamed segment with regular expression
  `{:.+}`
- `/foo/{*}` - Convenience shortcut for unnamed segment with regular expression
  `{:.*}`
- `/foo[/bar]` - An optional block, which translates to the `/foo(/bar)?`
  regular expression
- `/*` or `/foo*` - `*` Wildcard character can be matched with any number of
  characters.

> [!IMPORTANT]
> Path (matcher) routing is **exact**. For example, a `/foo/bar` request is
> **not** routed to `.post('/foo', ...)`.

> [!TIP]
> Always start *path* and *path patterns* with the `/` character.

For more precise setup of path, you can use factory methods on
`io.helidon.http.PathMatchers` and register using
`HttpRouting.Builder.route(Predicate<Method>, PathMatcher, Handler)` method.

### Using full `HttpRoute`

To have more control over selecting which requests should be handled by a
specific route, you can use the `io.helidon.webserver.http.HttpRoute` interface
using its `Builder`.

<!--@mdc ::code-callout -->
```java
routing.route(HttpRoute.builder()
    .path("/hello")
    .methods(Method.POST, Method.PUT) // <1>
    .handler((req, res) -> {
        String requestEntity = req.content().as(String.class);
        res.send(requestEntity); // <2>
    }));
```
1. The route is specified for `GET` and `POST` requests
2. The handler consumes the request payload and echoes it back
<!--@mdc :: -->

### Organizing Code into Services

By implementing the `io.helidon.webserver.http.HttpService` interface you can
organize your code into one or more services, each with its own path prefix and
set of handlers.

Use HttpRouting.Builder.register to register your service:

```java
routing.register("/hello", new HelloService());
```

Service implementation:

```java
class HelloService implements HttpService {
    @Override
    public void routing(HttpRules rules) {
        rules.get("/subpath", (req, res) -> {
            // Some logic
        });
    }
}
```

In this example, the `GET` handler matches requests to `/hello/subpath`.

### Locating Services at Request Time

Use `HttpServiceLocator` when the stable part of a route is known when the
server starts, but the service that should own the remaining route depends on
request data or application metadata that can change later.

Select an `HttpService` from request path data:

```java
interface ItemServiceRegistry {
    Optional<HttpService> service(String item);
}

static final class MetadataBackedLocator implements HttpServiceLocator {
    private final ItemServiceRegistry registry;

    MetadataBackedLocator(ItemServiceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Optional<HttpService> locate(ServerRequest request) {
        String item = request.path()
                .pathParameters()
                .first("item")
                .orElseThrow();
        return registry.service(item);
    }
}

static final class ItemService implements HttpService {
    @Override
    public void routing(HttpRules rules) {
        rules.get("/{id}", (req, res) -> {
            String id = req.path().pathParameters().first("id").orElseThrow();
            res.send(loadItem(id));
        });
    }

    private String loadItem(String id) {
        return id;
    }
}

void serviceLocator(ItemServiceRegistry registry) {
    HttpRouting routing = HttpRouting.builder()
            .registerLocator("/{item}", new MetadataBackedLocator(registry))
            .build();
}
```

The locator path pattern is prefix-matched before `locate` is invoked. In the
example, the locator sees the `item` path parameter from `/{item}`, and the
located service owns the remaining path such as `/{id}`.

Return `Optional.empty()` when no service is available for a request; routing
then continues with later routes and can fall through to the normal `404`
response. Route order still applies, so register more specific locator prefixes
before broader prefixes.

WebServer caches located services by service instance identity until the server
stops. Return a stable, bounded set of service instances for stable item kinds
so the cached route tree and lifecycle callbacks are reused. Each locator
enforces a default maximum number of cached service instances; override
`HttpServiceLocator.maxServiceCacheSize()` only when the locator intentionally
exposes a larger bounded set.

### Server Lifecycle

Your `HttpService` can interpose on the server lifecycle by overriding
`beforeStart`, `afterStart`, and `afterStop`. Failures from `beforeStart` or
`afterStart` fail `WebServer.start()` after startup cleanup, and failures from
`afterStop` fail `WebServer.stop()` after listener cleanup. Cleanup failures may
be attached as suppressed exceptions.
If `afterStop` runs during suspend or resume failure cleanup, its failure may be
suppressed on the original suspend or resume failure.

WebServer lifecycle:

```java
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

### Using `HttpFeature`

By implementing the `io.helidon.webserver.http.HttpFeature` interface, you can
organize multiple routes and/or filters into a feature, that will be setup
according to its defined `io.helidon.common.Weight` (or using
`io.helidon.common.Weighted`).

Each service has access to the routing builder. HTTP Features are configured for
each routing builder. If there is a need to configure a feature for multiple
sockets, you can use [Server Feature](#server-features) instead.

## Request Handling

Implement the logic to handle requests to WebServer in a `Handler`, which is a
`FunctionalInterface`. Handlers:

- Process the request and [send](#sending-a-response) a response.
- Act as a filter and forward requests to downstream handlers using the
  `response.next()` method.
- Throw an exception to begin [error handling](#error-handling).

### Process Request and Produce Response

Each `Handler` has two parameters. `ServerRequest` and `ServerResponse`.

- Request provides access to the request method, URI, path, query parameters,
  headers and entity.
- Response provides an ability to set response code, headers, and entity.

Status and headers configured on `ServerResponse` are mutable response metadata.
Configure them before calling `send()` or `outputStream()` when they should
apply to the response emitted by the route. Once configured, this metadata
remains on the current response until later handling changes it.

Calling `next()` or `reroute(...)` does not clear it automatically; downstream
routes can replace or remove response metadata before sending the response.

If a handler throws an exception while its response can still be reset,
WebServer discards the unsent entity before invoking error handling. It also
removes entity-related framing, representation, validator, range, digest, and
trailer metadata, together with response preparation callbacks and output
stream filters explicitly registered by Helidon infrastructure for that entity.
Public `beforeSend(...)` listeners and `streamFilter(...)` filters remain
registered so cross-cutting application policies also apply to a replacement
error response.

A response-scoped stream filter that changes representation metadata must use a
response-scoped `beforeSend(...)` listener to configure the matching headers and
register any trailer callback for whichever entity is sent. The configured
status and unrelated metadata, such as CORS, cookies, cache controls, `Vary`,
and custom headers, remain available to the error handler. The error handler is
responsible for configuring or deliberately retaining an appropriate status
before sending the replacement response. Once response metadata or entity bytes
have been sent, the response cannot be replaced.

### Filtering

Filtering can be done either using a dedicated `Filter`, or through routes.

#### Filter

You can register a `io.helidon.webserver.http.Filter` with HTTP routing to
handle filtering in interception style.

A simple filter example:

```java
routing.addFilter((chain, req, res) -> {
    try {
        chain.proceed();
    } finally {
        // do something for any finished request
    }
});
```

#### Routes

The handler forwards the request to the downstream handlers by *nexting*. There
are two options:

- call `res.next()`

  <!--@mdc ::code-callout -->
  ```java
  rules.any("/hello", (req, res) -> { // <1>
      // filtering logic // <2>
      res.next(); // <3>
  });
  ```
  1. handler for any HTTP method using the `/hello` path
  2. business logic implementation
  3. forward the current request to the downstream handler
  <!--@mdc ::-->

- throw an exception to forward to [error handling](#error-handling)

  <!--@mdc ::code-callout -->
  ```java
  rules.any("/hello", (req, res) -> { // <1>
      // filtering logic (e.g., validating parameters) // <2>
      if (userParametersOk()) {
          res.next(); // <3>
      } else {
          throw new IllegalArgumentException("Invalid parameters."); // <4>
      }
  });
  ```
  1. handler for any HTTP method using the `/hello` path
  2. custom logic
  3. forward the current request to the downstream handler
  4. forward the request to the error handler
  <!--@mdc ::-->

### Sending a Response

To complete the request handling, you must send a response by calling the
`res.send()` method.

> [!IMPORTANT]
> one of the variants of `send` method MUST be invoked in the same thread the
> request is started in; as we run in Virtual Threads, you can simply wait for
> any asynchronous tasks that must complete before sending a response

<!--@mdc ::code-callout -->
```java
rules.get("/hello", (req, res) -> { // <1>
    // terminating logic
    res.status(Status.ACCEPTED_202)
        .send("Saved!"); // <2>
});
```
1. handler that terminates the request handling for any HTTP method using the
   `/hello` path
2. send the response
<!--@mdc :: -->

## Protocol-Specific Routing

Handling routes based on the protocol version is possible by registering
specific routes on routing builder.

Routing based on HTTP version:

<!--@mdc ::code-callout -->
```java
rules.get("/any-version", (req, res) ->
        res.send("HTTP Version " + req.prologue().protocolVersion())) // <1>
    .route(Http1Route.route(Method.GET, "/version-specific", (req, res) ->
        res.send("HTTP/1.1 route"))) // <2>
    .route(Http2Route.route(Method.GET, "/version-specific", (req, res) ->
        res.send("HTTP/2 route"))); // <3>
```
1. An HTTP route registered on `/any-version` path that prints the version of
   HTTP protocol
2. An HTTP/1.1 route registered on `/version-specific` path
3. An HTTP/2 route registered on `/version-specific` path
<!--@mdc :: -->

While `Http1Route` for Http/1 is always available with Helidon webserver, other
routes like `Http2Route` for [HTTP/2](#http2-support) needs to be added as
additional dependency.

## Requested URI Discovery

Proxies and reverse proxies between an HTTP client and your Helidon application
mask important information (for example `Host` header, originating IP address,
protocol) about the request the client sent. Fortunately, many of these
intermediary network nodes set or update either the [standard HTTP `Forwarded`
header][standard-http-fo] or the [non-standard `X-Forwarded-*` family of
headers][non-standard-x-f] to preserve information about the original client
request.

Helidon’s requested URI discovery feature allows your application and Helidon
itself to reconstruct information about the original request using the
`Forwarded` header and the `X-Forwarded-*` family of headers.

When you prepare the connections in your server you can include the following
optional requested URI discovery settings:

- enabled or disabled
- which type or types of requested URI discovery to use:
  - `FORWARDED` - uses the `Forwarded` header
  - `X_FORWARDED` - uses the `X-Forwarded-*` headers
  - `HOST` - uses the `Host` header
- what intermediate nodes to trust

When your application invokes `request.requestedUri()` Helidon iterates through
the discovery types you set up for the receiving connection, gathering
information from the corresponding header(s) for that type. If the request does
not have the corresponding header(s), or your settings do not trust the
intermediate nodes reflected in those headers, then Helidon tries the next
discovery type you set up. Helidon uses the `HOST` discovery type if you do not
set up discovery yourself or if, for a particular request, it cannot assemble
the request information using any discovery type you did set up for the socket.

### Setting Up Requested URI Discovery Programmatically

To set up requested URI discovery on the default socket for your server, use the
[`WebServerConfig.Builder`][webserverconfig]:

Requested URI set-up for the default server socket:

<!--@mdc ::code-callout -->
```java
import io.helidon.common.configurable.AllowList;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

import static io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.FORWARDED;
import static io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED;

AllowList trustedProxies = AllowList.builder()
    .addAllowedPattern(Pattern.compile("lb.+\\.mycorp\\.com"))
    .addDenied("lbtest.mycorp.com")
    .build(); // <1>

WebServer.builder()
    .requestedUriDiscoveryContext(it -> it
        .addDiscoveryType(FORWARDED) // <2>
        .addDiscoveryType(X_FORWARDED)
        .trustedProxies(trustedProxies)); // <3>
```
1. Create the `AllowList` describing the intermediate networks nodes to trust
   and
not trust. Presumably the `lbxxx.mycorp.com` nodes are trusted load balancers
except for the test load balancer `lbtest`, and no other nodes are trusted.
`AllowList` accepts prefixes, suffixes, predicates, regex patterns, and exact
matches. See the [`AllowList`][allowlist] Javadoc for complete information.
2. Use `Forwarded` first, then try `X-Forwarded-*` on each request.
3. Set the `AllowList` for trusted intermediaries.
<!--@mdc :: -->

If you build your server with additional sockets, you can control requested URI
discovery separately for each.

### Setting Up Requested URI Discovery using Configuration

You can also use configuration to set up the requested URI discovery behavior.
The following example replicates the settings assigned programmatically in the
earlier code example:

Configuring requested URI behavior:

```yaml
server:
  port: 0
  requested-uri-discovery:
    types: FORWARDED,X_FORWARDED
    trusted-proxies:
      allow:
        pattern: "lb.*\\.mycorp\\.com"
      deny:
        exact: "lbtest.mycorp.com""
```

### Obtaining the Requested URI Information

Your code obtains the requested URI information from the Helidon server request
object:

Retrieving Requested URI Information:

```java
import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriInfo;

rules.get((req, res) -> {
    UriInfo uriInfo = req.requestedUri();
    // ...
});
```

See the [`UriInfo`][uriinfo] Javadoc for more information.

## Error Handling

### Error Routing

You may register an error handler for a specific `Throwable` in a
`HttpRouting.Builder` method.

<!--@mdc ::code-callout -->
```java
routing.error(MyException.class, (req, res, ex) -> { // <1>
    // handle the error, set the HTTP status code
    res.send(errorDescriptionObject); // <2>
});
```
1. Registers an error handler that handles `MyException` that are thrown from
   the upstream handlers
2. Finishes the request handling by sending a response
<!--@mdc :: -->

Error handlers are called when

- an exception is thrown from a handler

As with the standard handlers, the error handler must either

- send a response

  ```java
  routing.error(MyException.class, (req, res, ex) -> {
    res.status(Status.BAD_REQUEST_400);
    res.send("Unable to parse request. Message: " + ex.getMessage());
  });
  ```

- or throw an exception

  ```java
  routing.error(MyException.class, (req, res, ex) -> {
    // some logic
    throw ex;
  });
  ```

Exceptions thrown from error handlers are not error handled, and will end up in
an `InternalServerError`.

### Default Error Handling

If no user-defined error handler is matched, or if the error handler of the
exception threw an exception, then the exception is translated to an HTTP
response as follows:

- Subtypes of `HttpException` are translated to their associated HTTP error
  codes.

  Reply with the 406 HTTP error code by throwing an exception:

  ```java
  rules.get((req, res) -> {
    throw new HttpException(
        "Amount of money must be greater than 0.",
        Status.NOT_ACCEPTABLE_406);
  });
  ```

- Otherwise, the exceptions are translated to an Internal Server Error HTTP
  error code `500`.

## Direct Error Handling

There are a number of scenarios where errors can be detected before the request
routing phase is initiated, some of these include: error validating requests
(e.g. a bad URI), CORS rejections, invalid payloads, unsupported HTTP versions,
etc. For all these type of events, Helidon provides the so-called *direct
handlers*. The complete list of events that are handled in this way is defined
by the enum [EventType][eventtype].

Direct handlers can be configured independently for each port exposed by the
Webserver; similar to other config, if configured directly on the Webserver they
will only apply to the default port. For more information see
[directHandlers][directhandlers] method in `ListenerConfig`.

The following example shows how to register a custom handler for a request that
is deemed invalid before the routing phase stars. The custom handler in this
example simply returns a status code of 400 and a message that references the
server log.

Register a direct handler for bad requests in the Webserver:

```java
public static void main(String[] args) {
    WebServer server = WebServer.builder()
            .directHandlers(DirectHandlers.builder()
                    .addHandler(EventType.BAD_REQUEST, new MyDirectHandler())
                    .build())
            .build()
            .start();
}

static class MyDirectHandler implements DirectHandler {

    @Override
    public TransportResponse handle(TransportRequest transportRequest,
                                    EventType eventType,
                                    Status status,
                                    ServerResponseHeaders serverResponseHeaders,
                                    String s) {
        return DirectHandler.TransportResponse.builder()
                .status(Status.BAD_REQUEST_400)
                .entity("Bad request, see server log")
                .build();
    }
}
```

### Default Direct Error Handler

Helidon includes a *default* direct handler that offers basic support for all
these events out of the box. This default handler supports a couple of config
properties that control logging and error reporting: these are `includeEntity`
and `logAllMessages`. The former controls how data reflection from the request
is handled, while the latter controls logging of potentially sensitive
information. Both of these flags are set to `false` by default to prevent any
data leak either in the response or in the server log.

The default direct handler’s settings in the Webserver can be controlled via
config:

Configuring error handling on default port:

```yaml
server:
  error-handling:
    include-entity: true
    log-all-messages: true
```

With these settings, the default error handler on the default Webserver port
will log all messages and may include reflected user data in error response
entities.

Note: Even though some request data can be reflected back in responses when
`include-entity` is set to `true`, Helidon will always ensure that it is
properly encoded to prevent common HTML attacks.

Any other port defined in your application may include an `error-handling`
section to configure the default handler behavior on that port.

## TLS Configuration Options

<!--@include ../../config/io.helidon.common.tls.Tls.md#configuration-options delim=--- collapseTables=10 -->
See [Configuration options][io-helidon-commo].
<!--/include-->

## Server Features

Server features provide additional functionality to the WebServer, through
modification of the server configuration, listener configuration, or routing.

A server feature can be added by implementing
`io.helidon.webserver.spi.ServerFeature`. Server features support automated
discovery, as long as the implementation is available through Java
`ServiceLoader`. Server features can also be added through configuration, as can
be seen above in [Configuration Options](#configuration-options), configuration
key `features`.

All features (both `ServerFeature` and [HttpFeature](#using-httpfeature)) honor
weight of the feature (defined either through `@Weight` annotation, or by
implementing `Weighted` interface) when registering routes, `HttpService`, or
`Filter` to the routing.

The following table shows available server features and their weight. The
highest weight is always registered (and invoked) first.

| Feature                            | Weight |
|------------------------------------|--------|
| [Context][context]                 | 1100   |
| [Stuck Thread Detection][stuck-thread-detection] | 1050 |
| [Access Log][access-log]           | 1000   |
| [Tracing][tracing]                 | 900    |
| [HSTS][hsts]                       | 875    |
| [CORS][cors]                       | 850    |
| [Security][security]               | 800    |
| Routing (all handlers and filters) | 100    |
| [OpenAPI][openapi]                 | 90     |
| [Observability][observability]     | 80     |

### Context

Context feature adds a filter that executes all requests within the context of
`io.helidon.common.context.Context`. A `Context` instance is available on
`ServerRequest` even if this feature is not added. This feature adds support for
obtaining request context through
`io.helidon.common.context.Contexts.context()`.

This feature will provide the same behavior as previous versions of Helidon.
Since Helidon 4.0.0, this feature is not automatically added.

To enable execution of routes within Context, add the following dependency to
project’s `pom.xml`:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-context</artifactId>
</dependency>
```

Context feature can be configured, all options shown below are also available
both in config, and programmatically when using builder.

#### Configuration options

<!--@include ../../config/io.helidon.webserver.context.ContextFeature.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-webse-2].
<!--/include-->

### Stuck Thread Detection

Stuck thread detection is an opt-in WebServer feature that reports HTTP request
threads which have been executing longer than a configured threshold. The
feature starts one monitoring virtual thread for each selected server socket.
Each monitor periodically scans only ordinary HTTP requests while they execute
within the routing and filter chain; it does not enumerate JVM threads.

The first scan after a request crosses the threshold logs a warning with the
request method, path without query or matrix parameters, socket and request
identifiers, thread state, and stack trace. If that request later completes, the
feature normally logs an informational recovery message. Recovery logging is
bounded and best effort; when a burst exceeds the recovery queue capacity, the
monitor logs an informational summary with the number of omitted individual
recovery messages. Detection is diagnostic only and never interrupts a request
thread.

For HTTP/1.1, only the connection thread while it is processing a request is a
candidate; idle keep-alive time is excluded. For HTTP/2, only ordinary HTTP
stream threads are candidates; the connection reader and subprotocol processing
such as gRPC streams are excluded. Post-routing transport cleanup and
connections handed to an upgraded protocol are also excluded. Legitimately
long-running handlers, such as server-sent events or long polling, can still be
reported.

#### Configuring Stuck Thread Detection in Your Code

```java
WebServer.builder()
        .addFeature(StuckThreadDetectionFeature.create(config -> config
                .threshold(Duration.ofMinutes(10))
                .checkPeriod(Duration.ofMinutes(1))));
```

#### Configuring Stuck Thread Detection in a Configuration File

```yaml [application.yaml]
server:
  features:
    stuck-thread-detection:
      threshold: PT10M
      check-period: PT1M
```

With this configuration, a request becomes eligible after 10 minutes and is
normally detected on the next scan, within the following minute. Both
`threshold` and `check-period` must be positive durations representable in
nanoseconds. The feature can be limited to named server sockets using the
`sockets` option; an empty list selects all sockets.

#### Configuration options

<!--@include ../../config/io.helidon.webserver.StuckThreadDetectionFeature.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][stuck-thread-config].
<!--/include-->

### Access Log

Access logging in Helidon is done by a dedicated module that can be added to
WebServer and configured.

Access logging is a Helidon WebServer `ServerFeature`. Access Log feature has a
very high weight, so it is registered before other features (such as security)
that may terminate a request. This is to ensure the log contains all requests
with appropriate status codes.

To enable Access logging add the following dependency to project’s `pom.xml`:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-access-log</artifactId>
</dependency>
```

#### Configuring Access Log in Your Code

`AccessLogFeature` is discovered automatically by default, and configured
through `server.features.access-log`. You can also configure this feature in
code by registering it with WebServer (which will replace the discovered
feature).

```java
WebServer.builder()
        .addFeature(AccessLogFeature.builder()
                            .commonLogFormat()
                            .build());
```

#### Configuring Access Log in a Configuration File

Access log can be configured as follows:

Access Log configuration file:

```yaml
server:
  port: 8080
  features:
    access-log:
      format: "%h %l %u %t %r %s %b %{Referer}i"
```

All options shown below are also available programmatically when using builder.

#### Configuration options

<!--@include ../../config/io.helidon.webserver.accesslog.AccessLogFeature.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-webse-3].
<!--/include-->


See the [manifest](../../config/manifest.md) for all available types.

### HSTS

HTTP Strict Transport Security (HSTS) in Helidon is provided by a dedicated
WebServer feature module. The feature adds the `Strict-Transport-Security`
header to responses whose resolved request URI scheme is `https`.

This behavior is intentionally based on the resolved request scheme rather than
an HTTP version check. As a result, the feature works for HTTP/1.1 and HTTP/2
today, and it naturally extends to future secure transports. When TLS is
terminated by a trusted proxy, the feature relies on [requested URI
discovery](#requested-uri-discovery) being configured so Helidon resolves the
external request scheme correctly.

To enable HSTS, add the following dependency to the project’s `pom.xml`:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-hsts</artifactId>
</dependency>
```

#### Configuring HSTS in Your Code

`HstsFeature` is discovered automatically by default and configured through
`server.features.hsts`. You can also configure this feature in code by
registering it with WebServer.

```java
WebServer.builder()
        .addFeature(HstsFeature.builder()
                            .maxAge(Duration.ofDays(365))
                            .includeSubDomains(true)
                            .build());
```

#### Configuring HSTS in a Configuration File

HSTS can be configured as follows:

```yaml
server:
  features:
    hsts:
      max-age: 365d
      include-sub-domains: true
```

The optional `preload` token is a browser preload-list convention and is not
part of RFC 6797 itself. All options shown below are also available
programmatically when using the builder.

#### Configuration options

<!--@include ../../config/io.helidon.webserver.hsts.HstsFeature.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][hsts-config].
<!--/include-->

## Supported Technologies

### HTTP/2 Support

Helidon supports HTTP/2 upgrade from HTTP/1, HTTP/2 without prior knowledge,
HTTP/2 with prior knowledge, and HTTP/2 with ALPN over TLS. HTTP/2 support is
enabled in WebServer by default when it’s artifact is available on classpath.

> [!WARNING]
> For HTTP/2 `request.content().hasEntity()` returns `true` by default. It
> returns `false` only if the request’s header frame includes the `END_STREAM`
> flag or the `Content‑Length` header is present with a value of `0`.

#### Maven Coordinates

To enable HTTP/2 support add the following dependency to your project’s
`pom.xml`.

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-http2</artifactId>
</dependency>
```

### Alternative Service Advertisement

> [!NOTE]
> Alternative service advertisement is a preview feature. The APIs shown here
> are subject to change and will be finalized in a future Helidon release.

WebServer can add an `Alt-Svc` response header to advertise an alternative
service from HTTP/1.1 or HTTP/2 responses handled by HTTP routing. HTTP/2
subprotocol responses, such as gRPC, do not include the configured
advertisement. Advertisement is opt-in and is added only to successful and
redirection responses. An `Alt-Svc` header set by the application takes
precedence over the configured value.

Configure advertisement programmatically on
`io.helidon.webserver.http1.Http1Config` or
`io.helidon.webserver.http2.Http2Config` using
`io.helidon.webserver.http.AltSvcConfig`:

```java
AltSvcConfig altSvc = AltSvcConfig.builder()
        .protocol("h3")
        .port(8443)
        .maxAge(Duration.ofHours(1))
        .persist(true)
        .buildPrototype();

Http1Config http1Config = Http1Config.builder()
        .altSvc(altSvc)
        .build();
```

When `port` is omitted, WebServer uses the local port of a port-capable
binding, such as TCP. A binding without a valid port, such as a Unix domain
socket binding, does not add the configured `Alt-Svc` header unless `port` is
set explicitly. The default protocol is `h3`; maximum age and the `persist`
parameter are omitted by default.

The same advertisement can be configured for either server protocol:

```yaml [application.yaml]
server:
  protocols:
    http_1_1:
      alt-svc:
        protocol: "h3"
        port: 8443
        max-age: PT1H
        persist: true
```

### Static Content Support

Static content is served through a `StaticContentFeature`. As with other server
features, it can be configured through config, or registered with server config
builder.

Static content supports serving of files from classpath, or from any readable
directory on the file system. Each content handler must include a location, and
can provide a context that will be registered with the WebServer (defaults to
`/`).

#### Maven Coordinates

To enable Static Content Support add the following dependency to your project’s
`pom.xml`.

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver</groupId>
  <artifactId>helidon-webserver-static-content</artifactId>
</dependency>
```

#### Registering Static Content

To register static content based on a file system (`/pictures`), and classpath
(`/`):

server feature using WebServerConfig.Builder:

<!--@mdc ::code-callout -->
```java
builder.addFeature(StaticContentFeature.builder() // <1>
    .addPath(p -> p.location(Paths.get("/some/WEB/pics")) // <2>
        .context("/pictures")) // <3>
    .addClasspath(cl -> cl.location("/static-content") // <4>
        .welcome("index.html") // <5>
        .context("/")) // <6>
    .build());
```
1. Create a new `StaticContentFeature` to register with the web server (will be
   served on all sockets by default)
2. Add path location served from `/some/WEB/pics` absolute path
3. Associate the path location with server context `/pictures`
4. Add classpath location to serve resources from the contextual `ClassLoader`
   from location `/static-content`
5. `index.html` is the file that is returned if a directory is requested
6. serve the classpath content on root context `/`
<!--@mdc :: -->

Static content can also be registered using the configuration of server feature.

If you use `Config` with your webserver setup, you can register the same static
content using configuration:

```yaml [application.yaml]
server:
  features:
    static-content:
      path:
        - context: "/pictures"
          location: "/some/WEB/pics"
      classpath:
        - context: "/"
          welcome: "index.html"
          location: "/static-content"
```

See [Static Content Feature Configuration Reference][static-content-f] for
details of configuration options.

#### Resource Lifecycle

Static content is intended for resources that remain unchanged while the
service is running. Helidon does not cache missing file-system resources, so
files added under a configured directory or at a configured single-file
location can be discovered by later requests. For disk-backed resources whose
content is not cached in memory, Helidon also verifies that the source still
exists before using its cached location and metadata. A removed resource is no
longer served, and adding it again after the removal has been observed creates a
new cache record with current metadata for subsequent requests.

Helidon does not detect modifications or replacements of an existing resource,
including a resource deleted and recreated between requests without its absence
being observed. Even when the absence is observed, invalidation affects only
future cache lookups. A request that is already using the previous cache record
can still apply its cached metadata to the recreated resource, such as its old
ETag, last-modified time, or content length.

To safely replace an existing disk-backed resource, use one of the following
procedures:

- Restart the service, replacing the resource while the service is stopped and
  before it accepts further static-content requests.
- Drain traffic and wait for in-flight requests to complete, delete the
  resource, request every static-content URL that can resolve to the resource
  over HTTP on every service node and verify that none serves it, recreate the
  resource, and then resume traffic.

Retargeting an already-resolved symbolic link at runtime is not supported.
Resources explicitly cached in memory are snapshots and continue to be served
if their source file is removed.

ETags are derived from the last-modified time and, when available, content
length rather than from a hash of the resource bytes. If content is replaced
between service lifetimes, make sure its observed millisecond timestamp or known
length changes; otherwise the replacement can reuse the same ETag.

### Media Types Support

WebServer and WebClient share the HTTP media support of Helidon, and any
supported media type can be used in both. The media type support is
automatically discovered from classpath. Programmatic support is of course
enabled as well through `MediaContext`.

Customized media support for WebServer

```java
WebServer.builder()
    .mediaContext(it -> it
        .mediaSupportsDiscoverServices(false)
        .addMediaSupport(JsonSupport.create())
        .build());
```

Each registered (or discovered) media support adds support for writing and
reading entities of a specific type.

The following table lists JSON media supports:

| Media type                       | TypeName           | Maven groupId:artifactId                                 | Supported Java type(s)  |
|----------------------------------|--------------------|----------------------------------------------------------|-------------------------|
| **[JSON][json]**                 | JsonSupport        | `io.helidon.http.media:helidon-http-media-json`           | `JsonObject, JsonArray` |
| **[JSON Binding][json-binding]** | JsonBindingSupport | `io.helidon.http.media:helidon-http-media-json-binding`   | Any \*                  |
| **[Jackson][jackson]**           | JacksonSupport     | `io.helidon.http.media:helidon-http-media-jackson`        | Any \*                  |

JSON Binding and Jackson have lower weight, so they are used only when no other
media type matched the object being written or read.

#### JSON Support

The WebServer supports Helidon JSON. When enabled, you can send and receive
`io.helidon.json.JsonObject` and `JsonArray` objects transparently.

**Maven Coordinates**

To enable JSON Support add the following dependency to your project’s `pom.xml`.

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.http.media</groupId>
  <artifactId>helidon-http-media-json</artifactId>
</dependency>
```

**Usage**

Handler that receives and returns JSON objects:

<!--@mdc ::code-callout -->
```java
rules.post("/hello", (req, res) -> {
    JsonObject requestEntity = req.content().as(JsonObject.class); // <1>
    JsonObject responseEntity = JsonObject.builder() // <2>
        .set("message", "Hello " + requestEntity.string("name"))
        .build();
    res.send(responseEntity); // <3>
});
```
1. Get the request entity as `JsonObject`.
2. Create a new `JsonObject` for the response entity.
3. Send the `JsonObject` in the response.
<!--@mdc :: -->

Example of posting JSON to sayHello endpoint:

```shell [Terminal]
curl --noproxy '*' -X POST -H "Content-Type: application/json" \
    http://localhost:8080/sayhello -d '{"name":"Joe"}'
```

```json [Response]
{"message":"Hello Joe"}
```

#### JSON Binding Support

When JSON Binding support is enabled, Java objects are serialized to and
deserialized from JSON automatically if they are annotated with `@Json.Entity`
and annotation processing is configured.

**Maven Coordinates**

To enable JSON Binding support add the following dependency to your project’s
`pom.xml`.

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.http.media</groupId>
  <artifactId>helidon-http-media-json-binding</artifactId>
</dependency>
```

Configure the Helidon annotation processors in the Maven compiler plug-in:

```xml [pom.xml]
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>io.helidon.bundles</groupId>
        <artifactId>helidon-bundles-apt</artifactId>
        <version>${helidon.version}</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

**Usage**

Now that automatic JSON serialization and deserialization facilities have been
set up, you can register a `Handler` that works with Java objects instead of raw
JSON.

Suppose you have a `Person` class that looks like this:

Hypothetical Person class:

```java
@Json.Entity
public class Person {

    private String name;

    public Person() {
        super();
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

Then you can set up a `Handler` like this:

A Handler that works with Java objects instead of raw JSON:

<!--@mdc ::code-callout -->
```java
rules.post("/echo", (req, res) -> {
    res.send(req.content().as(Person.class)); // <1>
});
```
1. This handler consumes a `Person` instance and simply echoes it back. Note
   that there is no work with raw JSON here.
<!--@mdc :: -->

Example of posting JSON to the /echo endpoint:

```shell [Terminal]
curl --noproxy '*' -X POST -H "Content-Type: application/json" \
    http://localhost:8080/echo -d '{"name":"Joe"}'
{"name":"Joe"}
```

#### Jackson Support

The WebServer supports [Jackson][jackson-2]. When this support is enabled, Java
objects will be serialized to and deserialized from JSON automatically using
Jackson.

**Maven Coordinates**

To enable Jackson Support add the following dependency to your project’s
`pom.xml`.

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.http.media</groupId>
  <artifactId>helidon-http-media-jackson</artifactId>
</dependency>
```

**Configuration**

It is possible to configure the Jackson ObjectMapper instance via programmatic
or configuration-based approach.

**Configuration options**

<!--@include ../../config/io.helidon.http.media.jackson.JacksonSupport.md#configuration-options delim=--- offset=4 collapseTables=10 -->
See [Configuration options][io-helidon-http--2].
<!--/include-->

**Example**

Example Jackson configuration:

```yaml
jackson:
  properties:
    FAIL_ON_UNKNOWN_PROPERTIES: false
```

**Usage**

Now that automatic JSON serialization and deserialization facilities have been
set up, you can register a `Handler` that works with Java objects instead of raw
JSON. Deserialization from and serialization to JSON will be handled by
[Jackson][jackson-2].

Suppose you have a `Person` class that looks like this:

Hypothetical Person class:

```java
public class Person {

    private String name;

    public Person() {
        super();
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

Then you can set up a `Handler` like this:

A Handler that works with Java objects instead of raw JSON:

<!--@mdc ::code-callout -->
```java
rules.post("/echo", (req, res) -> {
    res.send(req.content().as(Person.class)); // <1>
});
```
1. This handler consumes a `Person` instance and simply echoes it back. Note
   that there is no working with raw JSON here.
<!--@mdc :: -->

Example of posting JSON to the /echo endpoint:

```shell [Terminal]
curl --noproxy '*' -X POST -H "Content-Type: application/json" \
    http://localhost:8080/echo -d '{"name":"Joe"}'
```

Response body:

```json
{"name":"Joe"}
```


### HTTP Content Encoding

HTTP encoding can improve bandwidth utilization and transfer speeds in certain
scenarios. It requires a few extra CPU cycles for compressing and uncompressing,
but these can be offset if data is transferred over low-bandwidth network links.

A client advertises the compression encodings it supports at request time, and
the WebServer responds by selecting an encoding it supports and setting it in a
header, effectively *negotiating* the content encoding of the response. If none
of the advertised encodings is supported by the WebServer, the response is
returned uncompressed.

Handlers can encode the response and set the appropriate header to preempt
encoding by the WebServer. For instance, if a Handler sets the
`Content-Encoding: gzip` header then the response will not be additionally
compressed.

#### Configuring HTTP Encoding

HTTP encoding support is discovered automatically by WebServer from the
classpath, or it can be customized programmatically.

Encoding can be configured per socket.

Disabling discovery and registering a Gzip encoding support:

```java
WebServer.builder()
    .contentEncoding(it -> it
    .contentEncodingsDiscoverServices(false)
    .addContentEncoding(GzipEncoding.create()));
```

Or use a config file using the following options:

**Configuration options**

<!--@include ../../config/io.helidon.http.encoding.ContentEncodingContext.md#configuration-options delim=--- offset=3 collapseTables=10 -->
See [Configuration options][io-helidon-http--4].
<!--/include-->


The following providers are currently available (simply add the library on the
classpath):

| Encoding type | TypeName       | Maven groupId:artifactId                                 |
|---------------|----------------|----------------------------------------------------------|
| **gzip**      | GzipEncoding   | `io.helidon.http.encoding:helidon-http-encoding-gzip`    |
| **deflate**   | DeflateEncoding | `io.helidon.http.encoding:helidon-http-encoding-deflate` |

#### HTTP Compression Negotiation

HTTP compression negotiation is controlled by clients using the
`Accept-Encoding` header. The value of this header is a comma-separated list of
encodings. The WebServer will select one of these encodings for compression
purposes; it currently supports `gzip` and `deflate`.

For example, if the request includes `Accept-Encoding: gzip, deflate`, and HTTP
compression has been enabled as shown above, the response shall include the
header `Content-Encoding: gzip` and a compressed payload.

### Proxy Protocol Support

The [Proxy Protocol][proxy-protocol] provides a way to convey client information
across reverse proxies or load balancers which would otherwise be lost given
that new connections are established for each network hop. Often times, this
information can be carried in HTTP headers, but not all proxies support this
feature. Helidon is capable of parsing a proxy protocol header (i.e., a network
preamble) that is based on either V1 or V2 of the protocol, thus making client
information available to service developers.

Proxy Protocol support is enabled via configuration, and can be done either
declaratively or programmatically. Once enabled, every new connection on the
corresponding port **MUST** be preambled by a proxy header for the connection
not to be rejected as invalid --that is, proxy headers are never optional.

Programmatically, support for the Proxy Protocol is enabled as follows:

```java
WebServer.builder()
        .enableProxyProtocol(true);
```

Declaratively, support for the Proxy Protocol is enabled as follows:

```yaml
server:
  port: 8080
  host: 0.0.0.0
  enable-proxy-protocol: true
```

#### Accessing Proxy Protocol Data

There are two ways in which the header data can be accessed in your application.
One way is by obtaining the protocol data directly from a request as shown next:

```java
rules.get("/", (req, res) -> {
    ProxyProtocolData data = req.proxyProtocolData().orElse(null);
    if (data != null
        && data.family() == ProxyProtocolData.Family.IPv4
        && data.protocol() == ProxyProtocolData.Protocol.TCP
        && data.sourceAddress().equals("192.168.0.1")
        && data.destAddress().equals("192.168.0.11")
        && data.sourcePort() == 56324
        && data.destPort() == 443) {
        // ...
    }
});
```

> [!NOTE]
> Every request associated with a certain connection shall have access to the
> Proxy Protocol data received when the connection was opened.

Alternatively, the WebServer also makes the original client source address and
source port available in the HTTP headers `X-Forwarded-For` and
`X-Forwarded-Port`, respectively. In some cases, it is just simpler to inspect
these headers instead of getting the complete `ProxyProtocolData` instance as
shown above.

#### Accessing Proxy Protocol V2 Data

The binary (V2) version of the Proxy Protocol includes additional information
beyond that found in the text (V1) protocol version. The V2 version exposes a
proxy command type (LOCAL or PROXY), allows source and destination addresses to
be Unix domain sockets, and supports structured metadata using Tag-Length-Value
(TLV) encoded structures. Helidon makes this additional information available
through the `ProxyProtocolV2Data` interface, which extends `ProxyProtocolData`.

To access the V2 data, check whether the `ProxyProtocolData` object obtained
from the request implements the `ProxyProtocolV2Data` interface:

```java
rules.get("/", (req, res) -> {
    ProxyProtocolData data = req.proxyProtocolData().orElse(null);
    // The data object will be an instance of ProxyProtocolV2Data if V2 of the Proxy Protocol
    // was used by the upstream proxy.
    if (data instanceof ProxyProtocolV2Data v2Data) {
        // PROXY or LOCAL?
        ProxyProtocolV2Data.Command command = v2Data.command();

        // Will be either an InetSocketAddress (for IPv4 or IPv6) or a UnixDomainSocketAddress.
        SocketAddress sourceSocketAddress = v2Data.sourceSocketAddress();
        SocketAddress destSocketAddress = v2Data.destSocketAddress();

        // Contains all of the Tag-Length-Value objects from the Proxy Protocol header.
        List<ProxyProtocolV2Data.Tlv> tlvData = v2Data.tlvs();
    }
});
```

## Additional Information

Here is the code for a minimalist web application that runs on a random free
port:

<!--@mdc ::code-callout -->
```java
public static void main(String[] args) {
    WebServer webServer = WebServer.builder()
        .routing(it -> it.any((req, res) -> res.send("It works!"))) // <1>
        .build() // <2>
        .start(); // <3>

    System.out.println("Server started at: http://localhost:" + webServer.port()); // <4>
}
```
1. For any kind of request, at any path, respond with `It works!`.
2. Build the server with the provided configuration.
3. Start the server (and wait for it to open the port).
4. The server is bound to a random free port.
<!--@mdc :: -->

## Reference

- [Helidon WebServer Javadoc][helidon-webserve]
- [Helidon WebServer Static Content Javadoc][helidon-webserve-2]
- [Helidon JSON Support Javadoc][helidon-json]
- [Helidon JSON Binding Support Javadoc][helidon-json-binding]
- [Helidon Jackson Support Javadoc][helidon-jackson]
- [Proxy Protocol Specification][proxy-protocol]

[standard-http-fo]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Forwarded
[non-standard-x-f]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For
[webserverconfig]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/WebServerConfig.Builder.html
[allowlist]: https://helidon.io/docs/v27/apidocs/io.helidon.common.configurable/io/helidon/common/configurable/AllowList.html
[uriinfo]: https://helidon.io/docs/v27/apidocs/io.helidon.common.uri/io/helidon/common/uri/UriInfo.html
[eventtype]: https://helidon.io/docs/v27/apidocs/io.helidon.http/io/helidon/http/DirectHandler.EventType.html
[directhandlers]: <https://helidon.io/docs/v27/apidocs/io.helidon.webserver/io/helidon/webserver/ListenerConfig.BuilderBase.html#directHandlers(io.helidon.webserver.http.DirectHandlers)>
[context]: #context
[stuck-thread-detection]: #stuck-thread-detection
[access-log]: #access-log
[hsts]: #hsts
[tracing]: ../tracing.md
[cors]: ../cors.md
[security]: ../security/security.md
[openapi]: ../openapi/openapi.md
[observability]: ../observability.md
[static-content-f]: ../../config/io.helidon.webserver.staticcontent.StaticContentFeature.md
[json]: #json-support
[json-binding]: #json-binding-support
[jackson]: #jackson-support
[jackson-2]: https://github.com/FasterXML/jackson#jackson-project-home-github
[proxy-protocol]: https://www.haproxy.org/download/1.8/doc/proxy-protocol.txt
[helidon-webserve]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver/module-summary.html
[helidon-webserve-2]: https://helidon.io/docs/v27/apidocs/io.helidon.webserver.staticcontent/module-summary.html
[helidon-json]: https://helidon.io/docs/v27/apidocs/io.helidon.http.media.json/module-summary.html
[helidon-json-binding]: https://helidon.io/docs/v27/apidocs/io.helidon.http.media.json.binding/module-summary.html
[helidon-jackson]: https://helidon.io/docs/v27/apidocs/io.helidon.http.media.jackson/module-summary.html
[io-helidon-webse]: ../../config/io.helidon.webserver.WebServer.md#configuration-options
[io-helidon-commo]: ../../config/io.helidon.common.tls.Tls.md#configuration-options
[io-helidon-webse-2]: ../../config/io.helidon.webserver.context.ContextFeature.md#configuration-options
[stuck-thread-config]: ../../config/io.helidon.webserver.StuckThreadDetectionFeature.md#configuration-options
[io-helidon-webse-3]: ../../config/io.helidon.webserver.accesslog.AccessLogFeature.md#configuration-options
[hsts-config]: ../../config/io.helidon.webserver.hsts.HstsFeature.md#configuration-options
[io-helidon-http--2]: ../../config/io.helidon.http.media.jackson.JacksonSupport.md#configuration-options
[io-helidon-http--4]: ../../config/io.helidon.http.encoding.ContentEncodingContext.md#configuration-options
