/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for class {@link Ascii}.
 *
 * @see Ascii
 */
public class AsciiTest {

    @Test
    public void testIsLowerCaseOne() {
        assertFalse(Ascii.isLowerCase('{'));
    }


    @Test
    public void testIsLowerCaseReturningTrue() {
        assertTrue(Ascii.isLowerCase('o'));
    }


    @Test
    public void testIsLowerCaseTwo() {
        assertFalse(Ascii.isLowerCase('\"'));
    }


    @Test
    public void testToLowerCaseTakingCharSequenceOne() {
        StringBuilder stringBuilder = new StringBuilder("uhho^s} b'jdwtym");

        assertEquals("uhho^s} b'jdwtym", Ascii.toLowerCase(stringBuilder));
    }


    @Test
    public void testToLowerCaseTakingCharSequenceTwo() {
        assertEquals("uhho^s} b'jdwtym", Ascii.toLowerCase((CharSequence) "uHHO^S} b'jDwTYM"));
    }


    @Test
    public void testToLowerCaseTakingString() {
        assertEquals("", Ascii.toLowerCase(""));
    }

}
