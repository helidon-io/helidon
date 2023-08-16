Helidon WebClient
----

The WebClient provides:

- an HTTP client API that can be used with any HTTP version
- HTTP version specific client API that adds HTTP version specific methods and features
- support for additional clients that use HTTP to start with 

Each client (including HTTP/2) is located in its own module. The only dependency is on HTTP/1, as it is needed by each
other protocol currently supported by Helidon WebClient. Nevertheless the `webclient-api` module does not depend on it, so
it could be (theoretically) used even without such support, if such a use case arrives in the future. 

# DNS Resolving

DNS resolution can be customized, we provide three implementations out of the box:

- default Java DNS resolution
- "first" DNS resolution (using the first IP address from the list)
- "round-robin" DNS resolution (cycling through IP address from the list)

# TCP based clients
We provide common support for TCP based clients to use Proxies and TLS (see `TcpClientConnection`). 

# HTTP Clients

The main API to use is `io.helidon.webclient.WebClient` that can be used to access an HTTP server.

When using plain socket, it will just use HTTP/1.1. You can configure `protocolPreference` to use a different HTTP version.
If you use HTTP/2, an upgrade attempt would be done (and if it fails, the client falls-back to HTTP/1.1).
You can also define `prior-knowledge` using HTTP/2 protocol configuration (through `WebClient.Builder#addProtocolConfig()`),
in such a case we use `prior-knowledge` and fail if we cannot switch to HTTP/2.

When using TLS, the client will use ALPN (protocol negotiation) to use appropriate HTTP version (either 1.1, or 2).

Once (and if) HTTP/3 is supported, it cannot follow the same approach (as it is UDP and not TCP based). For such cases, we
plan to support `Alt-Svc` header, with which the server can tell us to use a different protocol version, and the next request
to the same authority would switch to that version.

To provide HTTP version support extension, the implementation must provide `webclient.spi.HttpClientSpiProvider`.

HTTP requests support the concept of `WebClientService` that can be used to add features to the client, such as metrics, tracing,
security etc. 
To create a new compatible service, the implementation must provide `webclient.spi.WebClientServiceProvider`
It is sufficient to have the service on classpath for it to be used, can be disabled through configuration (or builder).

We have implementation for the following services:

- metrics
- security
- tracing

# Non-HTTP Clients

WebClient can be used to obtain instances of non-HTTP clients, that use HTTP to upgrade from, or as an underlying protocol.

This includes:

- WebSocket client - upgrades from HTTP/1.1
- grpc client - uses HTTP/2 as the underlying protocol (not yet ready)

To obtain an instance of such client, there are the following options:

1. Use `WebClient.client(WsClient.PROTOCOL)` to obtain a WebSocket client with configuration of the client
2. Use `WebClient.client(WsClient.PROTOCOL, WsClientProtocolConfig.builder()....build())` to customize protocol configuration
3. Use `WsClient.builder()...build()` to customize client and protocol configuration

Any such WebSocket client will use the HTTP/1.1 protocol of the WebClient instance to connect 
(and inherit the TLS configuration, proxy configuration etc.).

Similar rules will apply to grpc client once it is ready, with the exception of using the HTTP/2 protocol instead.

To provide a compatible client, the implementation must provide `webclient.spi.ClientProtocolProvider`.
