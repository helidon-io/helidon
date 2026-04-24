# HashiCorp Vault

## Overview

HashiCorp Vault is a commonly used Vault in many microservices. The APIs are REST-based and Helidon implements them using [WebClient](../../se/webclient.md).

## Maven Coordinates

To enable HashiCorp Vault, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.integrations.vault</groupId>
    <artifactId>helidon-integrations-vault</artifactId>
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

Each of these features is implemented as a separate module, with the Vault class binding them together. Code to set up Vault and obtain a specific secret engine:

```java
Vault vault = Vault.builder()
        .config(config.get("vault"))
        .build();
Kv2Secrets secrets = vault.secrets(Kv2Secrets.ENGINE);
```

Similar code can be used for any secret engine available:

- Kv2SecretsRx - Key/Value Version 2 Secrets (versioned secrets, default)
- Kv1SecretsRx - Key/Value Version 1 Secrets (unversioned secrets, legacy)
- CubbyholeSecretsRx - Cubbyhole secrets (token bound secrets)
- DbSecretsRx - Database secrets (for generating temporary DB credentials)
- PkiSecretsRx - PKI secrets (for generating keys and X.509 certificates)
- TransitSecretsRx - Transit operations (encryption, signatures, HMAC)

In addition to these features, Vault itself can be authenticated as follows:

- Token authentication - token is configured when connecting to Vault

<!-- -->

    vault:
       address: "http://localhost:8200"
       token: "my-token"

- AppRole authentication - AppRole ID and secret ID are configured, integration exchanges these for a temporary token that is used to connect to Vault

<!-- -->

    vault:
      auth:
        app-role:
          role-id: "app-role-id"
          secret-id: app-role-secret-id

- K8s authentication - the k8s JWT token is discovered on current node and used to obtain a temporary token that is used to connect to Vault

<!-- -->

    vault:
      auth:
        k8s:
          token-role: "my-role" 

- The token role must be configured in Vault Minimal configuration to connect to Vault:

Code to get the Sys operations of Vault:

```java
Sys sys = vault.sys(Sys.API);
```

### Extensibility

New secret engines and authentication methods can be implemented quite easily, as the integration is based on service providers (using ServiceLoader). This gives us (or you, as the users) the option to add new secret engines and/or authentication methods without adding a plethora of methods to the Vault class.

See the following SPIs:

```text
io.helidon.integrations.vault.spi.AuthMethodProvider
io.helidon.integrations.vault.spi.SecretsEngineProvider
io.helidon.integrations.vault.spi.SysProvider
io.helidon.integrations.vault.spi.VaultAuth
io.helidon.integrations.vault.spi.InjectionProvider
```

## Examples

The following example shows usage of Vault to encrypt a secret.

### Usage with WebServer

Configure the `Vault` object using token base configuration:

```java
Vault tokenVault = Vault.builder()
        .config(config.get("vault.token"))
        .updateWebClient(it -> it
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5)))
        .build();
```

Then `WebServer` has to be configured with endpoints routing registered:

```java
Sys sys = tokenVault.sys(Sys.API);
WebServer webServer = WebServer.builder()
        .config(config.get("server"))
        .routing(routing -> routing
                .register("/cubbyhole", new CubbyholeService(sys, tokenVault.secrets(CubbyholeSecrets.ENGINE)))
                .register("/kv1", new Kv1Service(sys, tokenVault.secrets(Kv1Secrets.ENGINE)))
                .register("/kv2", new Kv2Service(sys, tokenVault.secrets(Kv2Secrets.ENGINE)))
                .register("/transit", new TransitService(sys, tokenVault.secrets(TransitSecrets.ENGINE))))
        .build()
        .start();
```

AppRole-based and Kubernetes authentications are available.

### Cubbyhole secrets

Cubbyhole secrets engine operations:

```java
@Override
public void routing(HttpRules rules) {
    rules.get("/create", this::createSecrets)
            .get("/secrets/{path:.*}", this::getSecret);
}

void createSecrets(ServerRequest req, ServerResponse res) { 
    secrets.create("first/secret", Map.of("key", "secretValue"));
    res.send("Created secret on path /first/secret");
}

void getSecret(ServerRequest req, ServerResponse res) { 
    String path = req.path().pathParameters().get("path");
    Optional<Secret> secret = secrets.get(path);
    if (secret.isPresent()) {
        // using toString so we do not need to depend on JSON-B
        res.send(secret.get().values().toString());
    } else {
        res.status(Status.NOT_FOUND_404);
        res.send();
    }
}
```

- Create a secret from request entity.
- Get the secret on a specified path.

### KV1 Secrets

Key/Value version 1 secrets engine operations:

