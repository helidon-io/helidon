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

package io.helidon.webserver.tests;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.ContentEncodingContextConfig;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

@ServerTest
class ContentEncodingContextTest {

    private static final CustomizedEncodingContext encodingContext = new CustomizedEncodingContext();

    private final Http1Client client;

    ContentEncodingContextTest(Http1Client socketHttpClient) {
        this.client = socketHttpClient;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.contentEncoding(encodingContext);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/hello", (req, res) -> res.send("hello webserver"))
                .get("/reset", (req, res) -> {
                    RoutingResponse routingResponse = (RoutingResponse) res;
                    routingResponse.automaticContentEncoding(false);
                    if (!routingResponse.reset()) {
                        throw new IllegalStateException("Response reset failed");
                    }
                    res.send("hello webserver");
                })
                .get("/reset-stream", (req, res) -> {
                    RoutingResponse routingResponse = (RoutingResponse) res;
                    routingResponse.automaticContentEncoding(false);
                    res.outputStream();
                    if (!routingResponse.resetStream()) {
                        throw new IllegalStateException("Response stream reset failed");
                    }
                    res.send("hello webserver");
                })
                .get("/stream", (req, res) -> {
                    try (OutputStream out = res.outputStream()) {
                        out.write("hello webserver".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    @Test
    void testCustomizeContentEncodingContext() {
        try (Http1ClientResponse response = client.method(Method.GET).uri("/hello").request()) {
            assertThat(response.entity().as(String.class), equalTo("hello webserver"));
            assertThat(encodingContext.NO_ACCEPT_ENCODING_COUNT, greaterThan(0));
        }
    }

    @Test
    void testAutomaticContentEncodingAddsVaryAcceptEncoding() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/hello")
                .header(HeaderNames.ACCEPT_ENCODING, "test")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.entity().as(String.class), equalTo("encoded:hello webserver"));
        }
    }

    @Test
    void testAutomaticContentEncodingAddsVaryAcceptEncodingForOutputStream() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/stream")
                .header(HeaderNames.ACCEPT_ENCODING, "test")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.entity().as(String.class), equalTo("encoded:hello webserver"));
        }
    }

    @Test
    void testResetRestoresAutomaticContentEncoding() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/reset")
                .header(HeaderNames.ACCEPT_ENCODING, "test")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.entity().as(String.class), equalTo("encoded:hello webserver"));
        }
    }

    @Test
    void testResetStreamRestoresAutomaticContentEncoding() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/reset-stream")
                .header(HeaderNames.ACCEPT_ENCODING, "test")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.entity().as(String.class), equalTo("encoded:hello webserver"));
        }
    }


    private static class CustomizedEncodingContext implements ContentEncodingContext {
        int ACCEPT_ENCODING_COUNT = 0;

        int NO_ACCEPT_ENCODING_COUNT = 0;

        ContentEncodingContext contentEncodingContext = ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding())
                .build();

        @Override
        public ContentEncodingContextConfig prototype() {
            return contentEncodingContext.prototype();
        }

        @Override
        public boolean contentEncodingEnabled() {
            return true;
        }

        @Override
        public boolean contentDecodingEnabled() {
            return contentEncodingContext.contentDecodingEnabled();
        }

        @Override
        public boolean contentEncodingSupported(String encodingId) {
            return contentEncodingContext.contentEncodingSupported(encodingId);
        }

        @Override
        public List<String> contentEncodingIds() {
            return contentEncodingContext.contentEncodingIds();
        }

        @Override
        public boolean contentDecodingSupported(String encodingId) {
            return contentEncodingContext.contentDecodingSupported(encodingId);
        }

        @Override
        public ContentEncoder encoder(String encodingId) throws NoSuchElementException {
            return contentEncodingContext.encoder(encodingId);
        }

        @Override
        public ContentDecoder decoder(String encodingId) throws NoSuchElementException {
            return contentEncodingContext.decoder(encodingId);
        }

        @Override
        public ContentEncoder encoder(Headers headers) {
            if (headers.contains(HeaderNames.ACCEPT_ENCODING)) {
                ACCEPT_ENCODING_COUNT++;
            } else {
                NO_ACCEPT_ENCODING_COUNT++;
            }
            return contentEncodingContext.encoder(headers);
        }

    }

    private record TestEncoding() implements ContentEncoding {
        @Override
        public Set<String> ids() {
            return Set.of("test");
        }

        @Override
        public boolean supportsEncoding() {
            return true;
        }

        @Override
        public boolean supportsDecoding() {
            return false;
        }

        @Override
        public ContentDecoder decoder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ContentEncoder encoder() {
            return new ContentEncoder() {
                @Override
                public OutputStream apply(OutputStream network) {
                    return new OutputStream() {
                        private boolean prefixWritten;

                        @Override
                        public void write(int b) throws IOException {
                            writePrefix();
                            network.write(b);
                        }

                        @Override
                        public void write(byte[] bytes, int offset, int length) throws IOException {
                            writePrefix();
                            network.write(bytes, offset, length);
                        }

                        @Override
                        public void close() throws IOException {
                            network.close();
                        }

                        private void writePrefix() throws IOException {
                            if (!prefixWritten) {
                                network.write("encoded:".getBytes(StandardCharsets.UTF_8));
                                prefixWritten = true;
                            }
                        }
                    };
                }

                @Override
                public void headers(WritableHeaders<?> headers) {
                    headers.add(HeaderValues.create(HeaderNames.CONTENT_ENCODING, "test"));
                    headers.remove(HeaderNames.CONTENT_LENGTH);
                }
            };
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public String type() {
            return "test";
        }
    }
}
