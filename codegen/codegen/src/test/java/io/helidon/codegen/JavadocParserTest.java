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

import java.util.ArrayList;
import java.util.List;

import io.helidon.codegen.JavadocParser.Event;
import io.helidon.codegen.JavadocReader.CdataImpl;
import io.helidon.codegen.JavadocReader.CommentImpl;
import io.helidon.codegen.JavadocReader.DoctypeImpl;
import io.helidon.codegen.JavadocReader.EltCloseImpl;
import io.helidon.codegen.JavadocReader.EscapeImpl;
import io.helidon.codegen.JavadocReader.TextImpl;
import io.helidon.codegen.JavadocTree.AttrValue;

import org.junit.jupiter.api.Test;

import static io.helidon.codegen.JavadocParser.Event.AttrName;
import static io.helidon.codegen.JavadocParser.Event.BlockTag;
import static io.helidon.codegen.JavadocParser.Event.EltStart;
import static io.helidon.codegen.JavadocParser.Event.InlineTag;
import static io.helidon.codegen.JavadocParser.Event.SELF_CLOSE;
import static io.helidon.codegen.JavadocParser.Event.STOPPER;
import static io.helidon.codegen.JavadocTree.AttrValue.Kind.DOUBLE;
import static io.helidon.codegen.JavadocTree.AttrValue.Kind.SINGLE;
import static io.helidon.codegen.JavadocTree.AttrValue.Kind.UNQUOTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link JavadocParser}.
 */
class JavadocParserTest {

    @Test
    void testEmpty() {
        var events = parse("");
        assertThat(events, is(empty()));
    }

    @Test
    void testVoidElement() {
        var events = parse("<br>");
        assertThat(events, contains(
                new EltStart("br"),
                STOPPER));
    }

    @Test
    void testSelfClosedVoidElement() {
        var events = parse("<br/>");
        assertThat(events, contains(
                new EltStart("br"),
                SELF_CLOSE));
    }

    @Test
    void testSelfClosedUnquotedAttribute() {
        var events = parse("<div foo=bar/>");
        assertThat(events, contains(
                new EltStart("div"),
                new AttrName("foo"),
                new AttrValue("bar", UNQUOTED),
                SELF_CLOSE));
    }

    @Test
    void testElement() {
        var events = parse("<div></div>");
        assertThat(events, contains(
                new EltStart("div"),
                STOPPER,
                new EltCloseImpl("div")));
    }

    @Test
    void testText() {
        var events = parse("  some text\n  \n");
        assertThat(events, contains(new TextImpl("some text")));
    }

    @Test
    void testElementText() {
        var events = parse("<p>='\"</p>");
        assertThat(events, contains(
                new EltStart("p"),
                STOPPER,
                new TextImpl("='\""),
                new EltCloseImpl("p")));
    }

    @Test
    void testUnquotedAttribute() {
        var events = parse("<div foo=bar>");
        assertThat(events, contains(
                new EltStart("div"),
                new AttrName("foo"),
                new AttrValue("bar", UNQUOTED),
                STOPPER));
    }

    @Test
    void testUnquotedBooleanAttribute() {
        var events = parse("<div @foo @bar/>");
        assertThat(events, contains(
                new EltStart("div"),
                new AttrName("@foo"),
                new AttrName("@bar"),
                SELF_CLOSE));
    }

    @Test
    void testDoubleQuotedAttribute() {
        var events = parse("<div foo=\"bar\">");
        assertThat(events, contains(
                new EltStart("div"),
                new AttrName("foo"),
                new AttrValue("bar", DOUBLE),
                STOPPER));
    }

    @Test
    void testSingleQuotedAttribute() {
        var events = parse("<div foo='bar'>");
        assertThat(events, contains(
                new EltStart("div"),
                new AttrName("foo"),
                new AttrValue("bar", SINGLE),
                STOPPER));
    }

    @Test
    void testDoctype() {
        var events = parse("<!DOCTYPE html SYSTEM \"about:legacy-compat\">");
        assertThat(events, contains(new DoctypeImpl("html SYSTEM \"about:legacy-compat\"")));
    }

    @Test
    void testCdata() {
        var events = parse("""
                <![CDATA[
                foo
                bar
                ]]>
                """);
        assertThat(events, contains(new CdataImpl("\nfoo\nbar\n")));
    }

    @Test
    void testComment() {
        var events = parse("""
                <!--
                foo
                bar
                -->
                """);
        assertThat(events, contains(new CommentImpl("\nfoo\nbar\n")));
    }

    @Test
    void testHtmlCode() {
        var events = parse("First <code>1.2.3</code> sentence. Body sentence.");
        assertThat(events, contains(
                new TextImpl("First "),
                new EltStart("code"),
                STOPPER,
                new TextImpl("1.2.3"),
                new EltCloseImpl("code"),
                new TextImpl(" sentence. Body sentence.")));
    }

    @Test
    void testInlineTag() {
        var events = parse("{@code}");
        assertThat(events, contains(
                new InlineTag("code"),
                STOPPER));
    }

    @Test
    void testInlineTagBody() {
        var events = parse("{@code foo}");
        assertThat(events, contains(
                new InlineTag("code"),
                new TextImpl("foo"),
                STOPPER));
    }

    @Test
    void testInlineTagBodyWithLeadingSpaces() {
        var events = parse("{@code   foo  }");
        assertThat(events, contains(
                new InlineTag("code"),
                new TextImpl("  foo  "),
                STOPPER));
    }

