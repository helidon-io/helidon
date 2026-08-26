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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class Http2ConnectionWriterTest {

    @Test
    void headerTableSizeChangesWaitForAcknowledgmentAndSuccessfulWrite() throws InterruptedException {
        DataWriter dataWriter = mock(DataWriter.class);
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());

        writer.updateHeaderTableSize(4096);
        writer.updateHeaderTableSize(0);
        writer.updateHeaderTableSize(2048);
        writer.writeHeaders(requestHeaders(),
                            1,
                            Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                            FlowControl.Outbound.NOOP);

        BufferData beforeAcknowledgment = capturedHeaderBlock(dataWriter);
        byte[] beforeAcknowledgmentBytes = new byte[beforeAcknowledgment.available()];
        beforeAcknowledgment.read(beforeAcknowledgmentBytes);
        assertThat(beforeAcknowledgmentBytes[0] & 0xff, is(0x82));
        Http2Headers.DynamicTable peerTable =
                Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
        decodeHeaderBlock(BufferData.create(beforeAcknowledgmentBytes), peerTable, 1);

        clearInvocations(dataWriter);
        writer.write(settingsAckFrame());

        HeaderName retryHeaderName = HeaderNames.create("x-retry");
        WritableHeaders<?> retryHttpHeaders = WritableHeaders.create();
        retryHttpHeaders.set(retryHeaderName, "retry-value");
        Http2Headers retryHeaders = Http2Headers.create(retryHttpHeaders)
                .method(Method.GET)
                .scheme("https")
                .path("/")
                .authority("www.example.com");
        AtomicBoolean failWrite = new AtomicBoolean(true);
        doAnswer(ignored -> {
            if (failWrite.getAndSet(false)) {
                throw new UncheckedIOException(new IOException("expected test write failure"));
            }
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        assertThrows(UncheckedIOException.class,
                     () -> writer.writeHeaders(retryHeaders,
                                               3,
                                               Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                               FlowControl.Outbound.NOOP));

        clearInvocations(dataWriter);
        writer.writeHeaders(retryHeaders,
                            5,
                            Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                            FlowControl.Outbound.NOOP);

        BufferData firstHeaderBlock = capturedHeaderBlock(dataWriter);
        byte[] firstHeaderBlockBytes = new byte[firstHeaderBlock.available()];
        firstHeaderBlock.read(firstHeaderBlockBytes);
        BufferData headerTableUpdates = BufferData.create(firstHeaderBlockBytes);
        int firstUpdate = headerTableUpdates.read();
        assertThat(firstUpdate & 0b11100000, is(0b00100000));
        assertThat(headerTableUpdates.readHpackInt(firstUpdate, 5), is(0));
        int finalUpdate = headerTableUpdates.read();
        assertThat(finalUpdate & 0b11100000, is(0b00100000));
        assertThat(headerTableUpdates.readHpackInt(finalUpdate, 5), is(2048));
        Http2Headers decodedRetry = decodeHeaderBlock(BufferData.create(firstHeaderBlockBytes), peerTable, 5);
        assertThat(decodedRetry.httpHeaders().get(retryHeaderName).get(), is("retry-value"));

        clearInvocations(dataWriter);
        writer.writeHeaders(requestHeaders(),
                            7,
                            Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                            FlowControl.Outbound.NOOP);

        BufferData secondHeaderBlock = capturedHeaderBlock(dataWriter);
        assertThat(secondHeaderBlock.read(), is(0x82));
    }

    @Test
    void tryWriteWritesFrameWhenWriterIsAvailable() {
        DataWriter dataWriter = mock(DataWriter.class);
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());
        Http2GoAway goAway = new Http2GoAway(0, Http2ErrorCode.PROTOCOL, "");
        AtomicBoolean beforeWriteCalled = new AtomicBoolean();

        assertThat(writer.tryWrite(goAway.toFrameData(Http2Settings.create(), 0, Http2Flag.NoFlags.create()),
                                   () -> beforeWriteCalled.set(true)),
                   is(true));
        assertThat(beforeWriteCalled.get(), is(true));

        ArgumentCaptor<BufferData> frameCaptor = ArgumentCaptor.forClass(BufferData.class);
        verify(dataWriter).writeNow(frameCaptor.capture());
        BufferData frameData = frameCaptor.getValue().copy();
        Http2FrameHeader frameHeader = Http2FrameHeader.create(frameData);
        assertThat(frameHeader.type(), is(Http2FrameType.GO_AWAY));
        assertThat(frameHeader.streamId(), is(0));
        assertThat(Http2GoAway.create(frameData).errorCode(), is(Http2ErrorCode.PROTOCOL));
    }

    @Test
    void tryWriteDoesNotWaitForActiveWriter() throws InterruptedException {
        CountDownLatch dataWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseDataWrite = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(ignored -> {
            dataWriteStarted.countDown();
            releaseDataWrite.await();
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));
        Http2ConnectionWriter writer = new Http2ConnectionWriter(mock(SocketContext.class), dataWriter, List.of());
        Thread dataWriterThread = Thread.ofVirtual().start(() -> {
            try {
                writer.write(dataFrame(1, 1024));
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        assertThat("DATA write must start", dataWriteStarted.await(1, TimeUnit.SECONDS), is(true));
        AtomicBoolean beforeWriteCalled = new AtomicBoolean();
        AtomicReference<Boolean> tryWriteResult = new AtomicReference<>();
        Thread tryWriteThread = Thread.ofVirtual().start(() -> {
            Http2GoAway goAway = new Http2GoAway(0, Http2ErrorCode.PROTOCOL, "");
            tryWriteResult.set(writer.tryWrite(goAway.toFrameData(Http2Settings.create(),
                                                                 0,
                                                                 Http2Flag.NoFlags.create()),
                                                     () -> beforeWriteCalled.set(true)));
        });
        try {
            tryWriteThread.join(TimeUnit.SECONDS.toMillis(1));
            assertThat("GOAWAY attempt must terminate", tryWriteThread.isAlive(), is(false));
            assertThat(tryWriteResult.get(), is(false));
            assertThat(beforeWriteCalled.get(), is(false));
            verify(dataWriter).writeNow(any(BufferData.class));
        } finally {
            releaseDataWrite.countDown();
        }

        dataWriterThread.join(TimeUnit.SECONDS.toMillis(2));
        tryWriteThread.join(TimeUnit.SECONDS.toMillis(2));
        assertThat("DATA writer must terminate", dataWriterThread.isAlive(), is(false));
        assertThat("GOAWAY writer must terminate", tryWriteThread.isAlive(), is(false));
        assertThat(failure.get(), is(nullValue()));
    }

    private static BufferData capturedHeaderBlock(DataWriter dataWriter) {
        ArgumentCaptor<BufferData> frameCaptor = ArgumentCaptor.forClass(BufferData.class);
        verify(dataWriter).writeNow(frameCaptor.capture());
        BufferData frameData = frameCaptor.getValue().copy();
        Http2FrameHeader frameHeader = Http2FrameHeader.create(frameData);
        assertThat(frameHeader.type(), is(Http2FrameType.HEADERS));
        return frameData;
    }

    private static Http2Headers requestHeaders() {
        return Http2Headers.create(WritableHeaders.create())
                .method(Method.GET)
                .scheme("https")
                .path("/")
                .authority("www.example.com");
    }

    private static Http2Headers decodeHeaderBlock(BufferData data,
                                                  Http2Headers.DynamicTable table,
                                                  int streamId) {
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.HEADERS,
                                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                                          streamId);
        return Http2Headers.create(mock(Http2Stream.class),
                                   table,
                                   Http2HuffmanDecoder.create(),
                                   new Http2FrameData(header, data));
    }

    private static Http2FrameData settingsAckFrame() {
        Http2FrameHeader header = Http2FrameHeader.create(0,
                                                          Http2FrameTypes.SETTINGS,
                                                          Http2Flag.SettingsFlags.create(Http2Flag.ACK),
                                                          0);
        return new Http2FrameData(header, BufferData.empty());
    }

    private static Http2FrameData dataFrame(int streamId, int length) {
        return new Http2FrameData(Http2FrameHeader.create(length,
                                                           Http2FrameTypes.DATA,
                                                           Http2Flag.DataFlags.create(0),
                                                           streamId),
                                  BufferData.create(new byte[length]));
    }
}
