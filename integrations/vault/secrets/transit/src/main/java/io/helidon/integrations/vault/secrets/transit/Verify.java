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

public final class Verify {
    private Verify() {
    }

    public static class Request extends VaultRequest<Request> {
        private String signatureKeyName;

        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        /**
         * Specifies the name of the encryption key to verify against.
         * Required.
         *
         * @param signatureKeyName name of the key
         * @return updated request
         */
        public Request digestKeyName(String signatureKeyName) {
            this.signatureKeyName = signatureKeyName;
            return this;
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
         * Specifies the signature output from the /transit/sign function. Either this must be supplied or hmac must be supplied.
         *
         * @param signature signature string as provided by
         * {@link TransitSecrets#sign(io.helidon.integrations.vault.secrets.transit.Sign.Request)}
         * @return updated request
         */
        public Request signature(String signature) {
            return add("signature", signature);
        }

        /**
         * Specifies the signature output from the /transit/hmac function. Either this must be supplied or signature must be supplied.
         *
         * @param hmac hmac sting as provided by transit hmac
         * @return updated request
         */
        public Request hmac(String hmac) {
            return add("hmac", hmac);
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
         * @return udpated request
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

        String digestKeyName() {
            if (signatureKeyName == null) {
                throw new ApiException("Encryption key name is required");
            }
            return signatureKeyName;
        }
    }

    public static class Response extends VaultResponse {
        private final boolean valid;

        private Response(Builder builder) {
            super(builder);
            JsonObject data = builder.entity().getJsonObject("data");
            this.valid = data.getBoolean("valid");
        }

        static Builder builder() {
            return new Builder();
        }

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
