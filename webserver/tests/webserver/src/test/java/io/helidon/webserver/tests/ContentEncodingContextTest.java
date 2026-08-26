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
import io.helidon.http.Header;
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
import io.helidon.http.encoding.gzip.GzipEncoding;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class ContentEncodingContextTest {

    private static final String ETAG = "\"content-encoding\"";
    private static final Header VARY_ACCEPT_ENCODING =
            HeaderValues.createCached(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME);
    private static final Header VARY_ORIGIN =
            HeaderValues.createCached(HeaderNames.VARY, HeaderNames.ORIGIN.defaultCase());
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
        rules.get("/hello", (_, res) -> res.send("hello webserver"))
                .get("/explicit", (_, res) -> res.contentEncoder(encodingContext.encoder("test"))
                        .send("hello webserver"))
                .any("/explicit-reconfigured", (req, res) -> {
                    res.contentEncoder(new TestEncoding("first", "first:").encoder());
                    res.contentEncoder(new TestEncoding("second", "second:").encoder());
                    if (req.prologue().method() == Method.HEAD) {
                        res.contentLength("second:abc".getBytes(StandardCharsets.UTF_8).length);
                        res.send();
                    } else {
                        res.send("abc");
                    }
                })
                .get("/explicit-empty", (_, res) -> res.contentEncoder(encodingContext.encoder("test")).send())
                .head("/explicit-empty", (_, res) -> res.contentEncoder(encodingContext.encoder("test")).send())
                .get("/explicit-empty-filtered", (_, res) -> {
                    res.streamFilter(stream -> stream);
                    res.contentEncoder(encodingContext.encoder("test"));
                    res.send();
                })
                .get("/automatic-empty-filtered", (_, res) -> {
                    res.streamFilter(stream -> stream);
                    res.send();
                })
                .get("/explicit-empty-no-content", (_, res) -> {
                    res.streamFilter(stream -> stream);
                    res.contentEncoder(encodingContext.encoder("test"));
                    res.status(Status.NO_CONTENT_204);
                    res.send();
                })
                .any("/filtered", (req, res) -> {
                    res.streamFilter(encodingContext.encoder("test"));
                    if (req.prologue().method() == Method.HEAD) {
                        res.send();
                    } else {
                        res.send("abc");
                    }
                })
                .get("/explicit-stream-error", (_, res) -> {
                    res.header(HeaderNames.CONTENT_LENGTH, "1");
                    res.header(HeaderNames.CONTENT_RANGE, "bytes 0-0/1");
                    res.header(HeaderNames.ETAG, "\"stale\"");
                    res.header(HeaderNames.ACCEPT_RANGES, "bytes");
                    res.header(HeaderNames.CACHE_CONTROL, "no-store");
                    res.header(HeaderNames.VARY, "Origin");
                    res.contentEncoder(encodingContext.encoder("test"));
                    res.outputStream();
                    throw new IllegalStateException("Error after selecting explicit content encoder");
                })
                .get("/stream-error", (_, res) -> {
                    res.header(HeaderNames.CONTENT_LENGTH, "1");
                    res.outputStream();
                    throw new IllegalStateException("Error after selecting response stream");
                })
                .get("/explicit-reset", (_, res) -> {
                    res.contentEncoder(encodingContext.encoder("test"));
                    RoutingResponse routingResponse = (RoutingResponse) res;
                    if (!routingResponse.reset()) {
                        throw new IllegalStateException("Response reset failed");
                    }
                    routingResponse.automaticContentEncoding(false);
                    res.send("hello webserver");
                })
                .get("/vary", (_, res) -> {
                    res.headers().add(HeaderValues.create(HeaderNames.VARY, HeaderNames.ORIGIN.defaultCase()));
                    res.send("hello webserver");
                })
                .get("/not-modified", (req, res) -> {
                    res.header(HeaderNames.ETAG, ETAG);
                    if (req.headers().contains(HeaderValues.create(HeaderNames.IF_NONE_MATCH, ETAG))) {
                        res.status(Status.NOT_MODIFIED_304).send();
                    } else {
                        res.send("hello webserver");
                    }
                })
                .get("/reset", (_, res) -> {
                    RoutingResponse routingResponse = (RoutingResponse) res;
                    routingResponse.automaticContentEncoding(false);
                    if (!routingResponse.reset()) {
                        throw new IllegalStateException("Response reset failed");
                    }
                    res.send("hello webserver");
                })
                .get("/reset-stream", (_, res) -> {
                    RoutingResponse routingResponse = (RoutingResponse) res;
                    routingResponse.automaticContentEncoding(false);
                    res.outputStream();
                    if (!routingResponse.resetStream()) {
                        throw new IllegalStateException("Response stream reset failed");
                    }
                    res.send("hello webserver");
                })
                .get("/explicit-reset-stream", (req, res) -> {
                    RoutingResponse routingResponse = (RoutingResponse) res;
                    routingResponse.contentEncoder(new TestEncoding("second", "second:").encoder());
                    res.outputStream();
                    assertThrows(IllegalStateException.class,
                                 () -> routingResponse.contentEncoder(new TestEncoding("third", "third:").encoder()));
                    assertThrows(IllegalStateException.class, () -> routingResponse.automaticContentEncoding(false));
                    if (!routingResponse.resetStream()) {
                        throw new IllegalStateException("Response stream reset failed");
                    }
                    res.send("abc");
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
    void testAutomaticContentEncodingAddsVaryWithoutRequestHeader() {
        try (Http1ClientResponse response = client.method(Method.GET).uri("/hello").request()) {
            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.entity().as(String.class), equalTo("hello webserver"));
            assertThat(encodingContext.NO_ACCEPT_ENCODING_COUNT, greaterThan(0));
        }
    }

    @Test
    void testAutomaticContentEncodingAddsVaryWithoutRequestHeaderForOutputStream() {
        try (Http1ClientResponse response = client.method(Method.GET).uri("/stream").request()) {
            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.entity().as(String.class), equalTo("hello webserver"));
        }
    }

    @Test
    void testNoAcceptableContentEncodingAddsVaryAcceptEncoding() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/hello")
                .header(HeaderNames.ACCEPT_ENCODING, "zstd, identity;q=0")
                .request()) {

            assertThat(response.status(), equalTo(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testExplicitContentEncoder() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/explicit")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.VARY));
            assertThat(response.entity().as(String.class), equalTo("encoded:hello webserver"));
        }
    }

    @Test
    void testErrorResponseUsesSelectedExplicitContentEncoder() {
        String encodedEntity = "encoded:Internal Server Error";
        String encodedLength = String.valueOf(encodedEntity.getBytes(StandardCharsets.UTF_8).length);

        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/explicit-stream-error")
                .header(HeaderNames.ACCEPT_ENCODING, "g zip")
                .request()) {

            assertThat(response.status(), equalTo(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_RANGE));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.ETAG));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.ACCEPT_RANGES));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CACHE_CONTROL, "no-store"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, "Origin"));
            assertThat(response.entity().as(String.class), equalTo(encodedEntity));
        }
    }

    @Test
    void testErrorResponseReplacesStaleContentLength() {
        String entity = "Internal Server Error";
        String contentLength = String.valueOf(entity.getBytes(StandardCharsets.UTF_8).length);

        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/stream-error")
                .request()) {

            assertThat(response.status(), equalTo(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, contentLength));
            assertThat(response.entity().as(String.class), equalTo(entity));
        }
    }

    @Test
    void testLastExplicitContentEncoderWins() {
        String encodedEntity = "second:abc";
        String encodedLength = String.valueOf(encodedEntity.getBytes(StandardCharsets.UTF_8).length);

        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/explicit-reconfigured")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), equalTo(List.of("second")));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.entity().as(String.class), equalTo(encodedEntity));
        }

        try (Http1ClientResponse response = client.method(Method.HEAD)
                .uri("/explicit-reconfigured")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), equalTo(List.of("second")));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.entity().hasEntity(), equalTo(false));
        }
    }

    @Test
    void testExplicitContentEncoderForEmptyResponse() {
        String encodedEntity = "encoded:";
        String encodedLength = String.valueOf(encodedEntity.getBytes(StandardCharsets.UTF_8).length);

        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/explicit-empty")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.entity().as(String.class), equalTo(encodedEntity));
        }

        try (Http1ClientResponse response = client.method(Method.HEAD)
                .uri("/explicit-empty")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.entity().hasEntity(), equalTo(false));
        }
    }

    @Test
    void testExplicitContentEncoderForFilteredEmptyResponse() {
        String encodedEntity = "encoded:";
        String encodedLength = String.valueOf(encodedEntity.getBytes(StandardCharsets.UTF_8).length);

        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/explicit-empty-filtered")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.entity().as(String.class), equalTo(encodedEntity));
        }
    }

    @Test
    void testAutomaticContentEncoderSkippedForFilteredEmptyResponse() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/automatic-empty-filtered")
                .header(HeaderNames.ACCEPT_ENCODING, "test")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.VARY));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH, "0"));
            assertThat(response.entity().hasEntity(), equalTo(false));
        }
    }

    @Test
    void testExplicitContentEncoderDoesNotAddEntityToNoContentResponse() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/explicit-empty-no-content")
                .request()) {

            assertThat(response.status(), equalTo(Status.NO_CONTENT_204));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.entity().hasEntity(), equalTo(false));
        }
    }

    @Test
    void testResponseFilterHeadOmitsImplicitContentLength() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/filtered")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.entity().as(String.class), equalTo("encoded:abc"));
        }

        try (Http1ClientResponse response = client.method(Method.HEAD)
                .uri("/filtered")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_LENGTH));
            assertThat(response.entity().hasEntity(), equalTo(false));
        }
    }

    @Test
    void testResetClearsExplicitContentEncoder() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/explicit-reset")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.VARY));
            assertThat(response.entity().as(String.class), equalTo("hello webserver"));
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
    void testAutomaticContentEncodingAddsVaryAcceptEncoding() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/hello")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testAutomaticContentEncodingAddsVaryAcceptEncodingForOutputStream() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/stream")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testAutomaticContentEncodingPreservesVary() {
        try (Http1ClientResponse response = client.method(Method.GET).uri("/vary").request()) {
            assertThat(response.headers().values(HeaderNames.VARY), hasSize(2));
            assertThat(response.headers().containsToken(VARY_ORIGIN), is(true));
            assertThat(response.headers().containsToken(VARY_ACCEPT_ENCODING), is(true));
        }
    }

    @Test
    void testAutomaticContentEncodingAddsVaryToNotModified() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/not-modified")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));

            var varyValues = response.headers().values(HeaderNames.VARY);
            assertThat("Vary response header " + varyValues,
                       response.headers().containsToken(VARY_ACCEPT_ENCODING), is(true));
        }

        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/not-modified")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .header(HeaderNames.IF_NONE_MATCH, ETAG)
                .request()) {
            assertThat(response.status(), is(Status.NOT_MODIFIED_304));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.entity().hasEntity(), is(false));

            var varyValues = response.headers().values(HeaderNames.VARY);
            assertThat("Vary response header " + varyValues,
                       response.headers().containsToken(VARY_ACCEPT_ENCODING), is(true));
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

    @Test
    void testResetStreamPreservesSelectedExplicitContentEncoder() {
        try (Http1ClientResponse response = client.method(Method.GET)
                .uri("/explicit-reset-stream")
                .request()) {

            assertThat(response.status(), equalTo(Status.OK_200));
            assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), equalTo(List.of("second")));
            assertThat(response.entity().as(String.class), equalTo("second:abc"));
        }
    }

    private static class CustomizedEncodingContext implements ContentEncodingContext {
        int ACCEPT_ENCODING_COUNT = 0;

        int NO_ACCEPT_ENCODING_COUNT = 0;

        ContentEncodingContext contentEncodingContext = ContentEncodingContext.builder()
                .addContentEncoding(GzipEncoding.create())
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

    private record TestEncoding(String id, String prefix) implements ContentEncoding {
        private TestEncoding() {
            this("test", "encoded:");
        }

        @Override
        public Set<String> ids() {
            return Set.of(id);
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
                                network.write(prefix.getBytes(StandardCharsets.UTF_8));
                                prefixWritten = true;
                            }
                        }
                    };
                }

                @Override
                public void headers(WritableHeaders<?> headers) {
                    headers.add(HeaderValues.create(HeaderNames.CONTENT_ENCODING, id));
                    headers.remove(HeaderNames.CONTENT_LENGTH);
                }
            };
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public String type() {
            return id;
        }
    }
}
