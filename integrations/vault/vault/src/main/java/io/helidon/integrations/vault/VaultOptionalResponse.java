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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.integrations.common.rest.ApiOptionalResponse;

/**
 * Response for Vault operations that may contain entity.
 * The entity is present for successful requests (returning {@link io.helidon.common.http.Http.Status#OK_200};
 * entity is not present if the response was {@link io.helidon.common.http.Http.Status#NOT_FOUND_404}).
 *
 * @param <R> type of the response - a subclass of this class
 */
public final class VaultOptionalResponse<R> extends ApiOptionalResponse<R> {
    private final List<String> errors;

    /**
     * Construct a new optional response from the builder and an entity.
     *
     * @param builder subclass of builder of vault optional response
     * @param entity entity (if mapped from HTTP entity)
     */
    protected VaultOptionalResponse(BuilderBase<?, ?, R> builder, Optional<R> entity) {
        super(builder, entity);
        this.errors = List.copyOf(builder.errors());
    }

    /**
     * A builder to create an optional response.
     * Method name is not {@code builder} to allow subclasses to define their own builder methods.
     *
     * @param <X> expected entity (such as {@link javax.json.JsonObject}
     * @param <R> type of object used to represent the entity
     * @return a new builder
     */
    public static <R, X> Builder<X, R> vaultResponseBuilder() {
        return new Builder<>();
    }

    /**
     * List of errors (if any) as returned by Vault.
     * This list may contain errors when we get a {@link io.helidon.common.http.Http.Status#NOT_FOUND_404}.
     *
     * @return list of errors from Vault
     */
    public List<String> errors() {
        return errors;
    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.vault.VaultOptionalResponse}.
     *
     * @param <X> type of entity (actual entity on HTTP communication, such as {@link javax.json.JsonObject})
     * @param <R> type of response (object entity type)
     */
    public static class Builder<X, R> extends BuilderBase<Builder<X, R>, X, R> {
        private Builder() {
        }

        @Override
        public VaultOptionalResponse<R> build() {
            return new VaultOptionalResponse<>(this, entity().map(entityProcessor()));
        }
    }

    /**
     * Base builder class for subclasses of {@link io.helidon.integrations.vault.VaultOptionalResponse}.
     *
     * @param <B> Type of builder - a subclass of this class
     * @param <X> type of entity (actual entity on HTTP communication, such as {@link javax.json.JsonObject})
     * @param <R> type of response (object entity type)
     */
    public abstract static class BuilderBase<B extends BuilderBase<B, X, R>, X, R>
            extends ApiOptionalResponse.BuilderBase<B, VaultOptionalResponse<R>, X, R> {

        private final List<String> errors = new LinkedList<>();

        /**
         * Construct a new builder, should not be public.
         */
        protected BuilderBase() {
        }

        /**
         * Configure list of Vault errors as read from response.
         *
         * @param errors errors to add
         * @return updated builder
         */
        public B errors(List<String> errors) {
            this.errors.addAll(errors);
            return me();
        }

        List<String> errors() {
            return errors;
        }
    }
}
