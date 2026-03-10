/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link JavadocWriter}.
 */
class JavadocWriterTest {

    @Test
    void testLink() {
        var actual = html("A link to {@link com.acme.Stuff}.");
        assertThat(actual, is("A link to <code>com.acme.Stuff</code>."));
    }

    @Test
    void testLinkWithLabel() {
        var actual = html("A link to {@link com.acme.Stuff holder class}.");
        assertThat(actual, is("A link to <code>com.acme.Stuff holder class</code>."));
    }

    @Test
    void testLinkPlain() {
        var actual = html("A link to {@linkplain com.acme.Stuff}.");
        assertThat(actual, is("A link to <code>com.acme.Stuff</code>."));
    }

    @Test
    void testLinkPlainWithLabel() {
        var actual = html("A link to {@linkplain com.acme.Stuff holder class}.");
        assertThat(actual, is("A link to <code>com.acme.Stuff holder class</code>."));
    }

    @Test
    void testLiteral() {
        var actual = html("A literal: {@literal <>'\"&}.");
        assertThat(actual, is("A literal: &lt;&gt;'\"&amp;."));
    }

    @Test
    void testInlineCode() {
        var actual = html("Inline code: {@code <>'\"&}.");
        assertThat(actual, is("Inline code: <code>&lt;&gt;'\"&amp;</code>."));
    }

    @Test
    void testSummary() {
        var actual = html("A class with summary.\n {@summary The summary}");
        assertThat(actual, is("A class with summary.<summary>The summary</summary>"));
    }

    @Test
    void testValue() {
        var actual = html("Default value is {@value #DEFAULT_VALUE}.");
        assertThat(actual, is("Default value is <code>#DEFAULT_VALUE</code>."));
    }

    @Test
    void testQualifiedValue() {
        var actual = html("Default value is {@value com.acme.Holder#DEFAULT_VALUE}.");
        assertThat(actual, is("Default value is <code>com.acme.Holder#DEFAULT_VALUE</code>."));
    }

    @Test
    void testInheritDoc() {
        var actual = html("""
                {@inheritDoc}
                 And more...
                 @return break
             """);
        assertThat(actual, is("And more..."));
    }

    @Test
    void testEscapeNamedEntity() {
        var actual = html("Start &apos;s end.");
        assertThat(actual, is("Start &apos;s end."));
    }

    @Test
    void testDecimalEntity() {
        var actual = html("Start &#123; end");
        assertThat(actual, is("Start &#123; end"));
    }

    @Test
    void testHexEntity() {
        var actual = html("Start &#x0FA; end");
        assertThat(actual, is("Start &#x0FA; end"));
    }

    static String html(String javadoc) {
        var reader = JavadocReader.create(javadoc);
        var document = reader.read();
        var buf = new StringBuilder();
        var writer = JavadocWriter.create(buf);
        writer.write(document.fullBody());
        return buf.toString();
    }
}
