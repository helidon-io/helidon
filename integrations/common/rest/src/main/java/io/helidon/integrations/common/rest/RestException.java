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
 * Implementation of the {@link io.helidon.integrations.common.rest.ApiRestException}.
 */
public final class RestException extends ApiRestException {
    private RestException(Builder builder) {
        super(builder);
    }

    /**
     * Create a new builder for this exception.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.common.rest.RestException}.
     */
    public static class Builder extends ApiRestException.BaseBuilder<Builder>
            implements io.helidon.common.Builder<ApiRestException> {
        private Builder() {
        }

        @Override
        public ApiRestException build() {
            return new RestException(this);
        }
    }
}
