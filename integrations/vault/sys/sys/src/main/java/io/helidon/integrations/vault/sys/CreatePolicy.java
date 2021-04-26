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

package io.helidon.integrations.vault.sys;

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Create policy request and response.
 */
public final class CreatePolicy {
    private CreatePolicy() {
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
         * A new request with a policy name and policy text.
         *
         * @param name name of the policy
         * @param policy policy text
         * @return a new request
         */
        public static Request create(String name, String policy) {
            return builder()
                    .name(name)
                    .policy(policy);
        }

        /**
         * Policy text.
         *
         * @param policy policy
         * @return updated request
         */
        public Request policy(String policy) {
            return add("policy", policy);
        }

        /**
         * Policy name.
         *
         * @param name name of the policy
         * @return updated request
         */
        public Request name(String name) {
            this.name = name;
            return this;
        }

        String name() {
            if (name == null) {
                throw new VaultApiException("CreatePolicy.Request name must be defined");
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
