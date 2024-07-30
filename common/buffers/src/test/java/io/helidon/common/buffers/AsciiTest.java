/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class AsciiTest {
    @Test
    void testIsLowerCaseOne() {
        assertThat(Ascii.isLowerCase('{'), is(false));
    }

    @Test
    void testIsLowerCaseReturningTrue() {
        assertThat(Ascii.isLowerCase('o'), is(true));
    }

    @Test
    void testIsLowerCaseTwo() {
        assertThat(Ascii.isLowerCase('\"'), is(false));
    }

    @Test
    void testToLowerCaseChar() {
        assertThat(Ascii.toLowerCase('A'), is('a'));
        assertThat(Ascii.toLowerCase('Z'), is('z'));
        assertThat(Ascii.toLowerCase('a'), is('a'));
        assertThat(Ascii.toLowerCase('z'), is('z'));
        assertThat(Ascii.toLowerCase('5'), is('5'));
    }

    @Test
    void testToLowerCaseTakingCharSequenceOne() {
        StringBuilder stringBuilder = new StringBuilder("uhho^s} b'jdwtym");

        assertThat(Ascii.toLowerCase(stringBuilder), is("uhho^s} b'jdwtym"));
    }

    @Test
    void testToLowerCaseTakingCharSequenceTwo() {
        assertThat(Ascii.toLowerCase((CharSequence) "uHHO^S} b'jDwTYM"), is("uhho^s} b'jdwtym"));
    }

    @Test
    void testToLowerCaseTakingString() {
        assertThat(Ascii.toLowerCase(""), is(""));
    }

    @Test
    void testIsUpperCaseOne() {
        assertThat(Ascii.isUpperCase('{'), is(false));
    }

    @Test
    void testIsUpperCaseReturningTrue() {
        assertThat(Ascii.isUpperCase('O'), is(true));
    }

    @Test
    void testIsUpperCaseTwo() {
        assertThat(Ascii.isUpperCase('\"'), is(false));
    }

    @Test
    void testToUpperCaseChar() {
        assertThat(Ascii.toUpperCase('a'), is('A'));
        assertThat(Ascii.toUpperCase('z'), is('Z'));
        assertThat(Ascii.toUpperCase('A'), is('A'));
        assertThat(Ascii.toUpperCase('Z'), is('Z'));
        assertThat(Ascii.toUpperCase('5'), is('5'));
    }

    @Test
    void testToUpperCaseTakingCharSequenceOne() {
        StringBuilder stringBuilder = new StringBuilder("UhHO^S} B'JDWTYM");

        assertThat(Ascii.toUpperCase(stringBuilder), is("UHHO^S} B'JDWTYM"));
    }

    @Test
    void testToUpperCaseTakingCharSequenceTwo() {
        assertThat(Ascii.toUpperCase((CharSequence) "uHHO^S} b'jDwTYM"), is("UHHO^S} B'JDWTYM"));
    }

    @Test
    void testToUpperCaseTakingString() {
        assertThat(Ascii.toUpperCase("uHHO^S} b'jDwTYM"), is("UHHO^S} B'JDWTYM"));
    }
}