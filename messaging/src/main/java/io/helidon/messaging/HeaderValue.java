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
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import io.helidon.common.Api;

/**
 * Immutable portable messaging header value.
 * <p>
 * The closed value model preserves values without retaining arbitrary application objects. A connector that must
 * preserve a transport-specific value which does not have a portable representation can use {@link NativeValue} or
 * expose the value through a connector-specific message subtype.
 */
@Api.Preview
public sealed interface HeaderValue permits HeaderValue.NullValue,
                                            HeaderValue.TextValue,
                                            HeaderValue.BinaryValue,
                                            HeaderValue.BooleanValue,
                                            HeaderValue.IntegerValue,
                                            HeaderValue.DecimalValue,
                                            HeaderValue.Float32Value,
                                            HeaderValue.Float64Value,
                                            HeaderValue.TimestampValue,
                                            HeaderValue.UuidValue,
                                            HeaderValue.NativeValue {
    /**
     * Create an explicit null value.
     *
     * @return null value
     */
    static NullValue nullValue() {
        return NullValue.INSTANCE;
    }

    /**
     * Create a text value.
     *
     * @param value text
     * @return text value
     */
    static TextValue text(String value) {
        return new TextValue(value);
    }

    /**
     * Create an immutable binary snapshot.
     *
     * @param value bytes to copy
     * @return binary value
     */
    static BinaryValue binary(byte[] value) {
        return new BinaryValue(value);
    }

    /**
     * Create a boolean value.
     *
     * @param value boolean
     * @return boolean value
     */
    static BooleanValue booleanValue(boolean value) {
        return new BooleanValue(value);
    }

    /**
     * Create an integer value.
     *
     * @param value integer
     * @return integer value
     */
    static IntegerValue integer(long value) {
        return new IntegerValue(BigInteger.valueOf(value));
    }

    /**
     * Create an arbitrary-precision integer value.
     *
     * @param value integer
     * @return integer value
     */
    static IntegerValue integer(BigInteger value) {
        return new IntegerValue(value);
    }

    /**
     * Create an arbitrary-precision decimal value.
     *
     * @param value decimal
     * @return decimal value
     */
    static DecimalValue decimal(BigDecimal value) {
        return new DecimalValue(value);
    }

    /**
     * Create a 32-bit IEEE 754 floating-point value.
     *
     * @param value floating-point value
     * @return floating-point value
     */
    static Float32Value floatingPoint(float value) {
        return new Float32Value(value);
    }

    /**
     * Create a 64-bit IEEE 754 floating-point value.
     *
     * @param value floating-point value
     * @return floating-point value
     */
    static Float64Value floatingPoint(double value) {
        return new Float64Value(value);
    }

    /**
     * Create a timestamp value.
     *
     * @param value timestamp
     * @return timestamp value
     */
    static TimestampValue timestamp(Instant value) {
        return new TimestampValue(value);
    }

    /**
     * Create a UUID value.
     *
     * @param value UUID
     * @return UUID value
     */
    static UuidValue uuid(UUID value) {
        return new UuidValue(value);
    }

    /**
     * Create an immutable transport-specific encoded value.
     * <p>
     * The type identifier must identify the connector or encoding well enough for a consumer to interpret the bytes.
     * This value does not imply Java serialization or cross-connector portability; another connector can interpret it
     * only when both connectors share the identified encoding.
     *
     * @param typeId non-blank type identifier
     * @param value encoded bytes to copy
     * @return native encoded value
     */
    static NativeValue nativeValue(String typeId, byte[] value) {
        return new NativeValue(typeId, value);
    }

    /**
     * Explicit null header value.
     */
    enum NullValue implements HeaderValue {
        /**
         * Singleton instance.
         */
        INSTANCE
    }

    /**
     * Text header value.
     *
     * @param value text
     */
    record TextValue(String value) implements HeaderValue {
        /**
         * Create a text value.
         *
         * @param value text
         */
        public TextValue {
            Objects.requireNonNull(value);
        }
    }

    /**
     * Boolean header value.
     *
     * @param value boolean
     */
    record BooleanValue(boolean value) implements HeaderValue {
    }

    /**
     * Arbitrary-precision integer header value.
     * <p>
     * The portable representation intentionally does not retain a transport's signedness or encoded integer width.
     * A connector requiring exact wire-type identity can use {@link NativeValue}.
     *
     * @param value integer
     */
    record IntegerValue(BigInteger value) implements HeaderValue {
        /**
         * Create an integer value.
         *
         * @param value integer
         */
        public IntegerValue {
            Objects.requireNonNull(value);
        }
    }

    /**
     * Arbitrary-precision decimal header value.
     *
     * @param value decimal
     */
    record DecimalValue(BigDecimal value) implements HeaderValue {
        /**
         * Create a decimal value.
         *
         * @param value decimal
         */
        public DecimalValue {
            Objects.requireNonNull(value);
        }
    }

    /**
     * 32-bit IEEE 754 floating-point header value.
     *
     * @param value floating-point value
     */
    record Float32Value(float value) implements HeaderValue {
    }

    /**
     * 64-bit IEEE 754 floating-point header value.
     *
     * @param value floating-point value
     */
    record Float64Value(double value) implements HeaderValue {
    }

    /**
     * Timestamp header value.
     *
     * @param value timestamp
     */
    record TimestampValue(Instant value) implements HeaderValue {
        /**
         * Create a timestamp value.
         *
         * @param value timestamp
         */
        public TimestampValue {
            Objects.requireNonNull(value);
        }
    }

    /**
     * UUID header value.
     *
     * @param value UUID
     */
    record UuidValue(UUID value) implements HeaderValue {
        /**
         * Create a UUID value.
         *
         * @param value UUID
         */
        public UuidValue {
            Objects.requireNonNull(value);
        }
    }

    /**
     * Immutable binary header value.
     */
    final class BinaryValue implements HeaderValue {
        private final byte[] value;

        private BinaryValue(byte[] value) {
            this.value = Objects.requireNonNull(value).clone();
        }

        /**
         * Binary value copy.
         *
         * @return copied bytes
         */
        public byte[] value() {
            return value.clone();
        }

        /**
         * Number of bytes.
         *
         * @return byte count
         */
        public int size() {
            return value.length;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof BinaryValue that && Arrays.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "BinaryValue[size=" + value.length + "]";
        }
    }

    /**
     * Immutable transport-specific encoded header value.
     */
    final class NativeValue implements HeaderValue {
        private final String typeId;
        private final byte[] value;

        private NativeValue(String typeId, byte[] value) {
            this.typeId = Objects.requireNonNull(typeId);
            if (typeId.isBlank()) {
                throw new IllegalArgumentException("Native header value type identifier must not be blank");
            }
            this.value = Objects.requireNonNull(value).clone();
        }

        /**
         * Connector or encoding type identifier.
         *
         * @return type identifier
         */
        public String typeId() {
            return typeId;
        }

        /**
         * Encoded value copy.
         *
         * @return copied bytes
         */
        public byte[] value() {
            return value.clone();
        }

        /**
         * Number of encoded bytes.
         *
         * @return byte count
         */
        public int size() {
            return value.length;
        }

        @Override
        public boolean equals(Object object) {
            return this == object
                    || object instanceof NativeValue that
                    && typeId.equals(that.typeId)
                    && Arrays.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return 31 * typeId.hashCode() + Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "NativeValue[typeId=" + typeId + ", size=" + value.length + "]";
        }
    }
}
