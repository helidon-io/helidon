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

import java.time.Instant;
import java.util.Optional;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.integrations.oci.connect.OciApiException;
import io.helidon.integrations.oci.connect.OciRequestBase;
import io.helidon.integrations.oci.connect.OciResponseParser;

/**
 * Request and response for getting secret metadata.
 */
public final class GetKey {
    private GetKey() {
    }

    public static class Request extends OciRequestBase<Request> {
        private String keyId;

        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        public static Request create(String keyId) {
            return builder().keyId(keyId);
        }

        public Request keyId(String keyId) {
            this.keyId = keyId;
            return this;
        }

        public String keyId() {
            if (keyId == null) {
                throw new OciApiException("GetKey.Request keyId must be defined");
            }
            return keyId;
        }

        @Override
        public Optional<JsonObject> toJson(JsonBuilderFactory factory) {
            return Optional.empty();
        }
    }

    public static class Response extends OciResponseParser {
        private final String keyId;
        private final String currentKeyVersionId;
        private final String compartmentId;
        private final String displayName;
        private final String lifecycleState;
        private final Instant created;
        private final String vaultId;

        private Response(JsonObject json) {
            this.keyId = json.getString("id");
            this.currentKeyVersionId = json.getString("currentKeyVersion");
            this.compartmentId = json.getString("compartmentId");
            this.displayName = json.getString("displayName");
            this.lifecycleState = json.getString("lifecycleState");
            this.created = getInstant(json, "timeCreated");
            this.vaultId = json.getString("vaultId");
        }

        static Response create(JsonObject json) {
            return new Response(json);
        }

        public String keyId() {
            return keyId;
        }

        /**
         * The OCID of the key version used in cryptographic operations. During key rotation, the service might be in a
         * transitional state where this or a newer key version are used intermittently. The currentKeyVersion property is
         * updated when the service is guaranteed to use the new key version for all subsequent encryption operations.
         *
         * @return current key version ID
         */
        public String currentKeyVersionId() {
            return currentKeyVersionId;
        }

        /**
         * The OCID of the compartment that contains this master encryption key.
         *
         * @return compartment ID
         */
        public String compartmentId() {
            return compartmentId;
        }

        /**
         * A user-friendly name for the key. It does not have to be unique, and it is changeable. Avoid entering confidential information.
         *
         * @return display name
         */
        public String displayName() {
            return displayName;
        }

        /**
         * The key's current lifecycle state.
         *
         * @return lifecycle state
         */
        public String lifecycleState() {
            return lifecycleState;
        }

        /**
         * The date and time the key was created.
         *
         * @return created instant
         */
        public Instant created() {
            return created;
        }

        /**
         * The OCID of the vault that contains this key.
         *
         * @return Vault ID
         */
        public String vaultId() {
            return vaultId;
        }
    }
}
