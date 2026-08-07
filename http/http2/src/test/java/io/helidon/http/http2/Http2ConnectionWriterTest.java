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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class Http2ConnectionWriterTest {

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

    private static Http2FrameData dataFrame(int streamId, int length) {
        return new Http2FrameData(Http2FrameHeader.create(length,
                                                           Http2FrameTypes.DATA,
                                                           Http2Flag.DataFlags.create(0),
                                                           streamId),
                                  BufferData.create(new byte[length]));
    }
}
