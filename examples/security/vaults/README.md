Vaults example
----

This example demonstrates the use of `Security` to:
- access secrets
- generate digests (such as Signatures and HMAC)
- verify digests (dtto)
- encrypt secret text
- decrypt cipher text

The example uses three implementations of security providers that implement these features:

1. OCI Vault provider (supports all)
2. Config provider (supports encryption/decryption and secrets)
3. Hashicorp (HCP) Vault provider (supports all + HMAC digest)


# OCI Vault

The following information/configuration is needed:

1. `~/.oci/config` file should be present (TODO link to description how to get it)
2. A secret (for password) must be created and its OCID configured in `${oci.properties.secret-ocid}`
3. An RSA key must be created and its OCID configured in `${oci.properties.vault-rsa-key-ocid}` for signature
4. Key must be created and its OCID configured in `${oci.properties.vault-key-ocid}` for encryption

# HCP Vault

1. Vault address must be defined in `vault.address`
2. Vault token must be defined in `vault.token`
3. A secret must be defined in the default secrets under path `app/secret` with key `username` defining a user
4. Vault `transit` secret engine must be enabled
5. A key named `signature-key` must be created (RSA) in `transit` secret engine for signature
6. A key named `encryption-key` must be created in `transit` secret engine for encryption and HMAC
