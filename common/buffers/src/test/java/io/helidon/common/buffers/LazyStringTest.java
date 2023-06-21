/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.common.buffers;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class LazyStringTest {
    private static String INVALID_VALUE = "invalid-value";

    @ParameterizedTest
    @MethodSource("owsData")
    void testOwsHandling(OwsTestData data) {
        assertThat(data.string().stripOws(), is(data.expected()));
    }

    @ParameterizedTest
    @MethodSource("valuesToValidate")
    void testValidator(String value, boolean validate, boolean expectsValid) {
        LazyString lazyString = new LazyString(value.getBytes(US_ASCII), US_ASCII);
        // Supply custom validator when validate is true that will throw IllegalArgumentException if the value is invalid
        if (validate) {
            lazyString.setValidator(valueToValidate -> {
                if (valueToValidate.contains(INVALID_VALUE)) {
                    throw new IllegalArgumentException("Found an invalid value");
                }
            });
        }
        // If there is no validator or validator does not encounter a problem, expect the value retrieval to succeed. Otherwise,
        // expect that an IllegalArgumentException will be thrown.
        if (expectsValid) {
            assertThat(lazyString.stripOws(), is(value));
            assertThat(lazyString.toString(), is(value));
        } else {
            Assertions.assertThrows(IllegalArgumentException.class, () -> lazyString.stripOws());
            Assertions.assertThrows(IllegalArgumentException.class, () -> lazyString.toString());
        }
    }

    private static Stream<OwsTestData> owsData() {
        return Stream.of(
                new OwsTestData(new LazyString("some-value".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString(" some-value".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("\tsome-value".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("  some-value".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("\t\tsome-value".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString(" \tsome-value".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("some-value ".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("some-value\t".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("some-value  ".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("some-value\t\t".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("some-value \t".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString(" some-value ".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("\tsome-value\t".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("  some-value  ".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString("\t\tsome-value\t\t".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString(" \tsome-value\t ".getBytes(US_ASCII), US_ASCII), "some-value"),
                new OwsTestData(new LazyString(" \t\t ".getBytes(US_ASCII), US_ASCII), ""),
                new OwsTestData(new LazyString(" \t\r\t ".getBytes(US_ASCII), US_ASCII), "\r")
        );
    }

    private static Stream<Arguments> valuesToValidate() {
        return Stream.of(
                // Invalid value with validator set, expects that value retrieval will fail
                arguments("first-" + INVALID_VALUE, true, false),
                arguments(INVALID_VALUE + "-second", true, false),
                // Valid value with validator set, expects that value retrieval will succeed
                arguments("valid-third", true, true),
                arguments("fourth-valid", true, true),
                // Valid or Invalid value with no validator set, expects that value retrieval will succeed
                arguments("valid-fifth", false, true),
                arguments("sixth" + INVALID_VALUE, false, true)
        );
    }

    record OwsTestData(LazyString string, String expected) {
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();

            for (char c : string().toString().toCharArray()) {
                switch (c) {
                case '\t' -> result.append("\\t");
                case '\r' -> result.append("\\r");
                default -> result.append(c);
                }
            }

            return "\"" + result + "\"";
        }
    }
}
