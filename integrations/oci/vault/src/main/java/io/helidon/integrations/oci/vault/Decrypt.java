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
import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.integrations.oci.connect.OciApiException;
import io.helidon.integrations.oci.connect.OciRequestBase;

/**
 * Encrypt request and response.
 */
public final class Decrypt {
    private Decrypt() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends OciRequestBase<Request> {

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
         * The data to decrypt.
         *
         * @param cipherText encrypted data
         * @return updated request
         */
        public Request cipherText(String cipherText) {
            return add("ciphertext", cipherText);
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
         * @see io.helidon.integrations.oci.vault.Encrypt.Request#ALGORITHM_AES_256_GCM
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
        private final Base64Value decrypted;
        private final String checksum;
        private final Optional<String> encryptionAlgorithm;
        private final Optional<String> keyId;
        private final Optional<String> keyVersionId;

        private Response(Builder builder) {
            super(builder);

            JsonObject json = builder.entity();
            this.decrypted = Base64Value.createFromEncoded(json.getString("plaintext"));
            this.checksum = json.getString("plaintextChecksum");
            this.encryptionAlgorithm = toString(json, "encryptionAlgorithm");
            this.keyId = toString(json, "keyId");
            this.keyVersionId = toString(json, "keyVersionId");
        }

        static Builder builder() {
            return new Builder();
        }

        /**
         * Decrypted secret.
         *
         * @return decrypted value
         */
        public Base64Value decrypted() {
            return decrypted;
        }

        /**
         * Data checksum.
         *
         * @return checksum
         */
        public String checksum() {
            return checksum;
        }

        /**
         * Algorithm used.
         *
         * @return algorithm
         */
        public Optional<String> encryptionAlgorithm() {
            return encryptionAlgorithm;
        }

        /**
         * Decryption key ID.
         *
         * @return key ID
         */
        public Optional<String> keyId() {
            return keyId;
        }

        /**
         * Decryption key version ID.
         *
         * @return key version ID
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
