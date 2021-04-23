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

package io.helidon.integrations.oci.connect;

import java.util.Optional;

import io.helidon.integrations.common.rest.ApiRestException;

/**
 * Exception used when OCI REST call returned and we have HTTP status and headers, and possibly an entity.
 */
public class OciRestException extends ApiRestException {
    private final String errorCode;
    private final String errorMessage;

    private OciRestException(Builder builder) {
        super(builder);

        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
    }

    /**
     * Create a new builder for this exception.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * OCI error code (if present in response).
     *
     * @return OCI error code
     */
    public Optional<String> ociCode() {
        return Optional.ofNullable(errorCode);
    }

    /**
     * OCI error message (if present in response).
     *
     * @return OCI error message
     */
    public Optional<String> ociMessage() {
        return Optional.ofNullable(errorMessage);
    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.oci.connect.OciRestException}.
     */
    public static class Builder extends BaseBuilder<Builder> implements io.helidon.common.Builder<OciRestException> {
        private String errorCode;
        private String errorMessage;

        private Builder() {
        }

        @Override
        public OciRestException build() {
            return new OciRestException(this);
        }

        /**
         * OCI specific error code.
         *
         * @param errorCode OCI error code
         * @return updated builder
         */
        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        /**
         * OCI specific error message.
         *
         * @param errorMessage OCI error message
         * @return updated builder
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
    }
}
