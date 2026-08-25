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
 * Message envelope with a non-null payload and portable headers.
 * <p>
 * Portable headers are single-valued and keyed by exact, case-sensitive names. Implementations must be immutable
 * snapshots and must return an immutable map from {@link #headers()}. Connector-specific message subtypes may expose
 * richer native header representations separately.
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
     * Single-valued portable headers.
     * <p>
     * The returned map uses exact, case-sensitive names and does not define an iteration order.
     *
     * @return immutable headers
     */
    Map<String, String> headers();

    /**
     * Portable header value.
     *
     * @param name header name
     * @return header value
     */
    default Optional<String> header(String name) {
        return Optional.ofNullable(headers().get(name));
    }

    /**
     * Message builder.
     *
     * @param <T> payload type
     */
    final class Builder<T> {
        private final T entity;
        private final Map<String, String> headers = new LinkedHashMap<>();

        private Builder(T entity) {
            this.entity = Objects.requireNonNull(entity, "entity");
        }

        /**
         * Set a portable header.
         * <p>
         * Setting the same exact name again replaces its previous value, so the last value set for that name wins.
         *
         * @param name header name
         * @param value header value
         * @return updated builder
         */
        public Builder<T> header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /**
         * Create the message.
         *
         * @return immutable message
         */
        public Message<T> build() {
            return new DefaultMessage<>(entity, headers);
        }
    }
}
