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

package io.helidon.webserver.tests.sse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.sse.SseEvent;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.sse.SseSource;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.sse.SseSink;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HeaderValues.ACCEPT_EVENT_STREAM;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;

@ServerTest
class SseHttp1TransportTest {
    private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(10);
    private static final String SSE_ENTITY = "data:hello\n\ndata:world\n\n";
    private static final String COMPLETE = "complete";
    private static final HeaderName SOCKET_ID = HeaderNames.create("x-socket-id");
    private static final HeaderName SSE_TRAILER = HeaderNames.create("x-sse-complete");
    private static final HeaderName STREAM_RESULT = HeaderNames.create("stream-result");

    private final WebServer webServer;
    private final Http1Client http1Client;

    SseHttp1TransportTest(WebServer webServer, Http1Client http1Client) {
        this.webServer = webServer;
        this.http1Client = http1Client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.putSocket("unbuffered", socket -> socket.writeBufferSize(0));
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sse", (req, res) -> {
            res.header(SOCKET_ID, req.socketId());
            res.header(HeaderNames.TRAILER, SSE_TRAILER.defaultCase());
            res.beforeTrailers(trailers -> trailers.set(SSE_TRAILER, COMPLETE));
            try (SseSink sink = res.sink(SseSink.TYPE)) {
                sink.emit(SseEvent.create("hello"));
                sink.emit(SseEvent.create("world"));
            }
        });
        rules.get("/content-length-sse", (req, res) -> {
            res.header(SOCKET_ID, req.socketId());
            res.beforeSend(() -> res.header(HeaderNames.CONTENT_LENGTH, "13"));
            try (SseSink sink = res.sink(SseSink.TYPE)) {
                sink.emit(SseEvent.create("x"));
            }
        });
        rules.any("/sse-head", (req, res) -> {
            res.header(SOCKET_ID, req.socketId());
            try (SseSink _ = res.sink(SseSink.TYPE)) {
                // A HEAD response completes without an SSE entity.
            }
        });
        rules.get("/connection", (req, res) -> res.send(req.socketId()));
    }

    @SetUpRoute("unbuffered")
    static void unbufferedRouting(HttpRules rules) {
        rules.get("/sse-empty", (req, res) -> {
            res.header(SOCKET_ID, req.socketId());
            res.header(HeaderNames.TRAILER, SSE_TRAILER.defaultCase());
            res.beforeTrailers(trailers -> trailers.set(SSE_TRAILER, COMPLETE));
            try (SseSink sink = res.sink(SseSink.TYPE)) {
                sink.emit(SseEvent.create(""));
            }
        });
        rules.get("/connection", (req, res) -> res.send(req.socketId()));
    }

    @Test
    void plainSseUsesChunkedFramingAndKeepsConnectionReusable() throws Exception {
        try (SocketHttpClient client = socketClient()) {
            client.request(Method.GET,
                           "/sse",
                           null,
                           List.of("Accept: text/event-stream", "TE: trailers"));
            InputStream input = client.socketInputStream();
            RawResponse response = readResponse(input);

            assertSseResponse(response);
            assertThat("SSE wire entity must preserve exact field and event framing",
                       new String(response.entity(), StandardCharsets.UTF_8),
                       is(SSE_ENTITY));
            assertThat("SSE response must contain at least one data chunk",
                       response.chunkSizes().size(),
                       greaterThan(0));

            assertConnectionReuse(client, input, response.header(SOCKET_ID.defaultCase()));
        }
    }

    @Test
    void emptyDataValueDoesNotTerminateChunkedBody() throws Exception {
        try (SocketHttpClient client = socketClient("unbuffered")) {
            client.request(Method.GET,
                           "/sse-empty",
                           null,
                           List.of("Accept: text/event-stream", "TE: trailers"));
            InputStream input = client.socketInputStream();
            RawResponse response = readResponse(input);

            assertSseResponse(response);
            assertThat("Empty SSE data value must preserve complete event framing",
                       new String(response.entity(), StandardCharsets.UTF_8),
                       is("data:\n\n"));

            assertConnectionReuse(client, input, response.header(SOCKET_ID.defaultCase()));
        }
    }

    @Test
    void sseRemovesContentLengthBeforeReusingConnection() throws Exception {
        try (SocketHttpClient client = socketClient()) {
            client.request(Method.GET,
                           "/content-length-sse",
                           null,
                           List.of("Accept: text/event-stream"));
            client.request(Method.GET, "/connection", null, List.of("Accept: text/plain"));
            InputStream input = client.socketInputStream();

            RawResponse sse = readResponse(input);
            assertThat("SSE response must not retain filter-supplied Content-Length",
                       sse.header("content-length"), nullValue());
            assertThat("SSE response must use streaming HTTP/1 framing",
                       sse.header("transfer-encoding").toLowerCase(Locale.ROOT),
                       containsString("chunked"));
            assertThat("SSE response must preserve exact event framing",
                       new String(sse.entity(), StandardCharsets.UTF_8),
                       is("data:x\n\n"));

            String socketId = sse.header(SOCKET_ID.defaultCase());
            assertThat("SSE response must expose its physical socket id", socketId, notNullValue());
            RawResponse probe = readResponse(input);
            assertThat("Connection probe status", probe.statusCode(), is(200));
            assertThat("Probe must use the same physical server socket after SSE completion",
                       new String(probe.entity(), StandardCharsets.UTF_8),
                       is(socketId));
        }
    }

    @Test
    void teTrailersWithoutRouteTrailerKeepsHelidonClientConnectionReusable() throws InterruptedException {
        CountDownLatch complete = new CountDownLatch(1);
        String socketId;
        try (Http1ClientResponse response = http1Client.get("/content-length-sse")
                .header(ACCEPT_EVENT_STREAM)
                .header(HeaderValues.TE_TRAILERS)
                .request()) {
            socketId = response.headers().first(SOCKET_ID).orElse(null);
            assertThat("SSE response must expose its physical socket id", socketId, notNullValue());
            assertThat("SSE response must declare the stream-result trailer",
                       response.headers().first(HeaderNames.TRAILER).orElse(null),
                       is("stream-result"));
            response.source(SseSource.TYPE, new SseSource() {
                @Override
                public void onEvent(SseEvent event) {
                }

                @Override
                public void onClose() {
                    complete.countDown();
                }
            });
            assertThat("Helidon client must consume the complete SSE response",
                       complete.await(SOCKET_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                       is(true));
            assertThat("Helidon client must consume the declared stream-result trailer",
                       response.trailers().first(STREAM_RESULT).orElse(null),
                       is(""));
        }

        try (Http1ClientResponse response = http1Client.get("/connection").request()) {
            assertThat("Helidon client must reuse the same physical server socket after SSE completion",
                       response.as(String.class),
                       is(socketId));
        }
    }

    @Test
    void gzipSseFinalizesEncoderBeforeTerminatingChunkAndKeepsConnectionReusable() throws Exception {
        try (SocketHttpClient client = socketClient()) {
            client.request(Method.GET,
                           "/sse",
                           null,
                           List.of("Accept: text/event-stream", "Accept-Encoding: gzip", "TE: trailers"));
            InputStream input = client.socketInputStream();
            RawResponse response = readResponse(input);

            assertSseResponse(response);
            assertThat("SSE response content encoding", response.header("content-encoding"), is("gzip"));
            assertThat("Gzip entity must include its header and footer", response.entity().length, greaterThan(18));
            assertThat("Gzip magic byte 1", response.entity()[0] & 0xff, is(0x1f));
            assertThat("Gzip magic byte 2", response.entity()[1] & 0xff, is(0x8b));
            assertThat("Complete gzip stream must be available before the terminating HTTP chunk",
                       gunzip(response.entity()),
                       is(SSE_ENTITY));
            assertThat("Gzip footer must record the complete uncompressed SSE entity",
                       gzipInputSize(response.entity()),
                       is((long) SSE_ENTITY.getBytes(StandardCharsets.UTF_8).length));

            assertConnectionReuse(client, input, response.header(SOCKET_ID.defaultCase()));
        }
    }

    @Test
    void headSseSendsOneHeaderBlockAndKeepsConnectionReusable() throws Exception {
        try (SocketHttpClient client = socketClient()) {
            client.request(Method.HEAD,
                           "/sse-head",
                           null,
                           List.of("Accept: text/event-stream"));
            InputStream input = client.socketInputStream();
            RawResponse response = readHeadResponse(input);

            assertThat("HEAD SSE response status", response.statusCode(), is(200));
            assertThat("HEAD SSE must contain no entity", response.entity().length, is(0));
            assertThat("HEAD SSE response must expose its physical socket id",
                       response.header(SOCKET_ID.defaultCase()), notNullValue());

            assertConnectionReuse(client, input, response.header(SOCKET_ID.defaultCase()));
        }
    }

    private static void assertSseResponse(RawResponse response) {
        String contentType = response.header("content-type");
        String transferEncoding = response.header("transfer-encoding");
        String trailer = response.header("trailer");

        assertThat("SSE response status", response.statusCode(), is(200));
        assertThat("SSE response content type", contentType, notNullValue());
        assertThat("SSE response content type",
                   contentType.toLowerCase(Locale.ROOT),
                   startsWith("text/event-stream"));
        assertThat("SSE response transfer encoding", transferEncoding, notNullValue());
        assertThat("SSE response transfer encoding",
                   transferEncoding.toLowerCase(Locale.ROOT),
                   containsString("chunked"));
        assertThat("SSE response trailer declaration", trailer, notNullValue());
        assertThat("SSE response must declare its trailer",
                   trailer.toLowerCase(Locale.ROOT),
                   containsString(SSE_TRAILER.lowerCase()));
        assertThat("SSE response must end with the HTTP/1 zero chunk", response.terminatingChunkLine(), is("0"));
        assertThat("SSE response trailer value",
                   response.trailer(SSE_TRAILER.defaultCase()),
                   is(COMPLETE));
    }

    private static void assertConnectionReuse(SocketHttpClient client,
                                              InputStream input,
                                              String expectedSocketId) throws IOException {
        assertThat("SSE response must expose its physical socket id", expectedSocketId, notNullValue());

        client.request(Method.GET, "/connection", null, List.of("Accept: text/plain"));
        RawResponse response = readResponse(input);

        assertThat("Connection probe status", response.statusCode(), is(200));
        assertThat("Probe must use the same physical server socket as the completed SSE response",
                   new String(response.entity(), StandardCharsets.UTF_8),
                   is(expectedSocketId));
    }

    private static RawResponse readResponse(InputStream input) throws IOException {
        String statusLine = readCrlfLine(input);
        assertThat("HTTP response must start with a status line", statusLine, notNullValue());
        String[] statusParts = statusLine.split(" ", 3);
        assertThat("HTTP status line must contain a numeric status code: " + statusLine,
                   statusParts.length,
                   greaterThan(1));
        int statusCode = Integer.parseInt(statusParts[1]);

        Map<String, List<String>> headers = readFields(input);
        String transferEncoding = firstValue(headers, "transfer-encoding");
        if (transferEncoding != null && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            return readChunkedResponse(statusCode, headers, input);
        }

        String contentLength = firstValue(headers, "content-length");
        assertThat("Non-chunked response must declare Content-Length", contentLength, notNullValue());
        byte[] entity = readBytes(input, Integer.parseInt(contentLength));
        return new RawResponse(statusCode, headers, Map.of(), entity, List.of(), null);
    }

    private static RawResponse readHeadResponse(InputStream input) throws IOException {
        String statusLine = readCrlfLine(input);
        assertThat("HTTP response must start with a status line", statusLine, notNullValue());
        String[] statusParts = statusLine.split(" ", 3);
        assertThat("HTTP status line must contain a numeric status code: " + statusLine,
                   statusParts.length,
                   greaterThan(1));
        return new RawResponse(Integer.parseInt(statusParts[1]),
                               readFields(input),
                               Map.of(),
                               new byte[0],
                               List.of(),
                               null);
    }

    private static RawResponse readChunkedResponse(int statusCode,
                                                   Map<String, List<String>> headers,
                                                   InputStream input) throws IOException {
        ByteArrayOutputStream entity = new ByteArrayOutputStream();
        List<Integer> chunkSizes = new ArrayList<>();

        while (true) {
            String chunkLine = readCrlfLine(input);
            assertThat("Chunked response must contain a chunk-size line", chunkLine, notNullValue());
            String chunkSizeText = chunkLine.split(";", 2)[0].trim();
            int chunkSize = Integer.parseInt(chunkSizeText, 16);
            if (chunkSize == 0) {
                Map<String, List<String>> trailers = readFields(input);
                return new RawResponse(statusCode, headers, trailers, entity.toByteArray(), chunkSizes, chunkLine);
            }

            byte[] chunk = readBytes(input, chunkSize);
            entity.writeBytes(chunk);
            chunkSizes.add(chunkSize);
            expectCrlf(input, "data chunk of size " + chunkSize);
        }
    }

    private static Map<String, List<String>> readFields(InputStream input) throws IOException {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        while (true) {
            String line = readCrlfLine(input);
            assertThat("HTTP field block ended before its terminating empty line", line, notNullValue());
            if (line.isEmpty()) {
                return fields;
            }
            int colon = line.indexOf(':');
            assertThat("HTTP field must contain a colon: " + line, colon, greaterThan(0));
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            fields.computeIfAbsent(name, _ -> new ArrayList<>()).add(value);
        }
    }

    private static String firstValue(Map<String, List<String>> fields, String name) {
        List<String> values = fields.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String readCrlfLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int next = input.read();
            if (next == -1) {
                if (previous != -1) {
                    line.write(previous);
                }
                return line.size() == 0 ? null : line.toString(StandardCharsets.US_ASCII);
            }
            if (previous == '\r' && next == '\n') {
                return line.toString(StandardCharsets.US_ASCII);
            }
            if (previous != -1) {
                line.write(previous);
            }
            previous = next;
        }
    }

    private static byte[] readBytes(InputStream input, int length) throws IOException {
        byte[] bytes = input.readNBytes(length);
        assertThat("HTTP entity ended before " + length + " bytes were read", bytes.length, is(length));
        return bytes;
    }

    private static void expectCrlf(InputStream input, String after) throws IOException {
        assertThat("Expected CR after " + after, input.read(), is((int) '\r'));
        assertThat("Expected LF after " + after, input.read(), is((int) '\n'));
    }

    private static String gunzip(byte[] entity) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(entity))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long gzipInputSize(byte[] entity) {
        int offset = entity.length - Integer.BYTES;
        return (entity[offset] & 0xffL)
                | ((entity[offset + 1] & 0xffL) << 8)
                | ((entity[offset + 2] & 0xffL) << 16)
                | ((entity[offset + 3] & 0xffL) << 24);
    }

    private SocketHttpClient socketClient() {
        return SocketHttpClient.create("localhost", webServer.port(), SOCKET_TIMEOUT);
    }

    private SocketHttpClient socketClient(String socketName) {
        return SocketHttpClient.create("localhost", webServer.port(socketName), SOCKET_TIMEOUT);
    }

    private record RawResponse(int statusCode,
                               Map<String, List<String>> headers,
                               Map<String, List<String>> trailers,
                               byte[] entity,
                               List<Integer> chunkSizes,
                               String terminatingChunkLine) {

        private String header(String name) {
            return firstValue(headers, name);
        }

        private String trailer(String name) {
            return firstValue(trailers, name);
        }
    }
}
