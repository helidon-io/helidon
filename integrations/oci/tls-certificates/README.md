# Helidon OCI Certificates TLS Managers

This module provides two TLS managers backed by
[OCI Certificates](https://docs.oracle.com/en-us/iaas/Content/certificates/home.htm):

- `OciCertificatesTlsManager` preserves the existing integration, loading the public certificate bundle from
  OCI Certificates and exporting a separately configured software-protected private key through OCI Key Management
  Service.
- `OciCertificateBundleTlsManager` loads the leaf certificate, chain, and matching private key from one OCI-managed
  certificate bundle.

Add the module to the application:

```xml
<dependency>
    <groupId>io.helidon.integrations.oci</groupId>
    <artifactId>helidon-integrations-oci-tls-certificates</artifactId>
</dependency>
```

## OCI-managed certificate bundle

Use an OCI-issued certificate whose private key is stored by OCI Certificates. An imported or externally managed
certificate that exposes only a public bundle cannot be used by this manager.

```yaml
server:
  sockets:
    - name: secured
      port: 8443
      tls:
        manager:
          oci-certificate-bundle-tls-manager:
            schedule: "0/30 * * * * ? *"
            ca-ocid: ${CA_OCID}
            cert-ocid: ${SERVER_CERT_OCID}
```

The manager requests the `CURRENT` bundle as `CERTIFICATE_CONTENT_WITH_PRIVATE_KEY`. It verifies that the returned
private key matches the leaf certificate before installing the identity. Both RSA and EC PKCS#8 keys are supported,
including passphrase-protected keys; an OCI-provided passphrase is used only while decoding that bundle.

By default, polling does not reload TLS when both the certificate version and CA certificate are unchanged. A newer
identity version or independently rotated CA is installed as one complete TLS update. If download, parsing, validation,
or reload fails, the last successfully installed TLS material remains active and the candidate update is retried on a
later poll. Set `always-reload: true` to rebuild TLS even when both values are unchanged.

The leaf private key is materialized in application JVM memory. This manager does not provide non-exportable HSM-backed
TLS signing; the CA signing key can remain separately HSM protected.

The workload needs permission to read the configured private leaf bundle and CA bundle. Restrict private-bundle access
to the intended leaf certificate where practical, for example:

```text
Allow dynamic-group <dynamic-group> to read leaf-certificate-bundles in compartment <compartment>
  where all {target.leaf-certificate.id = '<leaf-certificate-ocid>',
             target.leaf-certificate.bundle-type = 'CERTIFICATE_CONTENT_WITH_PRIVATE_KEY'}
Allow dynamic-group <dynamic-group> to read certificate-authority-bundles in compartment <compartment>
```

See the OCI documentation for
[certificate IAM policies](https://docs.oracle.com/en-us/iaas/Content/Identity/policyreference/certificatespolicyreference.htm),
[viewing certificate bundles](https://docs.oracle.com/en-us/iaas/Content/certificates/viewing-certificate-version-bundle.htm),
and [automatic renewal](https://docs.oracle.com/en-us/iaas/Content/certificates/renewing-certificate.htm).

## Vault-exported private key

Existing `oci-certificates-tls-manager` configuration and programmatic builder usage remain unchanged. The certificate
and configured Vault key must represent the same TLS identity, and the Vault leaf key must be software protected and
exportable.

```yaml
server:
  sockets:
    - name: secured
      port: 8443
      tls:
        manager:
          oci-certificates-tls-manager:
            schedule: "0/30 * * * * ? *"
            vault-crypto-endpoint: ${VAULT_CRYPTO_ENDPOINT}
            ca-ocid: ${CA_OCID}
            cert-ocid: ${SERVER_CERT_OCID}
            key-ocid: ${SERVER_KEY_OCID}
            key-password: ${SERVER_KEY_PASSWORD}
```

The distinct `oci-certificate-bundle-tls-manager` key is intentional on Helidon 4.x. It adds the new OCI-managed
private-key flow without changing the public getters or required options of the already released Vault configuration.

## Example

See [examples/microprofile/oci-tls-certificates](../../../examples/microprofile/oci-tls-certificates) for more.
