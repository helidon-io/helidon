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

package io.helidon.webclient.websocket;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * In-memory receive path, including frame decoding and listener dispatch.
 * Each invocation receives a batch on a fresh connection; connection and EOF costs are amortized over the batch.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ClientWsReceiveJmhBenchmark {
    private static final int MESSAGES = 256;
    private static final String PAYLOAD = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final ReceivingListener listener = new ReceivingListener();
    private final MemoryConnection transport = new MemoryConnection();

    @Param({"TEXT", "BINARY"})
    public String messageType;

    @Param({"false", "true"})
    public boolean fragmented;

    private byte[] frames;
    private int expectedCallbacks;
    private String expectedPayload;

    @Setup
    public void setup() {
        int opCode = messageType.equals("TEXT") ? 1 : 2;
        byte[] payload = PAYLOAD.getBytes(StandardCharsets.UTF_8);
        var data = BufferData.growing(MESSAGES * (payload.length + 4));
        for (int i = 0; i < MESSAGES; i++) {
            if (fragmented) {
                data.write(opCode);
                data.write(payload.length / 2);
                data.write(payload, 0, payload.length / 2);
                data.write(0x80);
                data.write(payload.length / 2);
                data.write(payload, payload.length / 2, payload.length / 2);
            } else {
                data.write(0x80 | opCode);
                data.write(payload.length);
                data.write(payload);
            }
        }
        frames = data.readBytes();
        expectedCallbacks = MESSAGES * (fragmented ? 2 : 1);
        expectedPayload = fragmented ? PAYLOAD.substring(0, PAYLOAD.length() / 2) : PAYLOAD;
        receive();
    }

    @Benchmark
    @OperationsPerInvocation(MESSAGES)
    public int receive() {
        listener.callbacks = 0;
        listener.messages = 0;
        listener.bytes = 0;
        listener.failure = null;
        transport.inbound = frames;
        transport.reader = DataReader.create(transport);
        ClientWsConnection.create(transport, listener).run();
        if (listener.failure != null || listener.callbacks != expectedCallbacks
                || listener.messages != MESSAGES || listener.bytes != MESSAGES * PAYLOAD.length()) {
            throw new IllegalStateException("Receive failed: " + listener.callbacks + " callbacks, "
                                                    + listener.messages + " messages, " + listener.bytes + " bytes",
                                            listener.failure);
        }
        return listener.bytes;
    }

    private static final class MemoryConnection implements ClientConnection, HelidonSocket {
        private byte[] inbound;
        private DataReader reader;

        @Override
        public DataReader reader() {
            return reader;
        }

        @Override
        public DataWriter writer() {
            throw new IllegalStateException("Unexpected write on receive-only connection");
        }

        @Override
        public String channelId() {
            return "ws-receive-benchmark";
        }

        @Override
        public HelidonSocket helidonSocket() {
            return this;
        }

        @Override
        public void readTimeout(Duration readTimeout) {
        }

        @Override
        public void closeResource() {
        }

        @Override
        public byte[] get() {
            byte[] result = inbound;
            inbound = null;
            return result;
        }

        @Override
        public void close() {
        }

        @Override
        public void idle() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void write(BufferData buffer) {
            throw new IllegalStateException("Unexpected socket write");
        }

        @Override
        public PeerInfo remotePeer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PeerInfo localPeer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String socketId() {
            return channelId();
        }

        @Override
        public String childSocketId() {
            return channelId();
        }
    }

    private final class ReceivingListener implements WsListener {
        private int callbacks;
        private int messages;
        private int bytes;
        private Throwable failure;

        @Override
        public void onMessage(WsSession session, String text, boolean last) {
            if (!messageType.equals("TEXT")) {
                throw new IllegalStateException("Unexpected text callback");
            }
            check(text, last);
        }

        @Override
        public void onMessage(WsSession session, BufferData buffer, boolean last) {
            if (!messageType.equals("BINARY")) {
                throw new IllegalStateException("Unexpected binary callback");
            }
            check(buffer.readString(buffer.available(), StandardCharsets.UTF_8), last);
        }

        @Override
        public void onError(WsSession session, Throwable throwable) {
            failure = throwable;
        }

        private void check(String text, boolean last) {
            boolean expectedLast = !fragmented || callbacks % 2 == 1;
            if (!text.equals(expectedPayload) || last != expectedLast) {
                throw new IllegalStateException("Unexpected payload or FIN flag at callback " + callbacks);
            }
            callbacks++;
            messages += last ? 1 : 0;
            bytes += text.length();
        }
    }
}
