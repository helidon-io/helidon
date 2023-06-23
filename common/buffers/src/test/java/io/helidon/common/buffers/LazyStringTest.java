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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class LazyStringTest {
    @ParameterizedTest
    @MethodSource("owsData")
    void testOwsHandling(OwsTestData data) {
        assertThat(data.string().stripOws(), is(data.expected()));
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
