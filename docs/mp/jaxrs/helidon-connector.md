# Helidon Connector

## Overview

Helidon uses Jersey as the Jakarta REST (JAX-RS) implementation. Jersey supports the concept of *connectors* which is an SPI to handle low-level HTTP connections when using the Jakarta REST Client API. Helidon provides a connector that is based on its `WebClient` implementation and that has a few benefits, most notably, configuration using Config and support for HTTP/2.

## Maven Coordinates

To enable Helidon Connector, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
 <dependency>
     <groupId>io.helidon.jersey</groupId>
     <artifactId>helidon-jersey-connector</artifactId>
 </dependency>
```

## API

Enabling the Helidon connector is possible at creation time using Jersey’s `ClientConfig` instance as shown below:

```java
ClientConfig clientConfig = new ClientConfig();
clientConfig.connectorProvider(HelidonConnectorProvider.create());       // Helidon connector
Client client = ClientBuilder.newClient(clientConfig);
```

Any subsequent requests using a `Client` instance configured this way will defer to the Helidon connector to handle the underlying HTTP connection.

## Configuration

The Helidon connector implementation is based on WebClient. Even though WebClient can handle protocols other than HTTP (such as gRPC), only its HTTP capabilities are exercised by the connector when using Jersey. Since it is based on WebClient, it can be configured by passing a config fragment that matches the properties defined in `HttpClientConfig` —a supertype of `WebClientConfig` that handles HTTP specific configuration.

Assuming the root of the `WebClient` configuration is located at `my.webclient`, this can be done programmatically when building the `ClientConfig` instance in the Jakarta REST API:

```java
clientConfig.property(HelidonProperties.CONFIG, config.get("my.webclient"));
```

If not provided as the value of the `HelidonProperties.CONFIG` property as shown above, the connector will look for WebClient configuration rooted at `jersey.connector.webclient`. For example, the following YAML config file can be used to set some socket options and turn off 100-continue support for all WebClient instances created by the connector:

```yaml
jersey:
  connector:
    webclient:
      socket-options:
        connect-timeout: PT1M
        read-timeout: PT1M
        socket-receive-buffer-size: 1024
        socket-send-buffer-size: 1024
        tcp-no-delay: false
      send-expect-continue: false
```

There are some additional properties that can be passed via `ClientConfig` that also affect how WebClient instance used by the connector are configured. Some of these properties are defined by Jersey (those prefixed by `jersey.config.client`) and some by Helidon (those prefixed by `jersey.connector.helidon`). If any of these properties is specified, it will override the equivalent setting provided in a WebClient config as shown above —regardless of whether it was passed in using `HelidonProperties.CONFIG` or located at the `jersey.connector.webclient` root.

The complete list of properties supported by the Helidon connector is listed below:

| Property Name | Type | Scope | Default |
|----|----|----|----|
| jersey.config.client.connectTimeout | `Integer` | client | 10000 (millis) |
| jersey.config.client.readTimeout | `Integer` | client, invocation | 10000 (millis) |
| jersey.config.client.followRedirects | `Boolean` | client, invocation | true |
| jersey.config.client.request.expect.100.continue.processing | `Boolean` | client | true |
| jersey.connector.helidon.config | `io.helidon.config.Config` | client | (none) |
| jersey.connector.helidon.tls | `io.helidon.common.tls.Tls` | client | (none) |
| jersey.connector.helidon.protocolConfigs | `List<? extends ProtocolConfig>` | client | (none) |
| jersey.connector.helidon.defaultHeaders | `Map<String, String>` | client | (none) |
| jersey.connector.helidon.protocolId | `String` | invocation | (none) |
| jersey.connector.helidon.shareConnectionCache | `Boolean` | client | false |

> [!NOTE]
> Each of the properties in the table above accepts a well-defined type for its value. If passing a value whose type cannot be converted to the required one, the property is simply ignored by the connector.

### HTTP/2 Support

One clear advantage of using the Helidon connector, as opposed to the default one provided by Jersey, is the ability to issue HTTP/2 requests. There are three ways to enable HTTP/2:

1.  Via content negotiation from HTTP/1.1, where the initial request is HTTP/1.1 (text) and the first response is HTTP/2 (binary), assuming the negotiation is successful.
2.  Similar to (1) except that a TLS extension called ALPN is used to convey the upgrade negotiation. Naturally, this only works with secure connections, so TLS is a requirement here.
3.  Using prior knowledge, where the client simply sends an HTTP/2 request knowing *a priori* that the server is capable of handling it. This option always requires TLS.

## Examples

### HTTP/2 Negotiation Without TLS

Without TLS, HTTP/2 negotiation is accomplished by setting a single property. In the example below, the property is set on the correspoding `WebTarget`, which indicates that it applies to all requests created from it.

```java
ClientConfig clientConfig = new ClientConfig();
clientConfig.connectorProvider(HelidonConnectorProvider.create());
Client client = ClientBuilder.newClient(clientConfig);

