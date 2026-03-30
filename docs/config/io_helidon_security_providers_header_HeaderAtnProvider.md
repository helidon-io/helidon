# io.helidon.security.providers.header.HeaderAtnProvider

## Description

Security provider that extracts a username (or service name) from a header.

## Usages

- [`security.providers.header-atn`](../config/io_helidon_security_spi_SecurityProvider.md#a58e34-header-atn)
- [`server.features.security.security.providers.header-atn`](../config/io_helidon_security_spi_SecurityProvider.md#a58e34-header-atn)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a9672f-atn-token"></span> [`atn-token`](../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Token handler to extract username from request |
| <span id="a25377-authenticate"></span> `authenticate` | `VALUE` | `Boolean` | `true` | Whether to authenticate requests |
| <span id="adbdf3-optional"></span> `optional` | `VALUE` | `Boolean` | `false` | Whether authentication is required |
| <span id="aa4f36-outbound"></span> [`outbound`](../config/io_helidon_security_providers_common_OutboundTarget.md) | `LIST` | `i.h.s.p.c.OutboundTarget` |   | Configure outbound target for identity propagation |
| <span id="ad5021-outbound-token"></span> [`outbound-token`](../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Token handler to create outbound headers to propagate identity |
| <span id="aa8e94-principal-type"></span> [`principal-type`](../config/io_helidon_security_SubjectType.md) | `VALUE` | `i.h.s.SubjectType` | `USER` | Principal type this provider extracts (and also propagates) |
| <span id="a0309f-propagate"></span> `propagate` | `VALUE` | `Boolean` | `false` | Whether to propagate identity |

See the [manifest](../config/manifest.md) for all available types.
