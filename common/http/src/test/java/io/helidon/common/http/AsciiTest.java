/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit tests for class {@link Ascii}.
 *
 * @see Ascii
 */
public class AsciiTest {

    @Test
    public void testIsLowerCaseOne() {
        assertThat(Ascii.isLowerCase('{'), is(false));
    }


    @Test
    public void testIsLowerCaseReturningTrue() {
        assertThat(Ascii.isLowerCase('o'), is(true));
    }


    @Test
    public void testIsLowerCaseTwo() {
        assertThat(Ascii.isLowerCase('\"'), is(false));
    }


    @Test
    public void testToLowerCaseTakingCharSequenceOne() {
        StringBuilder stringBuilder = new StringBuilder("uhho^s} b'jdwtym");

        assertThat(Ascii.toLowerCase(stringBuilder), is("uhho^s} b'jdwtym"));
    }


    @Test
    public void testToLowerCaseTakingCharSequenceTwo() {
        assertThat(Ascii.toLowerCase((CharSequence) "uHHO^S} b'jDwTYM"), is("uhho^s} b'jdwtym"));
    }


    @Test
    public void testToLowerCaseTakingString() {
        assertThat(Ascii.toLowerCase(""), is(""));
    }

}
