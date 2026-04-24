# HashiCorp Vault

## Overview

HashiCorp Vault is a commonly used Vault in many microservices. The APIs are REST-based and Helidon implements them using [WebClient](../../se/webclient.md).

## Maven Coordinates

To enable HashiCorp Vault, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.integrations.vault</groupId>
    <artifactId>helidon-integrations-vault-cdi</artifactId>
</dependency>
```

The following is a list of maven coordinates of all Vault modules available:

```xml
<dependencies>
    <dependency>
        <groupId>io.helidon.integrations.vault.auths</groupId>
        <artifactId>helidon-integrations-vault-auths-token</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.integrations.vault.auths</groupId>
        <artifactId>helidon-integrations-vault-auths-approle</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.integrations.vault.auths</groupId>
        <artifactId>helidon-integrations-vault-auths-k8s</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.integrations.vault.secrets</groupId>
        <artifactId>helidon-integrations-vault-secrets-kv1</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.integrations.vault.secrets</groupId>
        <artifactId>helidon-integrations-vault-secrets-kv2</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.integrations.vault.secrets</groupId>
        <artifactId>helidon-integrations-vault-secrets-cubbyhole</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.integrations.vault.secrets</groupId>
        <artifactId>helidon-integrations-vault-secrets-transit</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.integrations.vault.secrets</groupId>
        <artifactId>helidon-integrations-vault-secrets-database</artifactId>
    </dependency>
    <dependency>
        <groupId>io.helidon.integrations.vault.sys</groupId>
        <artifactId>helidon-integrations-vault-sys</artifactId>
    </dependency>
</dependencies>
```

## Usage

Vault integration supports the following:

- **Secret Engines**: Key/Value version 2, Key/Value version 1, Cubbyhole, PKI, Transit, Database
- **Authentication Methods**: Token, Kubernetes (k8s), AppRole
- **Other Sys Operations and Configurations**

Each of these features is implemented as a separate module, with the Vault class binding them together. In Helidon MP, with injection, this binding is done automatically, and you can simply inject your favorite secret engine.

The following classes can be injected into any CDI bean (if appropriate module is on the classpath):

- Kv2Secrets - Key/Value Version 2 Secrets (versioned secrets, default)
- Kv1Secrets - Key/Value Version 1 Secrets (un-versioned secrets, legacy)
- CubbyholeSecrets - Cubbyhole secrets (token bound secrets)
- DbSecrets - Database secrets (for generating temporary DB credentials)
- PkiSecrets - PKI secrets (for generating keys and X.509 certificates)
- TransitSecrets - Transit operations (encryption, signatures, HMAC)
- AppRoleAuth - AppRole authentication method (management operations)
- K8sAuth - Kubernetes authentication method (management operations)
- TokenAuth - Token authentication method (management operations)
- Sys - System operations (management of Vault - enabling/disabling secret engines and authentication methods)

In addition to these features, Vault itself can be authenticated as follows:

- Token authentication - token is configured when connecting to Vault

<!-- -->

    vault.address=http://localhost:8200
    vault.token=my-token

- AppRole authentication - AppRole ID and secret ID are configured, integration exchanges these for a temporary token that is used to connect to Vault

<!-- -->

    vault.auth.app-role.role-id=app-role-id
    vault.auth.app-role.secret-id=app-role-secret-id

- K8s authentication - the k8s JWT token is discovered on current node and used to obtain a temporary token that is used to connect to Vault

<!-- -->

    vault.auth.k8s.token-role=my-role 

- The token role must be configured in Vault

### Extensibility

New secret engines and authentication methods can be implemented quite easily, as the integration is based on service providers (using ServiceLoader). This gives us (or you, as the users) the option to add new secret engines and/or authentication methods without adding a plethora of methods to the Vault class.

See the following SPIs:

```java
io.helidon.integrations.vault.spi.AuthMethodProvider
io.helidon.integrations.vault.spi.SecretsEngineProvider
io.helidon.integrations.vault.spi.SysProvider
io.helidon.integrations.vault.spi.VaultAuth
io.helidon.integrations.vault.spi.InjectionProvider
```

## Examples

The following example shows usage of Vault to encrypt a secret using the default Vault configuration (in a JAX-RS resource):

```java
@Path("/transit")
class TransitResource {
    private final TransitSecrets secrets;

