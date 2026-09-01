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
import java.util.Optional;

import io.helidon.common.Api;

/**
 * Immutable exact-name, single-valued metadata local to a message envelope.
 * <p>
 * Local metadata follows the same message envelope while it is used in-process. A newly created message starts with
 * empty local metadata unless its builder or implementation explicitly supplies a snapshot. Local metadata is separate
 * from {@link Message#headers() portable headers}; connectors and generic message mappers must never serialize it or
 * map it to a transport. Publishing a local value requires an explicit, application-controlled promotion to a
 * portable header, including any necessary redaction and size limit.
 */
@Api.Preview
public final class MessageMetadata {
    private static final MessageMetadata EMPTY = new MessageMetadata(Map.of());

    private final Map<String, HeaderValue> values;

    private MessageMetadata(Map<String, HeaderValue> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Empty local metadata.
     *
     * @return empty metadata
     */
    public static MessageMetadata empty() {
        return EMPTY;
    }

    /**
     * Create a mutable builder which produces immutable snapshots.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Immutable exact-name values.
     *
     * @return metadata values
     */
    public Map<String, HeaderValue> values() {
        return values;
    }

    /**
     * Number of metadata values.
     *
     * @return value count
     */
    public int size() {
        return values.size();
    }

    /**
     * Whether there are no metadata values.
     *
     * @return whether empty
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Whether an exact metadata name is present.
     *
     * @param name exact metadata name
     * @return whether present
     */
    public boolean contains(String name) {
        return values.containsKey(Objects.requireNonNull(name));
    }

    /**
     * Value with an exact metadata name.
     *
     * @param name exact metadata name
     * @return metadata value, or empty when absent
     */
    public Optional<HeaderValue> value(String name) {
        return Optional.ofNullable(values.get(Objects.requireNonNull(name)));
    }

    /**
     * Text value with an exact metadata name.
     * <p>
     * This method throws when the value exists but is not a {@link HeaderValue.TextValue}; it never stringifies typed
     * values.
     *
     * @param name exact metadata name
     * @return text value, or empty only when absent
     * @throws IllegalStateException if the value is present but is not text
     */
    public Optional<String> text(String name) {
        Optional<HeaderValue> value = value(name);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.get() instanceof HeaderValue.TextValue textValue) {
            return Optional.of(textValue.value());
        }
        throw new IllegalStateException("Local message metadata '" + name + "' is not a text value");
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof MessageMetadata that && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "MessageMetadata[size=" + values.size() + "]";
    }

    /**
     * Mutable local-metadata builder.
     */
    public static final class Builder {
        private final Map<String, HeaderValue> values = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Set a value, replacing the value with the same exact name.
         *
         * @param name exact metadata name
         * @param value metadata value
         * @return updated builder
         */
        public Builder set(String name, HeaderValue value) {
            String actualName = Objects.requireNonNull(name);
            HeaderValue actualValue = Objects.requireNonNull(value);
            values.put(actualName, actualValue);
            return this;
        }

        /**
         * Set a text value, replacing the value with the same exact name.
         *
         * @param name exact metadata name
         * @param value text value
         * @return updated builder
         */
        public Builder set(String name, String value) {
            String actualName = Objects.requireNonNull(name);
            HeaderValue actualValue = HeaderValue.text(value);
            values.put(actualName, actualValue);
            return this;
        }

        /**
         * Add all values, replacing values with matching exact names.
         *
         * @param metadata metadata
         * @return updated builder
         */
        public Builder addAll(MessageMetadata metadata) {
            values.putAll(Objects.requireNonNull(metadata).values);
            return this;
        }

        /**
         * Remove a value with an exact name.
         *
         * @param name exact metadata name
         * @return updated builder
         */
        public Builder remove(String name) {
            values.remove(Objects.requireNonNull(name));
            return this;
        }

        /**
         * Remove all values.
         *
         * @return updated builder
         */
        public Builder clear() {
            values.clear();
            return this;
        }

        /**
         * Create an immutable metadata snapshot.
         *
         * @return metadata
         */
        public MessageMetadata build() {
            return values.isEmpty() ? EMPTY : new MessageMetadata(values);
        }
    }
}
