/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.context;

import java.util.List;
import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.common.context.http.ContextRecordConfig;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.ServerRequestHeaders;

/**
 * Propagation record mapping classifier to header, may be a {@link String} type, or and array of strings.
 */
interface PropagationRecord {
    static PropagationRecord create(ContextRecordConfig contextRecord) {
        HeaderName headerName = contextRecord.header();
        boolean isArray = contextRecord.array();
        String classifier = contextRecord.classifier().orElseGet(headerName::defaultCase);

        if (isArray) {
            List<String> defaults = contextRecord.defaultValues();
            if (defaults.isEmpty()) {
                if (contextRecord.defaultValue().isPresent()) {
                    defaults = List.of(contextRecord.defaultValue().get());
                }
            }
            return new ArrayRecord(classifier, headerName, defaults.toArray(new String[0]));
        } else {
            Optional<String> defaultValue = contextRecord.defaultValue();
            if (defaultValue.isEmpty()) {
                if (!contextRecord.defaultValues().isEmpty()) {
                    defaultValue = Optional.of(contextRecord.defaultValues().getFirst());
                }
            }
            return new StringRecord(classifier, headerName, defaultValue);
        }
    }

    /**
     * Apply this record on the request headers, and propagate configured header value to the context.
     *
     * @param headers headers to write headers to
     * @param context context to read data from
     */
    void apply(ServerRequestHeaders headers, Context context);

    class ArrayRecord implements PropagationRecord {
        private final String classifier;
        private final HeaderName headerName;
        private final Optional<String[]> defaultValue;

        ArrayRecord(String classifier, HeaderName headerName, String[] defaultValue) {
            this.classifier = classifier;
            this.headerName = headerName;
            this.defaultValue = defaultValue.length == 0 ? Optional.empty() : Optional.of(defaultValue);
        }

        @Override
        public void apply(ServerRequestHeaders headers, Context context) {
            headers.find(headerName)
                    .map(Header::allValues)
                    .map(it -> it.toArray(String[]::new))
                    .or(() -> defaultValue)
                    .ifPresent(array -> context.register(classifier, array));
        }

        @Override
        public String toString() {
            return classifier + " -> " + headerName + " (String[])";
        }
    }

    class StringRecord implements PropagationRecord {
        private final String classifier;
        private final HeaderName headerName;
        private final Optional<String> defaultValue;

        StringRecord(String classifier, HeaderName headerName, Optional<String> defaultValue) {
            this.classifier = classifier;
            this.headerName = headerName;
            this.defaultValue = defaultValue;
        }

        @Override
        public void apply(ServerRequestHeaders headers, Context context) {
            headers.find(headerName)
                    .map(Header::get)
                    .or(() -> defaultValue)
                    .ifPresent(value -> context.register(classifier, value));
        }

        @Override
        public String toString() {
            return classifier + " -> " + headerName + " (String)";
        }
    }
}
