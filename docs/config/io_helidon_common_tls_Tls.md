# io.helidon.common.tls.Tls

## Description

TLS configuration - common for server and client.

## Usages

- [`clients.tls`](../config/io_helidon_webclient_api_WebClient.md#aba9ef-tls)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls`](../config/io_helidon_webclient_api_WebClient.md#aba9ef-tls)
- [`security.providers.oidc.webclient.tls`](../config/io_helidon_webclient_api_WebClient.md#aba9ef-tls)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls`](../config/io_helidon_webclient_api_WebClient.md#aba9ef-tls)
- [`server.features.security.security.providers.oidc.webclient.tls`](../config/io_helidon_webclient_api_WebClient.md#aba9ef-tls)
- [`server.sockets.tls`](../config/io_helidon_webserver_ListenerConfig.md#aed6f6-tls)
- [`server.tls`](../config/io_helidon_webserver_WebServer.md#ac9efa-tls)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a6e5c3-cipher-suite"></span> `cipher-suite` | `LIST` | `String` |   | Enabled cipher suites for TLS communication |
| <span id="aa9957-client-auth"></span> [`client-auth`](../config/io_helidon_common_tls_TlsClientAuth.md) | `VALUE` | `i.h.c.t.TlsClientAuth` | `NONE` | Configure requirement for mutual TLS |
| <span id="ab3264-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Flag indicating whether Tls is enabled |
| <span id="a734ef-endpoint-identification-algorithm"></span> `endpoint-identification-algorithm` | `VALUE` | `String` | `HTTPS` | Identification algorithm for SSL endpoints |
| <span id="a4eeba-internal-keystore-provider"></span> `internal-keystore-provider` | `VALUE` | `String` |   | Provider of the key stores used internally to create a key and trust manager factories |
| <span id="ab7ae6-internal-keystore-type"></span> `internal-keystore-type` | `VALUE` | `String` |   | Type of the key stores used internally to create a key and trust manager factories |
| <span id="a93230-key-manager-factory-algorithm"></span> `key-manager-factory-algorithm` | `VALUE` | `String` |   | Algorithm of the key manager factory used when private key is defined |
| <span id="a49b7a-manager"></span> [`manager`](../config/io_helidon_common_tls_TlsManager.md) | `VALUE` | `i.h.c.t.TlsManager` |   | The Tls manager |
| <span id="a7cad5-manager-discover-services"></span> `manager-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `manager` |
| <span id="aeed7c-private-key"></span> [`private-key`](../config/io_helidon_common_pki_Keys.md) | `VALUE` | `i.h.c.p.Keys` |   | Private key to use |
| <span id="a910b8-protocol"></span> `protocol` | `VALUE` | `String` | `TLS` | Configure the protocol used to obtain an instance of `javax.net.ssl.SSLContext` |
| <span id="aef2f6-protocols"></span> `protocols` | `LIST` | `String` |   | Enabled protocols for TLS communication |
| <span id="a0da60-provider"></span> `provider` | `VALUE` | `String` |   | Use explicit provider to obtain an instance of `javax.net.ssl.SSLContext` |
| <span id="a7a660-revocation"></span> [`revocation`](../config/io_helidon_common_tls_RevocationConfig.md) | `VALUE` | `i.h.c.t.RevocationConfig` |   | Certificate revocation check configuration |
| <span id="ab9360-secure-random-algorithm"></span> `secure-random-algorithm` | `VALUE` | `String` |   | Algorithm to use when creating a new secure random |
| <span id="a82d0c-secure-random-provider"></span> `secure-random-provider` | `VALUE` | `String` |   | Provider to use when creating a new secure random |
| <span id="a59f4a-session-cache-size"></span> `session-cache-size` | `VALUE` | `Integer` | `20480` | SSL session cache size |
| <span id="abf0bb-session-timeout"></span> `session-timeout` | `VALUE` | `Duration` | `PT24H` | SSL session timeout |
| <span id="adbc4b-trust"></span> [`trust`](../config/io_helidon_common_pki_Keys.md) | `LIST` | `i.h.c.p.Keys` |   | List of certificates that form the trust manager |
| <span id="a0346e-trust-all"></span> `trust-all` | `VALUE` | `Boolean` | `false` | Trust any certificate provided by the other side of communication |
| <span id="af626f-trust-manager-factory-algorithm"></span> `trust-manager-factory-algorithm` | `VALUE` | `String` |   | Trust manager factory algorithm |

See the [manifest](../config/manifest.md) for all available types.
