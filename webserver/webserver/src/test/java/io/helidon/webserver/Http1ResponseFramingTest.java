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
package io.helidon.webserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Multi;
import io.helidon.webserver.utils.SocketHttpClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.startsWith;

class Http1ResponseFramingTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final CompletableFuture<UnsupportedOperationException> COMMITTED_ERROR = new CompletableFuture<>();
    private static final CompletableFuture<Throwable> INCOMPLETE_RESPONSE = new CompletableFuture<>();
    private static final CompletableFuture<Map<String, List<String>>> CHAINED_HEADERS = new CompletableFuture<>();
    private static final IllegalStateException PUBLISHER_FAILURE = new IllegalStateException("publisher failed");
    private static final CompletableFuture<Throwable> FAILED_PUBLISHER_RESPONSE = new CompletableFuture<>();

    private static WebServer server;
    private static byte[] gzipBody;

    @BeforeAll
    static void start() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write("compressed response".getBytes(StandardCharsets.UTF_8));
        }
        gzipBody = bytes.toByteArray();
        server = WebServer.builder()
                .port(0)
                .routing(routing -> routing
                        .get("/both", (request, response) -> {
                            response.headers().contentLength(0);
                            response.addHeader(Http.Header.TRANSFER_ENCODING, "chunked");
                            response.send("OK");
                        })
                        .get("/failure", (request, response) -> {
                            response.headers().contentLength(0);
                            response.addHeader(Http.Header.TRANSFER_ENCODING, "chunked");
                            response.addHeader(Http.Header.TRAILER, "X-Old");
                            response.addHeader(Http.Header.CONTENT_TYPE, "application/json");
                            response.addHeader(Http.Header.CONTENT_ENCODING, "gzip");
                            response.addHeader(Http.Header.ETAG, "\"original\"");
                            response.addHeader("X-Correlation-Id", "retained");
                            throw new IllegalStateException("response failed");
                        })
                        .get("/custom-failure", (request, response) -> {
                            response.headers().contentLength(0);
                            response.addHeader(Http.Header.CONTENT_ENCODING, "gzip");
                            response.addHeader("X-Correlation-Id", "retained");
                            throw new IllegalArgumentException("replaced");
                        })
                        .get("/headers-only-failure", (request, response) -> {
                            response.headers().send();
                            throw new IllegalStateException("response failed after headers");
                        })
                        .get("/headers-length-failure", (request, response) -> {
                            response.whenSent().whenComplete((sent, failure) -> INCOMPLETE_RESPONSE.complete(failure));
                            response.headers().contentLength(10);
                            response.headers().send();
                            throw new IllegalStateException("response failed after fixed-length headers");
                        })
                        .get("/headers-zero-failure", (request, response) -> {
                            response.headers().contentLength(0);
                            response.headers().send();
                            throw new IllegalStateException("response failed after zero-length headers");
                        })
                        .get("/headers-length-publisher-failure", (request, response) -> {
                            response.whenSent().whenComplete((sent, failure) -> FAILED_PUBLISHER_RESPONSE.complete(failure));
                            response.headers().contentLength(10);
                            response.headers().send();
                            response.send(Multi.error(PUBLISHER_FAILURE), false);
                        })
                        .head("/headers-head-failure", (request, response) -> {
                            response.headers().contentLength(10);
                            response.headers().send();
                            throw new IllegalStateException("response failed after HEAD headers");
                        })
                        .get("/headers-not-modified-failure", (request, response) -> {
                            response.status(Http.Status.NOT_MODIFIED_304);
                            response.headers().contentLength(10);
                            response.headers().send();
                            throw new IllegalStateException("response failed after 304 headers");
                        })
                        .get("/headers-chunked-failure", (request, response) -> {
                            response.headers().contentLength(10);
                            response.addHeader(Http.Header.TRANSFER_ENCODING, "chunked");
                            response.headers().send();
                            throw new IllegalStateException("response failed after chunked headers");
                        })
                        .get("/chained-failure", (request, response) -> {
                            response.addHeader("X-Correlation-Id", "retained");
                            throw new ChainedFailure();
                        })
                        .get("/committed-failure", (request, response) ->
                                response.send("OK").thenRun(() -> request.next(new UnsupportedOperationException("committed"))))
                        .get("/completed-default-failure", (request, response) ->
                                response.send("OK").thenRun(() -> request.next(new IllegalStateException("committed"))))
                        .get("/unicode", (request, response) -> {
                            response.headers().contentLength(2);
                            response.send("é");
                        })
                        .get("/plain", (request, response) -> response.send("OK"))
                        .get("/transfer-gzip-single", (request, response) -> {
                            response.addHeader(Http.Header.TRANSFER_ENCODING, "gzip");
                            response.send(gzipBody);
                        })
                        .get("/transfer-gzip-multiple", (request, response) -> {
                            response.addHeader(Http.Header.TRANSFER_ENCODING, "gzip");
                            int middle = gzipBody.length / 2;
                            response.send(Multi.just(DataChunk.create(Arrays.copyOfRange(gzipBody, 0, middle)),
                                                     DataChunk.create(Arrays.copyOfRange(gzipBody, middle, gzipBody.length))));
                        })
                        .error(IllegalArgumentException.class, (request, response, failure) ->
                                response.status(Http.Status.BAD_REQUEST_400).send(failure.getMessage()))
                        .error(UnsupportedOperationException.class, (request, response, failure) ->
                                COMMITTED_ERROR.complete(failure))
                        .error(ChainedFailure.class, (request, response, failure) -> {
                            response.status(Http.Status.ACCEPTED_202);
                            response.headers().contentLength(0);
                            response.addHeader(Http.Header.CONTENT_TYPE, "application/json");
                            response.addHeader(Http.Header.CONTENT_ENCODING, "gzip");
                            response.addHeader(Http.Header.ETAG, "\"first-handler\"");
                            response.addHeader("X-Error-Handler", "retained");
                            request.next(failure);
                        })
                        .error(ChainedFailure.class, (request, response, failure) -> {
                            CHAINED_HEADERS.complete(response.headers().toMap());
                            response.send("handled");
                        }))
                .build()
                .start()
                .await(TIMEOUT);
    }

    @AfterAll
    static void stop() {
        if (server != null) {
            server.shutdown().await(TIMEOUT);
        }
    }

    @Test
    void chunkedResponseDoesNotAlsoSendContentLength() throws Exception {
        try (SocketHttpClient client = new SocketHttpClient(server)) {
            client.request(Http.Method.GET, "/both", null);
            String response = client.receive();
            String headers = response.substring(0, response.indexOf("\n\n")).toLowerCase(Locale.ROOT);

            assertThat(headers, containsString("transfer-encoding: chunked"));
            assertThat(headers, not(containsString("content-length:")));
            assertThat(SocketHttpClient.entityFromResponse(response, true), is("2\nOK\n0\n\n"));

            client.request(Http.Method.GET, "/plain", null);
            assertThat(client.receive(), containsString("HTTP/1.1 200 OK"));
        }
    }

    @Test
    void failedResponseDiscardsStaleEntityHeaders() throws Exception {
        try (SocketHttpClient client = new SocketHttpClient(server)) {
            client.request(Http.Method.GET, "/failure", null);
            String response = client.receive();
            String headers = response.substring(0, response.indexOf("\n\n")).toLowerCase(Locale.ROOT);

            assertThat(response, containsString("HTTP/1.1 500 Internal Server Error"));
            assertThat(response, containsString("Internal Server Error"));
            assertThat(headers, not(containsString("content-length: 0")));
            assertThat(headers, not(containsString("transfer-encoding:")));
            assertThat(headers, not(containsString("trailer:")));
            assertThat(headers, not(containsString("application/json")));
            assertThat(headers, not(containsString("content-encoding:")));
            assertThat(headers, not(containsString("etag:")));
            assertThat(headers, containsString("x-correlation-id: retained"));

            client.request(Http.Method.GET, "/plain", null);
            assertThat(client.receive(), containsString("HTTP/1.1 200 OK"));
        }
    }

    @Test
    void customErrorHandlerAlsoDiscardsStaleEntityHeaders() throws Exception {
        try (SocketHttpClient client = new SocketHttpClient(server)) {
            client.request(Http.Method.GET, "/custom-failure", null);
            String response = client.receive();
            String headers = response.substring(0, response.indexOf("\n\n")).toLowerCase(Locale.ROOT);

            assertThat(response, containsString("HTTP/1.1 400 Bad Request"));
            assertThat(response, containsString("replaced"));
            assertThat(headers, not(containsString("content-length: 0")));
            assertThat(headers, not(containsString("content-encoding:")));
            assertThat(headers, containsString("x-correlation-id: retained"));
        }
    }

    @Test
    void customErrorHandlerRunsAfterHeadersAreSent() throws Exception {
        try (SocketHttpClient client = new SocketHttpClient(server)) {
            client.request(Http.Method.GET, "/committed-failure", null);
            assertThat(client.receive(), containsString("HTTP/1.1 200 OK"));
            assertThat(COMMITTED_ERROR.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).getMessage(), is("committed"));
        }
    }

    @Test
    void defaultErrorHandlerCompletesResponseAfterHeadersAreSent() throws Exception {
        try (SocketHttpClient client = new SocketHttpClient(server)) {
            client.request(Http.Method.GET, "/headers-only-failure", null);
            assertThat(client.receive(), containsString("HTTP/1.1 200 OK"));
            client.request(Http.Method.GET, "/plain", null);
            assertThat(client.receive(), containsString("HTTP/1.1 200 OK"));
        }
    }

    @Test
    void emptyFixedLengthErrorFailsAndClosesBeforePipelinedResponse() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            socket.getOutputStream().write(("GET /headers-length-failure HTTP/1.1\r\nHost: localhost\r\n"
                    + "x-http2-stream-id: 3\r\n\r\n"
                    + "GET /plain HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                                                  .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            try {
                assertThat(socket.getInputStream().read(), is(-1));
            } catch (SocketException ignored) {
                // Closing with unread pipelined data can reset the connection instead of producing EOF.
            }
            Throwable failure = INCOMPLETE_RESPONSE.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(failure, instanceOf(CompletionException.class));
            assertThat(failure.getCause(), instanceOf(SocketClosedException.class));
        }
    }

    @Test
    void completedResponseIsNotAbortedByLaterError() throws Exception {
        try (SocketHttpClient client = new SocketHttpClient(server)) {
            client.request(Http.Method.GET, "/completed-default-failure", null);
            assertThat(client.receive(), containsString("\n\nOK"));
            client.request(Http.Method.GET, "/plain", null);
            assertThat(client.receive(), containsString("\n\nOK"));
        }
    }

    @Test
    void emptyFixedLengthPublisherFailurePreservesCauseAndCloses() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            socket.getOutputStream().write(("GET /headers-length-publisher-failure HTTP/1.1\r\n"
                    + "Host: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            assertThat(socket.getInputStream().readAllBytes(), is(new byte[0]));
            Throwable failure = FAILED_PUBLISHER_RESPONSE.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(failure, instanceOf(CompletionException.class));
            assertThat(failure.getCause(), sameInstance(PUBLISHER_FAILURE));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"zero", "head", "not-modified", "chunked"})
    void validEmptyErrorResponseKeepsNextResponseAligned(String framing) throws Exception {
        String method = framing.equals("head") ? "HEAD" : "GET";
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            socket.getOutputStream().write((method + " /headers-" + framing + "-failure HTTP/1.1\r\nHost: localhost\r\n\r\n"
                    + "GET /plain HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                                                  .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String wire = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            int headerEnd = wire.indexOf("\r\n\r\n");
            String headers = wire.substring(0, headerEnd).toLowerCase(Locale.ROOT);
            String remaining = wire.substring(headerEnd + 4);

            assertThat(wire, startsWith(framing.equals("not-modified") ? "HTTP/1.1 304 " : "HTTP/1.1 200 "));
            if (framing.equals("chunked")) {
                assertThat(headers, containsString("transfer-encoding: chunked"));
                assertThat(headers, not(containsString("content-length:")));
                assertThat(remaining, startsWith("0\r\n\r\nHTTP/1.1 200 OK\r\n"));
            } else {
                assertThat(headers, containsString("content-length: " + (framing.equals("zero") ? "0" : "10")));
                assertThat(remaining, startsWith("HTTP/1.1 200 OK\r\n"));
            }
            assertThat(remaining, containsString("\r\n\r\nOK"));
        }
    }

    @Test
    void chainedErrorHandlerResetsEntityHeadersAndPreservesOtherMetadata() throws Exception {
        try (SocketHttpClient client = new SocketHttpClient(server)) {
            client.request(Http.Method.GET, "/chained-failure", null);
            String response = client.receive();
            Map<String, List<String>> headers = CHAINED_HEADERS.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            assertThat(response, containsString("HTTP/1.1 202 Accepted"));
            assertThat(response, containsString("handled"));
            assertThat(headers, not(hasKey(Http.Header.CONTENT_LENGTH)));
            assertThat(headers, not(hasKey(Http.Header.CONTENT_TYPE)));
            assertThat(headers, not(hasKey(Http.Header.CONTENT_ENCODING)));
            assertThat(headers, not(hasKey(Http.Header.ETAG)));
            assertThat(headers, hasEntry("X-Correlation-Id", List.of("retained")));
            assertThat(headers, hasEntry("X-Error-Handler", List.of("retained")));
        }
    }

    @Test
    void fixedLengthUtf8ResponseKeepsNextResponseAligned() throws Exception {
        try (SocketHttpClient client = new SocketHttpClient(server)) {
            client.request(Http.Method.GET, "/unicode", null);
            assertThat(client.receive(), containsString("\n\né"));

            client.request(Http.Method.GET, "/plain", null);
            assertThat(client.receive(), containsString("HTTP/1.1 200 OK"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/transfer-gzip-single", "/transfer-gzip-multiple"})
    void nonChunkedTransferCodingIsRetainedAndClosed(String path) throws Exception {
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n"
                    + "Connection: keep-alive\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            byte[] wire = socket.getInputStream().readAllBytes();
            String wireText = new String(wire, StandardCharsets.ISO_8859_1);
            int headerEnd = wireText.indexOf("\r\n\r\n");
            String headers = wireText.substring(0, headerEnd).toLowerCase(Locale.ROOT);

            assertThat(headers, containsString("transfer-encoding: gzip"));
            assertThat(headers, containsString("connection: close"));
            assertThat(headers, not(containsString("content-length:")));
            assertThat(headers, not(containsString("chunked")));
            byte[] body = Arrays.copyOfRange(wire, headerEnd + 4, wire.length);
            assertThat(body, is(gzipBody));
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
                assertThat(new String(gzip.readAllBytes(), StandardCharsets.UTF_8), is("compressed response"));
            }
        }
    }

    private static class ChainedFailure extends RuntimeException {
    }
}
