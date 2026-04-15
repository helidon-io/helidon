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

package io.helidon.common.socket;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TlsNioSocketTest {

    @Test
    void closeDoesNotRaceWithInFlightTlsWrite() throws Exception {
        BlockingSocketChannel channel = new BlockingSocketChannel();
        SSLEngine engine = mock(SSLEngine.class);
        SSLSession session = mock(SSLSession.class);
        AtomicBoolean closing = new AtomicBoolean(false);
        AtomicBoolean closeOverflowTriggered = new AtomicBoolean(false);
        CountDownLatch closeWrapStarted = new CountDownLatch(1);

        when(engine.getSession()).thenReturn(session);
        when(session.getPacketBufferSize()).thenReturn(1);
        when(session.getApplicationBufferSize()).thenReturn(1);
        when(engine.wrap(any(ByteBuffer.class), any(ByteBuffer.class))).thenAnswer(invocation -> {
            ByteBuffer src = invocation.getArgument(0);
            ByteBuffer dst = invocation.getArgument(1);

            if (!closing.get()) {
                int consumed = Math.min(1, src.remaining());
                for (int i = 0; i < consumed; i++) {
                    src.get();
                    dst.put((byte) 0x5A);
                }
                return new SSLEngineResult(SSLEngineResult.Status.OK,
                                           SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                                           consumed,
                                           consumed);
            }

            closeWrapStarted.countDown();
            if (closeOverflowTriggered.compareAndSet(false, true)) {
                return new SSLEngineResult(SSLEngineResult.Status.BUFFER_OVERFLOW,
                                           SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                                           0,
                                           0);
            }

            return new SSLEngineResult(SSLEngineResult.Status.CLOSED,
                                       SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                                       0,
                                       0);
        });
        doAnswer(invocation -> {
            closing.set(true);
            return null;
        }).when(engine).closeOutbound();

        TlsNioSocket socket = TlsNioSocket.server(channel, engine, "listener", "server");
        AtomicReference<Throwable> writeFailure = new AtomicReference<>();
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();

        Thread writeThread = new Thread(() -> {
            try {
                socket.write(BufferData.create(new byte[] {1}));
            } catch (Throwable t) {
                writeFailure.set(t);
            }
        }, "tls-write-thread");

        Thread closeThread = new Thread(() -> {
            try {
                socket.close();
            } catch (Throwable t) {
                closeFailure.set(t);
            }
        }, "tls-close-thread");

        writeThread.start();
        assertTrue(channel.awaitWriteStarted(), "Timed out waiting for the TLS write to reach SocketChannel.write");

        closeThread.start();
        assertFalse(closeWrapStarted.await(200, TimeUnit.MILLISECONDS),
                    "TLS close should not reach wrapAndSend while a write is still in flight");

        channel.allowWriteToFinish();

        writeThread.join(5_000);
        closeThread.join(5_000);

        assertTrue(closeWrapStarted.await(5, TimeUnit.SECONDS), "Close should proceed once the write finishes");
        assertNull(writeFailure.get(), "TLS write must not fail when close races with it");
        assertNull(closeFailure.get(), "TLS close must not fail when a write is in flight");
    }

    private static final class BlockingSocketChannel extends SocketChannel {
        private final CountDownLatch writeStarted = new CountDownLatch(1);
        private final CountDownLatch allowWriteToFinish = new CountDownLatch(1);

        private BlockingSocketChannel() {
            super(SelectorProvider.provider());
        }

        boolean awaitWriteStarted() throws InterruptedException {
            return writeStarted.await(5, TimeUnit.SECONDS);
        }

        void allowWriteToFinish() {
            allowWriteToFinish.countDown();
        }

        @Override
        public int write(ByteBuffer src) {
            int position = src.position();
            int bytesToWrite = src.remaining();
            writeStarted.countDown();
            try {
                if (!allowWriteToFinish.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release blocked write");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to finish write", e);
            }
            src.position(position + bytesToWrite);
            return bytesToWrite;
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) {
            long totalWritten = 0;
            for (int i = offset; i < offset + length; i++) {
                if (!srcs[i].hasRemaining()) {
                    continue;
                }
                totalWritten += write(srcs[i]);
                break;
            }
            return totalWritten;
        }

        @Override
        public int read(ByteBuffer dst) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SocketChannel bind(SocketAddress local) {
            return this;
        }

        @Override
        public <T> SocketChannel setOption(SocketOption<T> name, T value) {
            return this;
        }

        @Override
        public <T> T getOption(SocketOption<T> name) {
            return null;
        }

        @Override
        public Set<SocketOption<?>> supportedOptions() {
            return Collections.emptySet();
        }

        @Override
        public SocketChannel shutdownInput() {
            return this;
        }

        @Override
        public SocketChannel shutdownOutput() {
            return this;
        }

        @Override
        public Socket socket() {
            return null;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isConnectionPending() {
            return false;
        }

        @Override
        public boolean connect(SocketAddress remote) {
            return true;
        }

        @Override
        public boolean finishConnect() {
            return true;
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }

        @Override
        protected void implCloseSelectableChannel() throws IOException {
        }

        @Override
        protected void implConfigureBlocking(boolean block) throws IOException {
        }
    }
}
