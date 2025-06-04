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

package io.helidon.webclient.context;

import io.helidon.common.config.Config;
import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;

/**
 * Propagation record mapping classifier to header, may be a {@link java.lang.String} type, or and array of strings.
 */
interface PropagationRecord {
    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Apply this record on web client headers, reading the classifier from the provided context and registering
     * headers.
     *
     * @param context context to read data from
     * @param headers headers to write headers to
     */
    void apply(Context context, ClientRequestHeaders headers);

    /**
     * Fluent API builder for {@link io.helidon.webclient.context.propagation.PropagationRecord}.
     */
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
        Builder config(Config config) {
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
         * Classifier of the value to be used with {@link io.helidon.common.context.Context#get(Object, Class)}.
         * Uses {@code classifier} configuration key, and if not present, uses the header name.
         *
         * @param classifier classifier to use
         * @return updated builder
         */
        Builder classifier(String classifier) {
            this.classifier = classifier;
            return this;
        }

        /**
         * Name of the header used to propagate the context value.
         * Uses {@code header} configuration key, and if not present, uses the classifier.
         *
         * @param headerName name of the header
         * @return updated builder
         */
        Builder header(String headerName) {
            this.headerName = headerName;
            return this;
        }

        /**
         * Default value to use, either a single string (any type), or an array of strings (only usable if this is an array).
         *
         * @param defaultValue default value to send over HTTP header if not present in the context
         * @return updated builder
         */
        Builder defaultValue(String... defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Whether this value is a String ({@code false}), or an array of Strings ({@code true}).
         * To write the value to context, you need to use the correct type (e.g. an array cannot be written as a String).
         *
         * @param isArray {@code true} to configure this as an array type, writing all values from the context
         * @return updated builder
         */
        Builder array(boolean isArray) {
            this.isArray = isArray;
            return this;
        }
    }
}
