# io.helidon.security.providers.httpsign.HttpSignProvider

## Description

HTTP header signature provider.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ac1d34-backward-compatible-eol"></span> `backward-compatible-eol` | `VALUE` | `Boolean` | `false` | Enable support for Helidon versions before 3.0.0 (exclusive) |
| <span id="acc108-headers"></span> [`headers`](../config/io_helidon_security_providers_httpsign_HttpSignHeader.md) | `LIST` | `i.h.s.p.h.HttpSignHeader` |   | Add a header that is validated on inbound requests |
| <span id="abbc62-inbound-keys"></span> [`inbound.keys`](../config/io_helidon_security_providers_httpsign_InboundClientDefinition.md) | `LIST` | `i.h.s.p.h.InboundClientDefinition` |   | Add inbound configuration |
| <span id="a9cb96-optional"></span> `optional` | `VALUE` | `Boolean` | `true` | Set whether the signature is optional |
| <span id="af2400-outbound"></span> [`outbound`](../config/io_helidon_security_providers_common_OutboundConfig.md) | `VALUE` | `i.h.s.p.c.OutboundConfig` |   | Add outbound targets to this builder |
| <span id="a4938a-realm"></span> `realm` | `VALUE` | `String` | `helidon` | Realm to use for challenging inbound requests that do not have "Authorization" header in case header is `HttpSignHeader#AUTHORIZATION` and singatures are not optional |
| <span id="a4ba7d-sign-headers"></span> [`sign-headers`](../config/io_helidon_security_providers_httpsign_SignedHeadersConfig_HeadersConfig.md) | `LIST` | `i.h.s.p.h.S.HeadersConfig` |   | Override the default inbound required headers (e.g |

See the [manifest](../config/manifest.md) for all available types.
