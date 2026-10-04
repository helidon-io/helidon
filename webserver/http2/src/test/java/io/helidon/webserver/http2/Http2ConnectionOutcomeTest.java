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

import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.HttpTransportObserverSupport.ConnectionObservationContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;

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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class Http2ConnectionOutcomeTest {
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
            assertThat("handle must not return before GOAWAY outcome is known",
                       connectionThread.join(Duration.ofMillis(100)),
                       is(false));
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

    private static ObservedConnection observedConnection(DataWriter writer, DataReader reader) {
        ConnectionContext ctx = mock(ConnectionContext.class,
                                     withSettings().extraInterfaces(ConnectionObservationContext.class));
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(mock(ListenerContext.class));
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

    private static void await(CountDownLatch latch, String message) {
        try {
            assertThat(message, latch.await(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, failure);
        }
    }

    private record ObservedConnection(Http2Connection connection, AtomicReference<ConnectionOutcome> outcome) {
    }
}
