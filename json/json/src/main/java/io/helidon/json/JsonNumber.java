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

package io.helidon.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

import io.helidon.common.Api;

/**
 * Represents a JSON number value.
 * Equality follows {@link BigDecimal#equals(Object)}, including its scale-sensitive semantics.
 */
@Api.Preview
public final class JsonNumber extends JsonValue {

    private final byte[] buffer;
    private final int start;
    private final int length;
    private byte jsonStartChar;
    private Object value;

    private JsonNumber(byte[] buffer, int start, int length) {
        this.buffer = buffer;
        this.start = start;
        this.length = length;
        this.jsonStartChar = buffer[start];
    }

    private JsonNumber(byte[] buffer,
                       int start,
                       int length,
                       BigIntegerExpansionBudget bigIntegerExpansionBudget) {
        this(buffer, start, length);
        this.value = bigIntegerExpansionBudget;
    }

    private JsonNumber(BigDecimal bigDecimalValue) {
        this.buffer = EMPTY_BYTES;
        this.start = -1;
        this.length = -1;
        this.value = Objects.requireNonNull(bigDecimalValue);
    }

    private JsonNumber(BigDecimal bigDecimalValue, BigIntegerExpansionBudget bigIntegerExpansionBudget) {
        this(bigDecimalValue);
        if (BigIntegerExpansionBudget.requiresExpansion(bigDecimalValue)
                && bigIntegerExpansionBudget.aggregateLimited()) {
            this.value = new BudgetedValue(bigIntegerExpansionBudget, bigDecimalValue);
        }
    }

    private JsonNumber(BigDecimal bigDecimalValue, byte jsonStartChar) {
        this(bigDecimalValue);
        this.jsonStartChar = jsonStartChar;
    }

    private JsonNumber(BigDecimal bigDecimalValue,
                       byte jsonStartChar,
                       BigIntegerExpansionBudget bigIntegerExpansionBudget) {
        this(bigDecimalValue, bigIntegerExpansionBudget);
        this.jsonStartChar = jsonStartChar;
    }

    /**
     * Create a JsonNumber from a BigDecimal value.
     *
     * @param bigDecimalValue the BigDecimal value
     * @return a new JsonNumber
     */
    public static JsonNumber create(BigDecimal bigDecimalValue) {
        return new JsonNumber(bigDecimalValue);
    }

    /**
     * Create a JsonNumber from a double value.
     *
     * @param doubleValue the double value
     * @return a new JsonNumber
     */
    public static JsonNumber create(double doubleValue) {
        BigDecimal value = BigDecimal.valueOf(doubleValue);
        return new JsonNumber(value, jsonStartChar(value));
    }

    /**
     * Create a JsonNumber from a long value.
     *
     * @param longValue the long value
     * @return a new JsonNumber
     */
    public static JsonNumber create(long longValue) {
        return new JsonNumber(BigDecimal.valueOf(longValue), jsonStartChar(longValue));
    }

    static JsonNumber create(byte[] buffer, int start, int length) {
        return new JsonNumber(buffer, start, length);
    }

    static JsonNumber create(byte[] buffer,
                             int start,
                             int length,
                             BigIntegerExpansionBudget bigIntegerExpansionBudget) {
        return new JsonNumber(buffer, start, length, bigIntegerExpansionBudget);
    }

    static JsonNumber create(BigDecimal value, JsonParserBase parser) {
        if (BigIntegerExpansionBudget.requiresExpansion(value)) {
            return new JsonNumber(value, parser.bigIntegerExpansionBudget());
        }
        return new JsonNumber(value);
    }

    static JsonNumber create(double value, JsonParserBase parser) {
        BigDecimal decimalValue = BigDecimal.valueOf(value);
        if (BigIntegerExpansionBudget.requiresExpansion(decimalValue)) {
            return new JsonNumber(decimalValue, jsonStartChar(decimalValue), parser.bigIntegerExpansionBudget());
        }
        return new JsonNumber(decimalValue, jsonStartChar(decimalValue));
    }

