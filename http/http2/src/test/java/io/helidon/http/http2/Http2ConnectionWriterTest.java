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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2ConnectionWriterTest {

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
}
