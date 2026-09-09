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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.helidon.common.Api;

/**
 * One immutable messaging header entry.
 * <p>
 * Header names are exact and case-sensitive. The core model does not impose a transport-specific name grammar;
 * connectors validate names when mapping a message to their transport.
 */
@Api.Preview
public final class MessageHeader {
    private final String name;
    private final MessageHeaderValue value;

    private MessageHeader(String name, MessageHeaderValue value) {
        this.name = Objects.requireNonNull(name);
        this.value = Objects.requireNonNull(value);
    }

    /**
     * Create a header entry.
     *
     * @param name exact header name
     * @param value header value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, MessageHeaderValue value) {
        return new MessageHeader(name, value);
    }

    /**
     * Create a text header entry.
     *
     * @param name exact header name
     * @param value text value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, String value) {
        return new MessageHeader(name, MessageHeaderValue.TextValue.create(value));
    }

    /**
     * Create a binary header entry.
     *
     * @param name exact header name
     * @param value bytes to copy
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, byte[] value) {
        return new MessageHeader(name, MessageHeaderValue.BinaryValue.create(value));
    }

    /**
     * Create a boolean header entry.
     *
     * @param name exact header name
     * @param value boolean value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, Boolean value) {
        return new MessageHeader(name,
                                 MessageHeaderValue.BooleanValue.create(Objects.requireNonNull(value, "value")));
    }

    /**
     * Create an integer header entry.
     *
     * @param name exact header name
     * @param value integer value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, Byte value) {
        return new MessageHeader(name,
                                 MessageHeaderValue.IntegerValue.create(
                                         Objects.requireNonNull(value, "value").longValue()));
    }

    /**
     * Create an integer header entry.
     *
     * @param name exact header name
     * @param value integer value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, Short value) {
        return new MessageHeader(name,
                                 MessageHeaderValue.IntegerValue.create(
                                         Objects.requireNonNull(value, "value").longValue()));
    }

    /**
     * Create an integer header entry.
     *
     * @param name exact header name
     * @param value integer value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, Integer value) {
        return new MessageHeader(name,
                                 MessageHeaderValue.IntegerValue.create(
                                         Objects.requireNonNull(value, "value").longValue()));
    }

    /**
     * Create an integer header entry.
     *
     * @param name exact header name
     * @param value integer value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, Long value) {
        return new MessageHeader(name,
                                 MessageHeaderValue.IntegerValue.create(Objects.requireNonNull(value, "value")));
    }

    /**
     * Create an arbitrary-precision integer header entry.
     *
     * @param name exact header name
     * @param value integer value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, BigInteger value) {
        return new MessageHeader(name, MessageHeaderValue.IntegerValue.create(value));
    }

    /**
     * Create an arbitrary-precision decimal header entry.
     *
     * @param name exact header name
     * @param value decimal value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, BigDecimal value) {
        return new MessageHeader(name, MessageHeaderValue.DecimalValue.create(value));
    }

    /**
     * Create a 32-bit IEEE 754 floating-point header entry.
     *
     * @param name exact header name
     * @param value floating-point value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, Float value) {
        return new MessageHeader(name,
                                 MessageHeaderValue.Float32Value.create(Objects.requireNonNull(value, "value")));
    }

    /**
     * Create a 64-bit IEEE 754 floating-point header entry.
     *
     * @param name exact header name
     * @param value floating-point value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, Double value) {
        return new MessageHeader(name,
                                 MessageHeaderValue.Float64Value.create(Objects.requireNonNull(value, "value")));
    }

    /**
     * Create a timestamp header entry.
     *
     * @param name exact header name
     * @param value timestamp value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, Instant value) {
        return new MessageHeader(name, MessageHeaderValue.TimestampValue.create(value));
    }

    /**
     * Create a UUID header entry.
     *
     * @param name exact header name
     * @param value UUID value
     * @return header entry
     * @throws NullPointerException if {@code name} or {@code value} is {@code null}
     */
    public static MessageHeader create(String name, UUID value) {
        return new MessageHeader(name, MessageHeaderValue.UuidValue.create(value));
    }

    /**
     * Exact header name.
     *
     * @return header name
     */
    public String name() {
        return name;
    }

    /**
     * Header value.
     *
     * @return header value
     */
    public MessageHeaderValue value() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        return this == object
                || object instanceof MessageHeader that
                && name.equals(that.name)
                && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + value.hashCode();
    }

    @Override
    public String toString() {
        return "MessageHeader[name=" + name + ", value=" + value + "]";
    }
}