    @Inject
    TransitResource(TransitSecrets secrets) {
        this.secrets = secrets;
    }

    @Path("/encrypt/{secret: .*}")
    @GET
    public String encrypt(@PathParam("secret") String secret) {
        return secrets.encrypt(Encrypt.Request.builder()
                                       .encryptionKeyName(ENCRYPTION_KEY)
                                       .data(Base64Value.create(secret)))
                .encrypted()
                .cipherText();
    }
}
```

### Cubbyhole secrets

Cubbyhole example:

```java
@Path("/cubbyhole")
public class CubbyholeResource {
    private final CubbyholeSecrets secrets;

    @Inject
    CubbyholeResource(CubbyholeSecrets secrets) {
        this.secrets = secrets;
    }

    @POST
    @Path("/secrets/{path: .*}")
    public Response createSecret(@PathParam("path") String path, String secret) { 
        CreateCubbyhole.Response response = secrets.create(path, Map.of("secret", secret));

        return Response.ok()
                .entity(String.format(
                        "Created secret on path: %s. , key is \"secret\", original status: %d",
                        path,
                        response.status().code()))
                .build();
    }

    @DELETE
    @Path("/secrets/{path: .*}")
    public Response deleteSecret(@PathParam("path") String path) { 
        DeleteCubbyhole.Response response = secrets.delete(path);

        return Response.ok()
                .entity(String.format(
                        "Deleted secret on path: %s. Original status: %d",
                        path,
                        response.status().code()))
                .build();
    }

