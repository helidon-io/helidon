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

package io.helidon.webclient.http2;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.mapper.MapperException;
import io.helidon.common.mapper.Value;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameListener;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2LoggingFrameListener;
import io.helidon.http.http2.Http2Ping;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2ClientConnectionPingTest {
    private static final Duration TEST_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final String CLIENT_SEND_LOGGER_NAME = Http2LoggingFrameListener.class.getName() + ".cl-send";
    private static final Header VALID_REGULAR_HEADER = HeaderValues.create("Valid-Header-Name", "Valid-Header-Value");
    private static final Header INVALID_REGULAR_HEADER = HeaderValues.create("Valid-Header-Name", "Header\u001fValue");
    private static final Header INFORMATIONAL_REGULAR_HEADER =
            HeaderValues.create("Informational-Header-Name", "Informational-Header-Value");
    private static final Http2StreamConfig STREAM_CONFIG = new Http2StreamConfig() {
        @Override
        public boolean priorKnowledge() {
            return true;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Duration readTimeout() {
            return Duration.ofSeconds(1);
        }
    };

    @Test
    void streamSendPingWritesFullFrameEachTime() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();

        stream.sendPing();
        stream.sendPing();

        assertThat(test.writes(), hasSize(2));
        assertThat(test.writes().get(0).available(), is(17));
        assertThat(test.writes().get(1).available(), is(17));
        assertThat(readPingPayloadId(test.writes().get(0)), is(0L));
        assertThat(readPingPayloadId(test.writes().get(1)), is(0L));
    }

    @Test
    void streamWindowUpdateRestoresCreditOnce() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), false);
        test.connection().flowControl().incrementOutboundConnectionWindowSize(100_000);
        int before = stream.flowControl().outbound().getRemainingWindowSize();

        stream.windowUpdate(new Http2WindowUpdate(1024));

        assertThat(stream.flowControl().outbound().getRemainingWindowSize(), is(before + 1024));
    }

    @Test
    void resetClosesStreamWindowUpdateProducerBeforeWritingReset() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        AtomicReference<Http2ClientStream> streamRef = new AtomicReference<>();
        Http2FrameListener sendListener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                if (header.type() == Http2FrameType.RST_STREAM) {
                    streamRef.get().incrementInboundWindowSize(1);
                }
            }
        };
        Http2ClientStream stream = new Http2ClientStream(test.connection,
                                                         Http2Settings.create(),
                                                         test.socket,
                                                         STREAM_CONFIG,
                                                         test.clientConfig,
                                                         test.streamIdSequence,
                                                         sendListener,
                                                         Http2FrameListener.create(List.of()));
        streamRef.set(stream);
        stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), false);
        stream.flowControl().inbound().decrementWindowSize(1);
        test.writes().clear();

        stream.cancel();

        List<Http2FrameType> frameTypes = new ArrayList<>();
        List<Integer> streamIds = new ArrayList<>();
        for (BufferData write : test.writes()) {
            Http2FrameHeader header = Http2FrameHeader.create(write.copy());
            frameTypes.add(header.type());
            streamIds.add(header.streamId());
        }
        assertThat(frameTypes, is(List.of(Http2FrameType.WINDOW_UPDATE, Http2FrameType.RST_STREAM)));
        assertThat(streamIds, is(List.of(0, stream.streamId())));
    }

    @Test
    void pingWaitsForMatchingAck() throws Exception {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofSeconds(1));
        CompletableFuture<Boolean> pingFuture = startPing(test);

        long pingPayload = readPingPayloadId(test.awaitWrite());
        assertThat(pingPayload, is(1L));
        test.connection().pong(pingPayload + 1);
        assertThat(pingFuture.isDone(), is(false));
        test.connection().pong(pingPayload);

        assertThat(pingFuture.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    void pingTimesOutWhenOnlyUnmatchedAckArrives() throws Exception {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        CompletableFuture<Boolean> pingFuture = startPing(test);

        long pingPayload = readPingPayloadId(test.awaitWrite());
        assertThat(pingPayload, is(1L));
        test.connection().pong(pingPayload + 1);

        assertThat(pingFuture.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(false));
    }

    @Test
    void connectionPingUsesClientSendListener() throws Exception {
        Http2FrameListener sendListener = mock(Http2FrameListener.class);
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofSeconds(1), sendListener);
        CompletableFuture<Boolean> pingFuture = startPing(test);

        long pingPayload = readPingPayloadId(test.awaitWrite());
        test.connection().pong(pingPayload);

        assertThat(pingFuture.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
        verify(sendListener).frameHeader(eq(test.socket), eq(0), any(Http2FrameHeader.class));
        verify(sendListener).frame(eq(test.socket), eq(0), any(Http2Ping.class));
    }

    @Test
    void sendLogFalseSuppressesConnectionPingLogs() throws Exception {
        Logger logger = Logger.getLogger(CLIENT_SEND_LOGGER_NAME);
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.FINER);

        try {
            MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100), false);
            CompletableFuture<Boolean> pingFuture = startPing(test);
            long pingPayload = readPingPayloadId(test.awaitWrite());
            test.connection().pong(pingPayload);

            assertThat(pingFuture.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
            assertThat(messages, hasSize(0));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @Test
    void requestHeaderValidationRejectsInvalidRegularHeader() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();

        Http2Exception exception = assertThrows(Http2Exception.class,
                                                () -> stream.writeHeaders(requestHeaders(INVALID_REGULAR_HEADER), true));

        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
        assertThat(test.writes(), hasSize(0));
    }

    @Test
    void requestHeaderValidationCanBeDisabled() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100),
                                                                          true,
                                                                          false,
                                                                          true);
        Http2ClientStream stream = test.newStream();

        stream.writeHeaders(requestHeaders(INVALID_REGULAR_HEADER), true);

        assertThat(test.writes(), hasSize(1));
    }

    @Test
    void responseHeaderValidationRejectsInvalidRegularHeader() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), true);
        pushResponseHeaders(test, responseHeadersFrame(responseHeaders(INVALID_REGULAR_HEADER)));

        Http2Exception exception = assertThrows(Http2Exception.class, stream::readHeaders);

        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
    }

    @Test
    void responseHeaderValidationFailureResetsAndClosesStream() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), true);
        pushResponseHeaders(test, responseHeadersFrame(responseHeaders(INVALID_REGULAR_HEADER)));

        Http2Exception exception = assertThrows(Http2Exception.class, () -> Http2CallChainBase.readHeaders(stream));

        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
        assertThat(test.connection().stream(stream.streamId()), nullValue());
        assertThat(test.writes(), hasSize(2));
    }

    @Test
    void responseHeaderValidationFailureRemovesStreamBeforeReset() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), true);
        test.dataWriter.onWrite = () -> assertThat(test.connection().stream(stream.streamId()), nullValue());
        pushResponseHeaders(test, responseHeadersFrame(responseHeaders(INVALID_REGULAR_HEADER)));

        assertThrows(Http2Exception.class, () -> Http2CallChainBase.readHeaders(stream));
    }

    @Test
    void responseHeaderValidationFailureDropsLaterDataWithoutFailingConnection() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), true);
        pushResponseHeaders(test, responseHeadersFrame(responseHeaders(INVALID_REGULAR_HEADER), false));
        BufferData data = BufferData.create("data");
        Http2FrameData frameData = new Http2FrameData(Http2FrameHeader.create(data.available(),
                                                                              Http2FrameTypes.DATA,
                                                                              Http2Flag.DataFlags.create(0),
                                                                              stream.streamId()),
                                                      data);

        assertThat(test.connection().handle(frameData.header(), frameData.data()), is(true));
        Http2Exception exception = assertThrows(Http2Exception.class, () -> Http2CallChainBase.readHeaders(stream));

        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
        assertThat(test.connection().stream(stream.streamId()), nullValue());
        assertThat(test.writes(), hasSize(3));
    }

    @Test
    void peerResetDuringResponseHeaderValidationDoesNotFailConnection() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), true);
        RacingHeader header = new RacingHeader(() -> handleRstStream(test, stream.streamId()));
        Http2FrameData frameData = responseHeadersFrame(responseHeaders(header));
        header.arm();
        pushResponseHeaders(test, frameData);

        Http2Exception exception = assertThrows(Http2Exception.class, () -> Http2CallChainBase.readHeaders(stream));

        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
        assertThat(test.connection().stream(stream.streamId()), nullValue());
    }

    @Test
    void continueHeaderValidationFailureResetsAndClosesStream() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(HeaderValues.EXPECT_100), false);
        pushResponseHeaders(test,
                            responseHeadersFrame(Http2Headers.create(writableHeaders(INVALID_REGULAR_HEADER))
                                                  .status(Status.CONTINUE_100),
                                                 false));

        Http2Exception exception = assertThrows(Http2Exception.class, () -> Http2CallChainBase.waitFor100Continue(stream));

        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
        assertThat(test.connection().stream(stream.streamId()), nullValue());
        assertThat(test.writes(), hasSize(2));
    }

    @Test
    void lateInformationalHeadersDoNotReplaceFinalResponseHeaders() {
        assertLateInformationalHeadersDoNotReplaceFinalResponseHeaders(Status.CONTINUE_100);
        assertLateInformationalHeadersDoNotReplaceFinalResponseHeaders(Status.create(103, "Early Hints"));
    }

    @Test
    void continueReceivedBeforeWaitSatisfiesContinueWait() {
        assertContinueReceivedBeforeWaitSatisfiesContinueWait(false);
        assertContinueReceivedBeforeWaitSatisfiesContinueWait(true);
    }

    @Test
    void resetAfterLateContinueDoesNotPublishContinueAsFinalHeaders() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(HeaderValues.EXPECT_100), false);

        assertThat(stream.waitFor100Continue(Duration.ZERO), is(Status.CONTINUE_100));
        pushResponseHeaders(test, responseHeadersFrame(responseStatusHeaders(Status.CONTINUE_100), false));
        handleRstStream(test, stream.streamId());

        assertThrows(IllegalStateException.class, stream::readHeaders);
    }

    @Test
    void switchingProtocolsResponseIsRejected() {
        assertInvalidInformationalHeaders(Status.SWITCHING_PROTOCOLS_101, false);
    }

    @Test
    void informationalResponseWithEndStreamIsRejected() {
        assertInvalidInformationalHeaders(Status.CONTINUE_100, true);
    }

    @Test
    void switchingProtocolsResponseWakesContinueWaiterWithProtocolFailure() throws Exception {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(HeaderValues.EXPECT_100), false);
        CompletableFuture<Throwable> waitFailure = new CompletableFuture<>();
        Thread.ofPlatform().start(() -> {
            try {
                Http2CallChainBase.waitFor100Continue(stream);
                waitFailure.complete(null);
            } catch (Throwable t) {
                waitFailure.complete(t);
            }
        });

        pushResponseHeaders(test, responseHeadersFrame(responseStatusHeaders(Status.SWITCHING_PROTOCOLS_101), false));

        Throwable failure = waitFailure.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(failure instanceof Http2Exception, is(true));
        assertThat(((Http2Exception) failure).code(), is(Http2ErrorCode.PROTOCOL));
    }

    @Test
    void informationalEndStreamWakesContinueWaiterWithProtocolFailure() throws Exception {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(HeaderValues.EXPECT_100), false);
        CompletableFuture<Throwable> waitFailure = new CompletableFuture<>();
        Thread.ofPlatform().start(() -> {
            try {
                Http2CallChainBase.waitFor100Continue(stream);
                waitFailure.complete(null);
            } catch (Throwable t) {
                waitFailure.complete(t);
            }
        });

        pushResponseHeaders(test, responseHeadersFrame(responseStatusHeaders(Status.CONTINUE_100), true));

        Throwable failure = waitFailure.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(failure instanceof Http2Exception, is(true));
        assertThat(((Http2Exception) failure).code(), is(Http2ErrorCode.PROTOCOL));
    }

    @Test
    void responseHeaderValidationCanBeDisabled() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100),
                                                                          true,
                                                                          true,
                                                                          false);
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), true);
        pushResponseHeaders(test, responseHeadersFrame(responseHeaders(INVALID_REGULAR_HEADER)));

        Http2Headers responseHeaders = stream.readHeaders();

        assertThat(responseHeaders.status(), is(Status.OK_200));
        assertThat(responseHeaders.httpHeaders().get(INVALID_REGULAR_HEADER.headerName()).get(),
                   is(INVALID_REGULAR_HEADER.get()));
    }

    @Test
    void closedStreamRstStreamIsIgnored() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newClosedStream();
        Http2RstStream rstStream = new Http2RstStream(Http2ErrorCode.CANCEL);
        Http2FrameData frameData = rstStream.toFrameData(Http2Settings.create(), stream.streamId(), Http2Flag.NoFlags.create());

        assertThat(test.connection().handle(frameData.header(), frameData.data()), is(true));
    }

    @Test
    void idleStreamRstStreamFails() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2RstStream rstStream = new Http2RstStream(Http2ErrorCode.CANCEL);
        Http2FrameData frameData = rstStream.toFrameData(Http2Settings.create(), 1, Http2Flag.NoFlags.create());

        Http2Exception exception = assertThrows(Http2Exception.class,
                                                () -> test.connection().handle(frameData.header(), frameData.data()));

        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
    }

    @Test
    void lowerNeverOpenedRstStreamFails() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        test.newClosedStream();
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), true);
        Http2RstStream rstStream = new Http2RstStream(Http2ErrorCode.CANCEL);
        Http2FrameData frameData = rstStream.toFrameData(Http2Settings.create(), 2, Http2Flag.NoFlags.create());

        Http2Exception exception = assertThrows(Http2Exception.class,
                                                () -> test.connection().handle(frameData.header(), frameData.data()));

        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
    }

    @Test
    void closedStreamWindowUpdateIsIgnored() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newClosedStream();
        Http2WindowUpdate windowUpdate = new Http2WindowUpdate(1);
        Http2FrameData frameData = windowUpdate.toFrameData(Http2Settings.create(), stream.streamId(), Http2Flag.NoFlags.create());

        assertThat(test.connection().handle(frameData.header(), frameData.data()), is(true));
    }

    @Test
    void idleStreamWindowUpdateFails() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2WindowUpdate windowUpdate = new Http2WindowUpdate(1);
        Http2FrameData frameData = windowUpdate.toFrameData(Http2Settings.create(), 1, Http2Flag.NoFlags.create());

        Http2Exception exception = assertThrows(Http2Exception.class,
                                                () -> test.connection().handle(frameData.header(), frameData.data()));

        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
    }

    @Test
    void lowerNeverOpenedWindowUpdateFails() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        test.newClosedStream();
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), true);
        Http2WindowUpdate windowUpdate = new Http2WindowUpdate(1);
        Http2FrameData frameData = windowUpdate.toFrameData(Http2Settings.create(), 2, Http2Flag.NoFlags.create());

        Http2Exception exception = assertThrows(Http2Exception.class,
                                                () -> test.connection().handle(frameData.header(), frameData.data()));

        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
    }

    private static void handleRstStream(MockedConnectionTestContext test, int streamId) {
        Http2RstStream rstStream = new Http2RstStream(Http2ErrorCode.CANCEL);
        Http2FrameData rstStreamFrame = rstStream.toFrameData(Http2Settings.create(), streamId, Http2Flag.NoFlags.create());
        assertThat(test.connection().handle(rstStreamFrame.header(), rstStreamFrame.data()), is(true));
    }

    private static void pushResponseHeaders(MockedConnectionTestContext test, Http2FrameData frameData) {
        assertThat(test.connection().handle(frameData.header(), frameData.data()), is(true));
    }

    private static CompletableFuture<Boolean> startPing(MockedConnectionTestContext test) {
        CompletableFuture<Boolean> pingFuture = new CompletableFuture<>();
        Thread.ofPlatform().start(() -> {
            try {
                pingFuture.complete(test.connection().ping());
            } catch (Throwable t) {
                pingFuture.completeExceptionally(t);
            }
        });
        return pingFuture;
    }

    private static void assertLateInformationalHeadersDoNotReplaceFinalResponseHeaders(Status informationalStatus) {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(HeaderValues.EXPECT_100), false);

        assertThat(stream.waitFor100Continue(Duration.ZERO), is(Status.CONTINUE_100));
        pushResponseHeaders(test,
                            responseHeadersFrame(responseStatusHeaders(informationalStatus,
                                                                       INFORMATIONAL_REGULAR_HEADER),
                                                 false));
        pushResponseHeaders(test, responseHeadersFrame(responseHeaders(VALID_REGULAR_HEADER), true));

        Http2Headers responseHeaders = stream.readHeaders();
        assertThat(responseHeaders.status(), is(Status.OK_200));
        assertThat(responseHeaders.httpHeaders().get(VALID_REGULAR_HEADER.headerName()).get(),
                   is(VALID_REGULAR_HEADER.get()));
        assertThat(responseHeaders.httpHeaders().contains(INFORMATIONAL_REGULAR_HEADER.headerName()), is(false));
    }

    private static void assertContinueReceivedBeforeWaitSatisfiesContinueWait(boolean followedByEarlyHints) {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(HeaderValues.EXPECT_100), false);

        pushResponseHeaders(test, responseHeadersFrame(responseStatusHeaders(Status.CONTINUE_100), false));
        if (followedByEarlyHints) {
            pushResponseHeaders(test,
                                responseHeadersFrame(responseStatusHeaders(Status.create(103, "Early Hints")),
                                                     false));
        }

        assertThat(stream.waitFor100Continue(Duration.ZERO), is(Status.CONTINUE_100));
    }

    private static void assertInvalidInformationalHeaders(Status informationalStatus, boolean endOfStream) {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();
        stream.writeHeaders(requestHeaders(HeaderValues.EXPECT_100), false);

        assertThat(stream.waitFor100Continue(Duration.ZERO), is(Status.CONTINUE_100));
        pushResponseHeaders(test, responseHeadersFrame(responseStatusHeaders(informationalStatus), endOfStream));

        Http2Exception exception = assertThrows(Http2Exception.class, stream::readHeaders);
        assertThat(exception.code(), is(Http2ErrorCode.PROTOCOL));
    }

    private static long readPingPayloadId(BufferData frame) {
        frame.rewind();
        // Captured writes contain the serialized frame header followed by the 8-byte PING payload.
        frame.skip(Http2FrameHeader.LENGTH);
        long payload = frame.readLong();
        frame.rewind();
        return payload;
    }

    private static Http2Headers requestHeaders(Header header) {
        return Http2Headers.create(writableHeaders(header))
                .method(Method.GET)
                .scheme("http")
                .path("/")
                .authority("localhost");
    }

    private static Http2Headers responseHeaders(Header header) {
        return Http2Headers.create(writableHeaders(header))
                .status(Status.OK_200);
    }

    private static Http2Headers responseStatusHeaders(Status status, Header... headers) {
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        for (Header header : headers) {
            writableHeaders.add(header);
        }
        return Http2Headers.create(writableHeaders)
                .status(status);
    }

    private static WritableHeaders<?> writableHeaders(Header header) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(header);
        return headers;
    }

    private static Http2FrameData responseHeadersFrame(Http2Headers headers) {
        return responseHeadersFrame(headers, true);
    }

    private static Http2FrameData responseHeadersFrame(Http2Headers headers, boolean endOfStream) {
        BufferData headersData = BufferData.growing(512);
        headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                      Http2HuffmanEncoder.create(),
                      headersData);
        int flags = Http2Flag.END_OF_HEADERS;
        if (endOfStream) {
            flags |= Http2Flag.END_OF_STREAM;
        }
        return new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                          Http2FrameTypes.HEADERS,
                                                          Http2Flag.HeaderFlags.create(flags),
                                                          1),
                                  headersData);
    }

    private static final class MockedConnectionTestContext {
        private final HelidonSocket socket;
        private final Http2ClientConfig clientConfig;
        private final Http2ClientConnection connection;
        private final LockingStreamIdSequence streamIdSequence = LockingStreamIdSequence.create();
        private final CaptureWriter dataWriter = new CaptureWriter();

        private MockedConnectionTestContext(Duration pingTimeout) {
            this(pingTimeout, true);
        }

        private MockedConnectionTestContext(Duration pingTimeout, boolean sendLog) {
            this(pingTimeout, sendLog, true, true);
        }

        private MockedConnectionTestContext(Duration pingTimeout,
                                            boolean sendLog,
                                            boolean validateRequestHeaders,
                                            boolean validateResponseHeaders) {
            Http2ClientProtocolConfig protocolConfig = Http2ClientProtocolConfig.builder()
                    .ping(true)
                    .pingTimeout(pingTimeout)
                    .validateRequestHeaders(validateRequestHeaders)
                    .validateResponseHeaders(validateResponseHeaders)
                    .log(it -> it.sendLog(sendLog))
                    .build();

            this.clientConfig = Http2ClientConfig.builder()
                    .protocolConfig(protocolConfig)
                    .buildPrototype();

            Http2ClientImpl client = new Http2ClientImpl(mock(WebClient.class), clientConfig);
            ClientConnection clientConnection = mock(ClientConnection.class);
            this.socket = mock(HelidonSocket.class);

            when(clientConnection.reader()).thenReturn(DataReader.create(() -> null));
            when(clientConnection.writer()).thenReturn(dataWriter);
            when(clientConnection.helidonSocket()).thenReturn(socket);
            when(socket.socketId()).thenReturn("test-socket");
            when(socket.childSocketId()).thenReturn("0");

            this.connection = new Http2ClientConnection(client, clientConnection);
        }

        private MockedConnectionTestContext(Duration pingTimeout, Http2FrameListener sendListener) {
            Http2ClientProtocolConfig protocolConfig = Http2ClientProtocolConfig.builder()
                    .ping(true)
                    .pingTimeout(pingTimeout)
                    .build();

            this.clientConfig = Http2ClientConfig.builder()
                    .protocolConfig(protocolConfig)
                    .buildPrototype();

            Http2ClientImpl client = mock(Http2ClientImpl.class);
            ClientConnection clientConnection = mock(ClientConnection.class);
            this.socket = mock(HelidonSocket.class);

            when(client.protocolConfig()).thenReturn(protocolConfig);
            when(client.sendListener()).thenReturn(sendListener);
            when(client.recvListener()).thenReturn(Http2FrameListener.create(List.of()));
            when(clientConnection.reader()).thenReturn(DataReader.create(() -> null));
            when(clientConnection.writer()).thenReturn(dataWriter);
            when(clientConnection.helidonSocket()).thenReturn(socket);
            when(socket.socketId()).thenReturn("test-socket");
            when(socket.childSocketId()).thenReturn("0");

            this.connection = new Http2ClientConnection(client, clientConnection);
        }

        private Http2ClientConnection connection() {
            return connection;
        }

        private List<BufferData> writes() {
            return dataWriter.writes;
        }

        private BufferData awaitWrite() throws InterruptedException {
            BufferData write = dataWriter.asyncWrites.poll(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (write == null) {
                fail("Timed out waiting for ping write");
            }
            return write;
        }

        private Http2ClientStream newStream() {
            return new Http2ClientStream(connection,
                                         Http2Settings.create(),
                                         socket,
                                         STREAM_CONFIG,
                                         clientConfig,
                                         streamIdSequence,
                                         Http2FrameListener.create(List.of()),
                                         Http2FrameListener.create(List.of()));
        }

        private Http2ClientStream newClosedStream() {
            Http2ClientStream stream = newStream();
            stream.writeHeaders(requestHeaders(VALID_REGULAR_HEADER), true);
            stream.close();
            return stream;
        }
    }

    private static final class RacingHeader implements Header {
        private final Header delegate = HeaderValues.create(HeaderNames.ACCEPT, INVALID_REGULAR_HEADER.get());
        private final Runnable beforeValidate;
        private boolean armed;

        private RacingHeader(Runnable beforeValidate) {
            this.beforeValidate = beforeValidate;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public HeaderName headerName() {
            return delegate.headerName();
        }

        @Override
        public List<String> allValues() {
            return delegate.allValues();
        }

        @Override
        public int valueCount() {
            return delegate.valueCount();
        }

        @Override
        public boolean sensitive() {
            return delegate.sensitive();
        }

        @Override
        public boolean changing() {
            return delegate.changing();
        }

        @Override
        public String get() {
            if (armed) {
                armed = false;
                beforeValidate.run();
            }
            return INVALID_REGULAR_HEADER.get();
        }

        @Override
        public <N> Value<N> as(Class<N> type) throws MapperException {
            return delegate.as(type);
        }

        @Override
        public <N> Value<N> as(GenericType<N> type) throws MapperException {
            return delegate.as(type);
        }

        @Override
        public <N> Value<N> as(Function<? super String, ? extends N> mapper) {
            return delegate.as(mapper);
        }

        @Override
        public Optional<String> asOptional() throws MapperException {
            return delegate.asOptional();
        }

        @Override
        public Value<Boolean> asBoolean() {
            return delegate.asBoolean();
        }

        @Override
        public Value<String> asString() {
            return delegate.asString();
        }

        @Override
        public Value<Integer> asInt() {
            return delegate.asInt();
        }

        @Override
        public Value<Long> asLong() {
            return delegate.asLong();
        }

        @Override
        public Value<Double> asDouble() {
            return delegate.asDouble();
        }

        private void arm() {
            armed = true;
        }
    }

    private static final class CaptureWriter implements DataWriter {
        private final List<BufferData> writes = new CopyOnWriteArrayList<>();
        private final LinkedBlockingQueue<BufferData> asyncWrites = new LinkedBlockingQueue<>();
        private Runnable onWrite;

        @Override
        public void write(BufferData... buffers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(BufferData buffer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeNow(BufferData... buffers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeNow(BufferData buffer) {
            Runnable onWrite = this.onWrite;
            if (onWrite != null) {
                onWrite.run();
            }
            BufferData snapshot = buffer.copy();
            writes.add(snapshot);
            asyncWrites.add(snapshot);
        }
    }
}
