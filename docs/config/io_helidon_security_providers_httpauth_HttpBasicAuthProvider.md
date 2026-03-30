# io.helidon.security.providers.httpauth.HttpBasicAuthProvider

## Description

HTTP Basic Authentication provider.

## Usages

- [`security.providers.http-basic-auth`](../config/io_helidon_security_spi_SecurityProvider.md#addbd3-http-basic-auth)
- [`server.features.security.security.providers.http-basic-auth`](../config/io_helidon_security_spi_SecurityProvider.md#addbd3-http-basic-auth)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a57c45-optional"></span> `optional` | `VALUE` | `Boolean` | `false` | Whether authentication is required |
| <span id="aade93-outbound"></span> [`outbound`](../config/io_helidon_security_providers_common_OutboundTarget.md) | `LIST` | `i.h.s.p.c.OutboundTarget` |   | Add a new outbound target to configure identity propagation or explicit username/password |
| <span id="aa4dbd-principal-type"></span> [`principal-type`](../config/io_helidon_security_SubjectType.md) | `VALUE` | `i.h.s.SubjectType` | `USER` | Principal type this provider extracts (and also propagates) |
| <span id="a9be1e-realm"></span> `realm` | `VALUE` | `String` | `helidon` | Set the realm to use when challenging users |
| <span id="a18d67-users"></span> [`users`](../config/io_helidon_security_providers_httpauth_ConfigUserStore_ConfigUser.md) | `LIST` | `i.h.s.p.h.C.ConfigUser` |   | Set user store to validate users |

See the [manifest](../config/manifest.md) for all available types.
