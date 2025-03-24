/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webclient.api;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class BufferedDataWriterTest {

    @Test
    void testBufferFull() {
        TestHelidonSocket socket = new TestHelidonSocket();
        try (TcpClientConnection.BufferedDataWriter writer = new TcpClientConnection.BufferedDataWriter(socket, 5)) {
            BufferData data = BufferData.create("0");
            writer.write(data);
            assertThat(socket.writeCounter(), is(0));

            for (int i = 0; i < 4; i++) {
                data.rewind();
                writer.write(data);
            }
            assertThat(socket.writeCounter(), is(0));

            data.rewind();
            writer.write(data);
            assertThat(socket.writeCounter(), is(5));

            writer.flush();
            assertThat(socket.writeCounter(), is(6));
        }
    }

    @Test
    void testNoFlush() {
        TestHelidonSocket socket = new TestHelidonSocket();
        try (TcpClientConnection.BufferedDataWriter writer = new TcpClientConnection.BufferedDataWriter(socket, 5)) {
            BufferData data = BufferData.create("0");
            for (int i = 0; i < 5; i++) {
                data.rewind();
                writer.write(data);
            }
            assertThat(socket.writeCounter(), is(0));
        }
        assertThat(socket.writeCounter(), is(5));
    }

    @Test
    void testWritesAtBoundary() {
        TestHelidonSocket socket = new TestHelidonSocket();
        TcpClientConnection.BufferedDataWriter writer = new TcpClientConnection.BufferedDataWriter(socket, 5);
        BufferData data = BufferData.create("0");
        for (int i = 0; i < 50; i++) {
            data.rewind();
            writer.write(data);
        }
        writer.flush();
        writer.close();
        assertThat(socket.writeCounter(), is(50));
    }

    @Test
    void testWritesOverBoundary() {
        TestHelidonSocket socket = new TestHelidonSocket();
        TcpClientConnection.BufferedDataWriter writer = new TcpClientConnection.BufferedDataWriter(socket, 5);
        BufferData data = BufferData.create("0");
        for (int i = 0; i < 52; i++) {
            data.rewind();
            writer.write(data);
        }
        writer.flush();
        writer.close();
        assertThat(socket.writeCounter(), is(52));
    }

    @Test
    void testNoBuffering() {
        TestHelidonSocket socket = new TestHelidonSocket();
        TcpClientConnection.BufferedDataWriter writer = new TcpClientConnection.BufferedDataWriter(socket, -1);
        BufferData data = BufferData.create("0");
        for (int i = 0; i < 10; i++) {
            data.rewind();
            writer.write(data);
            assertThat(socket.writeCounter(), is(i + 1));
        }
        writer.flush();
        writer.close();
    }

    @Test
    void testTooLargeToBuffer() {
        TestHelidonSocket socket = new TestHelidonSocket();
        TcpClientConnection.BufferedDataWriter writer = new TcpClientConnection.BufferedDataWriter(socket, 1);
        BufferData data = BufferData.create("12345");
        writer.write(data);     // can't buffer
        assertThat(socket.writeCounter(), is(5));
        writer.close();
    }

    static class TestHelidonSocket implements HelidonSocket {

        private int writeCounter;

        public int writeCounter() {
            return writeCounter;
        }

        @Override
        public void close() {
        }

        @Override
        public void idle() {
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public int read(BufferData buffer) {
            return 0;
        }

        @Override
        public void write(BufferData buffer) {
            writeCounter += buffer.available();
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
            return null;
        }

        @Override
        public String childSocketId() {
            return null;
        }

        @Override
        public byte[] get() {
            return new byte[0];
        }
    }
}
