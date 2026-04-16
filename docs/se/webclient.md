# WebClient Introduction

## Overview

WebClient is an HTTP client for Helidon SE. It can be used to send requests and retrieve corresponding responses in a programmatic way.

Helidon WebClient provides the following features:

- **Blocking approach**  
  The Webclient uses the blocking approach to synchronously process a request and its correspond response. Both `HTTP/1.1` and `HTTP/2` request and response will run in the thread of the user. Additionally, for `HTTP/2`, virtual thread is employed to manage the connection.

- **Builder-like setup and execution**  
  Creates every client and request as a builder pattern. This improves readability and code maintenance.

- **Redirect chain**  
  Follows the redirect chain and perform requests on the correct endpoint by itself.

- **Tracing and security propagation**  
  Automatically propagates the configured tracing and security settings of the Helidon WebServer to the WebClient and uses them during request and response.

## Maven Coordinates

To enable WebClient, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient</artifactId>
</dependency>
```

The `helidon-webclient` dependency has built-in support for `HTTP/1.1`.

If support for `HTTP/2` is a requirement, below dependency needs to be added:

``` xml
<dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-http2</artifactId>
</dependency>
```

## Usage

### Instantiating the WebClient

You can create an instance of a WebClient by executing `WebClient.create()` which will have default settings and without a base uri set.

To change the default settings and register additional services, you can use simple builder that allows you to customize the client behavior.

*Create a WebClient with simple builder:*

``` java
WebClient client = WebClient.builder()
        .baseUri("http://localhost")
        .build();
```

### Creating the Request

WebClient offers a set of request methods that are used to specify the type of action to be performed on a given resource. Below are some examples of request methods:

- `get()`
- `post()`
- `put()`
- `method(Method method)`

Check out [HttpClient](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClient.html) API to learn more about request methods. These methods will create a new instance of [HttpClientRequest](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClientRequest.html) which can then be configured to add optional settings that will customize the behavior of the request.

### Customizing the Request

Configuration can be set for every request type before it is sent.

*Customizing a request*

``` java
client.get()
        .uri("http://example.com") 
        .path("/path") 
        .queryParam("query", "parameter") 
        .fragment("someFragment") 
        .headers(headers -> headers.accept(MediaTypes.APPLICATION_JSON)); 
```

- Overrides `baseUri` from WebClient
- Adds path to the uri
- Adds query parameter to the request
- Adds fragment to the request
- Adds header to the request

For more information about these optional parameters, check out [ClientRequestBase](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/ClientRequestBase.html) API, which is a parent class of [HttpClientRequest](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClientRequest.html).

[HttpClientRequest](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClientRequest.html) class also provides specific header methods that help the user to set a particular header. Some examples of these are:

- `contentType` (MediaType contentType)
- `accept` (MediaType…​ mediaTypes)

For more information about these methods, check out [ClientRequest](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/ClientRequest.html) API, which is a parent class of [HttpClientRequest](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClientRequest.html).

### Sending the Request

Once the request setup is completed, the following methods can be used to send it:

- `HttpClientResponse request()`
- `<E> ClientResponseTyped<E> request(Class<E> type)`
- `<E> E requestEntity(Class<E> type)`
- `HttpClientResponse submit(Object entity)`
- `<T> ClientResponseTyped<T> submit(Object entity, Class<T> requestedType)`
- `HttpClientResponse outputStream(OutputStreamHandler outputStreamConsumer)`
- `<T> ClientResponseTyped<T> outputStream(OutputStreamHandler outputStreamConsumer, Class<T> requestedType)`

Each of the methods will provide a way to allow response to be retrieved in a particular response type. Refer to [ClientRequest API](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/ClientRequest.html) for more details about these methods.

*Execute a simple GET request to endpoint and receive a String response:*

``` java
ClientResponseTyped<String> response = client.get()
        .path("/endpoint")
        .request(String.class);
