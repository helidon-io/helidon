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

package io.helidon.integrations.common.rest;

/**
 * Response from a remote server with an entity.
 */
public abstract class ApiEntityResponse extends ApiResponse {
    /**
     * Create a new instance.
     *
     * @param builder the builder
     */
    protected ApiEntityResponse(Builder<?, ?, ?> builder) {
        super(builder);
    }

    /**
     * Fluent API builder base to build subclasses of {@link io.helidon.integrations.common.rest.ApiEntityResponse}.
     *
     * @param <B> type of the builder (subclass of this class)
     * @param <T> type of the response being built
     * @param <X> type of the entity supported ({@link javax.json.JsonObject}, {@link io.helidon.common.reactive.Multi}, or
     *           {@code byte[]})
     */
    public abstract static class Builder<B extends Builder<B, T, X>, T extends ApiEntityResponse, X>
            extends ApiResponse.Builder<B, T>
            implements ResponseBuilder<B, T, X> {
        private X entity;

        /**
         * Create a new builder instance.
         */
        protected Builder() {
        }

        /**
         * This method is invoked by {@link io.helidon.integrations.common.rest.RestApi} when an entity
         * is received.
         *
         * @param entity entity
         * @return updated builder
         */
        public B entity(X entity) {
            this.entity = entity;
            return me();
        }

        /**
         * Accessor to entity that can be used in subclasses of {@link io.helidon.integrations.common.rest.ApiEntityResponse}
         *  to set up fields.
         *
         * @return received entity
         */
        public X entity() {
            return entity;
        }
    }
}
