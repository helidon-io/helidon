/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.vault;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.Base64Value;
import io.helidon.common.http.Http;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import com.oracle.bmc.keymanagement.KmsCrypto;
import com.oracle.bmc.keymanagement.model.DecryptDataDetails;
import com.oracle.bmc.keymanagement.model.EncryptDataDetails;
import com.oracle.bmc.keymanagement.model.SignDataDetails;
import com.oracle.bmc.keymanagement.model.VerifyDataDetails;
import com.oracle.bmc.keymanagement.requests.DecryptRequest;
import com.oracle.bmc.keymanagement.requests.EncryptRequest;
import com.oracle.bmc.keymanagement.requests.SignRequest;
import com.oracle.bmc.keymanagement.requests.VerifyRequest;
import com.oracle.bmc.keymanagement.responses.DecryptResponse;
import com.oracle.bmc.keymanagement.responses.EncryptResponse;
import com.oracle.bmc.keymanagement.responses.SignResponse;
import com.oracle.bmc.keymanagement.responses.VerifyResponse;
import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;
import com.oracle.bmc.vault.Vaults;
import com.oracle.bmc.vault.model.Base64SecretContentDetails;
import com.oracle.bmc.vault.model.CreateSecretDetails;
import com.oracle.bmc.vault.model.ScheduleSecretDeletionDetails;
import com.oracle.bmc.vault.model.SecretContentDetails;
import com.oracle.bmc.vault.requests.CreateSecretRequest;
import com.oracle.bmc.vault.requests.ScheduleSecretDeletionRequest;
import com.oracle.bmc.vault.responses.CreateSecretResponse;

class VaultService implements HttpService {

    private static final Logger LOGGER = Logger.getLogger(VaultService.class.getName());
    private final Secrets secrets;
    private final Vaults vaults;
    private final KmsCrypto crypto;
    private final String vaultOcid;
    private final String compartmentOcid;
    private final String encryptionKeyOcid;
    private final String signatureKeyOcid;

    VaultService(Secrets secrets,
                 Vaults vaults,
                 KmsCrypto crypto,
                 String vaultOcid,
                 String compartmentOcid,
                 String encryptionKeyOcid,
                 String signatureKeyOcid) {
        this.secrets = secrets;
        this.vaults = vaults;
        this.crypto = crypto;
        this.vaultOcid = vaultOcid;
        this.compartmentOcid = compartmentOcid;
        this.encryptionKeyOcid = encryptionKeyOcid;
        this.signatureKeyOcid = signatureKeyOcid;
    }

