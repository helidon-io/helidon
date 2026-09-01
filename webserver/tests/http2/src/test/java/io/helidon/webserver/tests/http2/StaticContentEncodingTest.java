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
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.staticcontent.ClasspathHandlerConfig;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class StaticContentEncodingTest {
    private static final String CUSTOM_BAD_REQUEST = "custom-bad";
    private static final String CUSTOM_INTERNAL_ERROR = "custom-internal-error";
    private static final String BEFORE_SEND_FAILURE = "before-send-failure";

    @TempDir
    static Path tempDir;

    private final Http2Client client;

    StaticContentEncodingTest(Http2Client client) {
        this.client = client;
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
            if (request.path().equals("/custom-bad-request")
                    || request.path().equals("/custom-internal-error")
                    || request.path().equals("/explicit-stream-error")
                    || request.path().equals("/stream-error")) {
                return DirectHandler.TransportResponse.builder()
                        .status(Status.I_AM_A_TEAPOT_418)
                        .entity(CUSTOM_BAD_REQUEST)
                        .build();
            }
            return DirectHandler.defaultHandler()
                    .handle(request, eventType, defaultStatus, responseHeaders, message);
        };
        builder.contentEncoding(ContentEncodingContext.builder()
                                        .addContentEncoding(new TestEncoding())
                                        .build())
                .directHandlers(DirectHandlers.builder()
                                        .addHandler(DirectHandler.EventType.BAD_REQUEST, errorHandler)
                                        .addHandler(DirectHandler.EventType.INTERNAL_ERROR, errorHandler)
                                        .addHandler(DirectHandler.EventType.OTHER, errorHandler)
                                        .build());
    }

    @SetUpRoute
    static void setupRouting(HttpRouting.Builder builder) throws IOException {
        Path nested = tempDir.resolve("nested");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("resource.txt"), "Nested content");
        Files.writeString(nested.resolve("resource.txt.br"), "Pre-compressed content");

        builder.error(BeforeSendFailure.class,
                      (request, response, failure) -> response.status(Status.I_AM_A_TEAPOT_418)
                              .send(failure.getMessage()));
        builder.any("/before-send-failure/*", (request, response) -> {
                    AtomicBoolean fail = new AtomicBoolean(true);
                    response.beforeSend(() -> {
                        if (fail.getAndSet(false)) {
                            throw new BeforeSendFailure();
                        }
                    });
                    response.next();
                })
                .register("/before-send-failure",
                          StaticContentFeature.createService(FileSystemHandlerConfig.builder()
                                                                     .location(tempDir)
                                                                     .preCompressedEnabled(true)
                                                                     .build()))
                .any("/filtered-path/*", (request, response) -> {
                    response.streamFilter(network -> prefixingOutputStream(network, "filtered:"));
                    response.next();
                })
                .register("/filtered-path", StaticContentFeature.createService(FileSystemHandlerConfig.builder()
                                                                                        .location(tempDir)
                                                                                        .preCompressedEnabled(true)
                                                                                        .build()))
                .register("/path", StaticContentFeature.createService(FileSystemHandlerConfig.builder()
                                                                               .location(tempDir)
                                                                               .preCompressedEnabled(true)
                                                                               .build()))
                .register("/path-disabled",
                          StaticContentFeature.createService(FileSystemHandlerConfig.builder()
                                                                     .location(tempDir)
                                                                     .preCompressedEnabled(false)
                                                                     .build()))
                .register("/failing-sidecar", StaticContentFeature.createService(ClasspathHandlerConfig.builder()
                                                                                          .location("/web")
                                                                                          .classLoader(
                                                                                                  new FailingSidecarClassLoader(
                                                                                                          StreamFailure.READ))
                                                                                          .preCompressedEnabled(true)
                                                                                          .preCompressedCrossOriginSourcingEnabled(
                                                                                                  true)
                                                                                          .build()))
                .register("/failing-sidecar-close",
                          StaticContentFeature.createService(ClasspathHandlerConfig.builder()
                                                                                          .location("/web")
                                                                                          .classLoader(
                                                                                                  new FailingSidecarClassLoader(
                                                                                                          StreamFailure.CLOSE))
                                                                                          .preCompressedEnabled(true)
                                                                                          .preCompressedCrossOriginSourcingEnabled(
                                                                                                  true)
                                                                                          .build()));
        builder.any("/filtered", (request, response) -> {
            response.streamFilter(network -> prefixingOutputStream(network, "filtered:"));
            if (request.prologue().method() == Method.HEAD) {
                response.send();
            } else {
                response.send("abc");
            }
        });
        builder.get("/custom-bad-request", (req, res) -> res.send("OK"));
        builder.any("/custom-internal-error", (req, res) -> {
            throw new IllegalStateException("Internal error in routing");
        });
        builder.get("/explicit-stream-error", (_, res) -> {
            res.header(HeaderNames.CONTENT_LENGTH, "1");
            res.header(HeaderNames.CONTENT_RANGE, "bytes 0-0/1");
            res.header(HeaderNames.ETAG, "\"stale\"");
            res.header(HeaderNames.ACCEPT_RANGES, "bytes");
            res.header(HeaderNames.CACHE_CONTROL, "no-store");
            res.header(HeaderNames.VARY, "Origin");
            res.contentEncoder(new TestEncoding().encoder());
            res.outputStream();
            throw new IllegalStateException("Error after selecting explicit content encoder");
        });
        builder.get("/stream-error", (_, res) -> {
            res.header(HeaderNames.CONTENT_LENGTH, "1");
            res.outputStream();
            throw new IllegalStateException("Error after selecting response stream");
        });
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
        builder.any("/explicit-reconfigured", (req, res) -> {
            res.contentEncoder(new TestEncoding("first", "first:").encoder());
            res.contentEncoder(new TestEncoding("second", "second:").encoder());
            if (req.prologue().method() == Method.HEAD) {
                res.contentLength("second:abc".getBytes(StandardCharsets.UTF_8).length);
                res.send();
            } else {
                res.send("abc");
            }
        });
        builder.any("/explicit-before-send", (req, res) -> {
            res.beforeSend(() -> res.contentEncoder(new TestEncoding("second", "second:").encoder()));
            if (req.prologue().method() == Method.HEAD) {
                res.contentLength("second:abc".getBytes(StandardCharsets.UTF_8).length);
                res.send();
            } else {
                res.send("abc");
            }
        });
        builder.get("/explicit-reset-stream", (req, res) -> {
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
        });
        builder.get("/explicit-empty", (req, res) -> res.contentEncoder(new TestEncoding().encoder()).send());
        builder.head("/explicit-empty", (req, res) -> res.contentEncoder(new TestEncoding().encoder()).send());
        builder.get("/explicit-empty-filtered", (req, res) -> {
            res.streamFilter(stream -> stream);
            res.contentEncoder(new TestEncoding().encoder());
            res.send();
        });
        builder.get("/automatic-empty-filtered", (req, res) -> {
            res.streamFilter(stream -> stream);
            res.send();
        });
        builder.get("/explicit-empty-no-content", (req, res) -> {
            res.contentEncoder(new TestEncoding().encoder());
            res.status(Status.NO_CONTENT_204);
            res.send();
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
    void preCompressedDisabledGetIgnoresRangeForRuntimeEncoding() {
        try (Http2ClientResponse response = client.get("/path-disabled/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "test, identity;q=0")
                .header(HeaderNames.RANGE, "bytes=0-3")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_RANGE));
            assertThat(response.as(String.class), is("runtime:Nested content"));
        }
    }

    @Test
    void runtimeEncodingRunsAfterResponseFilter() {
        try (Http2ClientResponse response = client.get("/filtered-path/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "test, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.as(String.class), is("runtime:filtered:Nested content"));
        }
    }

    @Test
    void responseFilterHeadOmitsImplicitContentLength() {
        try (Http2ClientResponse response = client.get("/filtered").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("filtered:abc"));
        }

        try (Http2ClientResponse response = client.head("/filtered").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_LENGTH));
            assertThat(response.entity().hasEntity(), is(false));
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
    void invalidAcceptEncodingUsesBadRequestHandler() {
        try (Http2ClientResponse response = client.get("/custom-bad-request")
                .header(HeaderNames.ACCEPT_ENCODING, "g zip")
                .request()) {

            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.as(String.class), is(CUSTOM_BAD_REQUEST));
        }
    }

    @Test
    void sidecarTransferFailureClearsSelectedRepresentation() {
        try (Http2ClientResponse response = client.get("/failing-sidecar/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), noHeader(HeaderNames.ETAG));
            assertThat(response.headers(), noHeader(HeaderNames.LAST_MODIFIED));
            assertThat(response.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is(CUSTOM_INTERNAL_ERROR));
        }
    }

    @Test
    void sidecarCloseFailureClearsSelectedRepresentation() {
        try (Http2ClientResponse response = client.get("/failing-sidecar-close/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), noHeader(HeaderNames.ETAG));
            assertThat(response.headers(), noHeader(HeaderNames.LAST_MODIFIED));
            assertThat(response.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.as(String.class), is(CUSTOM_INTERNAL_ERROR));
        }
    }

    @Test
    void beforeSendFailurePreservesOriginalException() {
        try (Http2ClientResponse response = client.get("/before-send-failure/nested/resource.txt")
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {

            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.as(String.class), is(BEFORE_SEND_FAILURE));
        }
    }

    @Test
    void errorResponseUsesSelectedExplicitContentEncoder() {
        String encodedEntity = "runtime:" + CUSTOM_BAD_REQUEST;
        String encodedLength = String.valueOf(encodedEntity.getBytes(StandardCharsets.UTF_8).length);

        try (Http2ClientResponse response = client.get("/explicit-stream-error")
                .header(HeaderNames.ACCEPT_ENCODING, "g zip")
                .request()) {

            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_RANGE));
            assertThat(response.headers(), noHeader(HeaderNames.ETAG));
            assertThat(response.headers(), noHeader(HeaderNames.ACCEPT_RANGES));
            assertThat(response.headers(), hasHeader(HeaderNames.CACHE_CONTROL, "no-store"));
            assertThat(response.headers().containsToken(HeaderValues.create(HeaderNames.VARY, "Origin")), is(true));
            assertThat(response.headers().containsToken(HeaderValues.create(HeaderNames.VARY,
                                                                             HeaderNames.ACCEPT_ENCODING_NAME)),
                       is(false));
            assertThat(response.as(String.class), is(encodedEntity));
        }
    }

    @Test
    void errorResponseReplacesStaleContentLength() {
        String contentLength = String.valueOf(CUSTOM_BAD_REQUEST.getBytes(StandardCharsets.UTF_8).length);

        try (Http2ClientResponse response = client.get("/stream-error").request()) {
            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, contentLength));
            assertThat(response.as(String.class), is(CUSTOM_BAD_REQUEST));
        }
    }

    @Test
    void directHandlerHeadPreservesValidContentEncoding() {
        String encodedEntity = "runtime:" + CUSTOM_BAD_REQUEST;
        String encodedLength = String.valueOf(encodedEntity.getBytes(StandardCharsets.UTF_8).length);

        try (Http2ClientResponse response = client.get("/custom-internal-error")
                .header(HeaderNames.ACCEPT_ENCODING, "test, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.as(String.class), is(encodedEntity));
        }

        try (Http2ClientResponse response = client.head("/custom-internal-error")
                .header(HeaderNames.ACCEPT_ENCODING, "test, identity;q=0")
                .request()) {

            assertThat(response.status(), is(Status.I_AM_A_TEAPOT_418));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.entity().hasEntity(), is(false));
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
    void cachedLongerSidecarReplacementDoesNotReuseStaleMetadata() throws IOException {
        String resourceName = "longer-replacement-http2.txt";
        String identity = "Identity";
        String original = "Old br";
        String replacement = "New Brotli content is longer";
        Path resource = tempDir.resolve(resourceName);
        Path sidecar = tempDir.resolve(resourceName + ".br");
        Files.writeString(resource, identity);
        Files.writeString(sidecar, original);

        try (Http2ClientResponse response = client.get("/path/" + resourceName)
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, Integer.toString(original.length())));
            assertThat(response.as(String.class), is(original));
        }

        replaceFile(sidecar, replacement);

        try (Http2ClientResponse response = client.get("/path/" + resourceName)
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .header(HeaderNames.RANGE, "bytes=0-2")
                .request()) {
            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, "3"));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_RANGE,
                                                      "bytes 0-2/" + identity.length()));
            assertThat(response.as(String.class), is("Ide"));
        }

        try (Http2ClientResponse response = client.get("/path/" + resourceName)
                .header(HeaderNames.ACCEPT_ENCODING, "br")
                .header(HeaderNames.RANGE, "bytes=0-2")
                .request()) {
            assertThat(response.status(), is(Status.PARTIAL_CONTENT_206));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, "3"));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_RANGE,
                                                      "bytes 0-2/" + replacement.length()));
            assertThat(response.as(String.class), is("New"));
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

    @Test
    void lastExplicitContentEncoderWins() {
        String encodedEntity = "second:abc";
        String encodedLength = String.valueOf(encodedEntity.getBytes(StandardCharsets.UTF_8).length);

        try (Http2ClientResponse response = client.get("/explicit-reconfigured").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), is(List.of("second")));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.headers(), noHeader(HeaderNames.VARY));
            assertThat(response.as(String.class), is(encodedEntity));
        }

        try (Http2ClientResponse response = client.head("/explicit-reconfigured").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), is(List.of("second")));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.headers(), noHeader(HeaderNames.VARY));
            assertThat(response.entity().hasEntity(), is(false));
        }
    }

    @Test
    void beforeSendCanConfigureContentEncoder() {
        String encodedEntity = "second:abc";
        String encodedLength = String.valueOf(encodedEntity.getBytes(StandardCharsets.UTF_8).length);

        try (Http2ClientResponse response = client.get("/explicit-before-send").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), is(List.of("second")));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.as(String.class), is(encodedEntity));
        }

        try (Http2ClientResponse response = client.head("/explicit-before-send").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), is(List.of("second")));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.entity().hasEntity(), is(false));
        }
    }

    @Test
    void resetStreamPreservesSelectedExplicitContentEncoder() {
        try (Http2ClientResponse response = client.get("/explicit-reset-stream").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().get(HeaderNames.CONTENT_ENCODING).allValues(), is(List.of("second")));
            assertThat(response.as(String.class), is("second:abc"));
        }
    }

    @Test
    void explicitContentEncoderEncodesEmptyResponse() {
        String encodedEntity = "runtime:";
        String encodedLength = String.valueOf(encodedEntity.getBytes(StandardCharsets.UTF_8).length);

        try (Http2ClientResponse response = client.get("/explicit-empty").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.as(String.class), is(encodedEntity));
        }

        try (Http2ClientResponse response = client.head("/explicit-empty").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_LENGTH, encodedLength));
            assertThat(response.entity().hasEntity(), is(false));
        }
    }

    @Test
    void explicitContentEncoderEncodesFilteredEmptyResponse() {
        String encodedEntity = "runtime:";

        try (Http2ClientResponse response = client.get("/explicit-empty-filtered").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.headers(), noHeader(HeaderNames.VARY));
            assertThat(response.as(String.class), is(encodedEntity));
        }
    }

    @Test
    void automaticContentEncoderSkipsFilteredEmptyResponse() {
        try (Http2ClientResponse response = client.get("/automatic-empty-filtered")
                .header(HeaderNames.ACCEPT_ENCODING, "test")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
            assertThat(response.headers(), noHeader(HeaderNames.VARY));
            assertThat(response.entity().hasEntity(), is(false));
        }
    }

    @Test
    void explicitContentEncoderDoesNotEncodeNoContentResponse() {
        try (Http2ClientResponse response = client.get("/explicit-empty-no-content").request()) {
            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat(response.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "test"));
            assertThat(response.entity().hasEntity(), is(false));
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

    private static final class BeforeSendFailure extends RuntimeException {
        private BeforeSendFailure() {
            super(BEFORE_SEND_FAILURE);
        }
    }

    private record TestEncoding(String id, String prefix) implements ContentEncoding {
        private TestEncoding() {
            this("test", "runtime:");
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
                    return prefixingOutputStream(network, prefix);
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
