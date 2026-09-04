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
import io.helidon.messaging.spi.ConnectorDeliveryReservation;

/**
 * Message envelope with a payload, portable headers, and optional local metadata.
 * <p>
 * Ordinary messages expose a non-null payload. A connector-specific immutable metadata envelope created after transport
 * mapping fails may instead throw {@link MessagingException} from {@link #entity()}; such an envelope is only valid in
 * the batch passed to {@link ConnectorDeliveryReservation#startFailed(MessageBatch, RuntimeException)}. Portable headers
 * retain global order, duplicate exact case-sensitive names, and immutable typed values. Implementations must be
 * immutable snapshots. Accessors may be invoked repeatedly and concurrently; implementations must expose stable values
 * without consuming one-shot state. {@link #localMetadata()} follows the same in-process envelope but is never part of
 * portable headers or generic connector mapping. Connector-specific message subtypes may expose richer native metadata
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
        return new Builder<>(Objects.requireNonNull(entity, "entity"));
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
     * <p>
     * Repeated and concurrent invocations must be safe, and successful invocations must return the same stable payload
     * value. A connector metadata envelope whose transport payload could not be mapped must instead consistently throw
     * {@link MessagingException}.
     *
     * @return non-null payload
     * @throws MessagingException if this is a connector metadata envelope whose transport payload could not be mapped
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
     * Metadata local to this message envelope.
     * <p>
     * Local metadata follows the same envelope while it is used in-process. Connectors and generic message mappers
     * must never serialize or map it to a transport. A local value must be explicitly promoted to a portable header
     * before it can be sent, including any necessary redaction and size limit.
     *
     * @return immutable local metadata
     */
    default MessageMetadata localMetadata() {
        return MessageMetadata.empty();
    }

    /**
     * Last portable header value with an exact name.
     *
     * @param name header name
     * @return header value, or empty when the exact name is absent
     */
    default Optional<HeaderValue> headerValue(String name) {
        Objects.requireNonNull(name, "name");
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
        Objects.requireNonNull(name, "name");
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
        private final MessageMetadata.Builder localMetadata = MessageMetadata.builder();

        private Builder(T entity) {
            this.entity = entity;
        }

        /**
         * Set a portable text header, replacing all values with the same exact name.
         *
         * @param name header name
         * @param value header value
         * @return updated builder
         */
        public Builder<T> header(String name, String value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
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
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
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
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
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
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
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
            Objects.requireNonNull(header, "header");
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
         * Set a local text metadata value, replacing the value with the same exact name.
         *
         * @param name exact metadata name
         * @param value text value
         * @return updated builder
         */
        public Builder<T> localMetadata(String name, String value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            localMetadata.set(name, value);
            return this;
        }

        /**
         * Set a local typed metadata value, replacing the value with the same exact name.
         *
         * @param name exact metadata name
         * @param value metadata value
         * @return updated builder
         */
        public Builder<T> localMetadata(String name, HeaderValue value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            localMetadata.set(name, value);
            return this;
        }

        /**
         * Replace all local metadata with an immutable snapshot.
         * <p>
         * Local metadata remains in-process and is not part of portable headers or generic connector mapping.
         *
         * @param localMetadata local metadata
         * @return updated builder
         */
        public Builder<T> localMetadata(MessageMetadata localMetadata) {
            MessageMetadata actualMetadata = Objects.requireNonNull(localMetadata);
            this.localMetadata.clear().addAll(actualMetadata);
            return this;
        }

        /**
         * Create the message.
         *
         * @return immutable message
         */
        public Message<T> build() {
            return new DefaultMessage<>(entity, headers.build(), localMetadata.build());
        }
    }
}
