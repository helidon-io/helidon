/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.reactive.webclient;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaTypes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Header.CONTENT_TYPE;
import static io.helidon.common.http.Http.Header.IF_MATCH;
import static io.helidon.common.http.Http.Header.IF_MODIFIED_SINCE;
import static io.helidon.common.http.Http.Header.IF_NONE_MATCH;
import static io.helidon.common.http.Http.Header.IF_UNMODIFIED_SINCE;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientRequestHeadersImplTest {
    private static final HttpMediaType YAML = HttpMediaType.create(MediaTypes.APPLICATION_YAML);
    private static final HttpMediaType XML = HttpMediaType.create(MediaTypes.APPLICATION_XML);
    private static final HttpMediaType JSON = HttpMediaType.create(MediaTypes.APPLICATION_JSON);
    private static final HttpMediaType TEXT = HttpMediaType.create(MediaTypes.TEXT_PLAIN);

    private WebClientRequestHeaders clientRequestHeaders;

    @BeforeEach
    void beforeEach() {
        clientRequestHeaders = new WebClientRequestHeadersImpl();
    }

    @Test
    void testAcceptedTypes() {
        List<HttpMediaType> expectedTypes = List.of(HttpMediaType.PLAINTEXT_UTF_8, HttpMediaType.JSON_UTF_8);

        clientRequestHeaders.addAccept(HttpMediaType.PLAINTEXT_UTF_8);
        clientRequestHeaders.add(HeaderValue.create(Header.ACCEPT, HttpMediaType.JSON_UTF_8.text()));

        assertThat(clientRequestHeaders.acceptedTypes(), is(expectedTypes));
    }

    @Test
    void testContentType() {
        assertThat(clientRequestHeaders.contentType(), optionalEmpty());

        clientRequestHeaders.add(CONTENT_TYPE, HttpMediaType.create(MediaTypes.APPLICATION_XML).text());
        assertThat(clientRequestHeaders.contentType(), optionalValue(is(XML)));

        clientRequestHeaders.contentType(MediaTypes.APPLICATION_JSON);
        assertThat(clientRequestHeaders.contentType(), optionalValue(is(JSON)));
    }

    @Test
    void testContentLength() {
        long contentLengthTemplate = 123;

        assertThat(clientRequestHeaders, noHeader(Header.CONTENT_LENGTH));
        assertThat(clientRequestHeaders.contentLength(), is(OptionalLong.empty()));

        clientRequestHeaders.contentLength(contentLengthTemplate);
        assertThat(clientRequestHeaders.contentLength(), is(OptionalLong.of(contentLengthTemplate)));
    }

    @Test
    void testIfModifiedSince() {
        String template = "Mon, 30 Nov 2015 22:45:59 GMT";
        ZonedDateTime zonedDateTemplate =
                ZonedDateTime.of(2015, 11, 30, 22, 45, 59, 0, ZoneId.of("Z"));
        ZonedDateTime ifModifiedSince =
                ZonedDateTime.of(2015, 11, 30, 23, 45, 59, 1234, ZoneId.of("UTC+1"));

        assertThat(clientRequestHeaders.ifModifiedSince(), is(Optional.empty()));

        clientRequestHeaders.ifModifiedSince(ifModifiedSince);

        assertThat(clientRequestHeaders, hasHeader(HeaderValue.create(IF_MODIFIED_SINCE, template)));
        assertThat(clientRequestHeaders.ifModifiedSince(), is(Optional.of(zonedDateTemplate)));
    }

    @Test
    void testIfUnmodifiedSince() {
        String template = "Mon, 30 Nov 2015 22:45:59 GMT";
        ZonedDateTime zonedDateTemplate =
                ZonedDateTime.of(2015, 11, 30, 22, 45, 59, 0, ZoneId.of("Z"));
        ZonedDateTime ifUnmodifiedSince =
                ZonedDateTime.of(2015, 11, 30, 23, 45, 59, 1234, ZoneId.of("UTC+1"));

        assertThat(clientRequestHeaders.ifUnmodifiedSince(), is(Optional.empty()));

        clientRequestHeaders.ifUnmodifiedSince(ifUnmodifiedSince);

        assertThat(clientRequestHeaders, hasHeader(HeaderValue.create(IF_UNMODIFIED_SINCE, template)));
        assertThat(clientRequestHeaders.ifUnmodifiedSince(), is(Optional.of(zonedDateTemplate)));
    }

    @Test
    void testIfNoneMatch() {
        List<String> unquotedTemplate = List.of("test", "test2");
        List<String> quotedTemplate = List.of("\"test\"", "\"test2\"");
        List<String> star = List.of("*");

        assertThat(clientRequestHeaders.ifNoneMatch(), is(Collections.emptyList()));

        clientRequestHeaders.ifNoneMatch(unquotedTemplate.toArray(new String[0]));
        assertThat(clientRequestHeaders.ifNoneMatch(), is(unquotedTemplate));
        assertThat(clientRequestHeaders, hasHeader(HeaderValue.create(IF_NONE_MATCH, quotedTemplate)));

        clientRequestHeaders.ifNoneMatch(star.toArray(new String[0]));
        assertThat(clientRequestHeaders.ifNoneMatch(), is(star));
        assertThat(clientRequestHeaders, hasHeader(HeaderValue.create(IF_NONE_MATCH, star)));
    }

    @Test
    void testIfMatch() {
        List<String> unquotedTemplate = List.of("test", "test2");
        List<String> quotedTemplate = List.of("\"test\"", "\"test2\"");
        List<String> star = List.of("*");

        assertThat(clientRequestHeaders.ifMatch(), is(Collections.emptyList()));

        clientRequestHeaders.ifMatch(unquotedTemplate.toArray(new String[0]));
        assertThat(clientRequestHeaders.ifMatch(), is(unquotedTemplate));
        assertThat(clientRequestHeaders, hasHeader(HeaderValue.create(IF_MATCH, quotedTemplate)));

        clientRequestHeaders.ifMatch(star.toArray(new String[0]));
        assertThat(clientRequestHeaders.ifMatch(), is(star));
        assertThat(clientRequestHeaders, hasHeader(HeaderValue.create(IF_MATCH, star)));
    }

    @Test
    void testIfRange() {
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

    @Test
    void testCaseInsensitivity() {
        clientRequestHeaders.contentType(MediaTypes.APPLICATION_XML);
        assertThat(clientRequestHeaders.contentType(), optionalValue(is(XML)));
        clientRequestHeaders.set(Header.create(CONTENT_TYPE.lowerCase()), MediaTypes.APPLICATION_JSON.text());
        assertThat(clientRequestHeaders.contentType(), optionalValue(is(JSON)));
        clientRequestHeaders.set(Header.create("CoNtEnT-TyPe"), MediaTypes.APPLICATION_YAML.text());
        assertThat(clientRequestHeaders.contentType(), optionalValue(is(YAML)));
    }

    @Test
    void testCopyHeaders() {
        clientRequestHeaders.contentType(MediaTypes.APPLICATION_XML);
        clientRequestHeaders.set(Header.create(CONTENT_TYPE.lowerCase()), MediaTypes.APPLICATION_JSON.text());
        clientRequestHeaders.set(Header.create("CoNtEnT-TyPe"), MediaTypes.APPLICATION_YAML.text());
        WebClientRequestHeaders copy = new WebClientRequestHeadersImpl(clientRequestHeaders);
        assertThat(copy.contentType(), optionalValue(is(YAML)));
    }

}
