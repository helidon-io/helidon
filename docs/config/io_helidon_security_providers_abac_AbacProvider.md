# io.helidon.security.providers.abac.AbacProvider

## Description

Attribute Based Access Control provider.

## Usages

- [`security.providers.abac`](../config/io_helidon_security_spi_SecurityProvider.md#a4ca40-abac)
- [`server.features.security.security.providers.abac`](../config/io_helidon_security_spi_SecurityProvider.md#a4ca40-abac)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a4f520-fail-if-none-validated"></span> `fail-if-none-validated` | `VALUE` | `Boolean` | `true` | Whether to fail if NONE of the attributes is validated |
| <span id="a52725-fail-on-unvalidated"></span> `fail-on-unvalidated` | `VALUE` | `Boolean` | `true` | Whether to fail if any attribute is left unvalidated |

See the [manifest](../config/manifest.md) for all available types.
