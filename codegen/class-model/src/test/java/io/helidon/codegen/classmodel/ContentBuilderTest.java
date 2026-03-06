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

package io.helidon.codegen.classmodel;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ContentBuilderTest {
    @Test
    void testAddContentLiteralSimple() {
        var c = new TestContentBuilder();
        c.addContentLiteral("test string");

        assertThat(c.generatedString(), is("    \"test string\""));
    }

    @Test
    void testAddContentLiteralQuotes() {
        var c = new TestContentBuilder();
        c.addContentLiteral("test string with \" quotes");

        assertThat(c.generatedString(), is("    \"test string with \\\" quotes\""));
    }

    @Test
    void testAddContentLiteralTab() {
        var c = new TestContentBuilder();
        c.addContentLiteral("test string with \t tab");

        assertThat(c.generatedString(), is("    \"test string with \\t tab\""));
    }

    @Test
    void testAddContentLiteralNewLine() {
        var c = new TestContentBuilder();
        c.addContentLiteral("test string with\n lines");

        assertThat(c.generatedString(), is("    \"\"\"\ntest string with\n lines\"\"\""));
    }
}
