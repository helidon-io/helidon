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

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.HttpTransportObserverSupport.ConnectionObservationContext;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientWsTransportObservationTest {
    private static final byte[] REMOTE_CLOSE = {(byte) 0x88, 2, 3, (byte) 0xe8};

    @Test
    void remoteCloseRemainsRemoteWhenListenerAnswers() {
        var connection = new TestConnection(REMOTE_CLOSE);

        run(connection, new WsListener() {
            @Override
            public void onClose(WsSession session, int status, String reason) {
                session.close(status, reason);
            }
        });

        assertThat(connection.outcomes, contains(ConnectionOutcome.REMOTE_CLOSE));
        assertThat(connection.writes.size(), is(1));
        assertThat(connection.closed, is(1));
    }

    @Test
    void remoteAcknowledgementPreservesLocalClose() {
        var connection = new TestConnection(REMOTE_CLOSE);

        run(connection, new WsListener() {
            @Override
            public void onOpen(WsSession session) {
                session.close(WsCloseCodes.NORMAL_CLOSE, "done");
            }
        });

        assertThat(connection.outcomes, contains(ConnectionOutcome.LOCAL_CLOSE));
        assertThat(connection.writes.size(), is(1));
        assertThat(connection.closed, is(1));
    }

    @Test
    void remoteEofClosesPhysicalConnection() {
        var connection = new TestConnection(new byte[0]);

        run(connection, new WsListener() { });

        assertThat(connection.outcomes, contains(ConnectionOutcome.REMOTE_CLOSE));
        assertThat(connection.closed, is(1));
    }

    @Test
    void eofAfterCompleteFrameRemainsRemoteClose() {
        var connection = new TestConnection(new byte[] {(byte) 0x82, 1, 1});
        var messages = new ArrayList<BufferData>();

        run(connection, new WsListener() {
            @Override
            public void onMessage(WsSession session, BufferData buffer, boolean last) {
                messages.add(buffer);
            }
        });

        assertThat(messages.size(), is(1));
        assertThat(messages.getFirst().read(), is(1));
        assertThat(connection.outcomes, contains(ConnectionOutcome.REMOTE_CLOSE));
        assertThat(connection.closed, is(1));
    }

    @Test
    void truncatedFrameHeaderIsReportedAsError() {
        assertTruncatedFrame(new byte[] {(byte) 0x82});
    }

    @Test
    void truncatedShortFrameLengthIsReportedAsError() {
        assertTruncatedFrame(new byte[] {(byte) 0x82, 126, 0});
    }

    @Test
    void truncatedLongFrameLengthIsReportedAsError() {
        assertTruncatedFrame(new byte[] {(byte) 0x82, 127, 0, 0, 0, 0, 0, 0, 0});
    }

    @Test
    void truncatedFramePayloadIsReportedAsError() {
        assertTruncatedFrame(new byte[] {(byte) 0x82, 4, 1});
    }

    @Test
    void listenerFailureIsReportedBeforeSendingClose() {
        var connection = new TestConnection(new byte[0]);
        var failure = new IllegalStateException("listener failed");
        var reportedFailure = new AtomicReference<Throwable>();

        run(connection, new WsListener() {
            @Override
            public void onOpen(WsSession session) {
                throw failure;
            }

            @Override
            public void onError(WsSession session, Throwable t) {
                reportedFailure.set(t);
            }
        });

        assertThat(connection.outcomes, contains(ConnectionOutcome.ERROR, ConnectionOutcome.LOCAL_CLOSE));
        assertThat(reportedFailure.get(), sameInstance(failure));
        assertThat(connection.closed, is(1));
    }

    @Test
    void wrappedReadTimeoutIsReportedAsTimeout() {
        var connection = new TestConnection(() -> {
            throw new IllegalStateException("read failed", new SocketTimeoutException("timed out"));
        });

        run(connection, new WsListener() { });

        assertThat(connection.outcomes, contains(ConnectionOutcome.TIMEOUT, ConnectionOutcome.LOCAL_CLOSE));
        assertThat(connection.closed, is(1));
    }

    @Test
    void errorPropagatesAfterPhysicalCleanup() {
        var connection = new TestConnection(new byte[0]);
        var failure = new AssertionError("listener error");

        var thrown = assertThrows(AssertionError.class, () -> run(connection, new WsListener() {
            @Override
            public void onOpen(WsSession session) {
                throw failure;
            }
        }));

        assertThat(thrown, sameInstance(failure));
        assertThat(connection.outcomes, empty());
        assertThat(connection.writes, empty());
        assertThat(connection.closed, is(1));
    }

    @Test
    void applicationThreadWriteFailureIsReported() {
        var connection = new TestConnection(new byte[0]);
        var failure = new IllegalStateException("write failed");
        connection.writeFailure = failure;
        var session = ClientWsConnection.create(connection, new WsListener() { });

        var thrown = assertThrows(IllegalStateException.class, () -> session.send("hello", true));

        assertThat(thrown, sameInstance(failure));
        assertThat(connection.outcomes, contains(ConnectionOutcome.ERROR));
        assertThat("physical close remains owned by the receive loop", connection.closed, is(0));
    }

    @Test
    void messagesRetainHttpUpgradeObservation() {
        var connection = new TestConnection(new byte[] {(byte) 0x81, 2, 'o', 'k', (byte) 0x88, 2, 3, (byte) 0xe8});
        var messages = new ArrayList<String>();

        run(connection, new WsListener() {
            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                messages.add(text);
                session.send(text, last);
            }
        });

        assertThat(messages, contains("ok"));
        assertThat(connection.writes.size(), is(2));
        assertThat("WebSocket messages must not publish HTTP lifecycle events", connection.lifecycleEvents, empty());
        assertThat(connection.outcomes, contains(ConnectionOutcome.REMOTE_CLOSE));
        assertThat(connection.closed, is(1));
    }

    private static void assertTruncatedFrame(byte[] bytes) {
        var connection = new TestConnection(bytes);
        var messages = new ArrayList<BufferData>();

        run(connection, new WsListener() {
            @Override
            public void onMessage(WsSession session, BufferData buffer, boolean last) {
                messages.add(buffer);
            }
        });

        assertThat("A truncated frame must not deliver a message", messages, empty());
        assertThat(connection.outcomes, contains(ConnectionOutcome.ERROR));
        assertThat(connection.writes, empty());
        assertThat(connection.closed, is(1));
    }

    private static void run(TestConnection connection, WsListener listener) {
        String threadName = Thread.currentThread().getName();
        try {
            ClientWsConnection.create(connection, listener).run();
        } finally {
            Thread.currentThread().setName(threadName);
        }
    }

    private static final class TestConnection
            implements ClientConnection, ConnectionObservationContext, ConnectionObservation, HelidonSocket, DataWriter {
        private final List<ConnectionOutcome> outcomes = new ArrayList<>();
        private final List<byte[]> writes = new ArrayList<>();
        private final List<String> lifecycleEvents = new ArrayList<>();
        private final DataReader reader;
        private int closed;
        private RuntimeException writeFailure;

        private TestConnection(byte[] bytes) {
            var remaining = new AtomicReference<>(bytes);
            reader = DataReader.create(() -> remaining.getAndSet(null));
        }

        private TestConnection(Supplier<byte[]> bytes) {
            reader = DataReader.create(bytes);
        }

        @Override
        public DataReader reader() {
            return reader;
        }

        @Override
        public DataWriter writer() {
            return this;
        }

        @Override
        public String channelId() {
            return "test";
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
            closed++;
        }

        @Override
        public void httpTransportObserver(HttpTransportObserver observer) {
        }

        @Override
        public ConnectionObservation httpTransportObservation() {
            return this;
        }

        @Override
        public void httpTransportOutcome(ConnectionOutcome outcome) {
            assertThat("outcome must be recorded before physical closure", closed, is(0));
            outcomes.add(outcome);
        }

        @Override
        public HandshakeObservation handshakeStarted() {
            lifecycleEvents.add("handshake");
            return HandshakeObservation.noop();
        }

        @Override
        public void protocolSelected(String protocol) {
            lifecycleEvents.add("protocol:" + protocol);
        }

        @Override
        public StreamObservation streamOpened(Direction direction, Initiator initiator) {
            lifecycleEvents.add("stream");
            return StreamObservation.noop();
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            lifecycleEvents.add("connection:" + outcome);
        }

        @Override
        public void close() {
        }

        @Override
        public void idle() {
        }

        @Override
        public boolean isConnected() {
            return closed == 0;
        }

        @Override
        public void write(BufferData... buffers) {
            for (BufferData buffer : buffers) {
                writeNow(buffer);
            }
        }

        @Override
        public void write(BufferData buffer) {
            writeNow(buffer);
        }

        @Override
        public void writeNow(BufferData... buffers) {
            write(buffers);
        }

        @Override
        public void writeNow(BufferData buffer) {
            if (writeFailure != null) {
                throw writeFailure;
            }
            writes.add(buffer.readBytes());
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
            return "test";
        }

        @Override
        public String childSocketId() {
            return "test";
        }

        @Override
        public byte[] get() {
            return new byte[0];
        }
    }
}
