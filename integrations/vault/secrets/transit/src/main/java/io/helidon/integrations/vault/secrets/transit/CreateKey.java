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

import io.helidon.integrations.common.rest.ApiException;
import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Create Key request and response.
 */
public final class CreateKey {
    private CreateKey() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends VaultRequest<Request> {
        private String name;

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
         * Specifies the name of the encryption key to create.
         *
         * @param name key name
         * @return updated request
         */
        public Request name(String name) {
            this.name = name;
            return this;
        }

        /**
         * If enabled, the key will support convergent encryption, where the same plaintext creates the same ciphertext. This
         * requires derived to be set to true. When enabled, each encryption(/decryption/rewrap/datakey) operation will derive
         * a nonce value rather than randomly generate it.
         * Optional, defaults to {@code false}.
         *
         * @param convergent whether the key supports convergent encryption
         * @return updated request
         */
        public Request convergentEncryption(boolean convergent) {
            return add("convergent_encryption", convergent);
        }

        /**
         * Specifies if key derivation is to be used. If enabled, all encrypt/decrypt requests to this named key must provide a
         * context which is used for key derivation.
         * Optional, defaults to {@code false}.
         *
         * @param derived whether key derivation should be used
         * @return updated request
         */
        public Request derived(boolean derived) {
            return add("derived", derived);
        }

        /**
         * Enables keys to be exportable. This allows for all the valid keys in the key ring to be exported. Once set, this
         * cannot be disabled.
         * Optional, defaults to {@code false}.
         *
         * @param exportable whether the key is exportable
         * @return updated request
         */
        public Request exportable(boolean exportable) {
            return add("exportable", exportable);
        }

        /**
         * If set, enables taking backup of named key in the plaintext format. Once set, this cannot be disabled.
         * Optional, defaults to {@code false}.
         *
         * @param allowBackup whether to allow plain text backup
         * @return updated request
         */
        public Request allowPlaintextBackup(boolean allowBackup) {
            return add("allow_plaintext_backup", allowBackup);
        }

        /**
         * Specifies the type of key to create. The currently-supported types are:
         *
         * <ul>
         *     <li>{@code aes128-gcm96} - AES-128 wrapped with GCM using a 96-bit nonce size AEAD (symmetric, supports derivation and convergent encryption)</li>
         *     <li>{@code aes256-gcm96} - AES-256 wrapped with GCM using a 96-bit nonce size AEAD (symmetric, supports derivation and convergent encryption, default)</li>
         *     <li>{@code chacha20-poly1305 - ChaCha20-Poly1305 AEAD (symmetric, supports derivation and convergent encryption)} - </li>
         *     <li>{@code ed25519} - ED25519 (asymmetric, supports derivation). When using derivation, a sign operation with the same context will derive the same key and signature; this is a signing analogue to convergent_encryption</li>
         *     <li>{@code ecdsa-p256} - ECDSA using the P-256 elliptic curve (asymmetric)</li>
         *     <li>{@code ecdsa-p384} - ECDSA using the P-384 elliptic curve (asymmetric)</li>
         *     <li>{@code ecds-p521} - ECDSA using the P-521 elliptic curve (asymmetric)</li>
         *     <li>{@code rsa-2048} -  RSA with bit size of 2048 (asymmetric)</li>
         *     <li>{@code rsa-3072} -  RSA with bit size of 3072 (asymmetric)</li>
         *     <li>{@code rsa-4096} -  RSA with bit size of 4096 (asymmetric)</li>
         * </ul>
         *
         * Optional, defaults to {@code aes256-gcm96}.
         *
         * @param type type to use
         * @return updated request
         */
        public Request type(String type) {
            return add("type", type);
        }

        String name() {
            if (name == null) {
                throw new ApiException("Vault CreateKey request must have name configured");
            }
            return name;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends ApiResponse {
        private Response(Builder builder) {
            super(builder);
        }

        static Builder builder() {
            return new Builder();
        }

        static final class Builder extends ApiResponse.Builder<Builder, Response> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }
}
