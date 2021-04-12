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

import java.util.Optional;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Disable Secrets Engine request and response.
 */
public final class DisableEngine {
    private DisableEngine() {
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
         * Secrets engine to disable.
         * You can also just configure {@link #path(String)} to disable an engine
         *
         * @param engine engine to disable
         * @return updated request
         */
        public Request engine(Engine<?> engine) {
            return path(engine.defaultMount());
        }

        /**
         * Path of a secrets engine to disable.
         *
         * @param path path to disable
         * @return updated request
         */
        public Request path(String path) {
            this.path = path;
            return this;
        }

        String path() {
            if (path == null) {
                throw new VaultApiException("DisableEngine.Request path or engine must be defined");
            }
            return path;
        }

        @Override
        public Optional<JsonObject> toJson(JsonBuilderFactory factory) {
            return Optional.empty();
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
