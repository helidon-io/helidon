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

package io.helidon.integrations.vault.secrets.database;

import java.time.Duration;

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * DB create role request and response.
 */
public final class DbCreateRole {
    private DbCreateRole() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends VaultRequest<Request> {
        private String name;

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
         * Specifies the TTL for the leases associated with this role.
         * Defaults to system/engine default TTL time.
         *
         * @param defaultTtl default time to live
         * @return updated request
         */
        public Request defaultTtl(Duration defaultTtl) {
            return add("default_ttl", defaultTtl);
        }

        /**
         * Specifies the maximum TTL for the leases associated with this role.
         * Defaults to system/mount default TTL time; this value is allowed to be less than the mount max TTL
         * (or, if not set, the system max TTL), but it is not allowed to be longer.
         *
         * @param maxTtl maximal time to live
         * @return updated request
         */
        public Request maxTtl(Duration maxTtl) {
            return add("max_ttl", maxTtl);
        }

        /**
         * Specifies the database statements executed to create and configure a user. See the plugin's API page for more
         * information on support and formatting for this parameter.
         *
         * @param statement statement
         * @return updated request
         */
        public Request addCreationStatement(String statement) {
            return addToArray("creation_statements", statement);
        }

        /**
         * Specifies the database statements to be executed to revoke a user. See the plugin's API page for more information on
         * support and formatting for this parameter.
         *
         * @param statement statement
         * @return updated request
         */
        public Request addRevocationStatement(String statement) {
            return addToArray("revocation_statements", statement);
        }

        /**
         * Specifies the database statements to be executed to rollback a create operation in the event of an error. Not every
         * plugin type will support this functionality. See the plugin's API page for more information on support and formatting
         * for this parameter.
         *
         * @param statement statement
         * @return updated request
         */
        public Request addRollbackStatement(String statement) {
            return addToArray("rollback_statements", statement);
        }

        /**
         * Specifies the database statements to be executed to renew a user. Not every plugin type will support this functionality.
         * See the plugin's API page for more information on support and formatting for this parameter.
         *
         * @param statement statement
         * @return updated request
         */
        public Request addRenewStatement(String statement) {
            return addToArray("renew_statements", statement);
        }

        public Request dbName(String dbName) {
            return add("db_name", dbName);
        }

        /**
         * Name of the credentials.
         *
         * @param name the name
         * @return updated request
         */
        public Request name(String name) {
            this.name = name;
            return this;
        }

        String name() {
            if (name == null) {
                throw new VaultApiException("DbCreateRole.Request name must be defined");
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
