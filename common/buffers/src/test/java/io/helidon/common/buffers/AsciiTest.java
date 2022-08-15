/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsciiTest {
    @Test
    void testIsLowerCaseOne() {
        assertFalse(Ascii.isLowerCase('{'));
    }

    @Test
    void testIsLowerCaseReturningTrue() {
        assertTrue(Ascii.isLowerCase('o'));
    }

    @Test
    void testIsLowerCaseTwo() {
        assertFalse(Ascii.isLowerCase('\"'));
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

        assertEquals("uhho^s} b'jdwtym", Ascii.toLowerCase(stringBuilder));
    }

    @Test
    void testToLowerCaseTakingCharSequenceTwo() {
        assertEquals("uhho^s} b'jdwtym", Ascii.toLowerCase((CharSequence) "uHHO^S} b'jDwTYM"));
    }

    @Test
    void testToLowerCaseTakingString() {
        assertEquals("", Ascii.toLowerCase(""));
    }

    @Test
    void testIsUpperCaseOne() {
        assertFalse(Ascii.isUpperCase('{'));
    }

    @Test
    void testIsUpperCaseReturningTrue() {
        assertTrue(Ascii.isUpperCase('O'));
    }

    @Test
    void testIsUpperCaseTwo() {
        assertFalse(Ascii.isUpperCase('\"'));
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

        assertEquals("UHHO^S} B'JDWTYM", Ascii.toUpperCase(stringBuilder));
    }

    @Test
    void testToUpperCaseTakingCharSequenceTwo() {
        assertEquals("UHHO^S} B'JDWTYM", Ascii.toUpperCase((CharSequence) "uHHO^S} b'jDwTYM"));
    }

    @Test
    void testToUpperCaseTakingString() {
        assertEquals("UHHO^S} B'JDWTYM", Ascii.toUpperCase("uHHO^S} b'jDwTYM"));
    }
}