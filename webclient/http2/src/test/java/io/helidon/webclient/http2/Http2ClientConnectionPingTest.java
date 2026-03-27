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

package io.helidon.webclient.http2;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webclient.api.ClientConnection;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2ClientConnectionPingTest {
    private static final Http2StreamConfig STREAM_CONFIG = new Http2StreamConfig() {
        @Override
        public boolean priorKnowledge() {
            return true;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Duration readTimeout() {
            return Duration.ofSeconds(1);
        }
    };

    @Test
    void streamSendPingWritesFullFrameEachTime() {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        Http2ClientStream stream = test.newStream();

        stream.sendPing();
        stream.sendPing();

        assertThat(test.writes(), hasSize(2));
        assertThat(test.writes().get(0).available(), is(17));
        assertThat(test.writes().get(1).available(), is(17));
        assertThat(readPingPayloadId(test.writes().get(0)), is(0L));
        assertThat(readPingPayloadId(test.writes().get(1)), is(0L));
    }

    @Test
    void pingWaitsForMatchingAck() throws Exception {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofSeconds(1));
        CompletableFuture<Long> payloadFuture = new CompletableFuture<>();

        Thread.ofPlatform().start(() -> {
            try {
                long pingPayload = readPingPayloadId(test.awaitWrite());
                payloadFuture.complete(pingPayload);
                test.connection().pong(pingPayload + 1);
                Thread.sleep(50);
                test.connection().pong(pingPayload);
            } catch (Throwable t) {
                payloadFuture.completeExceptionally(t);
            }
        });

        boolean pingResult = test.connection().ping();
        long pingPayload = payloadFuture.get(1, TimeUnit.SECONDS);
        assertThat(pingPayload, is(1L));
        assertThat(pingResult, is(true));
    }

    @Test
    void pingTimesOutWhenOnlyUnmatchedAckArrives() throws Exception {
        MockedConnectionTestContext test = new MockedConnectionTestContext(Duration.ofMillis(100));
        CompletableFuture<Long> payloadFuture = new CompletableFuture<>();

        Thread.ofPlatform().start(() -> {
            try {
                long pingPayload = readPingPayloadId(test.awaitWrite());
                payloadFuture.complete(pingPayload);
                test.connection().pong(pingPayload + 1);
            } catch (Throwable t) {
                payloadFuture.completeExceptionally(t);
            }
        });

        boolean pingResult = test.connection().ping();
        long pingPayload = payloadFuture.get(1, TimeUnit.SECONDS);
        assertThat(pingPayload, is(1L));
        assertThat(pingResult, is(false));
    }

    private static long readPingPayloadId(BufferData frame) {
        frame.rewind();
        // Captured writes contain the serialized frame header followed by the 8-byte PING payload.
        frame.skip(Http2FrameHeader.LENGTH);
        long payload = frame.readLong();
        frame.rewind();
        return payload;
    }

    private static final class MockedConnectionTestContext {
        private final HelidonSocket socket;
        private final Http2ClientConfig clientConfig;
        private final Http2ClientConnection connection;
        private final CaptureWriter dataWriter = new CaptureWriter();

        private MockedConnectionTestContext(Duration pingTimeout) {
            Http2ClientProtocolConfig protocolConfig = Http2ClientProtocolConfig.builder()
                    .ping(true)
                    .pingTimeout(pingTimeout)
                    .build();

            this.clientConfig = Http2ClientConfig.builder()
                    .protocolConfig(protocolConfig)
                    .buildPrototype();

            Http2ClientImpl client = mock(Http2ClientImpl.class);
            ClientConnection clientConnection = mock(ClientConnection.class);
            this.socket = mock(HelidonSocket.class);

            when(client.protocolConfig()).thenReturn(protocolConfig);
            when(client.clientConfig()).thenReturn(clientConfig);
            when(clientConnection.reader()).thenReturn(DataReader.create(() -> null));
            when(clientConnection.writer()).thenReturn(dataWriter);
            when(clientConnection.helidonSocket()).thenReturn(socket);
            when(socket.socketId()).thenReturn("test-socket");
            when(socket.childSocketId()).thenReturn("0");

            this.connection = new Http2ClientConnection(client, clientConnection);
        }

        private Http2ClientConnection connection() {
            return connection;
        }

        private List<BufferData> writes() {
            return dataWriter.writes;
        }

        private BufferData awaitWrite() throws InterruptedException {
            BufferData write = dataWriter.asyncWrites.poll(5, TimeUnit.SECONDS);
            if (write == null) {
                fail("Timed out waiting for ping write");
            }
            return write;
        }

        private Http2ClientStream newStream() {
            return new Http2ClientStream(connection,
                                         Http2Settings.create(),
                                         socket,
                                         STREAM_CONFIG,
                                         clientConfig,
                                         new LockingStreamIdSequence());
        }
    }

    private static final class CaptureWriter implements DataWriter {
        private final List<BufferData> writes = new CopyOnWriteArrayList<>();
        private final LinkedBlockingQueue<BufferData> asyncWrites = new LinkedBlockingQueue<>();

        @Override
        public void write(BufferData... buffers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(BufferData buffer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeNow(BufferData... buffers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeNow(BufferData buffer) {
            BufferData snapshot = buffer.copy();
            writes.add(snapshot);
            asyncWrites.add(snapshot);
        }
    }
}
