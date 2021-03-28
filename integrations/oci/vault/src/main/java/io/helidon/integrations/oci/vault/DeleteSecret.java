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
 * Request and response for getting secret.
 */
public final class DeleteSecret {
    private DeleteSecret() {
    }

    public static final class Request extends OciRequestBase<Request> {
        private String secretId;

        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

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

        public Request timeOfDeletion(Instant whenToDelete) {
            return add("timeOfDeletion", whenToDelete);
        }

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
