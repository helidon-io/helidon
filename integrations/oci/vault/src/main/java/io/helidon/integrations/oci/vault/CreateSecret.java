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
import java.util.Base64;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.common.rest.ApiJsonBuilder;
import io.helidon.integrations.oci.connect.OciRequestBase;

/**
 * Request and response for creating a Vault secret.
 */
public final class CreateSecret {
    private CreateSecret() {
    }

    public static final class Request extends OciRequestBase<Request> {
        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        /**
         * The OCID of the vault where you want to create the secret.
         * Required.
         *
         * @param vaultOcid vault OCI
         * @return updated request
         */
        public Request vaultId(String vaultOcid) {
            return add("vaultId", vaultOcid);
        }

        /**
         * A user-friendly name for the secret. Secret names should be unique within a vault. Avoid entering confidential
         * information. Valid characters are uppercase or lowercase letters, numbers, hyphens, underscores, and periods.
         * Required.
         *
         * @param name name of the secret
         * @return updated request
         */
        public Request secretName(String name) {
            return add("secretName", name);
        }

        /**
         * A brief description of the secret. Avoid entering confidential information.
         * Optional.
         *
         * @param description description
         * @return updated request
         */
        public Request description(String description) {
            return add("description", description);
        }

        /**
         * The OCID of the master encryption key that is used to encrypt the secret. You must specify a symmetric key to
         * encrypt the secret during import to the vault. You cannot encrypt secrets with asymmetric keys. Furthermore, the key
         * must exist in the vault that you specify.
         * This is required, even though the API docs mark it as optional.
         *
         * @param encryptionKeyOcid OCID of the encryption key
         * @return updated request
         */
        public Request encryptionKeyId(String encryptionKeyOcid) {
            return add("keyId", encryptionKeyOcid);
        }

        /**
         * Content of the secret.
         *
         * @param secretContent content
         * @return updated request
         */
        public Request secretContent(SecretContent secretContent) {
            return add("secretContent", secretContent);
        }

        /**
         * The OCID of the compartment where you want to create the secret.
         * Required.
         *
         * @param compartmentOcid compartment OCID
         * @return updated request
         */
        public Request compartmentId(String compartmentOcid) {
            return add("compartmentId", compartmentOcid);
        }
    }

    /**
     * The content of the secret and metadata to help identify it.
     */
    public static class SecretContent extends ApiJsonBuilder<SecretContent> {
        private SecretContent() {
        }

        /**
         * Create a new secret content.
         * @return a new builder
         */
        public static SecretContent builder() {
            return new SecretContent();
        }

        /**
         * Create new content from plain text secret.
         * @param plainTextSecret plain text
         * @return a new builder with plain text content
         */
        public static SecretContent create(String plainTextSecret) {
            return builder().content(plainTextSecret);
        }
        /**
         * Names should be unique within a secret. Valid characters are uppercase or lowercase letters, numbers, hyphens,
         * underscores, and periods.
         * Optional.
         * @param name name of the secret
         * @return updated builder
         */
        public SecretContent name(String name) {
            return add("name", name);
        }

        /**
         * The text content of the secret.
         * Optional.
         *
         * @param content content of the secret
         * @return updated builder
         * @see #contentBase64(String)
         */
        public SecretContent content(String content) {
            return contentBase64(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        }

        /**
         * The base64-encoded content of the secret.
         * Optional.
         *
         * @param base64Content content
         * @return updated builder
         * @see #content(String) 
         */
        public SecretContent contentBase64(String base64Content) {
            add("contentType", "BASE64");
            return add("content", base64Content);
        }

        /**
         * The rotation state of the secret content. The default is CURRENT, meaning that the secret is currently in use. A
         * secret version that you mark as PENDING is staged and available for use, but you don't yet want to rotate it into
         * current, active use. For example, you might create or update a secret and mark its rotation state as PENDING if you
         * haven't yet updated the secret on the target system. When creating a secret, only the value CURRENT is applicable,
         * although the value LATEST is also automatically applied. When updating a secret, you can specify a version's
         * rotation state as either CURRENT or PENDING.
         * Optional.
         *
         * @param stage either {@link SecretStage#CURRENT} or {@link SecretStage#PENDING} are allowed
         * @return updated builder
         */
        public SecretContent stage(SecretStage stage) {
            return add("stage", stage.toString());
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends ApiEntityResponse {
        private final Secret secret;

        private Response(Builder builder) {
            super(builder);
            this.secret = Secret.create(builder.entity());
        }

        static Builder builder() {
            return new Builder();
        }

        public Secret secret() {
            return secret;
        }

        static final class Builder extends ApiEntityResponse.Builder<Builder, Response, JsonObject> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }
}
