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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.vault.VaultRequest;
import io.helidon.integrations.vault.VaultResponse;
import io.helidon.integrations.vault.VaultToken;

/**
 * request and response.
 */
public class Login {
    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends VaultRequest<Request> {
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
         * Create a new request.
         *
         * @param roleId role ID to use
         * @param secretId secret ID to use
         * @return new request
         */
        public static Request create(String roleId, String secretId) {
            return builder().roleId(roleId)
                    .secretId(secretId);
        }

        /**
         * Role ID of the AppRole.
         * This is the ID, not the name. Use {@link AppRoleAuth#readRoleId(String)} or UI to obtain the id.
         *
         * @param roleId role ID
         * @return updated request
         */
        public Request roleId(String roleId) {
            return add("role_id", roleId);
        }

        /**
         * Secret ID.
         *
         * @param secretId secret ID associated with the AppRole.
         * @return updated request
         */
        public Request secretId(String secretId) {
            return add("secret_id", secretId);
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends VaultResponse {
        private final VaultToken token;
        private final String accessor;
        private final List<String> tokenPolicies;
        private final Map<String, String> metadata;

        private Response(Builder builder) {
            super(builder);

            JsonObject json = builder.entity();
            JsonObject auth = json.getJsonObject("auth");

            this.metadata = toMap(auth, "metadata");
            this.tokenPolicies = toList(auth, "token_policies");
            this.accessor = auth.getString("accessor");
            this.token = VaultToken.builder()
                    .token(auth.getString("client_token"))
                    .renewable(auth.getBoolean("renewable"))
                    .leaseDuration(Duration.ofSeconds(auth.getInt("lease_duration")))
                    .build();
        }

        static Builder builder() {
            return new Builder();
        }

        public VaultToken token() {
            return token;
        }

        public String accessor() {
            return accessor;
        }

        public List<String> tokenPolicies() {
            return tokenPolicies;
        }

        public Map<String, String> metadata() {
            return metadata;
        }

        static class Builder extends ApiEntityResponse.Builder<Login.Response.Builder, Login.Response, JsonObject> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }
}
