# io.helidon.integrations.oci.sdk.runtime.OciConfig

## Description

Configuration used by

OciAuthenticationDetailsProvider

.

## Usages

- [`oci`](../config/config_reference.md#a111fa-oci)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a6010d-auth-strategies"></span> [`auth-strategies`](../config/io_helidon_integrations_oci_sdk_runtime_OciConfigAuthStrategies.md) | `LIST` | `i.h.i.o.s.r.OciConfigAuthStrategies` |   | The list of authentication strategies that will be attempted by `com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider` when one is called for |
| <span id="a39bed-auth-strategy"></span> [`auth-strategy`](../config/io_helidon_integrations_oci_sdk_runtime_OciConfigAuthStrategy.md) | `VALUE` | `i.h.i.o.s.r.OciConfigAuthStrategy` |   | The singular authentication strategy to apply |
| <span id="a44ef8-auth-fingerprint"></span> `auth.fingerprint` | `VALUE` | `String` |   | The OCI authentication fingerprint |
| <span id="a49640-auth-keyFile"></span> `auth.keyFile` | `VALUE` | `String` | `oci_api_key.pem` | The OCI authentication key file |
| <span id="aca4e4-auth-passphrase"></span> `auth.passphrase` | `VALUE` | `String` |   | The OCI authentication passphrase |
| <span id="a3eef2-auth-private-key"></span> `auth.private-key` | `VALUE` | `String` |   | The OCI authentication private key |
| <span id="accf2a-auth-private-key-path"></span> `auth.private-key-path` | `VALUE` | `String` |   | The OCI authentication key file path |
| <span id="a1f699-auth-region"></span> `auth.region` | `VALUE` | `String` |   | The OCI region |
| <span id="ae0f35-auth-tenant-id"></span> `auth.tenant-id` | `VALUE` | `String` |   | The OCI tenant id |
| <span id="af7284-auth-user-id"></span> `auth.user-id` | `VALUE` | `String` |   | The OCI user id |
| <span id="ae0ae5-config-path"></span> `config.path` | `VALUE` | `String` |   | The OCI configuration profile path |
| <span id="ae48e3-config-profile"></span> `config.profile` | `VALUE` | `String` | `DEFAULT` | The OCI configuration/auth profile name |
| <span id="ade0be-imds-hostname"></span> `imds.hostname` | `VALUE` | `String` | `169.254.169.254` | The OCI IMDS hostname |
| <span id="a5da8e-imds-timeout-milliseconds"></span> `imds.timeout.milliseconds` | `VALUE` | `Duration` | `PT0.1S` | The OCI IMDS connection timeout |

See the [manifest](../config/manifest.md) for all available types.
