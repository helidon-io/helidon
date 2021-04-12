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

package io.helidon.integrations.vault;

import java.util.List;
import java.util.Optional;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiJsonParser;

/**
 * List secrets request and response.
 * @see io.helidon.integrations.vault.Secrets#list(io.helidon.integrations.vault.ListSecrets.Request)
 */
public final class ListSecrets {
    private ListSecrets() {
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

        /**
         * Create with an empty path.
         * @return new request
         */
        public static Request create() {
            return builder();
        }

        /**
         * Create with a path.
         *
         * @param path path to use
         * @return new request
         */
        public static Request create(String path) {
            return builder().path(path);
        }

        /**
         * Configure the path to list, may be ignored by specific secret engines.
         *
         * @param path path to list
         * @return updated request
         */
        public Request path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Path to read, may be empty for root (or secret engines that do not support path).
         *
         * @return path to list
         */
        public Optional<String> path() {
            return Optional.ofNullable(path);
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends ApiJsonParser {
        private final List<String> paths;

        private Response(JsonObject object) {
            paths = VaultUtil.processListDataResponse(object);
        }

        /**
         * Create a new list response from JSON entity.
         *
         * @param json json object from HTTP response
         * @return new response
         */
        public static Response create(JsonObject json) {
            return new Response(json);
        }

        /**
         * Get the list.
         *
         * @return list of objects
         */
        public List<String> list() {
            return paths;
        }
    }
}
