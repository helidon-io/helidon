<!--@frontmatter
description: "Helidon WebClient"
navigation:
  icon: i-lucide-globe
-->
# WebClient

## Overview

WebClient is an HTTP client for Helidon. It can be used to send requests and
retrieve corresponding responses in a programmatic way.

Helidon WebClient provides the following features:

- **Blocking approach** The WebClient uses the blocking approach to
  synchronously process a request and its corresponding response. Both
  `HTTP/1.1` and `HTTP/2` request and response will run in the thread of the
  user. Additionally, for `HTTP/2`, virtual thread is employed to manage the
  connection.

- **Builder-like setup and execution** Creates every client and request as a
  builder pattern. This improves readability and code maintenance.

- **Redirect chain** Follows the redirect chain and perform requests on the
  correct endpoint by itself.

- **Tracing and security propagation** Automatically propagates the configured
  tracing and security settings of the Helidon WebServer to the WebClient and
  uses them during request and response.

## Maven Coordinates

To enable WebClient, add the following dependency to your project’s `pom.xml`
(see [Managing Dependencies](../dependency-management.md)).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient</groupId>
  <artifactId>helidon-webclient</artifactId>
</dependency>
```

The `helidon-webclient` dependency has built-in support for `HTTP/1.1`.

If support for `HTTP/2` is a requirement, below dependency needs to be added:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient</groupId>
  <artifactId>helidon-webclient-http2</artifactId>
</dependency>
```

## Usage

### Instantiating the WebClient

You can create an instance of a WebClient by executing `WebClient.create()`
which will have default settings and without a base uri set.

To change the default settings and register additional services, you can use
simple builder that allows you to customize the client behavior.

Create a WebClient with simple builder:

```java
WebClient client = WebClient.builder()
    .baseUri("http://localhost")
    .build();
```

### Alt-Svc Discovery

WebClient uses alternative services only when the optional common
`ClientAltSvcConfig` (`alt-svc`) configuration is present. Within a present
configuration, `enabled` defaults to `true`; setting it to `false` provides an
explicit override without removing the configuration. An empty `protocols`
list allows every available client protocol provider that supports Alt-Svc.
Otherwise, the list is an exact, case-sensitive ALPN protocol filter. The
HTTP/2 protocol ID is `h2`.

Initial client support accepts advertisements only from `https` origins and
only for alternatives on the same host; the alternative port may differ. Using
an alternative changes the connection endpoint, not the request scheme or
authority. WebClient does not support `h2c` alternatives. It also does not
upgrade a plain `http` origin to TLS-based HTTP/2 because the RFC 8164
`/.well-known/http-opportunistic` opt-in is not implemented.

Alternative-service routing never bypasses the configured proxy policy. The
current HTTP/2 provider uses alternatives only when proxying is disabled. When
any proxy policy is configured, including a `no-proxy` exception that selected
a direct route for the origin, WebClient ignores the advertisement and
continues with the selected route.

WebClient honors the configured TLS policy as-is when connecting to an
alternative. This includes custom TLS managers, custom SSL contexts, disabled
endpoint identification, and `trust-all`. An unsafe or permissive TLS
configuration therefore makes Alt-Svc steering equally unsafe or permissive.
Configure TLS trust and endpoint identification according to the security
requirements of the application.

The HTTP/2 provider accounts for `Age` and apparent age derived from `Date`,
honors `ma`, `clear`, and `persist`, and falls back to the origin if an
advertised endpoint cannot be established. Advertisement freshness controls
creation of new connections; an already-opening or reusable connection can
continue after the advertisement expires. Discovery state belongs to the
HTTP/2 connection cache. Shared connection caches share that state; disabling
connection-cache sharing isolates it. `persist` does not make discovery state
durable across a process or beyond that cache lifecycle.

### Creating the Request

WebClient offers a set of request methods that are used to specify the type of
action to be performed on a given resource. Below are some examples of request
methods:

- `get()`
- `post()`
- `put()`
- `method(Method method)`

Check out [HttpClient][httpclient] API to learn more about request methods.
These methods will create a new instance of
[HttpClientRequest][httpclientreques] which can then be configured to add
optional settings that will customize the behavior of the request.

### Customizing the Request

Configuration can be set for every request type before it is sent.

Customizing a request:

