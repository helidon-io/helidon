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

import io.helidon.integrations.common.rest.ApiResponse;

public final class DeleteAppRole {
    private DeleteAppRole() {
    }

    public static class Request extends AppRoleRequestBase<Request> {
        private Request() {
        }

        /**
         * New request builder.
         * @return new request
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
     * Delete AppRole response.
     *
     * @see io.helidon.integrations.vault.auths.approle.AppRoleAuth#deleteAppRole(io.helidon.integrations.vault.auths.approle.DeleteAppRole.Request)
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

        static class Builder extends ApiResponse.Builder<Response.Builder, Response> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }

}
