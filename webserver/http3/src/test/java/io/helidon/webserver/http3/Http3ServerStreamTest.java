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

package io.helidon.webserver.http3;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3Settings;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http3ServerStreamTest {
    @Test
    void finalDataIsChunkedWithoutAwaitingDispatch() throws Exception {
        TestStream testStream = testStream(65_536);
        CompletableFuture<Void> dispatch = new CompletableFuture<>();
        CountDownLatch receiptRequested = new CountDownLatch(1);
        when(testStream.writer().scheduleForWritingAndGetDispatchCompletion(any(), eq(true)))
                .thenAnswer(_ -> {
                    receiptRequested.countDown();
                    return dispatch;
                });

        CompletableFuture<Void> writing = CompletableFuture.runAsync(
                () -> testStream.stream().writeData(new byte[40_000], 0, 40_000, true));
        boolean requested = receiptRequested.await(5, TimeUnit.SECONDS);
        writing.get(5, TimeUnit.SECONDS);
        boolean dispatchStillPending = !dispatch.isDone();
        dispatch.complete(null);

        assertThat(requested, equalTo(true));
        assertThat(dispatchStillPending, equalTo(true));
        InOrder order = inOrder(testStream.writer());
        order.verify(testStream.writer(), times(2)).scheduleForWriting(any(), eq(false));
        order.verify(testStream.writer()).scheduleForWritingAndGetDispatchCompletion(any(), eq(true));
    }

    @Test
    void emptyFinDoesNotAwaitDispatch() throws Exception {
        TestStream testStream = testStream(65_536);
        CompletableFuture<Void> dispatch = new CompletableFuture<>();
        CountDownLatch receiptRequested = new CountDownLatch(1);
        when(testStream.writer().scheduleForWritingAndGetDispatchCompletion(any(), eq(true)))
                .thenAnswer(_ -> {
                    receiptRequested.countDown();
                    return dispatch;
                });

        CompletableFuture<Void> writing = CompletableFuture.runAsync(() -> {
            testStream.stream().writeData(new byte[1_024], 0, 1_024, false);
            testStream.stream().writeFin();
        });
        boolean requested = receiptRequested.await(5, TimeUnit.SECONDS);
        writing.get(5, TimeUnit.SECONDS);
        boolean dispatchStillPending = !dispatch.isDone();
        dispatch.complete(null);

        ArgumentCaptor<BufferData> bufferCaptor = ArgumentCaptor.forClass(BufferData.class);
        InOrder order = inOrder(testStream.writer());
        order.verify(testStream.writer()).scheduleForWriting(any(), eq(false));
        order.verify(testStream.writer())
                .scheduleForWritingAndGetDispatchCompletion(bufferCaptor.capture(), eq(true));
        assertThat(requested, equalTo(true));
        assertThat(dispatchStillPending, equalTo(true));
        assertThat(bufferCaptor.getValue().available(), equalTo(0));
    }

    @Test
    void finalTrailersDoNotAwaitDispatch() throws Exception {
        TestStream testStream = testStream(65_536);
        CompletableFuture<Void> dispatch = new CompletableFuture<>();
        CountDownLatch receiptRequested = new CountDownLatch(1);
        when(testStream.writer().scheduleForWritingAndGetDispatchCompletion(any(), eq(true)))
                .thenAnswer(_ -> {
                    receiptRequested.countDown();
                    return dispatch;
                });

        CompletableFuture<Void> writing = CompletableFuture.runAsync(() -> {
            testStream.stream().writeData(new byte[1_024], 0, 1_024, false);
            testStream.stream().writeTrailers(WritableHeaders.create(), true);
        });
        boolean requested = receiptRequested.await(5, TimeUnit.SECONDS);
        writing.get(5, TimeUnit.SECONDS);
        boolean dispatchStillPending = !dispatch.isDone();
        dispatch.complete(null);

        ArgumentCaptor<BufferData> bufferCaptor = ArgumentCaptor.forClass(BufferData.class);
        InOrder order = inOrder(testStream.writer());
        order.verify(testStream.writer()).scheduleForWriting(any(), eq(false));
        order.verify(testStream.writer())
                .scheduleForWritingAndGetDispatchCompletion(bufferCaptor.capture(), eq(true));
        assertThat(requested, equalTo(true));
        assertThat(dispatchStillPending, equalTo(true));
        assertThat(bufferCaptor.getValue().available(), greaterThan(0));
    }

    @Test
    void alreadyFailedFinalDispatchIsReportedSynchronously() {
        TestStream testStream = testStream(65_536);
        when(testStream.writer().scheduleForWritingAndGetDispatchCompletion(any(), eq(true)))
                .thenReturn(CompletableFuture.failedFuture(new IOException("test dispatch failure")));

        UncheckedIOException failure = assertThrows(UncheckedIOException.class, testStream.stream()::writeFin);

        assertThat(failure.getCause(), instanceOf(IOException.class));
    }

    @Test
    void responseDispatchWindowAwaitsDispatch() throws Exception {
        TestStream testStream = testStream(1_024);
        CompletableFuture<Void> dispatch = new CompletableFuture<>();
        CountDownLatch receiptRequested = new CountDownLatch(1);
        when(testStream.writer().scheduleForWritingAndGetDispatchCompletion(any(), eq(false)))
                .thenAnswer(_ -> {
                    receiptRequested.countDown();
                    return dispatch;
                });

        CompletableFuture<Void> writing = CompletableFuture.runAsync(
                () -> testStream.stream().writeData(new byte[1_024], 0, 1_024, false));
        boolean requested = receiptRequested.await(5, TimeUnit.SECONDS);
        boolean completedBeforeDispatch = writing.isDone();
        dispatch.complete(null);
        writing.get(5, TimeUnit.SECONDS);

        assertThat(requested, equalTo(true));
        assertThat(completedBeforeDispatch, equalTo(false));
    }

    @Test
    void flushAwaitsDispatch() throws Exception {
        TestStream testStream = testStream(65_536);
        CompletableFuture<Void> dispatch = new CompletableFuture<>();
        CountDownLatch receiptRequested = new CountDownLatch(1);
        when(testStream.writer().scheduleForWritingAndGetDispatchCompletion(any(), eq(false)))
                .thenAnswer(_ -> {
                    receiptRequested.countDown();
                    return dispatch;
                });

        CompletableFuture<Void> writing = CompletableFuture.runAsync(() -> {
            testStream.stream().writeData(new byte[1_024], 0, 1_024, false);
            testStream.stream().flushResponseData();
        });
        boolean requested = receiptRequested.await(5, TimeUnit.SECONDS);
        boolean completedBeforeDispatch = writing.isDone();
        dispatch.complete(null);
        writing.get(5, TimeUnit.SECONDS);

        assertThat(requested, equalTo(true));
        assertThat(completedBeforeDispatch, equalTo(false));
        InOrder order = inOrder(testStream.writer());
        order.verify(testStream.writer()).scheduleForWriting(any(), eq(false));
        order.verify(testStream.writer()).scheduleForWritingAndGetDispatchCompletion(any(), eq(false));
    }

    @Test
    void interruptionDuringResponseWindowCancelsResponseAndRestoresInterruptFlag() throws Exception {
        TestStream testStream = testStream(1_024);
        CompletableFuture<Void> dispatch = new CompletableFuture<>();
        CountDownLatch receiptRequested = new CountDownLatch(1);
        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        when(testStream.writer().scheduleForWritingAndGetDispatchCompletion(any(), eq(false)))
                .thenAnswer(_ -> {
                    receiptRequested.countDown();
                    return dispatch;
                });
        Thread writerThread = Thread.ofVirtual().start(() -> {
            try {
                testStream.stream().writeData(new byte[1_024], 0, 1_024, false);
                failure.complete(new AssertionError("Response dispatch unexpectedly completed"));
            } catch (Throwable t) {
                interrupted.set(Thread.currentThread().isInterrupted());
                failure.complete(t);
            }
        });

        boolean requested = receiptRequested.await(5, TimeUnit.SECONDS);
        writerThread.interrupt();
        Throwable thrown = failure.get(5, TimeUnit.SECONDS);
        writerThread.join(5_000);

        assertThat(requested, equalTo(true));
        assertThat(thrown, instanceOf(UncheckedIOException.class));
        assertThat(thrown.getCause(), instanceOf(InterruptedIOException.class));
        assertThat(interrupted.get(), equalTo(true));
        assertThat(writerThread.isAlive(), equalTo(false));
        verify(testStream.transportStream()).reset(Http3ErrorCode.REQUEST_CANCELLED.code());
    }

    private static TestStream testStream(int responseDispatchWindowSize) {
        Http3ServerConnection serverConnection = mock(Http3ServerConnection.class);
        QuicBidiStream stream = mock(QuicBidiStream.class);
        QuicStreamReader reader = mock(QuicStreamReader.class);
        QuicStreamWriter writer = mock(QuicStreamWriter.class);
        QuicConnection connection = mock(QuicConnection.class);
        Http3QpackContext qpackContext = Http3QpackContext.create(0, 0, 16_384, _ -> {
        });
        Http3FrameListener frameListener = Http3FrameListener.create(List.of());
        Http3Config config = Http3Config.builder()
                .responseDispatchWindowSize(responseDispatchWindowSize)
                .buildPrototype();

        when(stream.streamId()).thenReturn(0L);
        when(stream.connectReader(any())).thenReturn(reader);
        when(stream.connectWriter(any())).thenReturn(writer);
        when(serverConnection.qpackContext()).thenReturn(qpackContext);
        when(serverConnection.connection()).thenReturn(connection);

        return new TestStream(new Http3ServerStream(serverConnection,
                                                     stream,
                                                     1,
                                                     mock(Http3Handler.class),
                                                     Http3Settings.create(0, 0),
                                                     Duration.ZERO,
                                                     frameListener,
                                                     frameListener,
                                                     config,
                                                     mock(Limit.class),
                                                     mock(StreamObservation.class)),
                              stream,
                              writer);
    }

    private record TestStream(Http3ServerStream stream,
                              QuicBidiStream transportStream,
                              QuicStreamWriter writer) {
    }
}
