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

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;

/**
 * Message envelope with a non-null payload and portable headers.
 * <p>
 * Portable headers retain global order, duplicate exact case-sensitive names, and immutable typed values.
 * Implementations must be immutable snapshots. Connector-specific message subtypes may expose richer native metadata
 * separately.
 *
 * @param <T> payload type
 */
@Api.Preview
public interface Message<T> {
    /**
     * Create a message builder.
     *
     * @param entity non-null payload
     * @param <T> payload type
     * @return builder
     * @throws NullPointerException if {@code entity} is {@code null}
     */
    static <T> Builder<T> builder(T entity) {
        return new Builder<>(entity);
    }

    /**
     * Create a payload-only message.
     *
     * @param entity non-null payload
     * @param <T> payload type
     * @return message
     * @throws NullPointerException if {@code entity} is {@code null}
     */
    static <T> Message<T> create(T entity) {
        return builder(entity).build();
    }

    /**
     * Payload.
     *
     * @return non-null payload
     */
    T entity();

    /**
     * Ordered portable headers.
     * <p>
     * Connectors preserve exact names, global order, duplicates, and value kinds when supported by their transport.
     * An unsupported outbound value must be explicitly translated or rejected, never silently stringified, reordered,
     * or dropped.
     *
     * @return immutable headers
     */
    MessageHeaders headers();

    /**
     * Last portable header value with an exact name.
     *
     * @param name header name
     * @return header value, or empty when the exact name is absent
     */
    default Optional<HeaderValue> headerValue(String name) {
        return headers().last(name);
    }

    /**
     * Last portable text header value with an exact name.
     * <p>
     * This convenience method preserves the original text-header API. It throws when the last value exists but is not
     * a {@link HeaderValue.TextValue}; it never stringifies typed values or skips past a later non-text value.
     *
     * @param name header name
     * @return header value, or empty only when the exact name is absent
     * @throws IllegalStateException if the last value is present but is not text
     */
    default Optional<String> header(String name) {
        Optional<HeaderValue> value = headerValue(name);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.get() instanceof HeaderValue.TextValue textValue) {
            return Optional.of(textValue.value());
        }
        throw new IllegalStateException("Messaging header '" + name + "' is not a text value");
    }

    /**
     * Message builder.
     *
     * @param <T> payload type
     */
    final class Builder<T> {
        private final T entity;
        private final MessageHeaders.Builder headers = MessageHeaders.builder();

        private Builder(T entity) {
            this.entity = Objects.requireNonNull(entity, "entity");
        }

        /**
         * Set a portable text header, replacing all values with the same exact name.
         *
         * @param name header name
         * @param value header value
         * @return updated builder
         */
        public Builder<T> header(String name, String value) {
            headers.set(name, value);
            return this;
        }

        /**
         * Set a portable typed header, replacing all values with the same exact name.
         *
         * @param name header name
         * @param value header value
         * @return updated builder
         */
        public Builder<T> header(String name, HeaderValue value) {
            headers.set(name, value);
            return this;
        }

        /**
         * Append a portable text header, retaining values with the same exact name.
         *
         * @param name header name
         * @param value header value
         * @return updated builder
         */
        public Builder<T> addHeader(String name, String value) {
            headers.add(name, value);
            return this;
        }

        /**
         * Append a portable typed header, retaining values with the same exact name.
         *
         * @param name header name
         * @param value header value
         * @return updated builder
         */
        public Builder<T> addHeader(String name, HeaderValue value) {
            headers.add(name, value);
            return this;
        }

        /**
         * Append a portable header entry.
         *
         * @param header header entry
         * @return updated builder
         */
        public Builder<T> addHeader(MessageHeader header) {
            headers.add(header);
            return this;
        }

        /**
         * Replace all current headers with an ordered snapshot.
         *
         * @param headers headers
         * @return updated builder
         */
        public Builder<T> headers(MessageHeaders headers) {
            MessageHeaders actualHeaders = Objects.requireNonNull(headers);
            this.headers.clear().addAll(actualHeaders);
            return this;
        }

        /**
         * Create the message.
         *
         * @return immutable message
         */
        public Message<T> build() {
            return new DefaultMessage<>(entity, headers.build());
        }
    }
}
