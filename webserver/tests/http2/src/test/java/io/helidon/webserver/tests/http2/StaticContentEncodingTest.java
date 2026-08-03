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

package io.helidon.webserver.tests.http2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.staticcontent.FileSystemHandlerConfig;
import io.helidon.webserver.staticcontent.StaticContentFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class StaticContentEncodingTest {
    @TempDir
    static Path tempDir;

    private final Http2Client client;

    StaticContentEncodingTest(Http2Client client) {
        this.client = client;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.contentEncoding(ContentEncodingContext.builder()
                                        .addContentEncoding(new TestEncoding())
                                        .build());
    }

    @SetUpRoute
    static void setupRouting(HttpRouting.Builder builder) throws IOException {
        Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("resource.txt"), "Nested content");
        Files.writeString(nested.resolve("resource.txt.br"), "Pre-compressed content");

        builder.register("/path", StaticContentFeature.createService(FileSystemHandlerConfig.create(tempDir)));
        builder.get("/reset", (req, res) -> {
            RoutingResponse routingResponse = (RoutingResponse) res;
            routingResponse.automaticContentEncoding(false);
            if (!routingResponse.reset()) {
                throw new IllegalStateException("Response reset failed");
            }
            res.send("Nested content");
        });
        builder.get("/reset-stream", (req, res) -> {
            RoutingResponse routingResponse = (RoutingResponse) res;
            routingResponse.automaticContentEncoding(false);
            res.outputStream();
            if (!routingResponse.resetStream()) {
                throw new IllegalStateException("Response stream reset failed");
            }
            res.send("Nested content");
        });
    }

    @Test
    void runtimeEncodedHeadOmitsContentLength() {
        try (Http2ClientResponse response = client.head("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "test, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_LENGTH));
        }
    }

    @Test
    void invalidAcceptEncodingReturnsBadRequest() {
        try (Http2ClientResponse response = client.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "g zip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
        }
    }

    @Test
    void preCompressedSidecarSelected() {
        try (Http2ClientResponse response = client.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Pre-compressed content"));
        }
    }

    @Test
    void preCompressedSidecarHead() {
        try (Http2ClientResponse response = client.head("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, "22"));
        }
    }

    @Test
    void resetRestoresAutomaticContentEncoding() {
        try (Http2ClientResponse response = client.get("/reset")
                .header(HeaderNames.ACCEPT_ENCODING, "test")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.as(String.class), is("runtime:Nested content"));
        }
    }

    @Test
    void resetStreamRestoresAutomaticContentEncoding() {
        try (Http2ClientResponse response = client.get("/reset-stream")
                .header(HeaderNames.ACCEPT_ENCODING, "test")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.as(String.class), is("runtime:Nested content"));
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
                        public void flush() throws IOException {
                            network.flush();
                        }

                        @Override
                        public void close() throws IOException {
                            network.close();
                        }

                        private void writePrefix() throws IOException {
                            if (!prefixWritten) {
                                network.write("runtime:".getBytes(StandardCharsets.UTF_8));
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
