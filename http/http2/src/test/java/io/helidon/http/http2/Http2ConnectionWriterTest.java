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

package io.helidon.http.http2;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2ConnectionWriterTest {

    @Test
    void closesWriterWhenSynchronousWindowUpdateWriteFails() {
        SocketWriterException writeFailure = new SocketWriterException();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            throw writeFailure;
        }).when(dataWriter).writeNow(any(BufferData.class));
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());
        Http2WindowUpdate windowUpdate = new Http2WindowUpdate(1);

        SocketWriterException thrown = assertThrows(SocketWriterException.class,
                                                     () -> writer.write(windowUpdate.toFrameData(null,
                                                                                                1,
                                                                                                Http2Flag.NoFlags.create())));

        assertThat(thrown, is(writeFailure));
        verify(dataWriter).close();
    }

    @Test
    void closesWriterWhenPendingWindowUpdateFailsBeforeReset() throws InterruptedException {
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<Throwable> dataFailure = new AtomicReference<>();
        AtomicReference<Throwable> resetFailure = new AtomicReference<>();
        CountDownLatch dataWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseDataWrite = new CountDownLatch(1);
        CountDownLatch resetWriteStarted = new CountDownLatch(1);
        SocketWriterException writeFailure = new SocketWriterException();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            if (writes.incrementAndGet() == 1) {
                dataWriteStarted.countDown();
                releaseDataWrite.await();
                return null;
            }
            throw writeFailure;
        }).when(dataWriter).writeNow(any(BufferData.class));
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());
        Thread dataWriterThread = Thread.ofVirtual().start(() -> {
            try {
                writer.write(dataFrame(1, 1024));
            } catch (Throwable t) {
                dataFailure.set(t);
            }
        });

        assertThat("DATA write must start", dataWriteStarted.await(1, TimeUnit.SECONDS), is(true));
        Http2RstStream reset = new Http2RstStream(Http2ErrorCode.CANCEL);
        Thread resetWriterThread = Thread.ofVirtual().start(() -> {
            resetWriteStarted.countDown();
            try {
                writer.write(reset.toFrameData(null, 1, Http2Flag.NoFlags.create()));
            } catch (Throwable t) {
                resetFailure.set(t);
            }
        });

        try {
            assertThat("reset write must start", resetWriteStarted.await(1, TimeUnit.SECONDS), is(true));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (resetWriterThread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat("reset writer must wait for the DATA write",
                       resetWriterThread.getState(),
                       is(Thread.State.WAITING));
            Http2WindowUpdate windowUpdate = new Http2WindowUpdate(1);
            writer.write(windowUpdate.toFrameData(null, 1, Http2Flag.NoFlags.create()));
        } finally {
            releaseDataWrite.countDown();
        }

        dataWriterThread.join(TimeUnit.SECONDS.toMillis(2));
        resetWriterThread.join(TimeUnit.SECONDS.toMillis(2));
        assertThat("DATA writer must terminate", dataWriterThread.isAlive(), is(false));
        assertThat("reset writer must terminate", resetWriterThread.isAlive(), is(false));
        assertThat(dataFailure.get(), is(nullValue()));
        assertThat(resetFailure.get(), is(writeFailure));
        assertThat(writes.get(), is(2));
        verify(dataWriter).close();
    }

    @Test
    void writesPendingWindowUpdatesBeforeResetWhileDataWriteBlocked() throws InterruptedException {
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger windowIncrement = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch dataWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseDataWrite = new CountDownLatch(1);
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            if (writes.incrementAndGet() == 1) {
                dataWriteStarted.countDown();
                releaseDataWrite.await();
            }
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        List<Http2FrameType> frameTypes = new ArrayList<>();
        AtomicReference<Http2FrameType> frameType = new AtomicReference<>();
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                frameTypes.add(header.type());
                frameType.set(header.type());
            }

            @Override
            public void frame(SocketContext ctx, int streamId, BufferData data) {
                if (frameType.get() == Http2FrameType.WINDOW_UPDATE) {
                    windowIncrement.set(data.copy().readInt32() & Integer.MAX_VALUE);
                }
            }
        };
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of(listener));
        Thread dataWriterThread = Thread.ofVirtual().start(() -> {
            try {
                writer.write(dataFrame(1, 1024));
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });

        assertThat("DATA write must start", dataWriteStarted.await(1, TimeUnit.SECONDS), is(true));
        for (int i = 0; i < 2; i++) {
            Http2WindowUpdate windowUpdate = new Http2WindowUpdate(1);
            writer.write(windowUpdate.toFrameData(null, 1, Http2Flag.NoFlags.create()));
        }
        Http2RstStream reset = new Http2RstStream(Http2ErrorCode.CANCEL);
        Thread resetWriterThread = Thread.ofVirtual().start(() -> {
            try {
                writer.write(reset.toFrameData(null, 1, Http2Flag.NoFlags.create()));
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });

        try {
            releaseDataWrite.countDown();
            dataWriterThread.join(TimeUnit.SECONDS.toMillis(2));
            resetWriterThread.join(TimeUnit.SECONDS.toMillis(2));
        } finally {
            releaseDataWrite.countDown();
        }

        assertThat("DATA writer must terminate", dataWriterThread.isAlive(), is(false));
        assertThat("reset writer must terminate", resetWriterThread.isAlive(), is(false));
        assertThat(writes.get(), is(3));
        assertThat(frameTypes, is(List.of(Http2FrameType.DATA,
                                          Http2FrameType.WINDOW_UPDATE,
                                          Http2FrameType.RST_STREAM)));
        assertThat(windowIncrement.get(), is(2));
        assertThat(failure.get(), is(nullValue()));
    }

    @Test
    void doesNotWriteWindowUpdateAfterReset() {
        AtomicReference<Http2ConnectionWriter> writerRef = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Http2FrameType> frameTypes = new ArrayList<>();
        List<Integer> windowUpdateStreamIds = new ArrayList<>();
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                frameTypes.add(header.type());
                if (header.type() == Http2FrameType.WINDOW_UPDATE) {
                    windowUpdateStreamIds.add(streamId);
                }
                if (header.type() == Http2FrameType.RST_STREAM) {
                    Thread lateWindowUpdateWriter = Thread.ofVirtual().start(() -> {
                        try {
                            Http2WindowUpdate windowUpdate = new Http2WindowUpdate(1);
                            writerRef.get().write(windowUpdate.toFrameData(null,
                                                                          streamId,
                                                                          Http2Flag.NoFlags.create()));
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                        }
                    });
                    try {
                        lateWindowUpdateWriter.join(TimeUnit.SECONDS.toMillis(2));
                        if (lateWindowUpdateWriter.isAlive()) {
                            failure.compareAndSet(null, new AssertionError("WINDOW_UPDATE writer did not terminate"));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted", e);
                    }
                }
            }
        };
        DataWriter dataWriter = mock(DataWriter.class);
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of(listener));
        writerRef.set(writer);

        Http2RstStream reset = new Http2RstStream(Http2ErrorCode.CANCEL);
        writer.write(reset.toFrameData(null, 1, Http2Flag.NoFlags.create()));
        Http2WindowUpdate windowUpdate = new Http2WindowUpdate(1);
        writer.write(windowUpdate.toFrameData(null, 0, Http2Flag.NoFlags.create()));
        writer.write(Http2Ping.create().toFrameData());

        assertThat(frameTypes, is(List.of(Http2FrameType.RST_STREAM,
                                          Http2FrameType.WINDOW_UPDATE,
                                          Http2FrameType.PING)));
        assertThat(windowUpdateStreamIds, is(List.of(0)));
        assertThat(failure.get(), is(nullValue()));
        verify(dataWriter, times(3)).writeNow(any(BufferData.class));
    }

    @Test
    void coalescesWindowUpdateBacklogWhileWriterIsBlocked() throws InterruptedException {
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger windowIncrement = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch dataWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseDataWrite = new CountDownLatch(1);
        CountDownLatch windowUpdateWritten = new CountDownLatch(1);
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            int write = writes.incrementAndGet();
            if (write == 1) {
                dataWriteStarted.countDown();
                releaseDataWrite.await();
            } else {
                windowUpdateWritten.countDown();
            }
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        AtomicReference<Http2FrameType> frameType = new AtomicReference<>();
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                frameType.set(header.type());
            }

            @Override
            public void frame(SocketContext ctx, int streamId, BufferData data) {
                if (frameType.get() == Http2FrameType.WINDOW_UPDATE) {
                    windowIncrement.set(data.copy().readInt32() & Integer.MAX_VALUE);
                }
            }
        };
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of(listener));
        Thread dataWriterThread = Thread.ofVirtual().start(() -> {
            try {
                writer.write(dataFrame(1, 1024));
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });

        assertThat("DATA write must start", dataWriteStarted.await(1, TimeUnit.SECONDS), is(true));
        try {
            for (int i = 0; i < 5000; i++) {
                Http2WindowUpdate windowUpdate = new Http2WindowUpdate(1);
                writer.write(windowUpdate.toFrameData(null, 1, Http2Flag.NoFlags.create()));
            }
        } finally {
            releaseDataWrite.countDown();
        }

        dataWriterThread.join(TimeUnit.SECONDS.toMillis(2));
        assertThat("DATA writer must terminate", dataWriterThread.isAlive(), is(false));
        assertThat("WINDOW_UPDATE must be written", windowUpdateWritten.await(2, TimeUnit.SECONDS), is(true));
        assertThat(writes.get(), is(2));
        assertThat(windowIncrement.get(), is(5000));
        assertThat(failure.get(), is(nullValue()));
    }

    @Test
    void concurrentWritersDoNotReuseConnectionWindowCredit() throws InterruptedException {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofSeconds(5))
                .build();
        connection.outbound().decrementWindowSize(connection.outbound().getRemainingWindowSize());

        AtomicInteger cuts = new AtomicInteger();
        CountDownLatch fourthCut = new CountDownLatch(1);
        CountDownLatch initialWait = new CountDownLatch(2);
        CountDownLatch resumedWait = new CountDownLatch(2);
        CountDownLatch thirdWait = new CountDownLatch(1);
        FlowControl.Outbound firstFlowControl = trackingFlowControl(connection.createStreamFlowControl(1, 1024, 16384)
                                                                           .outbound(),
                                                                   cuts,
                                                                   fourthCut,
                                                                   initialWait,
                                                                   resumedWait,
                                                                   thirdWait);
        FlowControl.Outbound secondFlowControl = trackingFlowControl(connection.createStreamFlowControl(3, 1024, 16384)
                                                                            .outbound(),
                                                                    cuts,
                                                                    fourthCut,
                                                                    initialWait,
                                                                    resumedWait,
                                                                    thirdWait);

        AtomicInteger writes = new AtomicInteger();
        AtomicReference<Thread> firstWritingThread = new AtomicReference<>();
        CountDownLatch firstWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            if (writes.incrementAndGet() == 1) {
                firstWritingThread.set(Thread.currentThread());
                firstWriteStarted.countDown();
                releaseFirstWrite.await();
            }
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));

        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());
        Http2FrameData firstFrame = dataFrame(1, 1024);
        Http2FrameData secondFrame = dataFrame(3, 1024);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread firstWriter = Thread.ofVirtual().start(() -> writeData(writer, firstFrame, firstFlowControl, failure));
        Thread secondWriter = Thread.ofVirtual().start(() -> writeData(writer, secondFrame, secondFlowControl, failure));
        try {
            boolean bothWaiting = initialWait.await(1, TimeUnit.SECONDS);
            assertThat("both writers must wait for connection credit",
                       bothWaiting,
                       is(true));

            connection.incrementOutboundConnectionWindowSize(1024);
            assertThat("first DATA write must start", firstWriteStarted.await(1, TimeUnit.SECONDS), is(true));
            assertThat("both connection-window waits must resume", resumedWait.await(1, TimeUnit.SECONDS), is(true));
            Thread contender = firstWritingThread.get() == firstWriter ? secondWriter : firstWriter;
            long contenderDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (contender.getState() != Thread.State.WAITING && System.nanoTime() < contenderDeadline) {
                Thread.onSpinWait();
            }
            assertThat("second writer must wait for the writer lock", contender.getState(), is(Thread.State.WAITING));
            assertThat("second writer must not cut with stale connection credit", fourthCut.getCount(), is(1L));
            releaseFirstWrite.countDown();

            assertThat("second writer must wait for more connection credit",
                       thirdWait.await(1, TimeUnit.SECONDS),
                       is(true));
            assertThat("one connection-window update must fund one DATA frame", writes.get(), is(1));
        } finally {
            releaseFirstWrite.countDown();
            connection.incrementOutboundConnectionWindowSize(1024);
            firstWriter.join();
            secondWriter.join();
        }

        assertThat(writes.get(), is(2));
        assertThat(failure.get(), is(nullValue()));
    }

    @Test
    void endStreamCallbackRunsAfterHeadersAreWritten() {
        DataWriter dataWriter = mock(DataWriter.class);
        AtomicBoolean callbackCalled = new AtomicBoolean();
        AtomicBoolean writeReturned = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(callbackCalled.get(), is(false));
            writeReturned.set(true);
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));

        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());

        int written = writer.writeHeaders(headers(),
                                          1,
                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                          flowControl(),
                                          () -> {
                                              assertThat(writeReturned.get(), is(true));
                                              callbackCalled.set(true);
                                          });

        assertThat(callbackCalled.get(), is(true));
        assertThat(written, greaterThan(Http2FrameHeader.LENGTH));
        verify(dataWriter).writeNow(any(BufferData.class));
    }

    @Test
    void endStreamCallbackRunsAfterHeadersAndDataAreWritten() {
        DataWriter dataWriter = mock(DataWriter.class);
        AtomicBoolean callbackCalled = new AtomicBoolean();
        AtomicBoolean flowControlDebited = new AtomicBoolean();
        AtomicInteger writes = new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(callbackCalled.get(), is(false));
            writes.incrementAndGet();
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);
        FlowControl.Outbound flowControl = flowControl();
        doAnswer(invocation -> {
            assertThat(callbackCalled.get(), is(false));
            flowControlDebited.set(true);
            return null;
        }).when(flowControl).decrementWindowSize(data.length);

        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());
        Http2FrameData frame = new Http2FrameData(Http2FrameHeader.create(data.length,
                                                                          Http2FrameTypes.DATA,
                                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                          1),
                                                  BufferData.create(data));

        int written = writer.writeHeaders(headers(),
                                          1,
                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                          frame,
                                          flowControl,
                                          () -> {
                                              assertThat(writes.get(), is(1));
                                              assertThat(flowControlDebited.get(), is(true));
                                              callbackCalled.set(true);
                                          });

        assertThat(callbackCalled.get(), is(true));
        assertThat(written, greaterThan(data.length + Http2FrameHeader.LENGTH));
        verify(dataWriter).writeNow(any(BufferData.class));
        verify(flowControl).cut(frame);
        verify(flowControl).decrementWindowSize(data.length);
    }

    @Test
    void endStreamCallbackRunsOnceOutsideWriterLock() {
        AtomicInteger callbackCalls = new AtomicInteger();
        AtomicReference<Throwable> competitorFailure = new AtomicReference<>();
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class),
                                                                  mock(DataWriter.class),
                                                                  List.of());
        Http2FrameData frame = new Http2FrameData(Http2FrameHeader.create(1,
                                                                          Http2FrameTypes.DATA,
                                                                          Http2Flag.DataFlags.create(
                                                                                  Http2Flag.END_OF_STREAM),
                                                                          1),
                                                  BufferData.create(new byte[] {1}));

        writer.writeHeaders(headers(),
                            1,
                            Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                            frame,
                            flowControl(),
                            () -> {
                                callbackCalls.incrementAndGet();
                                Thread competitor = Thread.ofVirtual().start(() -> {
                                    try {
                                        writer.write(dataFrame(3, 1));
                                    } catch (Throwable t) {
                                        competitorFailure.set(t);
                                    }
                                });
                                try {
                                    competitor.join(TimeUnit.SECONDS.toMillis(1));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError("Interrupted while waiting for competing writer", e);
                                }
                                assertThat("callback must not retain the writer lock", competitor.isAlive(), is(false));
                            });

        assertThat(callbackCalls.get(), is(1));
        assertThat(competitorFailure.get(), is(nullValue()));
    }

    @Test
    void smallHeadersAndDataAreWrittenWithoutInterleaving() throws InterruptedException {
        CountDownLatch headerWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseHeaderWrite = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<Throwable> responseFailure = new AtomicReference<>();
        AtomicReference<Throwable> competitorFailure = new AtomicReference<>();
        List<Integer> streamIds = new ArrayList<>();
        List<Http2FrameType> frameTypes = new ArrayList<>();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            if (writes.incrementAndGet() == 1) {
                headerWriteStarted.countDown();
                releaseHeaderWrite.await();
            }
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                streamIds.add(streamId);
                frameTypes.add(header.type());
            }
        };
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of(listener));
        Http2FrameData responseData = new Http2FrameData(Http2FrameHeader.create(1,
                                                                                 Http2FrameTypes.DATA,
                                                                                 Http2Flag.DataFlags.create(
                                                                                         Http2Flag.END_OF_STREAM),
                                                                                 1),
                                                         BufferData.create(new byte[] {1}));
        Thread responseWriter = Thread.ofVirtual().start(() -> {
            try {
                writer.writeHeaders(headers(),
                                    1,
                                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                    responseData,
                                    FlowControl.Outbound.NOOP);
            } catch (Throwable t) {
                responseFailure.set(t);
            }
        });

        assertThat("header write must start", headerWriteStarted.await(1, TimeUnit.SECONDS), is(true));
        Thread competitor = Thread.ofVirtual().start(() -> {
            try {
                writer.write(dataFrame(3, 1));
            } catch (Throwable t) {
                competitorFailure.set(t);
            }
        });

        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (competitor.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat("competitor must wait for the writer lock", competitor.getState(), is(Thread.State.WAITING));
        } finally {
            releaseHeaderWrite.countDown();
        }

        responseWriter.join(TimeUnit.SECONDS.toMillis(2));
        competitor.join(TimeUnit.SECONDS.toMillis(2));
        assertThat("response writer must terminate", responseWriter.isAlive(), is(false));
        assertThat("competitor must terminate", competitor.isAlive(), is(false));
        assertThat(responseFailure.get(), is(nullValue()));
        assertThat(competitorFailure.get(), is(nullValue()));
        assertThat(streamIds, is(List.of(1, 1, 3)));
        assertThat(frameTypes, is(List.of(Http2FrameType.HEADERS, Http2FrameType.DATA, Http2FrameType.DATA)));
    }

    @Test
    void windowUpdateArrivingDuringBatchIsWrittenAfterBatch() throws InterruptedException {
        CountDownLatch headerWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseHeaderWrite = new CountDownLatch(1);
        CountDownLatch windowUpdateWritten = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<Throwable> responseFailure = new AtomicReference<>();
        List<Http2FrameType> frameTypes = new ArrayList<>();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            if (writes.incrementAndGet() == 1) {
                headerWriteStarted.countDown();
                releaseHeaderWrite.await();
            }
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                frameTypes.add(header.type());
                if (header.type() == Http2FrameType.WINDOW_UPDATE) {
                    windowUpdateWritten.countDown();
                }
            }
        };
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of(listener));
        Http2FrameData responseData = new Http2FrameData(Http2FrameHeader.create(1,
                                                                                 Http2FrameTypes.DATA,
                                                                                 Http2Flag.DataFlags.create(
                                                                                         Http2Flag.END_OF_STREAM),
                                                                                 1),
                                                         BufferData.create(new byte[] {1}));
        Thread responseWriter = Thread.ofVirtual().start(() -> {
            try {
                writer.writeHeaders(headers(),
                                    1,
                                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                    responseData,
                                    FlowControl.Outbound.NOOP);
            } catch (Throwable t) {
                responseFailure.set(t);
            }
        });

        try {
            assertThat("header write must start", headerWriteStarted.await(1, TimeUnit.SECONDS), is(true));
            writer.write(new Http2WindowUpdate(1).toFrameData(null, 1, Http2Flag.NoFlags.create()));
        } finally {
            releaseHeaderWrite.countDown();
        }

        responseWriter.join(TimeUnit.SECONDS.toMillis(2));
        assertThat("response writer must terminate", responseWriter.isAlive(), is(false));
        assertThat("WINDOW_UPDATE must be written", windowUpdateWritten.await(2, TimeUnit.SECONDS), is(true));
        assertThat(responseFailure.get(), is(nullValue()));
        assertThat(frameTypes, is(List.of(Http2FrameType.HEADERS,
                                          Http2FrameType.DATA,
                                          Http2FrameType.WINDOW_UPDATE)));
    }

    @Test
    void scheduledWindowUpdateSeparatesHeaderBlockFromData() throws InterruptedException {
        CountDownLatch blockerWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseBlockerWrite = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<Throwable> blockerFailure = new AtomicReference<>();
        AtomicReference<Throwable> responseFailure = new AtomicReference<>();
        List<Http2FrameType> frameTypes = new ArrayList<>();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            if (writes.incrementAndGet() == 1) {
                blockerWriteStarted.countDown();
                releaseBlockerWrite.await();
            }
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                frameTypes.add(header.type());
            }
        };
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class),
                                                                  dataWriter,
                                                                  List.of(listener));
        Thread blocker = Thread.ofVirtual().start(() -> {
            try {
                writer.write(dataFrame(3, 1));
            } catch (Throwable t) {
                blockerFailure.set(t);
            }
        });

        assertThat("blocker write must start", blockerWriteStarted.await(1, TimeUnit.SECONDS), is(true));
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        writableHeaders.add(HeaderNames.create("x-large-header"),
                            "abcdefghijklmnopqrstuvwxyz0123456789".repeat(16));
        Http2Headers headers = Http2Headers.create(writableHeaders)
                .status(Status.OK_200);
        FlowControl.Outbound flowControl = flowControl();
        when(flowControl.maxFrameSize()).thenReturn(32);
        Http2FrameData responseData = new Http2FrameData(Http2FrameHeader.create(1,
                                                                                 Http2FrameTypes.DATA,
                                                                                 Http2Flag.DataFlags.create(
                                                                                         Http2Flag.END_OF_STREAM),
                                                                                 1),
                                                         BufferData.create(new byte[] {1}));
        Thread response = Thread.ofVirtual().start(() -> {
            try {
                writer.writeHeaders(headers,
                                    1,
                                    Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                    responseData,
                                    flowControl);
            } catch (Throwable t) {
                responseFailure.set(t);
            }
        });

        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (response.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat("response writer must wait for the writer lock", response.getState(), is(Thread.State.WAITING));
            writer.write(new Http2WindowUpdate(1).toFrameData(null, 1, Http2Flag.NoFlags.create()));
        } finally {
            releaseBlockerWrite.countDown();
        }

        blocker.join(TimeUnit.SECONDS.toMillis(2));
        response.join(TimeUnit.SECONDS.toMillis(2));
        assertThat("blocker writer must terminate", blocker.isAlive(), is(false));
        assertThat("response writer must terminate", response.isAlive(), is(false));
        assertThat(blockerFailure.get(), is(nullValue()));
        assertThat(responseFailure.get(), is(nullValue()));
        assertThat(frameTypes.size() > 4, is(true));
        assertThat(frameTypes.getFirst(), is(Http2FrameType.DATA));
        assertThat(frameTypes.get(1), is(Http2FrameType.HEADERS));
        for (int i = 2; i < frameTypes.size() - 2; i++) {
            assertThat(frameTypes.get(i), is(Http2FrameType.CONTINUATION));
        }
        assertThat(frameTypes.get(frameTypes.size() - 2), is(Http2FrameType.WINDOW_UPDATE));
        assertThat(frameTypes.getLast(), is(Http2FrameType.DATA));
        assertThat("blocker, each header fragment, WINDOW_UPDATE, and DATA must use separate transport writes",
                   writes.get(),
                   is(frameTypes.size()));
    }

    @Test
    void headersAndDataWaitWhenFlowControlWindowIsExhausted() throws InterruptedException {
        RecordingDataWriter dataWriter = new RecordingDataWriter();
        AtomicBoolean callbackCalled = new AtomicBoolean();
        AtomicBoolean creditAvailable = new AtomicBoolean();
        AtomicReference<Integer> bytesWritten = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch waitingForCredit = new CountDownLatch(1);
        CountDownLatch releaseCredit = new CountDownLatch(1);
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);
        FlowControl.Outbound flowControl = flowControl();
        when(flowControl.cut(any(Http2FrameData.class)))
                .thenAnswer(invocation -> creditAvailable.get()
                        ? new Http2FrameData[] {invocation.getArgument(0)}
                        : new Http2FrameData[0]);
        doAnswer(_ -> {
            waitingForCredit.countDown();
            releaseCredit.await();
            creditAvailable.set(true);
            return null;
        }).when(flowControl).blockTillUpdate();

        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());
        Http2FrameData frame = new Http2FrameData(Http2FrameHeader.create(data.length,
                                                                          Http2FrameTypes.DATA,
                                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                          1),
                                                  BufferData.create(data));
        Thread responseWriter = Thread.ofVirtual().start(() -> {
            try {
                bytesWritten.set(writer.writeHeaders(headers(),
                                                     1,
                                                     Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                                     frame,
                                                     flowControl,
                                                     () -> callbackCalled.set(true)));
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        try {
            assertThat("writer must wait for flow-control credit", waitingForCredit.await(1, TimeUnit.SECONDS), is(true));
            assertThat(callbackCalled.get(), is(false));
            assertThat(dataWriter.writes.size(), is(1));
            List<CapturedFrame> headerFrames = parseFrames(dataWriter.writes.getFirst());
            assertThat(headerFrames.stream().map(captured -> captured.header().type()).toList(),
                       is(List.of(Http2FrameType.HEADERS)));
        } finally {
            releaseCredit.countDown();
        }
        responseWriter.join(TimeUnit.SECONDS.toMillis(2));

        assertThat("response writer must terminate", responseWriter.isAlive(), is(false));
        assertThat(failure.get(), is(nullValue()));
        assertThat(callbackCalled.get(), is(true));
        assertThat(bytesWritten.get(), greaterThan(data.length + Http2FrameHeader.LENGTH));
        assertThat(dataWriter.writes.size(), is(2));
        List<CapturedFrame> finalFrames = parseFrames(dataWriter.writes.getLast());
        assertThat(finalFrames.size(), is(1));
        assertThat(finalFrames.getFirst().header().type(), is(Http2FrameType.DATA));
        assertThat(finalFrames.getFirst().header().flags(Http2FrameTypes.DATA).endOfStream(), is(true));
        assertThat(finalFrames.getFirst().data(), is(data));
        verify(flowControl, times(2)).cut(frame);
        verify(flowControl).blockTillUpdate();
        verify(flowControl).decrementWindowSize(data.length);
    }

    @Test
    void combinedWriteAccountsForPartialFlowControlWindow() {
        AtomicBoolean callbackCalled = new AtomicBoolean();
        AtomicInteger actualBytes = new AtomicInteger();
        AtomicInteger cuts = new AtomicInteger();
        AtomicInteger debits = new AtomicInteger();
        RecordingDataWriter dataWriter = new RecordingDataWriter();
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                actualBytes.addAndGet(Http2FrameHeader.LENGTH + header.length());
            }
        };
        FlowControl.Outbound flowControl = flowControl();
        when(flowControl.cut(any(Http2FrameData.class))).thenAnswer(invocation -> {
            Http2FrameData argument = invocation.getArgument(0);
            return cuts.getAndIncrement() == 0
                    ? argument.cut(1)
                    : new Http2FrameData[] {argument};
        });
        doAnswer(invocation -> {
            debits.incrementAndGet();
            return null;
        }).when(flowControl).decrementWindowSize(1);
        byte[] data = new byte[] {1, 2};
        Http2FrameData frame = new Http2FrameData(Http2FrameHeader.create(data.length,
                                                                          Http2FrameTypes.DATA,
                                                                          Http2Flag.DataFlags.create(
                                                                                  Http2Flag.END_OF_STREAM),
                                                                          1),
                                                  BufferData.create(data));
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class),
                                                                  dataWriter,
                                                                  List.of(listener));

        int written = writer.writeHeaders(headers(),
                                          1,
                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                          frame,
                                          flowControl,
                                          () -> {
                                              assertThat(debits.get(), is(2));
                                              callbackCalled.set(true);
                                          });

        assertThat(written, is(actualBytes.get()));
        assertThat(callbackCalled.get(), is(true));
        assertThat(dataWriter.writes.size(), is(2));
        List<CapturedFrame> firstWrite = parseFrames(dataWriter.writes.getFirst());
        assertThat(firstWrite.getLast().header().type(), is(Http2FrameType.DATA));
        assertThat(firstWrite.getLast().header().length(), is(1));
        assertThat(firstWrite.getLast().header().flags(Http2FrameTypes.DATA).endOfStream(), is(false));
        assertThat(firstWrite.getLast().data(), is(new byte[] {1}));
        List<CapturedFrame> secondWrite = parseFrames(dataWriter.writes.getLast());
        assertThat(secondWrite.size(), is(1));
        assertThat(secondWrite.getFirst().header().type(), is(Http2FrameType.DATA));
        assertThat(secondWrite.getFirst().header().length(), is(1));
        assertThat(secondWrite.getFirst().header().flags(Http2FrameTypes.DATA).endOfStream(), is(true));
        assertThat(secondWrite.getFirst().data(), is(new byte[] {2}));
        verify(flowControl, times(2)).cut(any(Http2FrameData.class));
        verify(flowControl).blockTillUpdate();
        verify(flowControl, times(2)).decrementWindowSize(1);
    }

    @Test
    void combinedWriteReportsEveryDataFrameHeader() {
        AtomicBoolean callbackCalled = new AtomicBoolean();
        AtomicInteger actualBytes = new AtomicInteger();
        DataWriter dataWriter = mock(DataWriter.class);
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                actualBytes.addAndGet(Http2FrameHeader.LENGTH + header.length());
            }
        };
        FlowControl.Outbound flowControl = flowControl();
        byte[] data = new byte[32768];
        Http2FrameData frame = new Http2FrameData(Http2FrameHeader.create(data.length,
                                                                          Http2FrameTypes.DATA,
                                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                          1),
                                                  BufferData.create(data));
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of(listener));

        int written = writer.writeHeaders(headers(),
                                          1,
                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                          frame,
                                          flowControl,
                                          () -> callbackCalled.set(true));

        assertThat(written, is(actualBytes.get()));
        assertThat(callbackCalled.get(), is(true));
        verify(dataWriter, times(3)).writeNow(any(BufferData.class));
        verify(flowControl, times(2)).cut(any(Http2FrameData.class));
        verify(flowControl, times(2)).decrementWindowSize(16384);
    }

    @Test
    void largerDataUsesNegotiatedFrameSize() {
        int maxFrameSize = 65535;
        byte[] data = new byte[maxFrameSize];
        List<Integer> dataFrameLengths = new ArrayList<>();
        DataWriter dataWriter = mock(DataWriter.class);
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                if (header.type() == Http2FrameType.DATA) {
                    dataFrameLengths.add(header.length());
                }
            }
        };
        FlowControl.Outbound flowControl = flowControl();
        when(flowControl.maxFrameSize()).thenReturn(maxFrameSize);
        Http2FrameData frame = new Http2FrameData(Http2FrameHeader.create(data.length,
                                                                          Http2FrameTypes.DATA,
                                                                          Http2Flag.DataFlags.create(
                                                                                  Http2Flag.END_OF_STREAM),
                                                                          1),
                                                  BufferData.create(data));
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class),
                                                                  dataWriter,
                                                                  List.of(listener));

        writer.writeHeaders(headers(),
                            1,
                            Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                            frame,
                            flowControl);

        assertThat(dataFrameLengths, is(List.of(maxFrameSize)));
        verify(dataWriter, times(2)).writeNow(any(BufferData.class));
        verify(flowControl).cut(any(Http2FrameData.class));
        verify(flowControl).decrementWindowSize(maxFrameSize);
    }

    @Test
    void batchingRespectsEffectiveFrameSizeBoundaries() {
        assertTransportWriteCount(0, 16384, 1);
        assertTransportWriteCount(16384, 16384, 1);
        assertTransportWriteCount(16385, 16384, 3);
        assertTransportWriteCount(33, 32, 3);
    }

    @Test
    void writesSplitHeadersAndDataAsSeparateTransportWrites() {
        int maxFrameSize = 32;
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean flowControlDebited = new AtomicBoolean();
        AtomicBoolean callbackCalled = new AtomicBoolean();
        List<Http2FrameType> listenerFrameTypes = new ArrayList<>();
        RecordingDataWriter dataWriter = new RecordingDataWriter();
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                listenerFrameTypes.add(header.type());
            }
        };
        FlowControl.Outbound flowControl = flowControl();
        when(flowControl.maxFrameSize()).thenReturn(maxFrameSize);
        doAnswer(_ -> {
            flowControlDebited.set(true);
            return null;
        }).when(flowControl).decrementWindowSize(data.length);
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        writableHeaders.add(HeaderNames.create("x-large-header"),
                            "abcdefghijklmnopqrstuvwxyz0123456789".repeat(16));
        Http2Headers headers = Http2Headers.create(writableHeaders)
                .status(Status.OK_200);
        Http2FrameData dataFrame = new Http2FrameData(Http2FrameHeader.create(data.length,
                                                                               Http2FrameTypes.DATA,
                                                                               Http2Flag.DataFlags.create(
                                                                                       Http2Flag.END_OF_STREAM),
                                                                               1),
                                                       BufferData.create(data));
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class),
                                                                  dataWriter,
                                                                  List.of(listener));

        int written = writer.writeHeaders(headers,
                                          1,
                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                          dataFrame,
                                          flowControl,
                                          () -> {
                                              assertThat(flowControlDebited.get(), is(true));
                                              callbackCalled.set(true);
                                          });

        assertThat(callbackCalled.get(), is(true));
        assertThat(dataFrame.data().consumed(), is(true));
        byte[] wireBytes = combineWrites(dataWriter.writes);
        assertThat(written, is(wireBytes.length));
        List<CapturedFrame> frames = parseFrames(wireBytes);
        assertThat("Test setup must force continuation frames", frames.size() > 2, is(true));
        assertThat("Each fragmented header and DATA frame must use its own bounded transport write",
                   dataWriter.writes.size(),
                   is(frames.size()));
        assertThat(frames.getFirst().header().type(), is(Http2FrameType.HEADERS));
        assertThat(frames.getFirst().header().flags(Http2FrameTypes.HEADERS).endOfHeaders(), is(false));
        for (int i = 1; i < frames.size() - 2; i++) {
            CapturedFrame frame = frames.get(i);
            assertThat(frame.header().type(), is(Http2FrameType.CONTINUATION));
            assertThat(frame.header().flags(Http2FrameTypes.CONTINUATION).endOfHeaders(), is(false));
        }
        CapturedFrame finalHeaderFrame = frames.get(frames.size() - 2);
        assertThat(finalHeaderFrame.header().type(), is(Http2FrameType.CONTINUATION));
        assertThat(finalHeaderFrame.header().flags(Http2FrameTypes.CONTINUATION).endOfHeaders(), is(true));
        CapturedFrame finalDataFrame = frames.getLast();
        assertThat(finalDataFrame.header().type(), is(Http2FrameType.DATA));
        assertThat(finalDataFrame.header().flags(Http2FrameTypes.DATA).endOfStream(), is(true));
        assertThat(finalDataFrame.data(), is(data));
        assertThat(listenerFrameTypes,
                   is(frames.stream().map(frame -> frame.header().type()).toList()));
        assertThat(frames.stream().allMatch(frame -> frame.header().length() <= maxFrameSize), is(true));
        verify(flowControl).decrementWindowSize(data.length);
    }

    @Test
    void writesOversizedSingleFrameHeadersBeforeData() {
        int maxFrameSize = 32_768;
        byte[] data = {1};
        RecordingDataWriter dataWriter = new RecordingDataWriter();
        FlowControl.Outbound flowControl = flowControl();
        when(flowControl.maxFrameSize()).thenReturn(maxFrameSize);
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        writableHeaders.set(HeaderNames.create("x-large-header"), "~".repeat(18_000));
        Http2Headers headers = Http2Headers.create(writableHeaders)
                .status(Status.OK_200);
        Http2FrameData dataFrame = terminalDataFrame(data);
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());

        int written = writer.writeHeaders(headers,
                                          1,
                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                          dataFrame,
                                          flowControl);

        byte[] wireBytes = combineWrites(dataWriter.writes);
        List<CapturedFrame> frames = parseFrames(wireBytes);
        assertThat(written, is(wireBytes.length));
        assertThat(dataWriter.writes.size(), is(2));
        assertThat(frames.size(), is(2));
        assertThat(frames.getFirst().header().type(), is(Http2FrameType.HEADERS));
        assertThat(frames.getFirst().header().length(), greaterThan(16_384));
        assertThat(frames.getFirst().header().length(), lessThanOrEqualTo(maxFrameSize));
        assertThat(frames.getFirst().header().flags(Http2FrameTypes.HEADERS).endOfHeaders(), is(true));
        assertThat(frames.getLast().header().type(), is(Http2FrameType.DATA));
        assertThat(frames.getLast().data(), is(data));
        assertThat(dataFrame.data().consumed(), is(true));
        verify(flowControl).decrementWindowSize(data.length);
    }

    @Test
    void headerWriteFailureTerminatesWriter() {
        SocketWriterException writeFailure = new SocketWriterException();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            throw writeFailure;
        }).when(dataWriter).writeNow(any(BufferData.class));
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());

        SocketWriterException thrown = assertThrows(SocketWriterException.class,
                                                     () -> writer.writeHeaders(headers(),
                                                                               1,
                                                                               Http2Flag.HeaderFlags.create(
                                                                                       Http2Flag.END_OF_HEADERS),
                                                                               flowControl()));
        IllegalStateException terminal = assertThrows(IllegalStateException.class,
                                                       () -> writer.writeHeaders(headers(),
                                                                                 3,
                                                                                 Http2Flag.HeaderFlags.create(
                                                                                         Http2Flag.END_OF_HEADERS),
                                                                                 flowControl()));

        assertThat(thrown, is(writeFailure));
        assertThat(terminal.getCause(), is(writeFailure));
        verify(dataWriter).writeNow(any(BufferData.class));
        verify(dataWriter).close();
    }

    @Test
    void batchedWriteFailureDoesNotDebitOrComplete() {
        SocketWriterException writeFailure = new SocketWriterException();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            throw writeFailure;
        }).when(dataWriter).writeNow(any(BufferData.class));
        AtomicBoolean callbackCalled = new AtomicBoolean();
        FlowControl.Outbound flowControl = flowControl();
        Http2FrameData frame = new Http2FrameData(Http2FrameHeader.create(1,
                                                                          Http2FrameTypes.DATA,
                                                                          Http2Flag.DataFlags.create(
                                                                                  Http2Flag.END_OF_STREAM),
                                                                          1),
                                                  BufferData.create(new byte[] {1}));
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());

        SocketWriterException thrown = assertThrows(SocketWriterException.class,
                                                     () -> writer.writeHeaders(headers(),
                                                                               1,
                                                                               Http2Flag.HeaderFlags.create(
                                                                                       Http2Flag.END_OF_HEADERS),
                                                                               frame,
                                                                               flowControl,
                                                                               () -> callbackCalled.set(true)));

        assertThat(thrown, is(writeFailure));
        assertThat(callbackCalled.get(), is(false));
        verify(dataWriter).writeNow(any(BufferData.class));
        verify(dataWriter).close();
        verify(flowControl, times(0)).decrementWindowSize(1);
    }

    @Test
    void preservesBatchedWriteFailureWhenCloseThrowsSameFailure() {
        SocketWriterException writeFailure = new SocketWriterException();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            throw writeFailure;
        }).when(dataWriter).writeNow(any(BufferData.class));
        doAnswer(_ -> {
            throw writeFailure;
        }).when(dataWriter).close();
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());

        SocketWriterException thrown = assertThrows(SocketWriterException.class,
                                                     () -> writer.writeHeaders(headers(),
                                                                               1,
                                                                               Http2Flag.HeaderFlags.create(
                                                                                       Http2Flag.END_OF_HEADERS),
                                                                               terminalDataFrame(new byte[] {1}),
                                                                               flowControl()));

        assertThat(thrown, is(writeFailure));
        verify(dataWriter).close();
    }

    @Test
    void pendingWindowUpdateFailureClosesBatchedWriterOnce() {
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<Http2ConnectionWriter> writerRef = new AtomicReference<>();
        AtomicReference<Throwable> updateFailure = new AtomicReference<>();
        SocketWriterException writeFailure = new SocketWriterException();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            if (writes.incrementAndGet() == 2) {
                throw writeFailure;
            }
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                if (header.type() == Http2FrameType.HEADERS) {
                    Thread updateWriter = Thread.ofVirtual().start(() -> {
                        try {
                            Http2WindowUpdate windowUpdate = new Http2WindowUpdate(1);
                            writerRef.get().write(windowUpdate.toFrameData(null,
                                                                          streamId,
                                                                          Http2Flag.NoFlags.create()));
                        } catch (Throwable t) {
                            updateFailure.set(t);
                        }
                    });
                    try {
                        updateWriter.join(TimeUnit.SECONDS.toMillis(2));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted", e);
                    }
                    assertThat("WINDOW_UPDATE writer must terminate", updateWriter.isAlive(), is(false));
                }
            }
        };
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class),
                                                                  dataWriter,
                                                                  List.of(listener));
        writerRef.set(writer);

        SocketWriterException thrown = assertThrows(SocketWriterException.class,
                                                     () -> writer.writeHeaders(headers(),
                                                                               1,
                                                                               Http2Flag.HeaderFlags.create(
                                                                                       Http2Flag.END_OF_HEADERS),
                                                                               terminalDataFrame(new byte[] {1}),
                                                                               flowControl()));

        assertThat(thrown, is(writeFailure));
        assertThat(updateFailure.get(), is(nullValue()));
        verify(dataWriter, times(2)).writeNow(any(BufferData.class));
        verify(dataWriter, times(1)).close();
    }

    @Test
    void dataListenerFailureClosesBatchedWriter() {
        IllegalStateException listenerFailure = new IllegalStateException("listener failure");
        DataWriter dataWriter = mock(DataWriter.class);
        Http2FrameListener listener = new Http2FrameListener() {
            @Override
            public void frameHeader(SocketContext ctx, int streamId, Http2FrameHeader header) {
                if (header.type() == Http2FrameType.DATA) {
                    throw listenerFailure;
                }
            }
        };
        WritableHeaders<?> writableHeaders = WritableHeaders.create();
        writableHeaders.add(HeaderNames.create("x-indexable-header"), "indexable-value");
        Http2Headers headers = Http2Headers.create(writableHeaders)
                .status(Status.OK_200);
        FlowControl.Outbound flowControl = flowControl();
        AtomicInteger callbackCalls = new AtomicInteger();
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class),
                                                                  dataWriter,
                                                                  List.of(listener));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                     () -> writer.writeHeaders(headers,
                                                                               1,
                                                                               Http2Flag.HeaderFlags.create(
                                                                                       Http2Flag.END_OF_HEADERS),
                                                                               terminalDataFrame(new byte[] {1}),
                                                                               flowControl,
                                                                               callbackCalls::incrementAndGet));

        assertThat(thrown, is(listenerFailure));
        assertThat(callbackCalls.get(), is(0));
        verify(dataWriter, times(0)).writeNow(any(BufferData.class));
        verify(dataWriter).close();
        verify(flowControl, times(0)).decrementWindowSize(1);
    }

    @Test
    void dataWriteFailureAfterZeroWindowDoesNotDebitOrComplete() {
        SocketWriterException writeFailure = new SocketWriterException();
        AtomicInteger writes = new AtomicInteger();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            if (writes.incrementAndGet() == 2) {
                throw writeFailure;
            }
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        AtomicInteger cuts = new AtomicInteger();
        FlowControl.Outbound flowControl = flowControl();
        when(flowControl.cut(any(Http2FrameData.class))).thenAnswer(invocation -> cuts.getAndIncrement() == 0
                ? new Http2FrameData[0]
                : new Http2FrameData[] {invocation.getArgument(0)});
        AtomicInteger callbackCalls = new AtomicInteger();
        Http2FrameData frame = terminalDataFrame(new byte[] {1});
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());

        SocketWriterException thrown = assertThrows(SocketWriterException.class,
                                                     () -> writer.writeHeaders(headers(),
                                                                               1,
                                                                               Http2Flag.HeaderFlags.create(
                                                                                       Http2Flag.END_OF_HEADERS),
                                                                               frame,
                                                                               flowControl,
                                                                               callbackCalls::incrementAndGet));
        IllegalStateException terminal = assertThrows(IllegalStateException.class,
                                                       () -> writer.writeHeaders(headers(),
                                                                                 3,
                                                                                 Http2Flag.HeaderFlags.create(
                                                                                         Http2Flag.END_OF_HEADERS),
                                                                                 flowControl));

        assertThat(thrown, is(writeFailure));
        assertThat(terminal.getCause(), is(writeFailure));
        assertThat(callbackCalls.get(), is(0));
        verify(dataWriter, times(2)).writeNow(any(BufferData.class));
        verify(dataWriter).close();
        verify(flowControl, times(0)).decrementWindowSize(1);
    }

    @Test
    void finalDataWriteFailureAfterPartialWindowKeepsCompletedDebit() {
        SocketWriterException writeFailure = new SocketWriterException();
        AtomicInteger writes = new AtomicInteger();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            if (writes.incrementAndGet() == 2) {
                throw writeFailure;
            }
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        AtomicInteger cuts = new AtomicInteger();
        FlowControl.Outbound flowControl = flowControl();
        when(flowControl.cut(any(Http2FrameData.class))).thenAnswer(invocation -> {
            Http2FrameData frame = invocation.getArgument(0);
            return cuts.getAndIncrement() == 0 ? frame.cut(1) : new Http2FrameData[] {frame};
        });
        AtomicInteger callbackCalls = new AtomicInteger();
        Http2FrameData frame = terminalDataFrame(new byte[] {1, 2});
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());

        SocketWriterException thrown = assertThrows(SocketWriterException.class,
                                                     () -> writer.writeHeaders(headers(),
                                                                               1,
                                                                               Http2Flag.HeaderFlags.create(
                                                                                       Http2Flag.END_OF_HEADERS),
                                                                               frame,
                                                                               flowControl,
                                                                               callbackCalls::incrementAndGet));
        IllegalStateException terminal = assertThrows(IllegalStateException.class,
                                                       () -> writer.writeHeaders(headers(),
                                                                                 3,
                                                                                 Http2Flag.HeaderFlags.create(
                                                                                         Http2Flag.END_OF_HEADERS),
                                                                                 flowControl));

        assertThat(thrown, is(writeFailure));
        assertThat(terminal.getCause(), is(writeFailure));
        assertThat(callbackCalls.get(), is(0));
        verify(dataWriter, times(2)).writeNow(any(BufferData.class));
        verify(dataWriter).close();
        verify(flowControl).decrementWindowSize(1);
    }

    @Test
    void writeHeadersWithDataRejectsNullsBeforeWriting() {
        DataWriter dataWriter = mock(DataWriter.class);
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());
        Http2FrameData frame = new Http2FrameData(Http2FrameHeader.create(0,
                                                                          Http2FrameTypes.DATA,
                                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                          1),
                                                  BufferData.empty());

        assertThrows(NullPointerException.class,
                     () -> writer.writeHeaders(headers(),
                                               1,
                                               Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                               null,
                                               flowControl(),
                                               () -> { }));
        assertThrows(NullPointerException.class,
                     () -> writer.writeHeaders(headers(),
                                               1,
                                               Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                               frame,
                                               flowControl(),
                                               null));
        verify(dataWriter, times(0)).writeNow(any(BufferData.class));
    }

    @Test
    void endStreamCallbackRunsAfterDataIsWritten() {
        DataWriter dataWriter = mock(DataWriter.class);
        AtomicBoolean callbackCalled = new AtomicBoolean();
        AtomicBoolean writeReturned = new AtomicBoolean();
        AtomicBoolean flowControlDebited = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(callbackCalled.get(), is(false));
            writeReturned.set(true);
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);
        FlowControl.Outbound flowControl = mock(FlowControl.Outbound.class);
        when(flowControl.maxFrameSize()).thenReturn(16384);
        when(flowControl.cut(any(Http2FrameData.class))).thenAnswer(invocation -> new Http2FrameData[] {invocation.getArgument(0)});
        doAnswer(invocation -> {
            assertThat(callbackCalled.get(), is(false));
            flowControlDebited.set(true);
            return null;
        }).when(flowControl).decrementWindowSize(data.length);

        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());
        Http2FrameData frame = new Http2FrameData(Http2FrameHeader.create(data.length,
                                                                          Http2FrameTypes.DATA,
                                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                          1),
                                                  BufferData.create(data));

        int written = writer.writeData(frame, flowControl, () -> {
            assertThat(writeReturned.get(), is(true));
            assertThat(flowControlDebited.get(), is(true));
            callbackCalled.set(true);
        });

        assertThat(callbackCalled.get(), is(true));
        assertThat(written, is(data.length + Http2FrameHeader.LENGTH));
        verify(dataWriter).writeNow(any(BufferData.class));
        verify(flowControl).decrementWindowSize(data.length);
    }

    @Test
    void callbackAwareWriteDataRejectsNullArguments() {
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), mock(DataWriter.class), List.of());
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);
        Http2FrameData frame = new Http2FrameData(Http2FrameHeader.create(data.length,
                                                                          Http2FrameTypes.DATA,
                                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                                          1),
                                                  BufferData.create(data));

        assertThrows(NullPointerException.class, () -> writer.writeData(null, FlowControl.Outbound.NOOP, () -> { }));
        assertThrows(NullPointerException.class, () -> writer.writeData(frame, null, () -> { }));
        assertThrows(NullPointerException.class, () -> writer.writeData(frame, FlowControl.Outbound.NOOP, null));
    }

    private static Http2Headers headers() {
        return Http2Headers.create(WritableHeaders.create())
                .status(Status.OK_200);
    }

    private static FlowControl.Outbound flowControl() {
        FlowControl.Outbound flowControl = mock(FlowControl.Outbound.class);
        when(flowControl.maxFrameSize()).thenReturn(16384);
        when(flowControl.cut(any(Http2FrameData.class)))
                .thenAnswer(invocation -> new Http2FrameData[] {invocation.getArgument(0)});
        return flowControl;
    }

    private static FlowControl.Outbound trackingFlowControl(FlowControl.Outbound delegate,
                                                            AtomicInteger cuts,
                                                            CountDownLatch fourthCut,
                                                            CountDownLatch initialWait,
                                                            CountDownLatch resumedWait,
                                                            CountDownLatch thirdWait) {
        FlowControl.Outbound flowControl = mock(FlowControl.Outbound.class, delegatesTo(delegate));
        AtomicBoolean firstWait = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (cuts.incrementAndGet() == 4) {
                fourthCut.countDown();
            }
            return delegate.cut(invocation.getArgument(0));
        }).when(flowControl).cut(any(Http2FrameData.class));
        doAnswer(_ -> {
            boolean initial = firstWait.getAndSet(false);
            if (initial) {
                initialWait.countDown();
            } else {
                thirdWait.countDown();
            }
            delegate.blockTillUpdate();
            if (initial) {
                resumedWait.countDown();
            }
            return null;
        }).when(flowControl).blockTillUpdate();
        return flowControl;
    }

    private static Http2FrameData dataFrame(int streamId, int length) {
        return new Http2FrameData(Http2FrameHeader.create(length,
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(0),
                                                          streamId),
                                  BufferData.create(new byte[length]));
    }

    private static Http2FrameData terminalDataFrame(byte[] data) {
        return new Http2FrameData(Http2FrameHeader.create(data.length,
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                          1),
                                  BufferData.create(data));
    }

    private static void assertTransportWriteCount(int dataLength, int maxFrameSize, int expectedWrites) {
        RecordingDataWriter dataWriter = new RecordingDataWriter();
        FlowControl.Outbound flowControl = flowControl();
        when(flowControl.maxFrameSize()).thenReturn(maxFrameSize);
        byte[] data = new byte[dataLength];
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());

        writer.writeHeaders(headers(),
                            1,
                            Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                            terminalDataFrame(data),
                            flowControl);

        assertThat("unexpected transport write count for body length " + dataLength
                           + " and maximum frame size " + maxFrameSize,
                   dataWriter.writes.size(),
                   is(expectedWrites));
    }

    private static List<CapturedFrame> parseFrames(byte[] bytes) {
        BufferData buffer = BufferData.create(bytes);
        List<CapturedFrame> frames = new ArrayList<>();
        while (!buffer.consumed()) {
            Http2FrameHeader header = Http2FrameHeader.create(buffer);
            byte[] data = new byte[header.length()];
            buffer.read(data);
            frames.add(new CapturedFrame(header, data));
        }
        return frames;
    }

    private static byte[] combineWrites(List<byte[]> writes) {
        int length = writes.stream().mapToInt(it -> it.length).sum();
        BufferData buffer = BufferData.create(length);
        for (byte[] write : writes) {
            buffer.write(write);
        }
        return buffer.readBytes();
    }

    private static void writeData(Http2ConnectionWriter writer,
                                  Http2FrameData frame,
                                  FlowControl.Outbound flowControl,
                                  AtomicReference<Throwable> failure) {
        try {
            writer.writeData(frame, flowControl);
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
        }
    }

    private record CapturedFrame(Http2FrameHeader header, byte[] data) {
    }

    private static final class RecordingDataWriter implements DataWriter {
        private final List<byte[]> writes = new ArrayList<>();

        @Override
        public void write(BufferData... buffers) {
            writeNow(buffers);
        }

        @Override
        public void write(BufferData buffer) {
            writeNow(buffer);
        }

        @Override
        public void writeNow(BufferData... buffers) {
            writeNow(BufferData.create(buffers));
        }

        @Override
        public void writeNow(BufferData buffer) {
            writes.add(buffer.readBytes());
        }
    }
}
