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

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.common.rest.Base64Value;
import io.helidon.integrations.oci.connect.OciRequestBase;

/**
 * Sign request and response.
 */
public final class Verify {
    private Verify() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends OciRequestBase<Request> {
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
         * The base64-encoded binary data object denoting the message or message digest to sign. You can have a message up to
         * 4096 bytes in size. To sign a larger message, provide the message digest.
         *
         * @param value value to verify
         * @return updated request
         * @see io.helidon.integrations.common.rest.Base64Value#create(String)
         * @see io.helidon.integrations.common.rest.Base64Value#create(byte[])
         */
        public Request message(Base64Value value) {
            return add("message", value.toBase64());
        }

        /**
         * The base64-encoded binary data object denoting the cryptographic signature generated for the message.
         *
         * @param signature signature to verify
         * @return updated request
         */
        public Request signature(Base64Value signature) {
            return add("signature", signature.toBase64());
        }

        /**
         * The OCID of the key to sign with.
         * Required.
         *
         * @param keyOcid OCID of the key
         * @return updated request
         */
        public Request keyId(String keyOcid) {
            return add("keyId", keyOcid);
        }

        /**
         * Denotes whether the value of the message parameter is a raw message or a message digest. The default value, RAW,
         * indicates a message. To indicate a message digest, use {@value Sign.Request#MESSAGE_TYPE_DIGEST}.
         *
         * @param type type to use
         * @return updated request
         * @see Sign.Request#MESSAGE_TYPE_DIGEST
         * @see Sign.Request#MESSAGE_TYPE_RAW
         */
        public Request messageType(String type) {
            return add("messageType", type);
        }

        /**
         * The algorithm to use to sign the message or message digest. For RSA keys, supported signature schemes include PKCS
         * #1 and RSASSA-PSS, along with different hashing algorithms. For ECDSA keys, ECDSA is the supported signature scheme
         * with different hashing algorithms. When you pass a message digest for signing, ensure that you specify the same
         * hashing algorithm as used when creating the message digest.
         * Required.
         * See algorithm constants on {@link Sign.Request} class.
         *
         * @param algorithm algorithm to use
         * @return updated request
         */
        public Request algorithm(String algorithm) {
            return add("signingAlgorithm", algorithm);
        }

        /**
         * The OCID of the key version used to sing the message.
         * Optional.
         *
         * @param versionOcid OCID of the key version
         * @return updated request
         */
        public Request keyVersionId(String versionOcid) {
            return add("keyVersionId", versionOcid);
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends ApiEntityResponse {
        private final boolean valid;

        private Response(Builder builder) {
            super(builder);

            JsonObject json = builder.entity();
            this.valid = json.getBoolean("isSignatureValid");
        }

        static Builder builder() {
            return new Builder();
        }

        /**
         * A Boolean value that indicates whether the signature was verified.
         *
         * @return whether the signature is valid
         */
        public boolean isValid() {
            return valid;
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
