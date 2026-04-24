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
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

class ClientWsConnectionTest {
    private static final long TEST_TIMEOUT_SECONDS = 5;

    @Test
    void closePublishesStateBeforeWaitingForSendLock() throws Exception {
        BlockingDataWriter dataWriter = new BlockingDataWriter();
        ClientWsConnection connection = ClientWsConnection.create(new TestClientConnection(dataWriter),
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
        private final DataWriter dataWriter;
        private final HelidonSocket socket = new TestHelidonSocket();

        private TestClientConnection(DataWriter dataWriter) {
            this.dataWriter = dataWriter;
        }

        @Override
        public DataReader reader() {
            return null;
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
                    if (!releaseFirstWrite.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
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
}
