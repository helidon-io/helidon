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

package io.helidon.http.media.json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpMediaType;
import io.helidon.http.HttpMediaTypes;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/*
When adding/updating tests in this class, consider if it should be done
 in the following tests a well:
    - JacksonMediaTest
    - JsonbMediaTest
    - GsonMediaTest
    (and all other JSON based tests)
 */
class JsonMediaTest {
    private static final Charset ISO_8859_2 = Charset.forName("ISO-8859-2");
    private static final GenericType<JsonObject> JSON_OBJECT_TYPE = GenericType.create(JsonObject.class);
    private static final GenericType<JsonArray> JSON_ARRAY_TYPE = GenericType.create(JsonArray.class);

    private final MediaSupport provider;

    JsonMediaTest() {
        this.provider = JsonSupport.create();
        provider.init(MediaContext.create());
    }

    @Test
    void testWriteSingle() {
        WritableHeaders<?> headers = WritableHeaders.create();

        MediaSupport.WriterResponse<JsonObject> res = provider.writer(JSON_OBJECT_TYPE, headers);
        assertThat(res.support(), is(MediaSupport.SupportLevel.SUPPORTED));

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        res.supplier().get()
                .write(JSON_OBJECT_TYPE, createObject("test-title"), os, headers);

        assertThat(headers, HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, HttpMediaTypes.JSON_UTF_8.text()));
        String result = os.toString(StandardCharsets.UTF_8);
        assertThat(result, containsString("\"title\""));
        assertThat(result, containsString("\"test-title\""));

        // sanity check, parse back to JsonObject
        JsonObject sanity = provider.reader(JSON_OBJECT_TYPE, headers)
                .supplier()
                .get()
                .read(JSON_OBJECT_TYPE, new ByteArrayInputStream(os.toByteArray()), headers);

