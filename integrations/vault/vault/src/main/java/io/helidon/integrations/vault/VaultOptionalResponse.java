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

public final class VaultOptionalResponse<R> extends ApiOptionalResponse<R> {
    private final List<String> errors;

    private VaultOptionalResponse(Builder<?, R> builder, Optional<R> entity) {
        super(builder, entity);
        this.errors = List.copyOf(builder.errors);
    }

    public static <R, X> Builder<X, R> builder() {
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

    public static class Builder<X, R> extends BuilderBase<Builder<X, R>, VaultOptionalResponse<R>, X, R> {

        private final List<String> errors = new LinkedList<>();

        private Builder() {
        }

        @Override
        public VaultOptionalResponse<R> build() {
            return new VaultOptionalResponse<>(this, entity().map(entityProcessor()));
        }

        public Builder<X, R> errors(List<String> errors) {
            this.errors.addAll(errors);
            return me();
        }
    }
}
