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
 * Get Vault request and response.
 */
public final class GetVault {
    private GetVault() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends OciRequestBase<Request> {
        private String vaultId;

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
         * Create a request for a vault OCID.
         *
         * @param vaultId vault OCID
         * @return a new request
         */
        public static Request create(String vaultId) {
            return builder().vaultId(vaultId);
        }

        /**
         * Vault OCID.
         *
         * @param vaultId vault ID
         * @return updated request
         */
        public Request vaultId(String vaultId) {
            this.vaultId = vaultId;
            return this;
        }

        String vaultId() {
            if (vaultId == null) {
                throw new OciApiException("GetVault.Request vaultId must be defined");
            }
            return vaultId;
        }

        @Override
        public Optional<JsonObject> toJson(JsonBuilderFactory factory) {
            return Optional.empty();
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends OciResponseParser {
        private final String vaultId;
        private final String compartmentId;
        private final String cryptoEndpoint;
        private final String managementEndpoint;
        private final String displayName;
        private final String lifecycleState;
        private final Instant created;
        private final String vaultType;
        private final String wrappingKeyId;

        private Response(JsonObject json) {
            this.vaultId = json.getString("id");
            this.compartmentId = json.getString("compartmentId");
            this.cryptoEndpoint = json.getString("cryptoEndpoint");
            this.managementEndpoint = json.getString("managementEndpoint");
            this.displayName = json.getString("displayName");
            this.lifecycleState = json.getString("lifecycleState");
            this.created = getInstant(json, "timeCreated");
            this.vaultType = json.getString("vaultType");
            this.wrappingKeyId = json.getString("wrappingkeyId");
        }

        static Response create(JsonObject json) {
            return new Response(json);
        }

        /**
         * The OCID of the compartment that contains this vault.
         *
         * @return compartment ID
         */
        public String compartmentId() {
            return compartmentId;
        }

        /**
         * A user-friendly name for the vault. It does not have to be unique, and it is changeable. Avoid entering confidential
         * information.
         *
         * @return display name
         */
        public String displayName() {
            return displayName;
        }

        /**
         * The vault's current lifecycle state.
         *
         * @return lifecycle state
         */
        public String lifecycleState() {
            return lifecycleState;
        }

        /**
         * The date and time the vault was created.
         *
         * @return created instant
         */
        public Instant created() {
            return created;
        }

        /**
         * The OCID of the vault.
         *
         * @return Vault ID
         */
        public String vaultId() {
            return vaultId;
        }

        /**
         * The service endpoint to perform cryptographic operations against. Cryptographic operations include Encrypt, Decrypt,
         * and GenerateDataEncryptionKey operations.
         *
         * @return cryptographic endpoint
         */
        public String cryptoEndpoint() {
            return cryptoEndpoint;
        }

        /**
         * The service endpoint to perform management operations against. Management operations include "Create," "Update,"
         * "List," "Get," and "Delete" operations.
         *
         * @return management endpoint
         */
        public String managementEndpoint() {
            return managementEndpoint;
        }

        /**
         * The type of vault. Each type of vault stores the key with different degrees of isolation and has different options
         * and pricing.
         *
         * Allowed values are:
         *
         * <ul>
         *     <li>{@code VIRTUAL_PRIVATE}</li>
         *     <li>{@code DEFAULT}</li>
         * </ul>
         *
         * @return vault type
         */
        public String vaultType() {
            return vaultType;
        }

        /**
         * The OCID of the vault's wrapping key.
         *
         * @return wrapping key id
         */
        public String wrappingKeyId() {
            return wrappingKeyId;
        }
    }
}
