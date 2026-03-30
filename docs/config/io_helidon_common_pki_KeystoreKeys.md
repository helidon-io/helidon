# io.helidon.common.pki.KeystoreKeys

## Description

Resources from a java keystore (PKCS12, JKS etc.).

## Usages

- [`clients.tls.private-key.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`clients.tls.trust.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`security.providers.oidc.webclient.tls.private-key.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`security.providers.oidc.webclient.tls.trust.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`server.features.security.security.providers.oidc.webclient.tls.private-key.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`server.features.security.security.providers.oidc.webclient.tls.trust.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`server.sockets.tls.private-key.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`server.sockets.tls.trust.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`server.tls.private-key.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)
- [`server.tls.trust.keystore`](../config/io_helidon_common_pki_Keys.md#ad6e47-keystore)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ae4695-cert-chain-alias"></span> `cert-chain.alias` | `VALUE` | `String` |   | Alias of an X.509 chain |
| <span id="aed8d8-cert-alias"></span> `cert.alias` | `VALUE` | `String` |   | Alias of X.509 certificate of public key |
| <span id="a9f401-key-alias"></span> `key.alias` | `VALUE` | `String` |   | Alias of the private key in the keystore |
| <span id="a6402c-key-passphrase"></span> `key.passphrase` | `VALUE` | `String` |   | Pass-phrase of the key in the keystore (used for private keys) |
| <span id="a6cd35-passphrase"></span> `passphrase` | `VALUE` | `String` |   | Pass-phrase of the keystore (supported with JKS and PKCS12 keystores) |
| <span id="a842d3-resource"></span> [`resource`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Keystore resource definition |
| <span id="a4c0fc-trust-store"></span> `trust-store` | `VALUE` | `Boolean` | `false` | If you want to build a trust store, call this method to add all certificates present in the keystore to certificate list |
| <span id="a6c130-type"></span> `type` | `VALUE` | `String` | `PKCS12` | Set type of keystore |

See the [manifest](../config/manifest.md) for all available types.
