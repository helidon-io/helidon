/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.helidon.webserver.staticcontent.StaticContentFeature.createService;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest
class StaticContentTest {
    @TempDir
    static Path tempDir;

    private final DirectClient testClient;

    StaticContentTest(DirectClient testClient) {
        this.testClient = testClient;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.contentEncoding(ContentEncodingContext.builder()
                .addContentEncoding(new TestEncoding())
                .build());
    }

    @SetUpRoute
    static void setupRouting(HttpRouting.Builder builder) throws Exception {
        Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);

        Path resource = tempDir.resolve("resource.txt");
        Path favicon = tempDir.resolve("favicon.ico");

        Files.writeString(resource, "Content");
        Files.writeString(tempDir.resolve("resource.txt.br"), "Brotli content");
        Files.writeString(tempDir.resolve("resource.txt.gz"), "Gzip content");
        Files.writeString(favicon, "Wrong icon text");
        Files.writeString(nested.resolve("resource.txt"), "Nested content");

        builder.register("/classpath", createService(ClasspathHandlerConfig.create("web")))
                .register("/singleclasspath", createService(ClasspathHandlerConfig.create("web/resource.txt")))
                .register("/path", createService(FileSystemHandlerConfig.create(tempDir)))
                .register("/path-disabled", createService(FileSystemHandlerConfig.builder()
                        .location(tempDir)
                        .preCompressedEnabled(false)
                        .build()))
                .register("/singlepath", createService(FileSystemHandlerConfig.create(resource)));
    }

    @Test
    void testClasspathFavicon() {
        try (Http1ClientResponse response = testClient.get("/classpath/favicon.ico")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "image/x-icon"));
        }
    }

    @Test
    void testClasspathNested() {
        try (Http1ClientResponse response = testClient.get("/classpath/nested/resource.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Nested content"));
        }
    }

    @Test
    void testClasspathSingleFile() {
        try (Http1ClientResponse response = testClient.get("/singleclasspath")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testClasspathSingleFilePreCompressed() {
        try (Http1ClientResponse response = testClient.get("/singleclasspath")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Brotli content\n"));
        }
    }

    @Test
    void testClasspathPreCompressed() {
        try (Http1ClientResponse response = testClient.get("/classpath/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Brotli content\n"));
        }
    }

    @Test
    void testFileSystemFavicon() {
        try (Http1ClientResponse response = testClient.get("/path/favicon.ico")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "image/x-icon"));
        }
    }

    @Test
    void testFileSystemPreCompressedPrefersBrotli() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br;q=1, gzip;q=0.8")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Brotli content"));
        }
    }

    @Test
    void testFileSystemPreCompressedUsesGzipSidecar() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Gzip content"));
        }
    }

    @Test
    void testFileSystemPreCompressedUsesIdentityWhenSidecarMissing() {
        try (Http1ClientResponse response = testClient.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Nested content"));
        }
    }

    @Test
    void testFileSystemPreCompressedRejectsWhenIdentityRejectedWithoutListenerContext() {
        try (Http1ClientResponse response = testClient.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testFileSystemPreCompressedUsesIdentityRangeWhenSidecarMissing() {
        try (Http1ClientResponse response = testClient.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip")
                .header(HeaderNames.RANGE, "bytes=0-3")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE));
            assertThat(response.as(String.class), is("Nest"));
        }
    }

    @Test
    void testFileSystemPreCompressedUsesSidecarRangeWhenSidecarExists() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .header(HeaderNames.RANGE, "bytes=2-5")
                .request()) {

            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_RANGE, "bytes 2-5/14"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("otli"));
        }
    }

    @Test
    void testFileSystemPreCompressedDisabled() {
        try (Http1ClientResponse response = testClient.get("/path-disabled/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testFileSystemPreCompressedRejectsQZero() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Content"));
        }
    }

    @Test
    void testFileSystemPreCompressedUsesWildcardSidecar() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "*, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("Brotli content"));
        }
    }

    @Test
    void testFileSystemPreCompressedNoAcceptableRepresentation() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testFileSystemPreCompressedUnknownCodingIsUnavailable() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "zstd, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        }
    }

    @Test
    void testFileSystemPreCompressedRejectsInvalidAcceptEncoding() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "g zip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
        }
    }

    @Test
    void testFileSystemPreCompressedRejectsInvalidAcceptEncodingParameter() {
        try (Http1ClientResponse response = testClient.get("/path/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip;level=1")
                .request()) {

            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
        }
    }

    @Test
    void testFileSystemNested() {
        try (Http1ClientResponse response = testClient.get("/path/nested/resource.txt")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Nested content"));
        }
    }

    @Test
    void testFileSystemSingleFile() {
        try (Http1ClientResponse response = testClient.get("/singlepath")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_TYPE, "text/plain"));
            assertThat(response.as(String.class), is("Content"));
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
