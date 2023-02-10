/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.sse.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.common.Weighted;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.StringSupportProvider;
import io.helidon.nima.sse.common.SseEvent;
import io.helidon.nima.sse.webserver.SseSink;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.HeaderValues.ACCEPT_EVENT_STREAM;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test that shows how to serialize an individual SSE event using a custom
 * {@link io.helidon.nima.http.media.spi.MediaSupportProvider} and a user-defined
 * media type. Each SSE event can be given a different media type.
 */
@ServerTest
class SseServerMediaTest {

    private static final HttpMediaType MY_PLAIN_TEXT = HttpMediaType.create("text/my_plain");

    private final Http1Client client;

    SseServerMediaTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sse", SseServerMediaTest::sse);
    }

    private static void sse(ServerRequest req, ServerResponse res) {
        try (SseSink sseSink = res.sink(SseSink.TYPE)) {
            sseSink.emit(SseEvent.create("hello", MY_PLAIN_TEXT))        // custom media type
                    .emit(SseEvent.create("world"));                          // text/plain by default
        }
    }

    @Test
    void testSseJson() {
        testSse("/sse", "data:HELLO\n\ndata:world\n\n");
    }

    private void testSse(String path, String result) {
        try (Http1ClientResponse response = client.get(path).header(ACCEPT_EVENT_STREAM).request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.as(String.class), is(result));
        }
    }

    @SuppressWarnings("unchecked")
    public static class MyStringSupportProvider extends StringSupportProvider implements Weighted {

        private static final EntityWriter<?> WRITER = new MyStringWriter();

        @Override
        public double weight() {
            return Weighted.DEFAULT_WEIGHT + 1;
        }

        @Override
        public <T> WriterResponse<T> writer(GenericType<T> type,
                                            Headers requestHeaders,
                                            WritableHeaders<?> responseHeaders) {
            HttpMediaType mediaType = responseHeaders.contentType().orElse(null);
            if (type.equals(GenericType.STRING) && mediaType != null && mediaType.equals(MY_PLAIN_TEXT)) {
                return new WriterResponse<>(SupportLevel.SUPPORTED, MyStringSupportProvider::writer);
            }
            return WriterResponse.unsupported();
        }

        private static <T> EntityWriter<T> writer() {
            return (EntityWriter<T>) WRITER;
        }

        private static final class MyStringWriter implements EntityWriter<String> {


            @Override
            public void write(GenericType<String> type,
                              String object,
                              OutputStream outputStream,
                              Headers requestHeaders,
                              WritableHeaders<?> responseHeaders) {
                write(object, outputStream, responseHeaders);
            }

            @Override
            public void write(GenericType<String> type,
                              String object,
                              OutputStream outputStream,
                              WritableHeaders<?> headers) {
                write(object, outputStream, headers);
            }

            private void write(String toWrite,
                               OutputStream outputStream,
                               WritableHeaders<?> writableHeaders) {
                try (outputStream) {
                    outputStream.write(toWrite.toUpperCase().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