    @Test
    void testLinkTagWithSpaces() {
        var events = parse("{@link   Object  }");
        assertThat(events, contains(
                new InlineTag("link"),
                new TextImpl("Object"),
                STOPPER));
    }

    @Test
    void testLinkPlainTagWithSpaces() {
        var events = parse("{@linkplain   Object  }");
        assertThat(events, contains(
                new InlineTag("linkplain"),
                new TextImpl("Object"),
                STOPPER));
    }

    @Test
    void testValueTagWithSpaces() {
        var events = parse("{@value   Integer#MAX_VALUE  }");
        assertThat(events, contains(
                new InlineTag("value"),
                new TextImpl("Integer#MAX_VALUE"),
                STOPPER));
    }

    @Test
    void testNestedInlineTag() {
        var events = parse("{@code {@code foo}}");
        assertThat(events, contains(
                new InlineTag("code"),
                new TextImpl("{@code foo}"),
                STOPPER));
    }

    @Test
    void testInlineWithNestedCurly() {
        var events = parse("{@code {}}");
        assertThat(events, contains(
                new InlineTag("code"),
                new TextImpl("{}"),
                STOPPER));
    }

    @Test
    void testInlineLeadingCurly() {
        var events = parse("{{@code {}}");
        assertThat(events, contains(
                new TextImpl("{"),
                new InlineTag("code"),
                new TextImpl("{}"),
                STOPPER));
    }

    @Test
    void testInlineTrailingCurly() {
        var events = parse("{@code {}}}");
        assertThat(events, contains(
                new InlineTag("code"),
                new TextImpl("{}"),
                STOPPER,
                new TextImpl("}")));
    }

    @Test
    void testInlineLink() {
        var events = parse("{@link StuffMakerImpl#makeStuff() my-link}}");
        assertThat(events, contains(
                new InlineTag("link"),
                new TextImpl("StuffMakerImpl#makeStuff() my-link"),
                STOPPER,
                new TextImpl("}")));
    }

    @Test
    void testBlockTagBody() {
        var events = parse("@param test\n");
        assertThat(events, contains(
                new BlockTag("param"),
                new TextImpl("test")));
    }

    @Test
    void testBlockTagBodyWithSpaces() {
        var events = parse("@param\n   test\n");
        assertThat(events, contains(
                new BlockTag("param"), new TextImpl("test")));
    }

    @Test
    void testBlockTagWithoutBody() {
        var events = parse("@param");
        assertThat(events, contains(
                new BlockTag("param")));
    }

    @Test
    void testTwoBlockTags() {
        var events = parse("@param p1 param1\n@param p2 param2\n");
        assertThat(events, contains(
                new BlockTag("param"), new TextImpl("p1 param1"),
                new BlockTag("param"), new TextImpl("p2 param2")));
    }

    @Test
    void testBlockTagWithNestedInlineTags() {
        var events = parse("@deprecated use {@link com.acme.Foo} instead");
        assertThat(events, contains(
                new BlockTag("deprecated"),
                new TextImpl("use "),
                new InlineTag("link"),
                new TextImpl("com.acme.Foo"),
                STOPPER,
                new TextImpl(" instead")));
    }

    @Test
    void testSingleAt() {
        var events = parse("@");
        assertThat(events, contains(
                new TextImpl("@")));
    }

    @Test
    void testSingleCurlyAt() {
        var events = parse("{@");
        assertThat(events, contains(
                new TextImpl("{@")));
    }

    @Test
    void testEscapedBlockTag() {
        var events = parse("@@param");
        assertThat(events, contains(
                new EscapeImpl("@"),
                new TextImpl("param")));
    }

    @Test
    void testTextWithEscapedBlockTag() {
        var events = parse("some text\n @@param");
        assertThat(events, contains(
                new TextImpl("some text\n "),
                new EscapeImpl("@"),
                new TextImpl("param")));
    }

    @Test
    void testEscapedInlineTag() {
        var events = parse("{@@code}");
        assertThat(events, contains(
                new TextImpl("{"),
                new EscapeImpl("@"),
                new TextImpl("code}")));
    }

    @Test
    void testTextWithEscapedInlineTag() {
        var events = parse("some text {@@code}");
        assertThat(events, contains(
                new TextImpl("some text {"),
                new EscapeImpl("@"),
                new TextImpl("code}")));
    }

    @Test
    void testEscapeSlash() {
        var events = parse("@/");
        assertThat(events, contains(
                new EscapeImpl("/")));
    }

    @Test
    void testEscapeSlashWithLeadingSpaces() {
        var events = parse("     @/");
        assertThat(events, contains(
                new EscapeImpl("/")));
    }

    @Test
    void testEscapeStar() {
        var events = parse("@*");
        assertThat(events, contains(
                new EscapeImpl("*")));
    }

    @Test
    void testEscapeStarWithLeadingSpaces() {
        var events = parse("     @*");
        assertThat(events, contains(
                new EscapeImpl("*")));
    }

    @Test
    void testLeadingSpaces() {
        var events = parse("     Word");
        assertThat(events, contains(
                new TextImpl("Word")));
    }

    static List<Event> parse(String str) {
        var parser = new JavadocParser(str);
        var events = new ArrayList<Event>();
        while (parser.hasNext()) {
            var event = parser.next();
            events.add(event);
        }
        return events;
    }
}