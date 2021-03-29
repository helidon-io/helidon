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

package io.helidon.integrations.vault.auths.k8s;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.vault.VaultRequest;
import io.helidon.integrations.vault.VaultResponse;
import io.helidon.integrations.vault.VaultToken;

import static io.helidon.integrations.vault.VaultUtil.arrayToList;

/**
 * Login request and response.
 */
public final class Login {
    private Login() {
    }

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

        public static Request create(String roleName, String serviceAccountToken) {
            return builder()
                    .roleName(roleName)
                    .serviceAccountToken(serviceAccountToken);
        }

        public Request roleName(String roleName) {
            return add("role", roleName);
        }

        public Request serviceAccountToken(String token) {
            return add("jwt", token);
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends VaultResponse {
        private final VaultToken token;
        private final String accessor;
        private final List<String> policies;
        private final Map<String, String> metadata;

        private Response(Builder builder) {
            super(builder);

            JsonObject auth = builder.entity().getJsonObject("auth");

            this.accessor = auth.getString("accessor");
            this.policies = arrayToList(auth.getJsonArray("policies"));
            this.metadata = toMap(auth, "metadata");
            this.token = VaultToken.builder()
                    .token(auth.getString("client_token"))
                    .leaseDuration(Duration.ofSeconds(auth.getJsonNumber("lease_duration").longValue()))
                    .renewable(auth.getBoolean("renewable"))
                    .build();
        }

        static Builder builder() {
            return new Builder();
        }

        public VaultToken token() {
            return token;
        }

        /**
         * Accessor id.
         * @return accessor
         */
        public String accessor() {
            return accessor;
        }

        /**
         * List of policy names of this token.
         *
         * @return policies
         */
        public List<String> policies() {
            return policies;
        }

        /**
         * Additional token metadata.
         * @return map with metadata
         */
        public Map<String, String> metadata() {
            return metadata;
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