String entityString = response.entity();
```

### Protocol Used

WebClient currently supports `HTTP/1.1` and `HTTP/2` protocols. Below are the rules on which specific protocol will be used:

- Using plain socket triggers WebClient to process a request using `HTTP/1.1`.
- When using TLS, the client will use ALPN (protocol negotiation) to use appropriate HTTP version (either 1.1, or 2). `HTTP/2` has a higher weight, so it is chosen if supported by both sides.
- A specific protocol can be explicitly selected by calling `HttpClientRequest#protocolId(String)`.

<!-- -->

    String result = client.get()
            .protocolId("http/1.1")
            .requestEntity(String.class);

- If `HTTP/2` is used, an upgrade attempt will be performed. If it fails, the client falls-back to `HTTP/1.1`.
- The parameter `prior-knowledge` can be defined using `HTTP/2` protocol configuration. Please refer to [Setting Protocol configuration](#setting-protocol-configuration) on how to customize `HTTP/2`. In such a case, `prior-knowledge` will be used and fail if it is unable to switch to `HTTP/2`.

### Adding Media Support

Webclient supports the following built-in Helidon Media Support libraries:

1.  JSON Processing (JSON-P)
2.  JSON Binding (JSON-B)
3.  Jackson

They can be activated by adding their corresponding libraries into the classpath. This can simply be done by adding their corresponding dependencies.

*Add JSON-P support:*

``` xml
<dependency>
    <groupId>io.helidon.http.media</groupId>
    <artifactId>helidon-http-media-jsonp</artifactId>
</dependency>
```

*Add JSON-B support:*

``` xml
<dependency>
    <groupId>io.helidon.http.media</groupId>
    <artifactId>helidon-http-media-jsonb</artifactId>
</dependency>
```

*Add Jackson support:*

``` xml
<dependency>
    <groupId>io.helidon.http.media</groupId>
    <artifactId>helidon-http-media-jackson</artifactId>
</dependency>
```

Users can also create their own Custom Media Support library and make them work by following either of the approaches:

- Create a Provider of the Custom Media Support and expose it via Service Loader followed by adding the Media Support library to the classpath.
- Explicitly register the Custom Media Support from WebClient.

``` java
WebClient.builder()
        .mediaContext(it -> it
                .addMediaSupport(CustomMediaSupport.create())) 
        .build();
```

- Register CustomMedia support from the WebClient.

### DNS Resolving

Webclient provides three DNS resolver implementations out of the box:

- `Java DNS resolution` is the default.
- `First DNS resolution` uses the first IP address from a DNS lookup. To enable this option, add below dependency:

``` xml
<dependency>
    <groupId>io.helidon.webclient.dns.resolver</groupId>
    <artifactId>helidon-webclient-dns-resolver-first</artifactId>
</dependency>
```

- `Round-Robin DNS resolution` cycles through IP addresses from a DNS lookup. To enable this option, add this dependency:

``` xml
<dependency>
    <groupId>io.helidon.webclient.dns.resolver</groupId>
    <artifactId>helidon-webclient-dns-resolver-round-robin</artifactId>
</dependency>
```

## Configuring the WebClient

The class responsible for WebClient configuration is:

### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a48ec2-connect-timeout"></span> `connect-timeout` | `VALUE` | `Duration` |   | Connect timeout |
| <span id="a5bc70-follow-redirects"></span> `follow-redirects` | `VALUE` | `Boolean` | `true` | Whether to follow redirects |
| <span id="a6536a-keep-alive"></span> `keep-alive` | `VALUE` | `Boolean` | `true` | Determines if connection keep alive is enabled (NOT socket keep alive, but HTTP connection keep alive, to re-use the same connection for multiple requests) |
| <span id="a04b74-max-redirects"></span> `max-redirects` | `VALUE` | `Integer` | `10` | Max number of followed redirects |
| <span id="a419a4-properties"></span> `properties` | `MAP` | `String` |   | Properties configured for this client |
| <span id="a3662c-protocol-configs"></span> [`protocol-configs`](../config/io_helidon_webclient_spi_ProtocolConfig.md) | `LIST` | `i.h.w.s.ProtocolConfig` |   | Configuration of client protocols |
| <span id="adcd34-protocol-configs-discover-services"></span> `protocol-configs-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `protocol-configs` |
| <span id="a23735-protocol-preference"></span> `protocol-preference` | `LIST` | `String` |   | List of HTTP protocol IDs by order of preference |
| <span id="a62d6a-proxy"></span> [`proxy`](../config/io_helidon_webclient_api_Proxy.md) | `VALUE` | `i.h.w.a.Proxy` |   | Proxy configuration to be used for requests |
| <span id="aecd9d-read-timeout"></span> `read-timeout` | `VALUE` | `Duration` |   | Read timeout |
| <span id="aba9ef-tls"></span> [`tls`](../config/io_helidon_common_tls_Tls.md) | `VALUE` | `i.h.c.t.Tls` |   | TLS configuration for any TLS request from this client |

### Protocol Specific Configuration

Protocol specific configuration can be set using the `protocol-configs` parameter. Webclient currently supports `HTTP/1.1.` and `HTTP/2`. Below are the options for each of the protocol type:

- `HTTP/1.1`

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a7a44c-default-keep-alive"></span> `default-keep-alive` | `VALUE` | `Boolean` | `true` | Whether to use keep alive by default |
| <span id="a81fda-max-buffered-entity-size"></span> `max-buffered-entity-size` | `VALUE` | `i.h.c.Size` | `64 KB` | Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling `io.helidon.http.media.ReadableEntity#buffer` |
| <span id="a403a3-max-header-size"></span> `max-header-size` | `VALUE` | `Integer` | `16384` | Configure the maximum allowed header size of the response |
| <span id="ab0904-max-status-line-length"></span> `max-status-line-length` | `VALUE` | `Integer` | `256` | Configure the maximum allowed length of the status line from the response |
| <span id="a2ff23-name"></span> `name` | `VALUE` | `String` | `http_1_1` | `N/A` |
| <span id="a607dc-validate-request-headers"></span> `validate-request-headers` | `VALUE` | `Boolean` | `false` | Sets whether the request header format is validated or not |
| <span id="a21e77-validate-response-headers"></span> `validate-response-headers` | `VALUE` | `Boolean` | `true` | Sets whether the response header format is validated or not |

- `HTTP/2`

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a06f7b-flow-control-block-timeout"></span> `flow-control-block-timeout` | `VALUE` | `Duration` | `PT15S` | Timeout for blocking while waiting for window update when window is depleted |
| <span id="a942dc-initial-window-size"></span> `initial-window-size` | `VALUE` | `Integer` | `65535` | Configure INITIAL_WINDOW_SIZE setting for new HTTP/2 connections |
| <span id="a2ae0e-max-buffered-entity-size"></span> `max-buffered-entity-size` | `VALUE` | `i.h.c.Size` | `64 KB` | Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling `io.helidon.http.media.ReadableEntity#buffer` |
| <span id="aecd63-max-frame-size"></span> `max-frame-size` | `VALUE` | `Integer` | `16384` | Configure initial MAX_FRAME_SIZE setting for new HTTP/2 connections |
| <span id="aa6ab2-max-header-list-size"></span> `max-header-list-size` | `VALUE` | `Long` | `-1` | Configure initial MAX_HEADER_LIST_SIZE setting for new HTTP/2 connections |
| <span id="ae847a-name"></span> `name` | `VALUE` | `String` | `h2` | `N/A` |
| <span id="ac97c5-ping"></span> `ping` | `VALUE` | `Boolean` | `false` | Check healthiness of cached connections with HTTP/2.0 ping frame |
| <span id="af75f0-ping-timeout"></span> `ping-timeout` | `VALUE` | `Duration` | `PT0.5S` | Timeout for ping probe used for checking healthiness of cached connections |
| <span id="a8e968-prior-knowledge"></span> `prior-knowledge` | `VALUE` | `Boolean` | `false` | Prior knowledge of HTTP/2 capabilities of the server |

### Example of a WebClient Runtime Configuration

``` java
Config config = Config.create();
WebClient client = WebClient.builder()
        .baseUri("http://localhost")
        .config(config.get("client"))
        .build();
```

### Example of a WebClient YAML Configuration

``` yaml
client:
  connect-timeout-millis: 2000
  read-timeout-millis: 2000
  follow-redirects: true 
  max-redirects: 5
  cookie-manager: 
    automatic-store-enabled: true
    default-cookies:
      flavor3: strawberry
      flavor4: raspberry
  default-headers: 
    Accept: '"application/json", "text/plain"'
  services: 
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
  protocol-configs: 
    http_1_1:
      max-header-size: 20000
      validate-request-headers: true
    h2:
      prior-knowledge: true
  proxy: 
    host: "hostName"
    port: 80
    no-proxy: ["localhost:8080", ".helidon.io", "192.168.1.1"]
  tls: 
    trust:
      keystore:
        passphrase: "password"
        trust-store: true
        resource:
          resource-path: "client.p12"
```

- Client functional settings
- Cookie management
- Default client headers
- Client service configuration
- Protocol configuration
- Proxy configuration
- TLS configuration

## Examples

### Webclient with Proxy

Configure Proxy setup either programmatically or via the Helidon configuration framework.

#### Configuring Proxy in your code

Proxy can be set directly from WebClient builder.

``` java
Proxy proxy = Proxy.builder()
        .type(Proxy.ProxyType.HTTP)
        .host(PROXY_HOST)
        .port(PROXY_PORT)
        .build();
WebClient.builder()
        .proxy(proxy)
        .build();
```

Alternative is to set proxy directly from the request via `HttpClientRequest`.

``` java
Proxy proxy = Proxy.create(); 
HttpClientResponse response = client.get("/proxiedresource")
        .proxy(proxy) 
        .request();
```

- Proxy instance configured using system settings (environment variables and system properties)
- Configure the proxy per client request

#### Configuring Proxy in the config file

Proxy can also be configured in WebClient through the `application.yaml` configuration file.

*WebClient Proxy configuration in `application.yaml`*

``` yaml
client:
  proxy:
    host: "hostName"
    port: 80
    no-proxy: ["localhost:8080", ".helidon.io", "192.168.1.1"]
```

Then, in your application code, load the configuration from that file.

*WebClient initialization using the `application.yaml` file located on the classpath*

``` java
Config config = Config.create(); 
WebClient.builder()
        .config(config.get("client")) 
        .build();
```

- `application.yaml` is a default configuration source loaded when YAML support is on classpath, so we can just use `Config.create()`
- Passing the client configuration node

### WebClient TLS Setup

Configure TLS either programmatically or by the Helidon configuration framework.

#### Configuring TLS in your code

One way to configure TLS in WebClient is in your application code as shown below.

``` java
WebClient.builder()
        .tls(it -> it.trust(t -> t
                .keystore(k -> k.passphrase("password")
                        .trustStore(true)
                        .keystore(r -> r.resourcePath("client.p12")))))
        .build();
```

#### Configuring TLS in the config file

Another way to configure TLS in WebClient is through the `application.yaml` configuration file.

*WebClient TLS configuration in `application.yaml`*

``` yaml
client:
  tls:
    trust:
      keystore:
        passphrase: "password"
        trust-store: true
        resource:
          resource-path: "client.p12"
```

> [!NOTE]
> The `passphrase` value on the config file can be encrypted if stronger security is required. For more information on how secrets can be encrypted using a master password and store them in a configuration file, please see [Configuration Secrets](../mp/security/configuration-secrets.md).

In the application code, load the settings from the configuration file.

*WebClient initialization using the `application.yaml` file located on the classpath*

``` java
Config config = Config.create(); 
WebClient.builder()
        .config(config.get("client")) 
        .build();
```

- `application.yaml` is a default configuration source loaded when YAML support is on classpath, so we can just use `Config.create()`
- Passing the client configuration node

### Adding Service to WebClient

WebClient currently supports several built-in services, namely

- [`discovery`](discovery.md#_web_client_discovery_integration)
- `metrics`
- `tracing`
- `telemetry` (following OpenTelemetry semantic conventions)
  - `metrics`
  - `tracing`
- `security`.

#### Enabling the service

In order for a service to function, its dependencies need to be added in the application’s `pom.xml`. Below are examples on how to enable the built-in services:

- `discovery` (see [its documentation](discovery.md#_web_client_discovery_integration))

  *`pom.xml`*

``` xml
  <dependency>
      <groupId>io.helidon.webclient</groupId>
      <artifactId>helidon-webclient-discovery</artifactId>
      <scope>runtime</scope>
  </dependency>
  <dependency>
      <groupId>io.helidon.discovery.providers</groupId>
      <artifactId>helidon-discovery-providers-eureka</artifactId> 
      <scope>runtime</scope>
  </dependency>
  ```

  - Backs the `discovery` service with a [Discovery provider based on Netflix’s Eureka](discovery.md#_eureka)

- `metrics`

  *`pom.xml`*

``` xml
  <dependency>
      <groupId>io.helidon.webclient</groupId>
      <artifactId>helidon-webclient-metrics</artifactId>
  </dependency>
  ```

- `tracing`

  *`pom.xml`*

``` xml
  <dependency>
      <groupId>io.helidon.webclient</groupId>
      <artifactId>helidon-webclient-tracing</artifactId>
  </dependency>
  ```

- `telemetry metrics` and `tracing`

  *`pom.xml`*

``` xml
  <dependencdy>
      <groupId>io.helidon.webclient</groupId>
      <artifactId>helidon-webclient-telemetry</artifactId>
  </dependencdy>
  ```

- `security`

  *`pom.xml`*

``` xml
  <dependency>
      <groupId>io.helidon.webclient</groupId>
      <artifactId>helidon-webclient-security</artifactId>
  </dependency>
  ```

### Adding a service in your code

Services can be added in WebClient as shown in the code below.

``` java
WebClientService clientService = WebClientMetrics.counter()
        .methods(Method.GET)
        .nameFormat("example.metric.%1$s.%2$s")
        .build(); 

WebClient.builder()
        .addService(clientService) 
        .build();
```

- Creates new metric which will count all GET requests and has format of `example.metric.GET.<host-name>`

- Register the service in the client instance

### Adding service in the config file

Adding service in WebClient can also be done through the `application.yaml` configuration file.

*WebClient Service configuration in `application.yaml`*

``` yaml
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

*WebClient initialization using the `application.yaml` file located on the classpath*

``` java
Config config = Config.create(); 
WebClient.builder()
        .config(config.get("client")) 
        .build();
```

- `application.yaml` is a default configuration source loaded when YAML support is on classpath, so we can just use `Config.create()`
- Passing the client configuration node

<a id="setting-protocol-configuration"></a>
## Setting Protocol configuration

Individual protocols can be customized using the `protocol-config` parameter.

### Setting up protocol configuration in your code

Below is an example of customizing `HTTP/1.1` protocol in the application code.

``` java
WebClient.builder()
        .addProtocolConfig(Http1ClientProtocolConfig.builder()
                                   .defaultKeepAlive(false)
                                   .validateRequestHeaders(true)
                                   .validateResponseHeaders(false)
                                   .build())
        .build();
```

### Setting up protocol configuration in the config file

Protocol configuration can also be set in the `application.yaml` configuration file.

*Setting up `HTTP/1.1` and `HTTP/2` protocol using `application.yaml` file.*

``` yaml
webclient:
  protocol-configs:
    http_1_1:
      max-header-size: 20000
      validate-request-headers: true
    h2:
      prior-knowledge: true
```

Then, in your application code, load the configuration from that file.

*WebClient initialization using the `application.yaml` file located on the classpath*

``` java
Config config = Config.create(); 
WebClient.builder()
        .config(config.get("client")) 
        .build();
```

- `application.yaml` is a default configuration source loaded when YAML support is on classpath, so we can just use `Config.create()`

- Passing the client configuration node

## Configuring Telemetry

The telemetry webclient services provide metrics and tracing spans which follow the OpenTelemetry semantic conventions for clients. These are separate from the `services.metrics` and `services.tracing` services described elsewhere on this page.

To enable the telemetry webclient services, take the following two steps:

- Add the appropriate dependency.
- Add configuration or code to activate the telemetry services.

To set up metrics and tracing, add the following single dependency to your project:

*Dependency for webclient telemetry metrics and tracing*

``` xml
<dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-telemetry</artifactId>
    <scope>runtime</scope>
</dependency>
```

To transmit the metrics semantic conventions to a backend, add a dependency on an OpenTelemetry exporter and in the `telemetry` configuration set up an exporter under `signals.metrics`.

*Dependency for exporting metrics semantic conventions data using OTLP*

``` xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <scope>runtime</scope>
</dependency>
```

*Configuration for an OpenTelemetry exporter*

``` yaml
telemetry:
  service: my-app
  signals:
    metrics:
      exporters:
        type: otlp
```

To activate webclient telemetry collection using configuration, add the `telemetry` config section under `client.services` and, below it, add `metrics`, `tracing`, or both.

*Enabling metrics and tracing telemetry using configuration*

``` yaml
client:
  services:
    telemetry:
      metrics:
      tracing:
```

The `metrics` and `tracing` subsections have no explicit settings.

Alternatively, trigger webclient telemetry collection by modifying your client code to add one or more webclient telemetry services to the webclient builder. This example shows adding only telemetry metrics.

*Enabling telemetry using code*

``` java
WebClient.builder()
        .addService(WebClientTelemetryMetrics.create())
        .build();
```

## Context Propagation

WebClient supports the capability to propagate values from `io.helidon.common.context.Context` over HTTP headers.

To enable this feature (implemented as a WebClient service), add the following dependency to your pom file:

``` xml
<dependency>
    <groupId>io.helidon.webclient</groupId>
    <artifactId>helidon-webclient-context</artifactId>
</dependency>
```

Example configuration:

``` yaml
client:
  services:
    context:
      records:
          # This looks up a `java.lang.String` context value classified `X-Helidon-Tid` and sends it as a `X-Helidon_Tid` header
        - header: "X-Helidon-Tid"
          # This looks up a `java.lang.String[]` context value classified with the classifier and sends it as a `X-Helidon-Cid` header, in case the value is not present, values "first" and "second" are sent instead
        - classifier: "io.helidon.webclient.context.propagation.cid"
          header: "X-Helidon-Cid"
          default-value: [ "first", "second" ]
          array: true
```

Full configuration reference:

### io.helidon.webclient.context.WebClientContextService

#### Description

Configuration of WebClient transport level propagation of context values.

#### Usages

#### Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="ab403e-records"></span> [`records`](../config/io_helidon_common_context_http_ContextRecordConfig.md) | `LIST` | `i.h.c.c.h.ContextRecordConfig` | List of propagation records |

See the [manifest](../config/manifest.md) for all available types.

## Reference

- [Helidon Webclient API](/apidocs/io.helidon.webclient.api/module-summary.html)
- [Helidon WebClient HTTP/1.1 Support](/apidocs/io.helidon.webclient.http1/module-summary.html)
- [Helidon WebClient HTTP/2 Support](/apidocs/io.helidon.webclient.http2/module-summary.html)
- [Helidon WebClient DNS Resolver First Support](/apidocs/io.helidon.webclient.dns.resolver.first/module-summary.html)
- [Helidon WebClient DNS Resolver Round Robin Support](/apidocs/io.helidon.webclient.dns.resolver.roundrobin/module-summary.html)
- [Helidon WebClient Discovery Support](/apidocs/io.helidon.webclient.discovery/module-summary.html)
- [Helidon WebClient Metrics Support](/apidocs/io.helidon.webclient.metrics/module-summary.html)
- [Helidon WebClient Security Support](/apidocs/io.helidon.webclient.security/module-summary.html)
- [Helidon WebClient Tracing Support](/apidocs/io.helidon.webclient.tracing/module-summary.html)
