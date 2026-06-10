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

To enable MicroProfile Server add the helidon-microprofile-core bundle dependency to your project’s `pom.xml` (see [Managing Dependencies](../managing-dependencies.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile.bundles</groupId>
  <artifactId>helidon-microprofile-core</artifactId>
</dependency>
```

MicroProfile Server is already included in the bundle.

If full control over the dependencies is required, and you want to minimize the quantity of the dependencies - `Helidon MicroProfile Server` should be used. In this case the following dependencies should be included in your project’s `pom.xml`:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile.server</groupId>
  <artifactId>helidon-microprofile-server</artifactId>
</dependency>
```

## Usage

Helidon MicroProfile Server is used to collect and deploy JAX-RS application(s). When starting Helidon MP, it is recommended to use the `io.helidon.Main` main class, which will take care of starting Helidon. CDI will then discover all extensions, including the Server extension and start it.

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

<!--@include ../config/io.helidon.webserver.WebServer.md#configuration-options offset=1 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>restore-response-headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Copy and restore response headers before and after passing a request to Jersey for processing</td>
</tr>
<tr>
<td>
<a id="concurrency-limit"></a>
<a href="io.helidon.common.concurrency.limits.Limit.md">
<code>concurrency-limit</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Limit</code>
</td>
<td class="cm-default-cell">
</td>
<td>Concurrency limit to use to limit concurrent execution of incoming requests</td>
</tr>
<tr>
<td>
<a id="content-encoding"></a>
<a href="io.helidon.http.encoding.ContentEncodingContext.md">
<code>content-encoding</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ContentEncodingContext">ContentEncodingContext</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure the listener specific <code>io.helidon.http.encoding.ContentEncodingContext</code></td>
</tr>
<tr>
<td>
<a id="media-context"></a>
<a href="io.helidon.http.media.MediaContext.md">
<code>media-context</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="MediaContext">MediaContext</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure the listener specific <code>io.helidon.http.media.MediaContext</code></td>
</tr>
<tr>
<td>
<code>max-payload-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">-1</code>
</td>
<td>Maximal number of bytes an entity may have</td>
</tr>
<tr>
<td>
<a id="features"></a>
<a href="io.helidon.webserver.spi.ServerFeature.md">
<code>features</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ServerFeature&gt;">List&lt;ServerFeature&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Server features allow customization of the server, listeners, or routings</td>
</tr>
<tr>
<td>
<code>use-nio</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>If set to <code>true</code>, use NIO socket channel, instead of a socket</td>
</tr>
<tr>
<td>
<code>protocols-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>protocols</code></td>
</tr>
<tr>
<td>
<code>enable-proxy-protocol</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Enable proxy protocol support for this socket</td>
</tr>
<tr>
<td>
<code>host</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0.0.0.0</code>
</td>
<td>Host of the default socket</td>
</tr>
<tr>
<td>
<code>write-queue-length</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0</code>
</td>
<td>Number of buffers queued for write operations</td>
</tr>
<tr>
<td>
<a id="sockets"></a>
<a href="io.helidon.webserver.ListenerConfig.md">
<code>sockets</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, ListenerConfig&gt;">Map&lt;String, ListenerConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Socket configurations</td>
</tr>
<tr>
<td>
<a id="protocols"></a>
<a href="io.helidon.webserver.spi.ProtocolConfig.md">
<code>protocols</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ProtocolConfig&gt;">List&lt;ProtocolConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configuration of protocols</td>
</tr>
<tr>
<td>
<code>max-tcp-connections</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">-1</code>
</td>
<td>Limits the number of connections that can be opened at a single point in time</td>
</tr>
<tr>
<td>
<code>bind-address</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ListenerCustomMethods">ListenerCustomMethods</code>
</td>
<td class="cm-default-cell">
</td>
<td>The address to bind to</td>
</tr>
<tr>
<td>
<code>idle-connection-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT5M</code>
</td>
<td>How long should we wait before closing a connection that has no traffic on it</td>
</tr>
<tr>
<td>
<code>shutdown-grace-period</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT0.5S</code>
</td>
<td>Grace period in ISO 8601 duration format to allow running tasks to complete before listener's shutdown</td>
</tr>
<tr>
<td>
<code>max-concurrent-requests</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">-1</code>
</td>
<td>Limits the number of requests that can be executed at the same time (the number of active virtual threads of requests)</td>
</tr>
<tr>
<td>
<code>features-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>features</code></td>
</tr>
<tr>
<td>
<code>shutdown-hook</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>When true the webserver registers a shutdown hook with the JVM Runtime</td>
</tr>
<tr>
<td>
<a id="error-handling"></a>
<a href="io.helidon.webserver.ErrorHandling.md">
<code>error-handling</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ErrorHandling">ErrorHandling</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for this listener's error handling</td>
</tr>
<tr>
<td>
<code>concurrency-limit-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to enable automatic service discovery for <code>concurrency-limit</code></td>
</tr>
<tr>
<td>
<code>backlog</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1024</code>
</td>
<td>Accept backlog</td>
</tr>
<tr>
<td>
<code>max-in-memory-entity</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">131072</code>
</td>
<td>If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance when writing it</td>
</tr>
<tr>
<td>
<code>ignore-invalid-named-routing</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>If set to <code>true</code>, any named routing configured that does not have an associated named listener will NOT cause an exception to be thrown (default behavior is to throw an exception)</td>
</tr>
<tr>
<td>
<code>smart-async-writes</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>If enabled and <code>#writeQueueLength()</code> is greater than 1, then start with async writes but possibly switch to sync writes if async queue size is always below a certain threshold</td>
</tr>
<tr>
<td>
<a id="connection-options"></a>
<a href="io.helidon.common.socket.SocketOptions.md">
<code>connection-options</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SocketOptions">SocketOptions</code>
</td>
<td class="cm-default-cell">
</td>
<td>Options for connections accepted by this listener</td>
</tr>
<tr>
<td>
<code>port</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0</code>
</td>
<td>Port of the default socket</td>
</tr>
<tr>
<td>
<a id="requested-uri-discovery"></a>
<a href="io.helidon.http.RequestedUriDiscoveryContext.md">
<code>requested-uri-discovery</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="RequestedUriDiscoveryContext">RequestedUriDiscoveryContext</code>
</td>
<td class="cm-default-cell">
</td>
<td>Requested URI discovery context</td>
</tr>
<tr>
<td>
<code>idle-connection-period</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT2M</code>
</td>
<td>How often should we check for <code>#idleConnectionTimeout()</code></td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">@default</code>
</td>
<td>Name of this socket</td>
</tr>
<tr>
<td>
<a id="tls"></a>
<a href="io.helidon.common.tls.Tls.md">
<code>tls</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Tls</code>
</td>
<td class="cm-default-cell">
</td>
<td>Listener TLS configuration</td>
</tr>
<tr>
<td>
<code>write-buffer-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">4096</code>
</td>
<td>Initial buffer size in bytes of <code>java.io.BufferedOutputStream</code> created internally to write data to a socket connection</td>
</tr>
</tbody>
</table>


### Deprecated Options


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="connection-config"></a>
<a href="io.helidon.webserver.ConnectionConfig.md">
<code>connection-config</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ConnectionConfig">ConnectionConfig</code>
</td>
<td>Configuration of a connection (established from client against our server)</td>
</tr>
<tr>
<td>
<code>receive-buffer-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>Listener receive buffer size</td>
</tr>
</tbody>
</table>
<!--/include-->


## Examples

### Access Log

Access logging in Helidon is done by a dedicated module that can be added to Maven and configured.

To enable Access logging add the following dependency to project’s `pom.xml`:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile</groupId>
  <artifactId>helidon-microprofile-access-log</artifactId>
</dependency>
```

### Configuring Access Log in a configuration file

Access log can be configured as follows:

Access Log configuration file:

```properties
server.port=8080
server.host=0.0.0.0
server.features.access-log.format=helidon
```

## io.helidon.webserver.accesslog.AccessLogFeature

### Description

Configuration of access log feature.

### Usages

- [`server.features.access-log`][server-features]

### Configuration options

<!--@include ../config/io.helidon.webserver.accesslog.AccessLogFeature.md#configuration-options offset=1 -->
<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>format</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The format for log entries (similar to the Apache <code>LogFormat</code>)</td>
</tr>
<tr>
<td>
<code>logger-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="io.helidon.webserver.AccessLog">io.helidon.webserver.AccessLog</code>
</td>
<td>Name of the logger used to obtain access log logger from <code>System#getLogger(String)</code></td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1000.0</code>
</td>
<td>Weight of the access log feature</td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>List of sockets to register this feature on</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether this feature will be enabled</td>
</tr>
</tbody>
</table>
<!--/include-->


See the [manifest](../config/manifest.md) for all available types.

### Configuring TLS

Helidon MP also supports custom TLS configuration.

You can set the following properties:

- Server truststore
  - Keystore with trusted certificates
- Private key and certificate
  - Server certificate which will be used in TLS handshake

Server configuration:

```properties [microprofile-config.properties]
#Truststore setup
server.tls.trust.keystore.resource.resource-path=server.p12
server.tls.trust.keystore.passphrase=password
server.tls.trust.keystore.trust-store=true

#Keystore with private key and server certificate
server.tls.private-key.keystore.resource.resource-path=server.p12
server.tls.private-key.keystore.passphrase=password
```

Or the same configuration done in application.yaml file.

Server configuration:

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

Server configuration using `application.yaml`:

```yaml [application.yaml]
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

Server configuration using `META-INF/microprofile-config.properties`:

```properties [microprofile-config.properties]
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

@RoutingName example:

```java
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

```yaml
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

```yaml
io.helidon.examples.AdminService:
  routing-path:
    path: "/management"
```

### Serving Static Content

File system static content:

```properties [microprofile-config.properties]
# Location of content on file system
server.features.static-content.path.0.location=/var/www/html
# default is index.html (only in Helidon MicroProfile)
server.features.static-content.path.0.welcome=resource.html
# static content context on webserver - default is "/"
# server.features.static-content.path.0.context=/static-file
```

Classpath static content:

```properties [microprofile-config.properties]
# src/main/resources/WEB in your source tree
server.features.static-content.classpath.0.location=/WEB
# default is index.html
server.features.static-content.classpath.0.welcome=resource.html
# static content path - default is "/"
# server.features.static-content.classpath.0.context=/static-cp
```

It is usually easier to configure list-based options using `application.yaml` instead, such as:

Static content:

```yaml [application.yaml]
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

See [Static Content Feature Configuration Reference][static-content-f] for details. The only difference is that we set welcome file to `index.html` by default.

### Re-direct root using `server.base-path`

To redirect requests for the root path (`/`) to another path you can use the `server.base-path` property:

```yaml
server:
  base-path: /static/index.html
```

For any HTTP request for `/` this will return a 301 with the `Location:` header set to the value of `server.base-path`. This is often used with Static Content Support to serve a specific `index.html` when `/` is requested.

Note that this feature is not for setting a context root for applications. To configure alternate context roots see [Setting Application Path][setting-applicat].

### Example configuration of routing

A full configuration example (YAML):

```yaml
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

Proxies and reverse proxies between an HTTP client and your Helidon application mask important information (for example `Host` header, originating IP address, protocol) about the request the client sent. Fortunately, many of these intermediary network nodes set or update either the [standard HTTP `Forwarded` header][standard-http-fo] or the [non-standard `X-Forwarded-*` family of headers][non-standard-x-f] to preserve information about the original client request.

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

Configuring Request URI Discovery (properties format):

```properties
server.port=8080
server.requested-uri-discovery.types=FORWARDED,X_FORWARDED
server.requested-uri-discovery.trusted-proxies.allow.pattern=lb.*\\.mycorp\\.com
server.requested-uri-discovery.trusted-proxies.deny.exact=lbtest.mycorp.com
```

This example might apply if `mycorp.com` had trusted load balancers named `lbxxx.mycorp.com` except for an untrusted test load balancer `lbtest.mycorp.com`.

#### Obtaining the Requested URI Information

Helidon makes the requested URI information available as a property in the request context:

Retrieving Requested URI Information:

```java
public class MyFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        UriInfo uriInfo = (UriInfo) requestContext.getProperty("io.helidon.jaxrs.requested-uri");
        // ...
    }
}
```

See the [`UriInfo`][uriinfo] Javadoc for more information.

> [!NOTE]
> The `requestContext.getUriInfo()` method returns the Jakarta RESTful web services `UriInfo` object, *not* the Helidon-provided requested URI information `UriInfo` record.

## Reference

- [Helidon MicroProfile Server Javadoc][helidon-micropro]
- [Helidon MicroProfile Server on GitHub][helidon-micropro-2]

[server-features]: ../config/io.helidon.webserver.spi.ServerFeature.md#a42c97-access-log
[static-content-f]: ../config/io.helidon.webserver.staticcontent.StaticContentFeature.md
[setting-applicat]: jaxrs/jaxrs-applications.md#setting-application-path
[standard-http-fo]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Forwarded
[non-standard-x-f]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For
[uriinfo]: https://helidon.io/docs/v4/apidocs/io.helidon.common.uri/io/helidon/common/uri/UriInfo.html
[helidon-micropro]: https://helidon.io/docs/v4/apidocs/io.helidon.microprofile.server/module-summary.html
[helidon-micropro-2]: https://github.com/helidon-io/helidon/tree/main/microprofile/server
