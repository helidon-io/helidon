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

import java.util.Map;
import java.util.Optional;

import io.helidon.integrations.common.rest.ApiJsonParser;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

import jakarta.json.JsonObject;

/**
 * Get DB request and response.
 */
public final class DbGet {
    private DbGet() {
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
         * The equivalent of a build method is {@link #toJson(jakarta.json.JsonBuilderFactory)}
         * used by the {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @return new request builder
         */
        public static Request builder() {
            return new Request();
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
                throw new VaultApiException("DbGet.Request name must be defined");
            }
            return name;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends ApiJsonParser implements DbCredentials {
        private final String name;
        private final Map<String, String> values;

        private Response(String name, JsonObject object) {
            this.name = name;
            this.values = toMap(object, "data");
        }

        static Response create(String path, JsonObject json) {
            return new Response(path, json);
        }

        @Override
        public String path() {
            return name;
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
