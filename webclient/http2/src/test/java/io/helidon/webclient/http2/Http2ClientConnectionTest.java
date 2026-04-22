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
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanDecoder;
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
import static org.mockito.Mockito.when;

class Http2ClientConnectionTest {
    private static final Duration TEST_WAIT_TIMEOUT = Duration.ofSeconds(10);
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
    void readHeadersWaitsForExpectedDecodeOrder() throws Exception {
        try (MockedConnectionTestContext test = new MockedConnectionTestContext()) {
            Http2ClientConnection connection = test.newConnection();
            Http2ClientStream firstStream = mock(Http2ClientStream.class);
            Http2ClientStream secondStream = mock(Http2ClientStream.class);

            // RFC 7541 C.4 fixtures: the second block references a dynamic-table entry introduced by the first block.
            Http2FrameData firstHeaderBlock =
                    headerFrame(1, "828684418cf1e3c2e5f23a6ba0ab90f4ff");
            Http2FrameData secondHeaderBlock =
                    headerFrame(3, "828684be5886a8eb10649cbf");

            ExecutorService decodeExecutor = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch secondDecodeStarted = new CountDownLatch(1);
                CompletableFuture<Http2Headers> secondDecode = CompletableFuture.supplyAsync(() -> {
                    secondDecodeStarted.countDown();
                    return connection.readHeaders(secondStream,
                                                  Http2HuffmanDecoder.create(),
                                                  Http2Headers.create(WritableHeaders.create()),
                                                  2,
                                                  new Http2FrameData[] {secondHeaderBlock});
                }, decodeExecutor);

                assertTrue(secondDecodeStarted.await(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                assertThrows(TimeoutException.class, () -> secondDecode.get(200, TimeUnit.MILLISECONDS));

                Http2Headers firstHeaders = connection.readHeaders(firstStream,
                                                                   Http2HuffmanDecoder.create(),
                                                                   Http2Headers.create(WritableHeaders.create()),
                                                                   1,
                                                                   new Http2FrameData[] {firstHeaderBlock});

                assertThat(firstHeaders.authority(), is("www.example.com"));

                Http2Headers secondHeaders = secondDecode.get(TEST_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(secondHeaders.authority(), is("www.example.com"));
                assertThat(secondHeaders.httpHeaders().get(HeaderNames.CACHE_CONTROL).get(), is("no-cache"));
            } finally {
                decodeExecutor.shutdownNow();
            }
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

    private static Http2FrameData headerFrame(int streamId, String hexEncoded) {
        BufferData data = data(hexEncoded);
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.HEADERS,
                                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                                          streamId);
        return new Http2FrameData(header, data);
    }

    private static Http2FrameData settingsFrame(long maxConcurrentStreams) {
        Http2Settings settings = Http2Settings.builder()
                .add(Http2Setting.MAX_CONCURRENT_STREAMS, maxConcurrentStreams)
                .build();
        return settings.toFrameData(null, 0, Http2Flag.SettingsFlags.create(0));
    }

    private static BufferData data(String hexEncoded) {
        byte[] bytes = HexFormat.of().parseHex(hexEncoded.replace(" ", ""));
        return BufferData.create(bytes);
    }

    private static Http2Headers requestHeaders() {
        return Http2Headers.create(WritableHeaders.create())
                .method(Method.GET)
                .path("/")
                .authority("www.example.com");
    }

    private static byte[] serializeFrame(Http2FrameData frameData) {
        BufferData serialized = BufferData.create(frameData.header().write(), frameData.data().copy());
        byte[] bytes = new byte[serialized.available()];
        serialized.read(bytes);
        return bytes;
    }

    private static final class MockedConnectionTestContext implements AutoCloseable {
        private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();
        private final LinkedBlockingQueue<byte[]> inboundFrames = new LinkedBlockingQueue<>();
        private final AtomicBoolean failWrites = new AtomicBoolean();
        private final DataWriter dataWriter = mock(DataWriter.class);
        private final HelidonSocket socket;
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
            this.socket = mock(HelidonSocket.class);
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

        private Http2ClientConnection newConnection() {
            return new Http2ClientConnection(client, clientConnection);
        }

        private Http2ClientConnection createConnection(boolean sendSettings) {
            return Http2ClientConnection.create(client, clientConnection, sendSettings);
        }

        private void offerInbound(Http2FrameData frameData) {
            inboundFrames.add(serializeFrame(frameData));
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
                return inboundFrames.take();
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
}
