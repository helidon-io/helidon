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
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
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

        int written = writer.writeHeaders(headers(),
                                          1,
                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                          frame,
                                          flowControl,
                                          () -> {
                                              assertThat(writes.get(), is(2));
                                              assertThat(flowControlDebited.get(), is(true));
                                              callbackCalled.set(true);
                                          });

        assertThat(callbackCalled.get(), is(true));
        assertThat(written, greaterThan(data.length + Http2FrameHeader.LENGTH));
        verify(dataWriter, times(2)).writeNow(any(BufferData.class));
        verify(flowControl).decrementWindowSize(data.length);
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
}
