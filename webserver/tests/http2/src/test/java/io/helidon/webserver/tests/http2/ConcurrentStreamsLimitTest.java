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

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanDecoder;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2Priority;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.POST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
class ConcurrentStreamsLimitTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_CONCURRENT_STREAMS = 1;
    private static final String BLOCKING_PATH = "/blocking";
    private static final String REJECTED_PATH = "/rejected";

    private static volatile CountDownLatch requestsStarted;
    private static volatile CountDownLatch releaseHandlers;

    private final Http2Headers.DynamicTable responseDynamicTable =
            Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
    private final Http2HuffmanDecoder responseHuffman = Http2HuffmanDecoder.create();
    private Http2Headers.DynamicTable requestDynamicTable;

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Http2Route.route(POST, BLOCKING_PATH, (req, res) -> {
            requestsStarted.countDown();
            try {
                if (!releaseHandlers.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release HTTP/2 handler");
                }
                res.send("done");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release HTTP/2 handler", e);
            }
        }));
        router.route(Http2Route.route(POST, REJECTED_PATH, (req, res) -> {
            throw RequestException.builder()
                    .status(Status.BAD_REQUEST_400)
                    .message("Rejected request")
                    .build();
        }));
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        server.addProtocol(Http2Config.builder()
                                   .sendErrorDetails(true)
                                   .maxRapidResets(-1)
                                   .maxConcurrentStreams(MAX_CONCURRENT_STREAMS)
                                   .maxHeaderListSize(128_000)
                                   .build());
    }

    @BeforeEach
    void resetLatches() {
        requestsStarted = new CountDownLatch(MAX_CONCURRENT_STREAMS);
        releaseHandlers = new CountDownLatch(1);
        requestDynamicTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
    }

    @Test
    void completedStreamsDoNotConsumeConcurrentStreamLimit(Http2TestClient client) throws InterruptedException {
        try (Http2TestConnection h2conn = client.createConnection()) {
            h2conn.request(1, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));

            assertThat("Initial streams did not start",
                       requestsStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                       is(true));

            releaseHandlers.countDown();
            assertOkResponse(h2conn, 1);

            h2conn.request(3, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            assertOkResponse(h2conn, 3);
        }
    }

    @Test
    void delayedWindowUpdateForClosedStreamDoesNotCloseConnection(Http2TestClient client) throws InterruptedException {
        try (Http2TestConnection h2conn = client.createConnection()) {
            h2conn.request(1, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));

            assertThat("Initial streams did not start",
                       requestsStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                       is(true));

            releaseHandlers.countDown();
            assertOkResponse(h2conn, 1);

            requestsStarted = new CountDownLatch(MAX_CONCURRENT_STREAMS);
            releaseHandlers = new CountDownLatch(1);

            h2conn.request(3, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            assertThat("Second stream did not start",
                       requestsStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                       is(true));

            h2conn.writer().write(new Http2WindowUpdate(1)
                                          .toFrameData(null, 1, Http2Flag.NoFlags.create()));
            releaseHandlers.countDown();
            assertOkResponse(h2conn, 3);

            requestsStarted = new CountDownLatch(MAX_CONCURRENT_STREAMS);
            releaseHandlers = new CountDownLatch(1);

            h2conn.request(5, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            assertThat("Third stream did not start",
                       requestsStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                       is(true));

            releaseHandlers.countDown();
            assertOkResponse(h2conn, 5);
        }
    }

    @Test
    void idlePriorityStreamDoesNotConsumeConcurrentStreamLimit(Http2TestClient client) {
        try (Http2TestConnection h2conn = client.createConnection()) {
            for (int streamId = 1; streamId < 20; streamId += 2) {
                h2conn.writer().write(new Http2Priority(false, 0, 16)
                                              .toFrameData(null, streamId, Http2Flag.NoFlags.create()));
            }
            releaseHandlers.countDown();

            h2conn.request(21, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            assertOkResponse(h2conn, 21);
        }
    }

    @Test
    void activeStreamsConsumeConcurrentStreamLimit(Http2TestClient client) throws InterruptedException {
        try (Http2TestConnection h2conn = client.createConnection()) {
            h2conn.request(1, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));

            assertThat("Initial streams did not start",
                       requestsStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                       is(true));

            h2conn.request(3, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            for (;;) {
                Http2FrameData frame = h2conn.awaitNextFrame(TIMEOUT);
                assertThat("Timed out waiting for GOAWAY frame", frame, notNullValue());

                if (frame.header().type() == Http2FrameType.GO_AWAY) {
                    Http2GoAway goAway = Http2GoAway.create(frame.data());
                    assertThat(goAway.errorCode(), is(Http2ErrorCode.REFUSED_STREAM));
                    return;
                }
                if (frame.header().streamId() == 0) {
                    continue;
                }
                fail("Unexpected HTTP/2 frame " + frame.header().type() + " for stream " + frame.header().streamId());
            }
        } finally {
            releaseHandlers.countDown();
        }
    }

    @Test
    void synchronouslyResetStreamDoesNotConsumeConcurrentStreamLimit(Http2TestClient client) {
        try (Http2TestConnection h2conn = client.createConnection()) {
            WritableHeaders<?> headers = WritableHeaders.create();
            headers.add(HeaderNames.CONTENT_LENGTH, 1);

            Http2Headers h2Headers = Http2Headers.create(headers);
            h2Headers.method(POST);
            h2Headers.path(BLOCKING_PATH);
            h2Headers.scheme(h2conn.clientUri().scheme());
            h2Headers.authority(h2conn.clientUri().authority());
            h2conn.writer().writeHeaders(h2Headers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                         FlowControl.Outbound.NOOP);

            for (;;) {
                Http2FrameData frame = h2conn.awaitNextFrame(TIMEOUT);
                assertThat("Timed out waiting for RST_STREAM frame", frame, notNullValue());

                if (frame.header().type() == Http2FrameType.GO_AWAY) {
                    Http2GoAway goAway = Http2GoAway.create(frame.data());
                    fail("Unexpected GOAWAY " + goAway.errorCode() + ": "
                                 + frame.data().readString(frame.data().available()));
                }
                if (frame.header().streamId() == 0) {
                    continue;
                }
                assertThat("Unexpected response stream", frame.header().streamId(), is(1));
                assertThat("Unexpected frame type", frame.header().type(), is(Http2FrameType.RST_STREAM));
                Http2RstStream rstStream = Http2RstStream.create(frame.data());
                assertThat(rstStream.errorCode(), is(Http2ErrorCode.PROTOCOL));
                break;
            }
            releaseHandlers.countDown();

            h2conn.request(3, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            assertOkResponse(h2conn, 3);
        }
    }

    @Test
    void rejectedRequestDoesNotConsumeConcurrentStreamLimit(Http2TestClient client) throws InterruptedException {
        try (Http2TestConnection h2conn = client.createConnection()) {
            h2conn.request(1, POST, REJECTED_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            assertErrorResponse(h2conn, 1, Status.BAD_REQUEST_400);

            h2conn.request(3, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            assertThat("Replacement stream did not start",
                       requestsStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                       is(true));

            releaseHandlers.countDown();
            assertOkResponse(h2conn, 3);
        }
    }

    @Test
    void delayedDataAndTrailersForRejectedRequestDoNotCloseConnection(Http2TestClient client) throws InterruptedException {
        try (Http2TestConnection h2conn = client.createConnection()) {
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.add(HeaderNames.CONTENT_ENCODING, "unsupported-test-encoding");
            Http2Headers h2Headers = Http2Headers.create(requestHeaders);
            h2Headers.method(POST);
            h2Headers.path(BLOCKING_PATH);
            h2Headers.scheme(h2conn.clientUri().scheme());
            h2Headers.authority(h2conn.clientUri().authority());
            h2conn.writer().writeHeaders(h2Headers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                         FlowControl.Outbound.NOOP);

            assertErrorResponse(h2conn, 1, Status.UNSUPPORTED_MEDIA_TYPE_415);
            Http2RstStream rstStream = h2conn.assertRstStream(1, TIMEOUT);
            assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));

            h2conn.request(3, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            assertThat("Replacement stream did not start",
                       requestsStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                       is(true));

            BufferData delayedBody = BufferData.create("late body");
            h2conn.writer().writeData(new Http2FrameData(Http2FrameHeader.create(delayedBody.available(),
                                                                                 Http2FrameTypes.DATA,
                                                                                 Http2Flag.DataFlags.create(0),
                                                                                 1),
                                                         delayedBody),
                                      FlowControl.Outbound.NOOP);
            Http2Headers trailers = Http2Headers.create(WritableHeaders.create()
                                                                    .add(HeaderNames.create("x-trailer"), "done"));
            h2conn.writer().writeHeaders(trailers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                         FlowControl.Outbound.NOOP);

            releaseHandlers.countDown();
            assertOkResponse(h2conn, 3);
        }
    }

    @Test
    void resetForLocallyResetRequestDoesNotCloseConnection(Http2TestClient client) throws InterruptedException {
        try (Http2TestConnection h2conn = client.createConnection()) {
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.add(HeaderNames.CONTENT_ENCODING, "unsupported-test-encoding");
            Http2Headers h2Headers = Http2Headers.create(requestHeaders);
            h2Headers.method(POST);
            h2Headers.path(BLOCKING_PATH);
            h2Headers.scheme(h2conn.clientUri().scheme());
            h2Headers.authority(h2conn.clientUri().authority());
            h2conn.writer().writeHeaders(h2Headers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                         FlowControl.Outbound.NOOP);

            assertErrorResponse(h2conn, 1, Status.UNSUPPORTED_MEDIA_TYPE_415);
            Http2RstStream rstStream = h2conn.assertRstStream(1, TIMEOUT);
            assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));

            h2conn.request(3, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            assertThat("Replacement stream did not start",
                       requestsStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                       is(true));

            Http2RstStream resetFrame = new Http2RstStream(Http2ErrorCode.CANCEL);
            h2conn.writer().writeData(resetFrame.toFrameData(Http2Settings.builder().build(),
                                                             1,
                                                             Http2Flag.NoFlags.create()),
                                      FlowControl.Outbound.NOOP);

            releaseHandlers.countDown();
            assertOkResponse(h2conn, 3);
        } finally {
            releaseHandlers.countDown();
        }
    }

    @Test
    void lateResetForAlreadyEndedLocallyResetRequestDoesNotCloseConnection(Http2TestClient client) throws InterruptedException {
        try (Http2TestConnection h2conn = client.createConnection()) {
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.add(HeaderNames.CONTENT_LENGTH, 1);
            Http2Headers h2Headers = Http2Headers.create(requestHeaders);
            h2Headers.method(POST);
            h2Headers.path(BLOCKING_PATH);
            h2Headers.scheme(h2conn.clientUri().scheme());
            h2Headers.authority(h2conn.clientUri().authority());
            h2conn.writer().writeHeaders(h2Headers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                         FlowControl.Outbound.NOOP);

            for (;;) {
                Http2FrameData frame = h2conn.awaitNextFrame(TIMEOUT);
                assertThat("Timed out waiting for RST_STREAM frame", frame, notNullValue());
                if (frame.header().streamId() == 0) {
                    continue;
                }
                assertThat("Unexpected response stream", frame.header().streamId(), is(1));
                assertThat("Unexpected frame type", frame.header().type(), is(Http2FrameType.RST_STREAM));
                Http2RstStream rstStream = Http2RstStream.create(frame.data());
                assertThat(rstStream.errorCode(), is(Http2ErrorCode.PROTOCOL));
                break;
            }

            writeEmptyPostRequest(h2conn, 3);
            assertThat("Replacement stream did not start",
                       requestsStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                       is(true));

            Http2RstStream resetFrame = new Http2RstStream(Http2ErrorCode.CANCEL);
            h2conn.writer().writeData(resetFrame.toFrameData(Http2Settings.builder().build(),
                                                             1,
                                                             Http2Flag.NoFlags.create()),
                                      FlowControl.Outbound.NOOP);

            releaseHandlers.countDown();
            assertOkResponse(h2conn, 3);
        } finally {
            releaseHandlers.countDown();
        }
    }

    @Test
    void abandonedLocallyResetRequestsAreBounded(Http2TestClient client) {
        try (Http2TestConnection h2conn = client.createConnection()) {
            for (int streamId = 1; streamId < 128; streamId += 2) {
                writeRejectedRequestHeaders(h2conn, streamId);

                assertErrorResponse(h2conn, streamId, Status.UNSUPPORTED_MEDIA_TYPE_415);
                Http2RstStream rstStream = h2conn.assertRstStream(streamId, TIMEOUT);
                assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));
            }
            writeRejectedRequestHeaders(h2conn, 129);
            if (assertResetOverflowOrRejectedStream(h2conn, 129)) {
                return;
            }

            BufferData delayedBody = BufferData.create("late body");
            h2conn.writer().writeData(new Http2FrameData(Http2FrameHeader.create(delayedBody.available(),
                                                                                 Http2FrameTypes.DATA,
                                                                                 Http2Flag.DataFlags.create(
                                                                                         Http2Flag.END_OF_STREAM),
                                                                                 1),
                                                         delayedBody),
                                      FlowControl.Outbound.NOOP);

            assertGoAwayError(h2conn, Http2ErrorCode.ENHANCE_YOUR_CALM);
        }
    }

    @Test
    void abandonedLocallyResetRequestHeadersAreNotReused(Http2TestClient client) {
        try (Http2TestConnection h2conn = client.createConnection()) {
            for (int streamId = 1; streamId < 128; streamId += 2) {
                writeRejectedRequestHeaders(h2conn, streamId);

                assertErrorResponse(h2conn, streamId, Status.UNSUPPORTED_MEDIA_TYPE_415);
                Http2RstStream rstStream = h2conn.assertRstStream(streamId, TIMEOUT);
                assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));
            }
            writeRejectedRequestHeaders(h2conn, 129);
            if (assertResetOverflowOrRejectedStream(h2conn, 129)) {
                return;
            }

            Http2Headers h2Headers = Http2Headers.create(WritableHeaders.create());
            h2Headers.method(POST);
            h2Headers.path(BLOCKING_PATH);
            h2Headers.scheme(h2conn.clientUri().scheme());
            h2Headers.authority(h2conn.clientUri().authority());
            h2conn.writer().writeHeaders(h2Headers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                         FlowControl.Outbound.NOOP);

            assertGoAwayError(h2conn, Http2ErrorCode.ENHANCE_YOUR_CALM);
        }
    }

    @Test
    void nonTerminalHeadersForRejectedRequestDoNotCloseConnection(Http2TestClient client) throws InterruptedException {
        try (Http2TestConnection h2conn = client.createConnection()) {
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.add(HeaderNames.CONTENT_ENCODING, "unsupported-test-encoding");
            Http2Headers h2Headers = Http2Headers.create(requestHeaders);
            h2Headers.method(POST);
            h2Headers.path(BLOCKING_PATH);
            h2Headers.scheme(h2conn.clientUri().scheme());
            h2Headers.authority(h2conn.clientUri().authority());
            h2conn.writer().writeHeaders(h2Headers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                         FlowControl.Outbound.NOOP);

            assertErrorResponse(h2conn, 1, Status.UNSUPPORTED_MEDIA_TYPE_415);
            Http2RstStream rstStream = h2conn.assertRstStream(1, TIMEOUT);
            assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));

            Http2Headers trailers = Http2Headers.create(WritableHeaders.create()
                                                                        .add(HeaderNames.create("x-trailer"), "done"));
            h2conn.writer().writeHeaders(trailers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                         FlowControl.Outbound.NOOP);

            h2conn.request(3, POST, BLOCKING_PATH, WritableHeaders.create(), BufferData.create(new byte[0]));
            assertThat("Replacement stream did not start",
                       requestsStarted.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                       is(true));

            BufferData delayedBody = BufferData.create("late body");
            h2conn.writer().writeData(new Http2FrameData(Http2FrameHeader.create(delayedBody.available(),
                                                                                 Http2FrameTypes.DATA,
                                                                                 Http2Flag.DataFlags.create(
                                                                                         Http2Flag.END_OF_STREAM),
                                                                                 1),
                                                         delayedBody),
                                      FlowControl.Outbound.NOOP);

            releaseHandlers.countDown();
            assertOkResponse(h2conn, 3);
        } finally {
            releaseHandlers.countDown();
        }
    }

    @Test
    void paddedContinuedHeadersForRejectedRequestDoNotCloseConnection(Http2TestClient client) throws InterruptedException {
        try (Http2TestConnection h2conn = client.createConnection()) {
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.add(HeaderNames.CONTENT_ENCODING, "unsupported-test-encoding");
            Http2Headers h2Headers = Http2Headers.create(requestHeaders);
            h2Headers.method(POST);
            h2Headers.path(BLOCKING_PATH);
            h2Headers.scheme(h2conn.clientUri().scheme());
            h2Headers.authority(h2conn.clientUri().authority());
            h2conn.writer().writeHeaders(h2Headers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                         FlowControl.Outbound.NOOP);

            assertErrorResponse(h2conn, 1, Status.UNSUPPORTED_MEDIA_TYPE_415);
            Http2RstStream rstStream = h2conn.assertRstStream(1, TIMEOUT);
            assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));

            Http2Headers trailers = Http2Headers.create(WritableHeaders.create()
                                                                        .add(HeaderNames.create("x-hpack-proof"), "done"));
            BufferData headerBlock = BufferData.growing(256);
            trailers.write(requestDynamicTable, Http2HuffmanEncoder.create(), headerBlock);
            byte[] headerBytes = headerBlock.readBytes();
            int split = Math.max(1, headerBytes.length / 2);
            BufferData firstFragment = BufferData.growing(split + 2);
            firstFragment.write(1);
            firstFragment.write(Arrays.copyOf(headerBytes, split));
            firstFragment.write(0);

            h2conn.writer().write(new Http2FrameData(
                    Http2FrameHeader.create(firstFragment.available(),
                                            Http2FrameTypes.HEADERS,
                                            Http2Flag.HeaderFlags.create(Http2Flag.PADDED | Http2Flag.END_OF_STREAM),
                                            1),
                    firstFragment));
            h2conn.writer().write(new Http2FrameData(
                    Http2FrameHeader.create(headerBytes.length - split,
                                            Http2FrameTypes.CONTINUATION,
                                            Http2Flag.ContinuationFlags.create(Http2Flag.END_OF_HEADERS),
                                            1),
                    BufferData.create(Arrays.copyOfRange(headerBytes, split, headerBytes.length))));

            writeEmptyPostRequest(h2conn, 3);
            releaseHandlers.countDown();
            assertOkResponse(h2conn, 3);
        } finally {
            releaseHandlers.countDown();
        }
    }

    @Test
    void completeHeadersFloodForRejectedRequestIsBounded(Http2TestClient client) {
        try (Http2TestConnection h2conn = client.createConnection()) {
            writeRejectedRequestHeaders(h2conn, 1);

            assertErrorResponse(h2conn, 1, Status.UNSUPPORTED_MEDIA_TYPE_415);
            Http2RstStream rstStream = h2conn.assertRstStream(1, TIMEOUT);
            assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));

            for (int i = 0; i < 65; i++) {
                Http2Headers trailers = Http2Headers.create(WritableHeaders.create()
                                                                            .add(HeaderNames.create("x-trailer"),
                                                                                 String.valueOf(i)));
                h2conn.writer().writeHeaders(trailers,
                                             1,
                                             Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                             FlowControl.Outbound.NOOP);
            }

            assertGoAwayError(h2conn, Http2ErrorCode.ENHANCE_YOUR_CALM);
        }
    }

    @Test
    void emptyContinuationFloodForRejectedRequestIsBounded(Http2TestClient client) {
        try (Http2TestConnection h2conn = client.createConnection()) {
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.add(HeaderNames.CONTENT_ENCODING, "unsupported-test-encoding");
            Http2Headers h2Headers = Http2Headers.create(requestHeaders);
            h2Headers.method(POST);
            h2Headers.path(BLOCKING_PATH);
            h2Headers.scheme(h2conn.clientUri().scheme());
            h2Headers.authority(h2conn.clientUri().authority());
            h2conn.writer().writeHeaders(h2Headers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                         FlowControl.Outbound.NOOP);

            assertErrorResponse(h2conn, 1, Status.UNSUPPORTED_MEDIA_TYPE_415);
            Http2RstStream rstStream = h2conn.assertRstStream(1, TIMEOUT);
            assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));

            Http2Headers trailers = Http2Headers.create(WritableHeaders.create()
                                                                        .add(HeaderNames.create("x-trailer"), "done"));
            h2conn.writer().writeHeaders(trailers,
                                         1,
                                         Http2Flag.HeaderFlags.create(0),
                                         FlowControl.Outbound.NOOP);

            for (int i = 0; i < 12; i++) {
                h2conn.writer().write(new Http2FrameData(Http2FrameHeader.create(0,
                                                                                 Http2FrameTypes.CONTINUATION,
                                                                                 Http2Flag.ContinuationFlags.create(0),
                                                                                 1),
                                                     BufferData.empty()));
            }

            h2conn.assertGoAway(Http2ErrorCode.ENHANCE_YOUR_CALM,
                                "Too much subsequent empty frames received.",
                                TIMEOUT);
        }
    }

    @Test
    void nonTerminalDataFloodForRejectedRequestIsBounded(Http2TestClient client) {
        try (Http2TestConnection h2conn = client.createConnection()) {
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.add(HeaderNames.CONTENT_ENCODING, "unsupported-test-encoding");
            Http2Headers h2Headers = Http2Headers.create(requestHeaders);
            h2Headers.method(POST);
            h2Headers.path(BLOCKING_PATH);
            h2Headers.scheme(h2conn.clientUri().scheme());
            h2Headers.authority(h2conn.clientUri().authority());
            h2conn.writer().writeHeaders(h2Headers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                         FlowControl.Outbound.NOOP);

            assertErrorResponse(h2conn, 1, Status.UNSUPPORTED_MEDIA_TYPE_415);
            Http2RstStream rstStream = h2conn.assertRstStream(1, TIMEOUT);
            assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));

            for (int i = 0; i < 70; i++) {
                BufferData delayedBody = BufferData.create(new byte[1024]);
                h2conn.writer().writeData(new Http2FrameData(Http2FrameHeader.create(delayedBody.available(),
                                                                                     Http2FrameTypes.DATA,
                                                                                     Http2Flag.DataFlags.create(0),
                                                                                     1),
                                                             delayedBody),
                                          FlowControl.Outbound.NOOP);
            }

            assertGoAwayError(h2conn, Http2ErrorCode.ENHANCE_YOUR_CALM);
        }
    }

    @Test
    void nonEmptyContinuationFloodForRejectedRequestIsBounded(Http2TestClient client) {
        try (Http2TestConnection h2conn = client.createConnection()) {
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.add(HeaderNames.CONTENT_ENCODING, "unsupported-test-encoding");
            Http2Headers h2Headers = Http2Headers.create(requestHeaders);
            h2Headers.method(POST);
            h2Headers.path(BLOCKING_PATH);
            h2Headers.scheme(h2conn.clientUri().scheme());
            h2Headers.authority(h2conn.clientUri().authority());
            h2conn.writer().writeHeaders(h2Headers,
                                         1,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                         FlowControl.Outbound.NOOP);

            assertErrorResponse(h2conn, 1, Status.UNSUPPORTED_MEDIA_TYPE_415);
            Http2RstStream rstStream = h2conn.assertRstStream(1, TIMEOUT);
            assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));

            Http2Headers trailers = Http2Headers.create(WritableHeaders.create()
                                                                        .add(HeaderNames.create("x-trailer"), "done"));
            h2conn.writer().writeHeaders(trailers,
                                         1,
                                         Http2Flag.HeaderFlags.create(0),
                                         FlowControl.Outbound.NOOP);

            for (int i = 0; i < 70; i++) {
                BufferData continuation = BufferData.create(new byte[1024]);
                h2conn.writer().write(new Http2FrameData(Http2FrameHeader.create(continuation.available(),
                                                                                 Http2FrameTypes.CONTINUATION,
                                                                                 Http2Flag.ContinuationFlags.create(0),
                                                                                 1),
                                                     continuation));
            }

            assertGoAwayError(h2conn, Http2ErrorCode.ENHANCE_YOUR_CALM);
        }
    }

    private void assertRejectedStreamTerminal(Http2TestConnection h2conn, int streamId) {
        for (;;) {
            Http2FrameData frame = h2conn.awaitNextFrame(TIMEOUT);
            assertThat("Timed out waiting for rejected stream terminal frame", frame, notNullValue());

            if (frame.header().type() == Http2FrameType.GO_AWAY) {
                Http2GoAway goAway = Http2GoAway.create(frame.data());
                fail("Unexpected GOAWAY " + goAway.errorCode() + ": " + frame.data().readString(frame.data().available()));
            }
            if (frame.header().streamId() == 0) {
                continue;
            }
            assertThat("Unexpected response stream", frame.header().streamId(), is(streamId));
            if (frame.header().type() == Http2FrameType.DATA) {
                assertThat(frame.header().flags(Http2FrameTypes.DATA).endOfStream(), is(true));
                return;
            } else if (frame.header().type() == Http2FrameType.RST_STREAM) {
                Http2RstStream rstStream = Http2RstStream.create(frame.data());
                assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));
                return;
            } else {
                fail("Unexpected HTTP/2 frame " + frame.header().type() + " for stream " + frame.header().streamId());
            }
        }
    }

    private void writeRejectedRequestHeaders(Http2TestConnection h2conn, int streamId) {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.add(HeaderNames.CONTENT_ENCODING, "unsupported-test-encoding");
        Http2Headers h2Headers = Http2Headers.create(requestHeaders);
        h2Headers.method(POST);
        h2Headers.path(BLOCKING_PATH);
        h2Headers.scheme(h2conn.clientUri().scheme());
        h2Headers.authority(h2conn.clientUri().authority());
        h2conn.writer().writeHeaders(h2Headers,
                                     streamId,
                                         Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                         FlowControl.Outbound.NOOP);
    }

    private void writeEmptyPostRequest(Http2TestConnection h2conn, int streamId) {
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.add(HeaderNames.CONTENT_LENGTH, "0");
        requestHeaders.add(HeaderNames.create("x-hpack-proof"), "done");
        Http2Headers h2Headers = Http2Headers.create(requestHeaders);
        h2Headers.method(POST);
        h2Headers.path(BLOCKING_PATH);
        h2Headers.scheme(h2conn.clientUri().scheme());
        h2Headers.authority(h2conn.clientUri().authority());
        BufferData headerBlock = BufferData.growing(256);
        h2Headers.write(requestDynamicTable, Http2HuffmanEncoder.create(), headerBlock);
        h2conn.writer().write(new Http2FrameData(
                Http2FrameHeader.create(headerBlock.available(),
                                        Http2FrameTypes.HEADERS,
                                        Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                        streamId),
                headerBlock));
    }

    private boolean assertResetOverflowOrRejectedStream(Http2TestConnection h2conn, int streamId) {
        for (;;) {
            Http2FrameData frame = h2conn.awaitNextFrame(TIMEOUT);
            assertThat("Timed out waiting for rejected stream terminal frame", frame, notNullValue());

            if (frame.header().type() == Http2FrameType.GO_AWAY) {
                Http2GoAway goAway = Http2GoAway.create(frame.data());
                assertThat(goAway.errorCode(), is(Http2ErrorCode.ENHANCE_YOUR_CALM));
                return true;
            }
            if (frame.header().streamId() != streamId) {
                continue;
            }
            if (frame.header().type() == Http2FrameType.RST_STREAM) {
                Http2RstStream rstStream = Http2RstStream.create(frame.data());
                assertThat(rstStream.errorCode(), is(Http2ErrorCode.CANCEL));
                return false;
            }
        }
    }

    private void assertGoAwayError(Http2TestConnection h2conn, Http2ErrorCode errorCode) {
        for (;;) {
            Http2FrameData frame = h2conn.awaitNextFrame(TIMEOUT);
            assertThat("Timed out waiting for GOAWAY", frame, notNullValue());

            if (frame.header().type() != Http2FrameType.GO_AWAY) {
                continue;
            }

            Http2GoAway goAway = Http2GoAway.create(frame.data());
            assertThat(goAway.errorCode(), is(errorCode));
            return;
        }
    }

    private void assertErrorResponse(Http2TestConnection h2conn, int streamId, Status status) {
        boolean headersSeen = false;
        boolean dataSeen = false;

        while (!headersSeen || !dataSeen) {
            Http2FrameData frame = h2conn.awaitNextFrame(TIMEOUT);
            assertThat("Timed out waiting for error response frame", frame, notNullValue());

            if (frame.header().type() == Http2FrameType.GO_AWAY) {
                Http2GoAway goAway = Http2GoAway.create(frame.data());
                fail("Unexpected GOAWAY " + goAway.errorCode() + ": " + frame.data().readString(frame.data().available()));
            }
            if (frame.header().streamId() == 0) {
                continue;
            }
            assertThat("Unexpected response stream", frame.header().streamId(), is(streamId));

            if (frame.header().type() == Http2FrameType.HEADERS) {
                Http2Headers headers = Http2Headers.create(null, responseDynamicTable, responseHuffman, frame);
                assertThat(headers.status(), is(status));
                headersSeen = true;
                if (frame.header().flags(Http2FrameTypes.HEADERS).endOfStream()) {
                    return;
                }
            } else if (frame.header().type() == Http2FrameType.DATA) {
                assertThat(frame.header().flags(Http2FrameTypes.DATA).endOfStream(), is(true));
                dataSeen = true;
            } else {
                fail("Unexpected HTTP/2 frame " + frame.header().type() + " for stream " + frame.header().streamId());
            }
        }
    }

    private void assertOkResponse(Http2TestConnection h2conn, int streamId) {
        boolean headersSeen = false;
        boolean dataSeen = false;

        while (!headersSeen || !dataSeen) {
            Http2FrameData frame = h2conn.awaitNextFrame(TIMEOUT);
            assertThat("Timed out waiting for response frame", frame, notNullValue());

            if (frame.header().type() == Http2FrameType.GO_AWAY) {
                Http2GoAway goAway = Http2GoAway.create(frame.data());
                fail("Unexpected GOAWAY " + goAway.errorCode() + ": " + frame.data().readString(frame.data().available()));
            }
            if (frame.header().streamId() == 0) {
                continue;
            }
            assertThat("Unexpected response stream", frame.header().streamId(), is(streamId));

            if (frame.header().type() == Http2FrameType.HEADERS) {
                Http2Headers headers = Http2Headers.create(null, responseDynamicTable, responseHuffman, frame);
                assertThat(headers.status(), is(Status.OK_200));
                headersSeen = true;
            } else if (frame.header().type() == Http2FrameType.DATA) {
                assertThat(frame.header().flags(Http2FrameTypes.DATA).endOfStream(), is(true));
                dataSeen = true;
            } else {
                fail("Unexpected HTTP/2 frame " + frame.header().type() + " for stream " + frame.header().streamId());
            }
        }
    }
}
