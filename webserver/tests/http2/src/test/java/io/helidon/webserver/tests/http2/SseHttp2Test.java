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
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanDecoder;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.sse.SseEvent;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.sse.SseSink;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@ServerTest
class SseHttp2Test {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int FLOW_WINDOW = 64;
    private static final HeaderName SOCKET_ID = HeaderNames.create("x-socket-id");
    private static final HeaderName CONTROL_TRAILER = HeaderNames.create("x-control-complete");
    private static final HeaderName SSE_TRAILER = HeaderNames.create("x-sse-trailer");
    private static final byte[] FILTER_CLOSE_MARKER = "filter-closed".getBytes(StandardCharsets.UTF_8);
    private static final ConcurrentMap<String, StreamControl> STREAM_CONTROLS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, LifecycleProbe> LIFECYCLE_PROBES = new ConcurrentHashMap<>();

    private final int tlsPort;
    private final Tls clientTls;

    SseHttp2Test(WebServer server) {
        tlsPort = server.port("https");
        clientTls = Tls.builder()
                .trust(trust -> trust.keystore(store -> store
                        .passphrase("password")
                        .trustStore(true)
                        .keystore(Resource.create("client.p12"))))
                .build();
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        Keys serverKeys = Keys.builder()
                .keystore(store -> store
                        .passphrase("password")
                        .keystore(Resource.create("server.p12")))
                .build();
        Tls tls = Tls.builder()
                .privateKey(serverKeys)
                .privateKeyCertChain(serverKeys)
                .build();

        server.contentEncoding(ContentEncodingContext.builder()
                                       .addContentEncoding(GzipEncoding.create())
                                       .build())
                .putSocket("https", socket -> socket.tls(tls));
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        configureRoutes(router);
    }

    @SetUpRoute("https")
    static void tlsRouting(HttpRouting.Builder router) {
        configureRoutes(router);
    }

    @Test
    void tlsAlpnSseUsesOnePhysicalHttp2Connection() throws IOException, InterruptedException {
        URI uri = URI.create("https://localhost:" + tlsPort);
        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(TIMEOUT)
                .sslContext(clientTls.sslContext())
                .build()) {

            HttpResponse<String> sse = client.send(request(uri.resolve("/sse")), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> ping = client.send(request(uri.resolve("/ping")), HttpResponse.BodyHandlers.ofString());

            assertThat("SSE must be negotiated with ALPN as HTTP/2", sse.version(), is(HttpClient.Version.HTTP_2));
            assertThat("Ping must use HTTP/2", ping.version(), is(HttpClient.Version.HTTP_2));
            assertThat("SSE status", sse.statusCode(), is(Status.OK_200.code()));
            assertThat("SSE framing", sse.body(), is("data:first\n\ndata:second\n\n"));
            assertThat("Ping status", ping.statusCode(), is(Status.OK_200.code()));
            assertThat("Ping body", ping.body(), is("ok"));
            assertThat("SSE and ping must use the same physical server socket",
                       ping.headers().firstValue(SOCKET_ID.lowerCase()).orElseThrow(),
                       is(sse.headers().firstValue(SOCKET_ID.lowerCase()).orElseThrow()));
        }
    }

    @Test
    void headersArriveBeforeDelayedFirstEvent(Http2TestClient client) throws InterruptedException {
        StreamControl control = new StreamControl(null, "delayed", false);
        STREAM_CONTROLS.put("delayed", control);
        try (Http2TestConnection connection = client.createConnection()) {
            connection.completeHandshake(TIMEOUT);
            FrameDemultiplexer frames = new FrameDemultiplexer(connection);
            request(connection, 1, "/controlled?id=delayed", sseHeaders());
            await("SSE sink creation", control.sinkCreated);

            StreamCapture delayed = new StreamCapture();
            delayed.accept(frames.next(1, "delayed SSE response headers"));
            assertThat("The first delayed SSE frame must be HEADERS", delayed.responseHeaders, notNullValue());
            assertThat("HEADERS must not prematurely end the stream", delayed.ended, is(false));

            request(connection, 3, "/ping", WritableHeaders.create());
            assertPing(frames, 3);
            assertThat("No SSE frame may follow HEADERS before the release protocol event",
                       frames.hasQueuedFrame(1), is(false));

            control.release.countDown();
            await("Delayed SSE completion", control.completed);
            control.assertNoFailure("Delayed SSE handler");
            delayed.readUntilEnd(frames, 1);
            assertThat("Delayed SSE event framing", delayed.body(), is("data:delayed\n\n"));
        } finally {
            control.release.countDown();
            STREAM_CONTROLS.remove("delayed");
        }
    }

