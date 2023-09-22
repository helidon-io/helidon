# Helidon Integrations for OCI Key Manager Service

This module contains the **_OciKmsTlsManager_** provider that offers lifecycle and rotation of certificates to be used with Helidon Tls when configured. It is designed specifically to integrate with [Oracle Cloud Infrastructure](https://www.oracle.com/cloud)'s [Certificates](https://www.oracle.com/security/cloud-security/ssl-tls-certificates) Service.

## Usage
Integrating with OCI's Certificates Service from Helidon is a simple matter of configuration.

First, use OCI's Certificates Service to create your certificates. Follow the directions [here](https://docs.oracle.com/en-us/iaas/Content/certificates/home.htm).

In your pom.xml, include a dependency to this module.

In your application.yaml configuration, include a reference to the oci-certificates Tls Manager.

```yaml
...
  tls:
    manager:
      oci-certificates:
        # Download tls context each 30 seconds
        schedule: 0/30 * * * * ? *
        
        vault-crypto-endpoint: https://...

        ca-ocid: ${CA_OCID}
        cert-ocid: ${SERVER_CERT_OCID}
        key-ocid: ${SERVER_KEY_OCID}
        key-pass: TODO
...
```
