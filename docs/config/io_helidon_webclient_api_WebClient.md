# io.helidon.webclient.api.WebClient

## Description

WebClient configuration.

## Usages

- [`clients`](../config/config_reference.md#a7b703-clients)
- [`security.providers.idcs-role-mapper.oidc-config.webclient`](../config/io_helidon_security_providers_oidc_common_OidcConfig.md#a5fd1a-webclient)
- [`security.providers.oidc.webclient`](../config/io_helidon_security_providers_oidc_OidcProvider.md#a85467-webclient)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient`](../config/io_helidon_security_providers_oidc_common_OidcConfig.md#a5fd1a-webclient)
- [`server.features.security.security.providers.oidc.webclient`](../config/io_helidon_security_providers_oidc_OidcProvider.md#a85467-webclient)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a48ec2-connect-timeout"></span> `connect-timeout` | `VALUE` | `Duration` |   | Connect timeout |
| <span id="a5bc70-follow-redirects"></span> `follow-redirects` | `VALUE` | `Boolean` | `true` | Whether to follow redirects |
| <span id="a6536a-keep-alive"></span> `keep-alive` | `VALUE` | `Boolean` | `true` | Determines if connection keep alive is enabled (NOT socket keep alive, but HTTP connection keep alive, to re-use the same connection for multiple requests) |
| <span id="a04b74-max-redirects"></span> `max-redirects` | `VALUE` | `Integer` | `10` | Max number of followed redirects |
| <span id="a419a4-properties"></span> `properties` | `MAP` | `String` |   | Properties configured for this client |
| <span id="a3662c-protocol-configs"></span> [`protocol-configs`](../config/io_helidon_webclient_spi_ProtocolConfig.md) | `LIST` | `i.h.w.s.ProtocolConfig` |   | Configuration of client protocols |
| <span id="adcd34-protocol-configs-discover-services"></span> `protocol-configs-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `protocol-configs` |
| <span id="a23735-protocol-preference"></span> `protocol-preference` | `LIST` | `String` |   | List of HTTP protocol IDs by order of preference |
| <span id="a62d6a-proxy"></span> [`proxy`](../config/io_helidon_webclient_api_Proxy.md) | `VALUE` | `i.h.w.a.Proxy` |   | Proxy configuration to be used for requests |
| <span id="aecd9d-read-timeout"></span> `read-timeout` | `VALUE` | `Duration` |   | Read timeout |
| <span id="aba9ef-tls"></span> [`tls`](../config/io_helidon_common_tls_Tls.md) | `VALUE` | `i.h.c.t.Tls` |   | TLS configuration for any TLS request from this client |

See the [manifest](../config/manifest.md) for all available types.
