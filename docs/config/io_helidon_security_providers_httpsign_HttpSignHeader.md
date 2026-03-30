# io.helidon.security.providers.httpsign.HttpSignHeader

## Description

This type is an enumeration.

## Usages

## Allowed Values

| Value | Description |
|----|----|
| `SIGNATURE` | Creates (or validates) a "Signature" header |
| `AUTHORIZATION` | Creates (or validates) an "Authorization" header, that contains "Signature" as the beginning of its content (the rest of the header is the same as for `#SIGNATURE` |
| `CUSTOM` | Custom provided using a `io.helidon.security.util.TokenHandler` |

See the [manifest](../config/manifest.md) for all available types.
