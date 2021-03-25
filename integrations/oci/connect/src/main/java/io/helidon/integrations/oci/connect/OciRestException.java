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

public class OciRestException extends ApiRestException {
    private final String errorCode;
    private final String errorMessage;

    private OciRestException(Builder builder) {
        super(builder);

        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
    }

    public static Builder ociBuilder() {
        return new Builder();
    }

    public Optional<String> ociCode() {
        return Optional.ofNullable(errorCode);
    }

    public Optional<String> ociMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public static class Builder extends BaseBuilder<Builder> implements io.helidon.common.Builder<OciRestException> {
        private String errorCode;
        private String errorMessage;

        private Builder() {
        }

        @Override
        public OciRestException build() {
            return new OciRestException(this);
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
    }
}
