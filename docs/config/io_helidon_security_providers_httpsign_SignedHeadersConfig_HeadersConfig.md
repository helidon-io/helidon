# io.helidon.security.providers.httpsign.SignedHeadersConfig.HeadersConfig

## Description

Configuration of headers to be signed.

## Usages

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a96327-always"></span> `always` | `LIST` | `String` | Headers that must be signed (and signature validation or creation should fail if not signed or present) |
| <span id="ae3160-if-present"></span> `if-present` | `LIST` | `String` | Headers that must be signed if present in request |
| <span id="a492f6-method"></span> `method` | `VALUE` | `String` | HTTP method this header configuration is bound to. If not present, it is considered default header configuration |

See the [manifest](../config/manifest.md) for all available types.