    @GET
    @Path("/secrets/{path: .*}")
    public Response getSecret(@PathParam("path") String path) { 
        Optional<Secret> secret = secrets.get(path);

        if (secret.isPresent()) {
            Secret kv1Secret = secret.get();
            return Response.ok()
                    .entity("Secret: " + secret.get().values().toString())
                    .build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
```

- Create a secret from request entity, the name of the value is `secret`.
- Delete the secret on a specified path.
- Get the secret on a specified path.

### KV1 secrets

Key/Value version 1 secrets engine operations:

```java
@Path("/kv1")
public class Kv1Resource {
    private final Sys sys;
    private final Kv1Secrets secrets;

    @Inject
    Kv1Resource(Sys sys, Kv1Secrets secrets) {
        this.sys = sys;
        this.secrets = secrets;
    }

    @Path("/engine")
    @GET
    public Response enableEngine() { 
        EnableEngine.Response response = sys.enableEngine(Kv1Secrets.ENGINE);

        return Response.ok()
                .entity("Key/value version 1 secret engine is now enabled."
                        + " Original status: " + response.status().code())
                .build();
    }

    @Path("/engine")
    @DELETE
    public Response disableEngine() { 
        DisableEngine.Response response = sys.disableEngine(Kv1Secrets.ENGINE);
        return Response.ok()
                .entity("Key/value version 1 secret engine is now disabled."
                        + " Original status: " + response.status().code())
                .build();
    }

    @POST
    @Path("/secrets/{path: .*}")
    public Response createSecret(@PathParam("path") String path, String secret) { 
        CreateKv1.Response response = secrets.create(path, Map.of("secret", secret));

        return Response.ok()
                .entity(String.format(
                        "Created secret on path: %s, key is \"secret\", original status: %d",
                        path,
                        response.status().code()))
                .build();
    }

    @DELETE
    @Path("/secrets/{path: .*}")
    public Response deleteSecret(@PathParam("path") String path) { 
        DeleteKv1.Response response = secrets.delete(path);

        return Response.ok()
                .entity(String.format(
                        "Deleted secret on path: %s. Original status: %d",
                        path,
                        response.status().code()))
                .build();
    }

    @GET
    @Path("/secrets/{path: .*}")
    public Response getSecret(@PathParam("path") String path) { 
        Optional<Secret> secret = secrets.get(path);

        if (secret.isPresent()) {
            Secret kv1Secret = secret.get();
            return Response.ok()
                    .entity("Secret: " + secret.get().values().toString())
                    .build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
```

- Enable the secrets engine on the default path.
- Disable the secrets engine on the default path.
- Create a secret from request entity, the name of the value is `secret`.
- Delete the secret on a specified path.
- Get the secret on a specified path.

### KV2 secrets

Key/Value version 2 secrets engine operations:

```java
@Path("/kv2")
public class Kv2Resource {
    private final Kv2Secrets secrets;

    @Inject
    Kv2Resource(@VaultName("app-role")
                @VaultPath("custom") Kv2Secrets secrets) {
        this.secrets = secrets;
    }

    @POST
    @Path("/secrets/{path: .*}")
    public Response createSecret(@PathParam("path") String path, String secret) { 
        CreateKv2.Response response = secrets.create(path, Map.of("secret", secret));
        return Response.ok()
                .entity(String.format(
                        "Created secret on path: %s, key is \"secret\", original status: %d",
                        path,
                        response.status().code()))
                .build();
    }

    @DELETE
    @Path("/secrets/{path: .*}")
    public Response deleteSecret(@PathParam("path") String path) { 
        DeleteAllKv2.Response response = secrets.deleteAll(path);
        return Response.ok()
                .entity(String.format(
                        "Deleted secret on path: %s. Original status: %d",
                        path,
                        response.status().code()))
                .build();
    }

    @GET
    @Path("/secrets/{path: .*}")
    public Response getSecret(@PathParam("path") String path) { 

        Optional<Kv2Secret> secret = secrets.get(path);

        if (secret.isPresent()) {
            Kv2Secret kv2Secret = secret.get();
            return Response.ok()
                    .entity(String.format(
                            "Version %s, secret: %s",
                            kv2Secret.metadata().version(),
                            kv2Secret.values()))
                    .build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
```

- Create a secret from request entity, the name of the value is `secret`.
- Delete the secret on a specified path.
- Get the secret on a specified path.

### Transit secrets

Transit secrets engine operations:

```java
@Path("/transit")
public class TransitResource {
    private static final String ENCRYPTION_KEY = "encryption-key";
    private static final String SIGNATURE_KEY = "signature-key";

    private final Sys sys;
    private final TransitSecrets secrets;

    @Inject
    TransitResource(Sys sys, TransitSecrets secrets) {
        this.sys = sys;
        this.secrets = secrets;
    }

    @Path("/engine")
    @GET
    public Response enableEngine() { 
        EnableEngine.Response response = sys.enableEngine(TransitSecrets.ENGINE);

        return Response.ok()
                .entity("Transit secret engine is now enabled."
                        + " Original status: " + response.status().code())
                .build();
    }

    @Path("/engine")
    @DELETE
    public Response disableEngine() { 
        DisableEngine.Response response = sys.disableEngine(TransitSecrets.ENGINE);
        return Response.ok()
                .entity("Transit secret engine is now disabled."
                        + " Original status: " + response.status())
                .build();
    }

    @Path("/keys")
    @GET
    public Response createKeys() { 
        secrets.createKey(CreateKey.Request.builder()
                                  .name(ENCRYPTION_KEY));

        secrets.createKey(CreateKey.Request.builder()
                                  .name(SIGNATURE_KEY)
                                  .type("rsa-2048"));

        return Response.ok()
                .entity("Created encryption (and HMAC), and signature keys")
                .build();
    }

    @Path("/keys")
    @DELETE
    public Response deleteKeys() { 
        // we must first enable deletion of the key (by default it cannot be deleted)
        secrets.updateKeyConfig(UpdateKeyConfig.Request.builder()
                                        .name(ENCRYPTION_KEY)
                                        .allowDeletion(true));

        secrets.updateKeyConfig(UpdateKeyConfig.Request.builder()
                                        .name(SIGNATURE_KEY)
                                        .allowDeletion(true));

        secrets.deleteKey(DeleteKey.Request.create(ENCRYPTION_KEY));
        secrets.deleteKey(DeleteKey.Request.create(SIGNATURE_KEY));

        return Response.ok()
                .entity("Deleted encryption (and HMAC), and signature keys")
                .build();
    }

    @Path("/encrypt/{secret: .*}")
    @GET
    public String encryptSecret(@PathParam("secret") String secret) { 
        return secrets.encrypt(Encrypt.Request.builder()
                                       .encryptionKeyName(ENCRYPTION_KEY)
                                       .data(Base64Value.create(secret)))
                .encrypted()
                .cipherText();
    }

    @Path("/decrypt/{cipherText: .*}")
    @GET
    public String decryptSecret(@PathParam("cipherText") String cipherText) { 
        return secrets.decrypt(Decrypt.Request.builder()
                                       .encryptionKeyName(ENCRYPTION_KEY)
                                       .cipherText(cipherText))
                .decrypted()
                .toDecodedString();
    }

    @Path("/hmac/{text}")
    @GET
    public String hmac(@PathParam("text") String text) { 
        return secrets.hmac(Hmac.Request.builder()
                                    .hmacKeyName(ENCRYPTION_KEY)
                                    .data(Base64Value.create(text)))
                .hmac();
    }

    @Path("/sign/{text}")
    @GET
    public String sign(@PathParam("text") String text) { 
        return secrets.sign(Sign.Request.builder()
                                    .signatureKeyName(SIGNATURE_KEY)
                                    .data(Base64Value.create(text)))
                .signature();
    }

    @Path("/verify/hmac/{secret}/{hmac: .*}")
    @GET
    public String verifyHmac(@PathParam("secret") String secret,
                             @PathParam("hmac") String hmac) { 
        boolean isValid = secrets.verify(Verify.Request.builder()
                                                 .digestKeyName(ENCRYPTION_KEY)
                                                 .data(Base64Value.create(secret))
                                                 .hmac(hmac))
                .isValid();

        return (isValid ? "HMAC Valid" : "HMAC Invalid");
    }

    @Path("/verify/sign/{secret}/{signature: .*}")
    @GET
    public String verifySignature(@PathParam("secret") String secret,
                                  @PathParam("signature") String signature) { 
        boolean isValid = secrets.verify(Verify.Request.builder()
                                                 .digestKeyName(SIGNATURE_KEY)
                                                 .data(Base64Value.create(secret))
                                                 .signature(signature))
                .isValid();

        return (isValid ? "Signature Valid" : "Signature Invalid");
    }
}
```

- Enable the secrets engine on the default path.
- Disable the secrets engine on the default path.
- Create the encrypting and signature keys.
- Delete the encryption and signature keys.
- Encrypt a secret.
- Decrypt a secret.
- Create an HMAC for text.
- Create a signature for text.
- Verify HMAC.
- Verify signature.

## Local Testing

Vault is available as a docker image, so to test locally, you can simply:

```bash
docker run -e VAULT_DEV_ROOT_TOKEN_ID=my-token -d --name=vault -p8200:8200 vault
```

This will create a Vault docker image, run it in background and open it on `localhost:8200` with a custom root token my-token, using name vault. This is of course only suitable for local testing, as the root token has too many rights, but it can be easily used with the examples below.

## References

- [Hashicorp Vault Usage Examples](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/vault)