    static JsonNumber create(byte[] buffer,
                             int start,
                             int length,
                             JsonParserBase parser,
                             boolean hasExponent) {
        if (hasExponent && requiresExpansion(buffer, start, length)) {
            return new JsonNumber(buffer, start, length, parser.bigIntegerExpansionBudget());
        }
        return new JsonNumber(buffer, start, length);
    }

    @Override
    byte jsonStartChar() {
        if (jsonStartChar == 0) {
            jsonStartChar = jsonStartChar(bigDecimalValue());
        }
        return jsonStartChar;
    }

    private static byte jsonStartChar(BigDecimal value) {
        int signum = value.signum();
        if (signum < 0) {
            return '-';
        }
        if (signum == 0) {
            return '0';
        }
        int precision = value.precision();
        int scale = value.scale();
        int adjustedExponent = precision - scale - 1;
        if (scale >= precision && adjustedExponent >= -6) {
            return '0';
        }
        BigInteger unscaledValue = value.unscaledValue();
        if (unscaledValue.bitLength() < Long.SIZE) {
            return firstDigit(unscaledValue.longValue());
        }
        return (byte) unscaledValue.toString().charAt(0);
    }

    private static byte jsonStartChar(long value) {
        if (value < 0) {
            return '-';
        }
        return firstDigit(value);
    }

    private static byte firstDigit(long value) {
        while (value >= 10) {
            value /= 10;
        }
        return (byte) ('0' + value);
    }

    /**
     * Return the double value of this JsonNumber. This conversion may lose precision or return an infinity if the
     * magnitude is too large.
     *
     * @return the double value
     * @see BigDecimal#doubleValue()
     */
    public double doubleValue() {
        return bigDecimalValue().doubleValue();
    }

    /**
     * Return the byte value of this JsonNumber. Any fractional part is discarded. If the resulting integer does not fit,
     * only the low-order 8 bits are returned, so the result may have the opposite sign.
     *
     * @return the byte value
     * @see BigDecimal#byteValue()
     */
    public byte byteValue() {
        return bigDecimalValue().byteValue();
    }

    /**
     * Return the short value of this JsonNumber. Any fractional part is discarded. If the resulting integer does not fit,
     * only the low-order 16 bits are returned, so the result may have the opposite sign.
     *
     * @return the short value
     * @see BigDecimal#shortValue()
     */
    public short shortValue() {
        return bigDecimalValue().shortValue();
    }

    /**
     * Return the int value of this JsonNumber. Any fractional part is discarded. If the resulting integer does not fit,
     * only the low-order 32 bits are returned, so the result may have the opposite sign.
     *
     * @return the int value
     * @see BigDecimal#intValue()
     */
    public int intValue() {
        return bigDecimalValue().intValue();
    }

    /**
     * Return the long value of this JsonNumber. Any fractional part is discarded. If the resulting integer does not fit,
     * only the low-order 64 bits are returned, so the result may have the opposite sign.
     *
     * @return the long value
     * @see BigDecimal#longValue()
     */
    public long longValue() {
        return bigDecimalValue().longValue();
    }

    /**
     * Return the float value of this JsonNumber. This conversion may lose precision or return an infinity if the magnitude
     * is too large.
     *
     * @return the float value
     * @see BigDecimal#floatValue()
     */
    public float floatValue() {
        return bigDecimalValue().floatValue();
    }

    /**
     * Return the BigInteger value of this JsonNumber. Any fractional part is discarded.
     * <p>
     * For a nonzero value with a negative scale, a conversion may introduce at most 4,096 trailing decimal digits.
     * Numbers originating from the same parsed JSON document also share a cumulative budget of 65,536 such digits;
     * every invocation consumes that document budget. Programmatically created numbers have only the per-conversion
     * limit.
     * </p>
     *
     * @return the BigInteger value
     * @throws JsonException if the conversion exceeds the per-value or parsed-document expansion budget
     * @see BigDecimal#toBigInteger()
     */
    public BigInteger bigIntegerValue() {
        return bigIntegerExpansionBudget().toBigInteger(bigDecimalValue());
    }

