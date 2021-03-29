Vault
----

Integration with Hashicorp Vault and with vaults compatible with its API.


# Design

Why a custom implementation?
The `com.bettercloud:vault-java-driver` is dependency free, though it implements its own JSON parsing and HTTP client libraries.
As we have both of these libraries as part of Helidon, such code is a risk. Also we need a non-blocking reactive client, which is not implemented by that library.

This implementation is based on Java Service Loader to load implementations of the vault's SPIs
- `io.helidon.integrations.vault.spi.SecretsEngineProvider` - secret engines (kv1, kv2, transit, PKI)
- `io.helidon.integrations.vault.spi.AuthMethodProvider` - authentication methods (k8s, AppRole, token) - to provide API to manage such methods
- `io.helidon.integrations.vault.spi.SysProvider` - sys operations (enabling engines and such)
- `io.helidon.integrations.vault.spi.VaultAuth` - authenticating the client (k8s, AppRole, token) - internal implementations that allow us to connect configuration free (k8s) or with different methods (AppRole)

The most commonly used engines are supported by Helidon (and we may add additional engines in the future):

- KV v2 engine - the current versioned Key/Value pair engine
- KV v1 engine - the deprecated Key/Value pair engine
- Cubbyhole - the per token secret provider engine
- Database - for obtaining connection data to a database
- PKI - issuing certificates
- Transit - encryption, signatures, HMAC

Not all available APIs are implemented in the first release of Vault integration.


# API

The main access point is `Vault` class using a builder pattern.

Example of obtaining vault instance using a token:
```java
 Vault vault = Vault.builder()
                .address("http://localhost:8200")
                .token("s.oZZcsMzbasmwNqfAxPZOs8jw")
                .build();
```
