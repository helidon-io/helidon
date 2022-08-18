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
package io.helidon.media.multipart;

import io.helidon.common.http.ContentDisposition;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;

import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Header.CONTENT_DISPOSITION;
import static io.helidon.common.http.Http.Header.CONTENT_TYPE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test {@link ReadableBodyPartHeaders}.
 */
public class BodyPartHeadersTest {

    @Test
    public void testHeaderNameCaseInsensitive(){
        ReadableBodyPartHeaders headers = ReadableBodyPartHeaders.builder()
                .header(CONTENT_TYPE, "text/plain")
                .header(Http.Header.create("Content-ID"), "test")
                .header(Http.Header.create("my-header"), "abc=def; blah; key=value")
                .header(Http.Header.create("My-header"), "foo=bar")
                .build();
        assertThat(headers.values(Http.Header.create("Content-Type")), hasItems("text/plain"));
        assertThat(headers.values(Http.Header.create("Content-Id")), hasItems("test"));
        assertThat(headers.values(Http.Header.create("my-header")),
                hasItems("abc=def; blah; key=value", "foo=bar"));
    }

    @Test
    public void testContentType() {
        ReadableBodyPartHeaders headers = ReadableBodyPartHeaders.builder()
                .header(CONTENT_TYPE, "application/json")
                .build();
        assertThat(headers.partContentType(), is(notNullValue()));
        assertThat(headers.partContentType(),
                is(equalTo(HttpMediaType.APPLICATION_JSON)));
    }

    @Test
    public void testBuilderWithContentType() {
        WriteableBodyPartHeaders headers = WriteableBodyPartHeaders.builder()
                .contentType(HttpMediaType.APPLICATION_JSON)
                .build();
        assertThat(headers.contentType(), is(notNullValue()));
        assertThat(headers.partContentType(),
                is(equalTo(HttpMediaType.APPLICATION_JSON)));
    }

    @Test
    public void testDefaultContentType() {
        ReadableBodyPartHeaders headers = ReadableBodyPartHeaders.builder()
                .build();
        assertThat(headers.partContentType(), is(notNullValue()));
        assertThat(headers.partContentType(), is(equalTo(HttpMediaType.TEXT_PLAIN)));
    }

    @Test
    public void testDefaultContentTypeForFile() {
        ReadableBodyPartHeaders headers = ReadableBodyPartHeaders.builder()
                .header(CONTENT_DISPOSITION, "form-data; filename=foo")
                .build();
        assertThat(headers.partContentType(), is(notNullValue()));
        assertThat(headers.partContentType(),
                is(equalTo(HttpMediaType.APPLICATION_OCTET_STREAM)));
    }

    @Test
    public void testContentDisposition() {
        ReadableBodyPartHeaders headers = ReadableBodyPartHeaders.builder()
                .header(CONTENT_DISPOSITION, "form-data; name=foo")
                .build();
        assertThat(headers.contentDisposition(), is(notNullValue()));
        ContentDisposition cd = headers.contentDisposition();
        assertThat(cd.type(), is(equalTo("form-data")));
        assertThat(cd.contentName().isPresent(), is(equalTo(true)));
        assertThat(cd.contentName().get(), is(equalTo("foo")));
    }
}
