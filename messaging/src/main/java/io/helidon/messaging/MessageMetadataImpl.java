/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class MessageMetadataImpl implements MessageMetadata {
    private static final MessageMetadata EMPTY = new MessageMetadataImpl(Map.of());

    private final Map<String, MessageHeaderValue> values;

    private MessageMetadataImpl(Map<String, MessageHeaderValue> values) {
        LinkedHashMap<String, MessageHeaderValue> actualValues = new LinkedHashMap<>();
        for (Map.Entry<String, MessageHeaderValue> entry : values.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey());
            MessageHeaderValue value = Objects.requireNonNull(entry.getValue());
            actualValues.put(name, value);
        }
        this.values = Collections.unmodifiableMap(actualValues);
    }

    static MessageMetadata empty() {
        return EMPTY;
    }

    static MessageMetadata create(Map<String, MessageHeaderValue> values) {
        Map<String, MessageHeaderValue> actualValues = Objects.requireNonNull(values);
        return actualValues.isEmpty() ? EMPTY : new MessageMetadataImpl(actualValues);
    }

    @Override
    public Map<String, MessageHeaderValue> values() {
        return values;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof MessageMetadataImpl that && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "MessageMetadata[size=" + values.size() + "]";
    }
}
