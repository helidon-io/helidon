# io.helidon.integrations.oci.SessionTokenMethodConfig

## Description

Configuration of the

config

authentication method.

## Usages

- [`helidon.oci.authentication.session-token`](../config/io_helidon_integrations_oci_OciConfig.md#a6869c-authentication-session-token)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a6560b-fingerprint"></span> `fingerprint` | `VALUE` | `String` | The OCI authentication fingerprint |
| <span id="a6e207-initial-refresh-delay"></span> `initial-refresh-delay` | `VALUE` | `Duration` | Delay of the first refresh |
| <span id="ac0d12-passphrase"></span> `passphrase` | `VALUE` | `String` | The OCI authentication passphrase |
| <span id="ab5735-private-key-path"></span> `private-key-path` | `VALUE` | `Path` | The OCI authentication private key resource |
| <span id="a55e75-refresh-period"></span> `refresh-period` | `VALUE` | `Duration` | Refresh period, i.e |
| <span id="a16f15-region"></span> `region` | `VALUE` | `String` | The OCI region |
| <span id="a8a81f-session-lifetime-hours"></span> `session-lifetime-hours` | `VALUE` | `Long` | Maximal lifetime of a session |
| <span id="a4ccd6-session-token"></span> `session-token` | `VALUE` | `String` | Session token value |
| <span id="a5ad8b-session-token-path"></span> `session-token-path` | `VALUE` | `Path` | Session token path |
| <span id="ac1438-tenant-id"></span> `tenant-id` | `VALUE` | `String` | The OCI tenant id |
| <span id="af0f17-user-id"></span> `user-id` | `VALUE` | `String` | The OCI user id |

See the [manifest](../config/manifest.md) for all available types.