```java
@Override
public void routing(HttpRules rules) {
    rules.get("/enable", this::enableEngine)
            .get("/create", this::createSecrets)
            .get("/secrets/{path:.*}", this::getSecret)
            .delete("/secrets/{path:.*}", this::deleteSecret)
            .get("/disable", this::disableEngine);
}

void disableEngine(ServerRequest req, ServerResponse res) { 
    sys.disableEngine(Kv1Secrets.ENGINE);
    res.send("KV1 Secret engine disabled");
}

void enableEngine(ServerRequest req, ServerResponse res) { 
    sys.enableEngine(Kv1Secrets.ENGINE);
    res.send("KV1 Secret engine enabled");
}

void createSecrets(ServerRequest req, ServerResponse res) { 
    secrets.create("first/secret", Map.of("key", "secretValue"));
    res.send("Created secret on path /first/secret");
}

void deleteSecret(ServerRequest req, ServerResponse res) { 
    String path = req.path().pathParameters().get("path");
    secrets.delete(path);
    res.send("Deleted secret on path " + path);
}

void getSecret(ServerRequest req, ServerResponse res) { 
    String path = req.path().pathParameters().get("path");

    Optional<Secret> secret = secrets.get(path);
    if (secret.isPresent()) {
        // using toString so we do not need to depend on JSON-B
        res.send(secret.get().values().toString());
    } else {
        res.status(Status.NOT_FOUND_404);
        res.send();
    }
}
```

- Disable the secrets engine on the default path.
- Enable the secrets engine on the default path.
- Create a secret from request entity.
- Delete the secret on a specified path.
- Get the secret on a specified path.

### KV2 Secrets

Key/Value version 2 secrets engine operations:

```java
@Override
public void routing(HttpRules rules) {
    rules.get("/create", this::createSecrets)
            .get("/secrets/{path:.*}", this::getSecret)
            .delete("/secrets/{path:.*}", this::deleteSecret);
}

void createSecrets(ServerRequest req, ServerResponse res) { 
    secrets.create("first/secret", Map.of("key", "secretValue"));
    res.send("Created secret on path /first/secret");
}

void deleteSecret(ServerRequest req, ServerResponse res) { 
    String path = req.path().pathParameters().get("path");
    secrets.deleteAll(path);
    res.send("Deleted secret on path " + path);
}

void getSecret(ServerRequest req, ServerResponse res) { 
    String path = req.path().pathParameters().get("path");

    Optional<Kv2Secret> secret = secrets.get(path);
    if (secret.isPresent()) {
        // using toString so we do not need to depend on JSON-B
        Kv2Secret kv2Secret = secret.get();
        res.send("Version " + kv2Secret.metadata().version() + ", secret: " + kv2Secret.values().toString());
    } else {
        res.status(Status.NOT_FOUND_404);
        res.send();
    }
}
```

- Create a secret from request entity.
- Delete the secret on a specified path.
- Get the secret on a specified path.

### Transit secrets

Transit secrets engine operations:

```java
@Override
public void routing(HttpRules rules) {
    rules.get("/enable", this::enableEngine)
            .get("/keys", this::createKeys)
            .delete("/keys", this::deleteKeys)
            .get("/batch", this::batch)
            .get("/encrypt/{text:.*}", this::encryptSecret)
            .get("/decrypt/{text:.*}", this::decryptSecret)
            .get("/sign", this::sign)
            .get("/hmac", this::hmac)
            .get("/verify/sign/{text:.*}", this::verify)
            .get("/verify/hmac/{text:.*}", this::verifyHmac)
            .get("/disable", this::disableEngine);
}

void enableEngine(ServerRequest req, ServerResponse res) { 
    sys.enableEngine(TransitSecrets.ENGINE);
    res.send("Transit Secret engine enabled");
}

void disableEngine(ServerRequest req, ServerResponse res) { 
    sys.disableEngine(TransitSecrets.ENGINE);
    res.send("Transit Secret engine disabled");
}

void createKeys(ServerRequest req, ServerResponse res) { 
    CreateKey.Request request = CreateKey.Request.builder()
            .name(ENCRYPTION_KEY);

    secrets.createKey(request);
    secrets.createKey(CreateKey.Request.builder()
                              .name(SIGNATURE_KEY)
                              .type("rsa-2048"));

    res.send("Created keys");
}

void deleteKeys(ServerRequest req, ServerResponse res) { 
    secrets.updateKeyConfig(UpdateKeyConfig.Request.builder()
                                    .name(ENCRYPTION_KEY)
                                    .allowDeletion(true));
    System.out.println("Updated key config");

    secrets.deleteKey(DeleteKey.Request.create(ENCRYPTION_KEY));

    res.send("Deleted key.");
}

void encryptSecret(ServerRequest req, ServerResponse res) { 
    String secret = req.path().pathParameters().get("text");

    Encrypt.Response encryptResponse = secrets.encrypt(Encrypt.Request.builder()
                                                               .encryptionKeyName(ENCRYPTION_KEY)
                                                               .data(Base64Value.create(secret)));

    res.send(encryptResponse.encrypted().cipherText());
}

void decryptSecret(ServerRequest req, ServerResponse res) { 
    String encrypted = req.path().pathParameters().get("text");

    Decrypt.Response decryptResponse = secrets.decrypt(Decrypt.Request.builder()
                                                               .encryptionKeyName(ENCRYPTION_KEY)
                                                               .cipherText(encrypted));

    res.send(String.valueOf(decryptResponse.decrypted().toDecodedString()));
}

void hmac(ServerRequest req, ServerResponse res) { 
    Hmac.Response hmacResponse = secrets.hmac(Hmac.Request.builder()
                                                      .hmacKeyName(ENCRYPTION_KEY)
                                                      .data(SECRET_STRING));

    res.send(hmacResponse.hmac());
}

void sign(ServerRequest req, ServerResponse res) { 
    Sign.Response signResponse = secrets.sign(Sign.Request.builder()
                                                      .signatureKeyName(SIGNATURE_KEY)
                                                      .data(SECRET_STRING));

    res.send(signResponse.signature());
}

void verifyHmac(ServerRequest req, ServerResponse res) { 
    String hmac = req.path().pathParameters().get("text");

    Verify.Response verifyResponse = secrets.verify(Verify.Request.builder()
                                                            .digestKeyName(ENCRYPTION_KEY)
                                                            .data(SECRET_STRING)
                                                            .hmac(hmac));

    res.send("Valid: " + verifyResponse.isValid());
}

void verify(ServerRequest req, ServerResponse res) { 
    String signature = req.path().pathParameters().get("text");

    Verify.Response verifyResponse = secrets.verify(Verify.Request.builder()
                                                            .digestKeyName(SIGNATURE_KEY)
                                                            .data(SECRET_STRING)
                                                            .signature(signature));

    res.send("Valid: " + verifyResponse.isValid());
}
```

