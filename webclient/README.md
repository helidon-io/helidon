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

Client Alt-Svc use is opt-in through the optional common `ClientAltSvcConfig` (`alt-svc`) configuration. Within a
present configuration, `enabled` defaults to `true`; an empty `protocols` list allows every available supporting
provider, while a non-empty list is an exact, case-sensitive ALPN protocol filter. The current HTTP/2 provider uses the
`h2` protocol ID.

Initial Alt-Svc support accepts advertisements only from HTTPS origins and only for alternatives on the same host,
although the port may differ. Using an alternative changes only the connection endpoint; the request scheme and
authority remain unchanged, and WebClient adds `Alt-Used` only to the alternative request. `h2c` alternatives are not
supported. Plain HTTP origins cannot be upgraded to TLS-based HTTP/2 until the RFC 8164
`/.well-known/http-opportunistic` opt-in is implemented.

Alt-Svc never bypasses the configured proxy policy. The current HTTP/2 provider uses alternatives only when proxying is
disabled. When any proxy policy is configured, including a `no-proxy` exception that selected a direct origin route,
WebClient ignores the advertisement. WebClient uses the system proxy policy by default, so configure `proxy.type` as
`none`, or use `Proxy.noProxy()` programmatically, to use an advertised alternative. The configured TLS policy is
honored as-is for the alternative, including an explicit `SSLContext`, a custom TLS manager, disabled endpoint
identification, and `trust-all`. An unsafe or permissive TLS policy makes Alt-Svc steering equally unsafe or permissive.

The HTTP/2 provider applies `ma`, `Age`, `Date`, `clear`, and `persist` to its connection-cache discovery state. Shared
connection caches share that state; disabling connection-cache sharing isolates it. `persist` does not make discovery
state durable across a process or beyond the cache lifecycle.

HTTP/3 cannot use TCP ALPN or an HTTP/1.1 upgrade because it runs over QUIC. The common client policy, response
notification, and parsed `Alt-Svc` model can also support an HTTP/3 provider.

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
