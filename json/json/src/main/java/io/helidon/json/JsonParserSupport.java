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
import io.helidon.common.Size;

/**
 * Internal cross-module support for built-in JSON parser implementations.
 */
@Api.Internal
public final class JsonParserSupport {
    private JsonParserSupport() {
    }

    /**
     * Converts a configured replay buffer capacity to bytes.
     *
     * @param size configured maximum capacity
     * @return maximum capacity in bytes
     * @throws IllegalArgumentException if the size does not fit a non-negative array length
     */
    public static int replayBufferBytes(Size size) {
        try {
            int bytes = Math.toIntExact(Objects.requireNonNull(size).toBytes());
            if (bytes >= 0) {
                return bytes;
            }
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("max-replay-size must be between 0 and " + Integer.MAX_VALUE + " bytes", e);
        }
        throw new IllegalArgumentException("max-replay-size must be non-negative");
    }

    /**
     * Grows a compacted stream input buffer up to its maximum capacity.
     *
     * @param parser stream parser
     * @param buffer current buffer
     * @param kept number of retained bytes at the beginning of the buffer
     * @param required minimum required capacity
     * @param maximum maximum capacity in bytes for this growth
     * @return buffer for the next read
     * @throws JsonException if the minimum required capacity exceeds the maximum
     */
    public static byte[] growStreamBuffer(JsonParser parser, byte[] buffer,
                                          int kept, long required, int maximum) {
        Objects.requireNonNull(buffer);
        Objects.requireNonNull(parser);
        if (required > maximum) {
            if (maximum < Integer.MAX_VALUE) {
                throw parser.createException("Marked stream buffer exceeds max-replay-size of " + maximum + " bytes");
            }
            throw parser.createException("Stream input buffer exceeds the maximum array length");
        }
        int capacity = (int) Math.min(maximum, Math.max(required, (long) buffer.length * 2));
        byte[] result = new byte[capacity];
        System.arraycopy(buffer, 0, result, 0, kept);
        return result;
    }

    /**
     * Creates a JSON number associated with a built-in parser's expansion budget when needed.
     *
     * @param parser parser producing the number
     * @param value decimal value
     * @return JSON number
     */
    public static JsonNumber createJsonNumber(JsonParserBase parser, BigDecimal value) {
        return JsonNumber.create(Objects.requireNonNull(value), Objects.requireNonNull(parser));
    }

    /**
     * Creates a JSON number associated with a built-in parser's expansion budget when needed.
     *
     * @param parser parser producing the number
     * @param value double value
     * @return JSON number
     */
    public static JsonNumber createJsonNumber(JsonParserBase parser, double value) {
        return JsonNumber.create(value, Objects.requireNonNull(parser));
    }

    /**
     * Converts a decimal value with a built-in parser's expansion budget and exception context.
     *
     * @param parser parser reading the value
     * @param value decimal value
     * @return big integer value
     * @throws JsonException if the conversion exceeds either expansion budget
     */
    public static BigInteger toBigInteger(JsonParserBase parser, BigDecimal value) {
        Objects.requireNonNull(parser);
        Objects.requireNonNull(value);
        BigIntegerExpansionBudget budget = BigIntegerExpansionBudget.requiresExpansion(value)
                ? parser.bigIntegerExpansionBudget()
                : BigIntegerExpansionBudget.noAggregate();
        return budget.toBigInteger(value, parser);
    }
}
