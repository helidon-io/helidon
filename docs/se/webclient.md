# WebClient Introduction

## Overview

WebClient is an HTTP client for Helidon SE. It can be used to send requests and retrieve corresponding responses in a programmatic way.

Helidon WebClient provides the following features:

- **Blocking approach**
  The WebClient uses the blocking approach to synchronously process a request and its corresponding response. Both `HTTP/1.1` and `HTTP/2` request and response will run in the thread of the user. Additionally, for `HTTP/2`, virtual thread is employed to manage the connection.

- **Builder-like setup and execution**
  Creates every client and request as a builder pattern. This improves readability and code maintenance.

- **Redirect chain**
  Follows the redirect chain and perform requests on the correct endpoint by itself.

- **Tracing and security propagation**
  Automatically propagates the configured tracing and security settings of the Helidon WebServer to the WebClient and uses them during request and response.

## Maven Coordinates

To enable WebClient, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../managing-dependencies.md)).

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

You can create an instance of a WebClient by executing `WebClient.create()` which will have default settings and without a base uri set.

To change the default settings and register additional services, you can use simple builder that allows you to customize the client behavior.

Create a WebClient with simple builder:

```java
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

Check out [HttpClient][httpclient] API to learn more about request methods. These methods will create a new instance of [HttpClientRequest][httpclientreques] which can then be configured to add optional settings that will customize the behavior of the request.

### Customizing the Request

Configuration can be set for every request type before it is sent.

Customizing a request:

```java
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

For more information about these optional parameters, check out [ClientRequestBase][clientrequestbas] API, which is a parent class of [HttpClientRequest][httpclientreques].

[HttpClientRequest][httpclientreques] class also provides specific header methods that help the user to set a particular header. Some examples of these are:

- `contentType` (MediaType contentType)
- `accept` (MediaType…​ mediaTypes)

For more information about these methods, check out [ClientRequest][clientrequest] API, which is a parent class of [HttpClientRequest][httpclientreques].

### Sending the Request

Once the request setup is completed, the following methods can be used to send it:

- `HttpClientResponse request()`
- `<E> ClientResponseTyped<E> request(Class<E> type)`
- `<E> E requestEntity(Class<E> type)`
- `HttpClientResponse submit(Object entity)`
- `<T> ClientResponseTyped<T> submit(Object entity, Class<T> requestedType)`
- `HttpClientResponse outputStream(OutputStreamHandler outputStreamConsumer)`
- `<T> ClientResponseTyped<T> outputStream(OutputStreamHandler outputStreamConsumer, Class<T> requestedType)`

Each of the methods will provide a way to allow response to be retrieved in a particular response type. Refer to [ClientRequest API][clientrequest] for more details about these methods.

Execute a simple GET request to endpoint and receive a String response:

```java
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
   ```java
  String result = client.get()
      .protocolId("http/1.1")
      .requestEntity(String.class);
  ```
- If `HTTP/2` is used, an upgrade attempt will be performed. If it fails, the client falls-back to `HTTP/1.1`.
- The parameter `prior-knowledge` can be defined using `HTTP/2` protocol configuration. Please refer to [Setting Protocol configuration](#setting-protocol-configuration) on how to customize `HTTP/2`. In such a case, `prior-knowledge` will be used and fail if it is unable to switch to `HTTP/2`.

### Adding Media Support

WebClient supports the following built-in Helidon Media Support libraries:

1.  JSON Processing (JSON-P)
2.  JSON Binding (JSON-B)
3.  Jackson

They can be activated by adding their corresponding libraries into the classpath. This can simply be done by adding their corresponding dependencies.

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

Users can also create their own Custom Media Support library and make them work by following either of the approaches:

- Create a Provider of the Custom Media Support and expose it via Service Loader followed by adding the Media Support library to the classpath.
- Explicitly register the Custom Media Support from WebClient.

```java
WebClient.builder()
        .mediaContext(it -> it
                .addMediaSupport(CustomMediaSupport.create()))
        .build();
```

- Register CustomMedia support from the WebClient.

### DNS Resolving

WebClient provides three DNS resolver implementations out of the box:

- `Java DNS resolution` is the default.
- `First DNS resolution` uses the first IP address from a DNS lookup. To enable this option, add below dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient.dns.resolver</groupId>
  <artifactId>helidon-webclient-dns-resolver-first</artifactId>
</dependency>
```

- `Round-Robin DNS resolution` cycles through IP addresses from a DNS lookup. To enable this option, add this dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient.dns.resolver</groupId>
  <artifactId>helidon-webclient-dns-resolver-round-robin</artifactId>
</dependency>
```

## Configuring the WebClient

The class responsible for WebClient configuration is:

### Configuration options

<!--@include ../config/io.helidon.webclient.api.WebClient.md#configuration-options offset=1 collapseTables=10 -->
See [Configuration options](../config/io.helidon.webclient.api.WebClient.md#configuration-options).
<!--/include-->


### Protocol Specific Configuration

Protocol specific configuration can be set using the `protocol-configs` parameter. WebClient currently supports `HTTP/1.1.` and `HTTP/2`. Below are the options for each of the protocol type:

- `HTTP/1.1`

#### Configuration options

<!--@include ../config/io.helidon.webclient.http1.Http1ClientProtocolConfig.md#configuration-options offset=2 collapseTables=10 -->
See [Configuration options](../config/io.helidon.webclient.http1.Http1ClientProtocolConfig.md#configuration-options).
<!--/include-->


- `HTTP/2`

#### Configuration options

<!--@include ../config/io.helidon.webclient.http2.Http2ClientProtocolConfig.md#configuration-options offset=2 collapseTables=10 -->
See [Configuration options](../config/io.helidon.webclient.http2.Http2ClientProtocolConfig.md#configuration-options).
<!--/include-->


### Example of a WebClient Runtime Configuration

```java
Config config = Config.create();
WebClient client = WebClient.builder()
        .baseUri("http://localhost")
        .config(config.get("client"))
        .build();
