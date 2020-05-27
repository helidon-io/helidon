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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link ContentDisposition}.
 */
public class ContentDispositionTest {

    private static final ZonedDateTime ZDT = ZonedDateTime.of(2008, 6, 3, 11, 5, 30, 0, ZoneId.of("Z"));

    @Test
    public void testNoType() {
        assertThrows(IllegalArgumentException.class, () -> ContentDisposition.parse("foo=\"bar\"; bar=\"foo\""));
    }

    @Test
    public void testWhiteSpaces() {
        ContentDisposition cd = ContentDisposition.parse(" inline;foo=bar; bar=foo ; abc=xyz ");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(3)));
        assertThat(cd.parameters().get("foo"), is(equalTo("bar")));
        assertThat(cd.parameters().get("bar"), is(equalTo("foo")));
        assertThat(cd.parameters().get("abc"), is(equalTo("xyz")));
    }

    @Test
    public void testQuotedString() {
        ContentDisposition cd = ContentDisposition.parse("inline; foo=\" b a r\"");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
        assertThat(cd.parameters().get("foo"), is(equalTo(" b a r")));
    }

    @Test
    public void testName() {
        ContentDisposition cd = ContentDisposition.parse("form-data; name=user");
        assertThat(cd.type(), is(equalTo("form-data")));
        assertThat(cd.name().isPresent(), is(equalTo(true)));
        assertThat(cd.name().get(), is(equalTo("user")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    public void testFilename() {
        ContentDisposition cd = ContentDisposition.parse("attachment; filename=index.html");
        assertThat(cd.type(), is(equalTo("attachment")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("index.html")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    public void testCreationDate() {
        ContentDisposition cd = ContentDisposition.parse("attachment; creation-date=\"Tue, 3 Jun 2008 11:05:30 GMT\"");
        assertThat(cd.type(), is(equalTo("attachment")));
        assertThat(cd.creationDate().isPresent(), is(equalTo(true)));
        assertThat(cd.creationDate().get(), is(equalTo(ZDT)));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    public void testModificationDate() {
        ContentDisposition cd = ContentDisposition.parse("attachment; modification-date=\"Tue, 3 Jun 2008 11:05:30 GMT\"");
        assertThat(cd.type(), is(equalTo("attachment")));
        assertThat(cd.modificationDate().isPresent(), is(equalTo(true)));
        assertThat(cd.modificationDate().get(), is(equalTo(ZDT)));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    public void testReadDate() {
        ContentDisposition cd = ContentDisposition.parse("attachment; read-date=\"Tue, 3 Jun 2008 11:05:30 GMT\"");
        assertThat(cd.type(), is(equalTo("attachment")));
        assertThat(cd.readDate().isPresent(), is(equalTo(true)));
        assertThat(cd.readDate().get(), is(equalTo(ZDT)));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    public void testSize() {
        ContentDisposition cd = ContentDisposition.parse("inline; size=128");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.size().isPresent(), is(equalTo(true)));
        assertThat(cd.size().getAsLong(), is(equalTo(128L)));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    public void testPercentEncodedFilename() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=the%20great%20file.html");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("the great file.html")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    public void testFilenameWithBackslash() {
        ContentDisposition cd = ContentDisposition.parse("inline; filename=\"C:\\index.html\"");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.filename().isPresent(), is(equalTo(true)));
        assertThat(cd.filename().get(), is(equalTo("C:\\index.html")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    public void testParamsWithQuotedPair() {
        ContentDisposition cd = ContentDisposition.parse("inline; foo=\"\\\\\"; bar=\"\\\"\"");
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(2)));
        assertThat(cd.parameters().get("foo"), is(equalTo("\\")));
        assertThat(cd.parameters().get("bar"), is(equalTo("\"")));
    }

    @Test
    public void testCaseInsensitiveType() {
        ContentDisposition cd = ContentDisposition.parse("aTTachMENT; name=bar");
        assertThat(cd.type(), is(equalTo("attachment")));
        assertThat(cd.name().isPresent(), is(equalTo(true)));
        assertThat(cd.name().get(), is(equalTo("bar")));
        assertThat(cd.parameters(), is(notNullValue()));
        assertThat(cd.parameters().size(), is(equalTo(1)));
    }

    @Test
    public void testBuilderWithFilenameEncoded() {
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
    public void testBuilderWithDates() {
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
    public void testBuilderWithSize() {
        ContentDisposition cd = ContentDisposition.builder()
                .type("inline")
                .size(128)
                .build();
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.size().isPresent(), is(equalTo(true)));
        assertThat(cd.size().getAsLong(), is(equalTo(128L)));
    }

    @Test
    public void testBuilderWithCustomParam() {
        ContentDisposition cd = ContentDisposition.builder()
                .type("inline")
                .parameter("foo", "bar")
                .build();
        assertThat(cd.type(), is(equalTo("inline")));
        assertThat(cd.parameters().get("foo"), is(equalTo("bar")));
    }
}
