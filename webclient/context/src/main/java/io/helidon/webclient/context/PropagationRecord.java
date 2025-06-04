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

package io.helidon.webclient.context;

import java.util.List;
import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderName;

/**
 * Propagation record mapping classifier to header, may be a {@link java.lang.String} type, or and array of strings.
 */
interface PropagationRecord {
    static PropagationRecord create(ContextRecord contextRecord) {
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
     * Apply this record on web client headers, reading the classifier from the provided context and registering
     * headers.
     *
     * @param context context to read data from
     * @param headers headers to write headers to
     */
    void apply(Context context, ClientRequestHeaders headers);
}
