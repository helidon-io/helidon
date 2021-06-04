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

import java.util.Optional;
import java.util.function.Function;

/**
 * A response for requests that may or may not return an entity, such as
 * GET requests that may have a {@code 404} response code.
 *
 * @param <R> type of the (optional) response object created from entity
 */
public class ApiOptionalResponse<R> extends ApiResponse {
    private final Optional<R> entity;

    /**
     * Construct from builder.
     * @param builder builder to construct from
     * @param entity optional entity
     */
    protected ApiOptionalResponse(BuilderBase<?, ?, ?, ?> builder, Optional<R> entity) {
        super(builder);

        this.entity = entity;
    }

    /**
     * A builder to create an optional response.
     * Method name is not {@code builder} to allow subclasses to define their own builder methods.
     *
     * @param <X> expected entity (such as {@link javax.json.JsonObject}
     * @param <R> type of object used to represent the entity
     * @return a new builder
     */
    public static <X, R> Builder<X, R> apiResponseBuilder() {
        return new Builder<>();
    }

    /**
     * Get the entity if it is present.
     *
     * @return optional with the entity
     */
    public Optional<R> entity() {
        return entity;
    }

    /**
     * Map the (possible) response entity to a different type.
     *
     * @param mapper mapper function
     * @param <U> new type
     * @return new optional response with the mapped entity
     */
    public <U> ApiOptionalResponse<U> map(Function<R, U> mapper) {
        Builder<U, U> builder = ApiOptionalResponse.apiResponseBuilder();

        entity.map(mapper).ifPresent(builder::entity);

        return builder
                .entityProcessor(Function.identity())
                .headers(headers())
                .requestId(requestId())
                .status(status())
                .build();
    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.common.rest.ApiOptionalResponse}.
     *
     * @param <X> type of the entity, such as{@link javax.json.JsonObject}
     * @param <R> type of the response created from entity
     */
    public static final class Builder<X, R> extends BuilderBase<Builder<X, R>, ApiOptionalResponse<R>, X, R> {
        private Builder() {
        }

        @Override
        public ApiOptionalResponse<R> build() {
            return new ApiOptionalResponse<>(this, entity().map(entityProcessor()));
        }
    }

    /**
     * Fluent API builder base for subclasses of {@link io.helidon.integrations.common.rest.ApiOptionalResponse}.
     *
     * @param <B> type of the builder (extending the base builder)
     * @param <T> type of the subclass of {@link io.helidon.integrations.common.rest.ApiOptionalResponse}
     * @param <X> type of the entity (JsonObject, byte[])
     * @param <R> type of the (optional) response object
     */
    public abstract static class BuilderBase<B extends BuilderBase<B, T, X, R>, T extends ApiOptionalResponse<R>, X, R>
            extends ApiResponse.Builder<B, T>
            implements ResponseBuilder<B, T, X> {
        private X entity;
        private Function<X, R> entityProcessor;

        /**
         * New builder.
         */
        protected BuilderBase() {
        }

        /**
         * Configure the entity. Invoked by {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @param entity entity to use (only called if present)
         * @return updated builder
         */
        public B entity(X entity) {
            this.entity = entity;
            return me();
        }

        /**
         * A function to convert the entity to target object.
         * The processor is only invoked in case an entity is present.
         *
         * @param processor create response object from entity
         * @return updated builder
         */
        public B entityProcessor(Function<X, R> processor) {
            this.entityProcessor = processor;
            return me();
        }

        /**
         * Entity as received from network.
         * @return entity if present, empty otherwise
         */
        protected Optional<X> entity() {
            return Optional.ofNullable(entity);
        }

        /**
         * The configured entity processor.
         * @return processor
         */
        protected Function<X, R> entityProcessor() {
            return entityProcessor;
        }
    }
}
