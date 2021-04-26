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

package io.helidon.integrations.vault.secrets.kv2;

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Destroy Key/Value Version 2 Secret version request and response.
 */
public final class DestroyKv2 {
    private DestroyKv2() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends VaultRequest<Request> {
        private String path;

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
         * Path of the secret to destroy.
         *
         * @param path secret's path
         * @return updated request
         */
        public Request path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Version(s) of the secret to destroy.
         *
         * @param versions secret's version(s)
         * @return updated request
         */
        public Request versions(int... versions) {
            for (int version : versions) {
                addVersion(version);
            }
            return this;
        }

        /**
         * Add a secret version to destroy.
         *
         * @param version secret's version
         * @return updated request
         */
        public Request addVersion(int version) {
            return addToArray("versions", version);
        }

        String path() {
            if (path == null) {
                throw new VaultApiException("CreateKv1.Request path must be configured");
            }
            return path;
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
