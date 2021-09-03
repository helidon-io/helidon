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

import io.helidon.integrations.common.rest.ApiResponse;

/**
 * Objects related to RenameObject API calls.
 */
public final class RenameObject {
    private RenameObject() {
    }

    /**
     * Rename Object request.
     */
    public static class Request extends ObjectRequest<Request> {
        private Request() {
        }

        /**
         * Create a new request builder.
         *
         * @return a new request
         */
        public static Request builder() {
            return new Request();
        }

        /**
         * The name of the source object to be renamed.
         *
         * @param objectName name of the object
         * @return updated request
         */
        @Override
        public Request objectName(String objectName) {
            return add("sourceName", objectName);
        }

        /**
         * The new name of the source object. Avoid entering confidential information.
         * Required.
         *
         * @param objectName new name of the object
         * @return updated request
         */
        public Request newObjectName(String objectName) {
            return add("newName", objectName);
        }

        /**
         * The if-match entity tag (ETag) of the new object.
         *
         * @param eTag entity tag for the new object
         * @return updated request
         */
        public Request newIfMatchETag(String eTag) {
            return add("newObjIfMatchETag", eTag);
        }

        /**
         * The if-none-match entity tag (ETag) of the new object.
         *
         * @param eTag entity tag
         * @return updated request
         */
        public Request newIfNoneMatchETag(String eTag) {
            return add("newObjIfNoneMatchETag", eTag);
        }

        /**
         * The if-match entity tag (ETag) of the source object.
         *
         * @param eTag entity tag
         * @return updated request
         */
        public Request oldIfMatchETag(String eTag) {
            return add("srcObjIfMatchETag", eTag);
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
