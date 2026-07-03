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
    void concurrentWritersDoNotReuseConnectionWindowCredit() throws InterruptedException {
        ConnectionFlowControl connection = ConnectionFlowControl.clientBuilder((_, _) -> { })
                .blockTimeout(Duration.ofSeconds(5))
                .build();
        connection.outbound().decrementWindowSize(connection.outbound().getRemainingWindowSize());

        AtomicInteger cuts = new AtomicInteger();
        CountDownLatch fourthCut = new CountDownLatch(1);
        CountDownLatch initialWait = new CountDownLatch(2);
        CountDownLatch thirdWait = new CountDownLatch(1);
        FlowControl.Outbound firstFlowControl = trackingFlowControl(connection.createStreamFlowControl(1, 1024, 16384)
                                                                           .outbound(),
                                                                   cuts,
                                                                   fourthCut,
                                                                   initialWait,
                                                                   thirdWait);
        FlowControl.Outbound secondFlowControl = trackingFlowControl(connection.createStreamFlowControl(3, 1024, 16384)
                                                                            .outbound(),
                                                                    cuts,
                                                                    fourthCut,
                                                                    initialWait,
                                                                    thirdWait);

        AtomicInteger writes = new AtomicInteger();
        CountDownLatch firstWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(_ -> {
            if (writes.incrementAndGet() == 1) {
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
            // Give a non-atomic implementation time to cut both frames before the first write completes.
            var _ = fourthCut.await(1, TimeUnit.SECONDS);
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
                                                            CountDownLatch thirdWait) {
        FlowControl.Outbound flowControl = mock(FlowControl.Outbound.class, delegatesTo(delegate));
        doAnswer(invocation -> {
            if (cuts.incrementAndGet() == 4) {
                fourthCut.countDown();
            }
            return delegate.cut(invocation.getArgument(0));
        }).when(flowControl).cut(any(Http2FrameData.class));
        doAnswer(_ -> {
            if (cuts.get() >= 4) {
                thirdWait.countDown();
            } else {
                initialWait.countDown();
            }
            delegate.blockTillUpdate();
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
