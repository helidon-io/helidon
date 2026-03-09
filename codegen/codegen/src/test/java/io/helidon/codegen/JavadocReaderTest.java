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
package io.helidon.codegen;

import java.util.List;
import java.util.Map;

import io.helidon.codegen.JavadocReader.BlockTagImpl;
import io.helidon.codegen.JavadocReader.CdataImpl;
import io.helidon.codegen.JavadocReader.CommentImpl;
import io.helidon.codegen.JavadocReader.DoctypeImpl;
import io.helidon.codegen.JavadocReader.EltCloseImpl;
import io.helidon.codegen.JavadocReader.EltStartImpl;
import io.helidon.codegen.JavadocReader.EscapeImpl;
import io.helidon.codegen.JavadocReader.InlineTagImpl;
import io.helidon.codegen.JavadocReader.TextImpl;
import io.helidon.codegen.JavadocTree.AttrValue;
import io.helidon.codegen.JavadocTree.Document;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.helidon.codegen.JavadocTree.AttrValue.EMPTY;
import static io.helidon.codegen.JavadocTree.AttrValue.Kind.DOUBLE;
import static io.helidon.codegen.JavadocTree.AttrValue.Kind.SINGLE;
import static io.helidon.codegen.JavadocTree.AttrValue.Kind.UNQUOTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class JavadocReaderTest {

    @Test
    void testEmpty() {
        var document = read("");
        assertThat(document.firstSentence(), is(empty()));
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
    }

    @Test
    void testVoidElement() {
        var document = read("<br>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("br", false, Map.of())));
    }

    @Test
    void testSelfClosedVoidElement() {
        var document = read("<br/>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("br", true, Map.of())));
    }

    @Test
    void testElement() {
        var document = read("<div></div>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", false, Map.of()),
                new EltCloseImpl("div")));
    }

    @Test
    void testElements() {
        var document = read("<div></div><p></p>");
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", false, Map.of()),
                new EltCloseImpl("div")));
        assertThat(document.body(), contains(
                new EltStartImpl("p", false, Map.of()),
                new EltCloseImpl("p")));
        assertThat(document.blockTags(), is(empty()));
    }

    @Test
    void testNestedElements() {
        var document = read("<div><table></table></div><p></p>");
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", false, Map.of()),
                new EltStartImpl("table", false, Map.of()),
                new EltCloseImpl("table"),
                new EltCloseImpl("div")));
        assertThat(document.body(), contains(
                new EltStartImpl("p", false, Map.of()),
                new EltCloseImpl("p")));
        assertThat(document.blockTags(), is(empty()));
    }

    @Test
    void testText() {
        var document = read("some text");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new TextImpl("some text")));
    }

    @Test
    void testElementText() {
        var document = read("<p>='\"</p>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("p", false, Map.of()),
                new TextImpl("='\""),
                new EltCloseImpl("p")));
    }

    @Test
    void testUnquotedAttribute() {
        var document = read("<div foo=bar>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", false, Map.of("foo", new AttrValue("bar", UNQUOTED)))));
    }

    @Test
    void testBooleanAttributes() {
        var document = read("<div foo bar>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", false, Map.of(
                        "foo", EMPTY,
                        "bar", EMPTY))));
    }

    @Test
    void testDoubleQuotedAttribute() {
        var document = read("<div foo=\"bar\">");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", false, Map.of("foo", new AttrValue("bar", DOUBLE)))));
    }

    @Test
    void testSingleQuotedAttribute() {
        var document = read("<div foo='bar'>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", false, Map.of("foo", new AttrValue("bar", SINGLE)))));
    }

    @Test
    void testDoctype() {
        var document = read("<!DOCTYPE html SYSTEM \"about:legacy-compat\">");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new DoctypeImpl("html SYSTEM \"about:legacy-compat\"")));
    }

    @Test
    void testCdata() {
        var document = read("""
                <![CDATA[
                foo
                bar
                ]]>
                """);
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new CdataImpl("\nfoo\nbar\n")));
    }

    @Test
    void testComment() {
        var document = read("""
                <!--
                foo
                bar
                -->
                """);
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new CommentImpl("\nfoo\nbar\n")));
    }

    @Test
    void testMixedAttributes() {
        var document = read("<div attr1 attr2=\"value2\" attr3='value3'>some-text</div>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", false, Map.of(
                        "attr1", EMPTY,
                        "attr2", new AttrValue("value2", DOUBLE),
                        "attr3", new AttrValue("value3", SINGLE))),
                new TextImpl("some-text"),
                new EltCloseImpl("div")));
    }

    @Test
    void testBooleanAttribute() {
        var document = read("<div attr>some-text</div>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", false, Map.of("attr", EMPTY)),
                new TextImpl("some-text"),
                new EltCloseImpl("div")));
    }

    @Test
    void testSelfCloseBooleanAttribute() {
        var document = read("<div attr/>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", true, Map.of("attr", EMPTY))));
    }

    @Test
    void testVisit() {
        var document = read("<p><b>some-text</b></p>");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        var sb = new StringBuilder();
        for (var node : document.firstSentence()) {
            switch (node) {
                case JavadocTree.EltStart n -> {
                    sb.append("<");
                    if (n.selfClosing()) {
                        sb.append("/");
                    }
                    sb.append(n.name());
                    sb.append(">");
                }
                case JavadocTree.Text n -> sb.append(n.value());
                case JavadocTree.EltClose n -> sb.append("</").append(n.name()).append(">");
                default -> {
                }
            }
        }
        assertThat(sb.toString(), is("<p><b>some-text</b></p>"));
    }

    @Test
    void testUnclosed() {
        var document = read("<div><p>some-text</div>");
        assertThat(document.firstSentence(), contains(
                new EltStartImpl("div", false, Map.of())));
        assertThat(document.body(), contains(
                new EltStartImpl("p", false, Map.of()),
                new TextImpl("some-text"),
                new EltCloseImpl("div")));
        assertThat(document.blockTags(), is(empty()));
    }

    @Test
    void testInlineTag() {
        var document = read("{@code}");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new InlineTagImpl("code", "")));
    }

    @Test
    void testInlineTagBody() {
        var document = read("{@code foo}");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new InlineTagImpl("code", "foo")));
    }

    @Test
    void testNestedInlineTag() {
        var document = read("{@code {@code foo}}");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new InlineTagImpl("code", "{@code foo}")));
    }

    @Test
    void testInlineWithNestedCurly() {
        var document = read("{@code {}}");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new InlineTagImpl("code", "{}")));
    }

    @Test
    void testInlineLeadingCurly() {
        var document = read("{{@code {}}");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new TextImpl("{"),
                new InlineTagImpl("code", "{}")));
    }

    @Test
    void testInlineTrailingCurly() {
        var document = read("{@code {}}}");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new InlineTagImpl("code", "{}"),
                new TextImpl("}")));
    }

    @Test
    void testInlineLink() {
        var document = read("{@link StuffMakerImpl#makeStuff() my-link}");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new InlineTagImpl("link", "StuffMakerImpl#makeStuff() my-link")));
    }

    @Test
    void testBlockTagBody() {
        var document = read("@param test\n");
        assertThat(document.firstSentence(), is(empty()));
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), contains(
                new BlockTagImpl("param", List.of(new TextImpl("test")))));
    }

    @Test
    void testBlockTagBodyWithLeadingSpaces() {
        var document = read("@param   test\n");
        assertThat(document.firstSentence(), is(empty()));
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), contains(
                new BlockTagImpl("param", List.of(new TextImpl("test")))));
    }

    @Test
    void testBlockTagWithoutBody() {
        var document = read("@param");
        assertThat(document.firstSentence(), is(empty()));
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), contains(
                new BlockTagImpl("param", List.of())));
    }

    @Test
    void testTwoBlockTags() {
        var document = read("@param p1 param1\n@param p2 param2");
        assertThat(document.firstSentence(), is(empty()));
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), contains(
                new BlockTagImpl("param", List.of(new TextImpl("p1 param1"))),
                new BlockTagImpl("param", List.of(new TextImpl("p2 param2")))));
    }

    @Test
    void testBlockTagWithNestedInlineTags() {
        var document = read("@deprecated use {@link com.acme.Foo} instead");
        assertThat(document.firstSentence(), is(empty()));
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), contains(
                new BlockTagImpl("deprecated", List.of(
                        new TextImpl("use "),
                        new InlineTagImpl("link", "com.acme.Foo"),
                        new TextImpl(" instead")))));
    }

    @Test
    void testSingleAt() {
        var document = read("@");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new TextImpl("@")));
    }

    @Test
    void testSingleCurlyAt() {
        var document = read("{@");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), contains(
                new TextImpl("{@")));
    }

    @Test
    void testEscapedBlockTag() {
        var document = read("@@param");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), Matchers.contains(
                new EscapeImpl("@"),
                new TextImpl("param")));
    }

    @Test
    void testTextWithEscapedBlockTag() {
        var document = read("some text\n @@param");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), Matchers.contains(
                new TextImpl("some text\n "),
                new EscapeImpl("@"),
                new TextImpl("param")));
    }

    @Test
    void testEscapedInlineTag() {
        var document = read("{@@code}");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), Matchers.contains(
                new TextImpl("{"),
                new EscapeImpl("@"),
                new TextImpl("code}")));
    }

    @Test
    void testTextWithEscapedInlineTag() {
        var document = read("some text {@@code}");
        assertThat(document.body(), is(empty()));
        assertThat(document.blockTags(), is(empty()));
        assertThat(document.firstSentence(), Matchers.contains(
                new TextImpl("some text {"),
                new EscapeImpl("@"),
                new TextImpl("code}")));
    }

    @Test
    void testFirstSentence() {
        var document = read("First sentence. Body sentence.");
        assertThat(document.firstSentence(), contains(
                new TextImpl("First sentence.")));
        assertThat(document.body(), contains(
                new TextImpl("Body sentence.")));
        assertThat(document.blockTags(), is(empty()));
    }

    @Test
    void testFirstSentenceWithDottedCode() {
        var document = read("First {@code 1.2.3} sentence. Body sentence.");
        assertThat(document.firstSentence(), contains(
                new TextImpl("First "),
                new InlineTagImpl("code", "1.2.3"),
                new TextImpl(" sentence.")));
        assertThat(document.body(), contains(
                new TextImpl("Body sentence.")));
        assertThat(document.blockTags(), is(empty()));
    }

    @Test
    void testFirstSentenceWithDottedHtmlCode() {
        var document = read("First <code>1.2.3</code> sentence. Body sentence.");
        assertThat(document.firstSentence(), contains(
                new TextImpl("First "),
                new EltStartImpl("code", false, Map.of()),
                new TextImpl("1.2.3"),
                new EltCloseImpl("code"),
                new TextImpl(" sentence.")));
        assertThat(document.body(), contains(
                new TextImpl("Body sentence.")));
        assertThat(document.blockTags(), is(empty()));
    }

    @Test
    void testFirstSentenceWithLink() {
        var document = read("Wrapper for {@link com.acme.Service} class. Body sentence.");
        assertThat(document.firstSentence(), contains(
                new TextImpl("Wrapper for "),
                new InlineTagImpl("link", "com.acme.Service"),
                new TextImpl(" class.")));
        assertThat(document.body(), contains(
                new TextImpl("Body sentence.")));
        assertThat(document.blockTags(), is(empty()));
    }

    @Test
    void testFirstSentenceWithHtmlLink() {
        var document = read("See <a href=\"https://acme.com\">acme.com</a>. Body sentence.");
        assertThat(document.firstSentence(), contains(
                new TextImpl("See "),
                new EltStartImpl("a", false, Map.of("href", new AttrValue("https://acme.com", DOUBLE))),
                new TextImpl("acme.com"),
                new EltCloseImpl("a"),
                new TextImpl(".")));
        assertThat(document.body(), contains(
                new TextImpl("Body sentence.")));
        assertThat(document.blockTags(), is(empty()));
    }

    @Test
    void testSummaryBreak() {
        var document = read("A class with summary.\n {@summary The summary}");
        assertThat(document.firstSentence(), contains(
                new TextImpl("A class with summary.")));
        assertThat(document.body(), contains(
                new InlineTagImpl("summary", "The summary")));
        assertThat(document.blockTags(), is(empty()));
    }

    private static Document read(String input) {
        var reader = JavadocReader.create(input);
        return reader.read();
    }
}
