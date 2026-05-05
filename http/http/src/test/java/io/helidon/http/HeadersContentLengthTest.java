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

package io.helidon.http;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeadersContentLengthTest {

    @Test
    void acceptsSingleValueWithOws() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONTENT_LENGTH, "\t5 ");

        assertThat(headers.contentLength().orElseThrow(), is(5L));
    }

    @Test
    void rejectsRepeatedValues() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONTENT_LENGTH, "5")
                .add(HeaderNames.CONTENT_LENGTH, "5");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, headers::contentLength);
        assertThat(exception.getMessage(), is("Content-Length header must have exactly one value."));
    }

    @Test
    void rejectsCommaSeparatedValues() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONTENT_LENGTH, "\t5 , 5\t");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, headers::contentLength);
        assertThat(exception.getMessage(), is("Content-Length header must have exactly one value."));
    }

    @Test
    void rejectsConflictingValues() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONTENT_LENGTH, "5")
                .add(HeaderNames.CONTENT_LENGTH, "4");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, headers::contentLength);
        assertThat(exception.getMessage(), is("Content-Length header must have exactly one value."));
    }

    @Test
    void rejectsSignedValues() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONTENT_LENGTH, "+5");

        assertThrows(NumberFormatException.class, headers::contentLength);
    }

    @Test
    void rejectsOverflowValues() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONTENT_LENGTH, "9223372036854775808");

        assertThrows(NumberFormatException.class, headers::contentLength);
    }

    @Test
    void rejectsControlCharactersAroundDigits() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONTENT_LENGTH, "5\u000b");

        assertThrows(NumberFormatException.class, headers::contentLength);
    }
}
