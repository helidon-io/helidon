/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import io.helidon.common.Base64Value;
import io.helidon.integrations.oci.vault.CreateSecret;
import io.helidon.integrations.oci.vault.Decrypt;
import io.helidon.integrations.oci.vault.DeleteSecret;
import io.helidon.integrations.oci.vault.Encrypt;
import io.helidon.integrations.oci.vault.GetSecretBundle;
import io.helidon.integrations.oci.vault.OciVault;
import io.helidon.integrations.oci.vault.Sign;
import io.helidon.integrations.oci.vault.Verify;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * JAX-RS resource - REST API of the example.
 */
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

    /**
     * Encrypt a string.
     *
     * @param secret secret to encrypt
     * @return cipher text
     */
    @GET
    @Path("/encrypt/{text}")
    public String encrypt(@PathParam("text") String secret) {
        return vault.encrypt(Encrypt.Request.builder()
                                     .keyId(encryptionKeyOcid)
                                     .data(Base64Value.create(secret)))
                .cipherText();
    }

    /**
     * Decrypt a cipher text.
     *
     * @param cipherText cipher text to decrypt
     * @return original secret
     */
    @GET
    @Path("/decrypt/{text: .*}")
    public String decrypt(@PathParam("text") String cipherText) {
        return vault.decrypt(Decrypt.Request.builder()
                                     .keyId(encryptionKeyOcid)
                                     .cipherText(cipherText))
                .decrypted()
                .toDecodedString();
    }

    /**
     * Sign data.
     *
     * @param dataToSign data to sign (must be a String)
     * @return signature text
     */
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

    /**
     * Verify a signature.
     *
     * @param dataToVerify data that was signed
     * @param signature signature text
     * @return whether the signature is valid or not
     */
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

    /**
     * Get secret content from Vault.
     *
     * @param secretOcid OCID of the secret to get
     * @return content of the secret
     */
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

    /**
     * Delete a secret from Vault.
     * This operation actually marks a secret for deletion, and the minimal time is 30 days.
     *
     * @param secretOcid OCID of the secret to delete
     * @return short message
     */
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

    /**
     * Create a new secret.
     *
     * @param name name of the secret
     * @param secretText secret content
     * @return OCID of the created secret
     */
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
