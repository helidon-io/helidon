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
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2Ping;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2Util;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.WindowSize;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2ConnectionTest {

    @Test
    void zeroInitialWindowStillCreatesConnection() {
        Http2Config config = Http2Config.builder()
                .initialWindowSize(0)
                .build();

        assertDoesNotThrow(() -> new Http2Connection(http2Context(mock(DataWriter.class)), config, List.of()));
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
                     () -> connection.handle(mock(io.helidon.common.concurrency.limits.Limit.class)));
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
        ConnectionContext ctx = http2Context(writer, reader);
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

        connection.handle(mock(io.helidon.common.concurrency.limits.Limit.class));

        verify(executor, never()).submit(any(Runnable.class));
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
                     () -> connection.handle(mock(io.helidon.common.concurrency.limits.Limit.class)));

        assertThat(connection.idleTime(), lessThan(Duration.ofSeconds(5)));
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
        connection.handle(mock(io.helidon.common.concurrency.limits.Limit.class));

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

    private static byte[] frameBytes(Http2FrameData frameData) {
        return BufferData.create(frameData.header().write(), frameData.data()).readBytes();
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
