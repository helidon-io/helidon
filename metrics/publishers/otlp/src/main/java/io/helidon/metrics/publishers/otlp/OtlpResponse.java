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

package io.helidon.metrics.publishers.otlp;

import java.math.BigDecimal;

import io.helidon.common.GenericType;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonDeserializer;

@Json.Entity
record OtlpResponse(PartialSuccess partialSuccess) {
    @Json.Entity
    record PartialSuccess(@Json.Deserializer(Int64Deserializer.class) long rejectedDataPoints, String errorMessage) {
        PartialSuccess {
            errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }

    static final class Int64Deserializer implements JsonDeserializer<Long> {
        // Allow decimal and exponent forms without converting arbitrarily large response numbers.
        private static final int MAX_NUMBER_LENGTH = 128;
        private static final GenericType<Long> TYPE = GenericType.create(Long.class);

        @Override
        public Long deserialize(JsonParser parser) {
            try {
                if (parser.currentByte() == '"') {
                    String value = parser.readString();
                    if (value.length() > MAX_NUMBER_LENGTH) {
                        throw parser.createException("OTLP integer representation exceeds " + MAX_NUMBER_LENGTH + " characters");
                    }
                    return new BigDecimal(value).longValueExact();
                }
                byte current = parser.currentByte();
                if (current == '-' || current >= '0' && current <= '9') {
                    return readNumber(parser).longValueExact();
                }
                throw parser.createException("Expected an OTLP integer as a number or string");
            } catch (NumberFormatException | ArithmeticException e) {
                throw parser.createException("OTLP rejected data point count must be an exact signed 64-bit integer", e);
            }
        }

        @Override
        public Long deserializeNull() {
            return 0L;
        }

        @Override
        public GenericType<Long> type() {
            return TYPE;
        }

        private static BigDecimal readNumber(JsonParser parser) {
            // Check the token before readBigDecimal can allocate and convert an arbitrary-precision coefficient.
            parser.mark();
            try {
                int length = 0;
                byte current = parser.currentByte();
                while (current >= '0' && current <= '9'
                        || current == '-' || current == '+' || current == '.' || current == 'e' || current == 'E') {
                    if (++length > MAX_NUMBER_LENGTH) {
                        throw parser.createException("OTLP integer representation exceeds " + MAX_NUMBER_LENGTH + " characters");
                    }
                    if (!parser.hasNext()) {
                        break;
                    }
                    current = parser.nextToken();
                }
                parser.resetToMark();
            } finally {
                parser.clearMark();
            }
            return parser.readBigDecimal();
        }
    }
}
