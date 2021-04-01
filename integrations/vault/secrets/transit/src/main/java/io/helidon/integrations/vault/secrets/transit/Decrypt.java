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

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.common.rest.ApiException;
import io.helidon.integrations.common.rest.ApiJsonParser;
import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.integrations.vault.VaultRequest;
import io.helidon.integrations.vault.VaultResponse;

public final class Decrypt {
    private Decrypt() {
    }

    public static class Request extends VaultRequest<Request> {
        private String encryptionKeyName;

        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        /**
         * Specifies the name of the encryption key to decrypt against.
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
         * The data to decrypt (in current version something like {@code vault:v1:base64-text}.
         *
         * @param value value to encrypt
         * @return updated request
         */
        public Request cipherText(String value) {
            return add("ciphertext", value);
        }

        /**
         * Specifies the context for key derivation. This is required if key derivation is enabled for this key.
         *
         * @param value context
         * @return updated request
         */
        public Request context(Base64Value value) {
            return add("context", value.toBase64());
        }

        /**
         * Specifies a base64 encoded nonce value used during encryption. Must be provided if convergent encryption is enabled
         * for this key and the key was generated with Vault 0.6.1. Not required for keys created in 0.6.2+.
         *
         * @param value nonce
         * @return updated request
         */
        public Request nonce(Base64Value value) {
            return add("nonce", value.toBase64());
        }

        String encryptionKeyName() {
            if (encryptionKeyName == null) {
                throw new ApiException("Encryption key name is required");
            }
            return encryptionKeyName;
        }
    }

    public static class Response extends VaultResponse {
        private final Base64Value decrypted;

        private Response(Builder builder) {
            super(builder);
            JsonObject data = builder.entity().getJsonObject("data");
            this.decrypted = Base64Value.createFromEncoded(data.getString("plaintext"));
        }

        public Base64Value decrypted() {
            return decrypted;
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

    public static class Encrypted extends ApiJsonParser {
        private final String encrypted;
        private final int keyVersion;

        Encrypted(JsonObject json) {
            this.encrypted = json.getString("ciphertext");
            this.keyVersion = json.getInt("key_version");
        }

        public String encrypted() {
            return encrypted;
        }

        public int keyVersion() {
            return keyVersion;
        }

        @Override
        public String toString() {
            return "Encrypted{" +
                    "encrypted=" + encrypted +
                    ", keyVersion=" + keyVersion +
                    '}';
        }
    }
}
