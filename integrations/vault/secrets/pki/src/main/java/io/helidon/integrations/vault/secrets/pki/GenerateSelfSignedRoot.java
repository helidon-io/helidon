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

package io.helidon.integrations.vault.secrets.pki;

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Generate Self Signed Root request and response.
 * @see io.helidon.integrations.vault.secrets.pki.PkiSecrets#generateSelfSignedRoot(GenerateSelfSignedRoot.Request)
 * @see io.helidon.integrations.vault.secrets.pki.PkiSecretsRx#generateSelfSignedRoot(GenerateSelfSignedRoot.Request)
 *
 */
public final class GenerateSelfSignedRoot {
    private GenerateSelfSignedRoot() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends VaultRequest<Request> {
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
         * Common name of the certificate that is going to be generated.
         *
         * @param commonName common name
         * @return updated request
         */
        public Request commonName(String commonName) {
            return add("common_name", commonName);
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
