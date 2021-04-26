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

/**
 * Decrypt Batch request and response.
 */
public final class DecryptBatch {
    private DecryptBatch() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends VaultRequest<Request> {
        private String encryptionKeyName;

        private Request() {
        }

        /**
         * Fluent API builder for configuring a request.
         * The request builder is passed as is, without a build method.
         * The equivalent of a build method is {@link #toJson(javax.json.JsonBuilderFactory)}
         * used by the {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @return new request builder
         */
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
         *
         * @param batchEntry batch entry to add to this batch request
         * @return updated request
         */
        public Request addEntry(BatchEntry batchEntry) {
            return addToArray("batch_input", batchEntry);
        }

        String encryptionKeyName() {
            if (encryptionKeyName == null) {
                throw new ApiException("Encryption key name is required");
            }
            return encryptionKeyName;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends VaultResponse {
        private final List<Base64Value> batchResult;

        private Response(Builder builder) {
            super(builder);
            JsonObject data = builder.entity().getJsonObject("data");
            List<Base64Value> batchResults = new LinkedList<>();
            JsonArray jsonArray = data.getJsonArray("batch_results");
            for (JsonValue jsonValue : jsonArray) {
                batchResults.add(Base64Value.createFromEncoded(((JsonObject) jsonValue).getString("plaintext")));
            }
            this.batchResult = List.copyOf(batchResults);
        }

        static Builder builder() {
            return new Builder();
        }

        /**
         * Batch result, each element of the list is a single decrypted secret, in the same order the batch was created.
         *
         * @return result of batch decryption
         */
        public List<Base64Value> batchResult() {
            return batchResult;
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

    /**
     * Definition of a batch entry.
     */
    public static class BatchEntry extends ApiJsonBuilder<BatchEntry> {
        private BatchEntry() {
        }

        /**
         * A new builder for a batch entry.
         *
         * @return a new batch entry
         */
        public static BatchEntry builder() {
            return new BatchEntry();
        }

        /**
         * Create a new entry from cipher text.
         *
         * @param cipherText cipher text as returned by an encrypt method
         * @return a new batch entry
         */
        public static BatchEntry create(String cipherText) {
            return builder().cipherText(cipherText);
        }

        /**
         * Configure the cipher text to be decrypted.
         *
         * @param cipherText cipher text
         * @return updated entry
         */
        public BatchEntry cipherText(String cipherText) {
            return add("ciphertext", cipherText);
        }

        /**
         * Configure context data.
         *
         * @param value base64 context
         * @return updated entry
         */
        public BatchEntry context(Base64Value value) {
            return add("context", value.toBase64());
        }

        /**
         * Configure nonce.
         *
         * @param value base64 nonce
         * @return updated entry
         */
        public BatchEntry nonce(Base64Value value) {
            return add("nonce", value.toBase64());
        }
    }
}
