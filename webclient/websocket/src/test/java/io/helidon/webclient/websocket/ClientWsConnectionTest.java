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

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Size;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsCloseException;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsListenerBase;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class ClientWsConnectionTest {
    private static final long TEST_TIMEOUT_SECONDS = 5;

    @Test
    void clientProtocolConfigHasBufferedMessageSizeDefaultAndOverride() {
        WsClientProtocolConfig defaults = WsClientProtocolConfig.create();
        WsClientProtocolConfig configured = WsClientProtocolConfig.builder()
                .maxBufferedMessageSize(Size.create(16, Size.Unit.BYTE))
                .build();
        Config config = Config.create(ConfigSources.create(Map.of("max-buffered-message-size", "2 MiB")));
        WsClientProtocolConfig mapped = new WsProtocolConfigProvider().create(config, "test");

        assertThat(defaults.maxBufferedMessageSize().toBytes(), is(Size.create(1, Size.Unit.MIB).toBytes()));
        assertThat(configured.maxBufferedMessageSize().toBytes(), is(16L));
        assertThat(mapped.maxBufferedMessageSize().toBytes(), is(Size.create(2, Size.Unit.MIB).toBytes()));
    }

    @Test
    void clientProtocolConfigHasFrameLengthDefaultAndOverride() {
        WsClientProtocolConfig defaults = WsClientProtocolConfig.create();
        WsClientProtocolConfig configured = WsClientProtocolConfig.builder()
                .maxFrameLength(16)
                .build();
        Config config = Config.create(ConfigSources.create(Map.of("max-frame-length", "32")));
        WsClientProtocolConfig mapped = WsClientProtocolConfig.create(config);

        assertAll(
                () -> assertThat(defaults.maxFrameLength(), is(1024 * 1024)),
                () -> assertThat(configured.maxFrameLength(), is(16)),
                () -> assertThat(mapped.maxFrameLength(), is(32))
        );
    }

    @Test
    void clientProtocolConfigRejectsNonPositiveFrameLength() {
        assertThrows(IllegalArgumentException.class,
                     () -> WsClientProtocolConfig.builder().maxFrameLength(0).build());
        assertThrows(IllegalArgumentException.class,
                     () -> WsClientProtocolConfig.builder().maxFrameLength(-1).build());
        assertThrows(IllegalArgumentException.class,
                     () -> WsClientProtocolConfig.create(
                             Config.create(ConfigSources.create(Map.of("max-frame-length", "0")))));
        assertThrows(IllegalArgumentException.class,
                     () -> WsClientProtocolConfig.create(
                             Config.create(ConfigSources.create(Map.of("max-frame-length", "-1")))));
    }

    @Test
    void publicFactoriesApplyDefaultFrameLimit() {
        CapturingDataWriter withoutSubProtocolWriter = new CapturingDataWriter();
        CapturingDataWriter withSubProtocolWriter = new CapturingDataWriter();
        WsListener listener = new WsListener() {
        };
        ClientWsConnection withoutSubProtocol = ClientWsConnection.create(
                new TestClientConnection(largeFrameHeaderReader(), withoutSubProtocolWriter),
                listener);
        ClientWsConnection withSubProtocol = ClientWsConnection.create(
                new TestClientConnection(largeFrameHeaderReader(), withSubProtocolWriter),
                listener,
                "test");

        withoutSubProtocol.run();
        withSubProtocol.run();

        byte[] withoutSubProtocolCloseFrame = withoutSubProtocolWriter.data.get();
        byte[] withSubProtocolCloseFrame = withSubProtocolWriter.data.get();
        assertThat(withoutSubProtocolCloseFrame, notNullValue());
        assertThat(withSubProtocolCloseFrame, notNullValue());
        assertAll(
                () -> assertThat(withoutSubProtocol.protocolConfig().maxFrameLength(), is(1024 * 1024)),
                () -> assertThat(withSubProtocol.protocolConfig().maxFrameLength(), is(1024 * 1024)),
                () -> assertThat(maskedCloseCode(withoutSubProtocolCloseFrame), is(WsCloseCodes.TOO_BIG)),
                () -> assertThat(maskedCloseCode(withSubProtocolCloseFrame), is(WsCloseCodes.TOO_BIG))
        );
    }

    @Test
    void acceptsFrameAtClientLimit() {
        byte[] expected = "1234567890123456".getBytes(StandardCharsets.UTF_8);
        BufferData inboundFrameData = BufferData.growing(2 + expected.length);
        inboundFrameData.write(0x82);
        inboundFrameData.write(expected.length);
        inboundFrameData.write(expected);
        AtomicReference<byte[]> inbound = new AtomicReference<>(inboundFrameData.readBytes());
        AtomicReference<byte[]> received = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CapturingDataWriter dataWriter = new CapturingDataWriter();
        WsListener listener = new WsListener() {
            @Override
            public void onMessage(WsSession session, BufferData buffer, boolean last) {
                received.set(buffer.readBytes());
            }

            @Override
            public void onError(WsSession session, Throwable throwable) {
                error.set(throwable);
            }
        };
        WsClientProtocolConfig protocolConfig = WsClientProtocolConfig.builder()
                .maxFrameLength(expected.length)
                .build();
        ClientWsConnection connection = new ClientWsConnection(
                new TestClientConnection(DataReader.create(() -> inbound.getAndSet(null)), dataWriter),
                listener,
                null,
                protocolConfig);

        connection.run();

        assertAll(
                () -> assertThat(received.get(), is(expected)),
                () -> assertThat(error.get(), is(nullValue())),
                () -> assertThat(dataWriter.data.get(), is(nullValue()))
        );
    }

    @Test
    void closesWhenFrameExceedsClientLimit() {
        CapturingDataWriter dataWriter = new CapturingDataWriter();
        AtomicInteger messageInvocations = new AtomicInteger();
        AtomicInteger closeInvocations = new AtomicInteger();
        AtomicReference<Integer> closeCode = new AtomicReference<>();
        AtomicReference<String> closeReason = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        WsListener listener = new WsListener() {
            @Override
            public void onMessage(WsSession session, BufferData buffer, boolean last) {
                messageInvocations.incrementAndGet();
            }

            @Override
            public void onClose(WsSession session, int status, String reason) {
                closeInvocations.incrementAndGet();
                closeCode.set(status);
                closeReason.set(reason);
            }

            @Override
            public void onError(WsSession session, Throwable throwable) {
                error.set(throwable);
            }
        };
        WsClientProtocolConfig protocolConfig = WsClientProtocolConfig.builder()
                .maxFrameLength(16)
                .build();
        ClientWsConnection connection = new ClientWsConnection(new TestClientConnection(oversizedFrameReader(), dataWriter),
                                                               listener,
                                                               null,
                                                               protocolConfig);

        connection.run();

        byte[] closeFrame = dataWriter.data.get();
        assertThat(closeFrame, notNullValue());
        assertAll(
                () -> assertThat(messageInvocations.get(), is(0)),
                () -> assertThat(closeInvocations.get(), is(1)),
                () -> assertThat(closeCode.get(), is(WsCloseCodes.TOO_BIG)),
                () -> assertThat(closeReason.get(), is("Payload too large")),
                () -> assertThat(error.get(), is(nullValue())),
                () -> assertThat(closeFrame[0] & 0x0F, is(0x08)),
                () -> assertThat(maskedCloseCode(closeFrame), is(WsCloseCodes.TOO_BIG))
        );
    }

    @Test
    void oversizedFrameNotifiesCloseBeforeWaitingForSendLock() throws Exception {
        BlockingDataWriter dataWriter = new BlockingDataWriter();
        CountDownLatch closeNotified = new CountDownLatch(1);
        AtomicReference<Throwable> listenerFailure = new AtomicReference<>();
        WsListener listener = new WsListener() {
            @Override
            public void onClose(WsSession session, int status, String reason) {
                session.close(WsCloseCodes.NORMAL_CLOSE, "handled");
                closeNotified.countDown();
            }

            @Override
            public void onError(WsSession session, Throwable throwable) {
                listenerFailure.set(throwable);
            }
        };
        WsClientProtocolConfig protocolConfig = WsClientProtocolConfig.builder()
                .maxFrameLength(16)
                .build();
        ClientWsConnection connection = new ClientWsConnection(new TestClientConnection(oversizedFrameReader(), dataWriter),
                                                               listener,
                                                               null,
                                                               protocolConfig);

        AtomicReference<Throwable> sendFailure = new AtomicReference<>();
        Thread sendThread = new Thread(() -> invoke(() -> connection.send("hello", true), sendFailure), "client-ws-send");
        sendThread.start();
        dataWriter.awaitFirstWrite();

        AtomicReference<Throwable> runFailure = new AtomicReference<>();
        Thread runThread = new Thread(() -> invoke(connection::run, runFailure), "client-ws-run");
        runThread.start();

        boolean notifiedBeforeRelease;
        try {
            notifiedBeforeRelease = closeNotified.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            dataWriter.releaseFirstWrite();
            sendThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));
            runThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));
        }

        assertAll(
                () -> assertThat(notifiedBeforeRelease, is(true)),
                () -> assertThat(listenerFailure.get(), is(nullValue())),
                () -> assertThat(sendFailure.get(), is(nullValue())),
                () -> assertThat(runFailure.get(), is(nullValue())),
                () -> assertThat(sendThread.isAlive(), is(false)),
                () -> assertThat(runThread.isAlive(), is(false))
        );
    }

    @Test
    void closesWhenBufferedMessageExceedsClientLimit() {
        DataReader dataReader = oversizedMessageReader();
        CapturingDataWriter dataWriter = new CapturingDataWriter();
        AtomicInteger messageInvocations = new AtomicInteger();
        AtomicInteger closeInvocations = new AtomicInteger();
        AtomicReference<Integer> closeCode = new AtomicReference<>();
        AtomicReference<String> closeReason = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        IllegalStateException closeFailure = new IllegalStateException("Close callback failed");
        WsListener listener = new WsListenerBase() {
            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                textString(session, text, last, ignored -> messageInvocations.incrementAndGet());
            }

            @Override
            public void onClose(WsSession session, int status, String reason) {
                closeInvocations.incrementAndGet();
                closeCode.set(status);
                closeReason.set(reason);
                session.close(WsCloseCodes.NORMAL_CLOSE, "handled");
                throw closeFailure;
            }

            @Override
            public void onError(WsSession session, Throwable throwable) {
                error.set(throwable);
            }
        };
        WsClientProtocolConfig protocolConfig = WsClientProtocolConfig.builder()
                .maxBufferedMessageSize(Size.create(16, Size.Unit.BYTE))
                .build();
        ClientWsConnection connection = new ClientWsConnection(new TestClientConnection(dataReader, dataWriter),
                                                               listener,
                                                               null,
                                                               protocolConfig);

        connection.run();

        byte[] closeFrame = dataWriter.data.get();
        assertAll(
                () -> assertSame(protocolConfig, connection.protocolConfig()),
                () -> assertThat(messageInvocations.get(), is(0)),
                () -> assertThat(closeInvocations.get(), is(1)),
                () -> assertThat(closeCode.get(), is(WsCloseCodes.TOO_BIG)),
                () -> assertThat(closeReason.get(), is("Message too large")),
                () -> assertSame(closeFailure, error.get()),
                () -> assertThat(closeFrame[0] & 0x0F, is(0x08)),
                () -> assertThat(maskedCloseCode(closeFrame), is(WsCloseCodes.TOO_BIG))
        );
    }

    @Test
    void peerCloseCallbackFailureIsNotReportedAsSecondClose() {
        BufferData inboundFrameData = BufferData.growing(4);
        inboundFrameData.write(0x88);
        inboundFrameData.write(2);
        inboundFrameData.writeInt16(WsCloseCodes.NORMAL_CLOSE);
        AtomicReference<byte[]> inbound = new AtomicReference<>(inboundFrameData.readBytes());
        CapturingDataWriter dataWriter = new CapturingDataWriter();
        AtomicInteger closeInvocations = new AtomicInteger();
        AtomicReference<Integer> closeCode = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        WsCloseException closeFailure = new WsCloseException("Close callback failed", WsCloseCodes.VIOLATED_POLICY);
        WsListener listener = new WsListener() {
            @Override
            public void onClose(WsSession session, int status, String reason) {
                closeInvocations.incrementAndGet();
                closeCode.set(status);
                throw closeFailure;
            }

            @Override
            public void onError(WsSession session, Throwable throwable) {
                error.set(throwable);
            }
        };
        ClientWsConnection connection = ClientWsConnection.create(
                new TestClientConnection(DataReader.create(() -> inbound.getAndSet(null)), dataWriter),
                listener);

        connection.run();

        byte[] closeFrame = dataWriter.data.get();
        assertAll(
                () -> assertThat(closeInvocations.get(), is(1)),
                () -> assertThat(closeCode.get(), is(WsCloseCodes.NORMAL_CLOSE)),
                () -> assertThat(error.get(), is(nullValue())),
                () -> assertThat(closeFrame[0] & 0x0F, is(0x08)),
                () -> assertThat(maskedCloseCode(closeFrame), is(WsCloseCodes.VIOLATED_POLICY))
        );
    }

    @Test
    void oversizedMessageNotifiesCloseBeforeWaitingForSendLock() throws Exception {
        BlockingDataWriter dataWriter = new BlockingDataWriter();
        CountDownLatch closeNotified = new CountDownLatch(1);
        AtomicReference<Throwable> listenerFailure = new AtomicReference<>();
        WsListener listener = new WsListenerBase() {
            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                textString(session, text, last, ignored -> { });
            }

            @Override
            public void onClose(WsSession session, int status, String reason) {
                session.close(WsCloseCodes.NORMAL_CLOSE, "handled");
                closeNotified.countDown();
            }

            @Override
            public void onError(WsSession session, Throwable throwable) {
                listenerFailure.set(throwable);
            }
        };
        WsClientProtocolConfig protocolConfig = WsClientProtocolConfig.builder()
                .maxBufferedMessageSize(Size.create(16, Size.Unit.BYTE))
                .build();
        ClientWsConnection connection = new ClientWsConnection(new TestClientConnection(oversizedMessageReader(), dataWriter),
                                                               listener,
                                                               null,
                                                               protocolConfig);

        AtomicReference<Throwable> sendFailure = new AtomicReference<>();
        Thread sendThread = new Thread(() -> invoke(() -> connection.send("hello", true), sendFailure), "client-ws-send");
        sendThread.start();
        dataWriter.awaitFirstWrite();

        AtomicReference<Throwable> runFailure = new AtomicReference<>();
        Thread runThread = new Thread(() -> invoke(connection::run, runFailure), "client-ws-run");
        runThread.start();

        boolean notifiedBeforeRelease;
        try {
            notifiedBeforeRelease = closeNotified.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            dataWriter.releaseFirstWrite();
            sendThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));
            runThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));
        }

        assertAll(
                () -> assertThat("close callback waited for the send lock", notifiedBeforeRelease, is(true)),
                () -> assertThat("listener failed", listenerFailure.get(), is(nullValue())),
                () -> assertThat("send thread failed", sendFailure.get(), is(nullValue())),
                () -> assertThat("run thread failed", runFailure.get(), is(nullValue())),
                () -> assertThat("send thread did not finish", sendThread.isAlive(), is(false)),
                () -> assertThat("run thread did not finish", runThread.isAlive(), is(false))
        );
    }

    @Test
    void closePublishesStateBeforeWaitingForSendLock() throws Exception {
        BlockingDataWriter dataWriter = new BlockingDataWriter();
        ClientWsConnection connection = ClientWsConnection.create(new TestClientConnection(null, dataWriter),
                                                                  new WsListener() {
                                                                      @Override
                                                                      public void onMessage(WsSession session,
                                                                                            String text,
                                                                                            boolean last) {
                                                                      }
                                                                  });

        AtomicReference<Throwable> sendFailure = new AtomicReference<>();
        Thread sendThread = new Thread(() -> invoke(() -> connection.send("hello", true), sendFailure), "client-ws-send");
        sendThread.start();
        dataWriter.awaitFirstWrite();

        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        Thread closeThread = new Thread(() -> invoke(() -> connection.close(WsCloseCodes.NORMAL_CLOSE, "done"),
                                                     closeFailure),
                                        "client-ws-close");
        closeThread.start();

        assertThat("close flag was not published while send lock was held",
                   awaitCloseSent(connection),
                   is(true));

        dataWriter.releaseFirstWrite();
        sendThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));
        closeThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));

        assertAll(
                () -> assertThat("send thread failed", sendFailure.get(), is(nullValue())),
                () -> assertThat("close thread failed", closeFailure.get(), is(nullValue())),
                () -> assertThat("send thread did not finish", sendThread.isAlive(), is(false)),
                () -> assertThat("close thread did not finish", closeThread.isAlive(), is(false))
        );
    }

    private static boolean awaitCloseSent(ClientWsConnection connection)
            throws ReflectiveOperationException, InterruptedException {
        Field field = ClientWsConnection.class.getDeclaredField("closeSent");
        field.setAccessible(true);
        AtomicBoolean closeSent = (AtomicBoolean) field.get(connection);

        long timeoutNanos = TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS);
        long deadline = System.nanoTime() + timeoutNanos;
        while (System.nanoTime() < deadline) {
            if (closeSent.get()) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return closeSent.get();
    }

    private static DataReader oversizedFrameReader() {
        BufferData inboundFrameData = BufferData.growing(2);
        inboundFrameData.write(0x82);
        inboundFrameData.write(17);
        AtomicReference<byte[]> inbound = new AtomicReference<>(inboundFrameData.readBytes());
        return DataReader.create(() -> inbound.getAndSet(null));
    }

    private static DataReader largeFrameHeaderReader() {
        BufferData inboundFrameData = BufferData.growing(10);
        inboundFrameData.write(0x82);
        inboundFrameData.write(127);
        inboundFrameData.writeInt64(1024 * 1024 + 1L);
        AtomicReference<byte[]> inbound = new AtomicReference<>(inboundFrameData.readBytes());
        return DataReader.create(() -> inbound.getAndSet(null));
    }

    private static DataReader oversizedMessageReader() {
        BufferData inboundFrameData = BufferData.growing(32);
        inboundFrameData.write(0x01);
        inboundFrameData.write(9);
        inboundFrameData.write("123456789".getBytes(StandardCharsets.UTF_8));
        inboundFrameData.write(0x80);
        inboundFrameData.write(8);
        inboundFrameData.write("12345678".getBytes(StandardCharsets.UTF_8));
        AtomicReference<byte[]> inbound = new AtomicReference<>(inboundFrameData.readBytes());
        return DataReader.create(() -> inbound.getAndSet(null));
    }

    private static int maskedCloseCode(byte[] closeFrame) {
        int maskOffset = 2;
        int payloadOffset = maskOffset + 4;
        int firstCodeByte = closeFrame[payloadOffset] ^ closeFrame[maskOffset];
        int secondCodeByte = closeFrame[payloadOffset + 1] ^ closeFrame[maskOffset + 1];
        return ((firstCodeByte & 0xFF) << 8) | (secondCodeByte & 0xFF);
    }

    private static void invoke(ThrowingRunnable action, AtomicReference<Throwable> failure) {
        try {
            action.run();
        } catch (Throwable t) {
            failure.set(t);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TestClientConnection implements ClientConnection {
        private final DataReader dataReader;
        private final DataWriter dataWriter;
        private final HelidonSocket socket = new TestHelidonSocket();

        private TestClientConnection(DataReader dataReader, DataWriter dataWriter) {
            this.dataReader = dataReader;
            this.dataWriter = dataWriter;
        }

        @Override
        public DataReader reader() {
            return dataReader;
        }

        @Override
        public DataWriter writer() {
            return dataWriter;
        }

        @Override
        public String channelId() {
            return "test";
        }

        @Override
        public HelidonSocket helidonSocket() {
            return socket;
        }

        @Override
        public void readTimeout(Duration readTimeout) {
        }

        @Override
        public void closeResource() {
        }
    }

    private static final class TestHelidonSocket implements HelidonSocket {
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
        @SuppressWarnings("removal")
        public int read(BufferData buffer) {
            return 0;
        }

        @Override
        public void write(BufferData buffer) {
        }

        @Override
        public PeerInfo remotePeer() {
            return null;
        }

        @Override
        public PeerInfo localPeer() {
            return null;
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

    private static final class BlockingDataWriter implements DataWriter {
        private final CountDownLatch firstWriteStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        private final AtomicBoolean firstWrite = new AtomicBoolean(true);

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
            if (firstWrite.compareAndSet(true, false)) {
                firstWriteStarted.countDown();
                try {
                    if (!releaseFirstWrite.await(2 * TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        fail("Timed out waiting to release first write");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("Interrupted while waiting to release first write", e);
                }
            }
        }

        void awaitFirstWrite() throws InterruptedException {
            if (!firstWriteStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                fail("Timed out waiting for first websocket send");
            }
        }

        void releaseFirstWrite() {
            releaseFirstWrite.countDown();
        }
    }

    private static final class CapturingDataWriter implements DataWriter {
        private final AtomicReference<byte[]> data = new AtomicReference<>();

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
            data.set(buffer.readBytes());
        }
    }
}
