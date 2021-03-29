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

package io.helidon.integrations.vault.secrets.cubbyhole;

import java.util.Map;
import java.util.Optional;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiJsonParser;
import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * request and response.
 */
public final class GetCubbyhole {
    private GetCubbyhole() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends VaultRequest<Request> {
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

        public Request path(String path) {
            this.path = path;
            return this;
        }

        String path() {
            if (path == null) {
                throw new VaultApiException("GetCubbyhole.Request path must be configured");
            }
            return path;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends ApiJsonParser implements Secret {
        private final String path;
        private final Map<String, String> values;

        private Response(String path, JsonObject json) {
            this.path = path;
            this.values = toMap(json, "data");
        }

        static Response create(String path, JsonObject json) {
            return new Response(path, json);
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public Optional<String> value(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public Map<String, String> values() {
            return values;
        }
    }
}
