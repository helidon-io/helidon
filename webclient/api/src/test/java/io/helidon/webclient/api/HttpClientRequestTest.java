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

package io.helidon.webclient.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class HttpClientRequestTest {
    private static final HeaderName UNRELATED = HeaderNames.create("x-unrelated");
    private static final HeaderName PROTOCOL = HeaderNames.create("x-protocol");

    @Test
    void syncsUpdatedContentTypeBeforeFirstWrite() throws IOException {
        WritableHeaders<?> mutableInitialHeaders = WritableHeaders.create();
        mutableInitialHeaders.set(HeaderNames.CONTENT_TYPE, "multipart/form-data");
        mutableInitialHeaders.set(UNRELATED, "before");
        Headers initialHeaders = mutableInitialHeaders;

        ClientRequestHeaders sourceHeaders = ClientRequestHeaders.create(initialHeaders);
        sourceHeaders.set(HeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=generated");
        sourceHeaders.set(UNRELATED, "after");

        ClientRequestHeaders targetHeaders = ClientRequestHeaders.create(initialHeaders);
        targetHeaders.set(UNRELATED, "protocol-adjusted");
        targetHeaders.set(PROTOCOL, "owned");

        ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        try (var output = new HttpClientRequest.ContentTypeSyncOutputStream(delegate,
                                                                            initialHeaders,
                                                                            sourceHeaders,
                                                                            targetHeaders)) {
            output.write('a');
            sourceHeaders.set(HeaderNames.CONTENT_TYPE, "too-late");
            output.write('b');
        }

        assertThat(targetHeaders.get(HeaderNames.CONTENT_TYPE).get(), is("multipart/form-data; boundary=generated"));
        assertThat(targetHeaders.get(UNRELATED).get(), is("protocol-adjusted"));
        assertThat(targetHeaders.get(PROTOCOL).get(), is("owned"));
        assertThat(delegate.toString(StandardCharsets.UTF_8), is("ab"));
    }

    @Test
    void syncsAddedContentTypeWhenClosingEmptyBody() throws IOException {
        Headers initialHeaders = WritableHeaders.create();
        ClientRequestHeaders sourceHeaders = ClientRequestHeaders.create(initialHeaders);
        ClientRequestHeaders targetHeaders = ClientRequestHeaders.create(initialHeaders);
        sourceHeaders.set(HeaderValues.CONTENT_TYPE_TEXT_PLAIN);

        new HttpClientRequest.ContentTypeSyncOutputStream(new ByteArrayOutputStream(),
                                                          initialHeaders,
                                                          sourceHeaders,
                                                          targetHeaders).close();

        assertThat(targetHeaders.get(HeaderNames.CONTENT_TYPE).get(), is("text/plain"));
    }

    @Test
    void syncsRemovedContentType() throws IOException {
        WritableHeaders<?> mutableInitialHeaders = WritableHeaders.create();
        mutableInitialHeaders.set(HeaderValues.CONTENT_TYPE_TEXT_PLAIN);
        Headers initialHeaders = mutableInitialHeaders;
        ClientRequestHeaders sourceHeaders = ClientRequestHeaders.create(initialHeaders);
        ClientRequestHeaders targetHeaders = ClientRequestHeaders.create(initialHeaders);
        sourceHeaders.remove(HeaderNames.CONTENT_TYPE);

        new HttpClientRequest.ContentTypeSyncOutputStream(new ByteArrayOutputStream(),
                                                          initialHeaders,
                                                          sourceHeaders,
                                                          targetHeaders).close();

        assertThat(targetHeaders.contains(HeaderNames.CONTENT_TYPE), is(false));
    }

    @Test
    void waitsForWriteAfterFlush() throws IOException {
        Headers initialHeaders = WritableHeaders.create();
        ClientRequestHeaders sourceHeaders = ClientRequestHeaders.create(initialHeaders);
        ClientRequestHeaders targetHeaders = ClientRequestHeaders.create(initialHeaders);
        var output = new HttpClientRequest.ContentTypeSyncOutputStream(new ByteArrayOutputStream(),
                                                                       initialHeaders,
                                                                       sourceHeaders,
                                                                       targetHeaders);

        output.flush();
        sourceHeaders.set(HeaderValues.CONTENT_TYPE_TEXT_PLAIN);
        output.write('a');
        output.close();

        assertThat(targetHeaders.get(HeaderNames.CONTENT_TYPE).get(), is("text/plain"));
    }
}
