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

import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
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
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ErrorHandling;
import io.helidon.webserver.HttpTransportObserverSupport.ConnectionObservationContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.http2.spi.SubProtocolResult;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class Http2ConnectionOutcomeTest {
    @Test
    void peerGoAwayWaitsForSuccessfulTerminalResponseObservation() throws InterruptedException {
        peerGoAwayDuringTerminalResponse(TerminalWriteMode.SUCCESS, StreamOutcome.COMPLETED);
    }

    @Test
    void peerGoAwayInterruptsBlockedTerminalWriteAndObservesFailure() throws InterruptedException {
        peerGoAwayDuringTerminalResponse(TerminalWriteMode.INTERRUPTIBLE, StreamOutcome.ERROR);
    }

    @Test
    void peerGoAwayAbortsPlatformSocketWriteAndObservesFailure() throws InterruptedException {
        peerGoAwayDuringTerminalResponse(TerminalWriteMode.SOCKET_CLOSE, StreamOutcome.ERROR);
    }

    @Test
    void peerGoAwayWaitsForRejectedTerminalResponseObservation() throws InterruptedException {
        peerGoAwayDuringTerminalResponse(TerminalWriteMode.SUCCESS, StreamOutcome.REJECTED);
    }

    @Test
    void successfulGoAwayRecordsLocalClose() {
        ObservedConnection observed = observedConnection(mock(DataWriter.class), mock(DataReader.class));

        observed.connection().writeGoAwayAndFinish(Http2ErrorCode.NO_ERROR, "done");

        assertThat(observed.outcome().get(), is(ConnectionOutcome.LOCAL_CLOSE));
    }

    @Test
    void successfulErrorGoAwayRecordsError() {
        ObservedConnection observed = observedConnection(mock(DataWriter.class), mock(DataReader.class));

        observed.connection().writeGoAwayAndFinish(Http2ErrorCode.PROTOCOL, "invalid request");

        assertThat(observed.outcome().get(), is(ConnectionOutcome.ERROR));
    }

    @Test
    void failedGracefulGoAwayRecordsWriteError() {
        DataWriter writer = mock(DataWriter.class);
        var failure = new UncheckedIOException(new SocketException("Broken pipe"));
        doThrow(failure).when(writer).writeNow(any(BufferData.class));
        ObservedConnection observed = observedConnection(writer, mock(DataReader.class));

        ServerConnectionException thrown = assertThrows(ServerConnectionException.class,
                () -> observed.connection().writeGoAwayAndFinish(Http2ErrorCode.NO_ERROR, "done"));

        assertAll(
                () -> assertThat(thrown.getCause(), sameInstance(failure)),
                () -> assertThat(observed.outcome().get(), is(ConnectionOutcome.ERROR))
        );
    }

    @Test
    void protocolErrorDoesNotHideGoAwayWriteTimeout() {
        DataWriter writer = mock(DataWriter.class);
        var failure = new UncheckedIOException(new SocketTimeoutException("write timed out"));
        doAnswer(invocation -> {
            BufferData data = invocation.getArgument(0);
            if (Http2FrameHeader.create(data.copy()).type() == Http2FrameType.GO_AWAY) {
                throw failure;
            }
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        input.add(invalidSettingsFrame());
        ObservedConnection observed = observedConnection(writer, DataReader.create(input::poll));

        ServerConnectionException thrown = assertThrows(ServerConnectionException.class,
                () -> observed.connection().handle(mock(Limit.class)));

        assertAll(
                () -> assertThat(thrown.getCause(), sameInstance(failure)),
                () -> assertThat(observed.outcome().get(), is(ConnectionOutcome.TIMEOUT))
        );
    }

    @Test
    void nestedWriteTimeoutIsPreserved() {
        DataWriter writer = mock(DataWriter.class);
        var failure = new IllegalStateException("write failed", new TimeoutException("write timed out"));
        doThrow(failure).when(writer).writeNow(any(BufferData.class));
        ObservedConnection observed = observedConnection(writer, mock(DataReader.class));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> observed.connection().writeGoAwayAndFinish(Http2ErrorCode.INTERNAL, "failure"));

        assertAll(
                () -> assertThat(thrown, sameInstance(failure)),
                () -> assertThat(observed.outcome().get(), is(ConnectionOutcome.TIMEOUT))
        );
    }

    @Test
    void connectionStopWaitsForGoAwayOutcomeFromAnotherThread() throws InterruptedException {
        CountDownLatch readerStarted = new CountDownLatch(1);
        CountDownLatch goAwayStarted = new CountDownLatch(1);
        CountDownLatch invalidFrameRead = new CountDownLatch(1);
        CountDownLatch releaseGoAway = new CountDownLatch(1);
        DataReader reader = DataReader.create(() -> {
            readerStarted.countDown();
            await(goAwayStarted, "GOAWAY write must start before returning invalid frame");
            invalidFrameRead.countDown();
            return invalidSettingsFrame();
        });
        DataWriter writer = mock(DataWriter.class);
        var failure = new UncheckedIOException(new SocketTimeoutException("GOAWAY timed out"));
        doAnswer(invocation -> {
            BufferData data = invocation.getArgument(0);
            if (Http2FrameHeader.create(data.copy()).type() == Http2FrameType.GO_AWAY) {
                goAwayStarted.countDown();
                await(releaseGoAway, "GOAWAY write must be released");
                throw failure;
            }
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        ObservedConnection observed = observedConnection(writer, reader);
        AtomicReference<Throwable> connectionFailure = new AtomicReference<>();
        AtomicReference<Throwable> writerFailure = new AtomicReference<>();
        AtomicReference<ConnectionOutcome> outcomeWhenHandleReturned = new AtomicReference<>();
        Thread connectionThread = Thread.ofVirtual().start(() -> {
            try {
                observed.connection().handle(mock(Limit.class));
                outcomeWhenHandleReturned.set(observed.outcome().get());
            } catch (Throwable thrown) {
                connectionFailure.set(thrown);
            }
        });
        Thread writerThread = Thread.ofVirtual().start(() -> {
            try {
                await(readerStarted, "connection must start reading");
                observed.connection().writeGoAwayAndFinish(Http2ErrorCode.PROTOCOL, "stream rejection");
            } catch (Throwable thrown) {
                writerFailure.set(thrown);
            }
        });
        try {
            await(invalidFrameRead, "connection must read invalid frame while GOAWAY is pending");
            awaitState(connectionThread, Thread.State.WAITING, "handle waits until GOAWAY outcome is known");
            assertThat("pending GOAWAY must not preselect an error outcome", observed.outcome().get(), is(nullValue()));
            releaseGoAway.countDown();
            assertThat("GOAWAY caller must terminate", writerThread.join(Duration.ofSeconds(5)), is(true));
            assertThat("connection must terminate after GOAWAY",
                       connectionThread.join(Duration.ofSeconds(5)),
                       is(true));
            assertAll(
                    () -> assertThat(connectionFailure.get(), is(nullValue())),
                    () -> assertThat(writerFailure.get(), instanceOf(ServerConnectionException.class)),
                    () -> assertThat(writerFailure.get().getCause(), sameInstance(failure)),
                    () -> assertThat(outcomeWhenHandleReturned.get(), is(ConnectionOutcome.TIMEOUT))
            );
        } finally {
            releaseGoAway.countDown();
            observed.connection().close(true);
            writerThread.join(TimeUnit.SECONDS.toMillis(5));
            connectionThread.join(TimeUnit.SECONDS.toMillis(5));
        }
    }

    private static void peerGoAwayDuringTerminalResponse(TerminalWriteMode writeMode, StreamOutcome expectedOutcome)
            throws InterruptedException {
        CountDownLatch terminalResponseWritten = new CountDownLatch(1);
        CountDownLatch releaseTerminalWrite = new CountDownLatch(1);
        CountDownLatch peerGoAwayProcessed = new CountDownLatch(1);
        CountDownLatch teardownStarted = new CountDownLatch(1);
        CountDownLatch socketAborted = new CountDownLatch(1);
        AtomicReference<InterruptedException> writerInterruption = new AtomicReference<>();
        AtomicReference<Boolean> terminalWriteInterrupted = new AtomicReference<>();
        Queue<byte[]> input = new ConcurrentLinkedQueue<>();
        Http2Headers requestHeaders = Http2Headers.create(WritableHeaders.create());
        if (expectedOutcome == StreamOutcome.REJECTED) {
            // A valid HTTP/2 CONNECT is rejected before application routing because CONNECT is unsupported.
            requestHeaders.method(Method.CONNECT);
        } else {
            requestHeaders.method(Method.GET);
            requestHeaders.path("/grpc");
            requestHeaders.scheme("https");
        }
        requestHeaders.authority("localhost:443");
        BufferData headersData = BufferData.growing(512);
        requestHeaders.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                             Http2HuffmanEncoder.create(),
                             headersData);
        input.add(BufferData.create(Http2FrameHeader.create(headersData.available(),
                                                           Http2FrameTypes.HEADERS,
                                                           Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS
                                                                                               | Http2Flag.END_OF_STREAM),
                                                           1).write(), headersData).readBytes());
        Http2FrameData goAway = new Http2GoAway(1, Http2ErrorCode.NO_ERROR, "done")
                .toFrameData(Http2Settings.create(), 0, Http2Flag.NoFlags.create());
        input.add(BufferData.create(goAway.header().write(), goAway.data()).readBytes());
        DataReader reader = DataReader.create(() -> {
            byte[] bytes = input.poll();
            if (bytes != null && Http2FrameHeader.create(BufferData.create(bytes)).type() == Http2FrameType.GO_AWAY) {
                await(terminalResponseWritten, "peer must receive terminal response before sending GOAWAY");
            }
            return bytes;
        });
        DataWriter writer = mock(DataWriter.class);
        doAnswer(invocation -> {
            BufferData emitted = invocation.<BufferData>getArgument(0).copy();
            Http2FrameHeader header = Http2FrameHeader.create(emitted);
            if (header.type() == Http2FrameType.HEADERS && (header.flags() & Http2Flag.END_OF_STREAM) != 0) {
                terminalResponseWritten.countDown();
                if (writeMode == TerminalWriteMode.INTERRUPTIBLE) {
                    try {
                        assertThat("blocked socket write must be interrupted or released for cleanup",
                                   releaseTerminalWrite.await(10, TimeUnit.SECONDS), is(true));
                    } catch (InterruptedException interruption) {
                        writerInterruption.set(interruption);
                        Thread.currentThread().interrupt();
                        throw new UncheckedIOException(new InterruptedIOException("terminal socket write interrupted"));
                    }
                } else if (writeMode == TerminalWriteMode.SOCKET_CLOSE) {
                    // Legacy socket I/O on platform threads can ignore interrupts until the socket itself closes.
                    awaitSuccessfulWrite(socketAborted);
                    throw new UncheckedIOException(new SocketException("socket closed during terminal write"));
                } else {
                    awaitSuccessfulWrite(releaseTerminalWrite);
                }
                terminalWriteInterrupted.set(Thread.currentThread().isInterrupted());
            }
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        Queue<StreamOutcome> outcomes = new ConcurrentLinkedQueue<>();
        ConnectionObservation observation = mock(ConnectionObservation.class);
        when(observation.streamOpened(Direction.BIDIRECTIONAL, Initiator.REMOTE)).thenReturn(outcomes::add);
        ConnectionContext ctx = mock(ConnectionContext.class,
                                     withSettings().extraInterfaces(ConnectionObservationContext.class));
        ConnectionObservationContext observationContext = (ConnectionObservationContext) ctx;
        when(observationContext.httpTransportObservation()).thenReturn(observation);
        doAnswer(_ -> {
            socketAborted.countDown();
            return null;
        }).when(ctx).abortSocket();
        doAnswer(_ -> {
            peerGoAwayProcessed.countDown();
            return null;
        }).when(observationContext).httpTransportOutcome(ConnectionOutcome.REMOTE_CLOSE);
        when(ctx.router()).thenReturn(Router.empty());
        ListenerContext listenerContext = mock(ListenerContext.class);
        ListenerConfig listenerConfig = spy(ListenerConfig.builder()
                                                   .errorHandling(ErrorHandling.builder().includeEntity(false).build())
                                                   .build());
        doAnswer(_ -> {
            teardownStarted.countDown();
            return writeMode == TerminalWriteMode.SUCCESS ? Duration.ofNanos(Long.MAX_VALUE) : Duration.ZERO;
        }).when(listenerConfig).shutdownGracePeriod();
        when(listenerContext.config()).thenReturn(listenerConfig);
        when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.dataReader()).thenReturn(reader);
        when(ctx.dataWriter()).thenReturn(writer);
        when(ctx.sniContext()).thenReturn(Optional.empty());
        when(ctx.proxyProtocolData()).thenReturn(Optional.empty());
        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());
        when(ctx.remotePeer()).thenReturn(peerInfo);
        AtomicReference<Thread> streamThread = new AtomicReference<>();
        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            streamThread.set(writeMode == TerminalWriteMode.SOCKET_CLOSE
                                     ? Thread.ofPlatform().start(runnable)
                                     : Thread.ofVirtual().start(runnable));
            return null;
        }).when(executor).submit(any(Runnable.class));
        when(ctx.executor()).thenReturn(executor);
        Http2SubProtocolSelector selector = (_, _, _, streamWriter, streamId, _, _, _, _, _) -> {
            var handler = mock(Http2SubProtocolSelector.SubProtocolHandler.class);
            when(handler.streamState()).thenReturn(Http2StreamState.CLOSED);
            doAnswer(_ -> {
                streamWriter.writeHeaders(Http2Headers.create(WritableHeaders.create()).status(Status.OK_200),
                                          streamId,
                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                          FlowControl.Outbound.NOOP);
                return null;
            }).when(handler).init();
            return new SubProtocolResult(true, handler);
        };
        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of(selector));
        AtomicReference<Throwable> connectionFailure = new AtomicReference<>();
        AtomicReference<List<StreamOutcome>> outcomesWhenHandleReturned = new AtomicReference<>();
        Thread connectionThread = Thread.ofVirtual().start(() -> {
            try {
                connection.handle(mock(Limit.class));
                outcomesWhenHandleReturned.set(List.copyOf(outcomes));
            } catch (Throwable failure) {
                connectionFailure.set(failure);
            }
        });
        try {
            await(peerGoAwayProcessed, "connection must process peer GOAWAY during terminal write");
            if (writeMode == TerminalWriteMode.SUCCESS) {
                await(teardownStarted, "connection teardown must start while terminal publication is held");
                awaitState(connectionThread,
                           Thread.State.TIMED_WAITING,
                           "connection teardown waits for terminal outcome publication");
                releaseTerminalWrite.countDown();
            }
            assertThat("connection handler must terminate", connectionThread.join(Duration.ofSeconds(5)), is(true));
            assertThat("terminal writer must terminate", streamThread.get().join(Duration.ofSeconds(5)), is(true));
            if (writeMode == TerminalWriteMode.SUCCESS) {
                verify(ctx, never()).abortSocket();
                assertThat("successful terminal write must not be interrupted", terminalWriteInterrupted.get(), is(false));
            }
            if (writeMode == TerminalWriteMode.INTERRUPTIBLE) {
                assertThat("connection teardown must interrupt blocked socket I/O",
                           writerInterruption.get(), instanceOf(InterruptedException.class));
            }
            if (writeMode == TerminalWriteMode.SOCKET_CLOSE) {
                assertThat("connection teardown must abort the socket to release platform I/O",
                           socketAborted.getCount(), is(0L));
            }
            assertAll(
                    () -> assertThat(connectionFailure.get(), is(nullValue())),
                    () -> assertThat("terminal response outcome must be observed before handle returns",
                                     outcomesWhenHandleReturned.get(), is(List.of(expectedOutcome))),
                    () -> assertThat("terminal response outcome is observed exactly once",
                                     List.copyOf(outcomes), is(List.of(expectedOutcome)))
            );
        } finally {
            releaseTerminalWrite.countDown();
            terminalResponseWritten.countDown();
            socketAborted.countDown();
            connection.close(true);
            Thread runner = streamThread.get();
            if (runner != null) {
                assertThat("stream cleanup must terminate", runner.join(Duration.ofSeconds(5)), is(true));
            }
            assertThat("connection cleanup must terminate", connectionThread.join(Duration.ofSeconds(5)), is(true));
        }
    }

    private static ObservedConnection observedConnection(DataWriter writer, DataReader reader) {
        ConnectionContext ctx = mock(ConnectionContext.class,
                                     withSettings().extraInterfaces(ConnectionObservationContext.class));
        when(ctx.router()).thenReturn(Router.empty());
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(ListenerConfig.create());
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.dataWriter()).thenReturn(writer);
        when(ctx.dataReader()).thenReturn(reader);
        ConnectionObservationContext observationContext = (ConnectionObservationContext) ctx;
        when(observationContext.httpTransportObservation()).thenReturn(mock(ConnectionObservation.class));
        AtomicReference<ConnectionOutcome> outcome = new AtomicReference<>();
        doAnswer(invocation -> {
            outcome.compareAndSet(null, invocation.getArgument(0));
            return null;
        }).when(observationContext).httpTransportOutcome(any(ConnectionOutcome.class));
        return new ObservedConnection(new Http2Connection(ctx, Http2Config.create(), List.of()), outcome);
    }

    private static byte[] invalidSettingsFrame() {
        return Http2FrameHeader.create(0,
                                       Http2FrameTypes.SETTINGS,
                                       Http2Flag.SettingsFlags.create(0),
                                       1)
                .write()
                .readBytes();
    }

    private static void awaitState(Thread thread, Thread.State expected, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.isAlive() && thread.getState() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(message, thread.getState(), is(expected));
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            assertThat(message, latch.await(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, failure);
        }
    }

    private static void awaitSuccessfulWrite(CountDownLatch release) {
        // A peer can receive the bytes even if connection shutdown interrupts the writer before writeNow returns.
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (true) {
                try {
                    assertThat("terminal write must be released", release.await(Math.max(0, deadline - System.nanoTime()),
                                                                                 TimeUnit.NANOSECONDS), is(true));
                    return;
                } catch (InterruptedException _) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private enum TerminalWriteMode {
        SUCCESS,
        INTERRUPTIBLE,
        SOCKET_CLOSE
    }

    private record ObservedConnection(Http2Connection connection, AtomicReference<ConnectionOutcome> outcome) {
    }
}
