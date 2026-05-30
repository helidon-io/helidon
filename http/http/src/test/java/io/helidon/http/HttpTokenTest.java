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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpTokenTest {

    @Test
    void testIsValidMatchesValidateForValidTokens() {
        assertValid("");
        assertValid("*");
        assertValid("gzip");
        assertValid("x-gzip_1");
    }

    @Test
    void testIsValidMatchesValidateForInvalidTokens() {
        assertInvalid("g zip");
        assertInvalid("gzip;level=1");
        assertInvalid("x/gzip");
        assertInvalid("\u0100");
    }

    @Test
    void testIsValidRange() {
        assertThat(HttpToken.isValid(" gzip ;", 1, 5), is(true));
        assertThat(HttpToken.isValid(" gzip ;", 0, 6), is(false));
    }

    private static void assertValid(String token) {
        assertDoesNotThrow(() -> HttpToken.validate(token));
        assertThat(HttpToken.isValid(token), is(true));
    }

    private static void assertInvalid(String token) {
        assertThrows(IllegalArgumentException.class, () -> HttpToken.validate(token));
        assertThat(HttpToken.isValid(token), is(false));
    }
}
