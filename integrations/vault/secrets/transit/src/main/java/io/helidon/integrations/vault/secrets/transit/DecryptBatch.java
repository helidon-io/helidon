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

public final class DecryptBatch {
    private DecryptBatch() {
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
        private final List<Base64Value> batchResult;

        private Response(Builder builder) {
            super(builder);
            JsonObject data = builder.entity().getJsonObject("data");
            List<Base64Value> batchResults = new LinkedList<>();
            JsonArray jsonArray = data.getJsonArray("batch_results");
            for (JsonValue jsonValue : jsonArray) {
                batchResults.add(Base64Value.createFromEncoded(((JsonObject)jsonValue).getString("plaintext")));
            }
            this.batchResult = List.copyOf(batchResults);
        }

        public List<Base64Value> batchResult() {
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

        public static Batch create(String encryptedValue) {
            return builder().data(encryptedValue);
        }

        public Batch data(String value) {
            return add("ciphertext", value);
        }

        public Batch context(Base64Value value) {
            return add("context", value.toBase64());
        }

        public Batch nonce(Base64Value value) {
            return add("nonce", value.toBase64());
        }
    }
}
