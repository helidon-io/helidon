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

package io.helidon.webserver.staticcontent;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.helidon.webserver.staticcontent.StaticContentFeature.createService;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class StaticContentEncodingTest {
    @TempDir
    static Path tempDir;

    private final Http1Client client;

    StaticContentEncodingTest(Http1Client socketHttpClient) {
        this.client = socketHttpClient;
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

        Files.writeString(tempDir.resolve("resource.txt"), "Content");
        Files.writeString(tempDir.resolve("resource.txt.br"), "Brotli content");
        Files.writeString(nested.resolve("resource.txt"), "Nested content");

        builder.register("/path", createService(FileSystemHandlerConfig.create(tempDir)));
    }

    @Test
    void sidecarSatisfiesRejectedIdentityWithoutRuntimeProvider() {
        try (Http1ClientResponse response = client.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Brotli content"));
        }
    }

    @Test
    void runtimeEncodingSatisfiesRejectedIdentityWhenSidecarMissing() {
        try (Http1ClientResponse response = client.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("runtime:Nested content"));
        }
    }

    @Test
    void runtimeEncodedHeadOmitsContentLength() {
        try (Http1ClientResponse response = client.head("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_LENGTH));
        }
    }

    @Test
    void identitySelectionSuppressesAutomaticRuntimeEncoding() {
        try (Http1ClientResponse response = client.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip;q=0.5, identity;q=1")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void noAcceptableRepresentationReturnsTerminal406() {
        try (Http1ClientResponse response = client.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
        }
    }

    private record TestEncoding() implements ContentEncoding {
        @Override
        public Set<String> ids() {
            return Set.of("gzip");
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
                    headers.add(HeaderValues.create(HeaderNames.CONTENT_ENCODING, "gzip"));
                    headers.remove(HeaderNames.CONTENT_LENGTH);
                }
            };
        }

        @Override
        public String name() {
            return "gzip";
        }

        @Override
        public String type() {
            return "gzip";
        }
    }
}
