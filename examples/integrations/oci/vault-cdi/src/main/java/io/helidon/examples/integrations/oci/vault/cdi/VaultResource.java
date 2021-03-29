package io.helidon.examples.integrations.oci.vault.cdi;

import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.integrations.oci.vault.Encrypt;
import io.helidon.integrations.oci.vault.blocking.OciVault;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/vault")
public class VaultResource {
    private final OciVault vault;
    private final String vaultOcid;
    private final String compartmentOcid;
    private final String encryptionKeyOcid;
    private final String signatureKeyOcid;

    @Inject
    VaultResource(OciVault vault,
                  @ConfigProperty(name = "oci.vault.vault-ocid")
                          String vaultOcid,
                  @ConfigProperty(name = "oci.vault.compartment-ocid")
                          String compartmentOcid,
                  @ConfigProperty(name = "oci.vault.encryption-key-ocid")
                          String encryptionKeyOcid,
                  @ConfigProperty(name = "oci.vault.signature-key-ocid")
                          String signatureKeyOcid) {
        this.vault = vault;
        this.vaultOcid = vaultOcid;
        this.compartmentOcid = compartmentOcid;
        this.encryptionKeyOcid = encryptionKeyOcid;
        this.signatureKeyOcid = signatureKeyOcid;
    }

    @Path("/encrypt/{text}")
    public String encrypt(@PathParam("text") String secret) {
        return vault.encrypt(Encrypt.Request.builder()
                                     .keyId(encryptionKeyOcid)
                                     .data(Base64Value.create(secret)))
                .cipherText();
    }
/*
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/encrypt/{text:.*}", this::encrypt)
                .get("/decrypt/{text:.*}", this::decrypt)
                .get("/sign/{text}", this::sign)
                .get("/verify/{text}/{signature:.*}", this::verify)
                .get("/secret/{id}", this::getSecret)
                .post("/secret/{name}", Handler.create(String.class, this::createSecret))
                .delete("/secret/{id}", this::deleteSecret);
    }

    private void getSecret(ServerRequest req, ServerResponse res) {
        vault.getSecretBundle(GetSecretBundle.Request.create(req.path().param("id")))
                .forSingle(apiResponse -> {
                    Optional<GetSecretBundle.Response> entity = apiResponse.entity();
                    if (entity.isEmpty()) {
                        res.status(Http.Status.NOT_FOUND_404).send();
                    } else {
                        GetSecretBundle.Response response = entity.get();
                        res.send(response.secretString().orElse(""));
                    }
                })
                .exceptionally(res::send);

    }

    private void deleteSecret(ServerRequest req, ServerResponse res) {
        // has to be for quite a long period of time - did not work with less than 30 days
        Instant deleteTime = Instant.now().plus(30, ChronoUnit.DAYS);

        vault.deleteSecret(DeleteSecret.Request.builder()
                                   .secretId(req.path().param("id"))
                                   .timeOfDeletion(deleteTime))
                .forSingle(it -> res.status(it.status()).send())
                .exceptionally(res::send);

    }

    private void createSecret(ServerRequest req, ServerResponse res, String secretText) {
        vault.createSecret(CreateSecret.Request.builder()
                                   .secretContent(CreateSecret.SecretContent.create(secretText))
                                   .vaultId(vaultOcid)
                                   .compartmentId(compartmentOcid)
                                   .encryptionKeyId(encryptionKeyOcid)
                                   .secretName(req.path().param("name")))
                .map(CreateSecret.Response::secret)
                .map(Secret::id)
                .forSingle(res::send)
                .exceptionally(res::send);
    }

    private void verify(ServerRequest req, ServerResponse res) {
        String text = req.path().param("text");
        String signature = req.path().param("signature");

        vault.verify(Verify.Request.builder()
                             .keyId(signatureKeyOcid)
                             .algorithm(Sign.Request.ALGORITHM_SHA_224_RSA_PKCS_PSS)
                             .message(Base64Value.create(text))
                             .signature(Base64Value.createFromEncoded(signature)))
                .map(Verify.Response::isValid)
                .map(it -> it ? "Signature Valid" : "Signature Invalid")
                .forSingle(res::send)
                .exceptionally(res::send);
    }

    private void sign(ServerRequest req, ServerResponse res) {
        vault.sign(Sign.Request.builder()
                           .keyId(signatureKeyOcid)
                           .algorithm(Sign.Request.ALGORITHM_SHA_224_RSA_PKCS_PSS)
                           .message(Base64Value.create(req.path().param("text"))))
                .map(Sign.Response::signature)
                .map(Base64Value::toBase64)
                .forSingle(res::send)
                .exceptionally(res::send);
    }

    private void encrypt(ServerRequest req, ServerResponse res) {
        vault.encrypt(Encrypt.Request.builder()
                              .keyId(encryptionKeyOcid)
                              .data(Base64Value.create(req.path().param("text"))))
                .map(Encrypt.Response::cipherText)
                .forSingle(res::send)
                .exceptionally(res::send);
    }

    private void decrypt(ServerRequest req, ServerResponse res) {
        vault.decrypt(Decrypt.Request.builder()
                              .keyId(encryptionKeyOcid)
                              .cipherText(req.path().param("text")))
                .map(Decrypt.Response::decrypted)
                .map(Base64Value::toDecodedString)
                .forSingle(res::send)
                .exceptionally(res::send);
    }
 */
}
