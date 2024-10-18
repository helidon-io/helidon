/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
    void testFilenameWithBackslash() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=\"C:\\index.html\"");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("C:\\index.html")));
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