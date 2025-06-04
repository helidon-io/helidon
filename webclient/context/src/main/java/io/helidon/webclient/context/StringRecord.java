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

import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderName;

class StringRecord implements PropagationRecord {
    private final String classifier;
    private final HeaderName headerName;
    private final Optional<String> defaultValue;

    StringRecord(String classifier, HeaderName headerName, Optional<String> defaultValue) {
        this.classifier = classifier;
        this.headerName = headerName;
        this.defaultValue = defaultValue;
    }

    public void apply(Context context, ClientRequestHeaders headers) {
        context.get(classifier, String.class)
                .or(() -> defaultValue)
                .ifPresent(it -> headers.set(headerName, it));
    }

    @Override
    public String toString() {
        return classifier + " -> " + headerName + " (String)";
    }
}
