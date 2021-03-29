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

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Create role request.
 */
public final class CreateRole {
    private CreateRole() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends VaultRequest<Request> {
        private String roleName;

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
         * Add a service account name able to access this role.
         * If set to "*" all names are allowed, both this and
         * {@link #addBoundServiceAccountNamespace(String)} can not be "*".
         *
         * @param serviceAccountName service account name
         * @return updated request
         */
        public Request addBoundServiceAccountName(String serviceAccountName) {
            return addToArray("bound_service_account_names", serviceAccountName);
        }

        /**
         * Add a namespace allowed to access this role.
         * If set to "*" all namespaces are allowed, both this and
         * {@link #addBoundServiceAccountName(String)} can not be set to "*".
         *
         * @param serviceAccountNamespace service account namespace
         * @return updated request
         */
        public Request addBoundServiceAccountNamespace(String serviceAccountNamespace) {
            return addToArray("bound_service_account_namespaces", serviceAccountNamespace);
        }

        /**
         * Optional Audience claim to verify in the JWT.
         *
         * @param audience audience
         * @return updated request
         */
        public Request audience(String audience) {
            return add("audience", audience);
        }

        /**
         * The incremental lifetime for generated tokens. This current value of this will be referenced at renewal time.
         *
         * @param ttl time to live
         * @return updated request
         */
        public Request tokenTtl(int ttl) {
            return add("token_ttl", ttl);
        }

        /**
         * The maximum lifetime for generated tokens. This current value of this will be referenced at renewal time.
         *
         * @param ttl time to live
         * @return updated request
         */
        public Request tokenMaxTtl(int ttl) {
            return add("token_max_ttl", ttl);
        }

        /**
         * Add a policy to encode on the generated token.
         *
         * @param policy policy to add
         * @return updated request
         */
        public Request addTokenPolicy(String policy) {
            return addToArray("token_policies", policy);
        }

        /**
         * Add CIDR block. f set, specifies blocks of IP addresses which can authenticate successfully, and ties the resulting
         * token to these blocks as well.
         *
         * @param cidr CIDR to add
         * @return updated request
         */
        public Request addTokenBoundCidr(String cidr) {
            return addToArray("token_bound_cidrs", cidr);
        }

        /**
         * If set, will encode an explicit max TTL onto the token. This is a hard cap even if token_ttl and token_max_ttl would
         * otherwise allow a renewal.
         *
         * @param ttl time to live
         * @return updated request
         */
        public Request tokenExplicitMaxTtl(int ttl) {
            return add("token_explicit_max_ttl", ttl);
        }

        /**
         * If set, the default policy will not be set on generated tokens; otherwise it will be added to the policies set in
         * token_policies.
         *
         * @param noDefaultPolicy whether to disable default policy for this role
         * @return updated request
         */
        public Request tokenNoDefaultPolicy(boolean noDefaultPolicy) {
            return add("token_no_default_policy", noDefaultPolicy);
        }

        /**
         * The maximum number of times a generated token may be used (within its lifetime); 0 means unlimited. If you require the
         * token to have the ability to create child tokens, you will need to set this value to 0.
         *
         * @param numUses number of uses
         * @return updated request
         */
        public Request tokenNumUses(int numUses) {
            return add("token_num_uses", numUses);
        }

        /**
         * The period, if any, to set on the token.
         *
         * @param period period
         * @return updated request
         */
        public Request tokenPeriod(int period) {
            return add("token_period", period);
        }

        /**
         * The type of token that should be generated. Can be service, batch, or default to use the mount's tuned default (which
         * unless changed will be service tokens). For token store roles, there are two additional possibilities: default-service
         * and default-batch which specify the type to return unless the client requests a different type at generation time.
         *
         * @param type type
         * @return updated request
         * @see io.helidon.integrations.vault.auths.k8s.K8sAuth#TYPE_SERVICE
         * @see io.helidon.integrations.vault.auths.k8s.K8sAuth#TYPE_BATCH
         * @see io.helidon.integrations.vault.auths.k8s.K8sAuth#TYPE_DEFAULT
         */
        public Request tokenType(String type) {
            return add("token_type", type);
        }

        public Request roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        public String roleName() {
            if (roleName == null) {
                throw new VaultApiException("CreateRole.Request role name must be configured");
            }
            return roleName;
        }
    }

    /**
     * Create role response.
     */
    public static final class Response extends ApiResponse {
        // we could use a single response object for all responses without entity
        // but that would hinder future extensibility, as this allows us to add any field to this
        // class without impacting the API

        private Response(Builder builder) {
            super(builder);
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder extends ApiResponse.Builder<Builder, Response> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }
}
