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

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Create Token request and response.
 */
public final class CreateToken {
    private CreateToken() {
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
         * Add a policy for the token.
         * Policies must be a subset of the policies belonging to the token making the request, unless root.
         * If not specified, defaults to all the policies of the calling token.
         *
         * @param policy policy to add
         * @return updated request
         */
        public Request addPolicy(String policy) {
            return addToArray("policies", policy);
        }

        /**
         * Request metadata, passed through to the audit devices.
         *
         * @param key name
         * @param value value
         * @return updated request
         */
        public Request addMetadata(String key, String value) {
            return addToObject("meta", key, value);
        }

        /**
         * This argument only has effect if used by a root or sudo caller.
         * When set to true, the token created will not have a parent.
         *
         * @param noParent set to {@code true} to create an orphan token
         * @return updated request
         */
        public Request noParent(boolean noParent) {
            return add("no_parent", noParent);
        }

        /**
         * If configured to {@code true}, the {@code default} policy will not be contained in this token's
         *  policy set.
         *
         * @param noDefaultPolicy whether to exclude default policy
         * @return updated request
         */
        public Request noDefaultPolicy(boolean noDefaultPolicy) {
            return add("no_default_policy", noDefaultPolicy);
        }

        /**
         * Set to {@code false} to disable the ability of the token to be renewed past its initial TTL.
         * Setting the value to {@code true} will allow the token to be renewable up to the system/mount maximum TTL.
         * <p>
         * Defaults to {@code true}.
         *
         * @param renewable whether the token should be renewable
         * @return updated request
         */
        public Request renewable(boolean renewable) {
            return add("renewable", renewable);
        }

        /**
         * The maximum uses for the given token. This can be used to create a one-time-token or limited use token. The value of 0
         * has no limit to the number of uses.
         * @param numUses number of uses, defaults to {@code 0} - unlimited
         * @return updated request
         */
        public Request numUses(int numUses) {
            return add("num_uses", numUses);
        }

        /**
         * The ID of the client token. Can only be specified by a root token. The ID provided may not contain a . character.
         * Otherwise, the token ID is a randomly generated value.
         * <p>
         * Note: The ID should not start with the s. prefix.
         *
         * @param id id of the client token
         * @return updated request
         */
        public Request id(String id) {
            return add("id", id);
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
         * Choose token type. Defaults to {@value TokenAuthRx#TYPE_SERVICE}.
         *
         * @param type token type
         * @return updated request
         * @see TokenAuthRx#TYPE_SERVICE
         * @see TokenAuthRx#TYPE_BATCH
         */
        public Request type(String type) {
            return add("type", type);
        }

        /**
         * The display name of the token.
         *
         * @param displayName display name
         * @return updated request
         */
        public Request displayName(String displayName) {
            return add("display_name", displayName);
        }

        /**
         *  Name of the entity alias to associate with during token creation. Only works in combination with role_name argument
         *  and
         *  used entity alias must be listed in allowed_entity_aliases. If this has been specified, the entity will not be
         *  inherited from the parent.
         *
         * @param entityAlias entity alias
         * @return updated request
         */
        public Request entityAlias(String entityAlias) {
            return add("entity_alias", entityAlias);
        }

        /**
         * If specified, the token will be periodic; it will have no maximum TTL
         * (unless an "explicit-max-ttl" is also set) but every renewal will use the given period.
         * Requires a root token or one with the sudo capability.
         *
         * @param period period
         * @return updated request
         */
        public Request period(String period) {
            return add("period", period);
        }

        /**
         * The TTL period of the token. If not provided, the token is valid
         * for the default lease TTL, or indefinitely if the root policy is used.
         *
         * @param ttl duration of the token, smallest unit is seconds
         * @return updated request
         */
        public Request ttl(Duration ttl) {
            return add("ttl", durationToTtl(ttl));
        }

        /**
         *  If set, the token will have an explicit max TTL set upon it. This maximum token TTL cannot be changed later, and
         *  unlike
         *  with normal tokens, updates to the system/mount max TTL value will have no effect at renewal time -- the token will
         *  never be able to be renewed or used past the value set at issue time.
         *
         * @param explicitMaxTtl duration of the max TTL, smallest unit is seconds
         * @return updated request
         */
        public Request explicitMaxTtl(Duration explicitMaxTtl) {
            return add("explicit_max_ttl", durationToTtl(explicitMaxTtl));
        }

        Optional<String> roleName() {
            return Optional.ofNullable(roleName);
        }
    }

    public static final class Response extends TokenResponse {
        private Response(Builder builder) {
            super(builder);
        }

        static Builder builder() {
            return new Builder();
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
