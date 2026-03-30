# MicroProfile Server

## Content

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Usage](#usage)
- [API](#api)
- [Configuration](#configuration)
- [Examples](#examples)
- [Reference](#reference)

## Overview

Helidon provides a MicroProfile server implementation (`io.helidon.microprofile.server.Server`) that encapsulates the Helidon WebServer.

## Maven-Coordinates

To enable MicroProfile Server add the helidon-microprofile-core bundle dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.microprofile.bundles</groupId>
    <artifactId>helidon-microprofile-core</artifactId>
</dependency>
```

MicroProfile Server is already included in the bundle.

If full control over the dependencies is required, and you want to minimize the quantity of the dependencies - `Helidon MicroProfile Server` should be used. In this case the following dependencies should be included in your project’s `pom.xml`:

``` xml
<dependency>
    <groupId>io.helidon.microprofile.server</groupId>
    <artifactId>helidon-microprofile-server</artifactId>
</dependency>
```

## Usage

Helidon Microprofile Server is used to collect and deploy JAX-RS application(s). When starting Helidon MP, it is recommended to use the `io.helidon.Main` main class, which will take care of starting Helidon. CDI will then discover all extensions, including the Server extension and start it.

See the [Helidon MP Quickstart example](guides/quickstart.md). Note that the server lifecycle is bound to CDI.

Usage of the `io.helidon.microprofile.server.Server` API is discouraged, as Helidon MP uses convention to discover and configure features, which makes the applications easier to understand and maintain.

## API

The following table provides a brief description of routing annotations, including its parameters. More information in `Configuring a WebServer route` section.

<table>
<colgroup>
<col style="width: 37%" />
<col style="width: 62%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Annotation</th>
<th style="text-align: left;">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><pre><code>@RoutingName(
    value = &quot;&quot;
    required = false
)</code></pre></td>
<td style="text-align: left;"><p>Binds a JAX-RS Application or Helidon Service to a specific (named) routing on <code>WebServer</code>.The routing should have a corresponding named socket configured on the WebServer to run the routing on.</p></td>
</tr>
<tr>
<td style="text-align: left;"><pre><code>@RoutingPath(&quot;/path&quot;)</code></pre></td>
<td style="text-align: left;"><p>Path of a Helidon Service to register with routing.</p></td>
</tr>
</tbody>
</table>

## Configuration

By default, the server uses the MicroProfile Config, but you may also want to use [Helidon configuration](config/introduction.md).

In this example, the configuration is in a file, and it includes Helidon configuration options.

Configuration reference:

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="acb486-backlog"></span> `backlog` | `VALUE` | `Integer` | `1024` | Accept backlog |
| <span id="a4fc52-bind-address"></span> `bind-address` | `VALUE` | `i.h.w.W.ListenerCustomMethods` |   | The address to bind to |
| <span id="a32e67-concurrency-limit"></span> [`concurrency-limit`](../config/io_helidon_common_concurrency_limits_Limit.md) | `VALUE` | `i.h.c.c.l.Limit` |   | Concurrency limit to use to limit concurrent execution of incoming requests |
| <span id="a3f7e3-concurrency-limit-discover-services"></span> `concurrency-limit-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `concurrency-limit` |
| <span id="ac9c91-connection-options"></span> [`connection-options`](../config/io_helidon_common_socket_SocketOptions.md) | `VALUE` | `i.h.c.s.SocketOptions` |   | Options for connections accepted by this listener |
| <span id="a511a0-content-encoding"></span> [`content-encoding`](../config/io_helidon_http_encoding_ContentEncodingContext.md) | `VALUE` | `i.h.h.e.ContentEncodingContext` |   | Configure the listener specific `io.helidon.http.encoding.ContentEncodingContext` |
| <span id="aa0fb8-enable-proxy-protocol"></span> `enable-proxy-protocol` | `VALUE` | `Boolean` | `false` | Enable proxy protocol support for this socket |
| <span id="a92b62-error-handling"></span> [`error-handling`](../config/io_helidon_webserver_ErrorHandling.md) | `VALUE` | `i.h.w.ErrorHandling` |   | Configuration for this listener's error handling |
| <span id="ae9df6-features"></span> [`features`](../config/io_helidon_webserver_spi_ServerFeature.md) | `LIST` | `i.h.w.s.ServerFeature` |   | Server features allow customization of the server, listeners, or routings |
| <span id="a4431f-features-discover-services"></span> `features-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `features` |
| <span id="a47500-host"></span> `host` | `VALUE` | `String` | `0.0.0.0` | Host of the default socket |
| <span id="a570c4-idle-connection-period"></span> `idle-connection-period` | `VALUE` | `Duration` | `PT2M` | How often should we check for `#idleConnectionTimeout()` |
| <span id="abfcba-idle-connection-timeout"></span> `idle-connection-timeout` | `VALUE` | `Duration` | `PT5M` | How long should we wait before closing a connection that has no traffic on it |
| <span id="acfc0a-ignore-invalid-named-routing"></span> `ignore-invalid-named-routing` | `VALUE` | `Boolean` |   | If set to `true`, any named routing configured that does not have an associated named listener will NOT cause an exception to be thrown (default behavior is to throw an exception) |
| <span id="a71146-max-concurrent-requests"></span> `max-concurrent-requests` | `VALUE` | `Integer` | `-1` | Limits the number of requests that can be executed at the same time (the number of active virtual threads of requests) |
| <span id="a23186-max-in-memory-entity"></span> `max-in-memory-entity` | `VALUE` | `Integer` | `131072` | If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance when writing it |
| <span id="a6e9f1-max-payload-size"></span> `max-payload-size` | `VALUE` | `Long` | `-1` | Maximal number of bytes an entity may have |
| <span id="ac255e-max-tcp-connections"></span> `max-tcp-connections` | `VALUE` | `Integer` | `-1` | Limits the number of connections that can be opened at a single point in time |
| <span id="a847a9-media-context"></span> [`media-context`](../config/io_helidon_http_media_MediaContext.md) | `VALUE` | `i.h.h.m.MediaContext` |   | Configure the listener specific `io.helidon.http.media.MediaContext` |
| <span id="a390dc-name"></span> `name` | `VALUE` | `String` | `@default` | Name of this socket |
| <span id="a9d956-port"></span> `port` | `VALUE` | `Integer` | `0` | Port of the default socket |
| <span id="abdf05-protocols"></span> [`protocols`](../config/io_helidon_webserver_spi_ProtocolConfig.md) | `LIST` | `i.h.w.s.ProtocolConfig` |   | Configuration of protocols |
| <span id="a4b6cc-protocols-discover-services"></span> `protocols-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `protocols` |
| <span id="aaf9ce-requested-uri-discovery"></span> [`requested-uri-discovery`](../config/io_helidon_http_RequestedUriDiscoveryContext.md) | `VALUE` | `i.h.h.RequestedUriDiscoveryContext` |   | Requested URI discovery context |
| <span id="aa99af-restore-response-headers"></span> `restore-response-headers` | `VALUE` | `Boolean` | `true` | Copy and restore response headers before and after passing a request to Jersey for processing |
| <span id="a875ae-shutdown-grace-period"></span> `shutdown-grace-period` | `VALUE` | `Duration` | `PT0.5S` | Grace period in ISO 8601 duration format to allow running tasks to complete before listener's shutdown |
| <span id="aa36d3-shutdown-hook"></span> `shutdown-hook` | `VALUE` | `Boolean` | `true` | When true the webserver registers a shutdown hook with the JVM Runtime |
| <span id="a3378e-smart-async-writes"></span> `smart-async-writes` | `VALUE` | `Boolean` | `false` | If enabled and `#writeQueueLength()` is greater than 1, then start with async writes but possibly switch to sync writes if async queue size is always below a certain threshold |
| <span id="a03604-sockets"></span> [`sockets`](../config/io_helidon_webserver_ListenerConfig.md) | `MAP` | `i.h.w.ListenerConfig` |   | Socket configurations |
| <span id="ac9efa-tls"></span> [`tls`](../config/io_helidon_common_tls_Tls.md) | `VALUE` | `i.h.c.t.Tls` |   | Listener TLS configuration |
| <span id="a5f9ab-use-nio"></span> `use-nio` | `VALUE` | `Boolean` | `true` | If set to `true`, use NIO socket channel, instead of a socket |
| <span id="a57ab6-write-buffer-size"></span> `write-buffer-size` | `VALUE` | `Integer` | `4096` | Initial buffer size in bytes of `java.io.BufferedOutputStream` created internally to write data to a socket connection |
| <span id="adda19-write-queue-length"></span> `write-queue-length` | `VALUE` | `Integer` | `0` | Number of buffers queued for write operations |

#### Deprecated Options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a20877-connection-config"></span> [`connection-config`](../config/io_helidon_webserver_ConnectionConfig.md) | `VALUE` | `i.h.w.ConnectionConfig` | Configuration of a connection (established from client against our server) |
| <span id="ab275b-receive-buffer-size"></span> `receive-buffer-size` | `VALUE` | `Integer` | Listener receive buffer size |

## Examples

### Access Log

Access logging in Helidon is done by a dedicated module that can be added to Maven and configured.

To enable Access logging add the following dependency to project’s `pom.xml`:

``` xml
<dependency>
    <groupId>io.helidon.microprofile</groupId>
    <artifactId>helidon-microprofile-access-log</artifactId>
</dependency>
```

### Configuring Access Log in a configuration file

Access log can be configured as follows:

*Access Log configuration file*

``` properties
server.port=8080
server.host=0.0.0.0
server.features.access-log.format=helidon
```

## io.helidon.webserver.accesslog.AccessLogFeature

### Description

Configuration of access log feature.

### Usages

- [`server.features.access-log`](../config/io_helidon_webserver_spi_ServerFeature.md#a42c97-access-log)

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aaefb9-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this feature will be enabled |
| <span id="a8717c-format"></span> `format` | `VALUE` | `String` |   | The format for log entries (similar to the Apache `LogFormat`) |
| <span id="aeb9ad-logger-name"></span> `logger-name` | `VALUE` | `String` | `io.helidon.webserver.AccessLog` | Name of the logger used to obtain access log logger from `System#getLogger(String)` |
| <span id="a631a5-sockets"></span> `sockets` | `LIST` | `String` |   | List of sockets to register this feature on |
| <span id="ac3d7a-weight"></span> `weight` | `VALUE` | `Double` | `1000.0` | Weight of the access log feature |

See the [manifest](../config/manifest.md) for all available types.

### Configuring TLS

Helidon MP also supports custom TLS configuration.

You can set the following properties:

- Server truststore
  - Keystore with trusted certificates
- Private key and certificate
  - Server certificate which will be used in TLS handshake

*META-INF/microprofile-config.properties - Server configuration*

``` properties
#Truststore setup
server.tls.trust.keystore.resource.resource-path=server.p12
server.tls.trust.keystore.passphrase=password
server.tls.trust.keystore.trust-store=true

#Keystore with private key and server certificate
server.tls.private-key.keystore.resource.resource-path=server.p12
server.tls.private-key.keystore.passphrase=password
```

Or the same configuration done in application.yaml file.

*application.yaml - Server configuration*

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
    #Keystore with private key and server certificate
    private-key:
      keystore:
        passphrase: "password"
        resource:
          # load from file system
          path: "/path/to/keystore.p12" 
```

- File loaded from the classpath.
- File loaded from the file system.

### Configuring additional ports

Helidon MP can expose multiple ports, with the following limitations:

- The default port is the port that serves your application (JAX-RS applications and resources)
- Other ports (in this example we configure one "admin" port) can be assigned endpoints that are exposed by Helidon components, currently supported by MP Health and MP Metrics

You can set the configuration in either `application.yaml` or `META-INF/microprofile-config.properties`:

- The port `7011` is the default port and will serve your application
- The port `8011` is named "admin" (this is an arbitrary name)
- Observability endpoints, such as metrics and health, use the "admin" port through the `features.observe.sockets` setting.

*Server configuration using `application.yaml`*

``` yaml
server:
  port: 7011
  host: "localhost"
  sockets:
    admin:
      port: 8011
      bind-address: "localhost"
  features:
    observe:
      sockets: "admin"
```

*Server configuration using `META-INF/microprofile-config.properties`*

``` properties
server.port=7011
server.host=localhost
server.sockets.0.name=admin
server.sockets.0.port=8011
server.sockets.0.bind-address=localhost
server.features.observe.sockets=admin
```

### Configuring A WebServer Route

Helidon MP Server will pick up CDI beans that implement the `io.helidon.webserver.HttpService` interface and configure them with the underlying WebServer.

This allows configuration of WebServer routes to run alongside a JAX-RS application.

The bean is expected to be either `ApplicationScoped` or `Dependent` and will be requested only once during the boot of the `Server`.

The bean will support injection of `ApplicationScoped` and `Dependent` scoped beans. You cannot inject `RequestScoped` beans. Please use WebServer features to handle request related objects.

#### Customizing the HTTP service

The service can be customized using annotations and/or configuration to be

- registered on a specific path
- registered with a named routing

#### Assigning an HTTP service to named ports

Helidon has the concept of named routing. These correspond to the named ports configured with WebServer.

You can assign an HTTP service to a named routing (and as a result to a named port) using either an annotation or configuration (or both to override the value from annotation).

##### Annotation `@RoutingName`

You can annotate a service bean with this annotation to assign it to a specific named routing, that is (most likely) going to be bound to a specific port.

The annotation has two attributes: - `value` that defines the routing name - `required` to mark that the routing name MUST be configured in Helidon server

*`@RoutingName` example*

``` java
@ApplicationScoped
@RoutingName(value = "admin", required = true)
@RoutingPath("/admin")
public class AdminService implements HttpService {
    @Override
    public void routing(HttpRules rules) {
        // ...
    }
}
```

The example above will be bound to `admin` routing (and port) and will fail if such a port is not configured.

##### Configuration override of routing name

For each service bean you can define the routing name and its required flag by specifying a configuration option `bean-class-name.routing-name.name` and `bean-class-name.routing-name.required`. For service beans produced with producer method replace `bean-class-name` with `class-name.producer-method-name`.

Example (YAML) configuration for a service bean `io.helidon.examples.AdminService` that changes the routing name to `management` and its required flag to `false`:

``` yaml
io.helidon.examples.AdminService:
  routing-name:
    name: "management"
    required: false
```

#### Configuring an HTTP service path

Each service is registered on a path. If none is configured, then the service would be configured on the root path.

You can configure service path using an annotation or configuration (or both to override value from annotation)

##### Annotation `@RoutingPath`

You can configure `@RoutingPath` to define the path a service is registered on.

##### Configuration override of routing path

For each HTTP service class you can define the routing path by specifying a configuration option `class-name.routing-path.path`. The `routing-path` configuration can be applied to Jax-RS application. See [Jakarta REST Application](jaxrs/jaxrs-applications.md) for more information.

Example (YAML) configuration for a class `io.helidon.example.AdminService` that changes the routing path to `/management`:

``` yaml
io.helidon.examples.AdminService:
  routing-path:
    path: "/management"
```

### Serving Static Content

*META-INF/microprofile-config.properties - File system static content*

``` properties
# Location of content on file system
server.features.static-content.path.0.location=/var/www/html
# default is index.html (only in Helidon MicroProfile)
server.features.static-content.path.0.welcome=resource.html
# static content context on webserver - default is "/"
# server.features.static-content.path.0.context=/static-file
```

*META-INF/microprofile-config.properties - Classpath static content*

``` properties
# src/main/resources/WEB in your source tree
server.features.static-content.classpath.0.location=/WEB
# default is index.html
server.features.static-content.classpath.0.welcome=resource.html
# static content path - default is "/"
# server.features.static-content.classpath.0.context=/static-cp
```

It is usually easier to configure list-based options using `application.yaml` instead, such as:

*application.yaml - Static content*

``` yaml
server:
  features:
    static-content:
      welcome: "welcome.html"
      classpath:
        - context: "/static"
          location: "/WEB"
      path:
        - context: "/static-file"
          location: "./static-content"
```

See [Static Content Feature Configuration Reference](../config/io_helidon_webserver_staticcontent_StaticContentFeature.md) for details. The only difference is that we set welcome file to `index.html` by default.

### Re-direct root using `server.base-path`

To redirect requests for the root path (`/`) to another path you can use the `server.base-path` property:

``` yaml
server:
  base-path: /static/index.html
```

For any HTTP request for `/` this will return a 301 with the `Location:` header set to the value of `server.base-path`. This is often used with Static Content Support to serve a specific `index.html` when `/` is requested.

Note that this feature is not for setting a context root for applications. To configure alternate context roots see see [Setting Application Path](jaxrs/jaxrs-applications.md#_setting_application_path).

### Example configuration of routing

A full configuration example (YAML):

``` yaml
server:
  port: 8080
  sockets:
   management:
   port: 8090

io.helidon.examples.AdminApplication:
  routing-name:
    name: "management"
    required: true
  routing-path:
    path: "/management"
```

### Using Requested URI Discovery

Proxies and reverse proxies between an HTTP client and your Helidon application mask important information (for example `Host` header, originating IP address, protocol) about the request the client sent. Fortunately, many of these intermediary network nodes set or update either the [standard HTTP `Forwarded` header](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Forwarded) or the [non-standard `X-Forwarded-*` family of headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For) to preserve information about the original client request.

Helidon’s requested URI discovery feature allows your application—​and Helidon itself—​to reconstruct information about the original request using the `Forwarded` header and the `X-Forwarded-*` family of headers.

When you prepare the connections in your server you can include the following optional requested URI discovery settings:

- enabled or disabled
- which type or types of requested URI discovery to use:
  - `FORWARDED` - uses the `Forwarded` header
  - `X_FORWARDED` - uses the `X-Forwarded-*` headers
  - `HOST` - uses the `Host` header
- what intermediate nodes to trust

When your application receives a request Helidon iterates through the discovery types you set up for the receiving connection, gathering information from the corresponding header(s) for that type. If the request does not have the corresponding header(s), or your settings do not trust the intermediate nodes reflected in those headers, then Helidon tries the next discovery type you set up. Helidon uses the `HOST` discovery type if you do not set up discovery yourself or if, for a particular request, it cannot assemble the request information using any discovery type you did set up for the socket.

#### Setting Up Requested URI Discovery

You can use configuration to set up the requested URI discovery behavior.

*Configuring Request URI Discovery (properties format)*

``` properties
server.port=8080
server.requested-uri-discovery.types=FORWARDED,X_FORWARDED
server.requested-uri-discovery.trusted-proxies.allow.pattern=lb.*\\.mycorp\\.com
server.requested-uri-discovery.trusted-proxies.deny.exact=lbtest.mycorp.com
```

This example might apply if `mycorp.com` had trusted load balancers named `lbxxx.mycorp.com` except for an untrusted test load balancer `lbtest.mycorp.com`.

#### Obtaining the Requested URI Information

Helidon makes the requested URI information available as a property in the request context:

*Retrieving Requested URI Information*

``` java
public class MyFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        UriInfo uriInfo = (UriInfo) requestContext.getProperty("io.helidon.jaxrs.requested-uri");
        // ...
    }
}
```

See the [`UriInfo`](/apidocs/io.helidon.common.uri/io/helidon/common/uri/UriInfo.html) JavaDoc for more information.

> [!NOTE]
> The `requestContext.getUriInfo()` method returns the Jakarta RESTful web services `UriInfo` object, *not* the Helidon-provided requested URI information `UriInfo` record.

## Reference

- [Helidon MicroProfile Server Javadoc](/apidocs/io.helidon.microprofile.server/module-summary.html)
- [Helidon MicroProfile Server on GitHub](https://github.com/oracle/helidon/tree/main/microprofile/server)
