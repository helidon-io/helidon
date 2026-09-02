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

package io.helidon.webclient.http2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameListener;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.WindowSize;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2ClientConnectionTest {
    private static final Duration TEST_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final io.helidon.http.HeaderName SHARED_HEADER = HeaderNames.create("x-shared");
    private static final io.helidon.http.HeaderName GRPC_STATUS_HEADER = HeaderNames.create("grpc-status");
    private static final Http2StreamConfig STREAM_CONFIG = new Http2StreamConfig() {
        @Override
        public boolean priorKnowledge() {
            return true;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Duration readTimeout() {
            return Duration.ofSeconds(1);
        }
    };

    private static Http2FrameData encodedHeaderFrame(int streamId,
                                                     Http2Headers headers,
                                                     Http2Headers.DynamicTable dynamicTable,
                                                     Http2HuffmanEncoder huffman) {
        return encodedHeaderFrame(streamId, headers, dynamicTable, huffman, false);
    }

    private static Http2FrameData encodedHeaderFrame(int streamId,
                                                     Http2Headers headers,
                                                     Http2Headers.DynamicTable dynamicTable,
                                                     Http2HuffmanEncoder huffman,
                                                     boolean endOfStream) {
        BufferData data = BufferData.create(256);
        headers.write(dynamicTable, huffman, data);
        data.rewind();
        int flags = endOfStream ? Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM : Http2Flag.END_OF_HEADERS;
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.HEADERS,
                                                          Http2Flag.HeaderFlags.create(flags),
                                                          streamId);
        return new Http2FrameData(header, data);
    }

    private static Http2FrameData dataFrame(int streamId, byte[] bytes, boolean endOfStream) {
        Http2FrameHeader header = Http2FrameHeader.create(bytes.length,
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(endOfStream
                                                                                             ? Http2Flag.END_OF_STREAM
                                                                                             : 0),
                                                          streamId);
        return new Http2FrameData(header, BufferData.create(bytes));
    }

    private static Http2FrameData windowUpdateFrame(int streamId) {
        return new Http2WindowUpdate(1)
                .toFrameData(null, streamId, Http2Flag.NoFlags.create());
    }

    private static Http2FrameData rstStreamFrame(int streamId) {
        return new Http2RstStream(Http2ErrorCode.CANCEL)
                .toFrameData(null, streamId, Http2Flag.NoFlags.create());
    }

    private static Http2FrameData[] encodedSplitHeaderFrames(int streamId,
                                                             Http2Headers headers,
                                                             Http2Headers.DynamicTable dynamicTable,
                                                             Http2HuffmanEncoder huffman) {
        BufferData data = BufferData.create(256);
        headers.write(dynamicTable, huffman, data);
        data.rewind();

        int splitIndex = Math.min(data.available() - 1, Math.max(1, data.available() / 2));
        byte[] firstPart = new byte[splitIndex];
        data.read(firstPart);
        byte[] secondPart = new byte[data.available()];
        data.read(secondPart);

        Http2FrameData firstFrame = new Http2FrameData(Http2FrameHeader.create(firstPart.length,
                                                                               Http2FrameTypes.HEADERS,
                                                                               Http2Flag.HeaderFlags.create(0),
                                                                               streamId),
                                                       BufferData.create(firstPart));
        Http2FrameData secondFrame = new Http2FrameData(Http2FrameHeader.create(secondPart.length,
                                                                                Http2FrameTypes.CONTINUATION,
                                                                                Http2Flag.ContinuationFlags.create(
                                                                                        Http2Flag.END_OF_HEADERS),
                                                                                streamId),
                                                        BufferData.create(secondPart));
        return new Http2FrameData[] {firstFrame, secondFrame};
    }

    private static Http2FrameData settingsFrame(long maxConcurrentStreams) {
        Http2Settings settings = Http2Settings.builder()
                .add(Http2Setting.MAX_CONCURRENT_STREAMS, maxConcurrentStreams)
                .build();
        return settings.toFrameData(null, 0, Http2Flag.SettingsFlags.create(0));
    }

    private static Http2Headers encodedResponseHeaders(boolean includeCacheControl) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(SHARED_HEADER, "shared-value");
        if (includeCacheControl) {
            headers.set(HeaderNames.CACHE_CONTROL, "no-cache");
        }
        return Http2Headers.create(headers)
                .status(Status.OK_200);
    }

    private static Http2Headers encodedTrailers() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(GRPC_STATUS_HEADER, "0");
        return Http2Headers.create(headers);
    }

    private static Http2Headers requestHeaders() {
        return Http2Headers.create(WritableHeaders.create())
                .method(Method.GET)
                .scheme("http")
                .path("/")
                .authority("www.example.com");
    }

    private static void assertHeadersDecodeInArrivalOrder(MockedConnectionTestContext test,
                                                          Http2ClientStream firstStream,
                                                          Http2ClientStream secondStream,
                                                          Http2FrameData[] firstHeaderBlock,
                                                          Http2FrameData[] secondHeaderBlock) throws Exception {
        ExecutorService readExecutor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch secondReadStarted = new CountDownLatch(1);
            CompletableFuture<Http2Headers> secondRead = CompletableFuture.supplyAsync(() -> {
                secondReadStarted.countDown();
                return secondStream.readHeaders();
            }, readExecutor);

            assertTrue(secondReadStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

            // The later stream must become readable as soon as its frames arrive, even if an earlier stream caller is idle.
            test.offerInbound(firstHeaderBlock);
            test.offerInbound(secondHeaderBlock);

            Http2Headers secondHeaders = secondRead.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(secondHeaders.status(), is(Status.OK_200));
            assertThat(secondHeaders.httpHeaders().get(SHARED_HEADER).get(), is("shared-value"));
            assertThat(secondHeaders.httpHeaders().get(HeaderNames.CACHE_CONTROL).get(), is("no-cache"));

            Http2Headers firstHeaders = firstStream.readHeaders();
            assertThat(firstHeaders.status(), is(Status.OK_200));
            assertThat(firstHeaders.httpHeaders().get(SHARED_HEADER).get(), is("shared-value"));
        } finally {
            readExecutor.shutdownNow();
        }
    }

    private static byte[] serializeFrame(Http2FrameData frameData) {
        BufferData serialized = BufferData.create(frameData.header().write(), frameData.data().copy());
        byte[] bytes = new byte[serialized.available()];
        serialized.read(bytes);
        return bytes;
    }

    @Test
    void readHeadersDoNotDependOnCallerDecodeOrder() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream secondStream = connection.createStream(STREAM_CONFIG);

            firstStream.writeHeaders(requestHeaders(), false);
            secondStream.writeHeaders(requestHeaders(), false);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            assertHeadersDecodeInArrivalOrder(test,
                                              firstStream,
                                              secondStream,
                                              new Http2FrameData[] {
                                                      encodedHeaderFrame(firstStream.streamId(),
                                                                         encodedResponseHeaders(false),
                                                                         inboundTable,
                                                                         huffman)
                                              },
                                              new Http2FrameData[] {
                                                      encodedHeaderFrame(secondStream.streamId(),
                                                                         encodedResponseHeaders(true),
                                                                         inboundTable,
                                                                         huffman)
                                              });
            firstStream.close();
            secondStream.close();
            connection.close();
        }
    }

    @Test
    void splitHeadersDoNotDependOnCallerDecodeOrder() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream secondStream = connection.createStream(STREAM_CONFIG);

            firstStream.writeHeaders(requestHeaders(), false);
            secondStream.writeHeaders(requestHeaders(), false);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            assertHeadersDecodeInArrivalOrder(test,
                                              firstStream,
                                              secondStream,
                                              encodedSplitHeaderFrames(firstStream.streamId(),
                                                                      encodedResponseHeaders(false),
                                                                      inboundTable,
                                                                      huffman),
                                              encodedSplitHeaderFrames(secondStream.streamId(),
                                                                      encodedResponseHeaders(true),
                                                                      inboundTable,
                                                                      huffman));
            firstStream.close();
            secondStream.close();
            connection.close();
        }
    }

    @Test
    void headersOnStreamZeroCloseConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            test.createConnection(false);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedSplitHeaderFrames(0, encodedResponseHeaders(false), inboundTable, huffman)[0]);

            test.assertConnectionClosed();
        }
    }

    @Test
    void missingStreamHeadersForNeverOpenedStreamCloseConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            test.createConnection(false);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(1, encodedResponseHeaders(false), inboundTable, huffman));

            test.assertConnectionClosed();
        }
    }

    @Test
    void upgradedConnectionClearsSocketReadTimeoutAfterInitialSettings() throws InterruptedException {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            AtomicReference<Duration> socketReadTimeout = new AtomicReference<>(Duration.ofSeconds(30));
            LinkedBlockingQueue<Duration> http2ReadTimeouts = new LinkedBlockingQueue<>();
            doAnswer(invocation -> {
                socketReadTimeout.set(invocation.getArgument(0));
                return null;
            }).when(test.clientConnection).readTimeout(any(Duration.class));
            when(test.clientConnection.reader()).thenReturn(DataReader.create(() -> {
                http2ReadTimeouts.add(socketReadTimeout.get());
                return test.nextInboundFrame();
            }));
            test.offerInbound(settingsFrame(10));

            Http2ClientConnection connection = Http2ClientConnection.createUpgraded(test.client,
                                                                                     test.clientConnection,
                                                                                     _ -> { },
                                                                                     ignored -> { });

            assertThat(http2ReadTimeouts.poll(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                       is(Duration.ofSeconds(30)));
            assertThat(http2ReadTimeouts.poll(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                       is(Duration.ZERO));
            assertThat(socketReadTimeout.get(), is(Duration.ZERO));
            connection.close();
        }
    }

    @Test
    void initialSettingsFailureClosesConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.closeInbound();

            assertThrows(IllegalStateException.class, () -> test.createConnection(false));
            test.assertConnectionClosed();
        }
    }

    @Test
    void connectionLossFailsStreamWaitingForResponseHeaders() throws InterruptedException {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);
            firstStream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(firstStream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman,
                                                 true));
            assertThat(firstStream.readHeaders().status(), is(Status.OK_200));
            firstStream.close();

            Http2ClientStream redirectedStream = connection.createStream(STREAM_CONFIG);
            redirectedStream.writeHeaders(requestHeaders(), true);
            CountDownLatch responseReadStarted = new CountDownLatch(1);
            CompletableFuture<Http2Headers> response = CompletableFuture.supplyAsync(() -> {
                responseReadStarted.countDown();
                return redirectedStream.readHeaders();
            });

            try {
                assertThat(responseReadStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                test.closeInbound();
                test.assertConnectionClosed();
                ExecutionException exception = assertThrows(ExecutionException.class,
                                                              () -> response.get(TEST_WAIT_TIMEOUT.toMillis(),
                                                                                 TimeUnit.MILLISECONDS));
                assertThat(exception.getCause(), instanceOf(DataReader.InsufficientDataAvailableException.class));
            } finally {
                redirectedStream.close();
                connection.close();
            }
        }
    }

    @Test
    void connectionLossFailsStreamWaitingFor100Continue() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            WritableHeaders<?> headers = WritableHeaders.create();
            headers.set(HeaderValues.EXPECT_100);
            Http2Headers requestHeaders = Http2Headers.create(headers)
                    .method(Method.POST)
                    .scheme("http")
                    .path("/")
                    .authority("www.example.com");
            stream.writeHeaders(requestHeaders, false);

            CountDownLatch continueWaitStarted = new CountDownLatch(1);
            CompletableFuture<Status> continueStatus = CompletableFuture.supplyAsync(() -> {
                continueWaitStarted.countDown();
                return stream.waitFor100Continue(Duration.ofSeconds(30));
            });

            try {
                assertThat(continueWaitStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThrows(TimeoutException.class, () -> continueStatus.get(200, TimeUnit.MILLISECONDS));
                test.closeInbound();
                test.assertConnectionClosed();
                ExecutionException exception = assertThrows(ExecutionException.class,
                                                              () -> continueStatus.get(TEST_WAIT_TIMEOUT.toMillis(),
                                                                                       TimeUnit.MILLISECONDS));
                assertThat(exception.getCause(), instanceOf(DataReader.InsufficientDataAvailableException.class));
            } finally {
                stream.close();
                connection.close();
            }
        }
    }

    @Test
    void connectionLossFailsStreamWaitingForResponseEntity() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman));
            assertThat(stream.readHeaders().status(), is(Status.OK_200));

            CountDownLatch entityReadStarted = new CountDownLatch(1);
            CompletableFuture<BufferData> entity = CompletableFuture.supplyAsync(() -> {
                entityReadStarted.countDown();
                return stream.read();
            });

            try {
                assertThat(entityReadStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThrows(TimeoutException.class, () -> entity.get(200, TimeUnit.MILLISECONDS));
                test.closeInbound();
                test.assertConnectionClosed();
                ExecutionException exception = assertThrows(ExecutionException.class,
                                                              () -> entity.get(TEST_WAIT_TIMEOUT.toMillis(),
                                                                               TimeUnit.MILLISECONDS));
                assertThat(exception.getCause(), instanceOf(DataReader.InsufficientDataAvailableException.class));
            } finally {
                stream.close();
                connection.close();
            }
        }
    }

    @Test
    void connectionLossFailsStreamWaitingForOutboundWindowUpdate() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            Http2Settings settings = Http2Settings.builder()
                    .add(Http2Setting.MAX_CONCURRENT_STREAMS, 10L)
                    .add(Http2Setting.INITIAL_WINDOW_SIZE, 0L)
                    .build();
            test.offerInbound(settings.toFrameData(null, 0, Http2Flag.SettingsFlags.create(0)));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), false);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman,
                                                 true));
            assertThat(stream.readHeaders().status(), is(Status.OK_200));

            CompletableFuture<Void> write = CompletableFuture.runAsync(
                    () -> stream.writeData(BufferData.create(new byte[] {1}), true));

            try {
                assertThrows(TimeoutException.class, () -> write.get(200, TimeUnit.MILLISECONDS));
                test.closeInbound();
                test.assertConnectionClosed();
                ExecutionException exception = assertThrows(ExecutionException.class,
                                                              () -> write.get(TEST_WAIT_TIMEOUT.toMillis(),
                                                                              TimeUnit.MILLISECONDS));
                assertThat(exception.getCause(), instanceOf(DataReader.InsufficientDataAvailableException.class));
            } finally {
                stream.close();
                connection.close();
            }
        }
    }

    @Test
    void connectionLossPreservesFailureForAllConnectionWindowWaiters() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream secondStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream thirdStream = connection.createStream(STREAM_CONFIG);
            firstStream.writeHeaders(requestHeaders(), false);
            secondStream.writeHeaders(requestHeaders(), false);
            thirdStream.writeHeaders(requestHeaders(), false);
            firstStream.writeData(BufferData.create(new byte[WindowSize.DEFAULT_WIN_SIZE]), false);

            CountDownLatch writersStarted = new CountDownLatch(2);
            CompletableFuture<Void> secondWrite = CompletableFuture.runAsync(() -> {
                writersStarted.countDown();
                secondStream.writeData(BufferData.create(new byte[] {1}), true);
            });
            CompletableFuture<Void> thirdWrite = CompletableFuture.runAsync(() -> {
                writersStarted.countDown();
                thirdStream.writeData(BufferData.create(new byte[] {1}), true);
            });

            try {
                assertThat(writersStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThrows(TimeoutException.class, () -> secondWrite.get(200, TimeUnit.MILLISECONDS));
                assertThrows(TimeoutException.class, () -> thirdWrite.get(200, TimeUnit.MILLISECONDS));
                test.closeInbound();
                test.assertConnectionClosed();
                ExecutionException secondFailure = assertThrows(ExecutionException.class,
                                                                  () -> secondWrite.get(TEST_WAIT_TIMEOUT.toMillis(),
                                                                                        TimeUnit.MILLISECONDS));
                ExecutionException thirdFailure = assertThrows(ExecutionException.class,
                                                                 () -> thirdWrite.get(TEST_WAIT_TIMEOUT.toMillis(),
                                                                                      TimeUnit.MILLISECONDS));
                assertThat(secondFailure.getCause(), instanceOf(DataReader.InsufficientDataAvailableException.class));
                assertThat(thirdFailure.getCause(), instanceOf(DataReader.InsufficientDataAvailableException.class));
            } finally {
                firstStream.close();
                secondStream.close();
                thirdStream.close();
                connection.close();
            }
        }
    }

    @Test
    void unregisteredStreamFailureDoesNotWakeConnectionWindowWaiter() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream waitingStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream unregisteredStream = connection.createStream(STREAM_CONFIG);
            firstStream.writeHeaders(requestHeaders(), false);
            waitingStream.writeHeaders(requestHeaders(), false);
            unregisteredStream.writeHeaders(requestHeaders(), false);
            connection.removeStream(unregisteredStream.streamId());
            firstStream.writeData(BufferData.create(new byte[WindowSize.DEFAULT_WIN_SIZE]), false);

            CountDownLatch writerStarted = new CountDownLatch(1);
            CompletableFuture<Void> waitingWrite = CompletableFuture.runAsync(() -> {
                writerStarted.countDown();
                waitingStream.writeData(BufferData.create(new byte[] {1}), true);
            });

            try {
                assertThat(writerStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThrows(TimeoutException.class, () -> waitingWrite.get(200, TimeUnit.MILLISECONDS));
                unregisteredStream.connectionClosedBeforeRegistration(
                        new Http2Exception(Http2ErrorCode.PROTOCOL, "expected test connection failure"));
                assertThrows(TimeoutException.class, () -> waitingWrite.get(200, TimeUnit.MILLISECONDS));

                test.closeInbound();
                test.assertConnectionClosed();
                ExecutionException failure = assertThrows(ExecutionException.class,
                                                            () -> waitingWrite.get(TEST_WAIT_TIMEOUT.toMillis(),
                                                                                   TimeUnit.MILLISECONDS));
                assertThat(failure.getCause(), instanceOf(DataReader.InsufficientDataAvailableException.class));
            } finally {
                firstStream.close();
                waitingStream.close();
                unregisteredStream.close();
                connection.close();
            }
        }
    }

    @Test
    void connectionLossFailsPendingTrailers() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            WritableHeaders<?> responseHeaders = WritableHeaders.create();
            responseHeaders.set(HeaderNames.TRAILER, "grpc-status");
            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 Http2Headers.create(responseHeaders).status(Status.OK_200),
                                                 inboundTable,
                                                 huffman));
            assertThat(stream.readHeaders().status(), is(Status.OK_200));

            CompletableFuture<Boolean> trailersCompletedAfterTransportClose = stream.trailers()
                    .handle((_, _) -> test.transportClosed.get());
            test.closeInbound();
            test.assertConnectionClosed();

            ExecutionException exception = assertThrows(ExecutionException.class,
                                                          () -> stream.trailers().get(TEST_WAIT_TIMEOUT.toMillis(),
                                                                                      TimeUnit.MILLISECONDS));
            assertThat(exception.getCause(), instanceOf(DataReader.InsufficientDataAvailableException.class));
            assertThat(trailersCompletedAfterTransportClose.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                       is(true));
            stream.close();
            connection.close();
        }
    }

    @Test
    void connectionLossFailsSiblingTrailersDespiteBlockingCallback() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream secondStream = connection.createStream(STREAM_CONFIG);
            firstStream.writeHeaders(requestHeaders(), true);
            secondStream.writeHeaders(requestHeaders(), true);

            WritableHeaders<?> responseHeaders = WritableHeaders.create();
            responseHeaders.set(HeaderNames.TRAILER, "grpc-status");
            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            Http2Headers response = Http2Headers.create(responseHeaders).status(Status.OK_200);
            test.offerInbound(encodedHeaderFrame(firstStream.streamId(), response, inboundTable, huffman),
                              encodedHeaderFrame(secondStream.streamId(), response, inboundTable, huffman));
            assertThat(firstStream.readHeaders().status(), is(Status.OK_200));
            assertThat(secondStream.readHeaders().status(), is(Status.OK_200));

            CompletableFuture<Headers> firstTrailers = firstStream.trailers();
            CompletableFuture<Headers> secondTrailers = secondStream.trailers();
            AtomicBoolean callbackClaimed = new AtomicBoolean();
            CountDownLatch callbacksEntered = new CountDownLatch(2);
            CountDownLatch releaseCallback = new CountDownLatch(1);
            Runnable blockFirstCallback = () -> {
                boolean block = callbackClaimed.compareAndSet(false, true);
                callbacksEntered.countDown();
                if (block) {
                    try {
                        if (!releaseCallback.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                            throw new IllegalStateException("Timed out waiting to release trailers callback");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while blocking trailers callback", e);
                    }
                }
            };
            firstTrailers.whenComplete((_, _) -> blockFirstCallback.run());
            secondTrailers.whenComplete((_, _) -> blockFirstCallback.run());

            try {
                test.closeInbound();
                test.assertConnectionClosed();
                assertThat(callbacksEntered.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThat(firstTrailers.isCompletedExceptionally(), is(true));
                assertThat(secondTrailers.isCompletedExceptionally(), is(true));
            } finally {
                releaseCallback.countDown();
                firstStream.close();
                secondStream.close();
                connection.close();
            }
        }
    }

    @Test
    void connectionLossPreservesCompletedResponse() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman,
                                                 true));
            test.closeInbound();
            test.assertConnectionClosed();

            assertThat(stream.readHeaders().status(), is(Status.OK_200));
            stream.close();
            connection.close();
        }
    }

    @Test
    void connectionLossPreservesQueuedResponseEntity() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            byte[] expectedEntity = "done".getBytes(StandardCharsets.UTF_8);
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman),
                              dataFrame(stream.streamId(), expectedEntity, true));
            test.closeInbound();
            test.assertConnectionClosed();

            assertThat(stream.readHeaders().status(), is(Status.OK_200));
            BufferData entity = stream.read();
            byte[] actualEntity = new byte[entity.available()];
            entity.read(actualEntity);
            assertThat(actualEntity, is(expectedEntity));
            stream.close();
            connection.close();
        }
    }

    @Test
    void connectionLossFailsStreamRegisteredAfterClosure() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);

            test.closeInbound();
            test.assertConnectionClosed();

            assertThrows(DataReader.InsufficientDataAvailableException.class,
                         () -> stream.writeHeaders(requestHeaders(), true));
            stream.close();
            connection.close();
        }
    }

    @Test
    void protocolFailureWritesGoAwayBeforeFailingLateStream() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream activeStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream lateStream = connection.createStream(STREAM_CONFIG);
            activeStream.writeHeaders(requestHeaders(), false);

            MockedConnectionTestContext.BlockedWrite blockedGoAway = test.blockNextWriteNow();
            test.offerInbound(dataFrame(activeStream.streamId(), "invalid".getBytes(StandardCharsets.UTF_8), false));
            assertThat(blockedGoAway.awaitEntered(), is(true));

            CompletableFuture<Void> lateWrite;
            try {
                CountDownLatch lateWriteStarted = new CountDownLatch(1);
                lateWrite = CompletableFuture.runAsync(() -> {
                    lateWriteStarted.countDown();
                    lateStream.writeHeaders(requestHeaders(), true);
                });
                assertThat(lateWriteStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThrows(TimeoutException.class, () -> lateWrite.get(200, TimeUnit.MILLISECONDS));
            } finally {
                blockedGoAway.release();
            }

            ExecutionException exception = assertThrows(ExecutionException.class,
                                                          () -> lateWrite.get(TEST_WAIT_TIMEOUT.toMillis(),
                                                                              TimeUnit.MILLISECONDS));
            assertThat(exception.getCause(), instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) exception.getCause()).code(), is(Http2ErrorCode.PROTOCOL));
            test.assertConnectionClosed();
            activeStream.close();
            lateStream.close();
            connection.close();
        }
    }

    @Test
    void interruptedLateStreamDoesNotWaitForBlockedGoAwayWrite() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream activeStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream lateStream = connection.createStream(STREAM_CONFIG);
            activeStream.writeHeaders(requestHeaders(), false);

            MockedConnectionTestContext.BlockedWrite blockedGoAway = test.blockNextWriteNow();
            test.offerInbound(dataFrame(activeStream.streamId(), "invalid".getBytes(StandardCharsets.UTF_8), false));
            assertThat(blockedGoAway.awaitEntered(), is(true));

            CountDownLatch lateWriteStarted = new CountDownLatch(1);
            AtomicBoolean interruptPreserved = new AtomicBoolean();
            CompletableFuture<Throwable> lateWriteResult = new CompletableFuture<>();
            Thread lateWriteThread = Thread.ofPlatform().start(() -> {
                Throwable result;
                try {
                    lateWriteStarted.countDown();
                    lateStream.writeHeaders(requestHeaders(), true);
                    result = new AssertionError("Late stream write unexpectedly completed");
                } catch (Throwable t) {
                    result = t;
                }
                interruptPreserved.set(Thread.currentThread().isInterrupted());
                lateWriteResult.complete(result);
            });

            try {
                assertThat(lateWriteStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThrows(TimeoutException.class, () -> lateWriteResult.get(200, TimeUnit.MILLISECONDS));
                lateWriteThread.interrupt();
                Throwable failure = lateWriteResult.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(failure, instanceOf(Http2Exception.class));
                assertThat(((Http2Exception) failure).code(), is(Http2ErrorCode.PROTOCOL));
                assertThat(interruptPreserved.get(), is(true));
                test.assertConnectionClosed();
            } finally {
                lateWriteThread.interrupt();
                blockedGoAway.release();
            }
            lateWriteThread.join(TEST_WAIT_TIMEOUT.toMillis());
            assertThat(lateWriteThread.isAlive(), is(false));
            activeStream.close();
            lateStream.close();
            connection.close();
        }
    }

    @Test
    void goAwayWriteDoesNotDeadlockWriterFailureClose() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), false);

            MockedConnectionTestContext.BlockedWrite blockedReset = test.blockAndFailNextWriteNow();
            CompletableFuture<Void> cancel = CompletableFuture.runAsync(stream::cancel);
            assertThat(blockedReset.awaitEntered(), is(true));

            try {
                test.offerInbound(dataFrame(stream.streamId(), "invalid".getBytes(StandardCharsets.UTF_8), false));
                Http2ClientProtocolConfig noPing = Http2ClientProtocolConfig.builder()
                        .ping(false)
                        .buildPrototype();
                long deadline = System.nanoTime() + TEST_WAIT_TIMEOUT.toNanos();
                while (!connection.closed(noPing) && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                assertThat(connection.closed(noPing), is(true));
            } finally {
                blockedReset.release();
            }
            cancel.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            test.assertConnectionClosed();
            stream.close();
            connection.close();
        }
    }

    @Test
    void peerGoAwayWritesReciprocalGoAwayBeforeClosing() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            assertThat(test.initialWriteNowCallsCompleted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                       is(true));
            clearInvocations(test.dataWriter, test.clientConnection);

            Http2GoAway peerGoAway = new Http2GoAway(1, Http2ErrorCode.PROTOCOL, "peer protocol failure");
            test.offerInbound(peerGoAway.toFrameData(Http2Settings.create(), 0, Http2Flag.NoFlags.create()));

            ArgumentCaptor<BufferData> reciprocalGoAway = ArgumentCaptor.forClass(BufferData.class);
            InOrder closeOrder = inOrder(test.dataWriter, test.clientConnection);
            closeOrder.verify(test.dataWriter, timeout(TEST_WAIT_TIMEOUT.toMillis())).writeNow(reciprocalGoAway.capture());
            closeOrder.verify(test.clientConnection, timeout(TEST_WAIT_TIMEOUT.toMillis())).closeResource();

            BufferData frameData = reciprocalGoAway.getValue();
            byte[] headerBytes = new byte[Http2FrameHeader.LENGTH];
            frameData.read(headerBytes);
            Http2FrameHeader frameHeader = Http2FrameHeader.create(BufferData.create(headerBytes));
            assertThat(frameHeader.type(), is(Http2FrameType.GO_AWAY));
            byte[] payloadBytes = new byte[frameHeader.length()];
            frameData.read(payloadBytes);
            assertThat(Http2GoAway.create(BufferData.create(payloadBytes)).errorCode(), is(Http2ErrorCode.PROTOCOL));
            connection.close();
        }
    }

    @Test
    void protocolFailureOnRetiringConnectionWritesGoAwayBeforeClosing() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), false);
            verify(test.dataWriter, timeout(TEST_WAIT_TIMEOUT.toMillis()).times(3)).writeNow(any(BufferData.class));
            clearInvocations(test.dataWriter, test.clientConnection);

            connection.retire();
            test.offerInbound(dataFrame(stream.streamId(), "invalid".getBytes(StandardCharsets.UTF_8), false));

            ArgumentCaptor<BufferData> errorGoAway = ArgumentCaptor.forClass(BufferData.class);
            InOrder closeOrder = inOrder(test.dataWriter, test.clientConnection);
            closeOrder.verify(test.dataWriter, timeout(TEST_WAIT_TIMEOUT.toMillis())).writeNow(errorGoAway.capture());
            closeOrder.verify(test.clientConnection, timeout(TEST_WAIT_TIMEOUT.toMillis())).closeResource();

            BufferData frameData = errorGoAway.getValue();
            byte[] headerBytes = new byte[Http2FrameHeader.LENGTH];
            frameData.read(headerBytes);
            Http2FrameHeader frameHeader = Http2FrameHeader.create(BufferData.create(headerBytes));
            assertThat(frameHeader.type(), is(Http2FrameType.GO_AWAY));
            byte[] payloadBytes = new byte[frameHeader.length()];
            frameData.read(payloadBytes);
            assertThat(Http2GoAway.create(BufferData.create(payloadBytes)).errorCode(), is(Http2ErrorCode.PROTOCOL));
            stream.close();
            connection.close();
        }
    }

    @Test
    void protocolFailureBreaksBlockedRetirementGoAway() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), false);
            verify(test.dataWriter, timeout(TEST_WAIT_TIMEOUT.toMillis()).times(3)).writeNow(any(BufferData.class));
            clearInvocations(test.dataWriter, test.clientConnection);

            connection.retire();
            MockedConnectionTestContext.BlockedWrite blockedRetirement = test.blockNextWriteNow();
            CompletableFuture<Void> drained = CompletableFuture.runAsync(stream::close);
            assertThat(blockedRetirement.awaitEntered(), is(true));

            test.offerInbound(dataFrame(stream.streamId() + 2,
                                        "invalid".getBytes(StandardCharsets.UTF_8),
                                        false));

            verify(test.clientConnection, timeout(1_000)).closeResource();
            drained.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void lateHeadersWriteStreamClosedGoAwayBeforeClosing() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman,
                                                 true));
            assertThat(stream.readHeaders().status(), is(Status.OK_200));
            verify(test.dataWriter, timeout(TEST_WAIT_TIMEOUT.toMillis()).times(3)).writeNow(any(BufferData.class));
            clearInvocations(test.dataWriter, test.clientConnection);

            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman,
                                                 true));

            ArgumentCaptor<BufferData> errorGoAway = ArgumentCaptor.forClass(BufferData.class);
            InOrder closeOrder = inOrder(test.dataWriter, test.clientConnection);
            closeOrder.verify(test.dataWriter, timeout(TEST_WAIT_TIMEOUT.toMillis())).writeNow(errorGoAway.capture());
            closeOrder.verify(test.clientConnection, timeout(TEST_WAIT_TIMEOUT.toMillis())).closeResource();

            BufferData frameData = errorGoAway.getValue();
            byte[] headerBytes = new byte[Http2FrameHeader.LENGTH];
            frameData.read(headerBytes);
            Http2FrameHeader frameHeader = Http2FrameHeader.create(BufferData.create(headerBytes));
            assertThat(frameHeader.type(), is(Http2FrameType.GO_AWAY));
            byte[] payloadBytes = new byte[frameHeader.length()];
            frameData.read(payloadBytes);
            assertThat(Http2GoAway.create(BufferData.create(payloadBytes)).errorCode(),
                       is(Http2ErrorCode.STREAM_CLOSED));
            stream.close();
            connection.close();
        }
    }

    @Test
    void dataBeforeResponseHeadersClosesConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);

            stream.writeHeaders(requestHeaders(), true);
            test.offerInbound(dataFrame(stream.streamId(), "hello".getBytes(StandardCharsets.UTF_8), false));

            test.assertConnectionClosed();
        }
    }

    @Test
    void dataAfterTrailersClosesConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);

            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(), encodedResponseHeaders(false), inboundTable, huffman),
                              encodedHeaderFrame(stream.streamId(), encodedTrailers(), inboundTable, huffman, true),
                              dataFrame(stream.streamId(), "late".getBytes(StandardCharsets.UTF_8), false));

            test.assertConnectionClosed();
        }
    }

    @Test
    void closeBeforeWriteHeadersReleasesReservedStream() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream reservedStream = connection.createStream(STREAM_CONFIG);

            assertNull(connection.tryStream(STREAM_CONFIG));

            reservedStream.close();

            Http2ClientStream recoveredStream = connection.tryStream(STREAM_CONFIG);
            assertNotNull(recoveredStream);
            recoveredStream.close();
            connection.close();
        }
    }

    @Test
    void cancelReleasesReservedStreamAfterRstWrite() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);

            stream.writeHeaders(requestHeaders(), false);
            assertNull(connection.tryStream(STREAM_CONFIG));

            MockedConnectionTestContext.BlockedWrite blockedWrite = test.blockNextWriteNow();
            CompletableFuture<Void> cancel = CompletableFuture.runAsync(stream::cancel);

            assertTrue(blockedWrite.awaitEntered());
            try {
                Http2ClientStream unexpectedStream = connection.tryStream(STREAM_CONFIG);
                try {
                    assertNull(unexpectedStream);
                } finally {
                    if (unexpectedStream != null) {
                        unexpectedStream.close();
                    }
                }
            } finally {
                blockedWrite.release();
            }
            cancel.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            stream.close();

            Http2ClientStream recoveredStream = connection.tryStream(STREAM_CONFIG);
            assertNotNull(recoveredStream);
            recoveredStream.close();
            connection.close();
        }
    }

    @Test
    void closeNowDoesNotWaitForConnectionWriter() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);

            stream.writeHeaders(requestHeaders(), false);
            MockedConnectionTestContext.BlockedWrite blockedWrite = test.blockNextWriteNow();
            CompletableFuture<Void> cancel = CompletableFuture.runAsync(stream::cancel);

            assertTrue(blockedWrite.awaitEntered());
            CompletableFuture<Void> closed = CompletableFuture.runAsync(connection::closeNow);
            closed.get(1, TimeUnit.SECONDS);
            test.assertConnectionClosed();
            cancel.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            stream.close();
        }
    }

    @Test
    void secondCloseBreaksBlockedConnectionWriter() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), false);

            MockedConnectionTestContext.BlockedWrite blockedWrite = test.blockNextWriteNow();
            CompletableFuture<Void> write = CompletableFuture.runAsync(
                    () -> connection.writer().write(dataFrame(stream.streamId(), new byte[] {1}, false)));
            assertThat(blockedWrite.awaitEntered(), is(true));

            Http2ClientProtocolConfig noPing = Http2ClientProtocolConfig.builder()
                    .ping(false)
                    .buildPrototype();
            CompletableFuture<Void> firstClose = CompletableFuture.runAsync(connection::close);
            long deadline = System.nanoTime() + TEST_WAIT_TIMEOUT.toNanos();
            while (!connection.closed(noPing) && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(connection.closed(noPing), is(true));

            CompletableFuture<Void> secondClose = CompletableFuture.runAsync(connection::close);
            secondClose.get(1, TimeUnit.SECONDS);
            test.assertConnectionClosed();
            firstClose.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            ExecutionException writeFailure = assertThrows(ExecutionException.class,
                                                            () -> write.get(TEST_WAIT_TIMEOUT.toMillis(),
                                                                            TimeUnit.MILLISECONDS));
            assertThat(writeFailure.getCause(), instanceOf(UncheckedIOException.class));
            stream.close();
        }
    }

    @Test
    void closeBreaksBlockedRetirementGoAway() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), false);

            connection.retire();
            MockedConnectionTestContext.BlockedWrite blockedRetirement = test.blockNextWriteNow();
            CompletableFuture<Void> drained = CompletableFuture.runAsync(stream::close);
            assertThat(blockedRetirement.awaitEntered(), is(true));

            CompletableFuture<Void> closed = CompletableFuture.runAsync(connection::close);
            closed.get(1, TimeUnit.SECONDS);
            test.assertConnectionClosed();
            drained.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void closeWaitsForBlockedErrorGoAway() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            assertThat(test.initialWriteNowCallsCompleted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                       is(true));

            Http2Settings invalidSettings = Http2Settings.builder()
                    .add(Http2Setting.INITIAL_WINDOW_SIZE, WindowSize.MAX_WIN_SIZE + 1L)
                    .build();
            MockedConnectionTestContext.BlockedWrite blockedErrorGoAway = test.blockNextWriteNow();
            test.offerInbound(invalidSettings.toFrameData(null, 0, Http2Flag.SettingsFlags.create(0)));
            assertThat(blockedErrorGoAway.awaitEntered(), is(true));

            CompletableFuture<Void> closed = CompletableFuture.runAsync(connection::close);
            try {
                assertThrows(TimeoutException.class, () -> closed.get(200, TimeUnit.MILLISECONDS));
                verify(test.clientConnection, never()).closeResource();
            } finally {
                blockedErrorGoAway.release();
            }
            closed.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            test.assertConnectionClosed();
        }
    }

    @Test
    void closeBeforePrefaceDoesNotSendGoAway() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            AtomicReference<Http2ClientConnection> connectionRef = new AtomicReference<>();
            MockedConnectionTestContext.BlockedWrite blockedWrite = test.blockNextWriteNow();
            CompletableFuture<Http2ClientConnection> connectionFuture = CompletableFuture.supplyAsync(
                    () -> Http2ClientConnection.createUpgraded(test.client,
                                                               test.clientConnection,
                                                               _ -> { },
                                                               connectionRef::set));

            assertTrue(blockedWrite.awaitEntered());
            Http2ClientConnection connection = connectionRef.get();
            assertNotNull(connection);
            try {
                connection.close();
                test.assertConnectionClosed();
                verify(test.dataWriter).writeNow(any(BufferData.class));
            } finally {
                blockedWrite.release();
            }
            ExecutionException connectionFailure = assertThrows(ExecutionException.class,
                                                                () -> connectionFuture.get(
                                                                        TEST_WAIT_TIMEOUT.toMillis(),
                                                                        TimeUnit.MILLISECONDS));
            assertNotNull(connectionFailure.getCause());
        }
    }

    @Test
    void windowUpdateWriterDoesNotDependOnConnectionExecutor() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            try {
                Http2ClientStream stream = connection.createStream(STREAM_CONFIG);

                stream.writeHeaders(requestHeaders(), true);
                Http2Headers.DynamicTable inboundTable =
                        Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
                Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
                byte[] responseData = "response".getBytes(StandardCharsets.UTF_8);
                test.offerInbound(encodedHeaderFrame(stream.streamId(), encodedResponseHeaders(false), inboundTable, huffman),
                                  dataFrame(stream.streamId(), responseData, false));
                assertThat(stream.readHeaders().status(), is(Status.OK_200));

                clearInvocations(test.dataWriter);
                MockedConnectionTestContext.BlockedWrite blockedWrite = test.blockNextWriteNow();
                CompletableFuture<Void> activeWrite = CompletableFuture.runAsync(
                        () -> connection.writer().write(windowUpdateFrame(0)));

                assertTrue(blockedWrite.awaitEntered());
                try {
                    CompletableFuture<Http2FrameData> read = CompletableFuture.supplyAsync(
                            () -> stream.readOne(Duration.ofSeconds(1)));
                    Http2FrameData frame = read.get(2, TimeUnit.SECONDS);
                    assertThat(frame.header().type(), is(Http2FrameType.DATA));
                    assertThat(frame.data().readBytes(), is(responseData));
                } finally {
                    blockedWrite.release();
                }
                activeWrite.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

                verify(test.dataWriter, timeout(TEST_WAIT_TIMEOUT.toMillis()).atLeast(2))
                        .writeNow(any(BufferData.class));
            } finally {
                connection.closeNow();
            }
        }
    }

    @Test
    void deferredWindowUpdateFailureClosesConnection() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            assertTrue(test.initialWriteNowCallsCompleted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            MockedConnectionTestContext.BlockedWrite blockedWrite = test.blockNextWriteNow();
            CompletableFuture<Void> activeWrite = CompletableFuture.runAsync(
                    () -> connection.writer().write(dataFrame(1, new byte[1024], false)));

            assertTrue(blockedWrite.awaitEntered());
            try {
                test.failWrites();
                connection.writer().write(windowUpdateFrame(0));
            } finally {
                blockedWrite.release();
            }

            activeWrite.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            test.assertConnectionClosed();
        }
    }

    @Test
    void inboundHeadersEndStreamReleasesReservedStreamBeforeApplicationClose() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);

            firstStream.writeHeaders(requestHeaders(), true);
            assertNull(connection.tryStream(STREAM_CONFIG));

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(firstStream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman,
                                                 true));

            assertThat(firstStream.readHeaders().status(), is(Status.OK_200));

            Http2ClientStream secondStream = connection.tryStream(STREAM_CONFIG);
            assertNotNull(secondStream);

            secondStream.close();
            firstStream.close();
            connection.close();
        }
    }

    @Test
    void inboundDataEndStreamReleasesReservedStreamBeforeApplicationClose() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);

            firstStream.writeHeaders(requestHeaders(), true);
            assertNull(connection.tryStream(STREAM_CONFIG));

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(firstStream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman),
                              dataFrame(firstStream.streamId(), "done".getBytes(StandardCharsets.UTF_8), true));

            assertThat(firstStream.readHeaders().status(), is(Status.OK_200));
            BufferData data = firstStream.read();
            byte[] entity = new byte[data.available()];
            data.read(entity);
            assertThat(new String(entity, StandardCharsets.UTF_8), is("done"));

            Http2ClientStream secondStream = connection.tryStream(STREAM_CONFIG);
            assertNotNull(secondStream);

            secondStream.close();
            firstStream.close();
            connection.close();
        }
    }

    @Test
    void initialZeroMaxConcurrentStreamsClosesUnusableConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(0));
            Http2ClientConnection connection = test.createConnection(false);

            assertThrows(IllegalStateException.class, () -> connection.createStream(STREAM_CONFIG));

            test.assertConnectionClosed();
        }
    }

    @Test
    void missingStreamHeadersStillAdvanceConnectionHpackState() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream abandonedStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream secondStream = connection.createStream(STREAM_CONFIG);

            abandonedStream.writeHeaders(requestHeaders(), false);
            secondStream.writeHeaders(requestHeaders(), false);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            Http2FrameData[] abandonedHeaderBlock =
                    encodedSplitHeaderFrames(abandonedStream.streamId(), encodedResponseHeaders(false), inboundTable, huffman);
            Http2FrameData secondHeaderBlock =
                    encodedHeaderFrame(secondStream.streamId(), encodedResponseHeaders(true), inboundTable, huffman);

            abandonedStream.close();

            test.offerInbound(abandonedHeaderBlock);
            test.offerInbound(secondHeaderBlock);

            Http2Headers secondHeaders = secondStream.readHeaders();
            assertThat(secondHeaders.status(), is(Status.OK_200));
            assertThat(secondHeaders.httpHeaders().get(SHARED_HEADER).get(), is("shared-value"));
            assertThat(secondHeaders.httpHeaders().get(HeaderNames.CACHE_CONTROL).get(), is("no-cache"));

            secondStream.close();
            connection.close();
        }
    }

    @Test
    void lateControlFramesForAbandonedStreamDoNotCloseConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream abandonedStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream secondStream = connection.createStream(STREAM_CONFIG);

            abandonedStream.writeHeaders(requestHeaders(), false);
            secondStream.writeHeaders(requestHeaders(), false);
            abandonedStream.close();

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(windowUpdateFrame(abandonedStream.streamId()),
                              rstStreamFrame(abandonedStream.streamId()),
                              encodedHeaderFrame(secondStream.streamId(),
                                                 encodedResponseHeaders(true),
                                                 inboundTable,
                                                 huffman));

            Http2Headers secondHeaders = secondStream.readHeaders();
            assertThat(secondHeaders.status(), is(Status.OK_200));
            assertThat(secondHeaders.httpHeaders().get(HeaderNames.CACHE_CONTROL).get(), is("no-cache"));

            secondStream.close();
            connection.close();
        }
    }

    @Test
    void controlFrameForNeverOpenedStreamClosesConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            test.createConnection(false);

            test.offerInbound(windowUpdateFrame(1));

            test.assertConnectionClosed();
        }
    }

    @Test
    void rstStreamForNeverOpenedStreamClosesConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            test.createConnection(false);

            test.offerInbound(rstStreamFrame(1));

            test.assertConnectionClosed();
        }
    }

    @Test
    void missingServerInitiatedHeadersCloseConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            stream.writeHeaders(requestHeaders(), false);

            test.offerInbound(encodedHeaderFrame(2, encodedResponseHeaders(false), inboundTable, huffman));

            test.assertConnectionClosed();
        }
    }

    @Test
    void missingFutureClientStreamHeadersCloseConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            stream.writeHeaders(requestHeaders(), false);

            test.offerInbound(encodedHeaderFrame(stream.streamId() + 4,
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman));

            test.assertConnectionClosed();
        }
    }

    @Test
    void closedStreamDoesNotReceiveHeadersWhenClosedAfterDecode() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            AtomicReference<Http2ClientStream> streamToClose = new AtomicReference<>();
            CountDownLatch firstHeadersDecoded = new CountDownLatch(1);
            Http2ClientConnection connection = test.createConnection((client, clientConnection) ->
                                                                             new HookedHttp2ClientConnection(client,
                                                                                                             clientConnection,
                                                                                                             streamToClose,
                                                                                                             () -> {
                                                                                                                 streamToClose.get().close();
                                                                                                                 firstHeadersDecoded.countDown();
                                                                                                             }),
                                                                     false);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream secondStream = connection.createStream(STREAM_CONFIG);

            firstStream.writeHeaders(requestHeaders(), false);
            secondStream.writeHeaders(requestHeaders(), false);
            streamToClose.set(firstStream);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            Http2FrameData firstHeaderBlock =
                    encodedHeaderFrame(firstStream.streamId(), encodedResponseHeaders(false), inboundTable, huffman);
            Http2FrameData secondHeaderBlock =
                    encodedHeaderFrame(secondStream.streamId(), encodedResponseHeaders(true), inboundTable, huffman);

            try {
                test.offerInbound(firstHeaderBlock);
                assertTrue(firstHeadersDecoded.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

                test.offerInbound(secondHeaderBlock);

                Http2Headers secondHeaders = secondStream.readHeaders();
                assertThat(secondHeaders.status(), is(Status.OK_200));
                assertThat(secondHeaders.httpHeaders().get(SHARED_HEADER).get(), is("shared-value"));
                assertThat(secondHeaders.httpHeaders().get(HeaderNames.CACHE_CONTROL).get(), is("no-cache"));

                assertThrows(IllegalStateException.class, firstStream::readHeaders);
            } finally {
                secondStream.close();
                connection.close();
            }
        }
    }

    @Test
    void decodedTrailersWaitBehindBufferedData() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            AtomicReference<Http2ClientStream> streamToTrack = new AtomicReference<>();
            CountDownLatch decodedHeaderBlocks = new CountDownLatch(2);
            Http2ClientConnection connection = test.createConnection((client, clientConnection) ->
                                                                             new HookedHttp2ClientConnection(client,
                                                                                                             clientConnection,
                                                                                                             streamToTrack,
                                                                                                             decodedHeaderBlocks::countDown),
                                                                     false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);

            stream.writeHeaders(requestHeaders(), true);
            streamToTrack.set(stream);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(), encodedResponseHeaders(false), inboundTable, huffman),
                              dataFrame(stream.streamId(), "hello".getBytes(StandardCharsets.UTF_8), false),
                              encodedHeaderFrame(stream.streamId(), encodedTrailers(), inboundTable, huffman, true));

            assertTrue(decodedHeaderBlocks.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

            Http2Headers headers = stream.readHeaders();
            assertThat(headers.status(), is(Status.OK_200));

            BufferData data = stream.read();
            byte[] entity = new byte[data.available()];
            data.read(entity);
            assertThat(new String(entity, StandardCharsets.UTF_8), is("hello"));

            assertThat(stream.read().available(), is(0));
            Headers trailers = stream.trailers().get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(trailers.get(GRPC_STATUS_HEADER).get(), is("0"));

            stream.close();
            connection.close();
        }
    }

    @Test
    void invalidTrailersFailEntityAndTrailersWaiters() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman));
            assertThat(stream.readHeaders().status(), is(Status.OK_200));

            CountDownLatch entityReadStarted = new CountDownLatch(1);
            CompletableFuture<BufferData> entity = CompletableFuture.supplyAsync(() -> {
                entityReadStarted.countDown();
                return stream.read();
            });
            try {
                assertThat(entityReadStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThrows(TimeoutException.class, () -> entity.get(200, TimeUnit.MILLISECONDS));
                test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                     encodedTrailers(),
                                                     inboundTable,
                                                     huffman,
                                                     false));
                test.assertConnectionClosed();

                ExecutionException entityFailure = assertThrows(
                        ExecutionException.class,
                        () -> entity.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                assertThat(entityFailure.getCause(), instanceOf(Http2Exception.class));
                assertThat(((Http2Exception) entityFailure.getCause()).code(), is(Http2ErrorCode.PROTOCOL));

                ExecutionException trailersFailure = assertThrows(
                        ExecutionException.class,
                        () -> stream.trailers().get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                assertThat(trailersFailure.getCause(), instanceOf(Http2Exception.class));
                assertThat(((Http2Exception) trailersFailure.getCause()).code(), is(Http2ErrorCode.PROTOCOL));
            } finally {
                stream.close();
                connection.close();
            }
        }
    }

    @Test
    void pseudoHeaderInTrailersFailsStream() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman));
            assertThat(stream.readHeaders().status(), is(Status.OK_200));

            CountDownLatch entityReadStarted = new CountDownLatch(1);
            CompletableFuture<BufferData> entity = CompletableFuture.supplyAsync(() -> {
                entityReadStarted.countDown();
                return stream.read();
            });
            assertThat(entityReadStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
            assertThrows(TimeoutException.class, () -> entity.get(200, TimeUnit.MILLISECONDS));

            Http2Headers trailers = encodedTrailers().path("/forbidden");
            test.offerInbound(encodedHeaderFrame(stream.streamId(), trailers, inboundTable, huffman, true));

            ExecutionException entityFailure = assertThrows(
                    ExecutionException.class,
                    () -> entity.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(entityFailure.getCause(), instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) entityFailure.getCause()).code(), is(Http2ErrorCode.PROTOCOL));

            ExecutionException trailersFailure = assertThrows(
                    ExecutionException.class,
                    () -> stream.trailers().get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(trailersFailure.getCause(), instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) trailersFailure.getCause()).code(), is(Http2ErrorCode.PROTOCOL));

            stream.close();
            connection.close();
        }
    }

    @Test
    void invalidTrailersCallbackDoesNotBlockSiblingStream() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);
            Http2ClientStream secondStream = connection.createStream(STREAM_CONFIG);
            firstStream.writeHeaders(requestHeaders(), true);
            secondStream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(firstStream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman));
            assertThat(firstStream.readHeaders().status(), is(Status.OK_200));

            CountDownLatch callbackStarted = new CountDownLatch(1);
            CountDownLatch releaseCallback = new CountDownLatch(1);
            CompletableFuture<Headers> publishedTrailers = firstStream.trailers().thenApply(it -> it);
            publishedTrailers.whenComplete((_, _) -> {
                callbackStarted.countDown();
                try {
                    releaseCallback.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            try {
                Http2Headers invalidTrailers = encodedTrailers().path("/forbidden");
                test.offerInbound(encodedHeaderFrame(firstStream.streamId(),
                                                     invalidTrailers,
                                                     inboundTable,
                                                     huffman,
                                                     true),
                                  encodedHeaderFrame(secondStream.streamId(),
                                                     encodedResponseHeaders(false),
                                                     inboundTable,
                                                     huffman,
                                                     true));

                assertThat(callbackStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThat(secondStream.readHeaders().status(), is(Status.OK_200));
            } finally {
                releaseCallback.countDown();
                firstStream.close();
                secondStream.close();
                connection.close();
            }
        }
    }

    @Test
    void pseudoHeaderInTrailersRestoresConnectionWindowForDiscardedData() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman));
            assertThat(stream.readHeaders().status(), is(Status.OK_200));

            long initialConnectionWindow = connection.flowControl().incrementInboundConnectionWindowSize(0);
            byte[] entity = "hello".getBytes(StandardCharsets.UTF_8);
            Http2Headers trailers = encodedTrailers().path("/forbidden");
            test.offerInbound(dataFrame(stream.streamId(), entity, false),
                              encodedHeaderFrame(stream.streamId(), trailers, inboundTable, huffman, true));

            ExecutionException trailersFailure = assertThrows(
                    ExecutionException.class,
                    () -> stream.trailers().get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(trailersFailure.getCause(), instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) trailersFailure.getCause()).code(), is(Http2ErrorCode.PROTOCOL));
            assertThat(connection.flowControl().incrementInboundConnectionWindowSize(0), is(initialConnectionWindow));

            stream.close();
            connection.close();
        }
    }

    @Test
    void pseudoHeaderInTrailersRestoresConnectionWindowForDequeuedData() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            CountDownLatch dataDequeued = new CountDownLatch(1);
            CountDownLatch resumeEntityReader = new CountDownLatch(1);
            AtomicReference<Thread> entityReaderThread = new AtomicReference<>();
            Http2FrameListener recvListener = new Http2FrameListener() {
                @Override
                public void frame(SocketContext ctx, int streamId, BufferData data) {
                    if (Thread.currentThread() == entityReaderThread.get()) {
                        dataDequeued.countDown();
                        try {
                            assertThat(resumeEntityReader.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                                       is(true));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while holding dequeued DATA", e);
                        }
                    }
                }
            };
            when(test.client.recvListener()).thenReturn(recvListener);

            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman));
            assertThat(stream.readHeaders().status(), is(Status.OK_200));

            long initialConnectionWindow = connection.flowControl().incrementInboundConnectionWindowSize(0);
            byte[] entityBytes = "hello".getBytes(StandardCharsets.UTF_8);
            CompletableFuture<BufferData> entity = CompletableFuture.supplyAsync(() -> {
                entityReaderThread.set(Thread.currentThread());
                return stream.read();
            });
            try {
                test.offerInbound(dataFrame(stream.streamId(), entityBytes, false));
                assertThat(dataDequeued.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));

                Http2Headers trailers = encodedTrailers().path("/forbidden");
                test.offerInbound(encodedHeaderFrame(stream.streamId(), trailers, inboundTable, huffman, true));

                ExecutionException trailersFailure = assertThrows(
                        ExecutionException.class,
                        () -> stream.trailers().get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                assertThat(trailersFailure.getCause(), instanceOf(Http2Exception.class));
                assertThat(((Http2Exception) trailersFailure.getCause()).code(), is(Http2ErrorCode.PROTOCOL));
                assertThat(connection.flowControl().incrementInboundConnectionWindowSize(0), is(initialConnectionWindow));

                resumeEntityReader.countDown();
                ExecutionException entityFailure = assertThrows(
                        ExecutionException.class,
                        () -> entity.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                assertThat(entityFailure.getCause(), instanceOf(Http2Exception.class));
                assertThat(((Http2Exception) entityFailure.getCause()).code(), is(Http2ErrorCode.PROTOCOL));
            } finally {
                resumeEntityReader.countDown();
                stream.close();
                connection.close();
            }
        }
    }

    @Test
    void discardedStreamBufferPublishesFailure() {
        StreamBuffer buffer = new StreamBuffer(mock(Http2ClientStream.class), 1);
        byte[] entity = "hello".getBytes(StandardCharsets.UTF_8);
        buffer.push(dataFrame(1, entity, false));
        Http2Exception failure = new Http2Exception(Http2ErrorCode.PROTOCOL, "Invalid trailers");

        assertThat(buffer.failAndDiscard(failure), is(entity.length));
        assertThat(assertThrows(Http2Exception.class, () -> buffer.poll(Duration.ZERO)), is(failure));
    }

    @Test
    void invalidTrailersCompleteFutureWhenWindowUpdateFails() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman));
            assertThat(stream.readHeaders().status(), is(Status.OK_200));

            doAnswer(invocation -> {
                BufferData frame = invocation.getArgument(0);
                if (frame.get(3) == Http2FrameType.WINDOW_UPDATE.type()) {
                    throw new UncheckedIOException(new IOException("expected WINDOW_UPDATE failure"));
                }
                return null;
            }).when(test.dataWriter).writeNow(any(BufferData.class));

            byte[] entity = "hello".getBytes(StandardCharsets.UTF_8);
            Http2Headers trailers = encodedTrailers().path("/forbidden");
            test.offerInbound(dataFrame(stream.streamId(), entity, false),
                              encodedHeaderFrame(stream.streamId(), trailers, inboundTable, huffman, true));

            ExecutionException trailersFailure = assertThrows(
                    ExecutionException.class,
                    () -> stream.trailers().get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(trailersFailure.getCause(), instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) trailersFailure.getCause()).code(), is(Http2ErrorCode.PROTOCOL));
            test.assertConnectionClosed();

            stream.close();
            connection.close();
        }
    }

    @Test
    void pseudoHeaderTrailersWithoutEndStreamCloseConnection() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(10));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream stream = connection.createStream(STREAM_CONFIG);
            stream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(stream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman));
            assertThat(stream.readHeaders().status(), is(Status.OK_200));

            Http2Headers trailers = encodedTrailers().path("/forbidden");
            test.offerInbound(encodedHeaderFrame(stream.streamId(), trailers, inboundTable, huffman, false));

            test.assertConnectionClosed();

            stream.close();
            connection.close();
        }
    }

    @Test
    void createWaitsForInitialSettingsAndHonorsPeerMaxConcurrentStreams() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            CompletableFuture<Http2ClientConnection> connectionFuture = new CompletableFuture<>();
            Thread.ofPlatform().start(() -> {
                try {
                    connectionFuture.complete(test.createConnection(false));
                } catch (Throwable t) {
                    connectionFuture.completeExceptionally(t);
                }
            });

            assertThrows(TimeoutException.class, () -> connectionFuture.get(200, TimeUnit.MILLISECONDS));

            test.offerInbound(settingsFrame(1));

            Http2ClientConnection connection = connectionFuture.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            Http2ClientStream firstStream = connection.createStream(STREAM_CONFIG);
            assertNotNull(firstStream);
            assertNull(connection.tryStream(STREAM_CONFIG));

            firstStream.close();

            Http2ClientStream secondStream = connection.tryStream(STREAM_CONFIG);
            assertNotNull(secondStream);
            secondStream.close();
            connection.close();
        }
    }

    @Test
    void writeHeadersFailureReleasesReservedStream() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            CompletableFuture<Http2ClientConnection> connectionFuture = new CompletableFuture<>();
            Thread.ofPlatform().start(() -> {
                try {
                    connectionFuture.complete(test.createConnection(false));
                } catch (Throwable t) {
                    connectionFuture.completeExceptionally(t);
                }
            });

            test.offerInbound(settingsFrame(1));

            Http2ClientConnection connection = connectionFuture.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            Http2ClientStream failingStream = connection.createStream(STREAM_CONFIG);
            assertTrue(test.initialWriteNowCallsCompleted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

            test.failWrites();
            assertThrows(UncheckedIOException.class, () -> failingStream.writeHeaders(requestHeaders(), false));
            test.allowWrites();

            Http2ClientStream recoveredStream = connection.tryStream(STREAM_CONFIG);
            assertNotNull(recoveredStream);
            recoveredStream.close();
            connection.close();
        }
    }

    @Test
    void retiredConnectionAllowsReservedStreamToComplete() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream reservedStream = connection.createStream(STREAM_CONFIG);

            connection.retire();
            reservedStream.writeHeaders(requestHeaders(), true);

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            test.offerInbound(encodedHeaderFrame(reservedStream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman,
                                                 true));
            assertThat(reservedStream.readHeaders().status(), is(Status.OK_200));
            reservedStream.close();

            test.assertConnectionClosed();
        }
    }

    @Test
    void retiredConnectionDrainsExistingStreamBeforeClosing() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream existingStream = connection.createStream(STREAM_CONFIG);
            existingStream.writeHeaders(requestHeaders(), false);
            assertThat(existingStream.streamId(), is(1));
            verify(test.dataWriter, timeout(TEST_WAIT_TIMEOUT.toMillis()).times(3)).writeNow(any(BufferData.class));
            clearInvocations(test.dataWriter);

            connection.retire();

            assertNull(connection.tryStream(STREAM_CONFIG));
            verify(test.clientConnection, never()).closeResource();
            verify(test.dataWriter, never()).writeNow(any(BufferData.class));

            existingStream.close();

            test.assertConnectionClosed();
            ArgumentCaptor<BufferData> goAwayFrame = ArgumentCaptor.forClass(BufferData.class);
            verify(test.dataWriter, times(1)).writeNow(goAwayFrame.capture());

            BufferData frameData = goAwayFrame.getValue();
            byte[] headerBytes = new byte[Http2FrameHeader.LENGTH];
            frameData.read(headerBytes);
            Http2FrameHeader frameHeader = Http2FrameHeader.create(BufferData.create(headerBytes));
            assertThat(frameHeader.type(), is(Http2FrameType.GO_AWAY));
            assertThat(frameHeader.streamId(), is(0));

            byte[] payloadBytes = new byte[frameHeader.length()];
            frameData.read(payloadBytes);
            Http2GoAway goAway = Http2GoAway.create(BufferData.create(payloadBytes));
            assertThat(goAway.lastStreamId(), is(0));
        }
    }

    @Test
    void retiredConnectionWaitsForBufferedResponseEntityConsumption() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.offerInbound(settingsFrame(1));
            Http2ClientConnection connection = test.createConnection(false);
            Http2ClientStream existingStream = connection.createStream(STREAM_CONFIG);
            existingStream.writeHeaders(requestHeaders(), true);
            verify(test.dataWriter, timeout(TEST_WAIT_TIMEOUT.toMillis()).times(3)).writeNow(any(BufferData.class));
            clearInvocations(test.dataWriter, test.clientConnection);

            connection.retire();

            Http2Headers.DynamicTable inboundTable =
                    Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            Http2HuffmanEncoder huffman = Http2HuffmanEncoder.create();
            byte[] expectedEntity = "entity".getBytes(StandardCharsets.UTF_8);
            test.offerInbound(encodedHeaderFrame(existingStream.streamId(),
                                                 encodedResponseHeaders(false),
                                                 inboundTable,
                                                 huffman),
                              dataFrame(existingStream.streamId(), expectedEntity, true));

            assertThat(existingStream.readHeaders().status(), is(Status.OK_200));
            BufferData entity = existingStream.read();
            byte[] actualEntity = new byte[entity.available()];
            entity.read(actualEntity);
            assertThat(actualEntity, is(expectedEntity));

            verify(test.clientConnection, never()).closeResource();

            existingStream.close();

            test.assertConnectionClosed();
        }
    }

    @Test
    void retiredPreUpgradeConnectionClosesWithoutWritingGoAway() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            Http2ClientConnection connection = new Http2ClientConnection(test.client,
                                                                          test.clientConnection,
                                                                          _ -> { });
            clearInvocations(test.dataWriter);

            connection.retire();

            test.assertConnectionClosed();
            verify(test.dataWriter, never()).writeNow(any(BufferData.class));
        }
    }

    @FunctionalInterface
    private interface ConnectionFactory<T extends Http2ClientConnection> {
        T create(Http2ClientImpl client, ClientConnection clientConnection);
    }

    private static final class MockedConnectionTestContext implements AutoCloseable {
        private static final byte[] END_INBOUND = new byte[0];

        private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();
        private final LinkedBlockingQueue<byte[]> inboundFrames = new LinkedBlockingQueue<>();
        // The client preface is the first writeNow call; the initial SETTINGS ACK is the second.
        private final CountDownLatch initialWriteNowCallsCompleted = new CountDownLatch(2);
        private final AtomicBoolean failWrites = new AtomicBoolean();
        private final AtomicBoolean transportClosed = new AtomicBoolean();
        private final AtomicReference<BlockedWrite> blockedWrite = new AtomicReference<>();
        private final AtomicReference<BlockedWrite> activeBlockedWrite = new AtomicReference<>();
        private final DataWriter dataWriter = mock(DataWriter.class);
        private final io.helidon.common.socket.HelidonSocket socket = mock(io.helidon.common.socket.HelidonSocket.class);
        private final Http2ClientConfig clientConfig;
        private final Http2ClientImpl client;
        private final ClientConnection clientConnection;

        private MockedConnectionTestContext() {
            Http2ClientProtocolConfig protocolConfig = Http2ClientProtocolConfig.builder()
                    .ping(true)
                    .pingTimeout(Duration.ofMillis(100))
                    .build();

            this.clientConfig = Http2ClientConfig.builder()
                    .protocolConfig(protocolConfig)
                    .buildPrototype();

            this.client = mock(Http2ClientImpl.class);
            this.clientConnection = mock(ClientConnection.class);
            WebClient webClient = mock(WebClient.class);

            doAnswer(invocation -> {
                maybeFailWrites();
                return null;
            }).when(dataWriter).write(any(BufferData.class));
            doAnswer(invocation -> {
                maybeFailWrites();
                return null;
            }).when(dataWriter).write(any(BufferData[].class));
            doAnswer(invocation -> {
                maybeFailWrites();
                maybeBlockWriteNow();
                initialWriteNowCallsCompleted.countDown();
                return null;
            }).when(dataWriter).writeNow(any(BufferData.class));
            doAnswer(invocation -> {
                maybeFailWrites();
                maybeBlockWriteNow();
                initialWriteNowCallsCompleted.countDown();
                return null;
            }).when(dataWriter).writeNow(any(BufferData[].class));

            when(client.protocolConfig()).thenReturn(protocolConfig);
            when(client.clientConfig()).thenReturn(clientConfig);
            when(client.sendListener()).thenReturn(Http2FrameListener.create(List.of()));
            when(client.recvListener()).thenReturn(Http2FrameListener.create(List.of()));
            when(client.webClient()).thenReturn(webClient);
            when(webClient.executor()).thenReturn(connectionExecutor);
            when(clientConnection.reader()).thenReturn(DataReader.create(this::nextInboundFrame));
            when(clientConnection.writer()).thenReturn(dataWriter);
            when(clientConnection.helidonSocket()).thenReturn(socket);
            doAnswer(_ -> {
                transportClosed.set(true);
                failBlockedWrite();
                return null;
            }).when(clientConnection).closeResource();
            when(socket.socketId()).thenReturn("test-socket");
            when(socket.childSocketId()).thenReturn("0");
        }

        private Http2ClientConnection createConnection(boolean sendSettings) {
            return Http2ClientConnection.create(client, clientConnection, sendSettings);
        }

        private <T extends Http2ClientConnection> T createConnection(ConnectionFactory<T> connectionFactory,
                                                                     boolean sendSettings) {
            return Http2ClientConnection.create(connectionFactory.create(client, clientConnection), client, sendSettings);
        }

        private void offerInbound(Http2FrameData... frameData) {
            for (Http2FrameData oneFrame : frameData) {
                inboundFrames.add(serializeFrame(oneFrame));
            }
        }

        private void closeInbound() {
            inboundFrames.add(END_INBOUND);
        }

        private void assertConnectionClosed() {
            verify(clientConnection, timeout(TEST_WAIT_TIMEOUT.toMillis())).closeResource();
        }

        private void failWrites() {
            failWrites.set(true);
        }

        private void allowWrites() {
            failWrites.set(false);
        }

        private BlockedWrite blockNextWriteNow() {
            return blockNextWriteNow(false);
        }

        private BlockedWrite blockAndFailNextWriteNow() {
            return blockNextWriteNow(true);
        }

        private BlockedWrite blockNextWriteNow(boolean failAfterRelease) {
            BlockedWrite result = new BlockedWrite(failAfterRelease);
            if (!blockedWrite.compareAndSet(null, result)) {
                throw new IllegalStateException("A write is already blocked");
            }
            return result;
        }

        private void maybeFailWrites() {
            if (failWrites.get() || transportClosed.get()) {
                throw new UncheckedIOException(new IOException("expected test write failure"));
            }
        }

        private void maybeBlockWriteNow() {
            BlockedWrite block = blockedWrite.getAndSet(null);
            if (block != null) {
                activeBlockedWrite.set(block);
                try {
                    block.block();
                } finally {
                    activeBlockedWrite.compareAndSet(block, null);
                }
            }
        }

        private void failBlockedWrite() {
            BlockedWrite block = blockedWrite.get();
            if (block != null) {
                block.releaseAndFail();
            }
            block = activeBlockedWrite.get();
            if (block != null) {
                block.releaseAndFail();
            }
        }

        private byte[] nextInboundFrame() {
            try {
                byte[] frame = inboundFrames.take();
                if (frame == END_INBOUND) {
                    return null;
                }
                return frame;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        @Override
        public void close() {
            failBlockedWrite();
            connectionExecutor.shutdownNow();
        }

        private static final class BlockedWrite {
            private final CountDownLatch entered = new CountDownLatch(1);
            private final CountDownLatch released = new CountDownLatch(1);
            private final AtomicBoolean failAfterRelease;

            private BlockedWrite(boolean failAfterRelease) {
                this.failAfterRelease = new AtomicBoolean(failAfterRelease);
            }

            private boolean awaitEntered() throws InterruptedException {
                return entered.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }

            private void release() {
                released.countDown();
            }

            private void releaseAndFail() {
                failAfterRelease.set(true);
                release();
            }

            private void block() {
                entered.countDown();
                try {
                    if (!released.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                        throw new IllegalStateException("Timed out waiting for test to release blocked write");
                    }
                    if (failAfterRelease.get()) {
                        throw new UncheckedIOException(new IOException("expected blocked test write failure"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while blocking test write", e);
                }
            }
        }
    }

    private static final class HookedHttp2ClientConnection extends Http2ClientConnection {
        private final AtomicReference<Http2ClientStream> streamToClose;
        private final Runnable beforeDeliverHeaders;

        private HookedHttp2ClientConnection(Http2ClientImpl client,
                                            ClientConnection clientConnection,
                                            AtomicReference<Http2ClientStream> streamToClose,
                                            Runnable beforeDeliverHeaders) {
            super(client, clientConnection);
            this.streamToClose = streamToClose;
            this.beforeDeliverHeaders = beforeDeliverHeaders;
        }

        @Override
        void beforeDeliverInboundHeaders(Http2ClientStream stream, Http2Headers headers, boolean endOfStream) {
            if (stream == streamToClose.get()) {
                beforeDeliverHeaders.run();
            }
        }
    }
}