    @Test
    void concurrentSseStreamsCompleteIndependentlyOnOneConnection(Http2TestClient client) throws InterruptedException {
        StreamControl first = new StreamControl("open-one", "close-one", false);
        StreamControl second = new StreamControl("open-two", "close-two", false);
        STREAM_CONTROLS.put("one", first);
        STREAM_CONTROLS.put("two", second);
        try (Http2TestConnection connection = client.createConnection()) {
            connection.completeHandshake(TIMEOUT);
            FrameDemultiplexer frames = new FrameDemultiplexer(connection);
            request(connection, 1, "/controlled?id=one", sseHeaders());
            request(connection, 3, "/controlled?id=two", sseHeaders());

            StreamCapture streamOne = new StreamCapture();
            StreamCapture streamThree = new StreamCapture();
            streamOne.readUntilBytes(frames, 1, sseBytes("open-one").length);
            streamThree.readUntilBytes(frames, 3, sseBytes("open-two").length);

            first.release.countDown();
            streamOne.readUntilEnd(frames, 1);
            assertThat("Stream 1 exact events", streamOne.body(), is("data:open-one\n\ndata:close-one\n\n"));
            await("Stream 1 completion", first.completed);
            first.assertNoFailure("Stream 1 handler");
            assertThat("Stream 3 must remain open when stream 1 completes", streamThree.ended, is(false));

            request(connection, 5, "/ping", WritableHeaders.create());
            assertPing(frames, 5);
            assertThat("Stream 3 must not complete while only stream 1 was released", frames.hasQueuedFrame(3), is(false));

            second.release.countDown();
            streamThree.readUntilEnd(frames, 3);
            assertThat("Stream 3 exact events", streamThree.body(), is("data:open-two\n\ndata:close-two\n\n"));
            await("Stream 3 completion", second.completed);
            second.assertNoFailure("Stream 3 handler");
        } finally {
            first.release.countDown();
            second.release.countDown();
            STREAM_CONTROLS.remove("one");
            STREAM_CONTROLS.remove("two");
        }
    }

    @Test
    void clientCancelDoesNotAffectSiblingStreamOrConnection(Http2TestClient client) throws InterruptedException {
        StreamControl canceled = new StreamControl("open-cancel", "late-cancel", false);
        StreamControl sibling = new StreamControl("open-sibling", "close-sibling", false);
        STREAM_CONTROLS.put("cancel", canceled);
        STREAM_CONTROLS.put("sibling", sibling);
        try (Http2TestConnection connection = client.createConnection()) {
            connection.completeHandshake(TIMEOUT);
            FrameDemultiplexer frames = new FrameDemultiplexer(connection);
            request(connection, 1, "/controlled?id=cancel", sseHeaders());
            request(connection, 3, "/controlled?id=sibling", sseHeaders());

            new StreamCapture().readUntilBytes(frames, 1, sseBytes("open-cancel").length);
            StreamCapture siblingCapture = new StreamCapture();
            siblingCapture.readUntilBytes(frames, 3, sseBytes("open-sibling").length);

            connection.writer().write(new Http2RstStream(Http2ErrorCode.CANCEL)
                                              .toFrameData(null, 1, Http2Flag.NoFlags.create()));
            canceled.release.countDown();
            sibling.release.countDown();

            siblingCapture.readUntilEnd(frames, 3);
            assertThat("Sibling stream survives client RST_STREAM CANCEL",
                       siblingCapture.body(), is("data:open-sibling\n\ndata:close-sibling\n\n"));
            request(connection, 5, "/ping", WritableHeaders.create());
            assertPing(frames, 5);
            await("Canceled handler completion", canceled.completed);
        } finally {
            canceled.release.countDown();
            sibling.release.countDown();
            STREAM_CONTROLS.remove("cancel");
            STREAM_CONTROLS.remove("sibling");
        }
    }

