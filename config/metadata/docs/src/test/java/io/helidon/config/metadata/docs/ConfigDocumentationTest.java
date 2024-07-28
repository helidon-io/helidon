/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.docs;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigDocumentationTest {
    @Test
    void testTitleFromFileName() {
        String original = "io_helidon_tracing_jaeger_JaegerTracerBuilder.adoc";
        String expected = "JaegerTracerBuilder (tracing.jaeger)";
        String actual = ConfigDocs.titleFromFileName(original);
        assertThat(actual, is(expected));
    }
    @Test
    void testHtmlParagraph() {
        String original = """
                First
                <p>some text
                 <p>some text<p>some text
                Some other text
                """;
        String expected = """
                First
                some text
                some text
                some text
                Some other text
                """;
        String actual = ConfigDocs.translateHtml(original);
        assertThat(actual, is(expected));
    }

    @Test
    void testHtmlUl() {
        String original = """
                First ul: <ul><li>text</li><li>text</li></ul>
                Second ul:
                <ul>
                  <li>text</li>
                  <li>text</li>
                 </ul>
                """;
        String expected = """
                First ul:
                
                - text
                - text
                
                Second ul:
                
                - text
                - text
                                
                """;
        String actual = ConfigDocs.translateHtml(original);
        assertThat(actual, is(expected));
    }

    @Test
    void testAtValue() {
        String original = """
                Some value: {@value #DEFAULT_BASE_SCOPE}
                Some value: {@value SomeType#SOME_CONSTANT}
                """;
        String expected = """
                Some value: `DEFAULT_BASE_SCOPE`
                Some value: `SomeType#SOME_CONSTANT`
                """;
        String actual = ConfigDocs.translateHtml(original);
        assertThat(actual, is(expected));
    }
}