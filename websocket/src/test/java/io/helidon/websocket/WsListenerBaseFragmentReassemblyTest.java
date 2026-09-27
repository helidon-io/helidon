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

package io.helidon.websocket;

import java.nio.ByteBuffer;

import io.helidon.common.Size;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WsListenerBaseFragmentReassemblyTest {
    private static final long BUFFER_LIMIT = 4;

    @Test
    void defaultProtocolConfigIsCached() {
        WsSession session = new StubWsSession();

        assertSame(session.protocolConfig(), session.protocolConfig());
    }

    @Test
    void buffersTextUpToConfiguredLimit() {
        FullTextListener listener = new FullTextListener();
        WsSession session = session(BUFFER_LIMIT);

        onText(listener, session, "ab", false);
        onText(listener, session, "cd", true);

        assertThat(listener.message, is("abcd"));
        assertThat(listener.consumerInvocations, is(1));
    }

    @Test
    void rejectsTextOverConfiguredLimitAndClearsState() {
        FullTextListener listener = new FullTextListener();
        WsSession session = session(BUFFER_LIMIT);

        onText(listener, session, "abc", false);
        WsCloseException exception = assertThrows(WsCloseException.class,
                                                  () -> onText(listener, session, "de", true));

        assertThat(exception.closeCode(), is(WsCloseCodes.TOO_BIG));
        assertThat(listener.consumerInvocations, is(0));

        onText(listener, session, "ok", true);
        assertThat(listener.message, is("ok"));
        assertThat(listener.consumerInvocations, is(1));
    }

    @Test
    void approximatesTextSizeUsingUtf16CodeUnits() {
        FullTextListener listener = new FullTextListener();
        WsSession session = session(2);

        onText(listener, session, "😃", true);

        assertThat(listener.message, is("😃"));
        assertThat(listener.consumerInvocations, is(1));
    }

    @Test
    void ignoresEmptyBinaryFragments() {
        BufferDataListener listener = new BufferDataListener();
        WsSession session = session(BUFFER_LIMIT);

        for (int i = 0; i < 100; i++) {
            listener.onMessage(session, BufferData.empty(), false);
        }
        listener.onMessage(session, BufferData.create(new byte[]{1, 2, 3, 4}), false);
        listener.onMessage(session, BufferData.empty(), true);

        assertArrayEquals(new byte[]{1, 2, 3, 4}, listener.message.readBytes());
        assertThat(listener.consumerInvocations, is(1));
    }

    @Test
    void doesNotCopyUnfragmentedBinaryMessage() {
        BufferDataListener listener = new BufferDataListener();
        WsSession session = session(BUFFER_LIMIT);
        BufferData message = BufferData.create(new byte[]{1, 2, 3, 4});

        listener.onMessage(session, message, true);

        assertSame(message, listener.message);
        assertThat(listener.consumerInvocations, is(1));
    }

    @Test
    void combinesManySmallBinaryFragments() {
        int messageSize = 300;
        BufferDataListener listener = new BufferDataListener();
        WsSession session = session(messageSize);
        byte[] expected = new byte[messageSize];

        for (int i = 0; i < messageSize; i++) {
            expected[i] = (byte) i;
            listener.onMessage(session, BufferData.create(new byte[]{(byte) i}), i == messageSize - 1);
        }

        assertArrayEquals(expected, listener.message.readBytes());
        assertThat(listener.consumerInvocations, is(1));
    }

    @Test
    void doesNotRetainEmptyBinaryFragments() {
        BufferDataListener listener = new BufferDataListener();
        WsSession session = session(BUFFER_LIMIT);

        for (int i = 0; i < 100; i++) {
            listener.onMessage(session, BufferData.empty(), false);
        }
        listener.onMessage(session, BufferData.empty(), true);

        assertSame(BufferData.empty(), listener.message);
        assertThat(listener.consumerInvocations, is(1));
    }

    @Test
    void doesNotAllocateByteArrayForEmptyMessage() {
        ByteArrayListener listener = new ByteArrayListener();
        WsSession session = session(BUFFER_LIMIT);

        listener.onMessage(session, BufferData.empty(), true);

        assertSame(BufferData.EMPTY_BYTES, listener.message);
        assertThat(listener.consumerInvocations, is(1));
    }

    @Test
    void rejectsBufferDataOverConfiguredLimit() {
        BufferDataListener listener = new BufferDataListener();
        WsSession session = session(BUFFER_LIMIT);

        listener.onMessage(session, BufferData.create(new byte[3]), false);
        WsCloseException exception = assertThrows(WsCloseException.class,
                                                  () -> listener.onMessage(session, BufferData.create(new byte[2]), true));

        assertThat(exception.closeCode(), is(WsCloseCodes.TOO_BIG));
        assertThat(listener.consumerInvocations, is(0));
    }

    @Test
    void rejectsByteBufferOverConfiguredLimit() {
        ByteBufferListener listener = new ByteBufferListener();
        WsSession session = session(BUFFER_LIMIT);

        listener.onMessage(session, BufferData.create(new byte[3]), false);
        WsCloseException exception = assertThrows(WsCloseException.class,
                                                  () -> listener.onMessage(session, BufferData.create(new byte[2]), true));

        assertThat(exception.closeCode(), is(WsCloseCodes.TOO_BIG));
        assertThat(listener.consumerInvocations, is(0));
    }

    @Test
    void rejectsByteArrayOverConfiguredLimit() {
        ByteArrayListener listener = new ByteArrayListener();
        WsSession session = session(BUFFER_LIMIT);

        listener.onMessage(session, BufferData.create(new byte[3]), false);
        WsCloseException exception = assertThrows(WsCloseException.class,
                                                  () -> listener.onMessage(session, BufferData.create(new byte[2]), true));

        assertThat(exception.closeCode(), is(WsCloseCodes.TOO_BIG));
        assertThat(listener.consumerInvocations, is(0));
    }

    private static WsSession session(long maxBufferedMessageSize) {
        return new TestWsSession(maxBufferedMessageSize);
    }

    private static void onText(FullTextListener listener, WsSession session, String text, boolean last) {
        listener.onMessage(session, text, last);
    }

    private static final class FullTextListener extends WsListenerBase {
        private String message;
        private int consumerInvocations;

        @Override
        public void onMessage(WsSession session, String text, boolean last) {
            textString(session, text, last, payload -> {
                consumerInvocations++;
                message = payload;
            });
        }
    }

    private static final class BufferDataListener extends WsListenerBase {
        private BufferData message;
        private int consumerInvocations;

        @Override
        public void onMessage(WsSession session, BufferData buffer, boolean last) {
            binaryBufferData(session, buffer, last, payload -> {
                consumerInvocations++;
                message = payload;
            });
        }
    }

    private static final class ByteBufferListener extends WsListenerBase {
        private int consumerInvocations;

        @Override
        public void onMessage(WsSession session, BufferData buffer, boolean last) {
            binaryByteBuffer(session, buffer, last, payload -> consumerInvocations++);
        }
    }

    private static final class ByteArrayListener extends WsListenerBase {
        private byte[] message;
        private int consumerInvocations;

        @Override
        public void onMessage(WsSession session, BufferData buffer, boolean last) {
            binaryByteArray(session, buffer, last, payload -> {
                consumerInvocations++;
                message = payload;
            });
        }
    }

    private static final class TestWsSession extends StubWsSession {
        private final WsProtocolConfig protocolConfig;

        private TestWsSession(long maxBufferedMessageSize) {
            protocolConfig = new WsProtocolConfig() {
                @Override
                public Size maxBufferedMessageSize() {
                    return Size.create(maxBufferedMessageSize, Size.Unit.BYTE);
                }
            };
        }

        @Override
        public WsProtocolConfig protocolConfig() {
            return protocolConfig;
        }
    }

    private static class StubWsSession implements WsSession {
        @Override
        public WsSession send(String text, boolean last) {
            return this;
        }

        @Override
        public WsSession send(BufferData bufferData, boolean last) {
            return this;
        }

        @Override
        public WsSession ping(BufferData bufferData) {
            return this;
        }

        @Override
        public WsSession pong(BufferData bufferData) {
            return this;
        }

        @Override
        public WsSession close(int code, String reason) {
            return this;
        }

        @Override
        public WsSession terminate() {
            return this;
        }

        @Override
        public SocketContext socketContext() {
            return null;
        }
    }
}