```

### Example of a WebClient YAML Configuration

<!--@mdc ::code-collapse -->
```yaml [application.yaml]
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
<!--@mdc :: -->

- Client functional settings
- Cookie management
- Default client headers
- Client service configuration
- Protocol configuration
- Proxy configuration
- TLS configuration

## Examples

### WebClient with Proxy

Configure Proxy setup either programmatically or via the Helidon configuration framework.

#### Configuring Proxy in your code

Proxy can be set directly from WebClient builder.

```java
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

```java
Proxy proxy = Proxy.create();
HttpClientResponse response = client.get("/proxiedresource")
        .proxy(proxy)
        .request();
```

- Proxy instance configured using system settings (environment variables and system properties)
- Configure the proxy per client request

#### Configuring Proxy in the config file

Proxy can also be configured in WebClient through the `application.yaml` configuration file.

WebClient Proxy configuration in `application.yaml`:

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

```java
WebClient.builder()
        .tls(it -> it.trust(t -> t
                .keystore(k -> k.passphrase("password")
                        .trustStore(true)
                        .keystore(r -> r.resourcePath("client.p12")))))
        .build();
```

#### Configuring TLS in the config file

Another way to configure TLS in WebClient is through the `application.yaml` configuration file.

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

> [!NOTE]
> The `passphrase` value on the config file can be encrypted if stronger security is required. For more information on how secrets can be encrypted using a master password and store them in a configuration file, please see [Configuration Secrets][configuration-se].

In the application code, load the settings from the configuration file.

WebClient initialization using the `application.yaml` file located on the
classpath:

```java [application.yaml]
Config config = Config.create();
WebClient.builder()
        .config(config.get("client"))
        .build();
```

- `application.yaml` is a default configuration source loaded when YAML support is on classpath, so we can just use `Config.create()`
- Passing the client configuration node

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

In order for a service to function, its dependencies need to be added in the application’s `pom.xml`. Below are examples on how to enable the built-in services:

- `discovery` (see [its documentation][discovery])

  ```xml [pom.xml]
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

  - Backs the `discovery` service with a [Discovery provider based on Netflix’s Eureka](discovery.md#eureka)

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

```java
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

```java [application.yaml]
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

Protocol configuration can also be set in the `application.yaml` configuration file.

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

```java [application.yaml]
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

Dependency for webclient telemetry metrics and tracing:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient</groupId>
  <artifactId>helidon-webclient-telemetry</artifactId>
  <scope>runtime</scope>
</dependency>
```

To transmit the metrics semantic conventions to a backend, add a dependency on an OpenTelemetry exporter and in the `telemetry` configuration set up an exporter under `signals.metrics`.

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

To activate webclient telemetry collection using configuration, add the `telemetry` config section under `client.services` and, below it, add `metrics`, `tracing`, or both.

Enabling metrics and tracing telemetry using configuration:

```yaml [application.yaml]
client:
  services:
    telemetry:
      metrics:
      tracing:
```

The `metrics` and `tracing` subsections have no explicit settings.

Alternatively, trigger webclient telemetry collection by modifying your client code to add one or more webclient telemetry services to the webclient builder. This example shows adding only telemetry metrics.

Enabling telemetry using code:

```java
WebClient.builder()
        .addService(WebClientTelemetryMetrics.create())
        .build();
```

## Context Propagation

WebClient supports the capability to propagate values from `io.helidon.common.context.Context` over HTTP headers.

To enable this feature (implemented as a WebClient service), add the following dependency to your pom file:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webclient</groupId>
  <artifactId>helidon-webclient-context</artifactId>
</dependency>
```

Example configuration:

```yaml [application.yaml]
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

<!--@include ../config/io.helidon.webclient.context.WebClientContextService.md#configuration-options offset=1 collapseTables=10 -->
See [Configuration options](../config/io.helidon.webclient.context.WebClientContextService.md#configuration-options).
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

[httpclient]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClient.html
[httpclientreques]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/HttpClientRequest.html
[clientrequestbas]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/ClientRequestBase.html
[clientrequest]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/ClientRequest.html
[configuration-se]: ../mp/security/configuration-secrets.md
[discovery]: discovery.md#web-client-discovery-integration
[helidon-webclien]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.api/module-summary.html
[helidon-webclien-2]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.http1/module-summary.html
[helidon-webclien-3]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.http2/module-summary.html
[helidon-webclien-4]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.dns.resolver.first/module-summary.html
[helidon-webclien-5]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.dns.resolver.roundrobin/module-summary.html
[helidon-webclien-6]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.discovery/module-summary.html
[helidon-webclien-7]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.metrics/module-summary.html
[helidon-webclien-8]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.security/module-summary.html
[helidon-webclien-9]: https://helidon.io/docs/v4/apidocs/io.helidon.webclient.tracing/module-summary.html
