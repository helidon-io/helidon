# io.helidon.integrations.oci.OciConfig

## Description

Meta configuration of OCI integration for Helidon.

## Usages

- [`helidon.oci`](../config/config_reference.md#a4b5dd-helidon-oci)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aacd40-allowed-authentication-methods"></span> `allowed-authentication-methods` | `LIST` | `String` |   | List of attempted authentication strategies in case `io.helidon.integrations.oci.OciConfig#authenticationMethod()` is set to `#AUTHENTICATION_METHOD_AUTO` |
| <span id="aeb9fb-authentication-method"></span> `authentication-method` | `VALUE` | `String` | `auto` | Authentication method to use |
| <span id="ad3c68-authentication-timeout"></span> `authentication-timeout` | `VALUE` | `Duration` | `PT10S` | Timeout of authentication operations, where applicable |
| <span id="ad6046-authentication-config"></span> [`authentication.config`](../config/io_helidon_integrations_oci_ConfigMethodConfig.md) | `VALUE` | `i.h.i.o.ConfigMethodConfig` |   | Config method configuration (if provided and used) |
| <span id="a505ba-authentication-config-file"></span> [`authentication.config-file`](../config/io_helidon_integrations_oci_ConfigFileMethodConfig.md) | `VALUE` | `i.h.i.o.ConfigFileMethodConfig` |   | Config file method configuration (if provided and used) |
| <span id="a6869c-authentication-session-token"></span> [`authentication.session-token`](../config/io_helidon_integrations_oci_SessionTokenMethodConfig.md) | `VALUE` | `i.h.i.o.SessionTokenMethodConfig` |   | Session token method configuration (if provided and used) |
| <span id="a188d4-federation-endpoint"></span> `federation-endpoint` | `VALUE` | `URI` |   | Customization of federation endpoint for authentication providers |
| <span id="a9ebd7-imds-base-uri"></span> `imds-base-uri` | `VALUE` | `URI` |   | The OCI IMDS URI (http URL pointing to the metadata service, if customization needed) |
| <span id="a65c62-imds-detect-retries"></span> `imds-detect-retries` | `VALUE` | `Integer` |   | Customize the number of retries to contact IMDS service |
| <span id="afd2c2-imds-timeout"></span> `imds-timeout` | `VALUE` | `Duration` | `PT1S` | The OCI IMDS connection timeout |
| <span id="ab8217-region"></span> `region` | `VALUE` | `i.h.i.o.OciConfigSupport` |   | Explicit region |
| <span id="a3b76d-tenant-id"></span> `tenant-id` | `VALUE` | `String` |   | OCI tenant id for Instance Principal, Resource Principal or OKE Workload |

See the [manifest](../config/manifest.md) for all available types.
