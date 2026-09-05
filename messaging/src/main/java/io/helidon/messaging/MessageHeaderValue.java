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
 * Immutable value used by portable messaging headers and local message metadata.
 * <p>
 * The closed value model preserves values without retaining arbitrary application objects. Values in
 * {@link Message#headers() portable headers} are subject to connector mapping rules; values in
 * {@link Message#localMetadata() local metadata} are never generically mapped. A connector that must preserve a
 * transport-specific value which does not have a portable representation can use {@link NativeValue} in a portable
 * header or expose the value through a connector-specific message subtype.
 */
@Api.Preview
public sealed interface MessageHeaderValue permits MessageHeaderValue.NullValue,
                                            MessageHeaderValue.TextValue,
                                            MessageHeaderValue.BinaryValue,
                                            MessageHeaderValue.BooleanValue,
                                            MessageHeaderValue.IntegerValue,
                                            MessageHeaderValue.DecimalValue,
                                            MessageHeaderValue.Float32Value,
                                            MessageHeaderValue.Float64Value,
                                            MessageHeaderValue.TimestampValue,
                                            MessageHeaderValue.UuidValue,
                                            MessageHeaderValue.NativeValue {
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
     * Explicit null messaging value.
     */
    final class NullValue implements MessageHeaderValue {
        private static final NullValue INSTANCE = new NullValue();

        private NullValue() {
        }

        @Override
        public String toString() {
            return "INSTANCE";
        }
    }

    /**
     * Text messaging value.
     */
    final class TextValue implements MessageHeaderValue {
        private final String value;

        private TextValue(String value) {
            this.value = Objects.requireNonNull(value);
        }

        /**
         * Text value.
         *
         * @return text
         */
        public String value() {
            return value;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof TextValue that && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "TextValue[value=" + value + "]";
        }
    }

    /**
     * Boolean messaging value.
     */
    final class BooleanValue implements MessageHeaderValue {
        private final boolean value;

        private BooleanValue(boolean value) {
            this.value = value;
        }

        /**
         * Boolean value.
         *
         * @return boolean
         */
        public boolean value() {
            return value;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof BooleanValue that && value == that.value;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(value);
        }

        @Override
        public String toString() {
            return "BooleanValue[value=" + value + "]";
        }
    }

    /**
     * Arbitrary-precision integer messaging value.
     * <p>
     * The portable representation intentionally does not retain a transport's signedness or encoded integer width.
     * A connector requiring exact wire-type identity can use {@link NativeValue}.
     */
    final class IntegerValue implements MessageHeaderValue {
        private final BigInteger value;

        private IntegerValue(BigInteger value) {
            this.value = Objects.requireNonNull(value);
        }

        /**
         * Integer value.
         *
         * @return integer
         */
        public BigInteger value() {
            return value;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof IntegerValue that && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "IntegerValue[value=" + value + "]";
        }
    }

    /**
     * Arbitrary-precision decimal messaging value.
     */
    final class DecimalValue implements MessageHeaderValue {
        private final BigDecimal value;

        private DecimalValue(BigDecimal value) {
            this.value = Objects.requireNonNull(value);
        }

        /**
         * Decimal value.
         *
         * @return decimal
         */
        public BigDecimal value() {
            return value;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof DecimalValue that && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "DecimalValue[value=" + value + "]";
        }
    }

    /**
     * 32-bit IEEE 754 floating-point messaging value.
     */
    final class Float32Value implements MessageHeaderValue {
        private final float value;

        private Float32Value(float value) {
            this.value = value;
        }

        /**
         * Floating-point value.
         *
         * @return floating-point value
         */
        public float value() {
            return value;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof Float32Value that && Float.compare(value, that.value) == 0;
        }

        @Override
        public int hashCode() {
            return Float.hashCode(value);
        }

        @Override
        public String toString() {
            return "Float32Value[value=" + value + "]";
        }
    }

    /**
     * 64-bit IEEE 754 floating-point messaging value.
     */
    final class Float64Value implements MessageHeaderValue {
        private final double value;

        private Float64Value(double value) {
            this.value = value;
        }

        /**
         * Floating-point value.
         *
         * @return floating-point value
         */
        public double value() {
            return value;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof Float64Value that && Double.compare(value, that.value) == 0;
        }

        @Override
        public int hashCode() {
            return Double.hashCode(value);
        }

        @Override
        public String toString() {
            return "Float64Value[value=" + value + "]";
        }
    }

    /**
     * Timestamp messaging value.
     */
    final class TimestampValue implements MessageHeaderValue {
        private final Instant value;

        private TimestampValue(Instant value) {
            this.value = Objects.requireNonNull(value);
        }

        /**
         * Timestamp value.
         *
         * @return timestamp
         */
        public Instant value() {
            return value;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof TimestampValue that && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "TimestampValue[value=" + value + "]";
        }
    }

    /**
     * UUID messaging value.
     */
    final class UuidValue implements MessageHeaderValue {
        private final UUID value;

        private UuidValue(UUID value) {
            this.value = Objects.requireNonNull(value);
        }

        /**
         * UUID value.
         *
         * @return UUID
         */
        public UUID value() {
            return value;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof UuidValue that && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "UuidValue[value=" + value + "]";
        }
    }

    /**
     * Immutable binary messaging value.
     */
    final class BinaryValue implements MessageHeaderValue {
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
     * Immutable transport-specific encoded messaging value.
     */
    final class NativeValue implements MessageHeaderValue {
        private final String typeId;
        private final byte[] value;

        private NativeValue(String typeId, byte[] value) {
            this.typeId = Objects.requireNonNull(typeId);
            if (typeId.isBlank()) {
                throw new IllegalArgumentException("Native messaging value type identifier must not be blank");
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
