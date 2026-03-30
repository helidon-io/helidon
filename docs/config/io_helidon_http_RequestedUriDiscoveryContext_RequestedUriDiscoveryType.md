# io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType

## Description

This type is an enumeration.

## Usages

- [`server.protocols.http_1_1.requested-uri-discovery.types`](io_helidon_http_RequestedUriDiscoveryContext.md#a3fdad-types)
- [`server.protocols.http_2.requested-uri-discovery.types`](io_helidon_http_RequestedUriDiscoveryContext.md#a3fdad-types)
- [`server.requested-uri-discovery.types`](io_helidon_http_RequestedUriDiscoveryContext.md#a3fdad-types)
- [`server.sockets.protocols.http_1_1.requested-uri-discovery.types`](io_helidon_http_RequestedUriDiscoveryContext.md#a3fdad-types)
- [`server.sockets.protocols.http_2.requested-uri-discovery.types`](io_helidon_http_RequestedUriDiscoveryContext.md#a3fdad-types)
- [`server.sockets.requested-uri-discovery.types`](io_helidon_http_RequestedUriDiscoveryContext.md#a3fdad-types)

## Allowed Values

| Value | Description |
|----|----|
| `FORWARDED` | The `io.helidon.http.Header#FORWARDED` header is used to discover the original requested URI |
| `X_FORWARDED` | The `io.helidon.http.Header#X_FORWARDED_PROTO`, `io.helidon.http.Header#X_FORWARDED_HOST`, `io.helidon.http.Header#X_FORWARDED_PORT`, `io.helidon.http.Header#X_FORWARDED_PREFIX` headers are used to discover the original requested URI |
| `HOST` | This is the default, only the `io.helidon.http.Header#HOST` header is used to discover requested URI |

See the [manifest](../config/manifest.md) for all available types.
