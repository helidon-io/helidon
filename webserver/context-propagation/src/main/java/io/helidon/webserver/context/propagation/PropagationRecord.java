/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.webserver.context.propagation;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.webserver.ServerRequest;

/**
 * Propagation record mapping header to classifier, may be a {@link java.lang.String} type, or an array of strings.
 */
public interface PropagationRecord {
    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Apply this record on the server request, reading the header and registering the context value.
     *
     * @param req server request
     */
    void apply(ServerRequest req);

    /**
     * Fluent API builder for {@link io.helidon.webserver.context.propagation.PropagationRecord}.
     */
    @Configured(description = "Context propagation record mapping HTTP header to context classifier.")
    class Builder implements io.helidon.common.Builder<PropagationRecord> {
        private String classifier;
        private String headerName;
        private String[] defaultValue;
        private boolean isArray;

        private Builder() {
        }

        @Override
        public PropagationRecord build() {
            if (defaultValue == null || defaultValue.length == 0) {
                defaultValue = null;
            }
            if (classifier == null) {
                classifier = headerName;
            } else if (headerName == null) {
                headerName = classifier;
            }
            if (headerName == null) {
                throw new IllegalArgumentException("Either header name or classifier must be configured");
            }

            if (isArray) {
                return new ArrayRecord(classifier, headerName, defaultValue);
            } else {
                return new StringRecord(classifier, headerName, defaultValue == null ? null : defaultValue[0]);
            }
        }

        /**
         * Update from configuration.
         *
         * @param config configuration to use
         * @return updated builder
         */
        public Builder config(Config config) {
            config.get("classifier").asString().ifPresent(this::classifier);
            config.get("header").asString().ifPresent(this::header);
            config.get("array").asBoolean().ifPresent(this::array);
            if (isArray) {
                config.get("default-value").as(String[].class).ifPresent(this::defaultValue);
            } else {
                config.get("default-value").asString().ifPresent(this::defaultValue);
            }

            return this;
        }

        /**
         * Classifier of the value to be used with {@link io.helidon.common.context.Context#register(Object, Object)}.
         * Uses {@code classifier} configuration key, and if not present, uses the header name.
         *
         * @param classifier classifier to use
         * @return updated builder
         */
        @ConfiguredOption
        public Builder classifier(String classifier) {
            this.classifier = classifier;
            return this;
        }

        /**
         * Name of the header expected to contain value to be registered in context.
         * Uses {@code header} configuration key, and if not present, uses the classifier.
         *
         * @param headerName name of the header
         * @return updated builder
         */
        @ConfiguredOption
        public Builder header(String headerName) {
            this.headerName = headerName;
            return this;
        }

        /**
         * Default value to use, either a single string (any type), or an array of strings (only usable if this is an array).
         *
         * @param defaultValue default value to register in case the header is not present in the request
         * @return updated builder
         */
        @ConfiguredOption
        public Builder defaultValue(String... defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Whether this value is a String ({@code false}), or an array of Strings ({@code true}).
         * To read the value from context, you need to use the correct type (e.g. an array cannot be read as a String).
         *
         * @param isArray {@code true} to configure this as an array type, reading all values from the header
         * @return updated builder
         */
        @ConfiguredOption("false")
        public Builder array(boolean isArray) {
            this.isArray = isArray;
            return this;
        }
    }

}
