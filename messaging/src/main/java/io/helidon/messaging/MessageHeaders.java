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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;

/**
 * Immutable ordered messaging headers.
 * <p>
 * {@link #entries()} is the authoritative representation and preserves global insertion order, exact names, and
 * duplicate names. Lookup methods are explicitly first- or last-valued because transports assign different meanings
 * to duplicate entries. {@link #valuesByName()} is a derived grouped view and cannot represent cross-name ordering.
 * Connectors preserve every supported property and reject unsupported outbound values unless an explicit translation
 * is configured; they must not silently stringify, reorder, or drop entries.
 */
@Api.Preview
public final class MessageHeaders implements Iterable<MessageHeader> {
    private static final MessageHeaders EMPTY = new MessageHeaders(List.of());

    private final List<MessageHeader> entries;

    private MessageHeaders(List<? extends MessageHeader> entries) {
        this.entries = List.copyOf(entries);
    }

    /**
     * Empty headers.
     *
     * @return empty headers
     */
    public static MessageHeaders empty() {
        return EMPTY;
    }

    /**
     * Create an immutable ordered snapshot.
     *
     * @param entries ordered entries
     * @return headers
     */
    public static MessageHeaders create(List<? extends MessageHeader> entries) {
        List<? extends MessageHeader> actualEntries = Objects.requireNonNull(entries);
        return actualEntries.isEmpty() ? EMPTY : new MessageHeaders(actualEntries);
    }

    /**
     * Create immutable ordered headers.
     *
     * @param entries ordered entries
     * @return headers
     */
    public static MessageHeaders create(MessageHeader... entries) {
        return create(List.of(entries));
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
     * Authoritative ordered immutable entries.
     *
     * @return entries
     */
    public List<MessageHeader> entries() {
        return entries;
    }

    /**
     * Number of entries, including duplicate names.
     *
     * @return entry count
     */
    public int size() {
        return entries.size();
    }

    /**
     * Whether there are no entries.
     *
     * @return whether empty
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Whether an exact header name is present.
     *
     * @param name exact header name
     * @return whether present
     */
    public boolean contains(String name) {
        String actualName = Objects.requireNonNull(name);
        return entries.stream().anyMatch(entry -> entry.name().equals(actualName));
    }

    /**
     * First value with an exact header name.
     *
     * @param name exact header name
     * @return first value
     */
    public Optional<HeaderValue> first(String name) {
        String actualName = Objects.requireNonNull(name);
        for (MessageHeader entry : entries) {
            if (entry.name().equals(actualName)) {
                return Optional.of(entry.value());
            }
        }
        return Optional.empty();
    }

    /**
     * Last value with an exact header name.
     *
     * @param name exact header name
     * @return last value
     */
    public Optional<HeaderValue> last(String name) {
        String actualName = Objects.requireNonNull(name);
        for (int i = entries.size() - 1; i >= 0; i--) {
            MessageHeader entry = entries.get(i);
            if (entry.name().equals(actualName)) {
                return Optional.of(entry.value());
            }
        }
        return Optional.empty();
    }

    /**
     * All values with an exact header name in entry order.
     *
     * @param name exact header name
     * @return immutable values
     */
    public List<HeaderValue> all(String name) {
        String actualName = Objects.requireNonNull(name);
        ArrayList<HeaderValue> values = new ArrayList<>();
        for (MessageHeader entry : entries) {
            if (entry.name().equals(actualName)) {
                values.add(entry.value());
            }
        }
        return List.copyOf(values);
    }

    /**
     * Immutable grouped values in first-name-occurrence order.
     * <p>
     * Values for each name retain their relative order. This derived view does not retain interleaving between
     * different names; use {@link #entries()} when global order matters.
     *
     * @return immutable grouped values
     */
    public Map<String, List<HeaderValue>> valuesByName() {
        LinkedHashMap<String, List<HeaderValue>> values = new LinkedHashMap<>();
        for (MessageHeader entry : entries) {
            values.computeIfAbsent(entry.name(), ignored -> new ArrayList<>()).add(entry.value());
        }
        values.replaceAll((name, nameValues) -> List.copyOf(nameValues));
        return Collections.unmodifiableMap(values);
    }

    @Override
    public Iterator<MessageHeader> iterator() {
        return entries.iterator();
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof MessageHeaders that && entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return entries.toString();
    }

    /**
     * Mutable messaging-header builder.
     */
    public static final class Builder {
        private final List<MessageHeader> entries = new ArrayList<>();

        private Builder() {
        }

        /**
         * Append an entry.
         *
         * @param entry entry
         * @return updated builder
         */
        public Builder add(MessageHeader entry) {
            entries.add(Objects.requireNonNull(entry));
            return this;
        }

        /**
         * Append a value, retaining any entries with the same exact name.
         *
         * @param name exact header name
         * @param value value
         * @return updated builder
         */
        public Builder add(String name, HeaderValue value) {
            return add(MessageHeader.create(name, value));
        }

        /**
         * Append a text value, retaining any entries with the same exact name.
         *
         * @param name exact header name
         * @param value text value
         * @return updated builder
         */
        public Builder add(String name, String value) {
            return add(MessageHeader.create(name, value));
        }

        /**
         * Append all entries in their existing order.
         *
         * @param headers headers
         * @return updated builder
         */
        public Builder addAll(MessageHeaders headers) {
            entries.addAll(Objects.requireNonNull(headers).entries);
            return this;
        }

        /**
         * Replace all entries with an exact name and append one value.
         *
         * @param name exact header name
         * @param value value
         * @return updated builder
         */
        public Builder set(String name, HeaderValue value) {
            MessageHeader entry = MessageHeader.create(name, value);
            remove(entry.name());
            return add(entry);
        }

        /**
         * Replace all entries with an exact name and append one text value.
         *
         * @param name exact header name
         * @param value text value
         * @return updated builder
         */
        public Builder set(String name, String value) {
            MessageHeader entry = MessageHeader.create(name, value);
            remove(entry.name());
            return add(entry);
        }

        /**
         * Remove every entry with an exact name.
         *
         * @param name exact header name
         * @return updated builder
         */
        public Builder remove(String name) {
            String actualName = Objects.requireNonNull(name);
            entries.removeIf(entry -> entry.name().equals(actualName));
            return this;
        }

        /**
         * Remove all entries.
         *
         * @return updated builder
         */
        public Builder clear() {
            entries.clear();
            return this;
        }

        /**
         * Create an immutable ordered snapshot.
         *
         * @return headers
         */
        public MessageHeaders build() {
            return MessageHeaders.create(entries);
        }
    }
}
