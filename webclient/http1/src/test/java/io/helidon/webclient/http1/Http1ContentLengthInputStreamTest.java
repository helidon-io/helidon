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

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.http1.Http1ConnectionListener;
import io.helidon.webclient.api.WebClientServiceResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class Http1ContentLengthInputStreamTest {
    private static final HelidonSocket SOCKET = new TestSocket();
    private static final Http1ConnectionListener LISTENER = Http1ConnectionListener.create(List.of());

    @Test
    void zeroLengthEntityIsImmediatelyComplete() {
        var directCalls = new AtomicInteger();
        var reader = DataReader.create(
                () -> null,
                (bytes, offset, length) -> {
                    directCalls.incrementAndGet();
                    return -1;
                });
        var completed = new CompletableFuture<WebClientServiceResponse>();
        var input = new Http1CallChainBase.ContentLengthInputStream(
                SOCKET, reader, completed, new AtomicReference<>(), 0, LISTENER);
        byte[] target = new byte[1];

        assertThat(input.read(target, 0, target.length), is(-1));
        assertThat(directCalls.get(), is(0));
        assertThat(completed.isDone(), is(true));
    }

    @Test
    void bulkReadDoesNotConsumeTheNextResponse() {
        byte[] contentAndNextResponse = {1, 2, 3, 4, 99};
        var source = new ByteArrayInputStream(contentAndNextResponse);
        var directCalls = new AtomicInteger();
        var reader = DataReader.create(
                () -> null,
                (bytes, offset, length) -> {
                    directCalls.incrementAndGet();
                    return source.read(bytes, offset, length);
                });
        var completed = new CompletableFuture<WebClientServiceResponse>();
        var input = new Http1CallChainBase.ContentLengthInputStream(
                SOCKET, reader, completed, new AtomicReference<>(), 4, LISTENER);
        byte[] target = new byte[8];

        assertThat(input.read(target, 2, target.length - 2), is(4));
        assertArrayEquals(new byte[] {0, 0, 1, 2, 3, 4, 0, 0}, target);
        assertThat(directCalls.get(), is(1));
        assertThat(completed.isDone(), is(true));
        assertThat(input.read(target, 0, target.length), is(-1));
        assertThat(source.read(), is(99));
    }

    @Test
    void bulkReadDrainsParserBytesBeforeReadingTheSocket() {
        var buffered = new AtomicReference<>(new byte[] {1, 2});
        var source = new ByteArrayInputStream(new byte[] {3, 4});
        var directCalls = new AtomicInteger();
        var reader = DataReader.create(
                () -> buffered.getAndSet(null),
                (bytes, offset, length) -> {
                    directCalls.incrementAndGet();
                    return source.read(bytes, offset, length);
                });
        reader.ensureAvailable();
        var completed = new CompletableFuture<WebClientServiceResponse>();
        var input = new Http1CallChainBase.ContentLengthInputStream(
                SOCKET, reader, completed, new AtomicReference<>(), 4, LISTENER);
        byte[] target = new byte[4];

        assertThat(input.read(target, 0, target.length), is(2));
        assertThat(directCalls.get(), is(0));
        assertThat(input.read(target, 2, 2), is(2));
        assertThat(directCalls.get(), is(1));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, target);
        assertThat(completed.isDone(), is(true));
    }

    private static final class TestSocket implements HelidonSocket {
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
        public int read(BufferData buffer) {
            return -1;
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
            return null;
        }
    }
}
