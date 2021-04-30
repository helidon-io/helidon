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
import io.helidon.common.Base64Value;
import io.helidon.integrations.vault.VaultRequest;
import io.helidon.integrations.vault.VaultResponse;

/**
 * HMAC request and response.
 */
public final class Hmac {
    private Hmac() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends VaultRequest<Request> {
        /**
         * Hash algorithm {@value}.
         *
         * @see #hashAlgorithm(String)
         */
        public static final String HASH_ALGORITHM_SHA2_224 = "sha2-224";
        /**
         * Hash algorithm {@value}.
         *
         * @see #hashAlgorithm(String)
         */
        public static final String HASH_ALGORITHM_SHA2_256 = "sha2-256";
        /**
         * Hash algorithm {@value}.
         *
         * @see #hashAlgorithm(String)
         */
        public static final String HASH_ALGORITHM_SHA2_384 = "sha2-384";
        /**
         * Hash algorithm {@value}.
         *
         * @see #hashAlgorithm(String)
         */
        public static final String HASH_ALGORITHM_SHA2_512 = "sha2-512";

        private String signatureKeyName;

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
         * Specifies the name of the encryption key to sign against.
         * Required.
         *
         * @param signatureKeyName name of the key
         * @return updated request
         */
        public Request hmacKeyName(String signatureKeyName) {
            this.signatureKeyName = signatureKeyName;
            return this;
        }

        /**
         * Specifies the version of the key to use for signatures. If not set, uses the latest version. Must be greater than or
         * equal to the key's {@code min_encryption_version}, if set.
         * Optional.
         *
         * @param version key version
         * @return updated request
         */
        public Request hmacKeyVersion(int version) {
            return add("key_version", version);
        }

        /**
         * The data to sign.
         *
         * @param value value to encrypt
         * @return updated request
         * @see Base64Value#create(String)
         * @see Base64Value#create(byte[])
         */
        public Request data(Base64Value value) {
            return add("input", value.toBase64());
        }

        /**
         * Specifies the hash algorithm to use for supporting key types (notably, not including ed25519 which specifies its own
         * hash algorithm).
         * See hash algorithm constants on this class.
         *
         * @param hashAlgorithm algorithm to use
         * @return updated request
         */
        public Request hashAlgorithm(String hashAlgorithm) {
            return add("hash_algorithm", hashAlgorithm);
        }

        String hmacKeyName() {
            if (signatureKeyName == null) {
                throw new ApiException("HMAC key name is required");
            }
            return signatureKeyName;
        }

    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends VaultResponse {
        private final String hmac;

        private Response(Builder builder) {
            super(builder);
            JsonObject data = builder.entity().getJsonObject("data");
            this.hmac = data.getString("hmac");
        }

        static Builder builder() {
            return new Builder();
        }

        /**
         * HMAC string.
         * @return HMAC string
         */
        public String hmac() {
            return hmac;
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
}
