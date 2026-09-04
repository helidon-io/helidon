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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Size;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.concurrency.limits.LimitException;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.LocalCloseConnectionException;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsCloseException;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsListenerBase;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WsConnectionTest {
    private static final long TEST_TIMEOUT_SECONDS = 5;

    @Test
    void terminateSignalsAPlannedLocalClose() {
        WsConnection connection = createConnection(mock(DataWriter.class));

        assertThrows(LocalCloseConnectionException.class, connection::terminate);
    }

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

    @Test
    void oversizedMessageCloseWinsOverCallbackClose() {
        CapturingDataWriter dataWriter = new CapturingDataWriter();
        AtomicInteger messageInvocations = new AtomicInteger();
        AtomicInteger closeInvocations = new AtomicInteger();
        AtomicReference<Integer> closeCode = new AtomicReference<>();
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
                session.close(WsCloseCodes.NORMAL_CLOSE, "handled");
                throw closeFailure;
            }
        };
        WsConfig wsConfig = WsConfig.builder()
                .maxBufferedMessageSize(Size.create(16, Size.Unit.BYTE))
                .build();
        WsConnection connection = createConnection(oversizedMessageReader(), dataWriter, listener, wsConfig);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                    () -> connection.handle(FixedLimit.create()));

        byte[] closeFrame = dataWriter.data.get();
        int wireCloseCode = ((closeFrame[2] & 0xFF) << 8) | (closeFrame[3] & 0xFF);
        assertAll(
                () -> assertSame(closeFailure, thrown),
                () -> assertThat(messageInvocations.get(), is(0)),
                () -> assertThat(closeInvocations.get(), is(1)),
                () -> assertThat(closeCode.get(), is(WsCloseCodes.TOO_BIG)),
                () -> assertThat(closeFrame[0] & 0x0F, is(0x08)),
                () -> assertThat(wireCloseCode, is(WsCloseCodes.TOO_BIG))
        );
    }

    @Test
    void oversizedMessageClosePreservesCallbackLimitException() {
        CapturingDataWriter dataWriter = new CapturingDataWriter();
        AtomicInteger messageInvocations = new AtomicInteger();
        AtomicInteger closeInvocations = new AtomicInteger();
        AtomicReference<Integer> closeCode = new AtomicReference<>();
        LimitException closeFailure = new LimitException("Close callback failed",
                                                         LimitAlgorithm.Outcome.immediateRejection("test", "test"));
        WsListener listener = new WsListenerBase() {
            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                textString(session, text, last, ignored -> messageInvocations.incrementAndGet());
            }

            @Override
            public void onClose(WsSession session, int status, String reason) {
                closeInvocations.incrementAndGet();
                closeCode.set(status);
                throw closeFailure;
            }
        };
        WsConfig wsConfig = WsConfig.builder()
                .maxBufferedMessageSize(Size.create(16, Size.Unit.BYTE))
                .build();
        WsConnection connection = createConnection(oversizedMessageReader(), dataWriter, listener, wsConfig);

        LimitException thrown = assertThrows(LimitException.class, () -> connection.handle(FixedLimit.create()));

        byte[] closeFrame = dataWriter.data.get();
        int wireCloseCode = ((closeFrame[2] & 0xFF) << 8) | (closeFrame[3] & 0xFF);
        assertAll(
                () -> assertSame(closeFailure, thrown),
                () -> assertThat(messageInvocations.get(), is(0)),
                () -> assertThat(closeInvocations.get(), is(1)),
                () -> assertThat(closeCode.get(), is(WsCloseCodes.TOO_BIG)),
                () -> assertThat(closeFrame[0] & 0x0F, is(0x08)),
                () -> assertThat(wireCloseCode, is(WsCloseCodes.TOO_BIG))
        );
    }

    @Test
    void oversizedMessageNotifiesCloseBeforeWaitingForSendLock() throws Exception {
        BlockingDataWriter dataWriter = new BlockingDataWriter();
        CountDownLatch closeNotified = new CountDownLatch(1);
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
        };
        WsConfig wsConfig = WsConfig.builder()
                .maxBufferedMessageSize(Size.create(16, Size.Unit.BYTE))
                .build();
        WsConnection connection = createConnection(oversizedMessageReader(), dataWriter, listener, wsConfig);

        AtomicReference<Throwable> sendFailure = new AtomicReference<>();
        Thread sendThread = new Thread(() -> invoke(() -> connection.send("hello", true), sendFailure), "ws-send");
        sendThread.start();
        dataWriter.awaitFirstWrite();

        AtomicReference<Throwable> handleFailure = new AtomicReference<>();
        Thread handleThread = new Thread(() -> invoke(() -> connection.handle(FixedLimit.create()), handleFailure),
                                         "ws-handle");
        handleThread.start();

        boolean notifiedBeforeRelease;
        try {
            notifiedBeforeRelease = closeNotified.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            dataWriter.releaseFirstWrite();
            sendThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));
            handleThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));
        }

        assertAll(
                () -> assertThat("close callback waited for the send lock", notifiedBeforeRelease, is(true)),
                () -> assertThat("send thread failed", sendFailure.get(), is(nullValue())),
                () -> assertThat("handle thread failed", handleFailure.get(), is(nullValue())),
                () -> assertThat("send thread did not finish", sendThread.isAlive(), is(false)),
                () -> assertThat("handle thread did not finish", handleThread.isAlive(), is(false))
        );
    }

    @Test
    void peerCloseCallbackFailureIsNotReportedAsSecondClose() {
        BufferData inboundFrameData = BufferData.growing(8);
        int[] mask = {0x11, 0x22, 0x33, 0x44};
        inboundFrameData.write(0x88);
        inboundFrameData.write(0x82);
        for (int maskByte : mask) {
            inboundFrameData.write(maskByte);
        }
        inboundFrameData.write(0x03 ^ mask[0]);
        inboundFrameData.write(0xE8 ^ mask[1]);
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
        WsConnection connection = createConnection(DataReader.create(() -> inbound.getAndSet(null)),
                                                   dataWriter,
                                                   listener,
                                                   WsConfig.create());

        connection.handle(FixedLimit.create());

        byte[] closeFrame = dataWriter.data.get();
        int wireCloseCode = ((closeFrame[2] & 0xFF) << 8) | (closeFrame[3] & 0xFF);
        assertAll(
                () -> assertThat(closeInvocations.get(), is(1)),
                () -> assertThat(closeCode.get(), is(WsCloseCodes.NORMAL_CLOSE)),
                () -> assertSame(closeFailure, error.get()),
                () -> assertThat(closeFrame[0] & 0x0F, is(0x08)),
                () -> assertThat(wireCloseCode, is(WsCloseCodes.UNEXPECTED_CONDITION))
        );
    }

    @Test
    void oversizedMessageCloseCallbackRunsWithinLimit() throws Exception {
        FixedLimit limit = FixedLimit.builder()
                .permits(1)
                .queueLength(1)
                .queueTimeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                .build();
        CountDownLatch firstCloseEntered = new CountDownLatch(1);
        CountDownLatch secondCloseEntered = new CountDownLatch(1);
        CountDownLatch releaseCallbacks = new CountDownLatch(1);
        AtomicInteger activeCallbacks = new AtomicInteger();
        AtomicInteger maxActiveCallbacks = new AtomicInteger();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        WsConfig wsConfig = WsConfig.builder()
                .maxBufferedMessageSize(Size.create(16, Size.Unit.BYTE))
                .build();
        WsConnection first = createConnection(oversizedMessageReader(),
                                              new CapturingDataWriter(),
                                              limitingListener(firstCloseEntered,
                                                               releaseCallbacks,
                                                               activeCallbacks,
                                                               maxActiveCallbacks,
                                                               callbackFailure),
                                              wsConfig);
        WsConnection second = createConnection(oversizedMessageReader(),
                                               new CapturingDataWriter(),
                                               limitingListener(secondCloseEntered,
                                                                releaseCallbacks,
                                                                activeCallbacks,
                                                                maxActiveCallbacks,
                                                                callbackFailure),
                                               wsConfig);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread firstThread = new Thread(() -> invoke(() -> first.handle(limit), firstFailure), "ws-limit-first");
        Thread secondThread = new Thread(() -> invoke(() -> second.handle(limit), secondFailure), "ws-limit-second");

        firstThread.start();
        assertThat(firstCloseEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
        secondThread.start();
        boolean secondEnteredBeforeRelease = secondCloseEntered.await(100, TimeUnit.MILLISECONDS);
        releaseCallbacks.countDown();
        firstThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));
        secondThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));

        assertAll(
                () -> assertThat("second close callback ran without a permit", secondEnteredBeforeRelease, is(false)),
                () -> assertThat(secondCloseEntered.getCount(), is(0L)),
                () -> assertThat(maxActiveCallbacks.get(), is(1)),
                () -> assertThat("close callback failed", callbackFailure.get(), is(nullValue())),
                () -> assertThat("first connection failed", firstFailure.get(), is(nullValue())),
                () -> assertThat("second connection failed", secondFailure.get(), is(nullValue())),
                () -> assertThat("first connection did not finish", firstThread.isAlive(), is(false)),
                () -> assertThat("second connection did not finish", secondThread.isAlive(), is(false))
        );
    }

    private static DataReader oversizedMessageReader() {
        BufferData inboundFrameData = BufferData.growing(64);
        writeMaskedTextFrame(inboundFrameData, 0x01, false, "123456789");
        writeMaskedTextFrame(inboundFrameData, 0x00, true, "12345678");
        AtomicReference<byte[]> inbound = new AtomicReference<>(inboundFrameData.readBytes());
        return DataReader.create(() -> inbound.getAndSet(null));
    }

    private static void writeMaskedTextFrame(BufferData frames, int opCode, boolean last, String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        int[] mask = {0x11, 0x22, 0x33, 0x44};
        frames.write((last ? 0x80 : 0) | opCode);
        frames.write(0x80 | payload.length);
        for (int maskByte : mask) {
            frames.write(maskByte);
        }
        for (int i = 0; i < payload.length; i++) {
            frames.write(payload[i] ^ mask[i % mask.length]);
        }
    }

    private static WsListener limitingListener(CountDownLatch entered,
                                               CountDownLatch release,
                                               AtomicInteger active,
                                               AtomicInteger maximumActive,
                                               AtomicReference<Throwable> failure) {
        return new WsListenerBase() {
            @Override
            public void onMessage(WsSession session, String text, boolean last) {
                textString(session, text, last, ignored -> { });
            }

            @Override
            public void onClose(WsSession session, int status, String reason) {
                if (status != WsCloseCodes.TOO_BIG) {
                    failure.compareAndSet(null, new AssertionError("Unexpected close status " + status));
                }
                int current = active.incrementAndGet();
                maximumActive.accumulateAndGet(current, Math::max);
                entered.countDown();
                try {
                    if (!release.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        failure.compareAndSet(null, new AssertionError("Timed out waiting to release close callback"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure.compareAndSet(null, e);
                } finally {
                    active.decrementAndGet();
                }
            }
        };
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

    private static WsConnection createConnection(DataReader dataReader,
                                                 DataWriter dataWriter,
                                                 WsListener listener,
                                                 WsConfig wsConfig) {
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.dataReader()).thenReturn(dataReader);
        when(ctx.dataWriter()).thenReturn(dataWriter);
        return WsConnection.create(ctx,
                                   mock(HttpPrologue.class),
                                   mock(Headers.class),
                                   "key",
                                   listener,
                                   wsConfig);
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
