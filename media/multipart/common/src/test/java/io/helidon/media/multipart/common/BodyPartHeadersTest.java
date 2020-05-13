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
package io.helidon.media.multipart.common;

import io.helidon.common.http.MediaType;
import org.junit.jupiter.api.Test;

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
                .header("content-type", "text/plain")
                .header("Content-ID", "test")
                .header("my-header", "abc=def; blah; key=value")
                .header("My-header", "foo=bar")
                .build();
        assertThat(headers.values("Content-Type"), hasItems("text/plain"));
        assertThat(headers.values("Content-Id"), hasItems("test"));
        assertThat(headers.values("my-header"),
                hasItems("abc=def; blah; key=value", "foo=bar"));
    }

    @Test
    public void testContentType() {
        ReadableBodyPartHeaders headers = ReadableBodyPartHeaders.builder()
                .header("content-type", "application/json")
                .build();
        assertThat(headers.contentType(), is(notNullValue()));
        assertThat(headers.contentType(),
                is(equalTo(MediaType.APPLICATION_JSON)));
    }

    @Test
    public void testBuilderWithContentType() {
        WriteableBodyPartHeaders headers = WriteableBodyPartHeaders.builder()
                .contentType(MediaType.APPLICATION_JSON)
                .build();
        assertThat(headers.contentType(), is(notNullValue()));
        assertThat(headers.contentType(),
                is(equalTo(MediaType.APPLICATION_JSON)));
    }

    @Test
    public void testDefaultContentType() {
        ReadableBodyPartHeaders headers = ReadableBodyPartHeaders.builder()
                .build();
        assertThat(headers.contentType(), is(notNullValue()));
        assertThat(headers.contentType(), is(equalTo(MediaType.TEXT_PLAIN)));
    }

    @Test
    public void testDefaultContentTypeForFile() {
        ReadableBodyPartHeaders headers = ReadableBodyPartHeaders.builder()
                .header("Content-Disposition", "form-data; filename=foo")
                .build();
        assertThat(headers.contentType(), is(notNullValue()));
        assertThat(headers.contentType(),
                is(equalTo(MediaType.APPLICATION_OCTET_STREAM)));
    }

    @Test
    public void testContentDisposition() {
        ReadableBodyPartHeaders headers = ReadableBodyPartHeaders.builder()
                .header("Content-Disposition", "form-data; name=foo")
                .build();
        assertThat(headers.contentDisposition(), is(notNullValue()));
        ContentDisposition cd = headers.contentDisposition();
        assertThat(cd.type(), is(equalTo("form-data")));
        assertThat(cd.name().isPresent(), is(equalTo(true)));
        assertThat(cd.name().get(), is(equalTo("foo")));
    }
}
