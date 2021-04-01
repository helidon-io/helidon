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

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Create AppRole request and response.
 */
public class CreateAppRole {
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
         * Name of the AppRole.
         *
         * @param name AppRole name
         * @return updated request
         */
        public Request roleName(String name) {
            this.roleName = name;
            return this;
        }

        /**
         * Require secret_id to be presented when logging in using this AppRole.
         *
         * @param bindSecretId whether to bind secret id
         * @return updated request
         */
        public Request bindSecretId(boolean bindSecretId) {
            return add("bind_secret_id", bindSecretId);
        }

        /**
         * CIDR blocks; if set, specifies blocks of IP addresses which can perform the login operation.
         *
         * @param cidr CIDR block
         * @return updated request
         */
        public Request addSecretIdBoundCidr(String cidr) {
            return addToArray("secret_id_bound_cidrs", cidr);
        }

        /**
         * Number of times any particular SecretID can be used to fetch a token from this AppRole, after which the SecretID will
         * expire. A value of zero will allow unlimited uses.
         *
         * @param numberOfUses number of uses
         * @return updated request
         */
        public Request secretIdNumUses(int numberOfUses) {
            return add("secret_id_num_uses", numberOfUses);
        }

        /**
         * Duration after which the secret id expires.
         *
         * @param ttl time to live
         * @return updated request
         */
        public Request secretIdTtl(Duration ttl) {
            return add("secret_id_ttl", ttl);
        }

        /**
         * Token policy to encode onto generated tokens.
         *
         * @param policy policy name
         * @return updated request
         */
        public Request addTokenPolicy(String policy) {
            return addToArray("token_policies", policy);
        }

        /**
         * Token bound CIDR blocks. If set, specifies blocks of IP addresses which can authenticate successfully, and ties the
         * resulting token to these blocks as well.
         *
         * @param cidr CIDR block
         * @return updated request
         */
        public Request addTokenBoundCidr(String cidr) {
            return addToArray("token_bound_cidrs", cidr);
        }

        /**
         * If set, will encode an explicit max TTL onto the token. This is a hard cap even if token_ttl and token_max_ttl would
         * otherwise allow a renewal.
         *
         * @param duration time to live
         * @return updated request
         */
        public Request tokenExplicitMaxTtl(Duration duration) {
            return add("token_explicit_max_ttl", duration);
        }

        /**
         * If set, the default policy will not be set on generated tokens; otherwise it will be added to the policies set in
         * token_policies.
         *
         * @param noDefaultPolicy whether to disable default policy
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
         * Period to use on the token.
         * See
         * <a href="https://www.vaultproject.io/docs/concepts/tokens#token-time-to-live-periodic-tokens-and-explicit-max-ttls">Period</a>
         *
         * @param period period to use
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
         * @param tokenType token type to use
         * @return updated request
         */
        public Request tokenType(String tokenType) {
            return add("token_type", tokenType);
        }

        public String roleName() {
            if (roleName == null) {
                throw new VaultApiException("CreateAppRole.Request role name must be defined");
            }
            return roleName;
        }
    }

    /**
     * Create AppRole response.
     *
     * @see AppRoleAuthRx#createAppRole(io.helidon.integrations.vault.auths.approle.CreateAppRole.Request)
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
