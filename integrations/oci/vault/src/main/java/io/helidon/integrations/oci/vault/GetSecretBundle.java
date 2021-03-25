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

    public static class Request extends OciRequestBase<Request> {
        private String secretId;

        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        /**
         * Secret OCID.
         * Required.
         *
         * @param secretId
         * @return
         */
        public Request secretId(String secretId) {
            this.secretId = secretId;
            return this;
        }

        public Request versionNumber(int versionNumber) {
            return addQueryParam("versionNumber", String.valueOf(versionNumber));
        }

        public Request versionName(String versionName) {
            return addQueryParam("versionName", versionName);
        }

        public Request stage(SecretStage stage) {
            return addQueryParam("stage", stage.toString());
        }

        public String secretId() {
            if (secretId == null) {
                throw new ApiException("secretId is a mandatory parameter for GetSecretBundle");
            }
            return secretId;
        }
    }

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

        public Map<String, String> metadata() {
            return metadata;
        }

        public String secretId() {
            return secretId;
        }

        public List<SecretStage> stages() {
            return stages;
        }

        public Optional<Instant> timeCreated() {
            return timeCreated;
        }

        public Optional<Instant> timeDeleted() {
            return timeDeleted;
        }

        public Optional<Instant> expirationTime() {
            return expirationTime;
        }

        public Optional<String> versionName() {
            return versionName;
        }

        public int versionNumber() {
            return versionNumber;
        }

        public Optional<byte[]> secretBytes() {
            return secretContent;
        }

        public Optional<String> secretString() {
            return secretContent.map(it -> new String(it, StandardCharsets.UTF_8));
        }
    }
}
