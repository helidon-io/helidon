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
import java.util.List;
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
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.http2.Http2Ping;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2ConnectionTest {

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
            streams.doMaintenance(0);

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
            streams.doMaintenance(0);
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
            streams.doMaintenance(0);
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
            streams.doMaintenance(0);
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
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(mock(ListenerContext.class));
        when(ctx.dataWriter()).thenReturn(writer);
        when(ctx.dataReader()).thenReturn(mock(DataReader.class));
        return ctx;
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