- Enable the secrets engine on the default path.
- Disable the secrets engine on the default path.
- Create the encryption and signature keys.
- Delete the encryption and signature keys.
- Encrypt a secret.
- Decrypt a secret.
- Create an HMAC for text.
- Create a signature for text.
- Verify HMAC.
- Verify signature.

### Authentication with Kubernetes

In order to use Kubernetes authentication:

```java
class K8sExample {
    private static final String SECRET_PATH = "k8s/example/secret";
    private static final String POLICY_NAME = "k8s_policy";

    private final Vault tokenVault;
    private final String k8sAddress;
    private final Config config;
    private final Sys sys;

    private Vault k8sVault;

    K8sExample(Vault tokenVault, Config config) {
        this.tokenVault = tokenVault;
        this.sys = tokenVault.sys(Sys.API);
        this.k8sAddress = config.get("cluster-address").asString().get();
        this.config = config;
    }

    public String run() { 
        // The following tasks must be run before we authenticate
        enableK8sAuth();
        // Now we can login using k8s - must run within a k8s cluster
        // (or you need the k8s configuration files locally)
        workWithSecrets();
        // Now back to token based Vault, as we will clean up
        disableK8sAuth();
        return "k8s example finished successfully.";
    }

    private void workWithSecrets() { 
        Kv2Secrets secrets = k8sVault.secrets(Kv2Secrets.ENGINE);

        secrets.create(SECRET_PATH, Map.of(
                "secret-key", "secretValue",
                "secret-user", "username"));

        Optional<Kv2Secret> secret = secrets.get(SECRET_PATH);
        if (secret.isPresent()) {
            Kv2Secret kv2Secret = secret.get();
            System.out.println("k8s first secret: " + kv2Secret.value("secret-key"));
            System.out.println("k8s second secret: " + kv2Secret.value("secret-user"));
        } else {
            System.out.println("k8s secret not found");
        }
        secrets.deleteAll(SECRET_PATH);
    }

    private void disableK8sAuth() { 
        sys.deletePolicy(POLICY_NAME);
        sys.disableAuth(K8sAuth.AUTH_METHOD.defaultPath());
    }

    private void enableK8sAuth() { 
        // enable the method
        sys.enableAuth(K8sAuth.AUTH_METHOD);
        sys.createPolicy(POLICY_NAME, VaultPolicy.POLICY);
        tokenVault.auth(K8sAuth.AUTH_METHOD)
                .configure(ConfigureK8s.Request.builder()
                                   .address(k8sAddress));
        tokenVault.auth(K8sAuth.AUTH_METHOD)
                // this must be the same role name as is defined in application.yaml
                .createRole(CreateRole.Request.builder()
                                    .roleName("my-role")
                                    .addBoundServiceAccountName("*")
                                    .addBoundServiceAccountNamespace("default")
                                    .addTokenPolicy(POLICY_NAME));
        k8sVault = Vault.create(config);
    }
}
```

- Run the Kubernetes Authentication by enabling it.
- Create Kubernetes secrets.
- Disable Kubernetes authentication if needed.
- Function used to enable Kubernetes authentication.

## Local testing

Vault is available as a docker image, so to test locally, you can simply:

```bash
docker run -e VAULT_DEV_ROOT_TOKEN_ID=my-token -d --name=vault -p8200:8200 vault
```

This will create a Vault docker image, run it in background and open it on `localhost:8200` with a custom root token my-token, using name vault. This is of course only suitable for local testing, as the root token has too many rights, but it can be easily used with the examples below.

## References

- [Hashicorp Vault Usage Examples](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/vault)
