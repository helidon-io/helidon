# WebServer Introduction

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Configuration](#configuration)
  - [Configuring the WebServer in Your Code](#configuring-the-webserver-in-your-code)
  - [Configuring the WebServer in a Configuration File](#configuring-the-webserver-in-a-configuration-file)
  - [Configuring TLS](#configuring-tls)
  - [Configuration Options](#configuration-options)
- [Routing](#routing)
  - [Request Handling](#request-handling)
  - [Error Handling](#error-handling)
- [Server Features](#server-features)
  - [Access Log](#access-log)
  - [Context](#context)
- [Supported Technologies](#supported-technologies)
  - [HTTP/2 Support](#http2-support)
  - [Static Content Support](#static-content-support)
  - [Media Types Support](#media-types-support)
  - [HTTP Content Encoding](#http-content-encoding)
  - [Proxy Protocol Support](#proxy-protocol-support)
- [Reference](#reference)
- [Additional Information](#additional-information)

## Overview

WebServer provides an API for creating HTTP servers. It uses virtual threads and can handle nearly unlimited concurrent requests.

## Maven Coordinates

To enable WebServer, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver</artifactId>
</dependency>
```

## Configuration

You can configure the WebServer either programmatically or by the Helidon configuration framework.

### Configuring the WebServer in Your Code

The easiest way to configure the WebServer is in your application code.

``` java
WebServer.builder()
        .port(8080)
        .build()
        .start();
```

### Configuring the WebServer in a Configuration File

You can also define the configuration in a file.

*WebServer configuration file `application.yaml`*

``` yaml
server:
  port: 8080
  host: "0.0.0.0"
```

Then, in your application code, load the configuration from that file.

*WebServer initialization using the `application.yaml` file located on the classpath*

``` java
Config config = Config.create(); 
WebServer.builder()
        .config(config.get("server")); 
```

- `application.yaml` is a default configuration source loaded when YAML support is on classpath, so we can just use `Config.create()`
- Server expects the configuration tree located on the node of `server`

### Configuring TLS

Configure TLS either programmatically, or by the Helidon configuration framework.

#### Configuring TLS in Your Code

To configure TLS in WebServer programmatically create your keystore configuration and pass it to the WebServer builder.

``` java
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

#### Configuring TLS in the Config File

It is also possible to configure TLS via the config file.

*WebServer TLS configuration file `application.yaml`*

``` yaml
server:
  tls:
    #Truststore setup
    trust:
      keystore:
        passphrase: "password"
        trust-store: true
        resource:
          # load from classpath
          resource-path: "keystore.p12" 
    # Keystore with private key and server certificate
    private-key:
      keystore:
        passphrase: "password"
        resource:
          # load from file system
          path: "/path/to/keystore.p12" 
```

- File loaded from classpath.
- File loaded from file system.

Then, in your application code, load the configuration from that file.

*WebServer initialization using the `application.yaml` file located on the classpath*

``` java
Config config = Config.create(); 
WebServer.builder()
        .config(config.get("server")); 
```

- `application.yaml` is a default configuration source loaded when YAML support is on classpath, so we can just use `Config.create()`
- Server expects the configuration tree located on the node of `server`

Or you can only create WebServerTls instance based on the config file.

*WebServerTls instance based on `application.yaml` file located on the classpath*

``` java
Config config = Config.create();
WebServer.builder()
        .tls(it -> it.config(config.get("server.tls")));
```

This can alternatively be configured with paths to PKCS#8 PEM files rather than KeyStores:

*WebServer TLS configuration file `application.yaml`*

``` yaml
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

### Configuration Options

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="acb486-backlog"></span> `backlog` | `VALUE` | `Integer` | `1024` | Accept backlog |
| <span id="a4fc52-bind-address"></span> `bind-address` | `VALUE` | `i.h.w.W.ListenerCustomMethods` |   | The address to bind to |
| <span id="a32e67-concurrency-limit"></span> [`concurrency-limit`](../../config/io_helidon_common_concurrency_limits_Limit.md) | `VALUE` | `i.h.c.c.l.Limit` |   | Concurrency limit to use to limit concurrent execution of incoming requests |
| <span id="a3f7e3-concurrency-limit-discover-services"></span> `concurrency-limit-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `concurrency-limit` |
| <span id="ac9c91-connection-options"></span> [`connection-options`](../../config/io_helidon_common_socket_SocketOptions.md) | `VALUE` | `i.h.c.s.SocketOptions` |   | Options for connections accepted by this listener |
| <span id="a511a0-content-encoding"></span> [`content-encoding`](../../config/io_helidon_http_encoding_ContentEncodingContext.md) | `VALUE` | `i.h.h.e.ContentEncodingContext` |   | Configure the listener specific `io.helidon.http.encoding.ContentEncodingContext` |
| <span id="aa0fb8-enable-proxy-protocol"></span> `enable-proxy-protocol` | `VALUE` | `Boolean` | `false` | Enable proxy protocol support for this socket |
| <span id="a92b62-error-handling"></span> [`error-handling`](../../config/io_helidon_webserver_ErrorHandling.md) | `VALUE` | `i.h.w.ErrorHandling` |   | Configuration for this listener's error handling |
| <span id="ae9df6-features"></span> [`features`](../../config/io_helidon_webserver_spi_ServerFeature.md) | `LIST` | `i.h.w.s.ServerFeature` |   | Server features allow customization of the server, listeners, or routings |
| <span id="a4431f-features-discover-services"></span> `features-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `features` |
| <span id="a47500-host"></span> `host` | `VALUE` | `String` | `0.0.0.0` | Host of the default socket |
| <span id="a570c4-idle-connection-period"></span> `idle-connection-period` | `VALUE` | `Duration` | `PT2M` | How often should we check for `#idleConnectionTimeout()` |
| <span id="abfcba-idle-connection-timeout"></span> `idle-connection-timeout` | `VALUE` | `Duration` | `PT5M` | How long should we wait before closing a connection that has no traffic on it |
| <span id="acfc0a-ignore-invalid-named-routing"></span> `ignore-invalid-named-routing` | `VALUE` | `Boolean` |   | If set to `true`, any named routing configured that does not have an associated named listener will NOT cause an exception to be thrown (default behavior is to throw an exception) |
| <span id="a71146-max-concurrent-requests"></span> `max-concurrent-requests` | `VALUE` | `Integer` | `-1` | Limits the number of requests that can be executed at the same time (the number of active virtual threads of requests) |
| <span id="a23186-max-in-memory-entity"></span> `max-in-memory-entity` | `VALUE` | `Integer` | `131072` | If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance when writing it |
| <span id="a6e9f1-max-payload-size"></span> `max-payload-size` | `VALUE` | `Long` | `-1` | Maximal number of bytes an entity may have |
| <span id="ac255e-max-tcp-connections"></span> `max-tcp-connections` | `VALUE` | `Integer` | `-1` | Limits the number of connections that can be opened at a single point in time |
| <span id="a847a9-media-context"></span> [`media-context`](../../config/io_helidon_http_media_MediaContext.md) | `VALUE` | `i.h.h.m.MediaContext` |   | Configure the listener specific `io.helidon.http.media.MediaContext` |
| <span id="a390dc-name"></span> `name` | `VALUE` | `String` | `@default` | Name of this socket |
| <span id="a9d956-port"></span> `port` | `VALUE` | `Integer` | `0` | Port of the default socket |
| <span id="abdf05-protocols"></span> [`protocols`](../../config/io_helidon_webserver_spi_ProtocolConfig.md) | `LIST` | `i.h.w.s.ProtocolConfig` |   | Configuration of protocols |
| <span id="a4b6cc-protocols-discover-services"></span> `protocols-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `protocols` |
| <span id="aaf9ce-requested-uri-discovery"></span> [`requested-uri-discovery`](../../config/io_helidon_http_RequestedUriDiscoveryContext.md) | `VALUE` | `i.h.h.RequestedUriDiscoveryContext` |   | Requested URI discovery context |
| <span id="aa99af-restore-response-headers"></span> `restore-response-headers` | `VALUE` | `Boolean` | `true` | Copy and restore response headers before and after passing a request to Jersey for processing |
| <span id="a875ae-shutdown-grace-period"></span> `shutdown-grace-period` | `VALUE` | `Duration` | `PT0.5S` | Grace period in ISO 8601 duration format to allow running tasks to complete before listener's shutdown |
| <span id="aa36d3-shutdown-hook"></span> `shutdown-hook` | `VALUE` | `Boolean` | `true` | When true the webserver registers a shutdown hook with the JVM Runtime |
| <span id="a3378e-smart-async-writes"></span> `smart-async-writes` | `VALUE` | `Boolean` | `false` | If enabled and `#writeQueueLength()` is greater than 1, then start with async writes but possibly switch to sync writes if async queue size is always below a certain threshold |
| <span id="a03604-sockets"></span> [`sockets`](../../config/io_helidon_webserver_ListenerConfig.md) | `MAP` | `i.h.w.ListenerConfig` |   | Socket configurations |
| <span id="ac9efa-tls"></span> [`tls`](../../config/io_helidon_common_tls_Tls.md) | `VALUE` | `i.h.c.t.Tls` |   | Listener TLS configuration |
| <span id="a5f9ab-use-nio"></span> `use-nio` | `VALUE` | `Boolean` | `true` | If set to `true`, use NIO socket channel, instead of a socket |
| <span id="a57ab6-write-buffer-size"></span> `write-buffer-size` | `VALUE` | `Integer` | `4096` | Initial buffer size in bytes of `java.io.BufferedOutputStream` created internally to write data to a socket connection |
| <span id="adda19-write-queue-length"></span> `write-queue-length` | `VALUE` | `Integer` | `0` | Number of buffers queued for write operations |

##### Deprecated Options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a20877-connection-config"></span> [`connection-config`](../../config/io_helidon_webserver_ConnectionConfig.md) | `VALUE` | `i.h.w.ConnectionConfig` | Configuration of a connection (established from client against our server) |
| <span id="ab275b-receive-buffer-size"></span> `receive-buffer-size` | `VALUE` | `Integer` | Listener receive buffer size |

## Routing

Routing lets you use request matching criteria to bind requests to a `handler` that implements your custom business logic. Matching criteria include one or more **HTTP Method(s)** and, optionally, a request **path matcher**.

### Routing Basics

Routing also supports *Error Routing* which binds Java `Throwable` to the handling logic.

Configure HTTP request routing using `HttpRouting.Builder`.

*Using HttpRouting.Builder to specify how HTTP requests are handled*

``` java
WebServer.builder()
        .routing(it -> it
                .get("/hello", (req, res) -> res.send("Hello World!"))) 
        .build(); 
```

- Handle all GETs to `/hello` path. Send the `Hello World!` string.
- Create a server instance with the provided routing

### HTTP Method Routing

`HttpRouting.Builder` lets you specify how to handle each HTTP method. For example:

| HTTP Method | HttpRouting.Builder example |
|----|----|
| **GET** | `.get(handler)` |
| **PUT** | `.put(handler)` |
| **POST** | `.post(handler)` |
| **HEAD** | `.head(handler)` |
| **DELETE** | `.delete(handler)` |
| **TRACE** | `.trace(handler)` |
| **OPTIONS** | `.options(handler)` |
| *any method* | `.any(handler)` |
| *multiple methods* | `.route(Method.predicate(Method.GET, Method.POST), path, handler)` |
| *custom method* | `.route(Method.create("CUSTOM"), handler)` |

### Path Matcher Routing

You can combine HTTP method routing with request path matching.

``` java
routing.post("/some/path", (req, res) -> { /* handler */ });
```

You can use **path pattern** instead of *path* with the following syntax:

- `/foo/bar/baz` - Exact path match against resolved path even with non-usual characters
- `/foo/*` - convenience method to match `/foo` or any subpath (but not `/foobar`)
- `/foo/{}/baz` - `{}` Unnamed regular expression segment `([^/]+)`
- `/foo/{var}/baz` - Named regular expression segment `([^/]+)`
- `/foo/{var:\d+}` - Named regular expression segment with a specified expression
- `/foo/{:\d+}` - Unnamed regular expression segment with a specified expression
- `/foo/{+var}` - Convenience shortcut for `{var:.+}`
- `/foo/{+}` - Convenience shortcut for unnamed segment with regular expression `{:.+}`
- `/foo/{*}` - Convenience shortcut for unnamed segment with regular expression `{:.*}`
- `/foo[/bar]` - An optional block, which translates to the `/foo(/bar)?` regular expression
- `/*` or `/foo*` - `*` Wildcard character can be matched with any number of characters.

> [!IMPORTANT]
> Path (matcher) routing is **exact**. For example, a `/foo/bar` request is **not** routed to `.post('/foo', …​)`.

> [!TIP]
> Always start *path* and *path patterns* with the `/` character.

For more precise setup of path, you can use factory methods on `io.helidon.http.PathMatchers` and register using `HttpRouting.Builder.route(Predicate<Method>, PathMatcher, Handler)` method.

### Using full `HttpRoute`

To have more control over selecting which requests should be handled by a specific route, you can use the `io.helidon.webserver.http.HttpRoute` interface using its `Builder`.

``` java
routing.route(HttpRoute.builder()
                      .path("/hello")
                      .methods(Method.POST, Method.PUT) 
                      .handler((req, res) -> {
                          String requestEntity = req.content().as(String.class);
                          res.send(requestEntity); 
                      }));
```

- The route is specified for `GET` and `POST` requests
- The handler consumes the request payload and echoes it back

### Organizing Code into Services

By implementing the `io.helidon.webserver.http.HttpService` interface you can organize your code into one or more services, each with its own path prefix and set of handlers.

*Use `HttpRouting.Builder.register` to register your service*

``` java
routing.register("/hello", new HelloService());
```

*Service implementation*

``` java
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

### Server Lifecycle

In Helidon 4 your `HttpService` can interpose on the server lifecycle by overriding the `beforeStart` and `afterStop` methods:

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

### Using `HttpFeature`

By implementing the `io.helidon.webserver.http.HttpFeature` interface, you can organize multiple routes and/or filters into a feature, that will be setup according to its defined `io.helidon.common.Weight` (or using `io.helidon.common.Weighted`).

Each service has access to the routing builder. HTTP Features are configured for each routing builder. If there is a need to configure a feature for multiple sockets, you can use [Server Feature](#server-features) instead.

## Request Handling

Implement the logic to handle requests to WebServer in a `Handler`, which is a `FunctionalInterface`. Handlers:

- Process the request and [send](#sending-a-response) a response.
- Act as a filter and forward requests to downstream handlers using the `response.next()` method.
- Throw an exception to begin [error handling](#error-handling).

### Process Request and Produce Response

Each `Handler` has two parameters. `ServerRequest` and `ServerResponse`.

- Request provides access to the request method, URI, path, query parameters, headers and entity.
- Response provides an ability to set response code, headers, and entity.

### Filtering

Filtering can be done either using a dedicated `Filter`, or through routes.

#### Filter

You can register a `io.helidon.webserver.http.Filter` with HTTP routing to handle filtering in interception style.

A simple filter example:

``` java
routing.addFilter((chain, req, res) -> {
    try {
        chain.proceed();
    } finally {
        // do something for any finished request
    }
});
```

#### Routes

The handler forwards the request to the downstream handlers by *nexting*. There are two options:

- call `res.next()`

  ``` java
  rules.any("/hello", (req, res) -> { 
      // filtering logic  
      res.next(); 
  });
  ```

  - handler for any HTTP method using the `/hello` path
  - business logic implementation
  - forward the current request to the downstream handler
- throw an exception to forward to [error handling](#error-handling)

  ``` java
  rules.any("/hello", (req, res) -> { 
      // filtering logic (e.g., validating parameters) 
      if (userParametersOk()) {
          res.next(); 
      } else {
          throw new IllegalArgumentException("Invalid parameters."); 
      }
  });
  ```

  - handler for any HTTP method using the `/hello` path
  - custom logic
  - forward the current request to the downstream handler
  - forward the request to the error handler

### Sending a Response

To complete the request handling, you must send a response by calling the `res.send()` method.

> [!IMPORTANT]
> one of the variants of `send` method MUST be invoked in the same thread the request is started in; as we run in Virtual Threads, you can simply wait for any asynchronous tasks that must complete before sending a response

``` java
rules.get("/hello", (req, res) -> { 
    // terminating logic
    res.status(Status.ACCEPTED_202)
            .send("Saved!"); 
});
```

- handler that terminates the request handling for any HTTP method using the `/hello` path
- send the response

## Protocol-Specific Routing

Handling routes based on the protocol version is possible by registering specific routes on routing builder.

*Routing based on HTTP version*

``` java
rules.get("/any-version", (req, res) -> res.send("HTTP Version " + req.prologue().protocolVersion())) 
        .route(Http1Route.route(Method.GET, "/version-specific", (req, res) -> res.send("HTTP/1.1 route"))) 
        .route(Http2Route.route(Method.GET, "/version-specific", (req, res) -> res.send("HTTP/2 route"))); 
```

- An HTTP route registered on `/any-version` path that prints the version of HTTP protocol
- An HTTP/1.1 route registered on `/version-specific` path
- An HTTP/2 route registered on `/version-specific` path

While `Http1Route` for Http/1 is always available with Helidon webserver, other routes like `Http2Route` for [HTTP/2](#http2-support) needs to be added as additional dependency.

## Requested URI Discovery

Proxies and reverse proxies between an HTTP client and your Helidon application mask important information (for example `Host` header, originating IP address, protocol) about the request the client sent. Fortunately, many of these intermediary network nodes set or update either the [standard HTTP `Forwarded` header](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Forwarded) or the [non-standard `X-Forwarded-*` family of headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For) to preserve information about the original client request.

Helidon’s requested URI discovery feature allows your application—​and Helidon itself—​to reconstruct information about the original request using the `Forwarded` header and the `X-Forwarded-*` family of headers.

When you prepare the connections in your server you can include the following optional requested URI discovery settings:

- enabled or disabled
- which type or types of requested URI discovery to use:
  - `FORWARDED` - uses the `Forwarded` header
  - `X_FORWARDED` - uses the `X-Forwarded-*` headers
  - `HOST` - uses the `Host` header
- what intermediate nodes to trust

When your application invokes `request.requestedUri()` Helidon iterates through the discovery types you set up for the receiving connection, gathering information from the corresponding header(s) for that type. If the request does not have the corresponding header(s), or your settings do not trust the intermediate nodes reflected in those headers, then Helidon tries the next discovery type you set up. Helidon uses the `HOST` discovery type if you do not set up discovery yourself or if, for a particular request, it cannot assemble the request information using any discovery type you did set up for the socket.

### Setting Up Requested URI Discovery Programmatically

To set up requested URI discovery on the default socket for your server, use the [`WebServerConfig.Builder`](/apidocs/io.helidon.webserver/io/helidon/webserver/WebServerConfig.Builder.html):

*Requested URI set-up for the default server socket*

``` java
import io.helidon.common.configurable.AllowList;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

import static io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.FORWARDED;
import static io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED;

AllowList trustedProxies = AllowList.builder()
        .addAllowedPattern(Pattern.compile("lb.+\\.mycorp\\.com"))
        .addDenied("lbtest.mycorp.com")
        .build(); 

WebServer.builder()
        .requestedUriDiscoveryContext(it -> it
                .addDiscoveryType(FORWARDED) 
                .addDiscoveryType(X_FORWARDED)
                .trustedProxies(trustedProxies)); 
```

- Create the `AllowList` describing the intermediate networks nodes to trust and not trust. Presumably the `lbxxx.mycorp.com` nodes are trusted load balancers except for the test load balancer `lbtest`, and no other nodes are trusted. `AllowList` accepts prefixes, suffixes, predicates, regex patterns, and exact matches. See the [`AllowList`](/apidocs/io.helidon.common.configurable/io/helidon/common/configurable/AllowList.html) JavaDoc for complete information.
- Use `Forwarded` first, then try `X-Forwarded-*` on each request.
- Set the `AllowList` for trusted intermediaries.

If you build your server with additional sockets, you can control requested URI discovery separately for each.

### Setting Up Requested URI Discovery using Configuration

You can also use configuration to set up the requested URI discovery behavior. The following example replicates the settings assigned programmatically in the earlier code example:

*Configuring requested URI behavior*

``` yaml
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

Your code obtains the requested URI information from the Helidon server request object:

*Retrieving Requested URI Information*

``` java
import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriInfo;

rules.get((req, res) -> {
    UriInfo uriInfo = req.requestedUri();
    // ...
});
```

See the [`UriInfo`](/apidocs/io.helidon.common.uri/io/helidon/common/uri/UriInfo.html) JavaDoc for more information.

## Error Handling

### Error Routing

You may register an error handler for a specific `Throwable` in a `HttpRouting.Builder` method.

``` java
routing.error(MyException.class, (req, res, ex) -> { 
    // handle the error, set the HTTP status code
    res.send(errorDescriptionObject); 
});
```

- Registers an error handler that handles `MyException` that are thrown from the upstream handlers
- Finishes the request handling by sending a response

Error handlers are called when

- an exception is thrown from a handler

As with the standard handlers, the error handler must either

- send a response

  ``` java
  routing.error(MyException.class, (req, res, ex) -> {
      res.status(Status.BAD_REQUEST_400);
      res.send("Unable to parse request. Message: " + ex.getMessage());
  });
  ```

- or throw an exception

  ``` java
  routing.error(MyException.class, (req, res, ex) -> {
      // some logic
      throw ex;
  });
  ```

Exceptions thrown from error handlers are not error handled, and will end up in an `InternalServerError`.

### Default Error Handling

If no user-defined error handler is matched, or if the error handler of the exception threw an exception, then the exception is translated to an HTTP response as follows:

- Subtypes of `HttpException` are translated to their associated HTTP error codes.

  *Reply with the `406` HTTP error code by throwing an exception*

``` java
  rules.get((req, res) -> {
      throw new HttpException(
              "Amount of money must be greater than 0.",
              Status.NOT_ACCEPTABLE_406); 
  });
  ```

- Otherwise, the exceptions are translated to an Internal Server Error HTTP error code `500`.

## Direct Error Handling

There are a number of scenarios where errors can be detected before the request routing phase is initiated, some of these include: error validating requests (e.g. a bad URI), CORS rejections, invalid payloads, unsupported HTTP versions, etc. For all these type of events, Helidon provides the so-called *direct handlers*. The complete list of events that are handled in this way is defined by the enum [EventType](/apidocs/io.helidon.http/io/helidon/http/DirectHandler.EventType.html).

Direct handlers can be configured independently for each port exposed by the Webserver; similar to other config, if configured directly on the Webserver they will only apply to the default port. For more information see [directHandlers](/apidocs/io.helidon.webserver/io/helidon/webserver/ListenerConfig.BuilderBase.html#directHandlers(io.helidon.webserver.http.DirectHandlers)) method in `ListenerConfig`.

The following example shows how to register a custom handler for a request that is deemed invalid before the routing phase stars. The custom handler in this example simply returns a status code of 400 and a message that references the server log.

*Register a direct handler for bad requests in the Webserver*

``` java
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

Helidon includes a *default* direct handler that offers basic support for all these events out of the box. This default handler supports a couple of config properties that control logging and error reporting: these are `includeEntity` and `logAllMessages`. The former controls how data reflection from the request is handled, while the latter controls logging of potentially sensitive information. Both of these flags are set to `false` by default to prevent any data leak either in the response or in the server log.

The default direct handler’s settings in the Webserver can be controlled via config:

*Configuring error handling on default port*

``` yaml
server:
  error-handling:
    include-entity: true
    log-all-messages: true
```

With these settings, the default error handler—​on the default Webserver port—​will log all messages and may include reflected user data in error response entities.

Note: Even though some request data can be reflected back in responses when `include-entity` is set to `true`, Helidon will always ensure that it is properly encoded to prevent common HTML attacks.

Any other port defined in your application may include an `error-handling` section to configure the default handler behavior on that port.

## Configuration Options

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a6e5c3-cipher-suite"></span> `cipher-suite` | `LIST` | `String` |   | Enabled cipher suites for TLS communication |
| <span id="aa9957-client-auth"></span> [`client-auth`](../../config/io_helidon_common_tls_TlsClientAuth.md) | `VALUE` | `i.h.c.t.TlsClientAuth` | `NONE` | Configure requirement for mutual TLS |
| <span id="ab3264-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Flag indicating whether Tls is enabled |
| <span id="a734ef-endpoint-identification-algorithm"></span> `endpoint-identification-algorithm` | `VALUE` | `String` | `HTTPS` | Identification algorithm for SSL endpoints |
| <span id="a4eeba-internal-keystore-provider"></span> `internal-keystore-provider` | `VALUE` | `String` |   | Provider of the key stores used internally to create a key and trust manager factories |
| <span id="ab7ae6-internal-keystore-type"></span> `internal-keystore-type` | `VALUE` | `String` |   | Type of the key stores used internally to create a key and trust manager factories |
| <span id="a93230-key-manager-factory-algorithm"></span> `key-manager-factory-algorithm` | `VALUE` | `String` |   | Algorithm of the key manager factory used when private key is defined |
| <span id="a49b7a-manager"></span> [`manager`](../../config/io_helidon_common_tls_TlsManager.md) | `VALUE` | `i.h.c.t.TlsManager` |   | The Tls manager |
| <span id="a7cad5-manager-discover-services"></span> `manager-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `manager` |
| <span id="aeed7c-private-key"></span> [`private-key`](../../config/io_helidon_common_pki_Keys.md) | `VALUE` | `i.h.c.p.Keys` |   | Private key to use |
| <span id="a910b8-protocol"></span> `protocol` | `VALUE` | `String` | `TLS` | Configure the protocol used to obtain an instance of `javax.net.ssl.SSLContext` |
| <span id="aef2f6-protocols"></span> `protocols` | `LIST` | `String` |   | Enabled protocols for TLS communication |
| <span id="a0da60-provider"></span> `provider` | `VALUE` | `String` |   | Use explicit provider to obtain an instance of `javax.net.ssl.SSLContext` |
| <span id="a7a660-revocation"></span> [`revocation`](../../config/io_helidon_common_tls_RevocationConfig.md) | `VALUE` | `i.h.c.t.RevocationConfig` |   | Certificate revocation check configuration |
| <span id="ab9360-secure-random-algorithm"></span> `secure-random-algorithm` | `VALUE` | `String` |   | Algorithm to use when creating a new secure random |
| <span id="a82d0c-secure-random-provider"></span> `secure-random-provider` | `VALUE` | `String` |   | Provider to use when creating a new secure random |
| <span id="a59f4a-session-cache-size"></span> `session-cache-size` | `VALUE` | `Integer` | `20480` | SSL session cache size |
| <span id="abf0bb-session-timeout"></span> `session-timeout` | `VALUE` | `Duration` | `PT24H` | SSL session timeout |
| <span id="adbc4b-trust"></span> [`trust`](../../config/io_helidon_common_pki_Keys.md) | `LIST` | `i.h.c.p.Keys` |   | List of certificates that form the trust manager |
| <span id="a0346e-trust-all"></span> `trust-all` | `VALUE` | `Boolean` | `false` | Trust any certificate provided by the other side of communication |
| <span id="af626f-trust-manager-factory-algorithm"></span> `trust-manager-factory-algorithm` | `VALUE` | `String` |   | Trust manager factory algorithm |

<a id="server-features"></a>
## Server Features

Server features provide additional functionality to the WebServer, through modification of the server configuration, listener configuration, or routing.

A server feature can be added by implementing `io.helidon.webserver.spi.ServerFeature`. Server features support automated discovery, as long as the implementation is available through Java `ServiceLoader`. Server features can also be added through configuration, as can be seen above in [Configuration Options](#configuration-options), configuration key `features`.

All features (both `ServerFeature` and [HttpFeature](#using-httpfeature)) honor weight of the feature (defined either through `@Weight` annotation, or by implementing `Weighted` interface) when registering routes, `HttpService`, or `Filter` to the routing.

The following table shows available server features and their weight. The highest weight is always registered (and invoked) first.

| Feature | Weight |
|----|----|
| [Context](#context) | 1100 |
| [Access Log](#access-log) | 1000 |
| [Tracing](../../se/tracing.md) | 900 |
| [CORS](../../se/cors.md) | 850 |
| [Security](../../se/security/introduction.md) | 800 |
| Routing (all handlers and filters) | 100 |
| [OpenAPI](../../se/openapi/openapi.md) | 90 |
| [Observability](../../se/observability.md) | 80 |

<a id="context"></a>
### Context

Context feature adds a filter that executes all requests within the context of `io.helidon.common.context.Context`. A `Context` instance is available on `ServerRequest` even if this feature is not added. This feature adds support for obtaining request context through `io.helidon.common.context.Contexts.context()`.

This feature will provide the same behavior as previous versions of Helidon. Since Helidon 4.0.0, this feature is not automatically added.

To enable execution of routes within Context, add the following dependency to project’s `pom.xml`:

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-context</artifactId>
</dependency>
```

Context feature can be configured, all options shown below are also available both in config, and programmatically when using builder.

#### io.helidon.webserver.context.ContextFeature

##### Description

Configuration of context feature.

##### Usages

- [`server.features.context`](../../config/io_helidon_webserver_spi_ServerFeature.md#a57af2-context)

##### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aa10e9-records"></span> [`records`](../../config/io_helidon_common_context_http_ContextRecordConfig.md) | `LIST` | `i.h.c.c.h.ContextRecordConfig` |   | List of propagation records |
| <span id="ac7113-sockets"></span> `sockets` | `LIST` | `String` |   | List of sockets to register this feature on |
| <span id="a37f63-weight"></span> `weight` | `VALUE` | `Double` | `1100.0` | Weight of the context feature |

See the [manifest](../../config/manifest.md) for all available types.

<a id="access-log"></a>
### Access Log

Access logging in Helidon is done by a dedicated module that can be added to WebServer and configured.

Access logging is a Helidon WebServer `ServerFeature`. Access Log feature has a very high weight, so it is registered before other features (such as security) that may terminate a request. This is to ensure the log contains all requests with appropriate status codes.

To enable Access logging add the following dependency to project’s `pom.xml`:

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-access-log</artifactId>
</dependency>
```

#### Configuring Access Log in Your Code

`AccessLogFeature` is discovered automatically by default, and configured through `server.features.access-log`. You can also configure this feature in code by registering it with WebServer (which will replace the discovered feature).

``` java
WebServer.builder()
        .addFeature(AccessLogFeature.builder()
                            .commonLogFormat()
                            .build());
```

#### Configuring Access Log in a Configuration File

Access log can be configured as follows:

*Access Log configuration file*

``` yaml
server:
  port: 8080
  features:
    access-log:
      format: "%h %l %u %t %r %s %b %{Referer}i"
```

All options shown below are also available programmatically when using builder.

#### io.helidon.webserver.accesslog.AccessLogFeature

##### Description

Configuration of access log feature.

##### Usages

- [`server.features.access-log`](../../config/io_helidon_webserver_spi_ServerFeature.md#a42c97-access-log)

##### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aaefb9-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this feature will be enabled |
| <span id="a8717c-format"></span> `format` | `VALUE` | `String` |   | The format for log entries (similar to the Apache `LogFormat`) |
| <span id="aeb9ad-logger-name"></span> `logger-name` | `VALUE` | `String` | `io.helidon.webserver.AccessLog` | Name of the logger used to obtain access log logger from `System#getLogger(String)` |
| <span id="a631a5-sockets"></span> `sockets` | `LIST` | `String` |   | List of sockets to register this feature on |
| <span id="ac3d7a-weight"></span> `weight` | `VALUE` | `Double` | `1000.0` | Weight of the access log feature |

See the [manifest](../../config/manifest.md) for all available types.

<a id="supported-technologies"></a>
## Supported Technologies

<a id="http2-support"></a>
### HTTP/2 Support

Helidon supports HTTP/2 upgrade from HTTP/1, HTTP/2 without prior knowledge, HTTP/2 with prior knowledge, and HTTP/2 with ALPN over TLS. HTTP/2 support is enabled in WebServer by default when it’s artifact is available on classpath.

> [!WARNING]
> For HTTP/2 `request.content().hasEntity()` returns `true` by default. It returns `false` only if the request’s header frame includes the `END_STREAM` flag or the `Content‑Length` header is present with a value of `0`.

#### Maven Coordinates

To enable HTTP/2 support add the following dependency to your project’s `pom.xml`.

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-http2</artifactId>
</dependency>
```

<a id="static-content-support"></a>
### Static Content Support

Static content is served through a `StaticContentFeature`. As with other server features, it can be configured through config, or registered with server config builder.

Static content supports serving of files from classpath, or from any readable directory on the file system. Each content handler must include a location, and can provide a context that will be registered with the WebServer (defaults to `/`).

#### Maven Coordinates

To enable Static Content Support add the following dependency to your project’s `pom.xml`.

``` xml
<dependency>
    <groupId>io.helidon.webserver</groupId>
    <artifactId>helidon-webserver-static-content</artifactId>
</dependency>
```

#### Registering Static Content

To register static content based on a file system (`/pictures`), and classpath (`/`):

*server feature using `WebServerConfig.Builder`*

``` java
builder.addFeature(StaticContentFeature.builder() 
                           .addPath(p -> p.location(Paths.get("/some/WEB/pics")) 
                                   .context("/pictures")) 
                           .addClasspath(cl -> cl.location("/static-content") 
                                   .welcome("index.html") 
                                   .context("/")) 
                           .build());
```

- Create a new `StaticContentFeature` to register with the web server (will be served on all sockets by default)
- Add path location served from `/some/WEB/pics` absolute path
- Associate the path location with server context `/pictures`
- Add classpath location to serve resources from the contextual `ClassLoader` from location `/static-content`
- `index.html` is the file that is returned if a directory is requested
- serve the classpath content on root context `/`

Static content can also be registered using the configuration of server feature.

If you use `Config` with your webserver setup, you can register the same static content using configuration:

*application.yaml*

``` yaml
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

See [Static Content Feature Configuration Reference](../../config/io_helidon_webserver_staticcontent_StaticContentFeature.md) for details of configuration options.

<a id="media-types-support"></a>
### Media Types Support

WebServer and WebClient share the HTTP media support of Helidon, and any supported media type can be used in both. The media type support is automatically discovered from classpath. Programmatic support is of course enabled as well through `MediaContext`.

Customized media support for WebServer

``` java
WebServer.builder()
        .mediaContext(it -> it
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(JsonpSupport.create())
                .build());
```

Each registered (or discovered) media support adds support for writing and reading entities of a specific type.

The following table lists JSON media supports:

| Media type | TypeName | Maven groupId:artifactId | Supported Java type(s) |
|----|----|----|----|
| **[JSON-P](#json-p-support)** | JsonpSupport | `io.helidon.http.media:helidon-http-media-jsonp` | `JsonObject, JsonArray` |
| **[JSON-B](#json-b-support)** | JsonbSupport | `io.helidon.http.media:helidon-http-media-jsonb` | Any \* |
| **[Jackson](#jackson-support)** | JacksonSupport | `io.helidon.http.media:helidon-http-media-jackson` | Any \* |
| **[Gson](#gson-support)** | GsonSupport | `io.helidon.http.media:helidon-http-media-gson` | Any \* |

- JSON-B and Jackson have lower weight, so they are used only when no other media type matched the object being written or read

#### JSON-P Support

The WebServer supports JSON-P. When enabled, you can send and receive JSON-P objects transparently.

##### Maven Coordinates

To enable JSON Support add the following dependency to your project’s `pom.xml`.

``` xml
<dependency>
    <groupId>io.helidon.http.media</groupId>
    <artifactId>helidon-http-media-jsonp</artifactId>
</dependency>
```

##### Usage

*Handler that receives and returns JSON objects*

``` java
static final JsonBuilderFactory JSON_FACTORY = Json.createBuilderFactory(Map.of()); 

rules.post("/hello", (req, res) -> {
    JsonObject requestEntity = req.content().as(JsonObject.class); 
    JsonObject responseEntity = JSON_FACTORY.createObjectBuilder() 
            .add("message", "Hello " + requestEntity.getString("name"))
            .build();
    res.send(responseEntity); 
});
```

- Using a `JsonBuilderFactory` is more efficient than `Json.createObjectBuilder()`

- Get the request entity as `JsonObject`

- Create a new `JsonObject` for the response entity

- Send `JsonObject` in response

*Example of posting JSON to sayHello endpoint*

``` bash
curl --noproxy '*' -X POST -H "Content-Type: application/json" \
    http://localhost:8080/sayhello -d '{"name":"Joe"}'
```

*Response body*

``` json
{"message":"Hello Joe"}
```

#### JSON-B Support

The WebServer supports the [JSON-B specification](http://json-b.net/). When this support is enabled, Java objects will be serialized to and deserialized from JSON automatically using [Yasson](https://github.com/eclipse-ee4j/yasson), an implementation of the [JSON-B specification](https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0.html).

##### Maven Coordinates

To enable JSON-B Support add the following dependency to your project’s `pom.xml`.

``` xml
<dependency>
    <groupId>io.helidon.http.media</groupId>
    <artifactId>helidon-http-media-jsonb</artifactId>
</dependency>
```

##### Configuration

It is possible to configure the Jsonb instance via programmatic or configuration-based approach. When configured over the configuration, all the configured value types need to be selected correctly according to the JSON-B spec and placed to the right section.

###### Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a0e015-boolean-properties"></span> `boolean-properties` | `MAP` | `Boolean` | Jsonb `boolean` configuration properties |
| <span id="ad0c9b-class-properties"></span> `class-properties` | `MAP` | `Class` | Jsonb `Class` configuration properties |
| <span id="acf561-properties"></span> `properties` | `MAP` | `String` | Jsonb `String` configuration properties |

###### Example

*Example JSON-B configuration*

``` yaml
jsonb:
  boolean-properties:
    jsonb.null-values: true
  properties:
    jsonb.property-naming-strategy: "LOWER_CASE_WITH_DASHES"
```

##### Usage

Now that automatic JSON serialization and deserialization facilities have been set up, you can register a `Handler` that works with Java objects instead of raw JSON. Deserialization from and serialization to JSON will be handled according to the [JSON-B specification](https://jcp.org/en/jsr/detail?id=367).

Suppose you have a `Person` class that looks like this:

*Hypothetical `Person` class*

``` java
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

*A `Handler` that works with Java objects instead of raw JSON*

``` java
rules.post("/echo", (req, res) -> {
    res.send(req.content().as(Person.class)); 
});
```

- This handler consumes a `Person` instance and simply echoes it back. Note that there is not working with raw JSON here.

*Example of posting JSON to the `/echo` endpoint*

``` bash
curl --noproxy '*' -X POST -H "Content-Type: application/json" \
    http://localhost:8080/echo -d '{"name":"Joe"}'
{"name":"Joe"}
```

#### Jackson Support

The WebServer supports [Jackson](https://github.com/FasterXML/jackson#jackson-project-home-github). When this support is enabled, Java objects will be serialized to and deserialized from JSON automatically using Jackson.

##### Maven Coordinates

To enable Jackson Support add the following dependency to your project’s `pom.xml`.

``` xml
<dependency>
    <groupId>io.helidon.http.media</groupId>
    <artifactId>helidon-http-media-jackson</artifactId>
</dependency>
```

##### Configuration

It is possible to configure the Jackson ObjectMapper instance via programmatic or configuration-based approach.

###### Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a0b69d-properties"></span> `properties` | `MAP` | `Boolean` | Jackson configuration properties |

###### Example

*Example Jackson configuration*

``` yaml
jackson:
  properties:
    FAIL_ON_UNKNOWN_PROPERTIES: false
```

##### Usage

Now that automatic JSON serialization and deserialization facilities have been set up, you can register a `Handler` that works with Java objects instead of raw JSON. Deserialization from and serialization to JSON will be handled by [Jackson](https://github.com/FasterXML/jackson#jackson-project-home-github).

Suppose you have a `Person` class that looks like this:

*Hypothetical `Person` class*

``` java
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

*A `Handler` that works with Java objects instead of raw JSON*

``` java
rules.post("/echo", (req, res) -> {
    res.send(req.content().as(Person.class)); 
});
```

- This handler consumes a `Person` instance and simply echoes it back. Note that there is no working with raw JSON here.

*Example of posting JSON to the `/echo` endpoint*

``` bash
curl --noproxy '*' -X POST -H "Content-Type: application/json" \
    http://localhost:8080/echo -d '{"name":"Joe"}'
```

*Response body*

``` json
{"name":"Joe"}
```

#### Gson Support

The WebServer supports [Gson](https://github.com/google/gson#gson). When this support is enabled, Java objects will be serialized to and deserialized from JSON automatically using Gson.

##### Maven Coordinates

To enable Gson Support add the following dependency to your project’s `pom.xml`.

``` xml
<dependency>
    <groupId>io.helidon.http.media</groupId>
    <artifactId>helidon-http-media-gson</artifactId>
</dependency>
```

##### Configuration

It is possible to configure the Gson instance via programmatic or configuration-based approach.

###### Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a26a07-properties"></span> `properties` | `MAP` | `Boolean` | Gson configuration properties |

###### Example

*Example Gson configuration*

``` yaml
gson:
  properties:
    serialize-nulls: false
```

##### Usage

Now that automatic JSON serialization and deserialization facilities have been set up, you can register a `Handler` that works with Java objects instead of raw JSON. Deserialization from and serialization to JSON will be handled by [Gson](++https://github.com/google/gson#gson).

Suppose you have a `Person` class that looks like this:

*Hypothetical `Person` class*

``` java
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

*A `Handler` that works with Java objects instead of raw JSON*

``` java
rules.post("/echo", (req, res) -> {
    res.send(req.content().as(Person.class)); 
});
```

- This handler consumes a `Person` instance and simply echoes it back. Note that there is no working with raw JSON here.

*Example of posting JSON to the `/echo` endpoint*

``` bash
curl --noproxy '*' -X POST -H "Content-Type: application/json" \
    http://localhost:8080/echo -d '{"name":"Joe"}'
```

*Response body*

``` json
{"name":"Joe"}
```

<a id="http-content-encoding"></a>
### HTTP Content Encoding

HTTP encoding can improve bandwidth utilization and transfer speeds in certain scenarios. It requires a few extra CPU cycles for compressing and uncompressing, but these can be offset if data is transferred over low-bandwidth network links.

A client advertises the compression encodings it supports at request time, and the WebServer responds by selecting an encoding it supports and setting it in a header, effectively *negotiating* the content encoding of the response. If none of the advertised encodings is supported by the WebServer, the response is returned uncompressed.

Handlers can encode the response and set the appropriate header to preempt encoding by the WebServer. For instance, if a Handler sets the `Content-Encoding: gzip` header then the response will not be additionally compressed.

#### Configuring HTTP Encoding

HTTP encoding support is discovered automatically by WebServer from the classpath, or it can be customized programmatically.

Encoding can be configured per socket.

Disabling discovery and registering a Gzip encoding support:

``` java
WebServer.builder()
        .contentEncoding(it -> it
        .contentEncodingsDiscoverServices(false)
        .addContentEncoding(GzipEncoding.create()));
```

Or use a config file using the following options:

##### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ab960c-content-encodings"></span> [`content-encodings`](../../config/io_helidon_http_encoding_ContentEncoding.md) | `LIST` | `i.h.h.e.ContentEncoding` |   | List of content encodings that should be used |
| <span id="ac89ac-content-encodings-discover-services"></span> `content-encodings-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `content-encodings` |

The following providers are currently available (simply add the library on the classpath):

| Encoding type | TypeName | Maven groupId:artifactId |
|----|----|----|
| **gzip** | GzipEncoding | `io.helidon.http.encoding:helidon-http-encoding-gzip` |
| **deflate** | DeflateSupport | `io.helidon.http.encoding:helidon-http-encoding-deflate` |

#### HTTP Compression Negotiation

HTTP compression negotiation is controlled by clients using the `Accept-Encoding` header. The value of this header is a comma-separated list of encodings. The WebServer will select one of these encodings for compression purposes; it currently supports `gzip` and `deflate`.

For example, if the request includes `Accept-Encoding: gzip, deflate`, and HTTP compression has been enabled as shown above, the response shall include the header `Content-Encoding: gzip` and a compressed payload.

<a id="proxy-protocol-support"></a>
### Proxy Protocol Support

The [Proxy Protocol](https://www.haproxy.org/download/1.8/doc/proxy-protocol.txt) provides a way to convey client information across reverse proxies or load balancers which would otherwise be lost given that new connections are established for each network hop. Often times, this information can be carried in HTTP headers, but not all proxies support this feature. Helidon is capable of parsing a proxy protocol header (i.e., a network preamble) that is based on either V1 or V2 of the protocol, thus making client information available to service developers.

Proxy Protocol support is enabled via configuration, and can be done either declaratively or programmatically. Once enabled, every new connection on the corresponding port **MUST** be preambled by a proxy header for the connection not to be rejected as invalid --that is, proxy headers are never optional.

Programmatically, support for the Proxy Protocol is enabled as follows:

``` java
WebServer.builder()
        .enableProxyProtocol(true);
```

Declaratively, support for the Proxy Protocol is enabled as follows:

``` yaml
server:
  port: 8080
  host: 0.0.0.0
  enable-proxy-protocol: true
```

#### Accessing Proxy Protocol Data

There are two ways in which the header data can be accessed in your application. One way is by obtaining the protocol data directly from a request as shown next:

``` java
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
> Every request associated with a certain connection shall have access to the Proxy Protocol data received when the connection was opened.

Alternatively, the WebServer also makes the original client source address and source port available in the HTTP headers `X-Forwarded-For` and `X-Forwarded-Port`, respectively. In some cases, it is just simpler to inspect these headers instead of getting the complete `ProxyProtocolData` instance as shown above.

#### Accessing Proxy Protocol V2 Data

The binary (V2) version of the Proxy Protocol includes additional information beyond that found in the text (V1) protocol version. The V2 version exposes a proxy command type (LOCAL or PROXY), allows source and destination addresses to be Unix domain sockets, and supports structured metadata using Tag-Length-Value (TLV) encoded structures. Helidon makes this additional information available through the `ProxyProtocolV2Data` interface, which extends `ProxyProtocolData`.

To access the V2 data, check whether the `ProxyProtocolData` object obtained from the request implements the `ProxyProtocolV2Data` interface:

``` java
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

<a id="additional-information"></a>
## Additional Information

Here is the code for a minimalist web application that runs on a random free port:

``` java
public static void main(String[] args) {
    WebServer webServer = WebServer.builder()
            .routing(it -> it.any((req, res) -> res.send("It works!"))) 
            .build() 
            .start(); 

    System.out.println("Server started at: http://localhost:" + webServer.port()); 
}
```

- For any kind of request, at any path, respond with `It works!`.

- Build the server with the provided configuration

- Start the server (and wait for it to open the port).

- The server is bound to a random free port.

<a id="reference"></a>
## Reference

- [Helidon WebServer JavaDoc](/apidocs/io.helidon.webserver/module-summary.html)

- [Helidon WebServer Static Content JavaDoc](/apidocs/io.helidon.webserver.staticcontent/module-summary.html)
- [Helidon JSON-B Support JavaDoc](/apidocs/io.helidon.http.media.jsonp/module-summary.html)
- [Helidon JSON-P Support JavaDoc](/apidocs/io.helidon.http.media.jsonb/module-summary.html)
- [Helidon Jackson Support JavaDoc](/apidocs/io.helidon.http.media.jackson/module-summary.html)
- [Proxy Protocol Specification](https://www.haproxy.org/download/1.8/doc/proxy-protocol.txt)
