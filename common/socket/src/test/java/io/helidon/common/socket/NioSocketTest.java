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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NioSocketTest {

    @Test
    void writeHandlesPartialSocketChannelWrites() {
        byte[] expected = jsonPayload(900);

        assertWrittenBytes(13, expected);
    }

    @Test
    void writerHandlesPartialSocketChannelWritesForFixedLengthStreamingResponse() {
        byte[] json = jsonPayload(600);
        int firstSplit = json.length / 3;
        int secondSplit = (json.length * 2) / 3;
        byte[] firstBodyPart = Arrays.copyOfRange(json, 0, firstSplit);
        byte[] secondBodyPart = Arrays.copyOfRange(json, firstSplit, secondSplit);
        byte[] thirdBodyPart = Arrays.copyOfRange(json, secondSplit, json.length);

        byte[] headers = ("""
                HTTP/1.1 200 OK\r
                Content-Length: %d\r
                Connection: keep-alive\r
                \r
                """.formatted(json.length)).getBytes(StandardCharsets.US_ASCII);

        byte[] firstWrite = concat(headers, firstBodyPart);

        assertWrittenBytes(17, firstWrite, secondBodyPart, thirdBodyPart);
    }

    @Test
    void writerHandlesPartialSocketChannelWritesForChunkedStreamingResponse() {
        byte[] json = jsonPayload(450);
        int split = json.length / 2;
        byte[] firstChunkPayload = Arrays.copyOfRange(json, 0, split);
        byte[] secondChunkPayload = Arrays.copyOfRange(json, split, json.length);

        byte[] headers = ("""
                HTTP/1.1 200 OK\r
                Transfer-Encoding: chunked\r
                Connection: keep-alive\r
                \r
                """).getBytes(StandardCharsets.US_ASCII);

        assertWrittenBytes(19,
                           headers,
                           chunk(firstChunkPayload),
                           chunk(secondChunkPayload),
                           "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    private static void assertWrittenBytes(int maxBytesPerWrite, byte[]... writes) {
        CapturingSocketChannel channel = new CapturingSocketChannel(maxBytesPerWrite);
        SocketWriter writer = SocketWriter.create(NioSocket.server(channel, "child", "server"));

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        for (byte[] write : writes) {
            expected.writeBytes(write);
            writer.write(BufferData.create(write));
        }

        String expectedText = expected.toString(StandardCharsets.UTF_8);
        String actualText = new String(channel.writtenBytes(), StandardCharsets.UTF_8);
        assertEquals(expectedText, actualText);
    }

    private static byte[] chunk(byte[] payload) {
        byte[] size = Integer.toHexString(payload.length).getBytes(StandardCharsets.US_ASCII);
        return concat(size,
                      "\r\n".getBytes(StandardCharsets.US_ASCII),
                      payload,
                      "\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] jsonPayload(int items) {
        String itemJson = IntStream.range(0, items)
                .mapToObj(i -> "    " + """
                        {"id":%d,"name":"item-%d","active":%s,"score":%d}"""
                        .formatted(i, i, i % 2 == 0, i * 7))
                .collect(Collectors.joining(",\n"));

        return """
                {
                  "items": [
                %s
                  ],
                  "meta": {
                    "count": %d,
                    "source": "issue-11340"
                  }
                }
                """.formatted(itemJson, items).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static final class CapturingSocketChannel extends SocketChannel {
        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private final int maxBytesPerWrite;

        private CapturingSocketChannel(int maxBytesPerWrite) {
            super(SelectorProvider.provider());
            this.maxBytesPerWrite = maxBytesPerWrite;
        }

        byte[] writtenBytes() {
            return written.toByteArray();
        }

        @Override
        public int write(ByteBuffer src) {
            int writtenBytes = Math.min(maxBytesPerWrite, src.remaining());
            byte[] bytes = new byte[writtenBytes];
            src.get(bytes);
            written.writeBytes(bytes);
            return writtenBytes;
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
