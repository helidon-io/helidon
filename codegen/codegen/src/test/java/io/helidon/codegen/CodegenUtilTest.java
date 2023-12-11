/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class CodegenUtilTest {
    @ParameterizedTest
    @CsvSource(textBlock = """
            myMethod MyMethod
            MY_METHOD MY_METHOD
            """, delimiter = ' ')
    void testCapitalize(String source, String expected) {
        String actual = CodegenUtil.capitalize(source);
        assertThat(actual, is(expected));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            myMethod MY_METHOD
            MY_METHOD MY_METHOD
            some-value SOME_VALUE
            methodIA2 METHOD_IA2
            """, delimiter = ' ')
    void testConstantName(String source, String expected) {
        String actual = CodegenUtil.toConstantName(source);
        assertThat(actual, is(expected));
    }
}