    @Test
    void sustainedSseHonorsStreamFlowControl(Http2TestClient client) throws InterruptedException {
        String payload = "x".repeat(1024);
        StreamControl control = new StreamControl(payload, null, true);
        STREAM_CONTROLS.put("flow", control);
        try (Http2TestConnection connection = client.createConnection()) {
            connection.completeHandshake(TIMEOUT);
            connection.sendSettings(Http2Settings.builder()
                                            .add(Http2Setting.INITIAL_WINDOW_SIZE, (long) FLOW_WINDOW)
                                            .build());
            FrameDemultiplexer frames = new FrameDemultiplexer(connection);
            request(connection, 1, "/controlled?id=flow", sseHeaders());
            Http2FrameData settingsAck = frames.next(0, "small-window SETTINGS acknowledgment").frame();
            assertThat("Small-window response frame type", settingsAck.header().type(), is(Http2FrameType.SETTINGS));
            assertThat("Small-window SETTINGS must be acknowledged before response data",
                       settingsAck.header().flags(Http2FrameTypes.SETTINGS).ack(), is(true));

            StreamCapture capture = new StreamCapture();
            capture.readUntilBytes(frames, 1, FLOW_WINDOW);
            assertThat("Server must consume exactly the advertised stream credit", capture.data.size(), is(FLOW_WINDOW));
            assertThat("SSE handler must remain flow-control blocked before WINDOW_UPDATE",
                       control.completed.getCount(), is(1L));

            int expectedLength = sseBytes(payload).length;
            while (capture.data.size() < expectedLength) {
                int credit = Math.min(FLOW_WINDOW, expectedLength - capture.data.size());
                connection.writer().write(new Http2WindowUpdate(credit)
                                                  .toFrameData(null, 1, Http2Flag.NoFlags.create()));
                capture.readUntilBytes(frames, 1, capture.data.size() + credit);
            }
            capture.readUntilEnd(frames, 1);
            assertThat("Flow-controlled SSE framing", capture.body(), is("data:" + payload + "\n\n"));
            await("Flow-controlled SSE completion", control.completed);
            control.assertNoFailure("Flow-controlled SSE handler");
        } finally {
            control.release.countDown();
            STREAM_CONTROLS.remove("flow");
        }
    }

    @Test
    void encodingFiltersTrailersAndCallbacksFinalizeBeforeEndStream(Http2TestClient client)
            throws IOException, InterruptedException {
        LifecycleProbe probe = new LifecycleProbe();
        LIFECYCLE_PROBES.put("lifecycle", probe);
        try (Http2TestConnection connection = client.createConnection()) {
            connection.completeHandshake(TIMEOUT);
            WritableHeaders<?> headers = sseHeaders();
            headers.add(HeaderNames.ACCEPT_ENCODING, "gzip");
            headers.add(HeaderValues.TE_TRAILERS);
            request(connection, 1, "/lifecycle?id=lifecycle", headers);

            FrameDemultiplexer frames = new FrameDemultiplexer(connection);
            StreamCapture capture = new StreamCapture();
            capture.readUntilEnd(frames, 1);
            await("SSE lifecycle completion", probe.completed);

            assertThat("HTTP/2 response status", capture.responseHeaders.status(), is(Status.OK_200));
            assertThat("SSE gzip content encoding",
                       capture.responseHeaders.httpHeaders().get(HeaderNames.CONTENT_ENCODING).get(), is("gzip"));
            assertThat("Declared trailer value", capture.trailers.httpHeaders().get(SSE_TRAILER).get(), is("done"));
            assertThat("END_STREAM must be on trailers after gzip and filter finalization",
                       capture.terminalFrameType, is(Http2FrameType.HEADERS));
            assertThat("Decompressed SSE data includes the stream-filter close marker",
                       gunzip(capture.data.toByteArray()), is("data:payload\n\nfilter-closed"));
            assertThat("beforeSend callback count", probe.beforeSend.get(), is(1));
            assertThat("beforeTrailers callback count", probe.beforeTrailers.get(), is(1));
            assertThat("whenSent callback count", probe.whenSent.get(), is(1));
            assertThat("stream filter close count", probe.filterClose.get(), is(1));
        } finally {
            LIFECYCLE_PROBES.remove("lifecycle");
        }
    }

