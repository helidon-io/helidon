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

package io.helidon.integrations.oci.vault;

import java.time.Instant;

import io.helidon.integrations.common.rest.ApiException;
import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.oci.connect.OciRequestBase;

/**
 * Delete Secret request and response.
 */
public final class DeleteSecret {
    private DeleteSecret() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends OciRequestBase<Request> {
        private String secretId;

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
         * Create request for a secret ID.
         *
         * @param secretId secret OCID
         * @return a new request
         * @see #secretId(String)
         */
        public static Request create(String secretId) {
            return builder().secretId(secretId);
        }

        /**
         * Secret OCID.
         * Required.
         *
         * @param secretId id of secret to delte
         * @return updated request
         */
        public Request secretId(String secretId) {
            this.secretId = secretId;
            return this;
        }

        /**
         * Configure the time of deletion.
         *
         * @param whenToDelete when to delete this secret
         * @return updated request
         */
        public Request timeOfDeletion(Instant whenToDelete) {
            return add("timeOfDeletion", whenToDelete);
        }

        /**
         * The configured secret ID.
         *
         * @return secret ID
         */
        public String secretId() {
            if (secretId == null) {
                throw new ApiException("secretId is a mandatory parameter for DeleteSecret");
            }
            return secretId;
        }
    }

    /**
     * Response object for responses without an entity.
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
