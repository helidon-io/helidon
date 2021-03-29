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

package io.helidon.integrations.vault.secrets.transit;

import java.util.LinkedList;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.common.rest.ApiException;
import io.helidon.integrations.common.rest.ApiJsonBuilder;
import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.integrations.vault.VaultRequest;
import io.helidon.integrations.vault.VaultResponse;

public final class EncryptBatch {
    private EncryptBatch() {
    }

    public static class Request extends VaultRequest<Request> {
        private String encryptionKeyName;

        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        /**
         * Specifies the name of the encryption key to encrypt against.
         * Required.
         *
         * @param encryptionKeyName name of the key
         * @return updated request
         */
        public Request encryptionKeyName(String encryptionKeyName) {
            this.encryptionKeyName = encryptionKeyName;
            return this;
        }

        /**
         * Specifies the version of the key to use for encryption. If not set, uses the latest version. Must be greater than or
         * equal to the key's {@code min_encryption_version}, if set.
         * Optional.
         *
         * @param version key version
         * @return updated request
         */
        public Request encryptionKeyVersion(int version) {
            return add("key_version", version);
        }

        /**
         * This parameter is required when encryption key is expected to be created. When performing an upsert operation, the type
         * of key to create.
         * <p>
         * Defaults to {@code aes256-gcm96}.
         *
         * @param type type of the encryption key
         * @return updated request
         */
        public Request encryptionKeyType(String type) {
            return add("type", type);
        }

        /**
         * This parameter will only be used when a key is expected to be created. Whether to support convergent encryption.
         * This is
         * only supported when using a key with key derivation enabled and will require all requests to carry both a context and
         * 96-bit (12-byte) nonce. The given nonce will be used in place of a randomly generated nonce. As a result, when the same
         * context and nonce are supplied, the same ciphertext is generated. It is very important when using this mode that you
         * ensure that all nonces are unique for a given context. Failing to do so will severely impact the ciphertext's security.
         *
         * @param convergent convergetn encryption
         * @return updated request
         */
        public Request convergentEncryption(String convergent) {
            return add("convergent_encryption", convergent);
        }

        /**
         * Specifies a list of items to be encrypted in a single batch. When this parameter is set, if the parameters 'plaintext',
         * 'context' and 'nonce' are also set, they will be ignored.
         */
        public Request addBatch(Batch batch) {
            return addToArray("batch_input", batch);
        }

        String encryptionKeyName() {
            if (encryptionKeyName == null) {
                throw new ApiException("Encryption key name is required");
            }
            return encryptionKeyName;
        }
    }

    public static class Response extends VaultResponse {
        private final List<Encrypt.Encrypted> batchResult;

        private Response(Builder builder) {
            super(builder);
            JsonObject data = builder.entity().getJsonObject("data");
            List<Encrypt.Encrypted> batchResults = new LinkedList<>();
            JsonArray jsonArray = data.getJsonArray("batch_results");
            for (JsonValue jsonValue : jsonArray) {
                batchResults.add(new Encrypt.Encrypted((JsonObject) jsonValue));
            }
            this.batchResult = List.copyOf(batchResults);
        }

        public List<Encrypt.Encrypted> batchResult() {
            return batchResult;
        }

        static Builder builder() {
            return new Builder();
        }

        static final class Builder extends ApiEntityResponse.Builder<Builder, Response, JsonObject> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }

    public static class Batch extends ApiJsonBuilder<Batch> {
        private Batch() {
        }

        public static Batch builder() {
            return new Batch();
        }

        public static Batch create(Base64Value base64Value) {
            return builder().data(base64Value);
        }

        public Batch data(Base64Value value) {
            return add("plaintext", value.toBase64());
        }

        public Batch context(Base64Value value) {
            return add("context", value.toBase64());
        }

        public Batch nonce(Base64Value value) {
            return add("nonce", value.toBase64());
        }
    }
}
