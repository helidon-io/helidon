# io.helidon.integrations.oci.tls.certificates.OciCertificatesTlsManager

## Description

Blueprint configuration for

OciCertificatesTlsManager

.

## Usages

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="ac9ca6-ca-ocid"></span> `ca-ocid` | `VALUE` | `String` | The Certificate Authority OCID |
| <span id="a0d50d-cert-ocid"></span> `cert-ocid` | `VALUE` | `String` | The Certificate OCID |
| <span id="a628a2-compartment-ocid"></span> `compartment-ocid` | `VALUE` | `String` | The OCID of the compartment the services are in |
| <span id="a7b951-key-ocid"></span> `key-ocid` | `VALUE` | `String` | The Key OCID |
| <span id="ab9e46-key-password"></span> `key-password` | `VALUE` | `Supplier` | The Key password |
| <span id="a9c7c4-schedule"></span> `schedule` | `VALUE` | `String` | The schedule for trigger a reload check, testing whether there is a new `io.helidon.common.tls.Tls` instance available |
| <span id="a159ff-vault-crypto-endpoint"></span> `vault-crypto-endpoint` | `VALUE` | `URI` | The address to use for the OCI Key Management Service / Vault crypto usage |
| <span id="a0fad0-vault-management-endpoint"></span> `vault-management-endpoint` | `VALUE` | `URI` | The address to use for the OCI Key Management Service / Vault management usage |

See the [manifest](../config/manifest.md) for all available types.