<!--@mdc ::code-callout -->
```java
client.get()
        .uri("http://example.com") // <1>
        .path("/path") // <2>
        .queryParam("query", "parameter") // <3>
        .fragment("someFragment") // <4>
        .headers(headers -> headers.accept(MediaTypes.APPLICATION_JSON)); // <5>
```
1. Overrides `baseUri` from WebClient
2. Adds path to the uri
3. Adds query parameter to the request
4. Adds fragment to the request
5. Adds header to the request
<!--@mdc :: -->

For more information about these optional parameters, check out
[ClientRequestBase][clientrequestbas] API, which is a parent class of
[HttpClientRequest][httpclientreques].

[HttpClientRequest][httpclientreques] class also provides specific header
methods that help the user to set a particular header. Some examples of these
are:

- `contentType` (MediaType contentType)
- `accept` (MediaType... mediaTypes)

For more information about these methods, check out
[ClientRequest][clientrequest] API, which is a parent class of
[HttpClientRequest][httpclientreques].

### Sending the Request

Once the request setup is completed, the following methods can be used to send
it:

- `HttpClientResponse request()`
- `<E> ClientResponseTyped<E> request(Class<E> type)`
- `<E> E requestEntity(Class<E> type)`
- `HttpClientResponse submit(Object entity)`
- `<T> ClientResponseTyped<T> submit(Object entity, Class<T> requestedType)`
- `HttpClientResponse outputStream(OutputStreamHandler outputStreamConsumer)`
- `<T> ClientResponseTyped<T> outputStream(OutputStreamHandler
  outputStreamConsumer, Class<T> requestedType)`

Each of the methods will provide a way to allow response to be retrieved in a
particular response type. Refer to [ClientRequest API][clientrequest] for more
details about these methods.

Execute a simple GET request to endpoint and receive a String response:

```java
ClientResponseTyped<String> response = client.get()
    .path("/endpoint")
    .request(String.class);
String entityString = response.entity();
```

### Protocol Used

WebClient currently supports `HTTP/1.1` and `HTTP/2` protocols. Below are the
rules on which specific protocol will be used:

- Using plain socket triggers WebClient to process a request using `HTTP/1.1`.
- When using TLS, the client will use ALPN (protocol negotiation) to use
  appropriate HTTP version (either 1.1, or 2). `HTTP/2` has a higher weight, so
  it is chosen if supported by both sides.
- A specific protocol can be explicitly selected by calling
  `HttpClientRequest#protocolId(String)`.
  ```java
  String result = client.get()
      .protocolId("http/1.1")
      .requestEntity(String.class);
  ```
- If `HTTP/2` is used, an upgrade attempt will be performed. If it fails, the
  client falls-back to `HTTP/1.1`.