        assertThat(sanity.stringValue("title", "wrong"), is("test-title"));
    }

    @Test
    void testWriteList() {
        WritableHeaders<?> headers = WritableHeaders.create();

        MediaSupport.WriterResponse<JsonArray> res = provider.writer(JSON_ARRAY_TYPE, headers);
        assertThat(res.support(), is(MediaSupport.SupportLevel.SUPPORTED));

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        JsonArray jsonArray = createArray("first", "second", "third");
        res.supplier()
                .get()
                .write(JSON_ARRAY_TYPE, jsonArray, os, headers);

        assertThat(headers, HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, HttpMediaTypes.JSON_UTF_8.text()));
        String result = os.toString(StandardCharsets.UTF_8);
        assertThat(result, containsString("\"title\""));
        assertThat(result, containsString("\"first\""));
        assertThat(result, containsString("\"second\""));
        assertThat(result, containsString("\"third\""));

        // sanity check, parse back to JsonObjects
        JsonArray sanity = provider.reader(JSON_ARRAY_TYPE, headers)
                .supplier()
                .get()
                .read(JSON_ARRAY_TYPE, new ByteArrayInputStream(os.toByteArray()), headers);

        assertThat(sanity, is(createArray("first", "second", "third")));
    }

    @Test
    void testReadServerSingle() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.contentType(MediaTypes.APPLICATION_JSON);

        MediaSupport.ReaderResponse<JsonObject> res = provider.reader(JSON_OBJECT_TYPE, requestHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.SUPPORTED));

        InputStream is = new ByteArrayInputStream("{\"title\": \"utf-8: řžýčň\"}".getBytes(StandardCharsets.UTF_8));
        JsonObject JsonObject = res.supplier().get()
                .read(JSON_OBJECT_TYPE, is, requestHeaders);

        assertThat(JsonObject.stringValue("title", "wrong"), is("utf-8: řžýčň"));
    }

    @Test
    void testReadClientSingle() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        WritableHeaders<?> responseHeaders = WritableHeaders.create();
        requestHeaders.contentType(MediaTypes.APPLICATION_XML);
        responseHeaders.contentType(MediaTypes.APPLICATION_JSON);

        MediaSupport.ReaderResponse<JsonObject> res = provider.reader(JSON_OBJECT_TYPE, requestHeaders, responseHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.SUPPORTED));

        InputStream is = new ByteArrayInputStream("{\"title\": \"utf-8: řžýčň\"}".getBytes(StandardCharsets.UTF_8));
        JsonObject JsonObject = res.supplier().get()
                .read(JSON_OBJECT_TYPE, is, requestHeaders, responseHeaders);

        assertThat(JsonObject.stringValue("title", "wrong"), is("utf-8: řžýčň"));
    }

    @Test
    void testReadServerList() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.contentType(MediaTypes.APPLICATION_JSON);

        MediaSupport.ReaderResponse<JsonArray> res = provider.reader(JSON_ARRAY_TYPE, requestHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.SUPPORTED));

        InputStream is =
                new ByteArrayInputStream("[{\"title\": \"first\"}, {\"title\": \"second\"}]".getBytes(StandardCharsets.UTF_8));
        JsonArray jsonArray = res.supplier().get()
                .read(JSON_ARRAY_TYPE, is, requestHeaders);

        assertThat(jsonArray, is(createArray("first", "second")));
    }

    @Test
    void testReadServerSingleNonUtf8() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.contentType(HttpMediaType.create(MediaTypes.APPLICATION_JSON).withCharset(ISO_8859_2));

        MediaSupport.ReaderResponse<JsonObject> res = provider.reader(JSON_OBJECT_TYPE, requestHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.SUPPORTED));

        InputStream is = new ByteArrayInputStream("{\"title\": \"is-8859-2: řžýčň\"}".getBytes(ISO_8859_2));
        JsonObject JsonObject = res.supplier().get()
                .read(JSON_OBJECT_TYPE, is, requestHeaders);

        assertThat(JsonObject.stringValue("title", "wrong"), is("is-8859-2: řžýčň"));
    }

    @Test
    void testReadClientSingleNonUtf8() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        WritableHeaders<?> responseHeaders = WritableHeaders.create();
        requestHeaders.contentType(MediaTypes.APPLICATION_XML);
        responseHeaders.contentType(HttpMediaType.create(MediaTypes.APPLICATION_JSON).withCharset(ISO_8859_2));

        MediaSupport.ReaderResponse<JsonObject> res = provider.reader(JSON_OBJECT_TYPE, requestHeaders, responseHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.SUPPORTED));

        InputStream is = new ByteArrayInputStream("{\"title\": \"utf-8: řžýčň\"}".getBytes(ISO_8859_2));
        JsonObject JsonObject = res.supplier().get()
                .read(JSON_OBJECT_TYPE, is, requestHeaders, responseHeaders);

        assertThat(JsonObject.stringValue("title", "wrong"), is("utf-8: řžýčň"));
    }

    @Test
    void testReadServerListNonUtf8() {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.contentType(HttpMediaType.create(MediaTypes.APPLICATION_JSON).withCharset(ISO_8859_2));

        MediaSupport.ReaderResponse<JsonArray> res = provider.reader(JSON_ARRAY_TYPE, requestHeaders);
        assertThat(res.support(), is(MediaSupport.SupportLevel.SUPPORTED));

        InputStream is =
                new ByteArrayInputStream("[{\"title\": \"čř\"}, {\"title\": \"šň\"}]".getBytes(ISO_8859_2));
        JsonArray jsonObjects = res.supplier().get()
                .read(JSON_ARRAY_TYPE, is, requestHeaders);

        assertThat(jsonObjects, is(createArray("čř", "šň")));
    }

    private JsonObject createObject(String title) {
        return JsonObject.builder()
                .set("title", title)
                .build();
    }

    private JsonArray createArray(String... titles) {
        List<JsonObject> objects = new ArrayList<>();
        for (String title : titles) {
            objects.add(createObject(title));
        }
        return JsonArray.create(objects);
    }
}
