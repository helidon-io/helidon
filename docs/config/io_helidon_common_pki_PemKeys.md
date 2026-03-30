# io.helidon.common.pki.PemKeys

## Description

PEM files based keys - accepts private key and certificate chain.

## Usages

- [`clients.tls.private-key.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`clients.tls.trust.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`security.providers.oidc.webclient.tls.private-key.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`security.providers.oidc.webclient.tls.trust.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`server.features.security.security.providers.oidc.webclient.tls.private-key.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`server.features.security.security.providers.oidc.webclient.tls.trust.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`server.sockets.tls.private-key.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`server.sockets.tls.trust.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`server.tls.private-key.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)
- [`server.tls.trust.pem`](../config/io_helidon_common_pki_Keys.md#a0102e-pem)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a5c2aa-cert-chain-resource"></span> [`cert-chain.resource`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` | Load certificate chain from PEM resource |
| <span id="a09e86-certificates-resource"></span> [`certificates.resource`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` | Read one or more certificates in PEM format from a resource definition |
| <span id="a954fb-key-passphrase"></span> `key.passphrase` | `VALUE` | `String` | Passphrase for private key |
| <span id="a29d2c-key-resource"></span> [`key.resource`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` | Read a private key from PEM format from a resource definition |
| <span id="a045e2-public-key-resource"></span> [`public-key.resource`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` | Read a public key from PEM format from a resource definition |

See the [manifest](../config/manifest.md) for all available types.
