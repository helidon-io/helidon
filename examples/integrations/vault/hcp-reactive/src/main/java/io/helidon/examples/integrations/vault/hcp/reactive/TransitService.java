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

package io.helidon.examples.integrations.vault.hcp.reactive;

import java.util.List;

import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.integrations.vault.secrets.transit.CreateKey;
import io.helidon.integrations.vault.secrets.transit.Decrypt;
import io.helidon.integrations.vault.secrets.transit.DecryptBatch;
import io.helidon.integrations.vault.secrets.transit.DeleteKey;
import io.helidon.integrations.vault.secrets.transit.Encrypt;
import io.helidon.integrations.vault.secrets.transit.EncryptBatch;
import io.helidon.integrations.vault.secrets.transit.Hmac;
import io.helidon.integrations.vault.secrets.transit.Sign;
import io.helidon.integrations.vault.secrets.transit.TransitSecretsRx;
import io.helidon.integrations.vault.secrets.transit.UpdateKeyConfig;
import io.helidon.integrations.vault.secrets.transit.Verify;
import io.helidon.integrations.vault.sys.SysRx;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

class TransitService implements Service {
    private static final String ENCRYPTION_KEY = "encryption-key";
    private static final String SIGNATURE_KEY = "signature-key";
    private static final Base64Value SECRET_STRING = Base64Value.create("Hello World");
    private final SysRx sys;
    private final TransitSecretsRx secrets;

    TransitService(SysRx sys, TransitSecretsRx secrets) {
        this.sys = sys;
        this.secrets = secrets;
    }

    @Override
    public void update(Routing.Rules rules) {
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
        sys.enableEngine(TransitSecretsRx.ENGINE)
                .thenAccept(ignored -> res.send("Transit Secret engine enabled"))
                .exceptionally(res::send);
    }

    private void disableEngine(ServerRequest req, ServerResponse res) {
        sys.disableEngine(TransitSecretsRx.ENGINE)
                .thenAccept(ignored -> res.send("Transit Secret engine disabled"))
                .exceptionally(res::send);
    }

    private void createKeys(ServerRequest req, ServerResponse res) {
        CreateKey.Request request = CreateKey.Request.builder()
                .name(ENCRYPTION_KEY);

        secrets.createKey(request)
                .flatMapSingle(ignored -> secrets.createKey(CreateKey.Request.builder()
                                                                    .name(SIGNATURE_KEY)
                                                                    .type("rsa-2048")))
                .forSingle(ignored -> res.send("Created keys"))
                .exceptionally(res::send);
    }

    private void deleteKeys(ServerRequest req, ServerResponse res) {

        secrets.updateKeyConfig(UpdateKeyConfig.Request.builder()
                                        .name(ENCRYPTION_KEY)
                                        .allowDeletion(true))
                .peek(ignored -> System.out.println("Updated key config"))
                .flatMapSingle(ignored -> secrets.deleteKey(DeleteKey.Request.create(ENCRYPTION_KEY)))
                .forSingle(ignored -> res.send("Deleted key."))
                .exceptionally(res::send);
    }

    private void decryptSecret(ServerRequest req, ServerResponse res) {
        String encrypted = req.path().param("text");

        secrets.decrypt(Decrypt.Request.builder()
                                .encryptionKeyName(ENCRYPTION_KEY)
                                .cipherText(encrypted))
                .forSingle(response -> res.send(String.valueOf(response.decrypted().toDecodedString())))
                .exceptionally(res::send);
    }

    private void encryptSecret(ServerRequest req, ServerResponse res) {
        String secret = req.path().param("text");

        secrets.encrypt(Encrypt.Request.builder()
                                .encryptionKeyName(ENCRYPTION_KEY)
                                .data(Base64Value.create(secret)))
                .forSingle(response -> res.send(response.encrypted().cipherText()))
                .exceptionally(res::send);
    }

    private void hmac(ServerRequest req, ServerResponse res) {
        secrets.hmac(Hmac.Request.builder()
                             .hmacKeyName(ENCRYPTION_KEY)
                             .data(SECRET_STRING))
                .forSingle(response -> res.send(response.hmac()))
                .exceptionally(res::send);
    }

    private void sign(ServerRequest req, ServerResponse res) {
        secrets.sign(Sign.Request.builder()
                             .signatureKeyName(SIGNATURE_KEY)
                             .data(SECRET_STRING))
                .forSingle(response -> res.send(response.signature()))
                .exceptionally(res::send);
    }

    private void verifyHmac(ServerRequest req, ServerResponse res) {
        String hmac = req.path().param("text");

        secrets.verify(Verify.Request.builder()
                               .digestKeyName(ENCRYPTION_KEY)
                               .data(SECRET_STRING)
                               .hmac(hmac))
                .forSingle(response -> res.send("Valid: " + response.isValid()))
                .exceptionally(res::send);
    }

    private void verify(ServerRequest req, ServerResponse res) {
        String signature = req.path().param("text");

        secrets.verify(Verify.Request.builder()
                               .digestKeyName(SIGNATURE_KEY)
                               .data(SECRET_STRING)
                               .signature(signature))
                .forSingle(response -> res.send("Valid: " + response.isValid()))
                .exceptionally(res::send);
    }

    private void batch(ServerRequest req, ServerResponse res) {
        String[] data = new String[] {"one", "two", "three", "four"};
        EncryptBatch.Request request = EncryptBatch.Request.builder()
                .encryptionKeyName(ENCRYPTION_KEY);
        DecryptBatch.Request decryptRequest = DecryptBatch.Request.builder()
                .encryptionKeyName(ENCRYPTION_KEY);

        for (String dato : data) {
            request.addBatch(EncryptBatch.Batch.create(Base64Value.create(dato)));
        }
        secrets.encrypt(request)
                .map(EncryptBatch.Response::batchResult)
                .flatMapSingle(batchResult -> {
                    for (Encrypt.Encrypted encrypted : batchResult) {
                        System.out.println("Encrypted: " + encrypted.cipherText());
                        decryptRequest.addBatch(DecryptBatch.Batch.create(encrypted.cipherText()));
                    }
                    return secrets.decrypt(decryptRequest);
                })
                .forSingle(response -> {
                    List<Base64Value> base64Values = response.batchResult();
                    for (int i = 0; i < data.length; i++) {
                        String decryptedValue = base64Values.get(i).toDecodedString();
                        if (!data[i].equals(decryptedValue)) {
                            res.send("Data at index " + i + " is invalid. Decrypted " + decryptedValue
                                             + ", expected: " + data[i]);
                            return;
                        }
                    }
                    res.send("Batch encryption/decryption completed");
                })
                .exceptionally(res::send);
    }

}
