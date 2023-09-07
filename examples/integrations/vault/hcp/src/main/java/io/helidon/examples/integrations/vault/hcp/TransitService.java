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

package io.helidon.examples.integrations.vault.hcp;

import java.util.List;

import io.helidon.common.Base64Value;
import io.helidon.integrations.vault.secrets.transit.CreateKey;
import io.helidon.integrations.vault.secrets.transit.Decrypt;
import io.helidon.integrations.vault.secrets.transit.DecryptBatch;
import io.helidon.integrations.vault.secrets.transit.DeleteKey;
import io.helidon.integrations.vault.secrets.transit.Encrypt;
import io.helidon.integrations.vault.secrets.transit.EncryptBatch;
import io.helidon.integrations.vault.secrets.transit.Hmac;
import io.helidon.integrations.vault.secrets.transit.Sign;
import io.helidon.integrations.vault.secrets.transit.TransitSecrets;
import io.helidon.integrations.vault.secrets.transit.UpdateKeyConfig;
import io.helidon.integrations.vault.secrets.transit.Verify;
import io.helidon.integrations.vault.sys.Sys;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class TransitService implements HttpService {
    private static final String ENCRYPTION_KEY = "encryption-key";
    private static final String SIGNATURE_KEY = "signature-key";
    private static final Base64Value SECRET_STRING = Base64Value.create("Hello World");
    private final Sys sys;
    private final TransitSecrets secrets;

    TransitService(Sys sys, TransitSecrets secrets) {
        this.sys = sys;
        this.secrets = secrets;
    }

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

    private void enableEngine(ServerRequest req, ServerResponse res) {
        sys.enableEngine(TransitSecrets.ENGINE);
        res.send("Transit Secret engine enabled");
    }

    private void disableEngine(ServerRequest req, ServerResponse res) {
        sys.disableEngine(TransitSecrets.ENGINE);
        res.send("Transit Secret engine disabled");
    }

    private void createKeys(ServerRequest req, ServerResponse res) {
        CreateKey.Request request = CreateKey.Request.builder()
                                                     .name(ENCRYPTION_KEY);

        secrets.createKey(request);
        secrets.createKey(CreateKey.Request.builder()
                                           .name(SIGNATURE_KEY)
                                           .type("rsa-2048"));

        res.send("Created keys");
    }

    private void deleteKeys(ServerRequest req, ServerResponse res) {
        secrets.updateKeyConfig(UpdateKeyConfig.Request.builder()
                                                       .name(ENCRYPTION_KEY)
                                                       .allowDeletion(true));
        System.out.println("Updated key config");

        secrets.deleteKey(DeleteKey.Request.create(ENCRYPTION_KEY));

        res.send("Deleted key.");
    }

    private void decryptSecret(ServerRequest req, ServerResponse res) {
        String encrypted = req.path().pathParameters().get("text");

        Decrypt.Response decryptResponse = secrets.decrypt(Decrypt.Request.builder()
                                                                          .encryptionKeyName(ENCRYPTION_KEY)
                                                                          .cipherText(encrypted));

        res.send(String.valueOf(decryptResponse.decrypted().toDecodedString()));
    }

    private void encryptSecret(ServerRequest req, ServerResponse res) {
        String secret = req.path().pathParameters().get("text");

        Encrypt.Response encryptResponse = secrets.encrypt(Encrypt.Request.builder()
                                                                          .encryptionKeyName(ENCRYPTION_KEY)
                                                                          .data(Base64Value.create(secret)));

        res.send(encryptResponse.encrypted().cipherText());
    }

    private void hmac(ServerRequest req, ServerResponse res) {
        Hmac.Response hmacResponse = secrets.hmac(Hmac.Request.builder()
                                                              .hmacKeyName(ENCRYPTION_KEY)
                                                              .data(SECRET_STRING));

        res.send(hmacResponse.hmac());
    }

    private void sign(ServerRequest req, ServerResponse res) {
        Sign.Response signResponse = secrets.sign(Sign.Request.builder()
                                                              .signatureKeyName(SIGNATURE_KEY)
                                                              .data(SECRET_STRING));

        res.send(signResponse.signature());
    }

    private void verifyHmac(ServerRequest req, ServerResponse res) {
        String hmac = req.path().pathParameters().get("text");

        Verify.Response verifyResponse = secrets.verify(Verify.Request.builder()
                                                              .digestKeyName(ENCRYPTION_KEY)
                                                              .data(SECRET_STRING)
                                                              .hmac(hmac));

        res.send("Valid: " + verifyResponse.isValid());
    }

    private void verify(ServerRequest req, ServerResponse res) {
        String signature = req.path().pathParameters().get("text");

        Verify.Response verifyResponse = secrets.verify(Verify.Request.builder()
                                                              .digestKeyName(SIGNATURE_KEY)
                                                              .data(SECRET_STRING)
                                                              .signature(signature));

        res.send("Valid: " + verifyResponse.isValid());
    }

    private void batch(ServerRequest req, ServerResponse res) {
        String[] data = new String[]{"one", "two", "three", "four"};
        EncryptBatch.Request request = EncryptBatch.Request.builder()
                                                           .encryptionKeyName(ENCRYPTION_KEY);
        DecryptBatch.Request decryptRequest = DecryptBatch.Request.builder()
                                                                  .encryptionKeyName(ENCRYPTION_KEY);

        for (String item : data) {
            request.addEntry(EncryptBatch.BatchEntry.create(Base64Value.create(item)));
        }
        List<Encrypt.Encrypted> batchResult = secrets.encrypt(request).batchResult();
        for (Encrypt.Encrypted encrypted : batchResult) {
            System.out.println("Encrypted: " + encrypted.cipherText());
            decryptRequest.addEntry(DecryptBatch.BatchEntry.create(encrypted.cipherText()));
        }

        List<Base64Value> base64Values = secrets.decrypt(decryptRequest).batchResult();
        for (int i = 0; i < data.length; i++) {
            String decryptedValue = base64Values.get(i).toDecodedString();
            if (!data[i].equals(decryptedValue)) {
                res.send("Data at index " + i + " is invalid. Decrypted " + decryptedValue
                        + ", expected: " + data[i]);
                return;
            }
        }
        res.send("Batch encryption/decryption completed");
    }
}
