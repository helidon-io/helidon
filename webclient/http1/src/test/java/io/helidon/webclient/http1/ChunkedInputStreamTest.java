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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.http1.Http1ConnectionListener;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.WebClientServiceResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkedInputStreamTest {

    @Test
    void reusesAvailableChunkDataWithoutPullingRemainder() throws IOException {
        byte[][] fragments = {
                "5\r\nABC".getBytes(StandardCharsets.US_ASCII),
                "DE\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
        };
        AtomicInteger pulls = new AtomicInteger();
        AtomicInteger dataEvents = new AtomicInteger();
        DataReader reader = DataReader.create(() -> fragments[pulls.getAndIncrement()]);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        Http1ConnectionListener listener = new Http1ConnectionListener() {
            @Override
            public void data(SocketContext context, BufferData data) {
                if (context == null && data.available() > 0) {
                    dataEvents.incrementAndGet();
                }
            }
        };
        AtomicInteger closes = new AtomicInteger();
        var inputStream = new Http1CallChainBase.ChunkedInputStream(
                new TestConnection(reader, closes),
                reader,
                whenComplete,
                new AtomicReference<>(),
                false,
                listener);
        byte[] firstBytes = new byte[3];

        assertThat(inputStream.read(firstBytes, 0, 1), is(1));
        assertThat(inputStream.read(firstBytes, 1, 1), is(1));
        assertThat(inputStream.read(firstBytes, 2, 1), is(1));
        assertThat(new String(firstBytes, StandardCharsets.US_ASCII), is("ABC"));
        assertThat(pulls.get(), is(1));
        assertThat(dataEvents.get(), is(1));
        assertThat(new String(inputStream.readAllBytes(), StandardCharsets.US_ASCII), is("DE"));
        assertThat(pulls.get(), is(2));
        assertThat(dataEvents.get(), is(2));
        assertThat(whenComplete.isDone(), is(true));
    }

    @Test
    void returnsFinalChunkDataBeforePullingTerminator() throws IOException {
        byte[][] fragments = {
                "1\r\nA".getBytes(StandardCharsets.US_ASCII),
                "\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
        };
        AtomicInteger pulls = new AtomicInteger();
        DataReader reader = DataReader.create(() -> fragments[pulls.getAndIncrement()]);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        AtomicInteger closes = new AtomicInteger();
        var inputStream = new Http1CallChainBase.ChunkedInputStream(
                new TestConnection(reader, closes),
                reader,
                whenComplete,
                new AtomicReference<>(),
                false,
                Http1ConnectionListener.create(List.of()));
        byte[] firstByte = new byte[1];

        assertThat(inputStream.read(firstByte), is(1));
        assertThat(new String(firstByte, StandardCharsets.US_ASCII), is("A"));
        assertThat(pulls.get(), is(1));
        assertThat(inputStream.read(), is(-1));
        assertThat(pulls.get(), is(2));
        assertThat(whenComplete.isDone(), is(true));
    }

    @Test
    void closeConsumesExpectedFinalChunkAfterEntityData() throws IOException {
        byte[][] fragments = {
                "1\r\nA".getBytes(StandardCharsets.US_ASCII),
                "\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
        };
        AtomicInteger pulls = new AtomicInteger();
        DataReader reader = DataReader.create(() -> fragments[pulls.getAndIncrement()]);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        AtomicInteger closes = new AtomicInteger();
        var inputStream = new Http1CallChainBase.ChunkedInputStream(
                new TestConnection(reader, closes),
                reader,
                whenComplete,
                new AtomicReference<>(),
                false,
                Http1ConnectionListener.create(List.of()));

        assertThat(inputStream.read(), is((int) 'A'));
        assertThat(pulls.get(), is(1));
        inputStream.close();

        assertThat(pulls.get(), is(2));
        assertThat(closes.get(), is(0));
        assertThat(whenComplete.isDone(), is(true));
    }

    @Test
    void closePreservesAdvertisedTrailers() throws IOException {
        String response = "1\r\nA\r\n0\r\nX-Test: value\r\n\r\n";
        DataReader reader = DataReader.create(() -> response.getBytes(StandardCharsets.US_ASCII));
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        AtomicInteger closes = new AtomicInteger();
        var inputStream = new Http1CallChainBase.ChunkedInputStream(
                new TestConnection(reader, closes),
                reader,
                whenComplete,
                new AtomicReference<>(),
                true,
                Http1ConnectionListener.create(List.of()));

        assertThat(inputStream.read(), is((int) 'A'));
        inputStream.close();

        assertThat(reader.readLine(), is("X-Test: value"));
        assertThat(closes.get(), is(0));
        assertThat(whenComplete.isDone(), is(true));
    }

    @Test
    void closeRejectsAnotherChunkAfterDecodedEntityEnds() {
        String response = "1\r\nA\r\n1\r\nB\r\n0\r\n\r\n";
        DataReader reader = DataReader.create(() -> response.getBytes(StandardCharsets.US_ASCII));
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        AtomicInteger closes = new AtomicInteger();
        var inputStream = new Http1CallChainBase.ChunkedInputStream(
                new TestConnection(reader, closes),
                reader,
                whenComplete,
                new AtomicReference<>(),
                false,
                Http1ConnectionListener.create(List.of()));

        assertThat(inputStream.read(), is((int) 'A'));
        IllegalStateException failure = assertThrows(IllegalStateException.class, inputStream::close);

        assertThat(failure.getMessage(), is("HTTP response chunked transfer coding is incomplete"));
        assertThat(closes.get(), is(1));
        assertThat(whenComplete.isCompletedExceptionally(), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\rX", "\n0"})
    void rejectsInvalidChunkDataDelimiter(String delimiter) {
        String response = "2\r\nOK" + delimiter + "0\r\n\r\n";
        DataReader reader = DataReader.create(() -> response.getBytes(StandardCharsets.US_ASCII));
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        AtomicInteger closes = new AtomicInteger();
        var inputStream = new Http1CallChainBase.ChunkedInputStream(
                new TestConnection(reader, closes),
                reader,
                whenComplete,
                new AtomicReference<>(),
                false,
                Http1ConnectionListener.create(List.of()));

        IllegalStateException firstFailure = assertThrows(IllegalStateException.class, inputStream::readAllBytes);
        IllegalStateException secondFailure = assertThrows(IllegalStateException.class, inputStream::read);

        assertThat(firstFailure.getMessage(), is("HTTP response chunk data is not followed by CRLF"));
        assertThat(secondFailure.getMessage(), is(firstFailure.getMessage()));
        assertThat(secondFailure, not(sameInstance(firstFailure)));
        assertThat(closes.get(), is(1));
        assertThat(whenComplete.isCompletedExceptionally(), is(true));
    }

    private static final class TestConnection implements ClientConnection {
        private final DataReader reader;
        private final AtomicInteger closes;

        private TestConnection(DataReader reader, AtomicInteger closes) {
            this.reader = reader;
            this.closes = closes;
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
        public void closeResource() {
            closes.incrementAndGet();
        }
    }
}
