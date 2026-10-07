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

package io.helidon.webclient.grpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(20)
class GrpcClientCallTest {
    @Test
    void zeroDemandDoesNotCloseCallOrDeliverMessages() throws Exception {
        nonPositiveDemandDoesNotCloseCall(0);
    }

    @Test
    void negativeDemandThrowsWithoutClosingCall() throws Exception {
        nonPositiveDemandDoesNotCloseCall(-1);
    }

    @Test
    void peerResetClosesConnectionWithoutInterruptingListener() throws Exception {
        peerResetClosesConnection(false);
    }

    @Test
    void peerResetClosesTransportWhenGoAwayWriteFails() throws Exception {
        peerResetClosesConnection(true);
    }

    private static void nonPositiveDemandDoesNotCloseCall(int demand) throws Exception {
        var accepted = new CompletableFuture<Socket>();
        var sendSecond = new CompletableFuture<Void>();
        var secondSent = new CompletableFuture<Void>();
        var firstReceived = new CompletableFuture<String>();
        var secondReceived = new CompletableFuture<String>();
        var status = new CompletableFuture<Status>();
        var messageCount = new AtomicInteger();
        var closeCount = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             var listening = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listening.setSoTimeout(5000);
            var peer = executor.submit(() -> {
                try (Socket socket = listening.accept()) {
                    accepted.complete(socket);
                    socket.setSoTimeout(10000);
                    int streamId = sendStreamingResponse(socket);
                    sendSecond.get(5, TimeUnit.SECONDS);
                    sendResponseMessage(socket, streamId, "second response");
                    secondSent.complete(null);
                    socket.getInputStream().readAllBytes();
                }
                return null;
            });
            var client = GrpcClient.builder()
                    .baseUri("http://127.0.0.1:" + listening.getLocalPort())
                    .tls(tls -> tls.enabled(false))
                    .readTimeout(Duration.ofSeconds(10))
                    .protocolConfig(GrpcClientProtocolConfig.builder()
                                            .nextRequestWaitTime(Duration.ofSeconds(10))
                                            .build())
                    .build();
            var call = client.channel().newCall(descriptor(), CallOptions.DEFAULT);
            try {
                call.start(new ClientCall.Listener<>() {
                    @Override
                    public void onMessage(String message) {
                        if (messageCount.incrementAndGet() == 1) {
                            firstReceived.complete(message);
                        } else {
                            secondReceived.complete(message);
                        }
                    }

                    @Override
                    public void onClose(Status result, Metadata trailers) {
                        closeCount.incrementAndGet();
                        status.complete(result);
                    }
                }, new Metadata());
                call.request(1);
                assertThat(firstReceived.get(5, TimeUnit.SECONDS), is("response"));

                if (demand < 0) {
                    assertThrows(IllegalArgumentException.class, () -> call.request(demand));
                } else {
                    call.request(demand);
                }
                assertThat("non-positive demand does not close the call", status.isDone(), is(false));
                sendSecond.complete(null);
                secondSent.get(5, TimeUnit.SECONDS);
                assertThrows(TimeoutException.class, () -> secondReceived.get(250, TimeUnit.MILLISECONDS));
                assertThat("the second message still needs positive demand", messageCount.get(), is(1));
                assertThat("the call remains open while awaiting demand", status.isDone(), is(false));

                call.request(1);
                assertThat(secondReceived.get(5, TimeUnit.SECONDS), is("second response"));
                assertThat(messageCount.get(), is(2));
                assertThat("positive demand resumes the same call", status.isDone(), is(false));
                call.cancel("test complete", null);
                assertThat(status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.CANCELLED));
                assertThat(closeCount.get(), is(1));
                peer.get(5, TimeUnit.SECONDS);
            } finally {
                sendSecond.complete(null);
                if (accepted.isDone()) {
                    accepted.join().close();
                }
                call.cancel("test cleanup", null);
            }
        }
    }

    private static void peerResetClosesConnection(boolean failGoAway) throws Exception {
        var accepted = new CompletableFuture<Socket>();
        var reset = new CompletableFuture<Void>();
        var received = new CompletableFuture<String>();
        var status = new CompletableFuture<Status>();
        var closeCount = new AtomicInteger();
        var closeInterrupted = new AtomicBoolean();
        var failingConnection = new AtomicReference<FailingGoAwayConnection>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             var listening = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listening.setSoTimeout(5000);
            var peer = executor.submit(() -> {
                try (Socket socket = listening.accept()) {
                    accepted.complete(socket);
                    socket.setSoTimeout(10000);
                    int streamId = sendStreamingResponse(socket);
                    reset.get(5, TimeUnit.SECONDS);
                    var output = socket.getOutputStream();
                    Http2FrameHeader.create(4, Http2FrameTypes.RST_STREAM, Http2Flag.NoFlags.create(), streamId)
                            .write().writeTo(output);
                    BufferData errorCode = BufferData.create(4);
                    errorCode.writeInt32(Http2ErrorCode.CANCEL.code());
                    errorCode.writeTo(output);
                    output.flush();

                    // Keep the peer open until the client closes its dedicated connection.
                    List<Http2FrameType> frames = new ArrayList<>();
                    var input = socket.getInputStream();
                    while (true) {
                        byte[] bytes = input.readNBytes(Http2FrameHeader.LENGTH);
                        if (bytes.length == 0) {
                            return frames;
                        }
                        assertThat("complete HTTP/2 frame header", bytes.length, is(Http2FrameHeader.LENGTH));
                        Http2FrameHeader frame = Http2FrameHeader.create(BufferData.create(bytes));
                        assertThat("complete HTTP/2 frame payload", input.readNBytes(frame.length()).length, is(frame.length()));
                        frames.add(frame.type());
                    }
                }
            });
            var client = GrpcClient.builder()
                    .baseUri("http://127.0.0.1:" + listening.getLocalPort())
                    .tls(tls -> tls.enabled(false))
                    .readTimeout(Duration.ofSeconds(10))
                    .build();
            ClientCall<String, String> call;
            if (failGoAway) {
                call = new GrpcClientCall<>((GrpcChannel) client.channel(), descriptor(), CallOptions.DEFAULT) {
                    @Override
                    protected ClientConnection clientConnection(ClientUri uri, String authority) {
                        var connection = new FailingGoAwayConnection(super.clientConnection(uri, authority));
                        failingConnection.set(connection);
                        return connection;
                    }
                };
            } else {
                call = client.channel().newCall(descriptor(), CallOptions.DEFAULT);
            }
            try {
                call.start(new ClientCall.Listener<>() {
                    @Override
                    public void onMessage(String message) {
                        received.complete(message);
                    }

                    @Override
                    public void onClose(Status result, Metadata trailers) {
                        closeCount.incrementAndGet();
                        closeInterrupted.set(Thread.currentThread().isInterrupted());
                        status.complete(result);
                    }
                }, new Metadata());
                call.request(1);
                assertThat(received.get(5, TimeUnit.SECONDS), is("response"));
                reset.complete(null);

                assertThat(status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.UNKNOWN));
                assertThat("cleanup does not interrupt the listener thread", closeInterrupted.get(), is(false));
                List<Http2FrameType> frames = peer.get(5, TimeUnit.SECONDS);
                if (failGoAway) {
                    assertThat("the graceful close write failed", failingConnection.get().failedWrites.get(), is(1));
                    assertThat(frames, not(hasItem(Http2FrameType.GO_AWAY)));
                } else {
                    assertThat("graceful close writes GOAWAY before EOF", frames, hasItem(Http2FrameType.GO_AWAY));
                }
                call.cancel("repeated close", null);
                assertThat(closeCount.get(), is(1));
            } finally {
                reset.complete(null);
                if (accepted.isDone()) {
                    accepted.join().close();
                }
                call.cancel("test cleanup", null);
            }
        }
    }

    private static int sendStreamingResponse(Socket socket) throws IOException {
        var input = socket.getInputStream();
        var output = socket.getOutputStream();
        Http2FrameHeader.create(0, Http2FrameTypes.SETTINGS, Http2Flag.SettingsFlags.create(0), 0)
                .write().writeTo(output);
        assertThat(new String(input.readNBytes(24), StandardCharsets.US_ASCII),
                   is("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"));
        int streamId;
        while (true) {
            byte[] bytes = input.readNBytes(Http2FrameHeader.LENGTH);
            assertThat("complete HTTP/2 frame header", bytes.length, is(Http2FrameHeader.LENGTH));
            Http2FrameHeader frame = Http2FrameHeader.create(BufferData.create(bytes));
            assertThat("complete HTTP/2 frame payload", input.readNBytes(frame.length()).length, is(frame.length()));
            if (frame.type() == Http2FrameType.SETTINGS && !frame.flags(Http2FrameTypes.SETTINGS).ack()) {
                Http2FrameHeader.create(0, Http2FrameTypes.SETTINGS, Http2Flag.SettingsFlags.create(Http2Flag.ACK), 0)
                        .write().writeTo(output);
            } else if (frame.type() == Http2FrameType.HEADERS) {
                streamId = frame.streamId();
                break;
            }
        }
        // HPACK static index 8 is :status 200; index 31 names the literal content-type field.
        byte[] contentType = "application/grpc".getBytes(StandardCharsets.US_ASCII);
        Http2FrameHeader.create(4 + contentType.length, Http2FrameTypes.HEADERS,
                               Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS), streamId)
                .write().writeTo(output);
        output.write(new byte[] {(byte) 0x88, 0x0f, 0x10, (byte) contentType.length});
        output.write(contentType);
        sendResponseMessage(socket, streamId, "response");
        return streamId;
    }

    private static void sendResponseMessage(Socket socket, int streamId, String response) throws IOException {
        var output = socket.getOutputStream();
        byte[] message = response.getBytes(StandardCharsets.UTF_8);
        Http2FrameHeader.create(5 + message.length, Http2FrameTypes.DATA, Http2Flag.DataFlags.create(0), streamId)
                .write().writeTo(output);
        BufferData prefix = BufferData.create(5);
        prefix.writeInt8(0);
        prefix.writeUnsignedInt32(message.length);
        prefix.writeTo(output);
        output.write(message);
        output.flush();
    }

    private static MethodDescriptor<String, String> descriptor() {
        var marshaller = new MethodDescriptor.Marshaller<String>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                try {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                .setFullMethodName("test.Reset/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private static final class FailingGoAwayConnection implements ClientConnection {
        private final ClientConnection delegate;
        private final DataWriter writer;
        private final AtomicInteger failedWrites = new AtomicInteger();

        private FailingGoAwayConnection(ClientConnection delegate) {
            this.delegate = delegate;
            DataWriter delegateWriter = delegate.writer();
            this.writer = new DataWriter() {
                @Override
                public void write(BufferData... buffers) {
                    delegateWriter.write(buffers);
                }

                @Override
                public void write(BufferData buffer) {
                    delegateWriter.write(buffer);
                }

                @Override
                public void writeNow(BufferData... buffers) {
                    if (buffers.length > 0) {
                        rejectGoAway(buffers[0]);
                    }
                    delegateWriter.writeNow(buffers);
                }

                @Override
                public void writeNow(BufferData buffer) {
                    rejectGoAway(buffer);
                    delegateWriter.writeNow(buffer);
                }

                @Override
                public void flush() {
                    delegateWriter.flush();
                }
            };
        }

        @Override
        public DataReader reader() {
            return delegate.reader();
        }

        @Override
        public DataWriter writer() {
            return writer;
        }

        @Override
        public String channelId() {
            return delegate.channelId();
        }

        @Override
        public HelidonSocket helidonSocket() {
            return delegate.helidonSocket();
        }

        @Override
        public void readTimeout(Duration readTimeout) {
            delegate.readTimeout(readTimeout);
        }

        @Override
        public void closeResource() {
            delegate.closeResource();
        }

        private void rejectGoAway(BufferData buffer) {
            if (buffer.available() >= Http2FrameHeader.LENGTH && buffer.get(3) == Http2FrameType.GO_AWAY.type()) {
                failedWrites.incrementAndGet();
                throw new UncheckedIOException(new IOException("Injected GOAWAY write failure"));
            }
        }
    }
}
