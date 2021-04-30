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

package io.helidon.integrations.oci.vault;

import java.util.Optional;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.common.Base64Value;
import io.helidon.integrations.oci.connect.OciApiException;
import io.helidon.integrations.oci.connect.OciRequestBase;

/**
 * Encrypt request and response.
 */
public final class Encrypt {
    private Encrypt() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends OciRequestBase<Request> {
        /**
         * Default encryption algorithm used by encryption/decryption is {@value}.
         */
        public static final String ALGORITHM_AES_256_GCM = "AES_256_GCM";
        /**
         * {@value} algorithm.
         */
        public static final String ALGORITHM_RSA_OAEP_SHA_1 = "RSA_OAEP_SHA_1";
        /**
         * {@value} algorithm.
         */
        public static final String ALGORITHM_RSA_OAEP_SHA_256 = "RSA_OAEP_SHA_256";

        private String keyId;

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
         * The data to encrypt.
         *
         * @param value value to encrypt
         * @return updated request
         * @see Base64Value#create(String)
         * @see Base64Value#create(byte[])
         */
        public Request data(Base64Value value) {
            return add("plaintext", value.toBase64());
        }

        /**
         * The OCID of the key to encrypt with.
         * Required.
         *
         * @param keyOcid OCID of the key
         * @return updated request
         */
        public Request keyId(String keyOcid) {
            this.keyId = keyOcid;
            return add("keyId", keyOcid);
        }

        /**
         * Information that can be used to provide an encryption context for the encrypted data. The length of the string
         * representation of the associated data must be fewer than 4096 characters.
         * Optional.
         *
         * @param contextData context
         * @return updated request
         */
        public Request context(String contextData) {
            return add("associatedData", contextData);
        }

        /**
         * The encryption algorithm to use to encrypt and decrypt data with a customer-managed key. AES_256_GCM indicates that
         * the key is a symmetric key that uses the Advanced Encryption Standard (AES) algorithm and that the mode of
         * encryption is the Galois/Counter Mode (GCM). RSA_OAEP_SHA_1 indicates that the key is an asymmetric key that uses
         * the RSA encryption algorithm and uses Optimal Asymmetric Encryption Padding (OAEP). RSA_OAEP_SHA_256 indicates that
         * the key is an asymmetric key that uses the RSA encryption algorithm with a SHA-256 hash and uses OAEP.
         * Optional, defaults to {@code AES_256_GCM}.
         *
         * @param algorithm algorithm to use
         * @return updated request
         */
        public Request algorithm(String algorithm) {
            return add("encryptionAlgorithm", algorithm);
        }

        /**
         * The OCID of the key version used to encrypt the ciphertext.
         * Optional.
         *
         * @param versionOcid OCID of the key version
         * @return updated request
         */
        public Request keyVersionId(String versionOcid) {
            return add("keyVersionId", versionOcid);
        }

        String keyId() {
            if (keyId == null) {
                throw new OciApiException("Encrypt.Request keyId must be defined");
            }
            return keyId;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends ApiEntityResponse {
        private final String cipherText;
        private final Optional<String> encryptionAlgorithm;
        private final Optional<String> keyId;
        private final Optional<String> keyVersionId;

        private Response(Builder builder) {
            super(builder);

            JsonObject json = builder.entity();
            this.cipherText = json.getString("ciphertext");
            this.encryptionAlgorithm = toString(json, "encryptionAlgorithm");
            this.keyId = toString(json, "keyId");
            this.keyVersionId = toString(json, "keyVersionId");
        }

        static Builder builder() {
            return new Builder();
        }

        /**
         * Cipher text that can be passed to another service and then used
         * to obtain the decrypted secret.
         *
         * @return cipher text
         */
        public String cipherText() {
            return cipherText;
        }

        /**
         * Encryption algorithm used to encrypt the secret.
         *
         * @return encryption algorithm
         */
        public Optional<String> encryptionAlgorithm() {
            return encryptionAlgorithm;
        }

        /**
         * Encryption key OCID.
         *
         * @return key OCID
         */
        public Optional<String> keyId() {
            return keyId;
        }

        /**
         * Encryption key version OCID.
         *
         * @return key version ocid
         */
        public Optional<String> keyVersionId() {
            return keyVersionId;
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