    BigInteger bigIntegerValue(JsonParserBase parser) {
        BigDecimal decimalValue = bigDecimalValue();
        BigIntegerExpansionBudget expansionBudget = bigIntegerExpansionBudget();
        if (BigIntegerExpansionBudget.requiresExpansion(decimalValue)) {
            return expansionBudget.toBigInteger(decimalValue, parser, parser.bigIntegerExpansionBudget());
        }
        return expansionBudget.toBigInteger(decimalValue, parser);
    }

    /**
     * Return the BigDecimal value of this JsonNumber.
     *
     * @return the BigDecimal value
     */
    public BigDecimal bigDecimalValue() {
        Object currentValue = value;
        if (currentValue instanceof BigDecimal decimalValue) {
            return decimalValue;
        } else if (currentValue instanceof BudgetedValue budgetedValue) {
            return budgetedValue.value();
        }

        JsonParser parser = new JsonParserArray(buffer, start, length);
        BigDecimal decimalValue = parser.readBigDecimal();
        value = currentValue instanceof BigIntegerExpansionBudget budget
                && BigIntegerExpansionBudget.requiresExpansion(decimalValue)
                ? new BudgetedValue(budget, decimalValue)
                : decimalValue;
        return decimalValue;
    }

    @Override
    public JsonValueType type() {
        return JsonValueType.NUMBER;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof JsonNumber that)) {
            return false;
        }
        return bigDecimalValue().equals(that.bigDecimalValue());
    }

    @Override
    public int hashCode() {
        return bigDecimalValue().hashCode();
    }

    @Override
    public void toJson(JsonGenerator generator) {
        BigDecimal decimalValue = bigDecimalValue();
        int scale = decimalValue.scale();
        // Preserve legacy integer output for bounded trailing-zero expansion.
        if (scale == 0 || (scale < 0 && decimalValue.signum() == 0)) {
            generator.write(decimalValue.unscaledValue());
        } else if (scale >= -3 && scale < 0) {
            generator.write(decimalValue.toBigInteger());
        } else {
            generator.write(decimalValue);
        }
    }

    private static boolean requiresExpansion(byte[] buffer, int start, int length) {
        int end = start + length;
        int fractionDigits = 0;
        int exponentIndex = -1;
        boolean fraction = false;
        boolean nonZero = false;
        for (int i = start; i < end; i++) {
            byte current = buffer[i];
            if (current == '.') {
                fraction = true;
            } else if (current == 'e' || current == 'E') {
                exponentIndex = i + 1;
                break;
            } else if (current >= '0' && current <= '9') {
                nonZero |= current != '0';
                if (fraction) {
                    fractionDigits++;
                }
            }
        }
        if (!nonZero || exponentIndex == -1 || exponentIndex == end) {
            return false;
        }

        byte sign = buffer[exponentIndex];
        if (sign == '-') {
            return false;
        } else if (sign == '+') {
            exponentIndex++;
        }
        int exponent = 0;
        for (int i = exponentIndex; i < end; i++) {
            int digit = buffer[i] - '0';
            if (digit < 0 || digit > 9) {
                return true;
            }
            if (exponent > fractionDigits / 10
                    || (exponent == fractionDigits / 10 && digit > fractionDigits % 10)) {
                return true;
            }
            exponent = exponent * 10 + digit;
        }
        return false;
    }

    private BigIntegerExpansionBudget bigIntegerExpansionBudget() {
        Object currentValue = value;
        if (currentValue instanceof BigIntegerExpansionBudget budget) {
            return budget;
        } else if (currentValue instanceof BudgetedValue budgetedValue) {
            return budgetedValue.budget();
        }
        return BigIntegerExpansionBudget.noAggregate();
    }

    private record BudgetedValue(BigIntegerExpansionBudget budget, BigDecimal value) {
    }
}
