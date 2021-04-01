package io.helidon.examples.integrations.oci.vault.cdi;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.integrations.oci.vault.CreateSecret;
import io.helidon.integrations.oci.vault.Decrypt;
import io.helidon.integrations.oci.vault.DeleteSecret;
import io.helidon.integrations.oci.vault.Encrypt;
import io.helidon.integrations.oci.vault.GetSecretBundle;
import io.helidon.integrations.oci.vault.OciVault;
import io.helidon.integrations.oci.vault.Sign;
import io.helidon.integrations.oci.vault.Verify;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/vault")
public class VaultResource {
    private final OciVault vault;
    private final String vaultOcid;
    private final String compartmentOcid;
    private final String encryptionKeyOcid;
    private final String signatureKeyOcid;

    @Inject
    VaultResource(@Named("custom") OciVault vault,
                  @ConfigProperty(name = "app.vault.vault-ocid")
                          String vaultOcid,
                  @ConfigProperty(name = "app.vault.compartment-ocid")
                          String compartmentOcid,
                  @ConfigProperty(name = "app.vault.encryption-key-ocid")
                          String encryptionKeyOcid,
                  @ConfigProperty(name = "app.vault.signature-key-ocid")
                          String signatureKeyOcid) {
        this.vault = vault;
        this.vaultOcid = vaultOcid;
        this.compartmentOcid = compartmentOcid;
        this.encryptionKeyOcid = encryptionKeyOcid;
        this.signatureKeyOcid = signatureKeyOcid;
    }

    @GET
    @Path("/encrypt/{text}")
    public String encrypt(@PathParam("text") String secret) {
        return vault.encrypt(Encrypt.Request.builder()
                                     .keyId(encryptionKeyOcid)
                                     .data(Base64Value.create(secret)))
                .cipherText();
    }

    @GET
    @Path("/decrypt/{text: .*}")
    public String decrypt(@PathParam("text") String cipherText) {
        return vault.decrypt(Decrypt.Request.builder()
                                     .keyId(encryptionKeyOcid)
                                     .cipherText(cipherText))
                .decrypted()
                .toDecodedString();
    }

    @GET
    @Path("/sign/{text}")
    public String sign(@PathParam("text") String dataToSign) {
        return vault.sign(Sign.Request.builder()
                                  .keyId(signatureKeyOcid)
                                  .algorithm(Sign.Request.ALGORITHM_SHA_224_RSA_PKCS_PSS)
                                  .message(Base64Value.create(dataToSign)))
                .signature()
                .toBase64();
    }

    @GET
    @Path("/sign/{text}/{signature: .*}")
    public String verify(@PathParam("text") String dataToVerify,
                         @PathParam("signature") String signature) {
        boolean valid = vault.verify(Verify.Request.builder()
                                             .keyId(signatureKeyOcid)
                                             .message(Base64Value.create(dataToVerify))
                                             .algorithm(Sign.Request.ALGORITHM_SHA_224_RSA_PKCS_PSS)
                                             .signature(Base64Value.createFromEncoded(signature)))
                .isValid();

        return valid ? "Signature valid" : "Signature not valid";
    }

    @GET
    @Path("/secret/{id}")
    public String getSecret(@PathParam("id") String secretOcid) {
        Optional<GetSecretBundle.Response> response = vault.getSecretBundle(GetSecretBundle.Request.builder()
                                                                                    .secretId(secretOcid))
                .entity();

        if (response.isEmpty()) {
            throw new NotFoundException("Secret with id " + secretOcid + " does not exist");
        }

        return response.get().secretString().orElse("");
    }

    @DELETE
    @Path("/secret/{id}")
    public String deleteSecret(@PathParam("id") String secretOcid) {
        // has to be for quite a long period of time - did not work with less than 30 days
        Instant deleteTime = Instant.now().plus(30, ChronoUnit.DAYS);

        vault.deleteSecret(DeleteSecret.Request.builder()
                                   .secretId(secretOcid)
                                   .timeOfDeletion(deleteTime));

        return "Secret " + secretOcid + " was deleted";
    }

    @POST
    @Path("/secret/{name}")
    public String createSecret(@PathParam("name") String name,
                               String secretText) {
        return vault.createSecret(CreateSecret.Request.builder()
                                          .secretName(name)
                                          .secretContent(CreateSecret.SecretContent.create(secretText))
                                          .vaultId(vaultOcid)
                                          .compartmentId(compartmentOcid)
                                          .encryptionKeyId(encryptionKeyOcid))
                .secret()
                .id();

    }
}
