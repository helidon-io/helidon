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

import javax.json.JsonObject;

/**
 * Read Role ID request and response.
 */
public final class ReadRoleId {
    private ReadRoleId() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends AppRoleRequestBase<Request> {
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
         * Create request for a specific role name.
         *
         * @param appRoleName AppRole name
         * @return new request
         */
        public static Request create(String appRoleName) {
            return builder().roleName(appRoleName);
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response {
        private final String roleId;

        private Response(JsonObject jsonObject) {
            this.roleId = jsonObject.getJsonObject("data").getString("role_id");
        }

        static Response create(JsonObject jsonObject) {
            return new Response(jsonObject);
        }

        /**
         * Role ID read from the Vault.
         * @return role ID
         */
        public String roleId() {
            return roleId;
        }
    }
}
