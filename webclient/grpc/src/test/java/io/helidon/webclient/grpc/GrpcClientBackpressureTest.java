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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

@Timeout(20)
class GrpcClientBackpressureTest {
    @ParameterizedTest
    @EnumSource(HeartbeatOutcome.class)
    void successfulResponseCompletesAfterRequestEndWithBlockedHeartbeat(HeartbeatOutcome outcome) throws Exception {
        var transport = new CompletableFuture<BlockingConnection>();
        var accepted = new CompletableFuture<Socket>();
        var headersReceived = new CompletableFuture<Void>();
        var requestEnded = new CompletableFuture<Void>();
        var respond = new CompletableFuture<Void>();
        var status = new CompletableFuture<Status>();
        var trailers = new CompletableFuture<Metadata>();
        var closeCount = new AtomicInteger();
        var executor = Executors.newSingleThreadExecutor();
        var peerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        try (executor;
             peerExecutor;
             var listening = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listening.setSoTimeout(5000);
            var peer = peerExecutor.submit(() -> {
                try (Socket socket = listening.accept()) {
                    accepted.complete(socket);
                    socket.setSoTimeout(15000);
                    var output = socket.getOutputStream();
                    output.write(new byte[] {0, 0, 0, 4, 0, 0, 0, 0, 0});
                    InputStream input = socket.getInputStream();
                    readInitialHeaders(input);
                    headersReceived.complete(null);
                    boolean endOfStream = false;
                    while (!endOfStream) {
                        byte[] bytes = input.readNBytes(Http2FrameHeader.LENGTH);
                        assertThat("complete request frame header", bytes.length, is(Http2FrameHeader.LENGTH));
                        Http2FrameHeader frame = Http2FrameHeader.create(BufferData.create(bytes));
                        assertThat("complete request frame payload", input.readNBytes(frame.length()).length,
                                   is(frame.length()));
                        endOfStream = frame.type() == Http2FrameType.DATA
                                && frame.flags(Http2FrameTypes.DATA).endOfStream();
                    }
                    requestEnded.complete(null);
                    respond.get(5, TimeUnit.SECONDS);
                    var headers = BufferData.create(256);
                    headers.writeInt8(0x88); // HPACK static :status 200
                    writeLiteralHeader(headers, "content-type", "application/grpc");
                    writeLiteralHeader(headers, "grpc-status", "0");
                    writeLiteralHeader(headers, "grpc-message", "completed");
                    writeLiteralHeader(headers, "response-detail", "custom trailers");
                    output.write(new byte[] {0, 0, (byte) headers.available(), 1, 5, 0, 0, 0, 1});
                    headers.writeTo(output);
                    output.flush();
                    // Retain TCP until client cleanup, including while the heartbeat cannot write.
                    return input.readAllBytes();
                } catch (Throwable t) {
                    headersReceived.completeExceptionally(t);
                    requestEnded.completeExceptionally(t);
                    throw t;
                }
            });
            var client = GrpcClient.builder()
                    .baseUri("http://127.0.0.1:" + listening.getLocalPort())
                    .tls(tls -> tls.enabled(false))
                    .executor(executor)
                    .protocolConfig(config -> config.heartbeatPeriod(outcome == HeartbeatOutcome.BLOCKED
                                                                            ? Duration.ofMillis(20) : Duration.ZERO))
                    .readTimeout(Duration.ofSeconds(10))
                    .build();
            var method = descriptor().toBuilder().setType(MethodDescriptor.MethodType.BIDI_STREAMING).build();
            var call = new GrpcClientCall<byte[], byte[]>((GrpcChannel) client.channel(), method, CallOptions.DEFAULT) {
                @Override
                protected ClientConnection clientConnection(ClientUri uri, String authority) {
                    var connection = new BlockingConnection(super.clientConnection(uri, authority));
                    transport.complete(connection);
                    return connection;
                }
            };
            try {
                call.start(new ClientCall.Listener<>() {
                    @Override
                    public void onClose(Status result, Metadata metadata) {
                        closeCount.incrementAndGet();
                        trailers.complete(metadata);
                        status.complete(result);
                    }
                }, new Metadata());
                headersReceived.get(5, TimeUnit.SECONDS);
                call.request(1);
                call.sendMessage(new byte[32]);
                call.halfClose();
                requestEnded.get(5, TimeUnit.SECONDS);
                BlockingConnection connection = transport.get(5, TimeUnit.SECONDS);
                if (outcome == HeartbeatOutcome.BLOCKED) {
                    // Arm only after the peer observes END_STREAM: no request DATA can hit this gate.
                    connection.blockHeartbeatWrites = true;
                    Thread writer = connection.writeStarted.get(5, TimeUnit.SECONDS);
                    assertThat("the configured platform executor owns the blocked heartbeat", writer.isVirtual(), is(false));
                    assertThat("the heartbeat remains blocked before the response", connection.transportClosed.isDone(), is(false));
                } else {
                    // END_STREAM has reached the peer; this queued task also proves the writer task has exited.
                    executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
                }
                respond.complete(null);
                Status result = status.get(5, TimeUnit.SECONDS);
                assertThat(result.getCode(), is(Status.Code.OK));
                assertThat(result.getDescription(), is("completed"));
                assertThat(trailers.get(5, TimeUnit.SECONDS)
                                   .get(Metadata.Key.of("response-detail", Metadata.ASCII_STRING_MARSHALLER)),
                           is("custom trailers"));
                if (outcome == HeartbeatOutcome.BLOCKED) {
                    assertThat("transport close releases the blocked heartbeat", connection.transportClosed.isDone(), is(true));
                }
                byte[] closingFrames = peer.get(5, TimeUnit.SECONDS);
                boolean gracefulClose = false;
                var frames = BufferData.create(closingFrames);
                while (frames.available() >= Http2FrameHeader.LENGTH) {
                    Http2FrameHeader frame = Http2FrameHeader.create(frames);
                    if (frame.type() == Http2FrameType.GO_AWAY) {
                        gracefulClose = true;
                    }
                    frames.skip(frame.length());
                }
                assertThat("idle completion retains graceful GOAWAY; a blocked heartbeat requires transport abort",
                           gracefulClose, is(outcome == HeartbeatOutcome.IDLE));
                call.cancel("repeated close", null);
                assertThat(closeCount.get(), is(1));
            } finally {
                respond.complete(null);
                if (transport.isDone()) {
                    transport.join().closeResource();
                }
                if (accepted.isDone()) {
                    accepted.join().close();
                }
                call.cancel("test cleanup", null);
            }
        }
        assertThat("all configured executor tasks have terminated", executor.isTerminated(), is(true));
        assertThat("all peer executor tasks have terminated", peerExecutor.isTerminated(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ResponseOutcome.class)
    void serverResponseAbortsTransportBeforeWaitingForBlockedStreamingWriter(ResponseOutcome outcome) throws Exception {
        var nanos = new AtomicLong();
        var deadline = Deadline.after(1, TimeUnit.SECONDS, new Deadline.Ticker() {
            @Override
            public long nanoTime() {
                return nanos.get();
            }
        });
        CallOptions options = outcome == ResponseOutcome.OK_WITH_DEADLINE
                ? CallOptions.DEFAULT.withDeadline(deadline) : CallOptions.DEFAULT;
        var transport = new CompletableFuture<BlockingConnection>();
        var accepted = new CompletableFuture<Socket>();
        var headersReceived = new CompletableFuture<Void>();
        var reject = new CompletableFuture<Void>();
        var status = new CompletableFuture<Status>();
        var trailers = new CompletableFuture<Metadata>();
        var closeCount = new AtomicInteger();
        var deadlineExpiredAtClose = new AtomicBoolean();
        var received = new CompletableFuture<byte[]>();
        List<String> events = new ArrayList<>();
        var executor = Executors.newCachedThreadPool();
        try (executor;
             var listening = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listening.setSoTimeout(5000);
            var peer = executor.submit(() -> {
                try (Socket socket = listening.accept()) {
                    accepted.complete(socket);
                    socket.setSoTimeout(15000);
                    var output = socket.getOutputStream();
                    output.write(new byte[] {0, 0, 0, 4, 0, 0, 0, 0, 0});
                    readInitialHeaders(socket.getInputStream());
                    headersReceived.complete(null);
                    reject.get(5, TimeUnit.SECONDS);
                    var headers = BufferData.create(256);
                    headers.writeInt8(0x88); // HPACK static :status 200
                    writeLiteralHeader(headers, "content-type", "application/grpc");
                    output.write(new byte[] {0, 0, (byte) headers.available(), 1, 4, 0, 0, 0, 1});
                    headers.writeTo(output);
                    byte[] response = "response".getBytes(StandardCharsets.US_ASCII);
                    output.write(new byte[] {0, 0, 13, 0, 0, 0, 0, 0, 1});
                    output.write(new byte[] {0, 0, 0, 0, 8});
                    output.write(response);
                    var trailingHeaders = BufferData.create(256);
                    writeLiteralHeader(trailingHeaders, "grpc-status", outcome == ResponseOutcome.UNAVAILABLE ? "14" : "0");
                    if (outcome == ResponseOutcome.UNAVAILABLE) {
                        writeLiteralHeader(trailingHeaders, "grpc-message", "busy");
                    }
                    writeLiteralHeader(trailingHeaders, "response-detail", "custom trailers");
                    output.write(new byte[] {0, 0, (byte) trailingHeaders.available(), 1, 5, 0, 0, 0, 1});
                    trailingHeaders.writeTo(output);
                    output.flush();
                    // Keep the peer open until client cleanup closes the connection.
                    socket.getInputStream().readAllBytes();
                }
                return null;
            });
            var client = GrpcClient.builder()
                    .baseUri("http://127.0.0.1:" + listening.getLocalPort())
                    .tls(tls -> tls.enabled(false))
                    .executor(executor)
                    .readTimeout(Duration.ofSeconds(10))
                    .build();
            var method = descriptor().toBuilder().setType(MethodDescriptor.MethodType.BIDI_STREAMING).build();
            var call = new GrpcClientCall<byte[], byte[]>((GrpcChannel) client.channel(), method, options) {
                @Override
                protected ClientConnection clientConnection(ClientUri uri, String authority) {
                    var connection = new BlockingConnection(super.clientConnection(uri, authority));
                    transport.complete(connection);
                    return connection;
                }
            };
            try {
                call.start(new ClientCall.Listener<>() {
                    @Override
                    public void onMessage(byte[] message) {
                        events.add("message");
                        received.complete(message);
                    }

                    @Override
                    public void onClose(Status result, Metadata metadata) {
                        events.add("close");
                        deadlineExpiredAtClose.set(deadline.isExpired());
                        closeCount.incrementAndGet();
                        trailers.complete(metadata);
                        status.complete(result);
                    }
                }, new Metadata());
                headersReceived.get(5, TimeUnit.SECONDS);
                BlockingConnection connection = transport.get(5, TimeUnit.SECONDS);
                // Advance the logical clock during cleanup, after the server outcome wins.
                // A registered deadline may expire before onClose without replacing that outcome;
                // the frozen startup clock prevents an unrelated real-time setup failure.
                connection.transportClosed.thenRun(() -> nanos.set(TimeUnit.SECONDS.toNanos(2)));
                connection.blockWrites = true;
                call.request(1);
                call.sendMessage(new byte[32]);
                Thread writer = connection.writeStarted.get(5, TimeUnit.SECONDS);
                assertThat("the configured platform executor owns the blocked write", writer.isVirtual(), is(false));
                assertThat("the writer is blocked before the server rejection", connection.transportClosed.isDone(), is(false));
                reject.complete(null);

                assertThat(new String(received.get(5, TimeUnit.SECONDS), StandardCharsets.US_ASCII), is("response"));
                Status result = status.get(5, TimeUnit.SECONDS);
                assertThat(result.getCode(), is(outcome == ResponseOutcome.UNAVAILABLE ? Status.Code.UNAVAILABLE : Status.Code.OK));
                assertThat(result.getDescription(), is(outcome == ResponseOutcome.UNAVAILABLE ? "busy" : null));
                assertThat(trailers.get(5, TimeUnit.SECONDS)
                                   .get(Metadata.Key.of("response-detail", Metadata.ASCII_STRING_MARSHALLER)),
                           is("custom trailers"));
                assertThat(events, is(List.of("message", "close")));
                assertThat("the logical deadline expires during cleanup before onClose", deadlineExpiredAtClose.get(), is(true));
                assertThat("transport close releases the blocked writer", connection.transportClosed.isDone(), is(true));
                peer.get(5, TimeUnit.SECONDS);
                call.cancel("repeated close", null);
                assertThat(closeCount.get(), is(1));
            } finally {
                reject.complete(null);
                if (transport.isDone()) {
                    transport.join().closeResource();
                }
                if (accepted.isDone()) {
                    accepted.join().close();
                }
                call.cancel("test cleanup", null);
            }
        }
        assertThat("all configured executor tasks have terminated", executor.isTerminated(), is(true));
    }

    @ParameterizedTest
    @EnumSource(CancellationSource.class)
    void cancellationAbortsTransportBeforeWaitingForBlockedWriter(CancellationSource source) throws Exception {
        var nanos = new AtomicLong();
        var deadline = Deadline.after(1, TimeUnit.SECONDS, new Deadline.Ticker() {
            @Override
            public long nanoTime() {
                return nanos.get();
            }
        });
        CallOptions options = source == CancellationSource.DEADLINE
                ? CallOptions.DEFAULT.withDeadline(deadline) : CallOptions.DEFAULT;
        var transport = new CompletableFuture<BlockingConnection>();
        var accepted = new CompletableFuture<Socket>();
        var headersReceived = new CompletableFuture<Void>();
        var status = new CompletableFuture<Status>();
        var closeCount = new AtomicInteger();
        try (var context = Context.current().withCancellation();
             var executor = Executors.newVirtualThreadPerTaskExecutor();
             var listening = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listening.setSoTimeout(5000);
            var peer = executor.submit(() -> {
                try (Socket socket = listening.accept()) {
                    accepted.complete(socket);
                    socket.setSoTimeout(5000);
                    socket.getOutputStream().write(new byte[] {0, 0, 0, 4, 0, 0, 0, 0, 0});
                    readInitialHeaders(socket.getInputStream());
                    headersReceived.complete(null);
                    socket.setSoTimeout(15000);
                    socket.getInputStream().readAllBytes();
                } catch (Throwable t) {
                    headersReceived.completeExceptionally(t);
                    throw t;
                }
                return null;
            });
            var client = GrpcClient.builder()
                    .baseUri("http://127.0.0.1:" + listening.getLocalPort())
                    .tls(tls -> tls.enabled(false))
                    .readTimeout(Duration.ofSeconds(10))
                    .build();
            var call = context.call(() -> new GrpcUnaryClientCall<byte[], byte[]>((GrpcChannel) client.channel(),
                                                                                  descriptor(), options) {
                @Override
                protected ClientConnection clientConnection(ClientUri uri, String authority) {
                    var connection = new BlockingConnection(super.clientConnection(uri, authority));
                    transport.complete(connection);
                    return connection;
                }
            });
            try {
                call.start(new ClientCall.Listener<>() {
                    @Override
                    public void onClose(Status result, Metadata trailers) {
                        closeCount.incrementAndGet();
                        status.complete(result);
                    }
                }, new Metadata());
                BlockingConnection connection = transport.get(5, TimeUnit.SECONDS);
                headersReceived.get(5, TimeUnit.SECONDS);
                connection.blockWrites = true;
                var senderThread = new CompletableFuture<Thread>();
                var sender = executor.submit(() -> {
                    senderThread.complete(Thread.currentThread());
                    call.sendMessage(new byte[32]);
                    call.halfClose();
                    return Thread.currentThread().isInterrupted();
                });
                assertThat("the application sender owns the blocked transport write",
                           connection.writeStarted.get(5, TimeUnit.SECONDS),
                           sameInstance(senderThread.get(5, TimeUnit.SECONDS)));
                assertThat("call remains open while its transport write is blocked", status.isDone(), is(false));

                var cancellation = executor.submit(() -> {
                    switch (source) {
                        case DEADLINE -> nanos.set(TimeUnit.SECONDS.toNanos(1));
                        case CALL -> call.cancel("explicit cancellation", null);
                        case CONTEXT -> context.cancel(null);
                    }
                    return Thread.currentThread().isInterrupted();
                });

                Status.Code expected = source == CancellationSource.DEADLINE
                        ? Status.Code.DEADLINE_EXCEEDED : Status.Code.CANCELLED;
                assertThat(status.get(5, TimeUnit.SECONDS).getCode(), is(expected));
                assertThat("sender thread is not interrupted", sender.get(5, TimeUnit.SECONDS), is(false));
                assertThat("cancelling thread is not interrupted", cancellation.get(5, TimeUnit.SECONDS), is(false));
                peer.get(5, TimeUnit.SECONDS);
                call.cancel("repeated cancellation", null);
                assertThat(closeCount.get(), is(1));
            } finally {
                // Release the writer even when testing an implementation that deadlocks in stream cancellation.
                if (transport.isDone()) {
                    transport.join().closeResource();
                }
                if (accepted.isDone()) {
                    accepted.join().close();
                }
                call.cancel("test cleanup", null);
            }
        }
    }

    private static void readInitialHeaders(InputStream input) throws IOException {
        assertThat(new String(input.readNBytes(24), StandardCharsets.US_ASCII),
                   is("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"));
        boolean headersReceived = false;
        boolean settingsAcknowledged = false;
        // The initial settings latch can be released before its ACK is written. Await both frames
        // so the later write barrier can only be reached by the application's request DATA.
        while (!headersReceived || !settingsAcknowledged) {
            byte[] bytes = input.readNBytes(Http2FrameHeader.LENGTH);
            assertThat("complete HTTP/2 frame header", bytes.length, is(Http2FrameHeader.LENGTH));
            Http2FrameHeader frame = Http2FrameHeader.create(BufferData.create(bytes));
            assertThat("complete HTTP/2 frame payload", input.readNBytes(frame.length()).length, is(frame.length()));
            if (frame.type() == Http2FrameType.HEADERS) {
                headersReceived = true;
            } else if (frame.type() == Http2FrameType.SETTINGS && frame.flags(Http2FrameTypes.SETTINGS).ack()) {
                settingsAcknowledged = true;
            }
        }
    }

    private static void writeLiteralHeader(BufferData headers, String name, String value) {
        headers.writeInt8(0);
        headers.writeInt8(name.length());
        headers.write(name.getBytes(StandardCharsets.US_ASCII));
        headers.writeInt8(value.length());
        headers.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static MethodDescriptor<byte[], byte[]> descriptor() {
        var marshaller = new MethodDescriptor.Marshaller<byte[]>() {
            @Override
            public InputStream stream(byte[] value) {
                return new ByteArrayInputStream(value);
            }

            @Override
            public byte[] parse(InputStream stream) {
                try {
                    return stream.readAllBytes();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        return MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.Backpressure/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private enum CancellationSource {
        DEADLINE,
        CALL,
        CONTEXT
    }

    private enum ResponseOutcome {
        OK,
        OK_WITH_DEADLINE,
        UNAVAILABLE
    }

    private enum HeartbeatOutcome {
        BLOCKED,
        IDLE
    }

    private static final class BlockingConnection implements ClientConnection {
        private final ClientConnection delegate;
        private final DataWriter writer;
        private final CompletableFuture<Thread> writeStarted = new CompletableFuture<>();
        private final CompletableFuture<Void> transportClosed = new CompletableFuture<>();

        private volatile boolean blockWrites;
        private volatile boolean blockHeartbeatWrites;

        private BlockingConnection(ClientConnection delegate) {
            this.delegate = delegate;
            DataWriter delegateWriter = delegate.writer();
            this.writer = new DataWriter() {
                @Override
                public void write(BufferData... buffers) {
                    awaitWritable(buffers);
                    delegateWriter.write(buffers);
                }

                @Override
                public void write(BufferData buffer) {
                    awaitWritable(buffer);
                    delegateWriter.write(buffer);
                }

                @Override
                public void writeNow(BufferData... buffers) {
                    awaitWritable(buffers);
                    delegateWriter.writeNow(buffers);
                }

                @Override
                public void writeNow(BufferData buffer) {
                    awaitWritable(buffer);
                    delegateWriter.writeNow(buffer);
                }

                @Override
                public void flush() {
                    awaitWritable();
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
            try {
                delegate.closeResource();
            } finally {
                transportClosed.complete(null);
            }
        }

        private void awaitWritable(BufferData... buffers) {
            boolean heartbeat = buffers.length > 0
                    && buffers[0].available() >= Http2FrameHeader.LENGTH
                    && buffers[0].get(3) == 6; // HTTP/2 PING frame type
            if (blockWrites || (blockHeartbeatWrites && heartbeat)) {
                writeStarted.complete(Thread.currentThread());
                // Model TCP backpressure while the HTTP/2 writer owns its lock. Closing the
                // underlying transport releases the write without interrupting the application.
                transportClosed.join();
            }
        }
    }
}
