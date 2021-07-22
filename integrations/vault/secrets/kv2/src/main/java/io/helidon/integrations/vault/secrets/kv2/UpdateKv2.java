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

import java.util.Map;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;
import io.helidon.integrations.vault.VaultResponse;

/**
 * Key/Value Version 2 Secret request and response.
 */
public final class UpdateKv2 {
    private UpdateKv2() {
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
         * New secret values for this update request.
         *
         * @param values values to configure, these replace the current values
         * @return updated request
         */
        public Request secretValues(Map<String, String> values) {
            values.forEach(this::addSecretValue);
            return this;
        }

        /**
         * Add a secret value to the map of secret values.
         *
         * @param key key of the value
         * @param value value
         * @return updated request
         */
        public Request addSecretValue(String key, String value) {
            return addToObject("data", key, value);
        }

        /**
         * Path of the secret to update.
         *
         * @param path path of the secret
         * @return updated request
         */
        public Request path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Expected version of the secret being updated.
         *
         * @param expectedVersion expected current version of the user
         * @return updated request
         */
        public Request expectedVersion(int expectedVersion) {
            if (expectedVersion == 0) {
                throw new VaultApiException("Version 0 is reserved for create requests.");
            }
            return addToObject("options", "cas", expectedVersion);
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
    public static final class Response extends VaultResponse {
        private final int version;

        private Response(Builder builder) {
            super(builder);
            this.version = builder.entity().getJsonObject("data").getInt("version");
        }

        static Builder builder() {
            return new Builder();
        }

        /**
         * Version of the updated secret.
         *
         * @return version
         */
        public int version() {
            return version;
        }

        static final class Builder extends ApiEntityResponse.Builder<Builder, Response, JsonObject> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }
}