- The parameter `prior-knowledge` can be defined using `HTTP/2` protocol
  configuration. Please refer to [Setting Protocol
  configuration](#setting-protocol-configuration) on how to customize `HTTP/2`.
  In such a case, `prior-knowledge` will be used and fail if it is unable to
  switch to `HTTP/2`.
- When the common `alt-svc` configuration is present, enabled, and allows the
  exact `h2` protocol ID, an `https` origin can advertise a same-host TLS HTTP/2
  alternative. A later generic WebClient request can use that alternative
  while retaining the original request authority. WebClient adds `Alt-Used`
  only to the request sent to the alternative. See [Alt-Svc
  Discovery](#alt-svc-discovery) for current limitations and TLS, proxy, and
  lifecycle behavior.

### Adding Media Support

WebClient supports the following built-in Helidon Media Support libraries:

1.  JSON Processing (JSON-P)
2.  JSON Binding (JSON-B)
3.  Jackson

They can be activated by adding their corresponding libraries into the
classpath. This can simply be done by adding their corresponding dependencies.

Add JSON-P support:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.http.media</groupId>
  <artifactId>helidon-http-media-jsonp</artifactId>
</dependency>
```

Add JSON-B support:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.http.media</groupId>
  <artifactId>helidon-http-media-jsonb</artifactId>
</dependency>
```

Add Jackson support:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.http.media</groupId>
  <artifactId>helidon-http-media-jackson</artifactId>
</dependency>
```

Users can also create their own Custom Media Support library and make them work
by following either of the approaches:

- Create a Provider of the Custom Media Support and expose it via Service Loader
  followed by adding the Media Support library to the classpath.
- Explicitly register the Custom Media Support from WebClient.

<!--@mdc ::code-callout -->
```java
WebClient.builder()
        .mediaContext(it -> it
                .addMediaSupport(CustomMediaSupport.create())) // <1>
        .build();
```
1. Register CustomMedia support from the WebClient.
<!--@mdc :: -->

### DNS Resolving

WebClient provides three DNS resolver implementations out of the box:

- `Java DNS resolution` is the default.
- `First DNS resolution` uses the first IP address from a DNS lookup. To enable
  this option, add below dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient.dns.resolver</groupId>
  <artifactId>helidon-webclient-dns-resolver-first</artifactId>
</dependency>
```

- `Round-Robin DNS resolution` cycles through IP addresses from a DNS lookup. To
  enable this option, add this dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient.dns.resolver</groupId>
  <artifactId>helidon-webclient-dns-resolver-round-robin</artifactId>
</dependency>
```

## Configuration options

<!--@include ../config/io.helidon.webclient.api.WebClient.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-webcl].
<!--/include-->

## Protocol Configuration

Protocol specific configuration can be set using the `protocol-configs`
parameter. WebClient currently supports `HTTP/1.1.` and `HTTP/2`.

### HTTP1 Configuration options

<!--@include ../config/io.helidon.webclient.http1.Http1ClientProtocolConfig.md#configuration-options delim=--- offset=2 collapseTables=10 -->
See [Configuration options][io-helidon-webcl-2].
<!--/include-->

### HTTP2 Configuration options

See [HTTP/2 configuration options][io-helidon-webcl-3].


### Example of a WebClient Runtime Configuration

```java
Config config = Config.create();
WebClient client = WebClient.builder()
    .baseUri("http://localhost")
    .config(config.get("client"))
    .build();
```

## Configuration Example

<!--@mdc ::code-callout{collapsed} -->
```yaml [application.yaml]
client:
  connect-timeout-millis: 2000
  read-timeout-millis: 2000
  follow-redirects: true # <1>
  max-redirects: 5
  cookie-manager: # <2>
    automatic-store-enabled: true
    default-cookies:
      flavor3: strawberry
      flavor4: raspberry
  default-headers: # <3>
    Accept: '"application/json", "text/plain"'
  services: # <4>
    metrics:
      - methods: ["PUT", "POST", "DELETE"]
        type: METER
        name-format: "client.meter.overall"
      - type: TIMER
        # meter per method
        name-format: "client.meter.%1$s"
      - methods: ["GET"]
        type: COUNTER
        errors: false
        name-format: "client.counter.%1$s.success"
        description: "Counter of successful GET requests"
      - methods: ["PUT", "POST", "DELETE"]
        type: COUNTER
        success: false
        name-format: "wc.counter.%1$s.error"
        description: "Counter of failed PUT, POST and DELETE requests"
    tracing:
  alt-svc: # <5>
    enabled: true
    protocols: ["h2"]
  protocol-configs: # <6>
    http_1_1:
      max-header-size: 20000
      validate-request-headers: true
    h2:
      prior-knowledge: true
  proxy: # <7>
    host: "hostName"
    port: 80
    no-proxy: ["localhost:8080", ".helidon.io", "192.168.1.1"]
  tls: # <8>
    trust:
      keystore:
        passphrase: "password"
        trust-store: true
        resource:
          resource-path: "client.p12"
```
1. Client functional settings
2. Cookie management
3. Default client headers
4. Client service configuration
5. Opt-in alternative service configuration
6. Protocol configuration
7. Proxy configuration
8. TLS configuration
<!--@mdc :: -->

## Examples

### WebClient with Proxy

Proxy can be set directly from WebClient builder.

```java
WebClient.builder()
    .proxy(Proxy.builder()
      .type(Proxy.ProxyType.HTTP)
      .host(PROXY_HOST)
      .port(PROXY_PORT)
      .build())
    .build();
```

Alternative is to set proxy directly from the request via `HttpClientRequest`.

<!--@mdc ::code-callout -->
```java
Proxy proxy = Proxy.create(); // <1>
var response = client.get("/proxiedresource")
    .proxy(Proxy.create()) // <2>
    .request();
```
1. Proxy instance configured using system settings (environment variables and
system properties)
2. Configure the proxy per client request
<!--@mdc :: -->

Proxy can also be configured in WebClient through the `application.yaml`
configuration file.

```yaml [application.yaml]
client:
  proxy:
    host: "hostName"
    port: 80
    no-proxy: ["localhost:8080", ".helidon.io", "192.168.1.1"]
```

Then, in your application code, load the configuration from that file.

WebClient initialization using the `application.yaml` file located on the
classpath:

```java [application.yaml]
var config = Config.create();
WebClient.builder()
    .config(config.get("client"))
    .build();
```

- `application.yaml` is a default configuration source loaded when YAML support
  is on classpath, so we can just use `Config.create()`
- Passing the client configuration node

### WebClient TLS Setup

Configure TLS either programmatically or by the Helidon configuration framework.

#### Configuring TLS in your code

One way to configure TLS in WebClient is in your application code as shown
below.

```java
WebClient.builder()
    .tls(it -> it.trust(t -> t
        .keystore(k -> k.passphrase("password")
            .trustStore(true)
            .keystore(r -> r.resourcePath("client.p12")))))
    .build();
```

#### Configuring TLS in the config file

Another way to configure TLS in WebClient is through the `application.yaml`
configuration file.

WebClient TLS configuration in `application.yaml`:

```yaml [application.yaml]
client:
  tls:
    trust:
      keystore:
        passphrase: "password"
        trust-store: true
        resource:
          resource-path: "client.p12"
```

In the application code, load the settings from the configuration file.

WebClient initialization using the `application.yaml` file located on the
classpath:

<!--@mdc ::code-callout -->
```java [application.yaml]
Config config = Config.create(); // <1>
WebClient.builder()
        .config(config.get("client")) // <2>
        .build();
```
1. `application.yaml` is a default configuration source loaded when YAML support
   is on classpath, so we can just use `Config.create()`
2. Passing the client configuration node
<!--@mdc :: -->

#### Configuring TLS SNI

When `sni` is not configured, WebClient preserves the configured TLS
`SSLParameters` server names and uses the JDK TLS defaults for server name
indication. Configure `sni` when a client or request must choose the TLS peer
host explicitly. Request-level SNI overrides client-level SNI. If `sni` is
configured without a `mode`, WebClient uses the resolved request URI host.

When WebClient chooses a DNS name, it also sends that name as SNI. When it
chooses an IP literal, it uses the IP literal as the TLS peer host for endpoint
identification but does not send a DNS SNI server name. The `disabled` mode
clears the SNI server name without disabling endpoint identification.

WebClient SNI configuration in `application.yaml`:

```yaml [application.yaml]
client:
  sni:
    mode: "host-header" # uri-host, host-header, explicit, or disabled
```

The `uri-host` mode uses the resolved request URI host. The `host-header` mode
uses the effective HTTP `Host` authority. HTTP/2 requests use the same effective
authority for the generated `:authority` pseudo header. The `explicit` mode
uses `host` as the TLS peer host. Configure `host` only with `explicit` mode.
The configured value must be a host without a port; bracketed IPv6 literals are
accepted and normalized without brackets. The `disabled` mode clears the SNI
server name.

WebClient explicit SNI configuration in `application.yaml`:

```yaml [application.yaml]
client:
  sni:
    mode: "explicit"
    host: "service.example"
```

#### Configuring TLS over Unix Domain Sockets

When `base-address` is a Unix domain socket address, it selects only the
physical transport. Configure `base-uri` or use absolute request URIs to provide
the logical HTTPS authority. The logical host and port are used for the `Host`
header, HTTP/2 `:authority`, SNI, and endpoint identification.

WebClient TLS over Unix domain socket configuration in `application.yaml`:

```yaml [application.yaml]
client:
  base-uri: "https://service.example:8443"
  base-address: "unix:/var/run/service.sock"
  tls:
    trust:
      keystore:
        passphrase: "password"
        trust-store: true
        resource:
          resource-path: "client.p12"
```

### Adding Service to WebClient

WebClient currently supports several built-in services, namely

- [`discovery`][discovery]
- `metrics`
- `tracing`
- `telemetry` (following OpenTelemetry semantic conventions)
  - `metrics`
  - `tracing`
- `security`.

#### Enabling the service

In order for a service to function, its dependencies need to be added in the
application’s `pom.xml`. Below are examples on how to enable the built-in
services:

- `discovery` (see [its documentation][discovery])

  ```xml [pom.xml]
  <dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-discovery</artifactId>
    <scope>runtime</scope>
  </dependency>
  ```
- `metrics`

  ```xml [pom.xml]
  <dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-metrics</artifactId>
  </dependency>
  ```

- `tracing`

  ```xml [pom.xml]
  <dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-tracing</artifactId>
  </dependency>
  ```

- `telemetry metrics` and `tracing`

  ```xml [pom.xml]
  <dependencdy>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-telemetry</artifactId>
  </dependencdy>
  ```

- `security`

  ```xml [pom.xml]
  <dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-security</artifactId>
  </dependency>
  ```

### Adding a service in your code

Services can be added in WebClient as shown in the code below.

<!--@mdc ::code-callout -->
```java
WebClientService clientService = WebClientMetrics.counter()
        .methods(Method.GET)
        .nameFormat("example.metric.%1$s.%2$s")
        .build(); // <1>

WebClient.builder()
        .addService(clientService) // <2>
        .build();
```
1. Creates new metric which will count all GET requests and has format of
   `example.metric.GET.<host-name>`
2. Register the service in the client instance.
<!--@mdc :: -->

### Adding service in the config file

Adding service in WebClient can also be done through the `application.yaml`
configuration file.

WebClient Service configuration in `application.yaml`:

```yaml [application.yaml]
webclient:
  services:
    metrics:
      - type: METER
        name-format: "client.meter.overall"
      - type: TIMER
        # meter per method
        name-format: "client.meter.%1$s"
      - methods: ["PUT", "POST", "DELETE"]
        type: COUNTER
        success: false
        name-format: "wc.counter.%1$s.error"
        description: "Counter of failed PUT, POST and DELETE requests"
    tracing:
```

Then, in your application code, load the configuration from that file.

WebClient initialization using the `application.yaml` file located on the
classpath:

<!--@mdc ::code-callout -->
```java [application.yaml]
Config config = Config.create(); // <1>
WebClient.builder()
        .config(config.get("client")) // <2>
        .build();
```
1. `application.yaml` is a default configuration source loaded when YAML support
   is on classpath, so we can just use `Config.create()`
2. Passing the client configuration node
<!--@mdc :: -->

## Setting Protocol configuration

Individual protocols can be customized using the `protocol-config` parameter.

### Setting up protocol configuration in your code

Below is an example of customizing `HTTP/1.1` protocol in the application code.

```java
WebClient.builder()
    .addProtocolConfig(Http1ClientProtocolConfig.builder()
        .defaultKeepAlive(false)
        .validateRequestHeaders(true)
        .validateResponseHeaders(false)
        .build())
    .build();
```

### Setting up protocol configuration in the config file

Protocol configuration can also be set in the `application.yaml` configuration
file.

Setting up HTTP/1.1 and HTTP/2 protocol using `application.yaml` file:

```yaml [application.yaml]
webclient:
  protocol-configs:
    http_1_1:
      max-header-size: 20000
      validate-request-headers: true
    h2:
      prior-knowledge: true
```

Then, in your application code, load the configuration from that file.

WebClient initialization using the `application.yaml` file located on the
classpath:

<!--@mdc ::code-callout -->
```java [application.yaml]
Config config = Config.create(); // <1>
WebClient.builder()
        .config(config.get("client")) // <2>
        .build();
```
1. `application.yaml` is a default configuration source loaded when YAML support
   is on classpath, so we can just use `Config.create()`
2. Passing the client configuration node.
<!--@mdc :: -->

## Configuring Telemetry

The telemetry webclient services provide metrics and tracing spans which follow
the OpenTelemetry semantic conventions for clients. These are separate from the
`services.metrics` and `services.tracing` services described elsewhere on this
page.

To enable the telemetry webclient services, take the following two steps:

- Add the appropriate dependency.
- Add configuration or code to activate the telemetry services.

To set up metrics and tracing, add the following single dependency to your
project:

Dependency for webclient telemetry metrics and tracing:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient</groupId>
  <artifactId>helidon-webclient-telemetry</artifactId>
  <scope>runtime</scope>
</dependency>
```

To transmit the metrics semantic conventions to a backend, add a dependency on
an OpenTelemetry exporter and in the `telemetry` configuration set up an
exporter under `signals.metrics`.

Dependency for exporting metrics semantic conventions data using OTLP:

```xml [pom.xml]
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
  <scope>runtime</scope>
</dependency>
```

Configuration for an OpenTelemetry exporter:

```yaml [application.yaml]
telemetry:
  service: my-app
  signals:
    metrics:
      exporters:
        type: otlp
```

To activate webclient telemetry collection using configuration, add the
`telemetry` config section under `client.services` and, below it, add `metrics`,
`tracing`, or both.

Enabling metrics and tracing telemetry using configuration:

```yaml [application.yaml]
client:
  services:
    telemetry:
      metrics:
      tracing:
```

The `metrics` and `tracing` subsections have no explicit settings.

Alternatively, trigger webclient telemetry collection by modifying your client
code to add one or more webclient telemetry services to the webclient builder.
This example shows adding only telemetry metrics.

Enabling telemetry using code:

```java
WebClient.builder()
    .addService(WebClientTelemetryMetrics.create())
    .build();
```

## Context Propagation

WebClient supports the capability to propagate values from
`io.helidon.common.context.Context` over HTTP headers.

To enable this feature (implemented as a WebClient service), add the following
dependency to your pom file:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient</groupId>
  <artifactId>helidon-webclient-context</artifactId>
</dependency>
```

Example configuration:

<!--@mdc ::code-callout -->
```yaml [application.yaml]
client:
  services:
    context:
      records:
        - header: "X-Helidon-Tid" # <1>
        - classifier: "io.helidon.webclient.context.propagation.cid" # <2>
          header: "X-Helidon-Cid"
          default-value: [ "first", "second" ]
          array: true
```
1. This looks up a `java.lang.String` context value classified `X-Helidon-Tid`
   and sends it as `X-Helidon_Tid` header
2. This looks up a `java.lang.String[]` context value classified with the
   classifier and sends it as a `X-Helidon-Cid` header, in case the value is not
   present, values "first" and "second" are sent instead
<!--@mdc :: -->

### Configuration options

<!--@include ../config/io.helidon.webclient.context.WebClientContextService.md#configuration-options delim=--- offset=1 collapseTables=10 -->
See [Configuration options][io-helidon-webcl-4].
<!--/include-->


See the [manifest](../config/manifest.md) for all available types.

## Reference

- [Helidon WebClient API][helidon-webclien]
- [Helidon WebClient HTTP/1.1 Support][helidon-webclien-2]
- [Helidon WebClient HTTP/2 Support][helidon-webclien-3]
- [Helidon WebClient DNS Resolver First Support][helidon-webclien-4]
- [Helidon WebClient DNS Resolver Round Robin Support][helidon-webclien-5]
- [Helidon WebClient Discovery Support][helidon-webclien-6]
- [Helidon WebClient Metrics Support][helidon-webclien-7]
- [Helidon WebClient Security Support][helidon-webclien-8]
- [Helidon WebClient Tracing Support][helidon-webclien-9]

[httpclient]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClient.html
[httpclientreques]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClientRequest.html
[clientrequestbas]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/ClientRequestBase.html
[clientrequest]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/ClientRequest.html
[discovery]: discovery.md#webclient-integration
[helidon-webclien]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.api/module-summary.html
[helidon-webclien-2]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.http1/module-summary.html
[helidon-webclien-3]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.http2/module-summary.html
[helidon-webclien-4]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.dns.resolver.first/module-summary.html
[helidon-webclien-5]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.dns.resolver.roundrobin/module-summary.html
[helidon-webclien-6]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.discovery/module-summary.html
[helidon-webclien-7]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.metrics/module-summary.html
[helidon-webclien-8]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.security/module-summary.html
[helidon-webclien-9]: https://helidon.io/docs/v27/apidocs/io.helidon.webclient.tracing/module-summary.html
[io-helidon-webcl]: ../config/io.helidon.webclient.api.WebClient.md#configuration-options
[io-helidon-webcl-2]: ../config/io.helidon.webclient.http1.Http1ClientProtocolConfig.md#configuration-options
[io-helidon-webcl-3]: ../config/io.helidon.webclient.http2.Http2ClientProtocolConfig.md#configuration-options
[io-helidon-webcl-4]: ../config/io.helidon.webclient.context.WebClientContextService.md#configuration-options
