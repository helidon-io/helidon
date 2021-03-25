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

package io.helidon.integrations.oci.objectstorage;

import java.util.Optional;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.oci.connect.OciApiException;

public final class PutObject {
    private PutObject() {
    }

    public static class Request extends ObjectRequest<Request> {
        private Long contentLength;

        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        /**
         * The content length of the body (number of bytes in the request entity).
         * Required.
         *
         * @param contentLength content length
         * @return updated request
         */
        public Request contentLength(long contentLength) {
            this.contentLength = contentLength;
            return this;
        }

        public long contentLength() {
            if (contentLength == null) {
                throw new OciApiException("Content-Length must be defined for PutObject request.");
            }
            return contentLength;
        }

        @Override
        public Optional<JsonObject> toJson(JsonBuilderFactory factory) {
            return Optional.empty();
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
