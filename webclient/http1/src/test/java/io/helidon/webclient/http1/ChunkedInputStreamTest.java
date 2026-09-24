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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.http1.Http1ConnectionListener;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1ClientResponseImplTest.TestConnection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkedInputStreamTest {

    @Test
    void reusesAvailableChunkDataWithoutPullingRemainder() throws IOException {
        byte[][] fragments = {
                "5;test=true\r\nABC".getBytes(StandardCharsets.US_ASCII),
                "DE\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
        };
        AtomicInteger pulls = new AtomicInteger();
        AtomicInteger dataEvents = new AtomicInteger();
        DataReader reader = DataReader.create(() -> fragments[pulls.getAndIncrement()]);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        Http1ConnectionListener listener = new Http1ConnectionListener() {
            @Override
            public void data(SocketContext context, BufferData data) {
                assertThat(context, notNullValue());
                if (data.available() > 0) {
                    dataEvents.incrementAndGet();
                }
            }
        };
        var inputStream = new Http1CallChainBase.ChunkedInputStream(
                new TestConnection(reader),
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
        var inputStream = new Http1CallChainBase.ChunkedInputStream(
                new TestConnection(reader),
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
    void closingConsumedWindowDoesNotReadRemainingChunk() throws IOException {
        AtomicInteger pulls = new AtomicInteger();
        DataReader reader = DataReader.create(() -> {
            if (pulls.incrementAndGet() != 1) {
                throw new AssertionError("Closing a partially read chunk must not pull more data");
            }
            return "5;test=true\r\nABC".getBytes(StandardCharsets.US_ASCII);
        });
        TestConnection connection = new TestConnection(reader);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        try (var inputStream = new Http1CallChainBase.ChunkedInputStream(connection,
                                                                         reader,
                                                                         whenComplete,
                                                                         new AtomicReference<>(),
                                                                         false,
                                                                         Http1ConnectionListener.create(List.of()))) {
            assertThat(new String(inputStream.readNBytes(3), StandardCharsets.US_ASCII), is("ABC"));
        }

        assertThat(pulls.get(), is(1));
        assertThat(whenComplete.isDone(), is(true));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void closingConsumedChunkValidatesPendingFraming() throws IOException {
        byte[][] fragments = {
                "1\r\nA".getBytes(StandardCharsets.US_ASCII),
                "\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
        };
        AtomicInteger pulls = new AtomicInteger();
        DataReader reader = DataReader.create(() -> fragments[pulls.getAndIncrement()]);
        TestConnection connection = new TestConnection(reader);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        try (var inputStream = new Http1CallChainBase.ChunkedInputStream(connection,
                                                                         reader,
                                                                         whenComplete,
                                                                         new AtomicReference<>(),
                                                                         false,
                                                                         Http1ConnectionListener.create(List.of()))) {
            assertThat(inputStream.read(), is((int) 'A'));
            assertThat(pulls.get(), is(1));
            assertThat(whenComplete.isDone(), is(false));
        }

        assertThat(pulls.get(), is(2));
        assertThat(whenComplete.isDone(), is(true));
        assertThat(connection.closeCount(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pendingMalformedDelimiterClosesConnection(boolean close) throws IOException {
        byte[][] fragments = {
                "1\r\nA".getBytes(StandardCharsets.US_ASCII),
                "\rX0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
        };
        AtomicInteger pulls = new AtomicInteger();
        DataReader reader = DataReader.create(() -> fragments[pulls.getAndIncrement()]);
        TestConnection connection = new TestConnection(reader);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        try (var inputStream = new Http1CallChainBase.ChunkedInputStream(connection,
                                                                         reader,
                                                                         whenComplete,
                                                                         new AtomicReference<>(),
                                                                         false,
                                                                         Http1ConnectionListener.create(List.of()))) {
            assertThat(inputStream.read(), is((int) 'A'));
            assertThat(pulls.get(), is(1));
            IllegalStateException failure = close
                    ? assertThrows(IllegalStateException.class, inputStream::close)
                    : assertThrows(IllegalStateException.class, inputStream::read);

            assertThat(failure.getMessage(), containsString("not followed by CRLF"));
            CompletionException completionFailure = assertThrows(CompletionException.class, whenComplete::join);
            assertThat(completionFailure.getCause(), sameInstance(failure));
            assertThat(assertThrows(IllegalStateException.class, inputStream::read).getMessage(), is(failure.getMessage()));
        }

        assertThat(pulls.get(), is(2));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void bufferedMalformedDelimiterFailsBeforeExposingPayload() throws IOException {
        DataReader reader = DataReader.create(() -> "1\r\nA\rX0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        TestConnection connection = new TestConnection(reader);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        try (var inputStream = new Http1CallChainBase.ChunkedInputStream(connection,
                                                                         reader,
                                                                         whenComplete,
                                                                         new AtomicReference<>(),
                                                                         false,
                                                                         Http1ConnectionListener.create(List.of()))) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, inputStream::read);
            assertThat(failure.getMessage(), containsString("not followed by CRLF"));
            assertThat(assertThrows(CompletionException.class, whenComplete::join).getCause(), sameInstance(failure));
        }

        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void maximumChunkSizeReturnsAvailableByteWithoutPullingRemainder() throws IOException {
        AtomicInteger pulls = new AtomicInteger();
        DataReader reader = DataReader.create(() -> {
            if (pulls.incrementAndGet() != 1) {
                throw new AssertionError("The declared chunk size must not determine how much data is buffered");
            }
            return "7fffffff\r\nA".getBytes(StandardCharsets.US_ASCII);
        });
        TestConnection connection = new TestConnection(reader);
        CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
        try (var inputStream = new Http1CallChainBase.ChunkedInputStream(connection,
                                                                         reader,
                                                                         whenComplete,
                                                                         new AtomicReference<>(),
                                                                         false,
                                                                         Http1ConnectionListener.create(List.of()))) {
            assertThat(inputStream.read(), is((int) 'A'));
            assertThat(pulls.get(), is(1));
        }

        assertThat(whenComplete.isDone(), is(true));
        assertThat(connection.closeCount(), is(1));
    }
}
