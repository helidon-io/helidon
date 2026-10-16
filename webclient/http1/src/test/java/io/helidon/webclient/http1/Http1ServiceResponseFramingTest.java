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

package io.helidon.webclient.http1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webclient.api.WebClientServiceResponse;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(15)
class Http1ServiceResponseFramingTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String WIRE_ENTITY = "data";
    private static final String BUFFERED_ENTITY = "buffered " + WIRE_ENTITY;
    private static final String NEXT_ENTITY = "next response";

    @ParameterizedTest(name = "trailers={0}, consume={1}")
    @MethodSource("bufferedResponses")
    void bufferingServicePreservesWireFraming(String trailers, boolean consume) throws Exception {
        Map<String, Completion> completions = new LinkedHashMap<>();
        AtomicInteger connections = new AtomicInteger();
        AtomicInteger decoderCloses = new AtomicInteger();
        boolean advertised = trailers.equals("advertised");
        boolean gzip = trailers.startsWith("gzip");
        boolean closeInService = trailers.equals("gzip-closed-by-service");
        String trailerSection = trailers.equals("empty") ? "\r\n" : "Checksum: valid\r\n\r\n";
        try (TestServer server = new TestServer(listener -> {
            try (Socket first = accept(listener)) {
                connections.incrementAndGet();
                assertThat(readHead(first.getInputStream()), startsWith("GET /buffered HTTP/1.1\r\n"));
                send(first, chunkedResponse(advertised, trailerSection, gzip));
                String next = readHead(first.getInputStream());
                if (!next.isEmpty()) {
                    assertThat(next, startsWith("GET /next HTTP/1.1\r\n"));
                    send(first, nextResponse());
                    return;
                }
                assertThat("fully consumed buffered response should retain its connection", consume, is(false));
            }
            try (Socket next = accept(listener)) {
                connections.incrementAndGet();
                assertThat(readHead(next.getInputStream()), startsWith("GET /next HTTP/1.1\r\n"));
                send(next, nextResponse());
            }
        })) {
            Http1Client client = client(server, completions, decoderCloses, closeInService);
            try {
                try (Http1ClientResponse response = client.get("/buffered").request()) {
                    assertBufferedHeaders(response);
                    if (consume) {
                        assertThat(response.entity().as(String.class), is(BUFFERED_ENTITY));
                        assertThat("consumer EOF must leave the original decoder closed exactly once",
                                   decoderCloses.get(), is(gzip ? 1 : 0));
                        if (!trailers.equals("empty")) {
                            assertThat(response.trailers().get(HeaderNames.create("Checksum")).get(), is("valid"));
                        } else {
                            assertThat(response.trailers().size(), is(0));
                        }
                    }
                }
                assertCompleted(completions, "/buffered");
                assertThat("outer response close must leave the original decoder closed exactly once",
                           decoderCloses.get(), is(gzip ? 1 : 0));
                assertNextResponse(client);
                server.await();
                if (consume) {
                    assertThat("next request should reuse the fully framed connection", connections.get(), is(1));
                }
                assertCompleted(completions, "/buffered");
                assertCompleted(completions, "/next");
                assertThat("a later response must not close the previous decoder again",
                           decoderCloses.get(), is(gzip ? 1 : 0));
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest(name = "trailers={0}")
    @MethodSource("invalidTrailerSections")
    void bufferingServiceCannotHideInvalidWireFraming(String description, String trailerSection) throws Exception {
        Map<String, Completion> completions = new LinkedHashMap<>();
        CompletableFuture<Void> firstClosed = new CompletableFuture<>();
        try (TestServer server = new TestServer(listener -> {
            try (Socket first = accept(listener)) {
                assertThat(readHead(first.getInputStream()), startsWith("GET /buffered HTTP/1.1\r\n"));
                send(first, chunkedResponse(false, trailerSection, false));
                first.shutdownOutput();
                assertThat("invalid framing must retire the connection without another request",
                           first.getInputStream().read(), is(-1));
                firstClosed.complete(null);
            }
            try (Socket next = accept(listener)) {
                assertThat(readHead(next.getInputStream()), startsWith("GET /next HTTP/1.1\r\n"));
                send(next, nextResponse());
            }
        })) {
            Http1Client client = client(server, completions);
            try {
                try (Http1ClientResponse response = client.get("/buffered").request()) {
                    assertBufferedHeaders(response);
                    IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                                  () -> response.entity().as(String.class));
                    assertThat(description, failure.getMessage(), containsString("chunked transfer coding is incomplete"));
                }
                firstClosed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                assertCompleted(completions, "/buffered");
                assertNextResponse(client);
                server.await();
                assertCompleted(completions, "/buffered");
                assertCompleted(completions, "/next");
            } finally {
                client.closeResource();
            }
        }
    }

    @ParameterizedTest(name = "advertised={0}, partial={1}, consume={2}, close={3}")
    @MethodSource("discardedResponses")
    void discardingServicePreservesFallbackBody(boolean advertised,
                                               boolean partial,
                                               boolean consume,
                                               boolean close) throws Exception {
        Map<String, Completion> completions = new LinkedHashMap<>();
        AtomicInteger connections = new AtomicInteger();
        try (TestServer server = new TestServer(listener -> {
            try (Socket first = accept(listener)) {
                connections.incrementAndGet();
                assertThat(readHead(first.getInputStream()), startsWith("GET /fallback HTTP/1.1\r\n"));
                send(first, "HTTP/1.1 503 Service Unavailable\r\nTransfer-Encoding: chunked\r\n"
                        + (advertised ? "Trailer: Checksum\r\n" : "")
                        + "\r\n4\r\ndata\r\n0\r\nChecksum: valid\r\n\r\n");
                assertThat("discarded response must retire the connection without another request",
                           first.getInputStream().read(), is(-1));
            }
            try (Socket next = accept(listener)) {
                connections.incrementAndGet();
                assertThat(readHead(next.getInputStream()), startsWith("GET /next HTTP/1.1\r\n"));
                send(next, nextResponse());
            }
        })) {
            Http1Client client = Http1Client.builder()
                    .baseUri("http://127.0.0.1:" + server.listener.getLocalPort())
                    .readTimeout(TIMEOUT)
                    .addService((chain, request) -> {
                        AtomicInteger calls = new AtomicInteger();
                        CompletableFuture<Void> completion = request.whenComplete().<Void>handle((ignored1, ignored2) -> {
                            calls.incrementAndGet();
                            return null;
                        }).toCompletableFuture();
                        String path = request.uri().path().path();
                        completions.put(path, new Completion(completion, calls));
                        WebClientServiceResponse response = chain.proceed(request);
                        if (!path.equals("/fallback")) {
                            return response;
                        }
                        assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
                        try (InputStream original = response.inputStream().orElseThrow()) {
                            if (partial) {
                                assertThat(original.read(), is((int) 'd'));
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        WritableHeaders<?> headers = WritableHeaders.create(response.headers());
                        headers.remove(HeaderNames.TRANSFER_ENCODING);
                        headers.remove(HeaderNames.TRAILER);
                        headers.set(HeaderNames.CONTENT_LENGTH, 2);
                        if (close) {
                            headers.set(HeaderNames.CONNECTION, "close");
                        }
                        return WebClientServiceResponse.builder(response)
                                .status(Status.OK_200)
                                .headers(ClientResponseHeaders.create(headers))
                                .inputStream(new ByteArrayInputStream(new byte[] {'O', 'K'}))
                                .build();
                    })
                    .build();
            try {
                try (Http1ClientResponse response = client.get("/fallback").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.headers().contentLength().orElseThrow(), is(2L));
                    if (consume) {
                        assertThat(response.entity().as(String.class), is("OK"));
                        assertThat("discarded upstream trailers must not be exposed", response.trailers().size(), is(0));
                    }
                }
                assertCompleted(completions, "/fallback");
                assertNextResponse(client);
                server.await();
                assertThat("next request must use a new connection", connections.get(), is(2));
                assertCompleted(completions, "/fallback");
                assertCompleted(completions, "/next");
            } finally {
                client.closeResource();
            }
        }
    }

    private static Stream<Arguments> bufferedResponses() {
        return Stream.concat(Stream.of("empty", "unadvertised", "advertised")
                                     .flatMap(trailers -> Stream.of(false, true)
                                             .map(consume -> Arguments.of(trailers, consume))),
                             Stream.of(Arguments.of("gzip", true), Arguments.of("gzip-closed-by-service", true)));
    }

    private static Stream<Arguments> discardedResponses() {
        return Stream.of(false, true)
                .flatMap(advertised -> Stream.of(false, true)
                        .flatMap(partial -> Stream.of(false, true)
                                .flatMap(consume -> Stream.of(false, true)
                                        .map(close -> Arguments.of(advertised, partial, consume, close)))));
    }

    private static Stream<Arguments> invalidTrailerSections() {
        return Stream.of(Arguments.of("malformed", "Invalid trailer\r\n\r\n"),
                         Arguments.of("incomplete", "Checksum: valid\r\n\r"));
    }

    private static Http1Client client(TestServer server, Map<String, Completion> completions) {
        return client(server, completions, new AtomicInteger(), false);
    }

    private static Http1Client client(TestServer server,
                                      Map<String, Completion> completions,
                                      AtomicInteger decoderCloses,
                                      boolean closeInService) {
        return Http1Client.builder()
                .baseUri("http://127.0.0.1:" + server.listener.getLocalPort())
                .readTimeout(TIMEOUT)
                .contentEncoding(ContentEncodingContext.builder().addContentEncoding(new GzipEncoding(decoderCloses)))
                .addService((chain, request) -> {
                    AtomicInteger calls = new AtomicInteger();
                    CompletableFuture<Void> completion = request.whenComplete().<Void>handle((ignored3, ignored4) -> {
                        calls.incrementAndGet();
                        return null;
                    }).toCompletableFuture();
                    String path = request.uri().path().path();
                    completions.put(path, new Completion(completion, calls));
                    WebClientServiceResponse response = chain.proceed(request);
                    if (!path.equals("/buffered")) {
                        return response;
                    }
                    byte[] original;
                    try {
                        InputStream originalStream = response.inputStream().orElseThrow();
                        original = originalStream.readAllBytes();
                        if (closeInService) {
                            originalStream.close();
                            assertThat("service must close the original decoder exactly once", decoderCloses.get(), is(1));
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    assertThat("service must receive decoded chunk contents", new String(original, StandardCharsets.US_ASCII),
                               is(WIRE_ENTITY));
                    byte[] buffered = ("buffered " + new String(original, StandardCharsets.US_ASCII))
                            .getBytes(StandardCharsets.US_ASCII);
                    WritableHeaders<?> headers = WritableHeaders.create(response.headers());
                    headers.remove(HeaderNames.TRANSFER_ENCODING);
                    headers.remove(HeaderNames.CONTENT_ENCODING);
                    headers.set(HeaderNames.CONTENT_LENGTH, buffered.length);
                    return WebClientServiceResponse.builder(response)
                            .headers(ClientResponseHeaders.create(headers))
                            .inputStream(new ByteArrayInputStream(buffered))
                            .build();
                })
                .build();
    }

    private static void assertBufferedHeaders(Http1ClientResponse response) {
        assertThat(response.status(), is(Status.OK_200));
        assertThat("caller should see the service's fixed-length headers",
                   response.headers().contains(HeaderNames.TRANSFER_ENCODING), is(false));
        assertThat("caller should see the service's decoded headers",
                   response.headers().contains(HeaderNames.CONTENT_ENCODING), is(false));
        assertThat(response.headers().contentLength().orElseThrow(), is((long) BUFFERED_ENTITY.length()));
    }

    private static void assertNextResponse(Http1Client client) {
        try (Http1ClientResponse response = client.get("/next").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is(NEXT_ENTITY));
        }
    }

    private static void assertCompleted(Map<String, Completion> completions, String path) throws Exception {
        Completion completion = completions.get(path);
        assertThat("completion registered for " + path, completion, notNullValue());
        completion.future().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat("completion callback count for " + path, completion.calls().get(), is(1));
    }

    private static Socket accept(ServerSocket listener) throws IOException {
        Socket socket = listener.accept();
        socket.setSoTimeout((int) TIMEOUT.toMillis());
        return socket;
    }

    private static String readHead(InputStream input) throws IOException {
        StringBuilder head = new StringBuilder();
        while (head.length() < 16384) {
            int next = input.read();
            if (next == -1) {
                return head.toString();
            }
            head.append((char) next);
            if (head.toString().endsWith("\r\n\r\n")) {
                return head.toString();
            }
        }
        throw new IOException("Request headers exceed test limit");
    }

    private static byte[] chunkedResponse(boolean advertised, String trailerSection, boolean gzip) throws IOException {
        byte[] body = WIRE_ENTITY.getBytes(StandardCharsets.US_ASCII);
        if (gzip) {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream output = new GZIPOutputStream(compressed)) {
                output.write(body);
            }
            body = compressed.toByteArray();
        }
        String headers = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n"
                + (advertised ? "Trailer: Checksum\r\n" : "")
                + (gzip ? "Content-Encoding: gzip\r\n" : "")
                + "\r\n" + Integer.toHexString(body.length) + "\r\n";
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        response.write(headers.getBytes(StandardCharsets.US_ASCII));
        response.write(body);
        response.write(("\r\n0\r\n" + trailerSection).getBytes(StandardCharsets.US_ASCII));
        return response.toByteArray();
    }

    private static String nextResponse() {
        return "HTTP/1.1 200 OK\r\nContent-Length: " + NEXT_ENTITY.length() + "\r\n\r\n" + NEXT_ENTITY;
    }

    private static void send(Socket socket, String response) throws IOException {
        send(socket, response.getBytes(StandardCharsets.US_ASCII));
    }

    private static void send(Socket socket, byte[] response) throws IOException {
        socket.getOutputStream().write(response);
        socket.getOutputStream().flush();
    }

    @FunctionalInterface
    private interface ServerTask {
        void run(ServerSocket listener) throws Exception;
    }

    private record Completion(CompletableFuture<Void> future, AtomicInteger calls) {
    }

    private record GzipEncoding(AtomicInteger decoderCloses) implements ContentEncoding {
        @Override
        public String name() {
            return "gzip";
        }

        @Override
        public String type() {
            return "gzip";
        }

        @Override
        public Set<String> ids() {
            return Set.of("gzip");
        }

        @Override
        public boolean supportsEncoding() {
            return false;
        }

        @Override
        public boolean supportsDecoding() {
            return true;
        }

        @Override
        public ContentDecoder decoder() {
            return network -> {
                try {
                    return new GZIPInputStream(network) {
                        @Override
                        public void close() throws IOException {
                            decoderCloses.incrementAndGet();
                            super.close();
                        }
                    };
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }

        @Override
        public ContentEncoder encoder() {
            return ContentEncoder.NO_OP;
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final Future<?> task;

        private TestServer(ServerTask handler) throws IOException {
            listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            listener.setSoTimeout((int) TIMEOUT.toMillis());
            task = executor.submit(() -> {
                handler.run(listener);
                return null;
            });
        }

        void await() throws Exception {
            task.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        @Override
        public void close() throws IOException {
            try {
                listener.close();
            } finally {
                executor.shutdownNow();
                executor.close();
            }
        }
    }
}
