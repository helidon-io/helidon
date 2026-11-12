/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentDispositionTest {
    private static final ZonedDateTime ZDT = ZonedDateTime.of(2008, 6, 3, 11, 5, 30, 0, ZoneId.of("Z"));

    @Test
    void testNoType() {
        assertThrows(IllegalArgumentException.class, () -> ContentDisposition.parse("foo=\"bar\"; bar=\"foo\""));
    }

    @Test
    void testWhiteSpaces() {
        ContentDisposition cd = ContentDisposition.parse(" inline;foo=bar; bar=foo ; abc=xyz ");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(3)));
        assertThat(cd.parameters().get("foo"), is(equalTo("bar")));
        assertThat(cd.parameters().get("bar"), is(equalTo("foo")));
        assertThat(cd.parameters().get("abc"), is(equalTo("xyz")));
    }

    @Test
    void testQuotedString() {
        ContentDisposition cd = ContentDisposition.parse("inline; foo=\" b a r\"");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
        assertThat(cd.parameters().get("foo"), is(equalTo(" b a r")));
    }

    @Test
    void testQuotedSemicolons() {
        ContentDisposition cd = ContentDisposition.parse("form-data; name=\"field;name\"; filename=\"a;b.txt\"; size=128");
        assertThat(cd.contentName().orElseThrow(), is("field;name"));
        assertThat(cd.filename().orElseThrow(), is("a;b.txt"));
        assertThat(cd.size().orElseThrow(), is(128L));
        assertThat(cd.parameters().size(), is(3));
    }

    @Test
    void testBuilderQuotedNameRoundTrip() {
        ContentDisposition cd = ContentDisposition.builder().name("a\"b").filename("x.txt").build();
        assertThat(cd.get(), is("form-data;name=\"a\\\"b\";filename=\"x.txt\""));
        ContentDisposition parsed = ContentDisposition.parse(cd.get());
        assertThat(parsed.contentName().orElseThrow(), is("a\"b"));
        assertThat(parsed.filename().orElseThrow(), is("x.txt"));
        assertThat(parsed.parameters().size(), is(2));
    }

    @Test
    void testUnusualParameterRoundTrips() {
        for (String name : List.of("", "a\\", "a\\\"b", "a\\;b", "a\";b", " a b ", "a%22%0A%25+b",
                                   "a\";filename=\"other.txt", "\\\";\\\\;\"")) {
            ContentDisposition cd = ContentDisposition.builder()
                    .name(name)
                    .parameter("custom", name)
                    .filename("x.txt")
                    .size(12)
                    .build();
            ContentDisposition parsed = ContentDisposition.parse(cd.get());
            assertThat("name " + name, parsed.contentName().orElseThrow(), is(name));
            assertThat("custom " + name, parsed.parameters().get("custom"), is(name));
            assertThat(parsed.filename().orElseThrow(), is("x.txt"));
            assertThat(parsed.size().orElseThrow(), is(12L));
            assertThat(parsed.parameters().size(), is(4));
        }
    }

    @Test
    void testExternalBackslashBeforeSemicolon() {
        ContentDisposition cd = ContentDisposition.parse("form-data;name=\"a\\\\;b\";filename=\"x.txt\";size=12");
        assertThat(cd.contentName().orElseThrow(), is("a\\;b"));
        assertThat(cd.filename().orElseThrow(), is("x.txt"));
        assertThat(cd.size().orElseThrow(), is(12L));
        assertThat(cd.parameters().size(), is(3));
    }

    @Test
    void testUnknownBackslashAndLiteralPercentName() {
        ContentDisposition cd = ContentDisposition.parse("form-data;name=\"a\\qb%22%0A%25+b\"");
        assertThat(cd.contentName().orElseThrow(), is("a\\qb%22%0A%25+b"));
    }

    @Test
    void testMalformedQuotedParametersRejected() {
        for (String header : List.of("form-data;name=\"unterminated", "form-data;name=\"dangling\\",
                                     "form-data;name=\"a\"b\";filename=\"x.txt\"",
                                     "form-data;name=\"a\"b", "form-data;name=a\"b\"",
                                     "form-data;name=\"a\" \"b\"")) {
            assertThrows(IllegalArgumentException.class, () -> ContentDisposition.parse(header), header);
        }
    }

    @Test
    void testRawLineBreaksRejected() {
        for (String lineBreak : List.of("\r", "\n", "\r\n")) {
            assertThrows(IllegalArgumentException.class,
                         () -> ContentDisposition.parse("form-data;name=\"a" + lineBreak + "b\""));
            assertThrows(IllegalArgumentException.class,
                         () -> ContentDisposition.builder().name("a" + lineBreak + "b").build().get());
            assertThrows(IllegalArgumentException.class,
                         () -> ContentDisposition.builder().parameter("custom", "a" + lineBreak + "b").build().get());
        }
    }

    @Test
    void testQuotedSemicolonAfterEscapedQuote() {
        ContentDisposition cd = ContentDisposition.parse("form-data; name=\"a\\\";b\"; filename=\"file.txt\"");
        assertThat(cd.contentName().orElseThrow(), is("a\";b"));
        assertThat(cd.filename().orElseThrow(), is("file.txt"));
        assertThat(cd.parameters().size(), is(2));
    }

    @Test
    void testParameterAfterQuotedEscapedBackslash() {
        ContentDisposition cd = ContentDisposition.parse("form-data; name=\"a\\\\\"; filename=\"a;b.txt\"");
        assertThat(cd.contentName().orElseThrow(), is("a\\"));
        assertThat(cd.filename().orElseThrow(), is("a;b.txt"));
        assertThat(cd.parameters().size(), is(2));
    }

    @Test
    void testQuotedSemicolonInWindowsFilename() {
        ContentDisposition cd = ContentDisposition.parse("form-data; filename=\"C:\\dir\\a;b.txt\"; name=\"upload\"");
        assertThat(cd.parameters().get("filename"), is("C:\\dir\\a;b.txt"));
        assertThat(cd.filename().orElseThrow(), is("a;b.txt"));
        assertThat(cd.contentName().orElseThrow(), is("upload"));
        assertThat(cd.parameters().size(), is(2));
    }

    @Test
    void testName() {
        ContentDisposition cd = ContentDisposition.parse("form-data; name=user");
        assertThat(cd.type(), is(equalTo("form-data")));
        assertThat(cd.contentName().isPresent(), is(equalTo(true)));
        assertThat(cd.contentName().get(), is(equalTo("user")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testFilename() {
        ContentDisposition cd = ContentDisposition.parse("attachment; filename=index.html");
        assertThat(cd.type(), is(equalTo("attachment")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("index.html")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testCreationDate() {
        ContentDisposition cd = ContentDisposition.parse("attachment; creation-date=\"Tue, 3 Jun 2008 11:05:30 GMT\"");
        assertThat(cd.type(), is(equalTo("attachment")));
        assertThat(cd.creationDate().isPresent(), is(equalTo(true)));
        assertThat(cd.creationDate().get(), is(equalTo(ZDT)));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testModificationDate() {
        ContentDisposition cd = ContentDisposition.parse("attachment; modification-date=\"Tue, 3 Jun 2008 11:05:30 GMT\"");
        assertThat(cd.type(), is(equalTo("attachment")));
        assertThat(cd.modificationDate().isPresent(), is(equalTo(true)));
        assertThat(cd.modificationDate().get(), is(equalTo(ZDT)));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testReadDate() {
        ContentDisposition cd = ContentDisposition.parse("attachment; read-date=\"Tue, 3 Jun 2008 11:05:30 GMT\"");
        assertThat(cd.type(), is(equalTo("attachment")));
        assertThat(cd.readDate().isPresent(), is(equalTo(true)));
        assertThat(cd.readDate().get(), is(equalTo(ZDT)));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testSize() {
        ContentDisposition cd = ContentDisposition.parse("inline; size=128");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.size().isPresent(), is(equalTo(true)));
        assertThat(cd.size().getAsLong(), is(equalTo(128L)));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testPercentEncodedFilename() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=the%20great%20file.html");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("the great file.html")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testFilenameWithDirectoryPathUsesOnlyTerminalComponent() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=%2e%2e%2f%2e%2e%2fetc%2fpasswd");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("passwd")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testFilenameWithRawDirectoryPathUsesOnlyTerminalComponent() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=../../etc/passwd");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("passwd")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testFilenameWithEncodedBackslashUsesOnlyTerminalComponent() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=%2e%2e%5c%2e%2e%5cpasswd");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("passwd")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testFilenameWithControlCharacterRejected() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=file%0aname.txt");
        assertThrows(IllegalArgumentException.class, cd::filename);
    }

    @Test
    void testFilenameWithEmptyQuotedValueRejected() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=\"\"");
        assertThrows(IllegalArgumentException.class, cd::filename);
    }

    @Test
    void testFilenameWithEmptyFinalSegmentRejected() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=path%2f");
        assertThrows(IllegalArgumentException.class, cd::filename);
    }

    @Test
    void testFilenameWithDotFinalSegmentRejected() {
        ContentDisposition dot = ContentDisposition.parse("inline; filename=%2e");
        ContentDisposition dotDot = ContentDisposition.parse("inline; filename=%2e%2e");
        ContentDisposition pathDotDot = ContentDisposition.parse("inline; filename=path%2f%2e%2e");
        assertThrows(IllegalArgumentException.class, dot::filename);
        assertThrows(IllegalArgumentException.class, dotDot::filename);
        assertThrows(IllegalArgumentException.class, pathDotDot::filename);
    }

    @Test
    void testMalformedQuotedFilenameWithBackslashRunRejected() {
        assertThrows(IllegalArgumentException.class,
                     () -> ContentDisposition.parse("inline; filename=\"" + "\\".repeat(8_192)));
    }

    @Test
    void testFilenameWithBackslash() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=\"C:\\index.html\"");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("index.html")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testParamsWithQuotedPair() {
        ContentDisposition cd = ContentDisposition.parse("inline; foo=\"\\\\\"; bar=\"\\\"\"; car=\"\\;\"");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(3)));
        assertThat(cd.parameters().get("foo"), is(equalTo("\\")));
        assertThat(cd.parameters().get("bar"), is(equalTo("\"")));
        assertThat(cd.parameters().get("car"), is(";"));
    }

    @Test
    void testCaseInsensitiveType() {
        ContentDisposition cd = ContentDisposition.parse("aTTachMENT; name=bar");
        assertThat(cd.type(), is(equalTo("attachment")));
        assertThat(cd.contentName().isPresent(), is(equalTo(true)));
        assertThat(cd.contentName().get(), is(equalTo("bar")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    void testBuilderWithFilenameEncoded() {
        ContentDisposition cd = ContentDisposition.builder()
                .type("inline")
                .filename("filename with spaces.txt")
                .build();
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("filename with spaces.txt")));
        assertThat(cd.size().isPresent(), is(equalTo(false)));
    }

    @Test
    void testBuilderWithDates() {
        ContentDisposition cd = ContentDisposition.builder()
                .type("inline")
                .modificationDate(ZDT)
                .creationDate(ZDT)
                .readDate(ZDT)
                .build();
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.modificationDate().isPresent(), is(equalTo(true)));
        assertThat(cd.modificationDate().get(), is(equalTo(ZDT)));
        assertThat(cd.creationDate().isPresent(), is(equalTo(true)));
        assertThat(cd.creationDate().get(), is(equalTo(ZDT)));
        assertThat(cd.readDate().isPresent(), is(equalTo(true)));
        assertThat(cd.readDate().get(), is(equalTo(ZDT)));
    }

    @Test
    void testBuilderWithSize() {
        ContentDisposition cd = ContentDisposition.builder()
                .type("inline")
                .size(128)
                .build();
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.size().isPresent(), is(equalTo(true)));
        assertThat(cd.size().getAsLong(), is(equalTo(128L)));
    }

    @Test
    void testBuilderWithCustomParam() {
        ContentDisposition cd = ContentDisposition.builder()
                .type("inline")
                .parameter("foo", "bar")
                .build();
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.parameters().get("foo"), is(equalTo("bar")));
    }

    @Test
    void testContentDispositionDefault() {
        ContentDisposition cd = ContentDisposition.builder().build();
        assertThat(cd.type(), is(equalTo("form-data")));
        assertThat(cd.parameters().size(), is(0));
    }

    @Test
    void testQuotes() {
        String template = "form-data;"
                + "name=\"someName\";"
                + "filename=\"file.txt\";"
                + "size=300";
        ContentDisposition cd = ContentDisposition.builder()
                .name("someName")
                .filename("file.txt")
                .size(300)
                .build();
        assertThat(cd.get(), is(equalTo(template)));
    }

    @Test
    void testDateQuotes() {
        ZonedDateTime zonedDateTime = ZonedDateTime.now();
        String date = zonedDateTime.format(DateTime.RFC_1123_DATE_TIME);
        // order is in order of insertion backed by LinkedMap -> we want to preserve this
        String template = "form-data;"
                + "creation-date=\"" + date + "\";"
                + "modification-date=\"" + date + "\";"
                + "read-date=\"" + date + "\"";
        ContentDisposition cd = ContentDisposition.builder()
                .creationDate(zonedDateTime)
                .modificationDate(zonedDateTime)
                .readDate(zonedDateTime)
                .build();
        assertThat(cd.toString(), is(equalTo(template)));
    }
}
