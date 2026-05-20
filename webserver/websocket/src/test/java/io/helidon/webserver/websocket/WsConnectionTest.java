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

package io.helidon.webserver.websocket;

import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WsConnectionTest {
    private static final long TEST_TIMEOUT_SECONDS = 5;

    @Test
    void sendWrapsUncheckedIOException() {
        DataWriter dataWriter = mock(DataWriter.class);
        doThrow(new UncheckedIOException(new SocketException("Broken pipe")))
                .when(dataWriter)
                .writeNow(any(BufferData.class));

        WsConnection connection = createConnection(dataWriter);

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> connection.send("hello", true));

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(SocketException.class))
        );
    }

    @Test
    void sendWrapsSocketWriterExceptionFromSmartWriter() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        SocketWriter writer = smartFailingWriter(executor);
        try {
            WsConnection connection = createConnection(writer);

            ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                               () -> connection.send("hello", true));

            assertAll(
                    () -> assertThat(exception.getCause(), instanceOf(SocketWriterException.class)),
                    () -> assertThat(exception.getCause().getCause(), instanceOf(UncheckedIOException.class)),
                    () -> assertThat(exception.getCause().getCause().getCause(), instanceOf(SocketException.class))
            );
        } finally {
            writer.close();
            executor.shutdownNow();
        }
    }

    @Test
    void closePublishesStateBeforeWaitingForSendLock() throws Exception {
        BlockingDataWriter dataWriter = new BlockingDataWriter();
        WsConnection connection = createConnection(dataWriter);

        AtomicReference<Throwable> sendFailure = new AtomicReference<>();
        Thread sendThread = new Thread(() -> invoke(() -> connection.send("hello", true), sendFailure), "ws-send");
        sendThread.start();
        dataWriter.awaitFirstWrite();

        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        Thread closeThread = new Thread(() -> invoke(() -> connection.close(WsCloseCodes.NORMAL_CLOSE, "done"), closeFailure),
                                        "ws-close");
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

    private static WsConnection createConnection(DataWriter dataWriter) {
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        when(listenerConfig.protocols()).thenReturn(List.of(WsConfig.builder().build()));

        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(listenerConfig);

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.dataReader()).thenReturn(mock(DataReader.class));
        when(ctx.dataWriter()).thenReturn(dataWriter);

        return WsConnection.create(ctx,
                                   mock(HttpPrologue.class),
                                   mock(Headers.class),
                                   "key",
                                   mock(WsListener.class));
    }

    private static SocketWriter smartFailingWriter(ExecutorService executor) {
        HelidonSocket socket = mock(HelidonSocket.class);
        when(socket.socketId()).thenReturn("test");
        when(socket.childSocketId()).thenReturn("child");
        doThrow(new UncheckedIOException(new SocketException("Broken pipe")))
                .when(socket)
                .write(any(BufferData.class));
        return SocketWriter.create(executor, socket, 2, true);
    }

    private static boolean awaitCloseSent(WsConnection connection) throws ReflectiveOperationException, InterruptedException {
        Field field = WsConnection.class.getDeclaredField("closeSent");
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
