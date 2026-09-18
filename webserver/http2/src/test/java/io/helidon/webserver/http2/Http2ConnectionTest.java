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

package io.helidon.webserver.http2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2Ping;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2Util;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.WindowSize;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.HttpTransportObserverSupport.ConnectionObservationContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class Http2ConnectionTest {
    private static final HeaderName LIMIT_HEADER = HeaderNames.create("x-limit");

    @Test
    void localHeaderSizeRejectsOversizedRequestBelowAdvertisedLimit() throws InterruptedException {
        Http2Config config = Http2Config.builder()
                .maxHeadersSize(256)
                .maxHeaderListSize(4096)
                .build();
        Http2FrameData[] frames = headerLimitFrames(headerLimitRequest("a".repeat(300)),
                                                   Http2Headers.DynamicTable.create(
                                                           Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                                                   true,
                                                   false);
        assertThat("Compressed request fits below the local decoded-header limit",
                   frames[0].header().length(), is(lessThan(config.maxHeadersSize())));
        HeaderLimitTestContext test = new HeaderLimitTestContext(frames);

        test.handle(config);

        test.assertHeaderSizeRejected();
        verify(test.executor, never()).submit(any(Runnable.class));
    }

    @Test
    void localHeaderSizeAllowsRequestAboveAdvertisedLimit() throws InterruptedException {
        Http2Config config = Http2Config.builder()
                .maxHeadersSize(4096)
                .maxHeaderListSize(64)
                .build();
        HeaderLimitTestContext test = new HeaderLimitTestContext(headerLimitFrames(
                headerLimitRequest("a".repeat(300)),
                Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                true,
                true));

        test.handle(config);

        assertThat(test.frames(Http2FrameType.GO_AWAY), hasSize(0));
        verify(test.executor).submit(any(Runnable.class));
    }

    @Test
    void localHeaderSizeRejectsOversizedTrailersBelowAdvertisedLimit() throws InterruptedException {
        Http2Config config = Http2Config.builder()
                .maxHeadersSize(256)
                .maxHeaderListSize(4096)
                .build();
        Http2Headers.DynamicTable table = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
        Http2FrameData[] request = headerLimitFrames(headerLimitRequest(""), table, false, false);
        Http2FrameData[] trailers = headerLimitFrames(Http2Headers.create(WritableHeaders.create()
                                                                                   .set(LIMIT_HEADER, "a".repeat(300))),
                                                      table,
                                                      true,
                                                      false);
        assertThat("Compressed trailers fit below the local decoded-header limit",
                   trailers[0].header().length(), is(lessThan(config.maxHeadersSize())));
        HeaderLimitTestContext test = new HeaderLimitTestContext(request[0], trailers[0]);

        test.handle(config);

        test.assertHeaderSizeRejected();
        verify(test.executor).submit(any(Runnable.class));
    }

    @Test
    void localHeaderSizeAllowsTrailersAboveAdvertisedLimit() throws InterruptedException {
        Http2Config config = Http2Config.builder()
                .maxHeadersSize(4096)
                .maxHeaderListSize(64)
                .build();
        Http2Headers.DynamicTable table = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
        Http2FrameData[] request = headerLimitFrames(headerLimitRequest(""), table, false, false);
        Http2FrameData[] trailers = headerLimitFrames(Http2Headers.create(WritableHeaders.create()
                                                                                   .set(LIMIT_HEADER, "a".repeat(300))),
                                                      table,
                                                      true,
                                                      true);
        HeaderLimitTestContext test = new HeaderLimitTestContext(request[0], trailers[0], trailers[1]);

        test.handle(config);

        assertThat(test.frames(Http2FrameType.GO_AWAY), hasSize(0));
        verify(test.executor).submit(any(Runnable.class));
    }

    @Test
    void advertisedHeaderListSizeCanExceedLocalHeaderSize() throws InterruptedException {
        assertAdvertisedHeaderListSize(256, 4096);
    }

    @Test
    void advertisedHeaderListSizeCanBeBelowLocalHeaderSize() throws InterruptedException {
        assertAdvertisedHeaderListSize(4096, 64);
    }

    @Test
    void zeroInitialWindowStillCreatesConnection() {
        Http2Config config = Http2Config.builder()
                .initialWindowSize(0)
                .build();

        assertDoesNotThrow(() -> new Http2Connection(http2Context(mock(DataWriter.class)), config, List.of()));
    }

    @Test
    void streamContextDoesNotRetainEmptyContinuations() {
        Http2Connection.StreamContext streamContext =
                new Http2Connection.StreamContext(1, 16_384, mock(Http2ServerStream.class));
        Http2FrameHeader headers = Http2FrameHeader.create(0,
                                                           Http2FrameTypes.HEADERS,
                                                           Http2Flag.HeaderFlags.create(0),
                                                           1);
        streamContext.addHeadersToBeContinued(headers, BufferData.empty());

        Http2FrameHeader continuation = Http2FrameHeader.create(0,
                                                                Http2FrameTypes.CONTINUATION,
                                                                Http2Flag.ContinuationFlags.create(0),
                                                                1);
        for (int i = 0; i < 1_000; i++) {
            streamContext.addContinuation(new Http2FrameData(continuation, BufferData.empty()));
        }

        assertThat(streamContext.contData().length, is(1));
    }

    @Test
    void streamContextLimitsRetainedHeaderFrames() {
        Http2Connection.StreamContext streamContext =
                new Http2Connection.StreamContext(1, Long.MAX_VALUE, mock(Http2ServerStream.class));
        Http2FrameHeader headers = Http2FrameHeader.create(1,
                                                           Http2FrameTypes.HEADERS,
                                                           Http2Flag.HeaderFlags.create(0),
                                                           1);
        streamContext.addHeadersToBeContinued(headers, BufferData.create(new byte[1]));

        Http2FrameHeader continuation = Http2FrameHeader.create(1,
                                                                Http2FrameTypes.CONTINUATION,
                                                                Http2Flag.ContinuationFlags.create(0),
                                                                1);
        for (int i = 1; i < 8_192; i++) {
            streamContext.addContinuation(new Http2FrameData(continuation, BufferData.create(new byte[1])));
        }

        Http2FrameData excessContinuation = new Http2FrameData(continuation, BufferData.create(new byte[1]));
        Http2Exception exception = assertThrows(Http2Exception.class,
                                                () -> streamContext.addContinuation(excessContinuation));
        assertAll(
                () -> assertThat(exception.code(), is(Http2ErrorCode.ENHANCE_YOUR_CALM)),
                () -> assertThat(streamContext.contData().length, is(8_192))
        );
    }

    @Test
    void pingAckWrapsUncheckedIOException() {
        DataWriter writer = mock(DataWriter.class);
        doThrow(new UncheckedIOException(new SocketException("Broken pipe")))
                .when(writer)
                .writeNow(any(BufferData.class));

        ConnectionContext ctx = http2Context(writer);

        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());
        connection.pendingPing(Http2Ping.create());

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           connection::writePingAck);

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(SocketException.class))
        );
    }

    @Test
    void pingAckWrapsSocketWriterExceptionFromSmartWriter() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HelidonSocket socket = mock(HelidonSocket.class);
        when(socket.socketId()).thenReturn("test");
        when(socket.childSocketId()).thenReturn("child");
        doThrow(new UncheckedIOException(new SocketException("Broken pipe")))
                .when(socket)
                .write(any(BufferData.class));
        SocketWriter writer = SocketWriter.create(executor, socket, 2, true);
        try {
            ConnectionContext ctx = http2Context(writer);
            Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());
            connection.pendingPing(Http2Ping.create());

            ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                               connection::writePingAck);

            assertAll(
                    () -> assertThat(exception.getCause(), instanceOf(SocketWriterException.class)),
                    () -> assertThat(exception.getCause().getCause(), instanceOf(UncheckedIOException.class)),
                    () -> assertThat(exception.getCause().getCause().getCause(), instanceOf(SocketException.class))
            );
        } finally {
            writer.close();
            executor.shutdownNow();
        }
    }

    @Test
    void streamRunnableInterruptsConnectionThreadOnSocketWriterException() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        doThrow(new SocketWriterException()).when(stream).run();
        when(stream.streamId()).thenReturn(1);
        when(stream.streamState()).thenReturn(Http2StreamState.CLOSED);
        streams.put(new Http2Connection.StreamContext(1, 8192, stream));

        boolean previouslyInterrupted = Thread.interrupted();
        try {
            new Http2Connection.StreamRunnable(streams, stream, Thread.currentThread()).run();
            streams.doMaintenance();

            assertAll(
                    () -> assertThat(Thread.currentThread().isInterrupted(), is(true)),
                    () -> assertThat(streams.get(1), is(nullValue()))
            );
        } finally {
            Thread.interrupted();
            if (previouslyInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void streamRunnableLogsUnhandledThrowable() throws Exception {
        IllegalStateException failure = new IllegalStateException("stream failure");
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        doThrow(failure).when(stream).run();
        when(stream.streamId()).thenReturn(1);
        when(stream.streamState()).thenReturn(Http2StreamState.CLOSED);
        streams.put(new Http2Connection.StreamContext(1, 8192, stream));

        try (TestLogHandler handler = TestLogHandler.install()) {
            assertDoesNotThrow(() -> new Http2Connection.StreamRunnable(streams, stream, Thread.currentThread()).run());
            streams.doMaintenance();
            LogRecord record = handler.await();

            assertAll(
                    () -> assertThat(record.getMessage(), containsString("Unhandled exception on HTTP/2 stream thread")),
                    () -> assertThat(record.getThrown(), sameInstance(failure)),
                    () -> assertThat(streams.get(1), is(nullValue()))
            );
        }
    }

    @Test
    void streamRunnableLogsUnhandledUncheckedIOException() throws Exception {
        UncheckedIOException failure = new UncheckedIOException(new IOException("stream failure"));
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        doThrow(failure).when(stream).run();
        when(stream.streamId()).thenReturn(1);
        when(stream.streamState()).thenReturn(Http2StreamState.CLOSED);
        streams.put(new Http2Connection.StreamContext(1, 8192, stream));

        try (TestLogHandler handler = TestLogHandler.install()) {
            assertDoesNotThrow(() -> new Http2Connection.StreamRunnable(streams, stream, Thread.currentThread()).run());
            streams.doMaintenance();
            LogRecord record = handler.await();

            assertAll(
                    () -> assertThat(record.getMessage(), containsString("Unhandled exception on HTTP/2 stream thread")),
                    () -> assertThat(record.getThrown(), sameInstance(failure)),
                    () -> assertThat(streams.get(1), is(nullValue()))
            );
        }
    }

    @Test
    void streamRunnableLogsServerConnectionExceptionAsServerIoIssue() throws Exception {
        ServerConnectionException failure = new ServerConnectionException("stream failure", new IOException("closed"));
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream stream = mock(Http2ServerStream.class);
        doThrow(failure).when(stream).run();
        when(stream.streamId()).thenReturn(1);
        when(stream.streamState()).thenReturn(Http2StreamState.CLOSED);
        streams.put(new Http2Connection.StreamContext(1, 8192, stream));

        boolean previouslyInterrupted = Thread.interrupted();
        try (TestLogHandler handler = TestLogHandler.install()) {
            assertDoesNotThrow(() -> new Http2Connection.StreamRunnable(streams, stream, Thread.currentThread()).run());
            boolean interrupted = Thread.currentThread().isInterrupted();
            Thread.interrupted();
            streams.doMaintenance();
            LogRecord record = handler.await();

            assertAll(
                    () -> assertThat(interrupted, is(true)),
                    () -> assertThat(record.getMessage(), containsString("server I/O issue on HTTP/2 stream thread")),
                    () -> assertThat(record.getLevel(), is(Level.FINER)),
                    () -> assertThat(record.getThrown(), sameInstance(failure)),
                    () -> assertThat(streams.get(1), is(nullValue()))
            );
        } finally {
            Thread.interrupted();
            if (previouslyInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void connectionTerminationAbortsAndRemovesEveryStream() {
        Http2ConnectionStreams streams = new Http2ConnectionStreams();
        Http2ServerStream first = mock(Http2ServerStream.class);
        Http2ServerStream second = mock(Http2ServerStream.class);
        when(first.streamId()).thenReturn(1);
        when(second.streamId()).thenReturn(3);
        streams.put(new Http2Connection.StreamContext(1, 8192, first));
        streams.put(new Http2Connection.StreamContext(3, 8192, second));
        streams.activate(1);
        streams.activate(3);

        streams.abortAll();

        verify(first).abortConnection();
        verify(second).abortConnection();
        assertAll(
                () -> assertThat(streams.isEmpty(), is(true)),
                () -> assertThat(streams.get(1), is(nullValue())),
                () -> assertThat(streams.get(3), is(nullValue()))
        );
    }

    @Test
    void peerDisconnectPreventsQueuedStreamHandlerFromStarting() {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        Http2Headers h2Headers = Http2Headers.create(WritableHeaders.create());
        h2Headers.method(Method.POST);
        h2Headers.path("/grpc");
        h2Headers.scheme("http");
        h2Headers.authority("localhost");
        BufferData headersData = BufferData.growing(512);
        h2Headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                        Http2HuffmanEncoder.create(),
                        headersData);
        input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                        Http2FrameTypes.HEADERS,
                                                                        Http2Flag.HeaderFlags.create(
                                                                                Http2Flag.END_OF_HEADERS),
                                                                        1),
                                                headersData)));
        DataReader reader = DataReader.create(input::poll);
        ExecutorService executor = mock(ExecutorService.class);
        AtomicReference<Runnable> queuedHandler = new AtomicReference<>();
        doAnswer(invocation -> {
            queuedHandler.set(invocation.getArgument(0));
            return null;
        }).when(executor).submit(any(Runnable.class));
        ConnectionContext ctx = http2Context(mock(DataWriter.class), reader);
        when(ctx.executor()).thenReturn(executor);
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        when(ctx.proxyProtocolData()).thenReturn(Optional.empty());
        Http2SubProtocolSelector.SubProtocolHandler handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        Http2SubProtocolSelector selector = (connectionContext,
                                             prologue,
                                             headers,
                                             streamWriter,
                                             streamId,
                                             serverSettings,
                                             clientSettings,
                                             streamFlowControl,
                                             currentStreamState,
                                             router) -> new SubProtocolResult(true, handler);
        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of(selector));

        assertThrows(CloseConnectionException.class,
                     () -> connection.handle(mock(Limit.class)));
        queuedHandler.get().run();

        verify(handler, never()).init();
    }

    @Test
    void closeConnectionWrapsUncheckedIOException() {
        DataWriter writer = mock(DataWriter.class);
        doThrow(new UncheckedIOException(new SocketException("Broken pipe")))
                .when(writer)
                .writeNow(any(BufferData.class));

        Http2Config config = Http2Config.builder()
                .maxRapidResets(0)
                .build();
        ConnectionContext ctx = http2Context(writer);

        Http2Connection connection = new Http2Connection(ctx, config, List.of());
        Http2ConnectionChecks checks = new Http2ConnectionChecks(config, connection);

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           checks::madeYouResetCheck);

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(SocketException.class))
        );
    }

    @Test
    void peerCloseWhileReadingPrefaceClosesConnection() {
        byte[] preface = Http2Util.prefaceData().readBytes();
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        input.add(Arrays.copyOf(preface, preface.length - 1));
        DataWriter writer = mock(DataWriter.class);
        Http2Connection connection = new Http2Connection(http2Context(writer, DataReader.create(input::poll)),
                                                         Http2Config.create(),
                                                         List.of());
        connection.expectPreface();

        CloseConnectionException exception = assertThrows(CloseConnectionException.class,
                                                          () -> connection.handle(mock(Limit.class)));

        assertThat(exception.getCause(), instanceOf(DataReader.InsufficientDataAvailableException.class));
    }

    @Test
    void h2cUpgradeRespectsConcurrentStreamLimit() throws InterruptedException {
        List<BufferData> writtenFrames = new ArrayList<>();
        DataWriter writer = mock(DataWriter.class);
        doAnswer(invocation -> {
            BufferData data = invocation.getArgument(0);
            writtenFrames.add(data.copy());
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        input.add(frameBytes(Http2Settings.builder()
                                   .build()
                                   .toFrameData(null, 0, Http2Flag.SettingsFlags.create(0))));
        ExecutorService executor = mock(ExecutorService.class);
        DataReader reader = DataReader.create(input::poll);
        ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
        StreamObservation streamObservation = mock(StreamObservation.class);
        when(connectionObservation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE))
                .thenReturn(streamObservation);
        ConnectionContext ctx = http2Context(writer, reader, connectionObservation);
        when(ctx.executor()).thenReturn(executor);
        Http2Connection connection = new Http2Connection(ctx,
                                                         Http2Config.builder()
                                                                 .sendErrorDetails(true)
                                                                 .maxConcurrentStreams(0)
                                                                 .build(),
                                                         List.of());
        Http2Headers headers = Http2Headers.create(WritableHeaders.create());
        headers.method(Method.GET);
        headers.path("/upgrade");
        headers.scheme("http");
        headers.authority("localhost");
        connection.upgradeConnectionData(HttpPrologue.create("HTTP/1.1",
                                                             "HTTP",
                                                             "1.1",
                                                             Method.GET,
                                                             "/upgrade",
                                                             false),
                                         headers);

        connection.handle(mock(Limit.class));

        verify(executor, never()).submit(any(Runnable.class));
        verify(connectionObservation).streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE);
        verify(streamObservation).close(StreamOutcome.REJECTED);
        BufferData goAwayData = writtenFrames.get(writtenFrames.size() - 1);
        byte[] headerBytes = new byte[Http2FrameHeader.LENGTH];
        goAwayData.read(headerBytes);
        Http2FrameHeader frameHeader = Http2FrameHeader.create(BufferData.create(headerBytes));
        assertThat(frameHeader.type(), is(Http2FrameType.GO_AWAY));

        byte[] payloadBytes = new byte[frameHeader.length()];
        goAwayData.read(payloadBytes);
        Http2GoAway goAway = Http2GoAway.create(BufferData.create(payloadBytes));
        assertThat(goAway.errorCode(), is(Http2ErrorCode.REFUSED_STREAM));
        assertThat(goAway.lastStreamId(), is(1));
    }

    @Test
    void requestUsesConnectionLimitWithoutRenamingHandlerThread() {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        input.add(requestHeadersFrame());
        ConnectionContext ctx = runnableConnectionContext(input);
        Limit limit = throwingLimit();
        String originalThreadName = Thread.currentThread().getName();

        try {
            assertThrows(CloseConnectionException.class,
                         () -> new Http2Connection(ctx, Http2Config.create(), List.of()).handle(limit));
            assertThat(Thread.currentThread().getName(), is(originalThreadName));
        } finally {
            Thread.currentThread().setName(originalThreadName);
        }
        verify(limit).tryAcquireOutcome(true);
    }

    @Test
    void h2cUpgradeUsesConnectionLimit() {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        input.add(frameBytes(Http2Settings.builder()
                                     .build()
                                     .toFrameData(null, 0, Http2Flag.SettingsFlags.create(0))));
        ConnectionContext ctx = runnableConnectionContext(input);
        Limit limit = throwingLimit();
        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());
        Http2Headers headers = Http2Headers.create(WritableHeaders.create());
        headers.method(Method.GET);
        headers.path("/upgrade");
        headers.scheme("http");
        headers.authority("localhost");
        connection.upgradeConnectionData(HttpPrologue.create("HTTP/1.1",
                                                             "HTTP",
                                                             "1.1",
                                                             Method.GET,
                                                             "/upgrade",
                                                             false),
                                         headers);

        assertThrows(CloseConnectionException.class, () -> connection.handle(limit));

        verify(limit).tryAcquireOutcome(true);
    }

    @Test
    void belowLimitStreamIsRejectedAfterWriterFailure() {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        Http2Headers requestHeaders = Http2Headers.create(WritableHeaders.create());
        requestHeaders.method(Method.GET);
        requestHeaders.path("/grpc");
        requestHeaders.scheme("http");
        requestHeaders.authority("localhost");
        Http2Headers.DynamicTable dynamicTable = Http2Headers.DynamicTable.create(
                Http2Setting.HEADER_TABLE_SIZE.defaultValue());
        Http2HuffmanEncoder huffmanEncoder = Http2HuffmanEncoder.create();
        for (int streamId : List.of(1, 3)) {
            BufferData headersData = BufferData.growing(512);
            requestHeaders.write(dynamicTable, huffmanEncoder, headersData);
            input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                            Http2FrameTypes.HEADERS,
                                                                            Http2Flag.HeaderFlags.create(
                                                                                    Http2Flag.END_OF_HEADERS
                                                                                            | Http2Flag.END_OF_STREAM),
                                                                            streamId),
                                                    headersData)));
        }

        AtomicBoolean failNextWrite = new AtomicBoolean();
        DataWriter writer = mock(DataWriter.class);
        doAnswer(invocation -> {
            if (failNextWrite.compareAndSet(true, false)) {
                throw new IllegalStateException("test terminal write failure");
            }
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).submit(any(Runnable.class));
        ConnectionContext ctx = http2Context(writer, DataReader.create(input::poll));
        when(ctx.executor()).thenReturn(executor);
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        when(ctx.proxyProtocolData()).thenReturn(Optional.empty());
        Http2SubProtocolSelector selector = (_,
                                             _,
                                             _,
                                             streamWriter,
                                             streamId,
                                             _,
                                             _,
                                             _,
                                             currentStreamState,
                                             _) -> new SubProtocolResult(true,
                                                                              new FailingTerminalSubProtocolHandler(
                                                                                      streamWriter,
                                                                                      streamId,
                                                                                      currentStreamState,
                                                                                      failNextWrite));
        Http2Connection connection = new Http2Connection(ctx,
                                                         Http2Config.builder().maxConcurrentStreams(2).build(),
                                                         List.of(selector));

        assertThrows(CloseConnectionException.class,
                     () -> connection.handle(mock(Limit.class)));

        verify(executor, times(1)).submit(any(Runnable.class));
    }

    @Test
    void madeYouResetClosureReleasesAdmissionWaiter() throws InterruptedException {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        Http2Headers requestHeaders = Http2Headers.create(WritableHeaders.create());
        requestHeaders.method(Method.GET);
        requestHeaders.path("/grpc");
        requestHeaders.scheme("http");
        requestHeaders.authority("localhost");
        Http2Headers.DynamicTable dynamicTable = Http2Headers.DynamicTable.create(
                Http2Setting.HEADER_TABLE_SIZE.defaultValue());
        Http2HuffmanEncoder huffmanEncoder = Http2HuffmanEncoder.create();
        for (int streamId : List.of(1, 3)) {
            BufferData headersData = BufferData.growing(512);
            requestHeaders.write(dynamicTable, huffmanEncoder, headersData);
            input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                            Http2FrameTypes.HEADERS,
                                                                            Http2Flag.HeaderFlags.create(
                                                                                    Http2Flag.END_OF_HEADERS
                                                                                            | Http2Flag.END_OF_STREAM),
                                                                            streamId),
                                                    headersData)));
        }

        CountDownLatch resetWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseResetWrite = new CountDownLatch(1);
        CountDownLatch goAwayWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseGoAwayWrite = new CountDownLatch(1);
        DataWriter writer = mock(DataWriter.class);
        doAnswer(invocation -> {
            BufferData data = invocation.getArgument(0);
            Http2FrameHeader header = Http2FrameHeader.create(data.copy());
            if (header.type() == Http2FrameType.RST_STREAM) {
                resetWriteStarted.countDown();
                if (!releaseResetWrite.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release RST_STREAM write");
                }
            } else if (header.type() == Http2FrameType.GO_AWAY) {
                goAwayWriteStarted.countDown();
                if (!releaseGoAwayWrite.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release GOAWAY write");
                }
            }
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        AtomicBoolean firstInput = new AtomicBoolean(true);
        DataReader reader = DataReader.create(() -> {
            byte[] frame = input.poll();
            if (!firstInput.compareAndSet(true, false) && frame != null) {
                try {
                    if (!resetWriteStarted.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for RST_STREAM write");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted waiting for RST_STREAM write", e);
                }
            }
            return frame;
        });
        ExecutorService executor = mock(ExecutorService.class);
        AtomicReference<Thread> streamThread = new AtomicReference<>();
        doAnswer(invocation -> {
            Thread thread = Thread.ofVirtual().start(invocation.<Runnable>getArgument(0));
            streamThread.set(thread);
            return null;
        }).when(executor).submit(any(Runnable.class));
        ConnectionContext ctx = http2Context(writer, reader);
        when(ctx.executor()).thenReturn(executor);
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        when(ctx.proxyProtocolData()).thenReturn(Optional.empty());
        Http2SubProtocolSelector.SubProtocolHandler handler =
                mock(Http2SubProtocolSelector.SubProtocolHandler.class);
        when(handler.streamState()).thenReturn(Http2StreamState.HALF_CLOSED_REMOTE);
        doThrow(new Http2Exception(Http2ErrorCode.CANCEL, "test stream failure")).when(handler).init();
        Http2SubProtocolSelector selector = (_, _, _, _, _, _, _, _, _, _) -> new SubProtocolResult(true, handler);
        Http2Connection connection = new Http2Connection(ctx,
                                                         Http2Config.builder()
                                                                 .maxConcurrentStreams(1)
                                                                 .maxRapidResets(0)
                                                                 .build(),
                                                         List.of(selector));
        AtomicReference<Throwable> connectionFailure = new AtomicReference<>();
        Thread connectionThread = Thread.ofVirtual().start(() -> {
            try {
                connection.handle(mock(Limit.class));
            } catch (Throwable t) {
                connectionFailure.set(t);
            }
        });

        try {
            assertThat("RST_STREAM write must start",
                       resetWriteStarted.await(2, TimeUnit.SECONDS),
                       is(true));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (connectionThread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat("replacement stream admission must be waiting for reset publication",
                       connectionThread.getState(),
                       is(Thread.State.WAITING));
            releaseResetWrite.countDown();
            assertThat("made-you-reset threshold must start writing GOAWAY",
                       goAwayWriteStarted.await(2, TimeUnit.SECONDS),
                       is(true));
            assertThat("connection dispatch must remain active until GOAWAY write completes",
                       connectionThread.join(Duration.ofSeconds(1)),
                       is(false));
            releaseGoAwayWrite.countDown();
            assertThat("connection dispatch must terminate after GOAWAY",
                       connectionThread.join(Duration.ofSeconds(2)),
                       is(true));
        } finally {
            releaseResetWrite.countDown();
            releaseGoAwayWrite.countDown();
            connection.close(true);
            connectionThread.join(TimeUnit.SECONDS.toMillis(2));
            Thread worker = streamThread.get();
            if (worker != null) {
                worker.join(TimeUnit.SECONDS.toMillis(2));
            }
        }
        assertThat(connectionFailure.get(), instanceOf(CloseConnectionException.class));
    }

    @Test
    void opensObservationBeforeInvalidRequestTargetIsReset() throws InterruptedException {
        Http2Headers headers = Http2Headers.create(WritableHeaders.create());
        headers.method(Method.GET);
        headers.scheme("http");
        headers.authority("localhost");
        BufferData headersData = BufferData.growing(256);
        headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                      Http2HuffmanEncoder.create(),
                      headersData);
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(
                headersData.available(),
                Http2FrameTypes.HEADERS,
                Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                1),
                                                headersData)));
        input.add(frameBytes(new Http2GoAway(1, Http2ErrorCode.NO_ERROR, "")
                                     .toFrameData(Http2Settings.builder().build(),
                                                  0,
                                                  Http2Flag.NoFlags.create())));
        DataWriter writer = mock(DataWriter.class);
        DataReader reader = DataReader.create(input::poll);
        ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
        StreamObservation streamObservation = mock(StreamObservation.class);
        when(connectionObservation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE))
                .thenReturn(streamObservation);
        ConnectionContext ctx = http2Context(writer, reader, connectionObservation);
        ExecutorService executor = mock(ExecutorService.class);
        when(ctx.executor()).thenReturn(executor);
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        when(ctx.proxyProtocolData()).thenReturn(Optional.empty());
        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());

        connection.handle(mock(Limit.class));

        verify(connectionObservation).streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE);
        verify(streamObservation).close(StreamOutcome.RESET);
        verify((ConnectionObservationContext) ctx).httpTransportOutcome(ConnectionOutcome.REMOTE_CLOSE);
        verify(executor, never()).submit(any(Runnable.class));
    }

    @Test
    void windowUpdateForActiveStreamRefreshesIdleTime() throws InterruptedException {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        Http2Headers h2Headers = Http2Headers.create(WritableHeaders.create());
        h2Headers.method(Method.POST);
        h2Headers.path("/data");
        h2Headers.scheme("http");
        h2Headers.authority("localhost");

        BufferData headersData = BufferData.growing(512);
        h2Headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                        Http2HuffmanEncoder.create(),
                        headersData);
        input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                        Http2FrameTypes.HEADERS,
                                                                        Http2Flag.HeaderFlags.create(
                                                                                Http2Flag.END_OF_HEADERS
                                                                                        | Http2Flag.END_OF_STREAM),
                                                                        1),
                                                headersData)));

        AtomicReference<Http2Connection> connectionRef = new AtomicReference<>();
        DataReader reader = DataReader.create(() -> {
            if (input.size() == 1) {
                connectionRef.get().lastRequestTimestamp(ZonedDateTime.now().minusHours(1));
            }
            return input.poll();
        });
        ConnectionContext ctx = http2Context(mock(DataWriter.class), reader);
        when(ctx.executor()).thenReturn(mock(ExecutorService.class));
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        when(ctx.proxyProtocolData()).thenReturn(Optional.empty());
        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());
        connectionRef.set(connection);
        input.add(frameBytes(new Http2WindowUpdate(1)
                                     .toFrameData(null, 1, Http2Flag.NoFlags.create())));

        assertThrows(CloseConnectionException.class,
                     () -> connection.handle(mock(Limit.class)));

        assertThat(connection.idleTime(), lessThan(Duration.ofSeconds(5)));
    }

    @Test
    void proxyProtocolHeadersReplaceClientForwardedHeaders() throws InterruptedException {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        WritableHeaders<?> forwardedHeaders = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.X_FORWARDED_FOR, "10.0.0.5"))
                .add(HeaderValues.create(HeaderNames.X_FORWARDED_PORT, "1234"));
        Http2Headers h2Headers = Http2Headers.create(forwardedHeaders);
        h2Headers.method(Method.GET);
        h2Headers.path("/data");
        h2Headers.scheme("http");
        h2Headers.authority("localhost");

        BufferData headersData = BufferData.growing(512);
        h2Headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                        Http2HuffmanEncoder.create(),
                        headersData);
        input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                        Http2FrameTypes.HEADERS,
                                                                        Http2Flag.HeaderFlags.create(
                                                                                Http2Flag.END_OF_HEADERS
                                                                                        | Http2Flag.END_OF_STREAM),
                                                                        1),
                                                headersData)));

        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return mock(Future.class);
        }).when(executor).submit(any(Runnable.class));

        AtomicReference<Http2ServerRequest> requestRef = new AtomicReference<>();
        Router router = mock(Router.class);
        HttpRouting routing = mock(HttpRouting.class);
        when(router.routing(eq(HttpRouting.class), any(HttpRouting.class))).thenReturn(routing);
        doAnswer(invocation -> {
            requestRef.set(invocation.getArgument(1));
            return null;
        }).when(routing).route(any(), any(), any());

        DataReader reader = DataReader.create(input::poll);
        ConnectionContext ctx = http2Context(mock(DataWriter.class), reader);
        when(ctx.router()).thenReturn(router);
        when(ctx.executor()).thenReturn(executor);
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        ProxyProtocolData proxyProtocolData = mock(ProxyProtocolData.class);
        when(proxyProtocolData.family()).thenReturn(ProxyProtocolData.Family.IPv4);
        when(proxyProtocolData.sourceAddress()).thenReturn("192.168.0.1");
        when(proxyProtocolData.destPort()).thenReturn(443);
        when(ctx.proxyProtocolData()).thenReturn(Optional.of(proxyProtocolData));
        ListenerContext listenerContext = mock(ListenerContext.class);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentDecodingEnabled()).thenReturn(false);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        when(listenerContext.config()).thenReturn(WebServer.builder().buildPrototype());
        when(listenerContext.mediaContext()).thenReturn(MediaContext.create());
        when(ctx.listenerContext()).thenReturn(listenerContext);

        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());

        assertThrows(CloseConnectionException.class,
                     () -> connection.handle(FixedLimit.create()));

        assertThat(requestRef.get(), notNullValue());
        assertThat(requestRef.get().headers().all(HeaderNames.X_FORWARDED_FOR, List::of),
                   is(List.of("192.168.0.1")));
        assertThat(requestRef.get().headers().all(HeaderNames.X_FORWARDED_PORT, List::of),
                   is(List.of("443")));
    }

    @Test
    void proxyProtocolUnixAddressDoesNotBecomeForwardedHeader() throws InterruptedException {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        WritableHeaders<?> forwardedHeaders = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.X_FORWARDED_FOR, "10.0.0.5"))
                .add(HeaderValues.create(HeaderNames.X_FORWARDED_PORT, "1234"));
        Http2Headers h2Headers = Http2Headers.create(forwardedHeaders);
        h2Headers.method(Method.GET);
        h2Headers.path("/data");
        h2Headers.scheme("http");
        h2Headers.authority("localhost");

        BufferData headersData = BufferData.growing(512);
        h2Headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                        Http2HuffmanEncoder.create(),
                        headersData);
        input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                        Http2FrameTypes.HEADERS,
                                                                        Http2Flag.HeaderFlags.create(
                                                                                Http2Flag.END_OF_HEADERS
                                                                                        | Http2Flag.END_OF_STREAM),
                                                                        1),
                                                headersData)));

        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return mock(Future.class);
        }).when(executor).submit(any(Runnable.class));

        AtomicReference<Http2ServerRequest> requestRef = new AtomicReference<>();
        Router router = mock(Router.class);
        HttpRouting routing = mock(HttpRouting.class);
        when(router.routing(eq(HttpRouting.class), any(HttpRouting.class))).thenReturn(routing);
        doAnswer(invocation -> {
            requestRef.set(invocation.getArgument(1));
            return null;
        }).when(routing).route(any(), any(), any());

        DataReader reader = DataReader.create(input::poll);
        ConnectionContext ctx = http2Context(mock(DataWriter.class), reader);
        when(ctx.router()).thenReturn(router);
        when(ctx.executor()).thenReturn(executor);
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        ProxyProtocolData proxyProtocolData = mock(ProxyProtocolData.class);
        when(proxyProtocolData.family()).thenReturn(ProxyProtocolData.Family.UNIX);
        when(proxyProtocolData.sourceAddress()).thenReturn("/tmp/source\r\nx-forwarded-for: attacker");
        when(proxyProtocolData.destPort()).thenReturn(-1);
        when(ctx.proxyProtocolData()).thenReturn(Optional.of(proxyProtocolData));
        ListenerContext listenerContext = mock(ListenerContext.class);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentDecodingEnabled()).thenReturn(false);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        when(listenerContext.config()).thenReturn(WebServer.builder().buildPrototype());
        when(listenerContext.mediaContext()).thenReturn(MediaContext.create());
        when(ctx.listenerContext()).thenReturn(listenerContext);

        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());

        assertThrows(CloseConnectionException.class,
                     () -> connection.handle(FixedLimit.create()));

        assertThat(requestRef.get(), notNullValue());
        assertThat(requestRef.get().headers().all(HeaderNames.X_FORWARDED_FOR, List::of),
                   is(List.of()));
        assertThat(requestRef.get().headers().all(HeaderNames.X_FORWARDED_PORT, List::of),
                   is(List.of()));
    }

    @Test
    void proxyProtocolHeadersRemoveClientPortWhenDestinationPortUnavailable() throws InterruptedException {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        WritableHeaders<?> forwardedHeaders = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.X_FORWARDED_FOR, "10.0.0.5"))
                .add(HeaderValues.create(HeaderNames.X_FORWARDED_PORT, "1234"));
        Http2Headers h2Headers = Http2Headers.create(forwardedHeaders);
        h2Headers.method(Method.GET);
        h2Headers.path("/data");
        h2Headers.scheme("http");
        h2Headers.authority("localhost");

        BufferData headersData = BufferData.growing(512);
        h2Headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                        Http2HuffmanEncoder.create(),
                        headersData);
        input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                        Http2FrameTypes.HEADERS,
                                                                        Http2Flag.HeaderFlags.create(
                                                                                Http2Flag.END_OF_HEADERS
                                                                                        | Http2Flag.END_OF_STREAM),
                                                                        1),
                                                headersData)));

        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return mock(Future.class);
        }).when(executor).submit(any(Runnable.class));

        AtomicReference<Http2ServerRequest> requestRef = new AtomicReference<>();
        Router router = mock(Router.class);
        HttpRouting routing = mock(HttpRouting.class);
        when(router.routing(eq(HttpRouting.class), any(HttpRouting.class))).thenReturn(routing);
        doAnswer(invocation -> {
            requestRef.set(invocation.getArgument(1));
            return null;
        }).when(routing).route(any(), any(), any());

        DataReader reader = DataReader.create(input::poll);
        ConnectionContext ctx = http2Context(mock(DataWriter.class), reader);
        when(ctx.router()).thenReturn(router);
        when(ctx.executor()).thenReturn(executor);
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        ProxyProtocolData proxyProtocolData = mock(ProxyProtocolData.class);
        when(proxyProtocolData.family()).thenReturn(ProxyProtocolData.Family.IPv4);
        when(proxyProtocolData.sourceAddress()).thenReturn("192.168.0.1");
        when(proxyProtocolData.destPort()).thenReturn(-1);
        when(ctx.proxyProtocolData()).thenReturn(Optional.of(proxyProtocolData));
        ListenerContext listenerContext = mock(ListenerContext.class);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentDecodingEnabled()).thenReturn(false);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        when(listenerContext.config()).thenReturn(WebServer.builder().buildPrototype());
        when(listenerContext.mediaContext()).thenReturn(MediaContext.create());
        when(ctx.listenerContext()).thenReturn(listenerContext);

        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());

        assertThrows(CloseConnectionException.class,
                     () -> connection.handle(FixedLimit.create()));

        assertThat(requestRef.get(), notNullValue());
        assertThat(requestRef.get().headers().all(HeaderNames.X_FORWARDED_FOR, List::of),
                   is(List.of("192.168.0.1")));
        assertThat(requestRef.get().headers().all(HeaderNames.X_FORWARDED_PORT, List::of),
                   is(List.of()));
    }

    @Test
    void proxyProtocolHeadersValidateBeforeReplacement() throws InterruptedException {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        BufferData headersData = BufferData.growing(512);
        headersData.write(0x82);       // :method GET
        headersData.write(0x04);       // literal without indexing, indexed name :path
        headersData.write(5);
        headersData.write("/data".getBytes(US_ASCII));
        headersData.write(0x86);       // :scheme http
        headersData.write(0x01);       // literal without indexing, indexed name :authority
        headersData.write(9);
        headersData.write("localhost".getBytes(US_ASCII));
        headersData.write(0x00);       // literal without indexing, custom name
        headersData.write(16);
        headersData.write("x-forwarded-port".getBytes(US_ASCII));
        headersData.write(5);
        headersData.write("\r1234".getBytes(US_ASCII));
        input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                        Http2FrameTypes.HEADERS,
                                                                        Http2Flag.HeaderFlags.create(
                                                                                Http2Flag.END_OF_HEADERS
                                                                                        | Http2Flag.END_OF_STREAM),
                                                                        1),
                                                headersData)));

        List<BufferData> writtenFrames = new ArrayList<>();
        DataWriter writer = mock(DataWriter.class);
        doAnswer(invocation -> {
            BufferData data = invocation.getArgument(0);
            writtenFrames.add(data.copy());
            return null;
        }).when(writer).writeNow(any(BufferData.class));

        DataReader reader = DataReader.create(input::poll);
        ConnectionContext ctx = http2Context(writer, reader);
        ExecutorService executor = mock(ExecutorService.class);
        when(ctx.executor()).thenReturn(executor);
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        ProxyProtocolData proxyProtocolData = mock(ProxyProtocolData.class);
        when(proxyProtocolData.family()).thenReturn(ProxyProtocolData.Family.IPv4);
        when(proxyProtocolData.sourceAddress()).thenReturn("192.168.0.1");
        when(proxyProtocolData.destPort()).thenReturn(443);
        when(ctx.proxyProtocolData()).thenReturn(Optional.of(proxyProtocolData));

        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());
        connection.handle(FixedLimit.create());

        BufferData goAwayData = writtenFrames.get(writtenFrames.size() - 1);
        byte[] headerBytes = new byte[Http2FrameHeader.LENGTH];
        goAwayData.read(headerBytes);
        Http2FrameHeader frameHeader = Http2FrameHeader.create(BufferData.create(headerBytes));
        assertThat(frameHeader.type(), is(Http2FrameType.GO_AWAY));

        byte[] payloadBytes = new byte[frameHeader.length()];
        goAwayData.read(payloadBytes);
        Http2GoAway goAway = Http2GoAway.create(BufferData.create(payloadBytes));
        assertThat(goAway.errorCode(), is(Http2ErrorCode.PROTOCOL));
        verify(executor, never()).submit(any(Runnable.class));
    }

    @Test
    void activeStreamInitialWindowSizeOverflowHandledWithFlowControlGoAway() throws InterruptedException {
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        input.add(Http2Util.prefaceData().readBytes());
        Http2Headers h2Headers = Http2Headers.create(WritableHeaders.create());
        h2Headers.method(Method.POST);
        h2Headers.path("/data");
        h2Headers.scheme("http");
        h2Headers.authority("localhost");

        BufferData headersData = BufferData.growing(512);
        h2Headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                        Http2HuffmanEncoder.create(),
                        headersData);
        input.add(frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                        Http2FrameTypes.HEADERS,
                                                                        Http2Flag.HeaderFlags.create(
                                                                                Http2Flag.END_OF_HEADERS),
                                                                        1),
                                                headersData)));
        input.add(frameBytes(new Http2WindowUpdate(WindowSize.MAX_WIN_SIZE - WindowSize.DEFAULT_WIN_SIZE)
                                     .toFrameData(null, 1, Http2Flag.NoFlags.create())));
        input.add(frameBytes(Http2Settings.builder()
                                     .add(Http2Setting.INITIAL_WINDOW_SIZE, WindowSize.DEFAULT_WIN_SIZE + 1L)
                                     .build()
                                     .toFrameData(null, 0, Http2Flag.SettingsFlags.create(0))));

        List<BufferData> writtenFrames = new ArrayList<>();
        DataWriter writer = mock(DataWriter.class);
        doAnswer(invocation -> {
            BufferData data = invocation.getArgument(0);
            writtenFrames.add(data.copy());
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        DataReader reader = DataReader.create(input::poll);
        ExecutorService executor = mock(ExecutorService.class);
        ConnectionContext ctx = http2Context(writer, reader);
        when(ctx.executor()).thenReturn(executor);
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        when(ctx.proxyProtocolData()).thenReturn(Optional.empty());
        Http2Connection connection = new Http2Connection(ctx,
                                                         Http2Config.builder().sendErrorDetails(true).build(),
                                                         List.of());

        connection.expectPreface();
        connection.handle(mock(Limit.class));

        BufferData goAwayData = writtenFrames.get(writtenFrames.size() - 1);
        byte[] headerBytes = new byte[Http2FrameHeader.LENGTH];
        goAwayData.read(headerBytes);
        Http2FrameHeader frameHeader = Http2FrameHeader.create(BufferData.create(headerBytes));
        assertThat(frameHeader.type(), is(Http2FrameType.GO_AWAY));

        byte[] payloadBytes = new byte[frameHeader.length()];
        goAwayData.read(payloadBytes);
        Http2GoAway goAway = Http2GoAway.create(BufferData.create(payloadBytes));
        assertThat(goAway.errorCode(), is(Http2ErrorCode.FLOW_CONTROL));
    }

    @Test
    void madeYouResetClosesWhenThresholdIsExceeded() {
        DataWriter writer = mock(DataWriter.class);
        Http2Config config = Http2Config.builder()
                .maxRapidResets(2)
                .build();
        ConnectionContext ctx = http2Context(writer);
        Http2Connection connection = new Http2Connection(ctx, config, List.of());
        Http2ConnectionChecks checks = new Http2ConnectionChecks(config, connection);

        checks.madeYouResetCheck();
        checks.madeYouResetCheck();
        verify(writer, never()).writeNow(any(BufferData.class));

        assertThrows(CloseConnectionException.class, checks::madeYouResetCheck);
    }

    @Test
    void madeYouResetCanBeDisabled() {
        DataWriter writer = mock(DataWriter.class);
        Http2Config config = Http2Config.builder()
                .maxRapidResets(-1)
                .build();
        ConnectionContext ctx = http2Context(writer);
        Http2Connection connection = new Http2Connection(ctx, config, List.of());
        Http2ConnectionChecks checks = new Http2ConnectionChecks(config, connection);

        for (int i = 0; i < 10; i++) {
            checks.madeYouResetCheck();
        }

        verify(writer, never()).writeNow(any(BufferData.class));
    }

    @Test
    void rapidResetClosesWhenThresholdIsExceededWithinPeriod() {
        DataWriter writer = mock(DataWriter.class);
        Http2Config config = Http2Config.builder()
                .rapidResetCheckPeriod(Duration.ofSeconds(10))
                .maxRapidResets(2)
                .build();
        ConnectionContext ctx = http2Context(writer);
        Http2Connection connection = new Http2Connection(ctx, config, List.of());
        Http2ConnectionChecks checks = new Http2ConnectionChecks(config, connection);

        checks.rapidResetCheck(true);
        checks.rapidResetCheck(true);
        verify(writer, never()).writeNow(any(BufferData.class));

        assertThrows(CloseConnectionException.class, () -> checks.rapidResetCheck(true));
    }

    @Test
    void rapidResetCounterRestartsAfterCheckPeriod() throws InterruptedException {
        DataWriter writer = mock(DataWriter.class);
        Http2Config config = Http2Config.builder()
                .rapidResetCheckPeriod(Duration.ofNanos(1))
                .maxRapidResets(2)
                .build();
        ConnectionContext ctx = http2Context(writer);
        Http2Connection connection = new Http2Connection(ctx, config, List.of());
        Http2ConnectionChecks checks = new Http2ConnectionChecks(config, connection);

        checks.rapidResetCheck(true);
        checks.rapidResetCheck(true);
        TimeUnit.MILLISECONDS.sleep(1);
        checks.rapidResetCheck(true);
        checks.rapidResetCheck(true);

        verify(writer, never()).writeNow(any(BufferData.class));
    }

    @Test
    void gracefulCloseBeforeHandleDoesNotRequireHandlerThread() {
        closeBeforeHandleDoesNotRequireHandlerThread(false);
    }

    @Test
    void forcedCloseBeforeHandleDoesNotRequireHandlerThread() {
        closeBeforeHandleDoesNotRequireHandlerThread(true);
    }

    private static void closeBeforeHandleDoesNotRequireHandlerThread(boolean interrupt) {
        ConnectionContext ctx = http2Context(mock(DataWriter.class));

        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());

        connection.close(interrupt);

        assertThat(connection.canInterrupt(), is(true));
    }

    private static void assertAdvertisedHeaderListSize(int localLimit, long advertisedLimit) throws InterruptedException {
        HeaderLimitTestContext test = new HeaderLimitTestContext();
        test.handle(Http2Config.builder()
                            .maxHeadersSize(localLimit)
                            .maxHeaderListSize(advertisedLimit)
                            .build());

        List<Http2FrameData> settingsFrames = test.frames(Http2FrameType.SETTINGS).stream()
                .filter(frame -> !frame.header().flags(Http2FrameTypes.SETTINGS).ack())
                .toList();
        assertThat(settingsFrames, hasSize(1));
        Http2Settings settings = Http2Settings.create(settingsFrames.getFirst().data());
        assertThat(settings.presentValue(Http2Setting.MAX_HEADER_LIST_SIZE), is(Optional.of(advertisedLimit)));
    }

    private static Http2Headers headerLimitRequest(String value) {
        return Http2Headers.create(WritableHeaders.create().set(LIMIT_HEADER, value))
                .method(Method.GET)
                .path("/")
                .scheme("http")
                .authority("localhost");
    }

    private static Http2FrameData[] headerLimitFrames(Http2Headers headers,
                                                      Http2Headers.DynamicTable table,
                                                      boolean endOfStream,
                                                      boolean continuation) {
        BufferData data = BufferData.growing(512);
        headers.write(table, Http2HuffmanEncoder.create(), data);
        int flags = endOfStream ? Http2Flag.END_OF_STREAM : 0;
        if (!continuation) {
            return new Http2FrameData[] {
                    new Http2FrameData(Http2FrameHeader.create(data.available(),
                                                               Http2FrameTypes.HEADERS,
                                                               Http2Flag.HeaderFlags.create(flags | Http2Flag.END_OF_HEADERS),
                                                               1),
                                        data)
            };
        }
        byte[] first = new byte[data.available() / 2];
        data.read(first);
        byte[] second = data.readBytes();
        return new Http2FrameData[] {
                new Http2FrameData(Http2FrameHeader.create(first.length,
                                                           Http2FrameTypes.HEADERS,
                                                           Http2Flag.HeaderFlags.create(flags),
                                                           1),
                                    BufferData.create(first)),
                new Http2FrameData(Http2FrameHeader.create(second.length,
                                                           Http2FrameTypes.CONTINUATION,
                                                           Http2Flag.ContinuationFlags.create(Http2Flag.END_OF_HEADERS),
                                                           1),
                                    BufferData.create(second))
        };
    }

    private static ConnectionContext http2Context(DataWriter writer) {
        return http2Context(writer, mock(DataReader.class));
    }

    private static ConnectionContext http2Context(DataWriter writer, DataReader reader) {
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(mock(ListenerContext.class));
        when(ctx.dataWriter()).thenReturn(writer);
        when(ctx.dataReader()).thenReturn(reader);
        return ctx;
    }

    private static ConnectionContext runnableConnectionContext(Queue<byte[]> input) {
        ConnectionContext ctx = http2Context(mock(DataWriter.class), DataReader.create(input::poll));
        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).submit(any(Runnable.class));
        when(ctx.executor()).thenReturn(executor);
        ListenerContext listenerContext = mock(ListenerContext.class);
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        when(ctx.listenerContext()).thenReturn(listenerContext);
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        when(ctx.proxyProtocolData()).thenReturn(Optional.empty());
        return ctx;
    }

    private static Limit throwingLimit() {
        Limit limit = mock(Limit.class);
        doThrow(new IllegalStateException("connection limit used")).when(limit).tryAcquireOutcome(true);
        return limit;
    }

    private static byte[] requestHeadersFrame() {
        Http2Headers headers = Http2Headers.create(WritableHeaders.create());
        headers.method(Method.GET);
        headers.path("/");
        headers.scheme("http");
        headers.authority("localhost");
        BufferData headersData = BufferData.growing(512);
        headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                      Http2HuffmanEncoder.create(),
                      headersData);
        return frameBytes(new Http2FrameData(Http2FrameHeader.create(headersData.available(),
                                                                     Http2FrameTypes.HEADERS,
                                                                     Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS
                                                                                                           | Http2Flag.END_OF_STREAM),
                                                                     1),
                                             headersData));
    }

    private static ConnectionContext http2Context(DataWriter writer,
                                                  DataReader reader,
                                                  ConnectionObservation connectionObservation) {
        ConnectionContext ctx = mock(ConnectionContext.class,
                                     withSettings().extraInterfaces(ConnectionObservationContext.class));
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(mock(ListenerContext.class));
        when(ctx.dataWriter()).thenReturn(writer);
        when(ctx.dataReader()).thenReturn(reader);
        when(((ConnectionObservationContext) ctx).httpTransportObservation()).thenReturn(connectionObservation);
        return ctx;
    }

    private static byte[] frameBytes(Http2FrameData frameData) {
        return BufferData.create(frameData.header().write(), frameData.data()).readBytes();
    }

    private static final class HeaderLimitTestContext {
        private final List<Http2FrameData> writtenFrames = new ArrayList<>();
        private final ExecutorService executor = mock(ExecutorService.class);
        private final ConnectionContext context;

        private HeaderLimitTestContext(Http2FrameData... headerFrames) {
            Queue<byte[]> input = new ConcurrentLinkedQueue<>();
            input.add(frameBytes(Http2Settings.create().toFrameData(null, 0, Http2Flag.SettingsFlags.create(0))));
            for (Http2FrameData frame : headerFrames) {
                input.add(frameBytes(frame));
            }
            input.add(frameBytes(new Http2GoAway(1, Http2ErrorCode.NO_ERROR, "")
                                         .toFrameData(Http2Settings.create(), 0, Http2Flag.NoFlags.create())));
            DataWriter writer = mock(DataWriter.class);
            doAnswer(invocation -> {
                BufferData data = invocation.<BufferData>getArgument(0).copy();
                Http2FrameHeader header = Http2FrameHeader.create(data);
                writtenFrames.add(new Http2FrameData(header, data));
                return null;
            }).when(writer).writeNow(any(BufferData.class));
            context = http2Context(writer, DataReader.create(input::poll));
            // Keep request handlers queued while the connection decodes the complete request and trailers.
            when(context.executor()).thenReturn(executor);
            PeerInfo peerInfo = mock(PeerInfo.class);
            when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
            when(context.remotePeer()).thenReturn(peerInfo);
            when(context.proxyProtocolData()).thenReturn(Optional.empty());
        }

        private void handle(Http2Config config) throws InterruptedException {
            new Http2Connection(context, config, List.of()).handle(mock(Limit.class));
        }

        private List<Http2FrameData> frames(Http2FrameType type) {
            return writtenFrames.stream().filter(frame -> frame.header().type() == type).toList();
        }

        private void assertHeaderSizeRejected() {
            List<Http2FrameData> goAwayFrames = frames(Http2FrameType.GO_AWAY);
            assertThat("Oversized decoded headers must fail the connection", goAwayFrames, hasSize(1));
            assertThat(Http2GoAway.create(goAwayFrames.getFirst().data()).errorCode(),
                       is(Http2ErrorCode.ENHANCE_YOUR_CALM));
        }
    }

    private static final class FailingTerminalSubProtocolHandler
            implements Http2SubProtocolSelector.SubProtocolHandler {
        private final Http2StreamWriter writer;
        private final int streamId;
        private final AtomicBoolean failNextWrite;
        private volatile Http2StreamState state;

        private FailingTerminalSubProtocolHandler(Http2StreamWriter writer,
                                                  int streamId,
                                                  Http2StreamState state,
                                                  AtomicBoolean failNextWrite) {
            this.writer = writer;
            this.streamId = streamId;
            this.state = state;
            this.failNextWrite = failNextWrite;
        }

        @Override
        public void init() {
            failNextWrite.set(true);
            Http2FrameData terminalData = new Http2FrameData(
                    Http2FrameHeader.create(1,
                                            Http2FrameTypes.DATA,
                                            Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                            streamId),
                    BufferData.create(new byte[] {1}));
            writer.writeData(terminalData, FlowControl.Outbound.NOOP);
        }

        @Override
        public Http2StreamState streamState() {
            return state;
        }

        @Override
        public void rstStream(Http2RstStream rstStream) {
            state = Http2StreamState.CLOSED;
        }

        @Override
        public void windowUpdate(Http2WindowUpdate update) {
        }

        @Override
        public void data(Http2FrameHeader header, BufferData data) {
        }
    }

    private static final class TestLogHandler extends Handler implements AutoCloseable {
        private final Logger logger;
        private final Level previousLevel;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<LogRecord> record = new AtomicReference<>();

        private TestLogHandler(Logger logger) {
            this.logger = logger;
            this.previousLevel = logger.getLevel();
            setLevel(Level.ALL);
        }

        static TestLogHandler install() {
            Logger logger = Logger.getLogger(Http2Connection.class.getName());
            TestLogHandler handler = new TestLogHandler(logger);
            logger.setLevel(Level.ALL);
            logger.addHandler(handler);
            return handler;
        }

        @Override
        public void publish(LogRecord record) {
            if (this.record.compareAndSet(null, record)) {
                latch.countDown();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(previousLevel);
        }

        private LogRecord await() throws InterruptedException {
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
            return record.get();
        }
    }
}
