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

public final class UpdateKeyConfig {
    private UpdateKeyConfig() {
    }

    public static final class Request extends VaultRequest<Request> {
        private String name;

        private Request() {
        }

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
         * Specifies the minimum version of ciphertext allowed to be decrypted. Adjusting this as part of a key rotation policy
         * can prevent old copies of ciphertext from being decrypted, should they fall into the wrong hands. For signatures,
         * this value controls the minimum version of signature that can be verified against. For HMACs, this controls the
         * minimum version of a key allowed to be used as the key for verification.
         *
         * @param version version
         * @return updated request
         */
        public Request minDecryptionVersion(int version) {
            return add("min_decryption_version", version);
        }

        /**
         * Specifies the minimum version of the key that can be used to encrypt plaintext, sign payloads, or generate HMACs.
         * Must be 0 (which will use the latest version) or a value greater or equal to {@link #minDecryptionVersion(int)}.
         *
         * @param version version
         * @return updated request
         */
        public Request minEncryptionVersion(int version) {
            return add("min_encryption_version", version);
        }

        /**
         * Specifies if the key is allowed to be deleted.
         *
         * @param allowed whether is is allowed to delete the key
         * @return updated request
         */
        public Request allowDeletion(boolean allowed) {
            return add("deletion_allowed", allowed);
        }

        /**
         *  Enables keys to be exportable. This allows for all the valid keys in the key ring to be exported. Once set, this
         *  cannot be disabled.
         *
         * @param exportable whether the key should be exportable
         * @return updated request
         */
        public Request exportable(boolean exportable) {
            return add("exportable", exportable);
        }

        /**
         * If set, enables taking backup of named key in the plaintext format. Once set, this cannot be disabled.
         *
         * @param allowBackup whether to allow plaintext backup
         * @return updated request
         */
        public Request allowPlaintextBackup(boolean allowBackup) {
            return add("allow_plaintext_backup", allowBackup);
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
