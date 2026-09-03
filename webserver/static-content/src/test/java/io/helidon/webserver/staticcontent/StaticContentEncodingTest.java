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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import io.helidon.common.testing.http.junit5.HttpHeaderMatcher;
import io.helidon.http.DirectHandler;
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
import io.helidon.webserver.http.DirectHandlers;
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
    private static final String CUSTOM_INTERNAL_ERROR = "custom-internal-error";
    private static final String CUSTOM_NOT_ACCEPTABLE = "custom-not-acceptable";

    @TempDir
    static Path tempDir;

    private final Http1Client client;

    StaticContentEncodingTest(Http1Client socketHttpClient) {
        this.client = socketHttpClient;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        DirectHandler errorHandler = (request, eventType, defaultStatus, responseHeaders, message) -> {
            if (request.path().equals("/failing-sidecar/resource.txt")
                    || request.path().equals("/failing-sidecar-close/resource.txt")) {
                return DirectHandler.TransportResponse.builder()
                        .status(Status.INTERNAL_SERVER_ERROR_500)
                        .entity(CUSTOM_INTERNAL_ERROR)
                        .build();
            }
            if (defaultStatus.code() == Status.NOT_ACCEPTABLE_406.code()) {
                return DirectHandler.TransportResponse.builder()
                        .status(Status.I_AM_A_TEAPOT_418)
                        .headers(responseHeaders)
                        .entity(CUSTOM_NOT_ACCEPTABLE)
                        .build();
            }
            return DirectHandler.defaultHandler()
                    .handle(request, eventType, defaultStatus, responseHeaders, message);
        };
        builder.contentEncoding(ContentEncodingContext.builder()
                                        .addContentEncoding(new TestEncoding())
                                        .build())
                .directHandlers(DirectHandlers.builder()
                                        .addHandler(DirectHandler.EventType.INTERNAL_ERROR, errorHandler)
                                        .addHandler(DirectHandler.EventType.OTHER, errorHandler)
                                        .build());
    }

    @SetUpRoute
    static void setupRouting(HttpRouting.Builder builder) throws IOException {
        Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);

        Files.writeString(tempDir.resolve("resource.txt"), "Content");
        Files.writeString(tempDir.resolve("resource.txt.br"), "Brotli content");
        Files.writeString(nested.resolve("resource.txt"), "Nested content");

        builder.any("/filtered-path/*", (request, response) -> {
                    response.streamFilter(network -> prefixingOutputStream(network, "filtered:"));
                    response.next();
                })
                .register("/filtered-path", createService(FileSystemHandlerConfig.builder()
                                                                   .location(tempDir)
                                                                   .preCompressedEnabled(true)
                                                                   .build()))
                .register("/path", createService(FileSystemHandlerConfig.builder()
                                                          .location(tempDir)
                                                          .preCompressedEnabled(true)
                                                          .build()))
                .register("/failing-sidecar", createService(ClasspathHandlerConfig.builder()
                                                                     .location("/web")
                                                                     .classLoader(new FailingSidecarClassLoader(StreamFailure.READ))
                                                                     .preCompressedEnabled(true)
                                                                     .preCompressedCrossOriginSourcingEnabled(true)
                                                                     .build()))
                .register("/failing-sidecar-close", createService(ClasspathHandlerConfig.builder()
                                                                     .location("/web")
                                                                     .classLoader(new FailingSidecarClassLoader(StreamFailure.CLOSE))
                                                                     .preCompressedEnabled(true)
                                                                     .preCompressedCrossOriginSourcingEnabled(true)
                                                                     .build()));
        builder.register("/path-disabled", createService(FileSystemHandlerConfig.builder()
                                                                 .location(tempDir)
                                                                 .preCompressedEnabled(false)
                                                                 .build()));
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
    void cachedShorterSidecarReplacementDoesNotReuseStaleMetadata() throws IOException {
        String resourceName = "shorter-replacement-http1.txt";
        String identity = "Identity content";
        String original = "Original Brotli content";
        String replacement = "New br";
        Path resource = tempDir.resolve(resourceName);
        Path sidecar = tempDir.resolve(resourceName + ".br");
        Files.writeString(resource, identity);
        Files.writeString(sidecar, original);

        String originalEtag;
        try (Http1ClientResponse response = client.get("/path/" + resourceName)
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH,
                                                                        Integer.toString(original.length())));
            originalEtag = response.headers().get(HeaderNames.ETAG).get();
            assertThat(response.as(String.class), is(original));
        }

        replaceFile(sidecar, replacement);

        try (Http1ClientResponse response = client.head("/path/" + resourceName)
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .header(HeaderNames.IF_NONE_MATCH, originalEtag)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH,
                                                                        Integer.toString(identity.length())));
            assertThat(response.entity().hasEntity(), is(false));
        }

        try (Http1ClientResponse response = client.get("/path/" + resourceName)
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_LENGTH,
                                                                        Integer.toString(replacement.length())));
            assertThat(response.as(String.class), is(replacement));
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
    void runtimeEncodingRunsAfterResponseFilter() {
        try (Http1ClientResponse response = client.get("/filtered-path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.as(String.class), is("runtime:filtered:Nested content"));
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
    void preCompressedDisabledUsesRuntimeEncoding() {
        try (Http1ClientResponse response = client.get("/path-disabled/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is("runtime:Content"));
        }
    }

    @Test
    void preCompressedDisabledHeadIgnoresRangeForRuntimeEncoding() {
        try (Http1ClientResponse response = client.head("/path-disabled/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0")
                .header(HeaderNames.RANGE, "bytes=0-3")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_LENGTH));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_RANGE));
        }
    }

    @Test
    void preCompressedDisabledGetIgnoresRangeForRuntimeEncoding() {
        try (Http1ClientResponse response = client.get("/path-disabled/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0")
                .header(HeaderNames.RANGE, "bytes=0-3")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_RANGE));
            assertThat(response.as(String.class), is("runtime:Content"));
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
    void noAcceptableRepresentationUsesConfiguredHandler() {
        try (Http1ClientResponse response = client.get("/path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.as(String.class), is(CUSTOM_NOT_ACCEPTABLE));
        }
    }

    @Test
    void sidecarTransferFailureClearsSelectedRepresentation() {
        try (Http1ClientResponse response = client.get("/failing-sidecar/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.ETAG));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.LAST_MODIFIED));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is(CUSTOM_INTERNAL_ERROR));
        }
    }

    @Test
    void sidecarCloseFailureClearsSelectedRepresentation() {
        try (Http1ClientResponse response = client.get("/failing-sidecar-close/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.ETAG));
            assertThat(response.headers(), HttpHeaderMatcher.noHeader(HeaderNames.LAST_MODIFIED));
            assertThat(response.headers(), HttpHeaderMatcher.hasHeader(HeaderNames.VARY,
                                                                       HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is(CUSTOM_INTERNAL_ERROR));
        }
    }

    private static void replaceFile(Path target, String content) throws IOException {
        Path replacement = target.resolveSibling(target.getFileName() + ".replacement");
        Files.writeString(replacement, content);
        try {
            Files.move(replacement,
                       target,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(replacement, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class FailingSidecarClassLoader extends ClassLoader {
        private final URL identity;
        private final URL sidecar;

        private FailingSidecarClassLoader(StreamFailure failure) {
            super(null);
            this.identity = resourceUrl("web/resource.txt", "Content", StreamFailure.NONE);
            this.sidecar = resourceUrl("web/resource.txt.br", CUSTOM_INTERNAL_ERROR, failure);
        }

        @Override
        public URL getResource(String name) {
            return switch (name) {
            case "web/resource.txt" -> identity;
            case "web/resource.txt.br" -> sidecar;
            default -> null;
            };
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            URL resource = getResource(name);
            return resource == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(resource));
        }

        private static URL resourceUrl(String name, String content, StreamFailure failure) {
            try {
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                return new URL(null, "test:/" + name, new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL url) {
                        return new URLConnection(url) {
                            @Override
                            public void connect() {
                            }

                            @Override
                            public long getContentLengthLong() {
                                return bytes.length;
                            }

                            @Override
                            public long getLastModified() {
                                return 1_700_000_000_000L;
                            }

                            @Override
                            public InputStream getInputStream() {
                                return switch (failure) {
                                case NONE -> new ByteArrayInputStream(bytes);
                                case READ -> new InputStream() {
                                    @Override
                                    public int read() throws IOException {
                                        throw new IOException("Sidecar transfer failed");
                                    }
                                };
                                case CLOSE -> new InputStream() {
                                    @Override
                                    public int read() {
                                        return -1;
                                    }

                                    @Override
                                    public void close() throws IOException {
                                        throw new IOException("Sidecar close failed");
                                    }
                                };
                                };
                            }
                        };
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private enum StreamFailure {
        NONE,
        READ,
        CLOSE
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
                    return prefixingOutputStream(network, "runtime:");
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

    private static OutputStream prefixingOutputStream(OutputStream network, String prefix) {
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
                    network.write(prefix.getBytes(StandardCharsets.UTF_8));
                    prefixWritten = true;
                }
            }
        };
    }
}
