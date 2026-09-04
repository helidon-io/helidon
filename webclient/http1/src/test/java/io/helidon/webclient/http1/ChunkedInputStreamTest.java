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

package io.helidon.webclient.http1;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.http1.Http1ConnectionListener;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.WebClientServiceResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkedInputStreamTest {
    @Test
    void deliversPrefixWithoutPullingRemainderOfHugeChunk() {
        AtomicInteger pulls = new AtomicInteger();
        DataReader reader = DataReader.create(() -> {
            if (pulls.getAndIncrement() == 0) {
                return bytes("7fffffff\r\nprefix");
            }
            throw new AssertionError("Decoder waited for the remainder before delivering the prefix");
        });
        var complete = new CompletableFuture<WebClientServiceResponse>();
        var input = input(reader, complete);
        byte[] prefix = new byte[6];
        assertThat(input.read(prefix, 0, prefix.length), is(prefix.length));
        assertThat(new String(prefix, StandardCharsets.US_ASCII), is("prefix"));
        assertThat(complete.isDone(), is(false));
    }

    @Test
    void deliversLastPayloadByteBeforeChunkTerminatorArrives() {
        AtomicInteger pulls = new AtomicInteger();
        DataReader reader = DataReader.create(() -> {
            if (pulls.getAndIncrement() == 0) {
                return bytes("1\r\na");
            }
            throw new AssertionError("Decoder waited for the terminator before delivering the payload");
        });
        assertThat(input(reader, new CompletableFuture<>()).read(), is((int) 'a'));
    }

    @Test
    void decodesFragmentedChunkTerminatorsAndNextChunk() throws Exception {
        var segments = new ArrayDeque<byte[]>();
        for (String segment : new String[] {"3\r", "\nabc", "\r", "\n2\r\n", "de", "\r", "\n0\r", "\n\r", "\n"}) {
            segments.add(bytes(segment));
        }
        DataReader reader = DataReader.create(segments::poll);
        var complete = new CompletableFuture<WebClientServiceResponse>();
        var input = input(reader, complete);
        assertThat(new String(input.readAllBytes(), StandardCharsets.US_ASCII), is("abcde"));
        assertThat(complete.isDone(), is(true));
        assertThat(input.read(), is(-1));
    }

    @Test
    void leavesTrailersForResponseTrailerParser() throws Exception {
        var segments = new ArrayDeque<byte[]>();
        segments.add(bytes("3;example=value\r\nabc\r\n0\r\nChecksum: abc\r\n\r\n"));
        DataReader reader = DataReader.create(segments::poll);
        var input = input(reader, new CompletableFuture<>());
        assertThat(new String(input.readAllBytes(), StandardCharsets.US_ASCII), is("abc"));
        assertThat(reader.readLine(), is("Checksum: abc"));
        assertThat(reader.readLine(), is(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1\r\naXX0\r\n\r\n", "1\r\na\r", "2\r\na", "xyz\r\n"})
    void rejectsInvalidOrTruncatedFraming(String wire) {
        var segments = new ArrayDeque<byte[]>();
        segments.add(bytes(wire));
        var input = input(DataReader.create(segments::poll), new CompletableFuture<>());
        assertThrows(RuntimeException.class, input::readAllBytes);
    }

    @Test
    void emptyReadDoesNotWaitForWireBytes() {
        var input = input(DataReader.create(() -> {
            throw new AssertionError("Zero length read pulled wire bytes");
        }), new CompletableFuture<>());
        assertThat(input.read(new byte[0], 0, 0), is(0));
    }

    @Test
    void rejectsFirstDelimiterByteWithoutWaitingForSecondByte() {
        AtomicInteger pulls = new AtomicInteger();
        DataReader reader = DataReader.create(() -> {
            if (pulls.getAndIncrement() == 0) {
                return bytes("1\r\naX");
            }
            throw new AssertionError("Malformed CR must fail before waiting for LF");
        });
        var connection = new TestConnection(reader);
        var complete = new CompletableFuture<WebClientServiceResponse>();
        var input = input(connection, complete);
        assertThat(input.read(), is((int) 'a'));
        IllegalStateException failure = assertThrows(IllegalStateException.class, input::read);
        assertThat(failure.getMessage(), is("HTTP response chunk data is not followed by CRLF"));
        assertThat(connection.closes, is(1));
        assertThat(complete.isCompletedExceptionally(), is(true));
    }

    @Test
    void closingConsumedSliceBeforeWireChunkEndsDiscardsConnection() throws Exception {
        AtomicInteger pulls = new AtomicInteger();
        DataReader reader = DataReader.create(() -> {
            if (pulls.getAndIncrement() == 0) {
                return bytes("7fffffff\r\nprefix");
            }
            throw new AssertionError("Closing an incomplete wire chunk must not read further bytes");
        });
        var connection = new TestConnection(reader);
        var complete = new CompletableFuture<WebClientServiceResponse>();
        var input = input(connection, complete);
        byte[] prefix = new byte[6];
        assertThat(input.read(prefix), is(prefix.length));
        input.close();
        assertThat(connection.closes, is(1));
        assertThat(complete.isDone(), is(true));
    }

    private static Http1CallChainBase.ChunkedInputStream input(DataReader reader,
                                                             CompletableFuture<WebClientServiceResponse> complete) {
        return input(new TestConnection(reader), complete);
    }

    private static Http1CallChainBase.ChunkedInputStream input(TestConnection connection,
                                                             CompletableFuture<WebClientServiceResponse> complete) {
        return new Http1CallChainBase.ChunkedInputStream(connection, connection.reader(), complete, new AtomicReference<>(), false,
                                                       new Http1ConnectionListener() { }, null);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static class TestConnection implements ClientConnection {
        private final DataReader reader;
        private int closes;

        private TestConnection(DataReader reader) {
            this.reader = reader;
        }

        @Override
        public DataReader reader() {
            return reader;
        }

        @Override
        public DataWriter writer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String channelId() {
            return "test";
        }

        @Override
        public HelidonSocket helidonSocket() {
            return null;
        }

        @Override
        public void readTimeout(Duration readTimeout) {
        }

        @Override
        public void releaseResource() {
            throw new AssertionError("Chunk reader must not release the connection");
        }

        @Override
        public void closeResource() {
            closes++;
        }
    }
}
