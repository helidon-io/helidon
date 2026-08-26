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
 * Internal cross-module support for built-in JSON parser implementations.
 */
@Api.Internal
public final class JsonParserSupport {
    private JsonParserSupport() {
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
