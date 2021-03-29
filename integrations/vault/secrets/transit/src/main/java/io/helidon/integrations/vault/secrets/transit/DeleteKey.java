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

package io.helidon.integrations.vault.secrets.transit;

import io.helidon.integrations.common.rest.ApiException;
import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultRequest;

public final class DeleteKey {
    private DeleteKey() {
    }

    public static final class Request extends VaultRequest<Request> {
        private String name;

        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        public static Request create(String keyName) {
            return builder().name(keyName);
        }

        /**
         * Specifies the name of the encryption key to create.
         *
         * @param name key name
         * @return updated request
         */
        public Request name(String name) {
            this.name = name;
            return this;
        }

        String name() {
            if (name == null) {
                throw new ApiException("Vault CreateKey request must have name configured");
            }
            return name;
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
