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
package io.helidon.webclient;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClientRequestHeadersImplTest {

    private WebClientRequestHeaders clientRequestHeaders;

    @BeforeEach
    public void beforeEach() {
        clientRequestHeaders = new WebClientRequestHeadersImpl();
    }

    @Test
    public void testAcceptedTypes() {
        List<MediaType> expectedTypes = List.of(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);

        clientRequestHeaders.addAccept(MediaType.TEXT_PLAIN);
        clientRequestHeaders.add(Http.Header.ACCEPT, MediaType.APPLICATION_JSON.toString());

        assertThat(clientRequestHeaders.acceptedTypes(), is(expectedTypes));
    }

    @Test
    public void testContentType() {
        assertThat(clientRequestHeaders.contentType(), is(MediaType.WILDCARD));

        clientRequestHeaders.add(Http.Header.CONTENT_TYPE, MediaType.APPLICATION_XML.toString());
        assertThat(clientRequestHeaders.contentType(), is(MediaType.APPLICATION_XML));

        clientRequestHeaders.contentType(MediaType.APPLICATION_JSON);
        assertThat(clientRequestHeaders.contentType(), is(MediaType.APPLICATION_JSON));
    }

    @Test
    public void testContentLength() {
        long contentLengthTemplate = 123;

        assertThat(clientRequestHeaders.contentLength(), is(Optional.empty()));

        clientRequestHeaders.contentLength(contentLengthTemplate);
        assertThat(clientRequestHeaders.contentLength(), is(Optional.of(contentLengthTemplate)));
    }

    @Test
    public void testIfModifiedSince() {
        String template = "Mon, 30 Nov 2015 22:45:59 GMT";
        ZonedDateTime zonedDateTemplate =
                ZonedDateTime.of(2015, 11, 30, 22, 45, 59, 0, ZoneId.of("Z"));
        ZonedDateTime ifModifiedSince =
                ZonedDateTime.of(2015, 11, 30, 23, 45, 59, 1234, ZoneId.of("UTC+1"));

        assertThat(clientRequestHeaders.ifModifiedSince(), is(Optional.empty()));

        clientRequestHeaders.ifModifiedSince(ifModifiedSince);

        assertThat(clientRequestHeaders.first(Http.Header.IF_MODIFIED_SINCE), is(Optional.of(template)));
        assertThat(clientRequestHeaders.ifModifiedSince(), is(Optional.of(zonedDateTemplate)));
    }

    @Test
    public void testIfUnmodifiedSince() {
        String template = "Mon, 30 Nov 2015 22:45:59 GMT";
        ZonedDateTime zonedDateTemplate =
                ZonedDateTime.of(2015, 11, 30, 22, 45, 59, 0, ZoneId.of("Z"));
        ZonedDateTime ifUnmodifiedSince =
                ZonedDateTime.of(2015, 11, 30, 23, 45, 59, 1234, ZoneId.of("UTC+1"));

        assertThat(clientRequestHeaders.ifUnmodifiedSince(), is(Optional.empty()));

        clientRequestHeaders.ifUnmodifiedSince(ifUnmodifiedSince);

        assertThat(clientRequestHeaders.first(Http.Header.IF_UNMODIFIED_SINCE), is(Optional.of(template)));
        assertThat(clientRequestHeaders.ifUnmodifiedSince(), is(Optional.of(zonedDateTemplate)));
    }

    @Test
    public void testIfNoneMatch() {
        List<String> unquotedTemplate = List.of("test", "test2");
        List<String> quotedTemplate = List.of("\"test\"", "\"test2\"");
        List<String> star = List.of("*");

        assertThat(clientRequestHeaders.ifNoneMatch(), is(Collections.emptyList()));

        clientRequestHeaders.ifNoneMatch(unquotedTemplate.toArray(new String[0]));
        assertThat(clientRequestHeaders.ifNoneMatch(), is(unquotedTemplate));
        assertThat(clientRequestHeaders.all(Http.Header.IF_NONE_MATCH), is(quotedTemplate));

        clientRequestHeaders.ifNoneMatch(star.toArray(new String[0]));
        assertThat(clientRequestHeaders.ifNoneMatch(), is(star));
        assertThat(clientRequestHeaders.all(Http.Header.IF_NONE_MATCH), is(star));
    }

    @Test
    public void testIfMatch() {
        List<String> unquotedTemplate = List.of("test", "test2");
        List<String> quotedTemplate = List.of("\"test\"", "\"test2\"");
        List<String> star = List.of("*");

        assertThat(clientRequestHeaders.ifMatch(), is(Collections.emptyList()));

        clientRequestHeaders.ifMatch(unquotedTemplate.toArray(new String[0]));
        assertThat(clientRequestHeaders.ifMatch(), is(unquotedTemplate));
        assertThat(clientRequestHeaders.all(Http.Header.IF_MATCH), is(quotedTemplate));

        clientRequestHeaders.ifMatch(star.toArray(new String[0]));
        assertThat(clientRequestHeaders.ifMatch(), is(star));
        assertThat(clientRequestHeaders.all(Http.Header.IF_MATCH), is(star));
    }

    @Test
    public void testIfRange() {
        String template = "Mon, 30 Nov 2015 22:45:59 GMT";
        String templateString = "testString";
        ZonedDateTime zonedDateTemplate =
                ZonedDateTime.of(2015, 11, 30, 22, 45, 59, 0, ZoneId.of("Z"));

        assertThat(clientRequestHeaders.ifRangeDate(), is(Optional.empty()));
        assertThat(clientRequestHeaders.ifRangeString(), is(Optional.empty()));

        clientRequestHeaders.ifRange(zonedDateTemplate);

        assertThat(clientRequestHeaders.ifRangeString(), is(Optional.of(template)));
        assertThat(clientRequestHeaders.ifRangeDate(), is(Optional.of(zonedDateTemplate)));

        clientRequestHeaders.ifRange(templateString);
        assertThat(clientRequestHeaders.ifRangeString(), is(Optional.of(templateString)));
        assertThrows(DateTimeParseException.class, () -> clientRequestHeaders.ifRangeDate());
    }

}