    private static void configureRoutes(HttpRouting.Builder router) {
        router.route(Http2Route.route(Method.GET, "/sse", (req, res) -> {
                    res.header(SOCKET_ID, req.socketId());
                    try (SseSink sink = res.sink(SseSink.TYPE)) {
                        sink.emit(SseEvent.create("first")).emit(SseEvent.create("second"));
                    }
                }))
                .route(Http2Route.route(Method.GET, "/ping", (req, res) -> {
                    res.header(SOCKET_ID, req.socketId());
                    res.send("ok");
                }))
                .route(Http2Route.route(Method.GET, "/controlled", (req, res) -> controlled(req.query().get("id"), res)))
                .route(Http2Route.route(Method.GET, "/lifecycle", (req, res) -> lifecycle(req.query().get("id"), res)));
    }

    private static void controlled(String id, ServerResponse response) {
        StreamControl control = STREAM_CONTROLS.get(id);
        if (control == null) {
            throw new IllegalStateException("Missing SSE stream control " + id);
        }
        response.header(HeaderNames.TRAILER, CONTROL_TRAILER.defaultCase());
        response.beforeTrailers(trailers -> trailers.set(CONTROL_TRAILER, "done"));
        try (SseSink sink = response.sink(SseSink.TYPE)) {
            control.sinkCreated.countDown();
            if (control.firstEvent != null) {
                sink.emit(SseEvent.create(control.firstEvent));
            }
            control.awaitRelease();
            if (control.lastEvent != null) {
                sink.emit(SseEvent.create(control.lastEvent));
            }
        } catch (Throwable t) {
            control.failure.compareAndSet(null, t);
        } finally {
            control.completed.countDown();
        }
    }

    private static void lifecycle(String id, ServerResponse response) {
        LifecycleProbe probe = LIFECYCLE_PROBES.get(id);
        if (probe == null) {
            throw new IllegalStateException("Missing SSE lifecycle probe " + id);
        }
        response.header(HeaderNames.TRAILER, SSE_TRAILER.defaultCase());
        response.beforeSend(probe.beforeSend::incrementAndGet);
        response.beforeTrailers(trailers -> {
            probe.beforeTrailers.incrementAndGet();
            trailers.set(SSE_TRAILER, "done");
        });
        response.whenSent(probe.whenSent::incrementAndGet);
        response.streamFilter(delegate -> new FilterOutputStream(delegate) {
            private boolean closed;

            @Override
            public void close() throws IOException {
                if (!closed) {
                    closed = true;
                    probe.filterClose.incrementAndGet();
                    write(FILTER_CLOSE_MARKER);
                }
                super.close();
            }
        });
        try (SseSink sink = response.sink(SseSink.TYPE)) {
            sink.emit(SseEvent.create("payload"));
        } finally {
            probe.completed.countDown();
        }
    }

    private static HttpRequest request(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(TIMEOUT)
                .header(HeaderNames.ACCEPT.lowerCase(), "text/event-stream")
                .GET()
                .build();
    }

    private static void request(Http2TestConnection connection,
                                int streamId,
                                String path,
                                WritableHeaders<?> headers) {
        connection.request(streamId, Method.GET, path, headers, BufferData.empty());
    }

