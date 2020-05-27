/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.media.multipart;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link MimeParser}.
 */
public class MimeParserTest {

    private static final Logger LOGGER = Logger.getLogger(MimeParser.class.getName());

    @BeforeAll
    public static void before() {
        LOGGER.setLevel(Level.ALL);
    }

    @AfterAll
    public static void after() {
        LOGGER.setLevel(Level.INFO);
    }

    // TODO test mixed with nested boundaries
    @Test
    public void testSkipPreambule() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary
                + "          \t     \t  \t "
                + "\r\n"
                + "Content-Id: part1\n"
                + "\n"
                + "1\n"
                + "--" + boundary + "--").getBytes();

        List<MimePart> parts = parse("boundary", chunk1).parts;
        assertThat(parts.size(), is(equalTo(1)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("1")));
    }

    @Test
    public void testNoPreambule() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary
                + "Content-Id: part1\n"
                + "\n"
                + "1\n"
                + "--" + boundary + "--").getBytes();

        List<MimePart> parts = parse("boundary", chunk1).parts;
        assertThat(parts.size(), is(equalTo(1)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.get("-" + boundary + "Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("1")));
    }

    @Test
    public void testEndMessageEvent() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "1\n"
                + "--" + boundary + "\n"
                + "Content-Id: part2\n"
                + "\n"
                + "2\n"
                + "--" + boundary + "--").getBytes();

        MimeParser.ParserEvent lastEvent = parse(boundary, chunk1).lastEvent;
        assertThat(lastEvent, is(notNullValue()));
        assertThat(lastEvent.type(), is(equalTo(MimeParser.EventType.END_MESSAGE)));
    }

    @Test
    public void testBoundaryWhiteSpace() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "   \n"
                + "Content-Id: part1\n"
                + "\n"
                + "1\n"
                + "--" + boundary + "\n"
                + "Content-Id: part2\n"
                + "\n"
                + "2\n"
                + "--" + boundary + "--   ").getBytes();

        List<MimePart> parts = parse("boundary", chunk1).parts;
        assertThat(parts.size(), is(equalTo(2)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("1")));

        MimePart part2 = parts.get(1);
        assertThat(part2.headers.get("Content-Id"), hasItems("part2"));
        assertThat(part2.content, is(notNullValue()));
        assertThat(new String(part2.content), is(equalTo("2")));
    }

    @Test
    public void testMsg() {
        String boundary = "----=_Part_4_910054940.1065629194743";
        final byte[] chunk1 = concat(("--" + boundary + "\n"
                + "Content-Type: text/xml; charset=UTF-8\n"
                + "Content-Transfer-Encoding: binary\n"
                + "Content-Id: part1\n"
                + "Content-Description:   this is part1\n"
                + "\n"
                + "<foo>bar</foo>\n"
                + "--" + boundary + "\n"
                + "Content-Type: image/jpeg\n"
                + "Content-Transfer-Encoding: binary\n"
                + "Content-Id: part2\n"
                + "\n").getBytes(),
                new byte[]{(byte) 0xff, (byte) 0xd8},
                ("\n--" + boundary + "--").getBytes());

        List<MimePart> parts = parse(boundary, chunk1).parts;
        assertThat(parts.size(), is(equalTo(2)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.get("Content-Type"), hasItems("text/xml; charset=UTF-8"));
        assertThat(part1.headers.get("Content-Transfer-Encoding"), hasItems("binary"));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.headers.get("Content-Description"), hasItems("this is part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("<foo>bar</foo>")));

        MimePart part2 = parts.get(1);
        assertThat(part2.headers.get("Content-Type"), hasItems("image/jpeg"));
        assertThat(part2.headers.get("Content-Transfer-Encoding"), hasItems("binary"));
        assertThat(part2.headers.get("Content-Id"), hasItems("part2"));
        assertThat(part2.content, is(notNullValue()));
        assertThat(part2.content[0], is(equalTo((byte) 0xff)));
        assertThat(part2.content[1], is(equalTo((byte) 0xd8)));
    }

    @Test
    public void testEmptyPart() {
        String boundary = "----=_Part_7_10584188.1123489648993";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Type: text/xml; charset=utf-8\n"
                + "Content-Id: part1\n"
                + "\n"
                + "--" + boundary + "\n"
                + "Content-Type: text/xml\n"
                + "Content-Id: part2\n"
                + "\n"
                + "<foo>bar</foo>\n"
                + "--" + boundary + "--").getBytes();

        List<MimePart> parts = parse(boundary, chunk1).parts;
        assertThat(parts.size(), is(equalTo(2)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.get("Content-Type"), hasItems("text/xml; charset=utf-8"));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(part1.content.length, is(equalTo(0)));

        MimePart part2 = parts.get(1);
        assertThat(part2.headers.get("Content-Type"), hasItems("text/xml"));
        assertThat(part2.headers.get("Content-Id"), hasItems("part2"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part2.content), is(equalTo("<foo>bar</foo>")));
    }

    @Test
    public void testNoHeaders() {
        String boundary = "----=_Part_7_10584188.1123489648993";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "\n"
                + "<foo>bar</foo>\n"
                + "--" + boundary + "\n"
                + "\n"
                + "<bar>foo</bar>\n"
                + "--" + boundary + "--").getBytes();

        List<MimePart> parts = parse(boundary, chunk1).parts;
        assertThat(parts.size(), is(equalTo(2)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(0)));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("<foo>bar</foo>")));

        MimePart part2 = parts.get(1);
        assertThat(part2.headers.size(), is(equalTo(0)));
        assertThat(part2.content, is(notNullValue()));
        assertThat(new String(part2.content), is(equalTo("<bar>foo</bar>")));
    }

    @Test
    public void testNoClosingBoundary() {
        String boundary = "----=_Part_4_910054940.1065629194743";
        final byte[] chunk1 = concat(("--" + boundary + "\n"
                + "Content-Type: text/xml; charset=UTF-8\n"
                + "Content-Id: part1\n"
                + "\n"
                + "<foo>bar</foo>\n"
                + "--" + boundary + "\n"
                + "Content-Type: image/jpeg\n"
                + "Content-Transfer-Encoding: binary\n"
                + "Content-Id: part2\n"
                + "\n").getBytes(),
                new byte[]{(byte) 0xff, (byte) 0xd8});

        MimeParser.ParsingException ex = assertThrows(MimeParser.ParsingException.class,
                () -> parse(boundary, chunk1));
        assertThat(ex.getMessage(), is(equalTo("No closing MIME boundary")));
    }

    @Test
    public void testIntermediateBoundary() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "   \n"
                + "Content-Id: part1\n"
                + "\n"
                + "1\n"
                + "--" + boundary + " \r\n"
                + "Content-Id: part2\n"
                + "\n"
                + "2\n"
                + "--" + boundary + "--   ").getBytes();

        List<MimePart> parts = parse("boundary", chunk1).parts;
        assertThat(parts.size(), is(equalTo(2)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("1")));

        MimePart part2 = parts.get(1);
        assertThat(part2.headers.get("Content-Id"), hasItems("part2"));
        assertThat(part2.content, is(notNullValue()));
        assertThat(new String(part2.content), is(equalTo("2")));
    }

    @Test
    public void testBoundaryInBody() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "1 --" + boundary + " in body\n"
                + "--" + boundary + "\n"
                + "Content-Id: part2\n"
                + "\n"
                + "2 --" + boundary + " in body\n"
                + "--" + boundary + " starts on a new line\n"
                + "--" + boundary + "--         ").getBytes();

        List<MimePart> parts = parse(boundary, chunk1).parts;
        assertThat(parts.size(), is(equalTo(2)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(1)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("1 --" + boundary + " in body")));

        MimePart part2 = parts.get(1);
        assertThat(part2.headers.size(), is(equalTo(1)));
        assertThat(part2.headers.get("Content-Id"), hasItems("part2"));
        assertThat(part2.content, is(notNullValue()));
        assertThat(new String(part2.content), is(equalTo("2 --" + boundary + " in body\n"
                        + "--" + boundary + " starts on a new line")));
    }

    @Test
    public void testInvalidClosingBoundary() {
        String boundary = "----=_Part_4_910054940.1065629194743";
        final byte[] chunk1 = concat(("--" + boundary + "\n"
                + "Content-Type: text/xml; charset=UTF-8\n"
                + "Content-Id: part1\n"
                + "\n"
                + "<foo>bar</foo>\n"
                + "--" + boundary + "\n"
                + "Content-Type: image/jpeg\n"
                + "Content-Transfer-Encoding: binary\n"
                + "Content-Id: part2\n"
                + "\n").getBytes(),
                new byte[]{(byte) 0xff, (byte) 0xd8},
                ("\n--" + boundary).getBytes());

        MimeParser.ParsingException ex = assertThrows(MimeParser.ParsingException.class,
                () -> parse(boundary, chunk1));
        assertThat(ex.getMessage(), is(equalTo("No closing MIME boundary")));
    }

    @Test
    public void testOneExactPartPerChunk() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "body 1\n"
                + "--" + boundary + "\n").getBytes();
        final byte[] chunk2 = ("Content-Id: part2\n"
                + "\n"
                + "body 2\n"
                + "--" + boundary + "--").getBytes();

        List<MimePart> parts = parse(boundary, List.of(chunk1, chunk2)).parts;
        assertThat(parts.size(), is(equalTo(2)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(1)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("body 1")));

        MimePart part2 = parts.get(1);
        assertThat(part2.headers.size(), is(equalTo(1)));
        assertThat(part2.headers.get("Content-Id"), hasItems("part2"));
        assertThat(part2.content, is(notNullValue()));
        assertThat(new String(part2.content), is(equalTo("body 2")));
    }

    @Test
    public void testPartInMultipleChunks() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "this-is-the-1st-slice-of-the-body\n").getBytes();
        final byte[] chunk2 = ("this-is-the-2nd-slice-of-the-body\n"
                + "--" + boundary + "--").getBytes();

        List<MimePart> parts = parse(boundary, List.of(chunk1, chunk2)).parts;
        assertThat(parts.size(), is(equalTo(1)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(1)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("this-is-the-1st-slice-of-the-body\n"
                + "this-is-the-2nd-slice-of-the-body")));
    }

    @Test
    public void testBoundaryAcrossChunksDataRequired() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "this-is-the-body-of-part1\n"
                + "--" + boundary.substring(0, 3)).getBytes();

        ParserEventProcessor processor = new ParserEventProcessor();
        MimeParser parser = new MimeParser(boundary, processor);
        parser.offer(ByteBuffer.wrap(chunk1));
        parser.parse();

        assertThat(processor.partContent, is(notNullValue()));
        assertThat(new String(processor.partContent), is(equalTo("this-is-the-body-of-")));
        assertThat(processor.lastEvent, is(notNullValue()));
        assertThat(processor.lastEvent.type(), is(equalTo(MimeParser.EventType.DATA_REQUIRED)));
    }

    @Test
    public void testBoundaryAcrossChunks() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "this-is-the-body-of-part1\n"
                + "--" + boundary.substring(0, 3)).getBytes();
        final byte[] chunk2 = (boundary.substring(3) + "\n"
                + "\n"
                + "this-is-the-body-of-part2\n"
                + "--" + boundary + "--").getBytes();

        List<MimePart> parts = parse(boundary, List.of(chunk1, chunk2)).parts;
        assertThat(parts.size(), is(equalTo(2)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(1)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("this-is-the-body-of-part1")));

        MimePart part2 = parts.get(1);
        assertThat(part2.headers.size(), is(equalTo(0)));
        assertThat(part2.content, is(notNullValue()));
        assertThat(new String(part2.content), is(equalTo("this-is-the-body-of-part2")));
    }

    @Test
    public void testClosingBoundaryAcrossChunks() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "this-is-the-body-of-part1\n"
                + "--" + boundary.substring(0, 3)).getBytes();
        final byte[] chunk2 = (boundary.substring(3) + "--").getBytes();

        List<MimePart> parts = parse(boundary, List.of(chunk1, chunk2)).parts;
        assertThat(parts.size(), is(equalTo(1)));

        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(1)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("this-is-the-body-of-part1")));
    }

    @Test
    public void testPreamble() {
        String boundary = "boundary";
        final byte[] chunk1 = ("\n\n\n\r\r\r\n\n\n\n\r\n"
                + "--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "part1\n"
                + "--" + boundary + "--").getBytes();

        List<MimePart> parts = parse(boundary, chunk1).parts;
        assertThat(parts.size(), is(equalTo(1)));
        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(1)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("part1")));
    }

    @Test
    public void testPreambleWithNoStartingBoundary() {
        String boundary = "boundary";
        final byte[] chunk1 = ("       \t   \t\t      \t \r\n"
                + "Content-Id: part1\n"
                + "\n"
                + "part1\n").getBytes();

        MimeParser.ParsingException ex = assertThrows(MimeParser.ParsingException.class,
                () -> parse(boundary, chunk1));
        assertThat(ex.getMessage(), is(equalTo("Missing start boundary")));
    }

    @Test
    public void testPreambleWithStartingBoundaryInNextChunk() {
        String boundary = "boundary";
        final byte[] chunk1 = "      \t    \t    \r\n".getBytes();
        final byte[] chunk2 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "part1\n"
                + "--" + boundary + "--").getBytes();

        List<MimePart> parts = parse(boundary, List.of(chunk1, chunk2)).parts;
        assertThat(parts.size(), is(equalTo(1)));
        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(1)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("part1")));
    }

    @Test
    public void testPreambleAcrossChunks() {
        String boundary = "boundary";
        final byte[] chunk1 = "      \t    \t    ".getBytes();
        final byte[] chunk2 = ("\t      \t     \r\n"
                + "--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "\n"
                + "part1\n"
                + "--" + boundary + "--").getBytes();

        List<MimePart> parts = parse(boundary, List.of(chunk1, chunk2)).parts;
        assertThat(parts.size(), is(equalTo(1)));
        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(1)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("part1")));
    }

    @Test
    public void testPreambleAcrossChunksWithNoStartingBoundary() {
        String boundary = "boundary";
        final byte[] chunk1 = "      \t    \t    ".getBytes();
        final byte[] chunk2 = ("\t      \t     \r\n"
                + "Content-Id: part1\n"
                + "\n"
                + "part1\n").getBytes();
        MimeParser.ParsingException ex = assertThrows(MimeParser.ParsingException.class,
                () -> parse(boundary, List.of(chunk1, chunk2)));
        assertThat(ex.getMessage(), is(equalTo("Missing start boundary")));
    }

    @Test
    public void testHeadersWithNoBlankLine() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "part1\n"
                + "--" + boundary + "--").getBytes();
        MimeParser.ParsingException ex = assertThrows(MimeParser.ParsingException.class,
                () -> parse(boundary, chunk1));
        assertThat(ex.getMessage(), is(equalTo("No blank line found")));
    }

    @Test
    public void testHeadersAcrossChunksWithNoBlankLine() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "Content-Type: text/plain\n").getBytes();
        final byte[] chunk2 = ("Content-Description: this is part1\n"
                + "part1\n"
                + "--" + boundary + "--").getBytes();
        MimeParser.ParsingException ex = assertThrows(MimeParser.ParsingException.class,
                () -> parse(boundary, List.of(chunk1, chunk2)));
        assertThat(ex.getMessage(), is(equalTo("No blank line found")));
    }

    @Test
    public void testHeadersAcrossChunks() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n").getBytes();
        final byte[] chunk2 = ("Content-Type: text/plain\n"
                + "\n"
                + "part1\n"
                + "--" + boundary + "--").getBytes();
        List<MimePart> parts = parse(boundary, List.of(chunk1, chunk2)).parts;
        assertThat(parts.size(), is(equalTo(1)));
        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(2)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.headers.get("Content-Type"), hasItems("text/plain"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("part1")));
    }

    @Test
    public void testHeaderBlankLineInNextChunk() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: part1\n"
                + "Content-Type: text/plain\r\n").getBytes();
        final byte[] chunk2 = ("\n"
                + "part1\n"
                + "--" + boundary + "--").getBytes();
        List<MimePart> parts = parse(boundary, List.of(chunk1, chunk2)).parts;
        assertThat(parts.size(), is(equalTo(1)));
        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(2)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.headers.get("Content-Type"), hasItems("text/plain"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("part1")));
    }

    @Test
    public void testHeaderValueWithLeadingWhiteSpace() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Id: \tpart1\n"
                + "Content-Type:    \t  \t\t   text/plain\r\n"
                + "\n"
                + "part1\n"
                + "--" + boundary + "--").getBytes();
        List<MimePart> parts = parse(boundary, chunk1).parts;
        assertThat(parts.size(), is(equalTo(1)));
        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(2)));
        assertThat(part1.headers.get("Content-Id"), hasItems("part1"));
        assertThat(part1.headers.get("Content-Type"), hasItems("text/plain"));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("part1")));
    }

    @Test
    public void testHeaderValueWithWhiteSpacesOnly() {
        String boundary = "boundary";
        final byte[] chunk1 = ("--" + boundary + "\n"
                + "Content-Type:    \t  \t\t \n"
                + "\n"
                + "part1\n"
                + "--" + boundary + "--").getBytes();
        List<MimePart> parts = parse(boundary, chunk1).parts;
        assertThat(parts.size(), is(equalTo(1)));
        MimePart part1 = parts.get(0);
        assertThat(part1.headers.size(), is(equalTo(1)));
        assertThat(part1.headers.get("Content-Type"), hasItems(""));
        assertThat(part1.content, is(notNullValue()));
        assertThat(new String(part1.content), is(equalTo("part1")));
    }

    @Test
    public void testParserClosed() {
        try {
            ParserEventProcessor processor = new ParserEventProcessor();
            MimeParser parser = new MimeParser("boundary", processor);
            parser.close();
            parser.offer(ByteBuffer.wrap("foo".getBytes()));
            parser.parse();
            fail("exception should be thrown");
        } catch (MimeParser.ParsingException ex) {
            assertThat(ex.getMessage(), is(equalTo("Parser is closed")));
        }
    }

    /**
     * Concatenate the specified byte arrays.
     *
     * @param arrays byte arrays to concatenate
     * @return resulting array of the concatenation
     */
    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] res = new byte[length];
        int pos = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, res, pos, array.length);
            pos += array.length;
        }
        return res;
    }

    /**
     * Parse the parts in the given chunk.
     *
     * @param boundary boundary string
     * @param data for the chunks to parse
     * @return test parser event processor
     */
    static ParserEventProcessor parse(String boundary, byte[] data) {
        return parse(boundary, List.of(data));
    }

    /**
     * Parse the parts in the given chunks.
     *
     * @param boundary boundary string
     * @param data for the chunks to parse
     * @return test parser event processor
     */
    static ParserEventProcessor parse(String boundary, List<byte[]> data) {
        ParserEventProcessor processor = new ParserEventProcessor();
        MimeParser parser = new MimeParser(boundary, processor);
        for (byte[] bytes : data) {
            parser.offer(ByteBuffer.wrap(bytes));
            parser.parse();
        }
        parser.close();
        return processor;
    }

    /**
     * Test parser event processor.
     */
    static final class ParserEventProcessor implements MimeParser.EventProcessor {

        List<MimePart> parts = new LinkedList<>();
        Map<String, List<String>> partHeaders = new HashMap<>();
        byte[] partContent = null;
        MimeParser.ParserEvent lastEvent = null;

        @Override
        public void process(MimeParser.ParserEvent event) {
            switch (event.type()) {
                case START_PART:
                    partHeaders = new HashMap<>();
                    partContent = null;
                    break;

                case HEADER:
                    MimeParser.HeaderEvent headerEvent = event.asHeaderEvent();
                    String name = headerEvent.name();
                    String value = headerEvent.value();
                    assertThat(name, notNullValue());
                    assertThat(name.length(), not(equalTo(0)));
                    assertThat(value, notNullValue());
                    List<String> values = partHeaders.get(name);
                    if (values == null) {
                        values = new ArrayList<>();
                        partHeaders.put(name, values);
                    }
                    values.add(value);
                    break;

                case CONTENT:
                    ByteBuffer content = event.asContentEvent().content().buffer();
                    assertThat(content, is(notNullValue()));
                    if (partContent == null) {
                        partContent = Utils.toByteArray(content);
                    } else {
                        partContent = concat(partContent, Utils.toByteArray(content));
                    }
                    break;

                case END_PART:
                    parts.add(new MimePart(partHeaders, partContent));
                    break;
            }
            lastEvent = event;
        }
    }

    /**
     * Pair of part headers and body part content.
     */
    static final class MimePart {

        final Map<String, List<String>> headers;
        final byte[] content;

        MimePart(Map<String, List<String>> headers, byte[] content) {
            this.headers = headers;
            this.content = content;
        }
    }
}
