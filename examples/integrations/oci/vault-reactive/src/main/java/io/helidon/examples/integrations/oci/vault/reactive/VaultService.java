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

package io.helidon.examples.integrations.oci.vault.reactive;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import io.helidon.common.Base64Value;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import com.oracle.bmc.keymanagement.KmsCryptoAsync;
import com.oracle.bmc.keymanagement.model.DecryptDataDetails;
import com.oracle.bmc.keymanagement.model.EncryptDataDetails;
import com.oracle.bmc.keymanagement.model.SignDataDetails;
import com.oracle.bmc.keymanagement.model.VerifyDataDetails;
import com.oracle.bmc.keymanagement.requests.DecryptRequest;
import com.oracle.bmc.keymanagement.requests.EncryptRequest;
import com.oracle.bmc.keymanagement.requests.SignRequest;
import com.oracle.bmc.keymanagement.requests.VerifyRequest;
import com.oracle.bmc.secrets.SecretsAsync;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.vault.VaultsAsync;
import com.oracle.bmc.vault.model.Base64SecretContentDetails;
import com.oracle.bmc.vault.model.CreateSecretDetails;
import com.oracle.bmc.vault.model.ScheduleSecretDeletionDetails;
import com.oracle.bmc.vault.model.SecretContentDetails;
import com.oracle.bmc.vault.requests.CreateSecretRequest;
import com.oracle.bmc.vault.requests.ScheduleSecretDeletionRequest;

import static io.helidon.examples.integrations.oci.vault.reactive.OciHandler.ociHandler;

class VaultService implements Service {
    private final SecretsAsync secrets;
    private final VaultsAsync vaults;
    private final KmsCryptoAsync crypto;
    private final String vaultOcid;
    private final String compartmentOcid;
    private final String encryptionKeyOcid;
    private final String signatureKeyOcid;

    VaultService(SecretsAsync secrets,
                 VaultsAsync vaults,
                 KmsCryptoAsync crypto,
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

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/encrypt/{text:.*}", this::encrypt)
                .get("/decrypt/{text:.*}", this::decrypt)
                .get("/sign/{text}", this::sign)
                .post("/verify/{text}", Handler.create(String.class, this::verify))
                .get("/secret/{id}", this::getSecret)
                .post("/secret/{name}", Handler.create(String.class, this::createSecret))
                .delete("/secret/{id}", this::deleteSecret);
    }

    private void getSecret(ServerRequest req, ServerResponse res) {
        secrets.getSecretBundle(GetSecretBundleRequest.builder()
                                        .secretId(req.path().param("id"))
                                        .build(), ociHandler(ociRes -> {
            SecretBundleContentDetails content = ociRes.getSecretBundle().getSecretBundleContent();
            if (content instanceof Base64SecretBundleContentDetails) {
                // the only supported type
                res.send(Base64Value.createFromEncoded(((Base64SecretBundleContentDetails) content).getContent())
                                 .toDecodedString());
            } else {
                req.next(new Exception("Invalid secret content type"));
            }
        }));
    }

    private void deleteSecret(ServerRequest req, ServerResponse res) {
        // has to be for quite a long period of time - did not work with less than 30 days
        Date deleteTime = Date.from(Instant.now().plus(30, ChronoUnit.DAYS));

        String secretOcid = req.path().param("id");

        vaults.scheduleSecretDeletion(ScheduleSecretDeletionRequest.builder()
                                              .secretId(secretOcid)
                                              .scheduleSecretDeletionDetails(ScheduleSecretDeletionDetails.builder()
                                                                                     .timeOfDeletion(deleteTime)
                                                                                     .build())
                                              .build(), ociHandler(ociRes -> res.send("Secret " + secretOcid
                                                                                              + " was marked for deletion")));
    }

    private void createSecret(ServerRequest req, ServerResponse res, String secretText) {
        SecretContentDetails content = Base64SecretContentDetails.builder()
                .content(Base64Value.create(secretText).toBase64())
                .build();

        vaults.createSecret(CreateSecretRequest.builder()
                                    .createSecretDetails(CreateSecretDetails.builder()
                                                                 .secretName(req.path().param("name"))
                                                                 .vaultId(vaultOcid)
                                                                 .compartmentId(compartmentOcid)
                                                                 .keyId(encryptionKeyOcid)
                                                                 .secretContent(content)
                                                                 .build())
                                    .build(), ociHandler(ociRes -> res.send(ociRes.getSecret().getId())));
    }

    private void verify(ServerRequest req, ServerResponse res, String signature) {
        String text = req.path().param("text");
        VerifyDataDetails.SigningAlgorithm algorithm = VerifyDataDetails.SigningAlgorithm.Sha224RsaPkcsPss;

        crypto.verify(VerifyRequest.builder()
                              .verifyDataDetails(VerifyDataDetails.builder()
                                                         .keyId(signatureKeyOcid)
                                                         .signingAlgorithm(algorithm)
                                                         .message(Base64Value.create(text).toBase64())
                                                         .signature(signature)
                                                         .build())
                              .build(),
                      ociHandler(ociRes -> {
                          boolean valid = ociRes.getVerifiedData()
                                  .getIsSignatureValid();
                          res.send(valid ? "Signature valid" : "Signature not valid");
                      }));
    }

    private void sign(ServerRequest req, ServerResponse res) {
        crypto.sign(SignRequest.builder()
                            .signDataDetails(SignDataDetails.builder()
                                                     .keyId(signatureKeyOcid)
                                                     .signingAlgorithm(SignDataDetails.SigningAlgorithm.Sha224RsaPkcsPss)
                                                     .message(Base64Value.create(req.path().param("text")).toBase64())
                                                     .build())
                            .build(), ociHandler(ociRes -> res.send(ociRes.getSignedData()
                                                                            .getSignature())));
    }

    private void encrypt(ServerRequest req, ServerResponse res) {
        crypto.encrypt(EncryptRequest.builder()
                               .encryptDataDetails(EncryptDataDetails.builder()
                                                           .keyId(encryptionKeyOcid)
                                                           .plaintext(Base64Value.create(req.path().param("text")).toBase64())
                                                           .build())
                               .build(), ociHandler(ociRes -> res.send(ociRes.getEncryptedData().getCiphertext())));
    }

    private void decrypt(ServerRequest req, ServerResponse res) {
        crypto.decrypt(DecryptRequest.builder()
                               .decryptDataDetails(DecryptDataDetails.builder()
                                                           .keyId(encryptionKeyOcid)
                                                           .ciphertext(req.path().param("text"))
                                                           .build())
                               .build(), ociHandler(ociRes -> res.send(Base64Value.createFromEncoded(ociRes.getDecryptedData()
                                                                                                             .getPlaintext())
                                                                               .toDecodedString())));
    }
}
