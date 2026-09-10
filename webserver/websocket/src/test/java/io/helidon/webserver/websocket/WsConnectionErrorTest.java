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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.ConnectionContext;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsCloseException;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsConnectionErrorTest {
    @Test
    void onOpenExceptionDoesNotExposeMessage() {
        assertOnOpenFailure(new IllegalStateException("Backend rejected database credentials"));
    }

    @Test
    void onOpenExceptionWithoutMessageClosesCleanly() {
        assertOnOpenFailure(new IllegalStateException());
    }

    @Test
    void onOpenExceptionWithLongMessageClosesCleanly() {
        assertOnOpenFailure(new IllegalStateException("Internal failure details ".repeat(20)));
    }

    @Test
    void onMessageExceptionDoesNotExposeMessage() {
        assertOnMessageFailure(new IllegalStateException("Backend rejected database credentials"));
    }

    @Test
    void onMessageExceptionWithoutMessageClosesCleanly() {
        assertOnMessageFailure(new IllegalStateException());
    }

    @Test
    void onMessageExceptionWithLongMessageClosesCleanly() {
        assertOnMessageFailure(new IllegalStateException("Internal failure details ".repeat(20)));
    }

    @Test
    void explicitCloseFromOnErrorPreservesReasonAndPrecedesFallback() {
        IllegalStateException failure = new IllegalStateException("Backend rejected database credentials");
        WsListener listener = mock(WsListener.class);
        doThrow(failure).when(listener).onMessage(any(WsSession.class), eq("message"), eq(true));
        doAnswer(invocation -> {
            WsSession session = invocation.getArgument(0);
            session.close(WsCloseCodes.VIOLATED_POLICY, "Endpoint unavailable");
            return null;
        }).when(listener).onError(any(WsSession.class), same(failure));
        List<BufferData> frames = new ArrayList<>();
        WsConnection connection = createConnection(listener, frames);

        connection.handle(FixedLimit.create());

        verify(listener).onError(same(connection), same(failure));
        assertCloseFrame(frames, WsCloseCodes.VIOLATED_POLICY, "Endpoint unavailable");
    }

    @Test
    void applicationCloseExceptionPreservesCodeAndReason() {
        WsCloseException failure = new WsCloseException("Message rejected", WsCloseCodes.VIOLATED_POLICY);
        WsListener listener = mock(WsListener.class);
        doThrow(failure).when(listener).onMessage(any(WsSession.class), eq("message"), eq(true));
        List<BufferData> frames = new ArrayList<>();
        WsConnection connection = createConnection(listener, frames);

        connection.handle(FixedLimit.create());

        verify(listener).onClose(connection, WsCloseCodes.VIOLATED_POLICY, "Message rejected");
        assertCloseFrame(frames, WsCloseCodes.VIOLATED_POLICY, "Message rejected");
    }

    private static void assertOnOpenFailure(IllegalStateException failure) {
        WsListener listener = mock(WsListener.class);
        doThrow(failure).when(listener).onOpen(any(WsSession.class));
        List<BufferData> frames = new ArrayList<>();
        WsConnection connection = createConnection(listener, frames);

        connection.handle(FixedLimit.create());

        assertCloseFrame(frames, WsCloseCodes.UNEXPECTED_CONDITION, "Internal server error");
    }

    private static void assertOnMessageFailure(IllegalStateException failure) {
        WsListener listener = mock(WsListener.class);
        doThrow(failure).when(listener).onMessage(any(WsSession.class), eq("message"), eq(true));
        List<BufferData> frames = new ArrayList<>();
        WsConnection connection = createConnection(listener, frames);

        connection.handle(FixedLimit.create());

        verify(listener).onError(same(connection), same(failure));
        assertCloseFrame(frames, WsCloseCodes.UNEXPECTED_CONDITION, "Internal server error");
    }

    private static WsConnection createConnection(WsListener listener, List<BufferData> frames) {
        byte[] payload = "message".getBytes(StandardCharsets.UTF_8);
        int[] mask = {0x11, 0x22, 0x33, 0x44};
        BufferData input = BufferData.create(6 + payload.length);
        input.write(0x81);
        input.write(0x80 | payload.length);
        for (int maskByte : mask) {
            input.write(maskByte);
        }
        for (int i = 0; i < payload.length; i++) {
            input.write(payload[i] ^ mask[i % mask.length]);
        }
        AtomicReference<byte[]> inbound = new AtomicReference<>(input.readBytes());
        DataReader reader = DataReader.create(() -> inbound.getAndSet(null));
        DataWriter writer = mock(DataWriter.class);
        doAnswer(invocation -> {
            BufferData frame = invocation.getArgument(0);
            frames.add(BufferData.create(frame.readBytes()));
            return null;
        }).when(writer).writeNow(any(BufferData.class));
        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.dataReader()).thenReturn(reader);
        when(ctx.dataWriter()).thenReturn(writer);
        return WsConnection.create(ctx,
                                   mock(HttpPrologue.class),
                                   mock(Headers.class),
                                   "key",
                                   listener,
                                   WsConfig.create());
    }

    private static void assertCloseFrame(List<BufferData> frames, int code, String reason) {
        assertThat(frames, hasSize(1));
        BufferData frame = frames.getFirst();
        int flagsAndOpcode = frame.read();
        int payloadLength = frame.read();
        int closeCode = frame.readInt16();
        String closeReason = frame.readString(frame.available(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertThat(flagsAndOpcode, is(0x88)),
                () -> assertThat(payloadLength, is(2 + reason.getBytes(StandardCharsets.UTF_8).length)),
                () -> assertThat(closeCode, is(code)),
                () -> assertThat(closeReason, is(reason))
        );
    }
}
