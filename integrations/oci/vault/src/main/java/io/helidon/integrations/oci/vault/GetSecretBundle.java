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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiException;
import io.helidon.integrations.oci.connect.OciRequestBase;
import io.helidon.integrations.oci.connect.OciResponseParser;

/**
 * Request and response for getting secret.
 */
public class GetSecretBundle {
    private GetSecretBundle() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends OciRequestBase<Request> {
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
         * Create a new request for secret ID.
         *
         * @param secretId secret OCID
         * @return a new request
         */
        public static Request create(String secretId) {
            return builder().secretId(secretId);
        }

        /**
         * Secret OCID.
         * Required.
         *
         * @param secretId secret OCID
         * @return updated request
         */
        public Request secretId(String secretId) {
            this.secretId = secretId;
            return this;
        }

        /**
         * Secret version number.
         *
         * @param versionNumber version number
         * @return updated request
         */
        public Request versionNumber(int versionNumber) {
            return addQueryParam("versionNumber", String.valueOf(versionNumber));
        }

        /**
         * Secret version name.
         *
         * @param versionName version name
         * @return updated request
         */
        public Request versionName(String versionName) {
            return addQueryParam("versionName", versionName);
        }

        /**
         * Secret stage.
         *
         * @param stage stage
         * @return updated request
         */
        public Request stage(SecretStage stage) {
            return addQueryParam("stage", stage.toString());
        }

        String secretId() {
            if (secretId == null) {
                throw new ApiException("secretId is a mandatory parameter for GetSecretBundle");
            }
            return secretId;
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static class Response extends OciResponseParser {
        private final Map<String, String> metadata;
        private final String secretId;
        private final List<SecretStage> stages;
        private final Optional<Instant> timeCreated;
        private final Optional<Instant> timeDeleted;
        private final Optional<Instant> expirationTime;
        private final Optional<String> versionName;
        private final int versionNumber;
        private final Optional<byte[]> secretContent;

        private Response(JsonObject json) {
            this.metadata = toMap(json, "metadata");
            this.secretId = json.getString("secretId");
            this.timeCreated = toInstant(json, "timeCreated");
            this.timeDeleted = toInstant(json, "timeOfDeletion");
            this.expirationTime = toInstant(json, "timeOfExpiry");
            this.versionName = toString(json, "versionName");
            this.versionNumber = json.getInt("versionNumber");
            this.secretContent = toObject(json, "secretBundleContent")
                    .flatMap(bundle -> {
                        String contentType = bundle.getString("contentType");
                        if ("BASE64".equals(contentType)) {
                            return toBytesBase64(bundle, "content");
                        }
                        throw new ApiException("Unsupported secret content type: " + contentType + ", only BASE64 is supported");
                    });
            this.stages = toList(json, "stages")
                    .stream()
                    .map(SecretStage::valueOf)
                    .collect(Collectors.toList());

        }

        static Response create(JsonObject json) {
            return new Response(json);
        }

        /**
         * Customer-provided contextual metadata for the secret.
         *
         * @return metadata
         */
        public Map<String, String> metadata() {
            return metadata;
        }

        /**
         * The OCID of the secret.
         *
         * @return secret OCID
         */
        public String secretId() {
            return secretId;
        }

        /**
         * A list of possible rotation states for the secret version.
         *
         * @return stages
         */
        public List<SecretStage> stages() {
            return stages;
        }

        /**
         * The time when the secret bundle was created.
         *
         * @return time of creation
         */
        public Optional<Instant> timeCreated() {
            return timeCreated;
        }

        /**
         * The time the secret would be deleted.
         *
         * @return time of deletion
         */
        public Optional<Instant> timeDeleted() {
            return timeDeleted;
        }

        /**
         * An optional property indicating when the secret version will expire.
         *
         * @return expiration time
         */
        public Optional<Instant> expirationTime() {
            return expirationTime;
        }

        /**
         * The name of the secret version. Labels are unique across the different versions of a particular secret.
         *
         * @return version name
         */
        public Optional<String> versionName() {
            return versionName;
        }

        /**
         * The version number of the secret.
         *
         * @return version number
         */
        public int versionNumber() {
            return versionNumber;
        }

        /**
         * The content of the secrets, as bytes.
         *
         * @return byte value if present
         */
        public Optional<byte[]> secretBytes() {
            return secretContent;
        }

        /**
         * The content of the secrets as a string.
         * This method will attempt to create a string from the provided bytes using UTF-8 encoding.
         *
         * @return string value if present
         */
        public Optional<String> secretString() {
            return secretContent.map(it -> new String(it, StandardCharsets.UTF_8));
        }
    }
}
