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
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.concurrency.limits.LimitException;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsCloseException;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WsConnectionFrameRejectionTest {
    private static final int MAX_FRAME_LENGTH = 16;
    private static final int TIMEOUT_SECONDS = 5;

    @Test
    void oversizedHeaderNotifiesCloseWithoutReadingPayload() {
        for (int opCode : new int[] {1, 2}) {
            for (int length : new int[] {17, 126, 65536}) {
                assertAll("opCode=" + opCode + ", length=" + length, () -> {
                    RecordingListener listener = new RecordingListener();
                    List<byte[]> outbound = new CopyOnWriteArrayList<>();
                    AtomicInteger reads = new AtomicInteger();
                    DataReader reader = reader(opCode, length, reads);
                    WsConnection connection = connection(reader, capturingWriter(outbound), listener);

                    CloseConnectionException thrown = assertThrows(CloseConnectionException.class,
                                                                   () -> connection.handle(FixedLimit.create()));
                    connection.close(WsCloseCodes.NORMAL_CLOSE, "again");
                    connection.close(WsCloseCodes.VIOLATED_POLICY, "again");

                    assertAll(
                            () -> assertThat(thrown.getCause(), instanceOf(WsCloseException.class)),
                            () -> assertThat(((WsCloseException) thrown.getCause()).closeCode(), is(WsCloseCodes.TOO_BIG)),
                            () -> assertThat(reads.get(), is(1)),
                            () -> assertThat("mask and payload must remain unread", reader.available(), is(5)),
                            () -> assertThat(listener.messages.get(), is(0)),
                            listener::assertRejected,
                            () -> assertCloseFrame(outbound)
                    );
                });
            }
        }
    }

    @Test
    void callbackCloseCannotReplaceParserClose() {
        RecordingListener listener = new RecordingListener();
        listener.closeAction = session -> {
            session.close(WsCloseCodes.NORMAL_CLOSE, "callback");
            session.close(WsCloseCodes.VIOLATED_POLICY, "callback again");
        };
        List<byte[]> outbound = new CopyOnWriteArrayList<>();
        WsConnection connection = connection(reader(1, 17, new AtomicInteger()), capturingWriter(outbound), listener);

        assertThrows(CloseConnectionException.class, () -> connection.handle(FixedLimit.create()));

        listener.assertRejected();
        assertCloseFrame(outbound);
    }

    @Test
    void callbackFailureStillSendsParserClose() {
        List<RuntimeException> failures = List.of(
                new IllegalStateException("callback failed"),
                new LimitException("callback failed", LimitAlgorithm.Outcome.immediateRejection("test", "test")),
                new WsCloseException("callback failed", WsCloseCodes.VIOLATED_POLICY),
                new CloseConnectionException("callback failed"));
        for (RuntimeException failure : failures) {
            assertAll(failure.getClass().getSimpleName(), () -> {
                RecordingListener listener = new RecordingListener();
                listener.closeAction = _ -> {
                    throw failure;
                };
                List<byte[]> outbound = new CopyOnWriteArrayList<>();
                WsConnection connection = connection(reader(2, 17, new AtomicInteger()), capturingWriter(outbound), listener);

                RuntimeException thrown = assertThrows(failure.getClass(), () -> connection.handle(FixedLimit.create()));

                assertThat(thrown, sameInstance(failure));
                listener.assertRejected();
                assertCloseFrame(outbound);
            });
        }
    }

    @Test
    void callbackTerminateStillSendsParserClose() {
        RecordingListener listener = new RecordingListener();
        listener.closeAction = WsSession::terminate;
        List<byte[]> outbound = new CopyOnWriteArrayList<>();
        WsConnection connection = connection(reader(1, 17, new AtomicInteger()), capturingWriter(outbound), listener);

        CloseConnectionException thrown = assertThrows(CloseConnectionException.class,
                                                       () -> connection.handle(FixedLimit.create()));

        assertThat(thrown.getMessage(), is("Terminate from WebSocket"));
        listener.assertRejected();
        assertCloseFrame(outbound);
    }

    @Test
    void writeFailureDoesNotPreventCloseCallback() {
        RecordingListener listener = new RecordingListener();
        DataWriter writer = mock(DataWriter.class);
        doAnswer(_ -> {
            listener.assertRejected();
            throw new UncheckedIOException(new SocketException("Broken pipe"));
        }).when(writer).writeNow(any(BufferData.class));
        WsConnection connection = connection(reader(1, 17, new AtomicInteger()), writer, listener);

        ServerConnectionException thrown = assertThrows(ServerConnectionException.class,
                                                        () -> connection.handle(FixedLimit.create()));

        assertThat(thrown.getCause(), instanceOf(UncheckedIOException.class));
        listener.assertRejected();
    }

    @Test
    void closeCallbackDoesNotInterruptHandlerAfterReading() {
        RecordingListener listener = new RecordingListener();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        listener.closeAction = session -> {
            ((WsConnection) session).close(false);
            interrupted.set(Thread.currentThread().isInterrupted());
        };
        WsConnection connection = connection(reader(1, 17, new AtomicInteger()),
                                             capturingWriter(new CopyOnWriteArrayList<>()),
                                             listener);
        try {
            assertThrows(CloseConnectionException.class, () -> connection.handle(FixedLimit.create()));
            assertThat("callback ran while connection still considered itself reading", interrupted.get(), is(false));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void parserCloseCleanupDoesNotRequireAnotherConcurrencyPermit() {
        Limit limit = mock(Limit.class, CALLS_REAL_METHODS);
        when(limit.tryAcquireOutcome(true)).thenReturn(
                LimitAlgorithm.Outcome.immediateAcceptance("test", "test", mock(LimitAlgorithm.Token.class)),
                LimitAlgorithm.Outcome.immediateRejection("test", "test"));
        RecordingListener listener = new RecordingListener();
        List<byte[]> outbound = new CopyOnWriteArrayList<>();
        WsConnection connection = connection(reader(1, 17, new AtomicInteger()), capturingWriter(outbound), listener);

        assertThrows(CloseConnectionException.class, () -> connection.handle(limit));

        listener.assertRejected();
        assertCloseFrame(outbound);
    }

    @Test
    void closeCallbackRunsBeforeWaitingForBlockedSend() throws Exception {
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        CountDownLatch callbackFinished = new CountDownLatch(1);
        List<byte[]> outbound = new CopyOnWriteArrayList<>();
        AtomicInteger writes = new AtomicInteger();
        DataWriter writer = mock(DataWriter.class);
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 1) {
                writeStarted.countDown();
                assertThat("blocked send was not released", releaseWrite.await(2 * TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
            }
            outbound.add(invocation.<BufferData>getArgument(0).readBytes());
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        RecordingListener listener = new RecordingListener();
        listener.closeAction = session -> {
            session.close(WsCloseCodes.NORMAL_CLOSE, "callback");
            callbackFinished.countDown();
        };
        WsConnection connection = connection(reader(1, 17, new AtomicInteger()), writer, listener);
        AtomicReference<Throwable> sendFailure = new AtomicReference<>();
        AtomicReference<Throwable> handleFailure = new AtomicReference<>();
        Thread sender = Thread.ofVirtual().unstarted(() -> invoke(() -> connection.send("hello", true), sendFailure));
        Thread handler = Thread.ofVirtual().unstarted(() -> invoke(() -> connection.handle(FixedLimit.create()), handleFailure));
        sender.start();
        try {
            assertThat("send never entered writer", writeStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
            handler.start();
            assertThat("close callback waited for send lock",
                       callbackFinished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                       is(true));
        } finally {
            releaseWrite.countDown();
            sender.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            handler.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }
        assertAll(
                () -> assertThat("send thread still running", sender.isAlive(), is(false)),
                () -> assertThat("handler thread still running", handler.isAlive(), is(false)),
                () -> assertThat(sendFailure.get(), nullValue()),
                () -> assertThat(handleFailure.get(), instanceOf(CloseConnectionException.class)),
                () -> assertThat(outbound.size(), is(2))
        );
        listener.assertRejected();
        assertCloseFrame(outbound.subList(1, 2));
    }

    private static DataReader reader(int opCode, int length, AtomicInteger reads) {
        BufferData header = BufferData.growing(32);
        header.write(0x80 | opCode);
        if (length < 126) {
            header.write(0x80 | length);
        } else if (length < 65536) {
            header.write(0x80 | 126);
            header.writeInt16(length);
        } else {
            header.write(0x80 | 127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                header.write((int) ((long) length >>> shift) & 0xFF);
            }
        }
        header.write(new byte[] {0x11, 0x22, 0x33, 0x44, 0x55});
        byte[] bytes = header.readBytes();
        return DataReader.create(() -> {
            assertThat("parser requested rejected payload", reads.incrementAndGet(), is(1));
            return bytes;
        });
    }

    private static DataWriter capturingWriter(List<byte[]> outbound) {
        DataWriter writer = mock(DataWriter.class);
        doAnswer(invocation -> {
            outbound.add(invocation.<BufferData>getArgument(0).readBytes());
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        return writer;
    }

    private static WsConnection connection(DataReader reader, DataWriter writer, WsListener listener) {
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.dataReader()).thenReturn(reader);
        when(ctx.dataWriter()).thenReturn(writer);
        return WsConnection.create(ctx,
                                   mock(HttpPrologue.class),
                                   mock(Headers.class),
                                   "key",
                                   listener,
                                   WsConfig.builder().maxFrameLength(MAX_FRAME_LENGTH).build());
    }

    private static void assertCloseFrame(List<byte[]> outbound) {
        assertThat("exactly one close frame", outbound.size(), is(1));
        byte[] frame = outbound.getFirst();
        assertAll(
                () -> assertThat("final unmasked close frame", frame[0] & 0xFF, is(0x88)),
                () -> assertThat("close payload length", frame[1] & 0xFF, is(frame.length - 2)),
                () -> assertThat("wire close code", ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF), is(WsCloseCodes.TOO_BIG)),
                () -> assertThat("wire close reason", new String(frame, 4, frame.length - 4, StandardCharsets.UTF_8),
                                 is("Payload too large"))
        );
    }

    private static void invoke(Runnable action, AtomicReference<Throwable> failure) {
        try {
            action.run();
        } catch (Throwable throwable) {
            failure.set(throwable);
        }
    }

    private static final class RecordingListener implements WsListener {
        private final AtomicInteger messages = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();
        private int closeCode;
        private String closeReason;
        private Consumer<WsSession> closeAction = _ -> { };

        @Override
        public void onMessage(WsSession session, String text, boolean last) {
            messages.incrementAndGet();
        }

        @Override
        public void onMessage(WsSession session, BufferData data, boolean last) {
            messages.incrementAndGet();
        }

        @Override
        public void onClose(WsSession session, int status, String reason) {
            closes.incrementAndGet();
            closeCode = status;
            closeReason = reason;
            closeAction.accept(session);
        }

        @Override
        public void onError(WsSession session, Throwable throwable) {
            errors.incrementAndGet();
        }

        private void assertRejected() {
            assertAll(
                    () -> assertThat("close callbacks", closes.get(), is(1)),
                    () -> assertThat("close callback code", closeCode, is(WsCloseCodes.TOO_BIG)),
                    () -> assertThat("close callback reason", closeReason, is("Payload too large")),
                    () -> assertThat("parser rejection must not invoke onError", errors.get(), is(0))
            );
        }
    }
}
