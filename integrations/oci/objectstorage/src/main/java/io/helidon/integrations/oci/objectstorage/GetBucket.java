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

import java.time.Instant;
import java.util.Optional;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.integrations.oci.connect.OciApiException;
import io.helidon.integrations.oci.connect.OciRequestBase;
import io.helidon.integrations.oci.connect.OciResponseParser;

/**
 * Get Bucket request and response.
 */
public final class GetBucket {
    private GetBucket() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends OciRequestBase<Request> {
        private String namespace;
        private String bucket;

        private Request() {
        }

        /**
         * Fluent API builder for configuring a request.
         * The request builder is passed as is, without a build method.
         * The equivalent of a build method is {@link #toJson(JsonBuilderFactory)}
         * used by the {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @return new request builder
         */
        public static Request builder() {
            return new Request();
        }

        @Override
        public Optional<JsonObject> toJson(JsonBuilderFactory factory) {
            return Optional.empty();
        }

        /**
         * The Object Storage namespace used for the request.
         * Override the default namespace (if one is configured).
         *
         * @param namespace namespace
         * @return updated request
         */
        public GetBucket.Request namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * The name of the bucket. Avoid entering confidential information.
         * Required.
         *
         * @param bucket bucket name
         * @return updated requst
         */
        public GetBucket.Request bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        /**
         * Namespace if configured on this request.
         *
         * @return namespace or empty if not configured per request
         */
        public Optional<String> namespace() {
            return Optional.ofNullable(namespace);
        }

        /**
         * Name of the bucket, required.
         *
         * @return bucket name
         */
        public String bucket() {
            if (bucket == null) {
                throw new OciApiException("Bucket name must be defined for PutObject request.");
            }
            return bucket;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends OciResponseParser {
        private final String bucketId;
        private final String namespace;
        private final Instant created;
        private final String name;
        private final String compartmentId;
        private final String createdBy;
        private final int approximateCount;
        private final long approximateSize;

        private Response(JsonObject json) {
            this.bucketId = json.getString("id");
            this.namespace = json.getString("namespace");
            this.created = getInstant(json, "timeCreated");
            this.name = json.getString("name");
            this.compartmentId = json.getString("compartmentId");
            this.createdBy = json.getString("createdBy");
            this.approximateCount = json.getInt("approximateCount", -1);

            if (json.containsKey("approximateSize") && !json.isNull("approximateSize")) {
                this.approximateSize = json.getJsonNumber("approximateSize").longValue();
            } else {
                this.approximateSize = -1;
            }
        }

        static Response create(JsonObject json) {
            return new Response(json);
        }

        /**
         * Bucket OCID.
         *
         * @return bucket id
         */
        public String bucketId() {
            return bucketId;
        }

        /**
         * The Object Storage namespace in which the bucket resides.
         *
         * @return namespace
         */
        public String namespace() {
            return namespace;
        }

        /**
         * The date and time the bucket was created.
         *
         * @return created instant
         */
        public Instant created() {
            return created;
        }

        /**
         * The name of the bucket. Avoid entering confidential information. Example: my-new-bucket1.
         *
         * @return bucket name
         */
        public String name() {
            return name;
        }

        /**
         * The compartment ID in which the bucket is authorized.
         *
         * @return compartment ID
         */
        public String compartmentId() {
            return compartmentId;
        }

        /**
         * The OCID of the user who created the bucket.
         * @return user OCID
         */
        public String createdBy() {
            return createdBy;
        }

        /**
         * The approximate number of objects in the bucket. Count statistics are reported periodically. You will see a lag
         * between what is displayed and the actual object count.
         *
         * @return count on {@code -1} if not available
         */
        public int approximateCount() {
            return approximateCount;
        }

        /**
         * The approximate total size in bytes of all objects in the bucket. Size statistics are reported periodically. You
         * will see a lag between what is displayed and the actual size of the bucket.
         *
         * @return size or {@code -1} if not available
         */
        public long approximateSize() {
            return approximateSize;
        }
    }
}
