/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
import java.util.Date;

import io.helidon.common.Base64Value;

import com.oracle.bmc.keymanagement.KmsCrypto;
import com.oracle.bmc.keymanagement.model.DecryptDataDetails;
import com.oracle.bmc.keymanagement.model.EncryptDataDetails;
import com.oracle.bmc.keymanagement.model.SignDataDetails;
import com.oracle.bmc.keymanagement.model.VerifyDataDetails;
import com.oracle.bmc.keymanagement.requests.DecryptRequest;
import com.oracle.bmc.keymanagement.requests.EncryptRequest;
import com.oracle.bmc.keymanagement.requests.SignRequest;
import com.oracle.bmc.keymanagement.requests.VerifyRequest;
import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.vault.Vaults;
import com.oracle.bmc.vault.model.Base64SecretContentDetails;
import com.oracle.bmc.vault.model.CreateSecretDetails;
import com.oracle.bmc.vault.model.ScheduleSecretDeletionDetails;
import com.oracle.bmc.vault.model.SecretContentDetails;
import com.oracle.bmc.vault.requests.CreateSecretRequest;
import com.oracle.bmc.vault.requests.ScheduleSecretDeletionRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * JAX-RS resource - REST API of the example.
 */
@Path("/vault")
public class VaultResource {
    private final Secrets secrets;
    private final KmsCrypto crypto;
    private final Vaults vaults;
    private final String vaultOcid;
    private final String compartmentOcid;
    private final String encryptionKeyOcid;
    private final String signatureKeyOcid;

    @Inject
    VaultResource(Secrets secrets,
                  KmsCrypto crypto,
                  Vaults vaults,
                  @ConfigProperty(name = "app.vault.vault-ocid")
                  String vaultOcid,
                  @ConfigProperty(name = "app.vault.compartment-ocid")
                  String compartmentOcid,
                  @ConfigProperty(name = "app.vault.encryption-key-ocid")
                  String encryptionKeyOcid,
                  @ConfigProperty(name = "app.vault.signature-key-ocid")
                  String signatureKeyOcid) {
        this.secrets = secrets;
        this.crypto = crypto;
        this.vaults = vaults;
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
        return crypto.encrypt(EncryptRequest.builder()
                                      .encryptDataDetails(EncryptDataDetails.builder()
                                                                  .keyId(encryptionKeyOcid)
                                                                  .plaintext(Base64Value.create(secret).toBase64())
                                                                  .build())
                                      .build())
                .getEncryptedData()
                .getCiphertext();
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
        return Base64Value.createFromEncoded(crypto.decrypt(DecryptRequest.builder()
                                                                    .decryptDataDetails(DecryptDataDetails.builder()
                                                                                                .keyId(encryptionKeyOcid)
                                                                                                .ciphertext(cipherText)
                                                                                                .build())
                                                                    .build())
                                                     .getDecryptedData()
                                                     .getPlaintext())
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
        return crypto.sign(SignRequest.builder()
                                   .signDataDetails(SignDataDetails.builder()
                                                            .keyId(signatureKeyOcid)
                                                            .signingAlgorithm(SignDataDetails.SigningAlgorithm.Sha224RsaPkcsPss)
                                                            .message(Base64Value.create(dataToSign).toBase64())
                                                            .build())
                                   .build())
                .getSignedData()
                .getSignature();
    }

    /**
     * Verify a signature. The base64 encoded signature is the entity
     *
     * @param dataToVerify data that was signed
     * @param signature    signature text
     * @return whether the signature is valid or not
     */
    @POST
    @Path("/verify/{text}")
    public String verify(@PathParam("text") String dataToVerify,
                         String signature) {
        VerifyDataDetails.SigningAlgorithm algorithm = VerifyDataDetails.SigningAlgorithm.Sha224RsaPkcsPss;

        boolean valid = crypto.verify(VerifyRequest.builder()
                                              .verifyDataDetails(VerifyDataDetails.builder()
                                                                         .keyId(signatureKeyOcid)
                                                                         .signingAlgorithm(algorithm)
                                                                         .message(Base64Value.create(dataToVerify).toBase64())
                                                                         .signature(signature)
                                                                         .build())
                                              .build())
                .getVerifiedData()
                .getIsSignatureValid();

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
        SecretBundleContentDetails content = secrets.getSecretBundle(GetSecretBundleRequest.builder()
                                                                             .secretId(secretOcid)
                                                                             .build())
                .getSecretBundle()
                .getSecretBundleContent();

        if (content instanceof Base64SecretBundleContentDetails) {
            // the only supported type
            return Base64Value.createFromEncoded(((Base64SecretBundleContentDetails) content).getContent()).toDecodedString();
        } else {
            throw new InternalServerErrorException("Invalid secret content type");
        }
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
        Date deleteTime = Date.from(Instant.now().plus(30, ChronoUnit.DAYS));

        vaults.scheduleSecretDeletion(ScheduleSecretDeletionRequest.builder()
                                              .secretId(secretOcid)
                                              .scheduleSecretDeletionDetails(ScheduleSecretDeletionDetails.builder()
                                                                                     .timeOfDeletion(deleteTime)
                                                                                     .build())
                                              .build());

        return "Secret " + secretOcid + " was marked for deletion";
    }

    /**
     * Create a new secret.
     *
     * @param name       name of the secret
     * @param secretText secret content
     * @return OCID of the created secret
     */
    @POST
    @Path("/secret/{name}")
    public String createSecret(@PathParam("name") String name,
                               String secretText) {
        SecretContentDetails content = Base64SecretContentDetails.builder()
                .content(Base64Value.create(secretText).toBase64())
                .build();

        return vaults.createSecret(CreateSecretRequest.builder()
                                           .createSecretDetails(CreateSecretDetails.builder()
                                                                        .secretName(name)
                                                                        .vaultId(vaultOcid)
                                                                        .compartmentId(compartmentOcid)
                                                                        .keyId(encryptionKeyOcid)
                                                                        .secretContent(content)
                                                                        .build())
                                           .build())
                .getSecret()
                .getId();
    }
}
