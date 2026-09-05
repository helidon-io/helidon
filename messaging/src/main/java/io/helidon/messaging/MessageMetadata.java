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
 * <p>
 * This interface is sealed because its single abstract accessor would otherwise make it a lambda target. Lambdas
 * cannot implement the value-based {@link Object#equals(Object)} and {@link Object#hashCode()} required by this type.
 * Keeping implementations under Helidon control also guarantees value-independent {@link Object#toString()} output
 * and a stable, immutable, non-null value map containing no null names or values.
 */
@Api.Preview
public sealed interface MessageMetadata permits MessageMetadataImpl {

    /**
     * Empty local metadata.
     *
     * @return empty metadata
     */
    static MessageMetadata empty() {
        return MessageMetadataImpl.empty();
    }

    /**
     * Create a mutable builder which produces immutable snapshots.
     *
     * @return builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Immutable exact-name values.
     *
     * @return stable, immutable, non-null metadata values
     */
    Map<String, MessageHeaderValue> values();

    /**
     * Number of metadata values.
     *
     * @return value count
     */
    default int size() {
        return values().size();
    }

    /**
     * Whether there are no metadata values.
     *
     * @return whether empty
     */
    default boolean isEmpty() {
        return values().isEmpty();
    }

    /**
     * Whether an exact metadata name is present.
     *
     * @param name exact metadata name
     * @return whether present
     */
    default boolean contains(String name) {
        String actualName = Objects.requireNonNull(name);
        return values().containsKey(actualName);
    }

    /**
     * Value with an exact metadata name.
     *
     * @param name exact metadata name
     * @return metadata value, or empty when absent
     */
    default Optional<MessageHeaderValue> value(String name) {
        String actualName = Objects.requireNonNull(name);
        return Optional.ofNullable(values().get(actualName));
    }

    /**
     * Text value with an exact metadata name.
     * <p>
     * This method throws when the value exists but is not a {@link MessageHeaderValue.TextValue}; it never stringifies typed
     * values.
     *
     * @param name exact metadata name
     * @return text value, or empty only when absent
     * @throws IllegalStateException if the value is present but is not text
     */
    default Optional<String> text(String name) {
        Optional<MessageHeaderValue> value = value(name);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.get() instanceof MessageHeaderValue.TextValue textValue) {
            return Optional.of(textValue.value());
        }
        throw new IllegalStateException("Local message metadata '" + name + "' is not a text value");
    }

    /**
     * Mutable local-metadata builder.
     */
    final class Builder {
        private final Map<String, MessageHeaderValue> values = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Set a value, replacing the value with the same exact name.
         *
         * @param name exact metadata name
         * @param value metadata value
         * @return updated builder
         */
        public Builder set(String name, MessageHeaderValue value) {
            String actualName = Objects.requireNonNull(name);
            MessageHeaderValue actualValue = Objects.requireNonNull(value);
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
            MessageHeaderValue actualValue = MessageHeaderValue.text(value);
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
            values.putAll(Objects.requireNonNull(metadata).values());
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
            return MessageMetadataImpl.create(values);
        }
    }
}
