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

package io.helidon.integrations.vault.auths.approle;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.vault.VaultResponse;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriterFactory;

/**
 * Generate secret ID request and response.
 * @see AppRoleAuthRx#generateSecretId(io.helidon.integrations.vault.auths.approle.GenerateSecretId.Request)
 */
public final class GenerateSecretId {
    private GenerateSecretId() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends AppRoleRequestBase<Request> {
        private static final JsonWriterFactory JSON_WRITER_FACTORY = Json.createWriterFactory(Map.of());
        private final Map<String, String> metadata = new HashMap<>();

        private Request() {
        }

        /**
         * Fluent API builder for configuring a request.
         * The request builder is passed as is, without a build method.
         * The equivalent of a build method is {@link #toJson(jakarta.json.JsonBuilderFactory)}
         * used by the {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @return new request builder
         */
        public static Request builder() {
            return new Request();
        }

        /**
         * This metadata will be set on tokens issued with this SecretID, and is logged in audit logs in plaintext.
         *
         * @param key name
         * @param value value
         * @return updated request
         */
        public Request addMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * CIDR blocks enforcing secret IDs to be used from specific set of IP addresses. If bound_cidr_list is set on the role,
         * then the list of CIDR blocks listed here should be a subset of the CIDR blocks listed on the role.
         *
         * @param cidr CIDR block
         * @return updated request
         */
        public Request addCidr(String cidr) {
            return addToArray("cidr_list", cidr);
        }

        /**
         * Specifies blocks of IP addresses
         * which can use the auth tokens generated by this SecretID. Overrides any role-set value but must be a subset.
         *
         * @param cidr CIDR block
         * @return updated request
         */
        public Request addTokenBoundCidr(String cidr) {
            return addToArray("token_bound_cidrs", cidr);
        }

        @Override
        protected void postBuild(JsonBuilderFactory factory, JsonObjectBuilder payload) {
            if (!metadata.isEmpty()) {
                JsonObjectBuilder metaJson = factory.createObjectBuilder();
                metadata.forEach(metaJson::add);

                StringWriter sw = new StringWriter();
                JSON_WRITER_FACTORY.createWriter(sw)
                        .writeObject(metaJson.build());
                String serialized = sw.toString();

                payload.add("meta", serialized);
            }
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends VaultResponse {
        private final String secretId;
        private final String secretIdAccessor;

        private Response(Builder builder) {
            super(builder);

            JsonObject data = builder.entity().getJsonObject("data");
            this.secretId = data.getString("secret_id");
            this.secretIdAccessor = data.getString("secret_id_accessor");
        }

        static Builder builder() {
            return new Builder();
        }

        /**
         * The generated secret ID.
         *
         * @return secret ID
         */
        public String secretId() {
            return secretId;
        }

        /**
         * Secret ID accessor.
         *
         * @return accessor
         */
        public String secretIdAccessor() {
            return secretIdAccessor;
        }

        static class Builder extends ApiEntityResponse.Builder<Builder, Response, JsonObject> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }
}
