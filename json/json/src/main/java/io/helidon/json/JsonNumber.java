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

/**
 * Represents a JSON number value.
 */
public final class JsonNumber extends JsonValue {

    private final byte[] buffer;
    private final int start;
    private final int length;
    private Double doubleValue;
    private Integer intValue;
    private BigDecimal bigDecimalValue;

    private JsonNumber(byte[] buffer, int start, int length) {
        this.buffer = buffer;
        this.start = start;
        this.length = length;
    }

    private JsonNumber(BigDecimal bigDecimalValue) {
        this.buffer = EMPTY_BYTES;
        this.start = -1;
        this.length = -1;
        this.bigDecimalValue = bigDecimalValue;
        this.intValue = bigDecimalValue.intValue();
        this.doubleValue = bigDecimalValue.doubleValue();
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

    static JsonNumber create(byte[] buffer, int start, int length) {
        return new JsonNumber(buffer, start, length);
    }

    @Override
    byte jsonStartChar() {
        // Approximate the first character of the number string for optimization
        // This is used by some parsers to quickly identify number values
        int val = intValue();
        if (val < 0) {
            return '-';
        }
        // For positive numbers, calculate first digit using log10
        // Note: this is an approximation and may not be accurate for all cases
        int digits = (int) Math.log10(val);
        return (byte) ('0' + (val / (int) Math.pow(10, digits)));
    }

    /**
     * Return the double value of this JsonNumber.
     *
     * @return the double value
     */
    public double doubleValue() {
        if (doubleValue == null) {
            JsonParser parser = new ArrayJsonParser(buffer, start, start + length);
            doubleValue = parser.readDouble();
        }
        return doubleValue;
    }

    /**
     * Return the int value of this JsonNumber.
     *
     * @return the int value
     */
    public int intValue() {
        if (intValue == null) {
            JsonParser parser = new ArrayJsonParser(buffer, start, start + length);
            intValue = parser.readInt();
        }
        return intValue;
    }

    /**
     * Return the BigDecimal value of this JsonNumber.
     *
     * @return the BigDecimal value
     */
    public BigDecimal bigDecimalValue() {
        if (bigDecimalValue == null) {
            JsonParser parser = new ArrayJsonParser(buffer, start, start + length);
            bigDecimalValue = new BigDecimal(parser.readCharArray());
        }
        return bigDecimalValue;
    }

    @Override
    public JsonValueType type() {
        return JsonValueType.NUMBER;
    }

    @Override
    public void toJson(JsonGenerator generator) {
        BigDecimal bigDecimal = bigDecimalValue();
        if (bigDecimal.stripTrailingZeros().scale() <= 0) {
            generator.write(bigDecimal.longValue());
        } else {
            generator.write(bigDecimal.doubleValue());
        }
    }
}