    private static WritableHeaders<?> sseHeaders() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ACCEPT, "text/event-stream");
        return headers;
    }

    private static void assertPing(FrameDemultiplexer frames, int streamId) {
        StreamCapture ping = new StreamCapture();
        ping.readUntilEnd(frames, streamId);
        assertThat("Ping status on stream " + streamId, ping.responseHeaders.status(), is(Status.OK_200));
        assertThat("Ping body on stream " + streamId, ping.body(), is("ok"));
    }

    private static byte[] sseBytes(String value) {
        return ("data:" + value + "\n\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void await(String reason, CountDownLatch latch) throws InterruptedException {
        assertThat("Timed out waiting for " + reason, latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
    }

    private record InboundFrame(Http2FrameData frame, Http2Headers headers) {
    }

    private static final class FrameDemultiplexer {
        private final Http2TestConnection connection;
        private final Map<Integer, ArrayDeque<InboundFrame>> queued = new HashMap<>();
        private final Http2Headers.DynamicTable dynamicTable =
                Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
        private final Http2HuffmanDecoder huffman = Http2HuffmanDecoder.create();

        private FrameDemultiplexer(Http2TestConnection connection) {
            this.connection = connection;
        }

        private InboundFrame next(int streamId, String reason) {
            ArrayDeque<InboundFrame> streamFrames = queued.get(streamId);
            if (streamFrames != null && !streamFrames.isEmpty()) {
                return streamFrames.removeFirst();
            }
            for (;;) {
                Http2FrameData frame = connection.awaitNextFrame(TIMEOUT);
                assertThat("Timed out waiting for " + reason, frame, notNullValue());
                if (frame.header().type() == Http2FrameType.GO_AWAY) {
                    Http2GoAway goAway = Http2GoAway.create(frame.data());
                    assertThat("Unexpected GOAWAY " + goAway.errorCode() + ": " + goAway.details(), false, is(true));
                }
                Http2Headers headers = frame.header().type() == Http2FrameType.HEADERS
                        ? Http2Headers.create(null, dynamicTable, huffman, frame)
                        : null;
                InboundFrame inbound = new InboundFrame(frame, headers);
                if (frame.header().streamId() == streamId) {
                    return inbound;
                }
                queued.computeIfAbsent(frame.header().streamId(), _ -> new ArrayDeque<>()).addLast(inbound);
            }
        }

        private boolean hasQueuedFrame(int streamId) {
            ArrayDeque<InboundFrame> frames = queued.get(streamId);
            return frames != null && !frames.isEmpty();
        }
    }

    private static final class StreamCapture {
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private Http2Headers responseHeaders;
        private Http2Headers trailers;
        private Http2FrameType terminalFrameType;
        private boolean ended;

        private void readUntilBytes(FrameDemultiplexer frames, int streamId, int byteCount) {
            while (data.size() < byteCount) {
                accept(frames.next(streamId, "stream " + streamId + " data byte " + byteCount));
                assertThat("Stream " + streamId + " ended before receiving " + byteCount + " bytes", ended, is(false));
            }
            assertThat("Stream " + streamId + " must stop at the exact flow/event boundary", data.size(), is(byteCount));
        }

        private void readUntilEnd(FrameDemultiplexer frames, int streamId) {
            while (!ended) {
                accept(frames.next(streamId, "stream " + streamId + " END_STREAM"));
            }
        }

        private void accept(InboundFrame inbound) {
            Http2FrameData frame = inbound.frame();
            Http2FrameType type = frame.header().type();
            assertThat("Unexpected frame " + type + " on stream " + frame.header().streamId(),
                       type == Http2FrameType.HEADERS || type == Http2FrameType.DATA, is(true));
            if (type == Http2FrameType.HEADERS) {
                if (responseHeaders == null) {
                    responseHeaders = inbound.headers();
                } else {
                    trailers = inbound.headers();
                }
                if (frame.header().flags(Http2FrameTypes.HEADERS).endOfStream()) {
                    ended = true;
                    terminalFrameType = type;
                }
            } else {
                data.writeBytes(frame.data().readBytes());
                if (frame.header().flags(Http2FrameTypes.DATA).endOfStream()) {
                    ended = true;
                    terminalFrameType = type;
                }
            }
        }

        private String body() {
            return data.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class StreamControl {
        private final String firstEvent;
        private final String lastEvent;
        private final CountDownLatch sinkCreated = new CountDownLatch(1);
        private final CountDownLatch release;
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private StreamControl(String firstEvent, String lastEvent, boolean initiallyReleased) {
            this.firstEvent = firstEvent;
            this.lastEvent = lastEvent;
            release = new CountDownLatch(initiallyReleased ? 0 : 1);
        }

        private void awaitRelease() {
            try {
                if (!release.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release controlled SSE stream");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting to release controlled SSE stream", e);
            }
        }

        private void assertNoFailure(String reason) {
            assertThat(reason + " failed", failure.get(), nullValue());
        }
    }

    private static final class LifecycleProbe {
        private final AtomicInteger beforeSend = new AtomicInteger();
        private final AtomicInteger beforeTrailers = new AtomicInteger();
        private final AtomicInteger whenSent = new AtomicInteger();
        private final AtomicInteger filterClose = new AtomicInteger();
        private final CountDownLatch completed = new CountDownLatch(1);
    }
}
