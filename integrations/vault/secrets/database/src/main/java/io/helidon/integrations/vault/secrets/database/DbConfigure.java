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

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Configure DB request and response.
 */
public final class DbConfigure {
    private DbConfigure() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     *
     * @param <T> type of the subclass of this class
     */
    public abstract static class Request<T extends Request<T>> extends VaultRequest<T> {
        private String name;

        /**
         * Create a new request with the defined plugin name.
         * @param pluginName name of the database plugin
         */
        protected Request(String pluginName) {
            add("plugin_name", pluginName);
        }

        /**
         * Specifies the name of the user to use as the "root" user when connecting to the database. This "root" user is used to
         * create/update/delete users managed by these plugins, so you will need to ensure that this user has permissions to
         * manipulate users appropriate to the database. This is typically used in the connection_url field via the templating
         * directive {{username}} or {{name}}.
         *
         * @param username user
         * @return updated request
         */
        public T username(String username) {
            return add("username", username);
        }

        /**
         * Specifies the password to use when connecting with the username. This value will not be returned by Vault when
         * performing a read upon the configuration. This is typically used in the connection_url field via the templating
         * directive {{password}}.
         *
         * @param password password
         * @return updated request
         */
        public T password(String password) {
            return add("password", password);
        }

        /**
         * Specifies if the connection is verified during initial configuration. Defaults to {@code true}.
         * @param verify whether to verify connections
         * @return updated request
         */
        public T verifyConnection(boolean verify) {
            return add("verify_connection", verify);
        }

        /**
         * List of the roles allowed to use this connection. Defaults to empty (no roles),
         * if contains a {@code "*"} any role can use this connection.
         *
         * @param role role name
         * @return updated request
         */
        public T addAllowedRole(String role) {
            return addToArray("allowed_roles", role);
        }

        /**
         * Specifies the database statements to be executed to rotate the root user's credentials. See the plugin's API page for
         * more information on support and formatting for this parameter.
         *
         * @param statement statement to add
         * @return updated request
         */
        public T addRootRotationStatement(String statement) {
            return addToArray("root_rotation_statements", statement);
        }

        /**
         * The name of the password policy to use when generating passwords for this database. If not specified, this will use a
         * default policy defined as: 20 characters with at least 1 uppercase, 1 lowercase, 1 number, and 1 dash character.
         * <p>
         * Password policy docs: <a href="https://www.vaultproject.io/docs/concepts/password-policies">password policies</a>
         *
         * @param policy password policy to use
         * @return updated request
         */
        public T passwordPolicy(String policy) {
            return add("password_policy", policy);
        }

        /**
         * Name of the credentials.
         *
         * @param name the name
         * @return updated request
         */
        public T name(String name) {
            this.name = name;
            return me();
        }

        String name() {
            if (name == null) {
                throw new VaultApiException("DbGet.Request name must be defined");
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
