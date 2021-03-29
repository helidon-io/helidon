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

package io.helidon.integrations.vault.auths.token;

import java.time.Duration;
import java.util.Optional;

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultRequest;

/**
 * request and response.
 */
public final class CreateTokenRole {
    private CreateTokenRole() {
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
         * The name of the token role.
         *
         * @param roleName role name
         * @return updated request
         */
        public Request roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        /**
         * If set, tokens can be created with any subset of the policies in this list, rather than the normal semantics of tokens
         * being a subset of the calling token's policies. If at
         * creation time {@link #tokenNoDefaultPolicy(boolean)} is not set and "default" is not contained in
         * {@link #addDisallowedPolicy(String)}, the "default" policy will be added to the created token automatically.
         *
         * @param policyName policy name
         * @return updated request
         */
        public Request addAllowedPolicy(String policyName) {
            return addToCommaDelimitedArray("allowed_policies", policyName);
        }

        /**
         * If set, successful token creation via this role will require that no policies in the given list are requested.
         * Adding "default" to this list will prevent "default" from being
         * added automatically to created tokens.
         *
         * @param policyName policy name
         * @return updated request
         */
        public Request addDisallowedPolicy(String policyName) {
            return addToCommaDelimitedArray("disallowed_policies", policyName);
        }

        /**
         *  If true, tokens created against this policy will be orphan tokens (they will have no parent). As such, they will
         *  not be
         *  automatically revoked by the revocation of any other token.
         *  Defaults to {@code false}.
         *
         * @param orphan whether to create orphan tokens
         * @return updated request
         */
        public Request orphan(boolean orphan) {
            return add("orphan", orphan);
        }

        /**
         * Set to false to disable the ability of the token to be renewed past its initial TTL. Setting the value to true will
         * allow the token to be renewable up to the system/mount maximum TTL.
         * Defaults to {@code true}.
         *
         * @param renewable whether the tokens should be renewable
         * @return updated request
         */
        public Request renewable(boolean renewable) {
            return add("renewable", renewable);
        }

        /**
         * If set, tokens created against this role will have the given suffix as part of their path in addition to the role name.
         * This can be useful in certain scenarios, such as keeping the same role name in the future but revoking all tokens
         * created against it before some point in time. The suffix can be changed, allowing new callers to have the new suffix as
         * part of their path, and then tokens with the old suffix can be revoked via /sys/leases/revoke-prefix.
         *
         * @param pathSuffix path suffix
         * @return updated request
         */
        public Request pathSuffix(String pathSuffix) {
            return add("path_suffix", pathSuffix);
        }

        /**
         * f set, specifies the entity aliases which are allowed to be used during token generation. This field supports globbing.
         *
         * @param alias alias to add
         * @return updated request
         */
        public Request addAllowedEntityAlias(String alias) {
            return addToArray("allowed_entity_aliases", alias);
        }

        /**
         * List of CIDR blocks; if set, specifies blocks of IP addresses which can authenticate successfully, and ties the
         *  resulting token to these blocks as well.
         * @param cidr CIDR to add
         * @return updated request
         */
        public Request addTokenBoundCidr(String cidr) {
            return addToArray("token_bound_cidrs", cidr);
        }

        /**
         * If set, will encode an explicit max TTL onto the token. This is a hard cap even if
         * {@link io.helidon.integrations.vault.auths.token.CreateToken.Request#ttl(java.time.Duration)} and
         * {@link io.helidon.integrations.vault.auths.token.CreateToken.Request#explicitMaxTtl(java.time.Duration)} would
         *  otherwise allow a renewal.
         *
         * @param duration max time to live
         * @return updated request
         */
        public Request tokenExplicitMaxTtl(Duration duration) {
            return add("token_explicit_max_ttl", duration);
        }

        /**
         * If set, the default policy will not be set on generated tokens; otherwise it will be added to the policies set in
         * {@link #addAllowedPolicy(String)}.
         *
         * @param noDefaultPolicy whether to disable {@code default} policy
         * @return updated request
         */
        public Request tokenNoDefaultPolicy(boolean noDefaultPolicy) {
            return add("token_no_default_policy", noDefaultPolicy);
        }

        /**
         * The maximum number of times a generated token may be used (within its lifetime); 0 means unlimited. If you require the
         * token to have the ability to create child tokens, you will need to set this value to 0.
         *
         * @param uses number of uses
         * @return updated request
         */
        public Request tokenNumUses(int uses) {
            return add("token_num_uses", uses);
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
         * @param type type of token
         * @return updated request
         * @see io.helidon.integrations.vault.auths.token.TokenAuth#TYPE_SERVICE
         * @see io.helidon.integrations.vault.auths.token.TokenAuth#TYPE_BATCH
         * @see io.helidon.integrations.vault.auths.token.TokenAuth#TYPE_DEFAULT
         */
        public Request tokenType(String type) {
            return add("token_type", type);
        }

        Optional<String> roleName() {
            return Optional.ofNullable(roleName);
        }
    }

    public static final class Response extends ApiResponse {
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
