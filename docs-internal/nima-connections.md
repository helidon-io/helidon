Connections
----

# Server Connection Providers

Níma server uses SPI to discover how to handle a specific incoming connection.
The main entry point is `ServerConnectionProvider` - a service loader provider interface to discover 
which connections are supported.

We have the following implementations currently:

- `Http1ConnectionProvider` - support for the usual HTTP/1.1 connections
- `Http2ConnectionProvider` - support for "prior-knowledge" HTTP/2 connections

The connection provider creates a configured `ServerConnectionSelector`, which is then used at runtime.
The selector works based on initial bytes of the connection.


# HTTP/1.1 Upgrade Providers

HTTP/1.1 supports the concept of upgrading a connection. This is supported through
`Http1UpgradeProvider` - a service loader provider interface to discover which upgrades are supported.

We have the following implementations currently:

- `Http2UpgradeProvider` - upgrade to HTTP/2 using upgrade
- `WsUpgradeProvider` - upgrade to Níma WebSocket implementation
- `TyrusUpgradeProvider` - upgrade to MP Tyrus WebSocket implementation (higher weight than WsUpgradeProvider)

The upgrade provider creates a configured `Http1Upgrader`, which is then used at runtime.
Upgraders work based on protocol identificator (`h2c`, `websocket`). When more than one for the same protocol is configured,
the provider with higher weight will be used.

# Configurability

ALl of connection providers, HTTP/1.1 upgrade providers and HTTP/2 subprotocols are configured under `server.connection-providers`, to have a single configuration of a protocol regardless whether this is accessed directly or through upgrade mechanism.

The configuration key is the one provided by the Connection provider, HTTP/1.1 Upgrade provider, or HTTP/2 SubProtocol provider `configKey()` or `configKeys()` method.

As all providers are configured on the same leave, each provider should have a descriptive and unique configuration key
relevant to its purpose.

Example of such configuration (Tyrus and Níma WebSocket both use `websocket`, as only one of them can be active):
```yaml
server:
  connection-providers:
    http_1_1:
      max-prologue-length: 4096
    websocket:
      origins: ["origin1"]
    http_2:
      max-frame-size: 128000
    grpc:
      something: "value"
```