WebTarget webTarget = client.target(uri)
        .property(HelidonProperties.PROTOCOL_ID, Http2Client.PROTOCOL_ID);      // HTTP2 upgrade
try (Response response = webTarget.request().get()) {
    // ...
}
```

> [!NOTE]
> Properties in the Jakarta REST Client API can be set on `Client`, `WebTarget` and `Invocation` and are inherited accordingly.

The request invocation in the example above will include an HTTP/2 protocol upgrade request which may be granted by the server if HTTP/2 support is enabled.

### HTTP/2 Negotiation With TLS/ALPN

ALPN is a TLS extension that can be used for HTTP/2 negotiation. The Helidon connector accepts a `Tls` instance to enable protocol security and also to negotiate an HTTP/2 upgrade as shown below.

```java
Tls tls = Tls.builder()
        .trustAll(true)
        .addApplicationProtocol(Http2Client.PROTOCOL_ID)        // HTTP/2 upgrade
        .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
        .build();

ClientConfig clientConfig = new ClientConfig();
clientConfig.connectorProvider(HelidonConnectorProvider.create());
clientConfig.property(HelidonProperties.TLS, tls);
Client client = ClientBuilder.newClient(clientConfig);

WebTarget webTarget = client.target(uri);
try (Response response = webTarget.request().get()) {
    // ...
}
```

The call to `addApplicationProtocol()` indicates the desire to negotiate a protocol upgrade. Naturally, ALPN only works on secure connections, so TLS is always configured at the same time.

### HTTP/2 Prior Knowledge

The last example shows how to enable HTTP/2 when prior knowledge of the server’s capabilities is known ahead of time. In order to force HTTP/2 for the initial request, we must provide an `Http2ClientProtocolConfig` instance that is properly configured for that purpose. Passing protocol configurations is a general mechanism supported by the connector; in this example, we take advantage of this mechanism to pre-configure the desired HTTP/2 support as shown next.

```java
Tls tls = Tls.builder()
        .trustAll(true)
        .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
        .build();

ClientConfig clientConfig = new ClientConfig();
clientConfig.connectorProvider(HelidonConnectorProvider.create());
clientConfig.property(HelidonProperties.TLS, tls);
clientConfig.property(HelidonProperties.PROTOCOL_CONFIGS,
                      List.of(Http2ClientProtocolConfig.builder()
                                      .priorKnowledge(true)    // HTTP/2 knowlege
                                      .build()));
Client client = ClientBuilder.newClient(clientConfig);

WebTarget webTarget = client.target(uri);
try (Response response = webTarget.request().get()) {
    // ...
}
```

The property `HelidonProperties.PROTOCOL_CONFIGS` accepts a list of protocol configurations that are passed directly to the underlying `WebClient` layer.

## Additional Information

For additional information, see the [Jakarta REST Javadocs](https://jakarta.ee/specifications/restful-ws/3.1/apidocs).

## Reference

- [Jakarta REST Client Specification](https://jakarta.ee/specifications/restful-ws/3.1/jakarta-restful-ws-spec-3.1.html#client_api)
- [Jersey User Guide](https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest31x/index.html)
