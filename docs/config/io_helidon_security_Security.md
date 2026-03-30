# io.helidon.security.Security

## Description

Configuration of security providers, integration and other security options.

## Usages

- [`security`](../config/config_reference.md#ac4d0b-security)
- [`server.features.security.security`](../config/io_helidon_webserver_security_SecurityFeature.md#ad067b-security)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aee5a9-default-authentication-provider"></span> `default-authentication-provider` | `VALUE` | `String` |   | ID of the default authentication provider |
| <span id="a45bf6-default-authorization-provider"></span> `default-authorization-provider` | `VALUE` | `String` |   | ID of the default authorization provider |
| <span id="af885d-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Security can be disabled using configuration, or explicitly |
| <span id="a53e2d-environment-server-time"></span> [`environment.server-time`](../config/io_helidon_security_SecurityTime.md) | `VALUE` | `i.h.s.SecurityTime` |   | Server time to use when evaluating security policies that depend on time |
| <span id="a0600a-provider-policy-class-name"></span> `provider-policy.class-name` | `VALUE` | `Class` |   | Provider selection policy class name, only used when type is set to CLASS |
| <span id="a28411-provider-policy-type"></span> [`provider-policy.type`](../config/io_helidon_security_ProviderSelectionPolicyType.md) | `VALUE` | `i.h.s.ProviderSelectionPolicyType` | `FIRST` | Type of the policy |
| <span id="a56406-providers"></span> [`providers`](../config/io_helidon_security_spi_SecurityProvider.md) | `LIST` | `i.h.s.s.SecurityProvider` |   | Add a provider, works as `#addProvider(io.helidon.security.spi.SecurityProvider, String)`, where the name is set to `Class#getSimpleName()` |
| <span id="aaaeab-secrets"></span> `secrets` | `LIST` | `i.h.c.Config` |   | Configured secrets |
| <span id="aeab91-secrets---config"></span> [`secrets.*.config`](../config/io_helidon_security_SecretsProviderConfig.md) | `VALUE` | `i.h.s.SecretsProviderConfig` |   | Configuration specific to the secret provider |
| <span id="a3969f-secrets---name"></span> `secrets.*.name` | `VALUE` | `String` |   | Name of the secret, used for lookup |
| <span id="a0fd52-secrets---provider"></span> `secrets.*.provider` | `VALUE` | `String` |   | Name of the secret provider |
| <span id="ab15fc-tracing-enabled"></span> `tracing.enabled` | `VALUE` | `Boolean` | `true` | Whether or not tracing should be enabled |

See the [manifest](../config/manifest.md) for all available types.