    /**
     * A service registers itself by updating the routine rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void routing(HttpRules rules) {
        rules.get("/encrypt/{text:.*}", this::encrypt)
                .get("/decrypt/{text:.*}", this::decrypt)
                .get("/sign/{text}", this::sign)
                .post("/verify/{text}", this::verify)
                .get("/secret/{id}", this::getSecret)
                .post("/secret/{name}", this::createSecret)
                .delete("/secret/{id}", this::deleteSecret);
    }

    private void getSecret(ServerRequest req, ServerResponse res) {
        ociHandler(response -> {
            GetSecretBundleResponse id = secrets.getSecretBundle(GetSecretBundleRequest.builder()
                    .secretId(req.path().pathParameters().value("id"))
                    .build());
            SecretBundleContentDetails content = id.getSecretBundle().getSecretBundleContent();
            if (content instanceof Base64SecretBundleContentDetails) {
                // the only supported type
                res.send(Base64Value.createFromEncoded(((Base64SecretBundleContentDetails) content).getContent())
                        .toDecodedString());
            } else {
                res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send("Invalid secret content type");
            }
        }, res);

    }

    private void deleteSecret(ServerRequest req, ServerResponse res) {
        ociHandler(response -> {
            // has to be for quite a long period of time - did not work with less than 30 days
            Date deleteTime = Date.from(Instant.now().plus(30, ChronoUnit.DAYS));
            String secretOcid = req.path().pathParameters().value("id");
            vaults.scheduleSecretDeletion(ScheduleSecretDeletionRequest.builder()
                    .secretId(secretOcid)
                    .scheduleSecretDeletionDetails(ScheduleSecretDeletionDetails.builder()
                            .timeOfDeletion(deleteTime)
                            .build())
                    .build()
            );
            response.send(String.format("Secret %s was marked for deletion", secretOcid));
        }, res);
    }

    private void createSecret(ServerRequest req, ServerResponse res) {
        ociHandler(response -> {
            String secretText = req.content().as(String.class);
            SecretContentDetails content = Base64SecretContentDetails.builder()
                    .content(Base64Value.create(secretText).toBase64())
                    .build();
            CreateSecretResponse vaultsSecret = vaults.createSecret(CreateSecretRequest.builder()
                    .createSecretDetails(CreateSecretDetails.builder()
                            .secretName(req.path().pathParameters().value("name"))
                            .vaultId(vaultOcid)
                            .compartmentId(compartmentOcid)
                            .keyId(encryptionKeyOcid)
                            .secretContent(content)
                            .build())
                    .build());
            response.send(vaultsSecret.getSecret().getId());
        }, res);
    }

    private void verify(ServerRequest req, ServerResponse res) {


        ociHandler(response -> {
            String text = req.path().pathParameters().value("text");
            String signature = req.content().as(String.class);
            VerifyDataDetails.SigningAlgorithm algorithm = VerifyDataDetails.SigningAlgorithm.Sha224RsaPkcsPss;
            VerifyResponse verifyResponse = crypto.verify(VerifyRequest.builder()
                    .verifyDataDetails(VerifyDataDetails.builder()
                            .keyId(signatureKeyOcid)
                            .signingAlgorithm(algorithm)
                            .message(Base64Value.create(text).toBase64())
                            .signature(signature)
                            .build())
                    .build());
            boolean valid = verifyResponse.getVerifiedData().getIsSignatureValid();
            response.send(valid ? "Signature valid" : "Signature not valid");
        }, res);
    }

    private void sign(ServerRequest req, ServerResponse res) {
        ociHandler(response -> {
            SignResponse signResponse = crypto.sign(SignRequest.builder()
                    .signDataDetails(SignDataDetails.builder()
                            .keyId(signatureKeyOcid)
                            .signingAlgorithm(SignDataDetails.SigningAlgorithm.Sha224RsaPkcsPss)
                            .message(Base64Value.create(req.path()
                                    .pathParameters().value("text")).toBase64())
                            .build())
                    .build());
            response.send(signResponse.getSignedData().getSignature());
        }, res);
    }

    private void encrypt(ServerRequest req, ServerResponse res) {
        ociHandler(response -> {
            EncryptResponse encryptResponse = crypto.encrypt(EncryptRequest.builder()
                    .encryptDataDetails(EncryptDataDetails.builder()
                            .keyId(encryptionKeyOcid)
                            .plaintext(Base64Value.create(req.path()
                                    .pathParameters().value("text")).toBase64())
                            .build())
                    .build());
            response.send(encryptResponse.getEncryptedData().getCiphertext());
        }, res);
    }

    private void decrypt(ServerRequest req, ServerResponse res) {
        ociHandler(response -> {
            DecryptResponse decryptResponse = crypto.decrypt(DecryptRequest.builder()
                    .decryptDataDetails(DecryptDataDetails.builder()
                            .keyId(encryptionKeyOcid)
                            .ciphertext(req.path()
                                    .pathParameters().value("text"))
                            .build())
                    .build());
            response.send(Base64Value.createFromEncoded(decryptResponse.getDecryptedData().getPlaintext())
                    .toDecodedString());
        }, res);
    }

    private void ociHandler(Consumer<ServerResponse> consumer, ServerResponse response) {
        try {
            consumer.accept(response);
        } catch (Throwable error) {
            LOGGER.log(Level.WARNING, "OCI Exception", error);
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(error.getMessage());
        }
    }
}
