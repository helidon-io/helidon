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
import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.integrations.vault.VaultRequest;
import io.helidon.integrations.vault.VaultResponse;

/**
 * Sign request and response.
 */
public final class Sign {
    private Sign() {
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

        /**
         * Signature algorithm {@value}.
         *
         * @see #signatureAlgorithm(String)
         */
        public static final String SIGNATURE_ALGORITHM_PSS = "pss";
        /**
         * Signature algorithm {@value}.
         *
         * @see #signatureAlgorithm(String)
         */
        public static final String SIGNATURE_ALGORITHM_PKCS1_V15 = "pkcs1v15";

        /**
         * Marshalling algorithm {@value}.
         *
         * @see #marshalingAlgorithm(String)
         */
        public static final String MARSHALLING_ALGORITHM_ASN_1 = "asn1";
        /**
         * Marshalling algorithm {@value}.
         *
         * @see #marshalingAlgorithm(String)
         */
        public static final String MARSHALLING_ALGORITHM_JWS = "jws";

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
        public Request signatureKeyName(String signatureKeyName) {
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
        public Request signatureKeyVersion(int version) {
            return add("key_version", version);
        }

        /**
         * The data to sign.
         *
         * @param value value to encrypt
         * @return updated request
         * @see io.helidon.integrations.common.rest.Base64Value#create(String)
         * @see io.helidon.integrations.common.rest.Base64Value#create(byte[])
         */
        public Request data(Base64Value value) {
            return add("input", value.toBase64());
        }

        /**
         * Specifies the context for key derivation. This is required if key derivation is enabled for this key; currently only
         * available with ed25519 keys.
         *
         * @param value context
         * @return updated request
         */
        public Request context(Base64Value value) {
            return add("context", value.toBase64());
        }

        /**
         * Set to true when the input is already hashed. If the key type is rsa-2048, rsa-3072 or rsa-4096, then the algorithm
         * used to hash the input should be indicated by the hash_algorithm parameter. Just as the value to sign should be the
         * base64-encoded representation of the exact binary data you want signed, when set, input is expected to be
         * base64-encoded binary hashed data, not hex-formatted. (As an example, on the command line, you could generate a
         * suitable input via openssl dgst -sha256 -binary | base64.).
         *
         * @param preHashed whether the data is pre hashed or not
         * @return updated erqust
         */
        public Request preHashed(boolean preHashed) {
            return add("prehashed", preHashed);
        }

        /**
         * When using a RSA key, specifies the RSA signature algorithm to use for signing. Supported signature types are:
         *
         * pss
         * pkcs1v15
         *
         * See signature algorithm constants on this class.
         *
         * @param signatureAlgorithm signature algorithm to use
         * @return updated request
         */
        public Request signatureAlgorithm(String signatureAlgorithm) {
            return add("signature_algorithm", signatureAlgorithm);
        }

        /**
         * Specifies the way in which the signature should be marshaled. This currently only applies to ECDSA keys. Supported
         * types are:
         * asn1: The default, used by OpenSSL and X.509
         * jws: The version used by JWS (and thus for JWTs). Selecting this will also change the output encoding to URL-safe
         * Base64 encoding instead of standard Base64-encoding.
         *
         * @param marshalingAlgorithm marshaling algorithm
         * @return updated request
         */
        public Request marshalingAlgorithm(String marshalingAlgorithm) {
            return add("marshaling_algorithm", marshalingAlgorithm);
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

        String signatureKeyName() {
            if (signatureKeyName == null) {
                throw new ApiException("Encryption key name is required");
            }
            return signatureKeyName;
        }

    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends VaultResponse {
        private final String signature;

        private Response(Builder builder) {
            super(builder);
            JsonObject data = builder.entity().getJsonObject("data");
            this.signature = data.getString("signature");
        }

        static Builder builder() {
            return new Builder();
        }

        /**
         * Signature string.
         *
         * @return signature as a string
         */
        public String signature() {
            return signature;
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
