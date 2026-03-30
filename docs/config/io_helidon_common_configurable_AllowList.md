# io.helidon.common.configurable.AllowList

## Description

AllowList

defines a list of allowed and/or denied matches and tests if a particular value conforms to the conditions.

## Usages

- [`server.protocols.http_1_1.requested-uri-discovery.trusted-proxies`](../config/io_helidon_http_RequestedUriDiscoveryContext.md#acaa10-trusted-proxies)
- [`server.protocols.http_2.requested-uri-discovery.trusted-proxies`](../config/io_helidon_http_RequestedUriDiscoveryContext.md#acaa10-trusted-proxies)
- [`server.requested-uri-discovery.trusted-proxies`](../config/io_helidon_http_RequestedUriDiscoveryContext.md#acaa10-trusted-proxies)
- [`server.sockets.protocols.http_1_1.requested-uri-discovery.trusted-proxies`](../config/io_helidon_http_RequestedUriDiscoveryContext.md#acaa10-trusted-proxies)
- [`server.sockets.protocols.http_2.requested-uri-discovery.trusted-proxies`](../config/io_helidon_http_RequestedUriDiscoveryContext.md#acaa10-trusted-proxies)
- [`server.sockets.requested-uri-discovery.trusted-proxies`](../config/io_helidon_http_RequestedUriDiscoveryContext.md#acaa10-trusted-proxies)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a2e40a-allow-all"></span> `allow.all` | `VALUE` | `Boolean` | `false` | Allows all strings to match (subject to "deny" conditions) |
| <span id="a92614-allow-exact"></span> `allow.exact` | `LIST` | `String` |   | Exact strings to allow |
| <span id="a4ac9a-allow-pattern"></span> `allow.pattern` | `LIST` | `Pattern` |   | `Pattern`s specifying strings to allow |
| <span id="ab1da1-allow-prefix"></span> `allow.prefix` | `LIST` | `String` |   | Prefixes specifying strings to allow |
| <span id="aef91b-allow-suffix"></span> `allow.suffix` | `LIST` | `String` |   | Suffixes specifying strings to allow |
| <span id="a6423c-deny-exact"></span> `deny.exact` | `LIST` | `String` |   | Exact strings to deny |
| <span id="a60eff-deny-pattern"></span> `deny.pattern` | `LIST` | `Pattern` |   | Patterns specifying strings to deny |
| <span id="a06a3f-deny-prefix"></span> `deny.prefix` | `LIST` | `String` |   | Prefixes specifying strings to deny |
| <span id="aadd4a-deny-suffix"></span> `deny.suffix` | `LIST` | `String` |   | Suffixes specifying strings to deny |

See the [manifest](../config/manifest.md) for all available types.
