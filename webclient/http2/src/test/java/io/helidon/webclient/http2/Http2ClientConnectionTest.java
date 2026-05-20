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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
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
    void initialSettingsFailureClosesConnection() {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            test.closeInbound();

            assertThrows(IllegalStateException.class, () -> test.createConnection(false));
            test.assertConnectionClosed();
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

            test.failWrites();
            assertThrows(UncheckedIOException.class, () -> failingStream.writeHeaders(requestHeaders(), false));
            test.allowWrites();

            Http2ClientStream recoveredStream = connection.tryStream(STREAM_CONFIG);
            assertNotNull(recoveredStream);
            recoveredStream.close();
            connection.close();
        }
    }

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

    private static final class MockedConnectionTestContext implements AutoCloseable {
        private static final byte[] END_INBOUND = new byte[0];

        private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();
        private final LinkedBlockingQueue<byte[]> inboundFrames = new LinkedBlockingQueue<>();
        private final AtomicBoolean failWrites = new AtomicBoolean();
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
                return null;
            }).when(dataWriter).writeNow(any(BufferData.class));
            doAnswer(invocation -> {
                maybeFailWrites();
                return null;
            }).when(dataWriter).writeNow(any(BufferData[].class));

            when(client.protocolConfig()).thenReturn(protocolConfig);
            when(client.clientConfig()).thenReturn(clientConfig);
            when(client.webClient()).thenReturn(webClient);
            when(webClient.executor()).thenReturn(connectionExecutor);
            when(clientConnection.reader()).thenReturn(DataReader.create(this::nextInboundFrame));
            when(clientConnection.writer()).thenReturn(dataWriter);
            when(clientConnection.helidonSocket()).thenReturn(socket);
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

        private void maybeFailWrites() {
            if (failWrites.get()) {
                throw new UncheckedIOException(new IOException("expected test write failure"));
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
            connectionExecutor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ConnectionFactory<T extends Http2ClientConnection> {
        T create(Http2ClientImpl client, ClientConnection clientConnection);
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
