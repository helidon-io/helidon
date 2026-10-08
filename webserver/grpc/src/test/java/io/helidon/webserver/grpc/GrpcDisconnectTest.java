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

package io.helidon.webserver.grpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.task.InterruptableTask;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanDecoder;
import io.helidon.http.http2.Http2HuffmanEncoder;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2Util;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@Timeout(15)
class GrpcDisconnectTest {
    @Test
    void deadlineDuringSocketWritePreservesSiblingAndSubsequentCalls() throws Exception {
        var cancelled = new CompletableFuture<Context>();
        var writing = new CompletableFuture<Void>();
        var finished = new CompletableFuture<Void>();
        var responseThread = new AtomicReference<Thread>();
        var dataThread = new AtomicReference<Thread>();
        String largeResponse = "x".repeat(8 * 1024 * 1024);
        var service = ServerServiceDefinition.builder("test.Disconnect")
                .addMethod(descriptor("Large"), (call, _) -> {
                    Context.current().addListener(cancelled::complete, Runnable::run);
                    return new ServerCall.Listener<>() {
                        @Override
                        public void onHalfClose() {
                            responseThread.set(Thread.currentThread());
                            assertThat("response callback uses a virtual stream thread", Thread.currentThread().isVirtual(), is(true));
                            call.sendHeaders(new Metadata());
                            writing.complete(null);
                            try {
                                call.sendMessage(largeResponse);
                                call.close(Status.OK, new Metadata());
                            } finally {
                                finished.complete(null);
                            }
                        }
                    };
                })
                .addMethod(descriptor("Small"), (call, _) -> new ServerCall.Listener<>() {
                    @Override
                    public void onHalfClose() {
                        // The sibling responds only once the other RPC has expired.
                        cancelled.join();
                        call.sendHeaders(new Metadata());
                        call.sendMessage("small response");
                        call.close(Status.OK, new Metadata());
                    }
                })
                .build();
        var grpcSelector = GrpcProtocolSelector.create(GrpcConfig.create());
        Http2SubProtocolSelector checkedGrpcSelector = (ctx, prologue, headers, writer, streamId, serverSettings,
                                                      clientSettings, flowControl, state, router) ->
                grpcSelector.subProtocol(ctx, prologue, headers,
                                         streamId == 1 ? new InlineDataWriter(writer, responseThread, dataThread) : writer,
                                         streamId, serverSettings, clientSettings, flowControl, state, router);
        var http2Selector = Http2ConnectionSelector.builder()
                .http2Config(Http2Config.create())
                .addSubProtocolSelector(checkedGrpcSelector)
                .build();
        var readers = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().daemon().name("grpc-test-reader-", 1).factory());
        // JDK-8334574 loses socket readiness events on Windows when virtual readers and writers share a socket.
        // Only this fixture's connection reader uses a platform thread; stream callbacks and DATA writes stay inline.
        ServerConnectionSelector selector = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                ? new PlatformReaderSelector(http2Selector, readers)
                : http2Selector;
        var server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .connectionOptions(options -> options.socketSendBufferSize(64 * 1024))
                .addConnectionSelector(selector)
                .addRouting(GrpcRouting.builder().config(Config.empty()).service(service))
                .build()
                .start();
        try (var socket = new Socket()) {
            socket.setReceiveBufferSize(64 * 1024);
            socket.connect(new InetSocketAddress("127.0.0.1", server.port()));
            socket.setSoTimeout(5000);
            var output = socket.getOutputStream();
            output.write(Http2Util.prefaceData().readBytes());
            // Both windows exceed the entire response: the blocked writer must be in socket I/O.
            var settings = Http2Settings.builder().add(Http2Setting.INITIAL_WINDOW_SIZE, 16L * 1024 * 1024).build();
            writeFrame(socket, settings.toFrameData(Http2Settings.create(), 0, Http2Flag.SettingsFlags.create(0)));
            var increment = BufferData.create(4);
            increment.writeInt32(16 * 1024 * 1024 - 65535);
            writeFrame(socket, new Http2FrameData(Http2FrameHeader.create(4, Http2FrameTypes.WINDOW_UPDATE,
                                                                        Http2Flag.NoFlags.create(), 0), increment));
            startCall(socket, 1, "Large", "2S");
            startCall(socket, 3, "Small", null);
            writing.get(5, TimeUnit.SECONDS);

            var responseTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            var decoder = Http2HuffmanDecoder.create();
            Http2FrameData frame;
            do {
                frame = readFrame(socket);
                if (frame.header().type() == Http2FrameType.HEADERS) {
                    Http2Headers.create(null, responseTable, decoder, frame);
                }
            } while (frame.header().type() != Http2FrameType.DATA);
            assertThat("large response started on stream 1", frame.header().streamId(), is(1));
            assertThat("DATA uses the response callback's stream thread", dataThread.get(), is(responseThread.get()));

            // Pause reads only after observing response DATA; resume when the deadline actually fires.
            assertThat(cancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
            assertThat("large response cannot finish while the peer is not reading", finished.isDone(), is(false));

            var siblingBody = new ByteArrayOutputStream();
            int expiredStatus = -1;
            int siblingStatus = -1;
            while (expiredStatus < 0 || siblingStatus < 0) {
                frame = readFrame(socket);
                int streamId = frame.header().streamId();
                if (frame.header().type() == Http2FrameType.DATA && streamId == 3) {
                    siblingBody.write(frame.data().readBytes());
                } else if (frame.header().type() == Http2FrameType.HEADERS) {
                    var headers = Http2Headers.create(null, responseTable, decoder, frame).httpHeaders();
                    if (frame.header().flags(Http2FrameTypes.HEADERS).endOfStream()) {
                        int status = headers.get(HeaderNames.create("grpc-status")).asInt().get();
                        if (streamId == 1) {
                            expiredStatus = status;
                        } else if (streamId == 3) {
                            siblingStatus = status;
                        }
                    }
                }
            }
            assertThat("expired RPC status", expiredStatus, is(4));
            assertThat("sibling RPC status", siblingStatus, is(0));
            assertResponse(siblingBody);
            finished.get(5, TimeUnit.SECONDS);

            startCall(socket, 5, "Small", null);
            var subsequentBody = new ByteArrayOutputStream();
            int subsequentStatus = -1;
            while (subsequentStatus < 0) {
                frame = readFrame(socket);
                if (frame.header().type() == Http2FrameType.DATA && frame.header().streamId() == 5) {
                    subsequentBody.write(frame.data().readBytes());
                } else if (frame.header().type() == Http2FrameType.HEADERS) {
                    var headers = Http2Headers.create(null, responseTable, decoder, frame).httpHeaders();
                    if (frame.header().streamId() == 5 && frame.header().flags(Http2FrameTypes.HEADERS).endOfStream()) {
                        subsequentStatus = headers.get(HeaderNames.create("grpc-status")).asInt().get();
                    }
                }
            }
            assertThat("subsequent RPC status on the same connection", subsequentStatus, is(0));
            assertResponse(subsequentBody);
        } finally {
            cancelled.complete(Context.ROOT);
            try {
                server.stop();
            } finally {
                readers.shutdownNow();
                assertThat("fixture connection readers stopped", readers.awaitTermination(5, TimeUnit.SECONDS), is(true));
            }
        }
    }

    @Test
    void disconnectCancelsIdleCallWithLongDeadline() throws Exception {
        var started = new CompletableFuture<Context>();
        var contextCancelled = new CompletableFuture<Context>();
        var listenerCancelled = new CompletableFuture<Context>();
        var cancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var service = ServerServiceDefinition.builder("test.Disconnect")
                .addMethod(descriptor("Call"), (_, _) -> {
                    Context.current().addListener(contextCancelled::complete, Runnable::run);
                    return new ServerCall.Listener<>() {
                        @Override
                        public void onReady() {
                            started.complete(Context.current());
                        }

                        @Override
                        public void onCancel() {
                            cancellations.incrementAndGet();
                            listenerCancelled.complete(Context.current());
                        }

                        @Override
                        public void onComplete() {
                            completions.incrementAndGet();
                        }
                    };
                })
                .build();
        var server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .addRouting(GrpcRouting.builder().config(Config.empty()).service(service))
                .build()
                .start();
        try {
            try (var socket = new Socket("127.0.0.1", server.port())) {
                startCall(socket);
                Context context = started.get(5, TimeUnit.SECONDS);
                assertThat(context.getDeadline(), notNullValue());
                assertThat(context.getDeadline().timeRemaining(TimeUnit.MINUTES), greaterThan(30L));
                assertThat(context.isCancelled(), is(false));
            }

            // Closing TCP, without RST_STREAM or any request DATA, must release the call before its deadline.
            assertThat(contextCancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
            assertThat(listenerCancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
            assertThat(cancellations.get(), is(1));
            assertThat(completions.get(), is(0));
        } finally {
            server.stop();
        }
    }

    private static void startCall(Socket socket) throws IOException {
        var output = socket.getOutputStream();
        output.write(Http2Util.prefaceData().readBytes());
        var settings = Http2Settings.create().toFrameData(Http2Settings.create(), 0, Http2Flag.SettingsFlags.create(0));
        output.write(settings.header().write().readBytes());
        output.write(settings.data().readBytes());

        var writable = WritableHeaders.create()
                .set(HeaderNames.CONTENT_TYPE, "application/grpc")
                .set(HeaderNames.TE, "trailers")
                .set(HeaderNames.create("grpc-timeout"), "1H");
        var headers = Http2Headers.create(writable);
        headers.method(Method.POST);
        headers.path("/test.Disconnect/Call");
        headers.scheme("http");
        headers.authority("localhost");
        var encoded = BufferData.growing(512);
        headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                      Http2HuffmanEncoder.create(), encoded);
        var header = Http2FrameHeader.create(encoded.available(), Http2FrameTypes.HEADERS,
                                              Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS), 1);
        output.write(header.write().readBytes());
        output.write(encoded.readBytes());
        output.flush();
    }

    private static void startCall(Socket socket, int streamId, String method, String timeout) throws IOException {
        var writable = WritableHeaders.create()
                .set(HeaderNames.CONTENT_TYPE, "application/grpc")
                .set(HeaderNames.TE, "trailers");
        if (timeout != null) {
            writable.set(HeaderNames.create("grpc-timeout"), timeout);
        }
        var headers = Http2Headers.create(writable);
        headers.method(Method.POST);
        headers.path("/test.Disconnect/" + method);
        headers.scheme("http");
        headers.authority("localhost");
        var encoded = BufferData.growing(512);
        headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                      Http2HuffmanEncoder.create(), encoded);
        writeFrame(socket, new Http2FrameData(Http2FrameHeader.create(encoded.available(), Http2FrameTypes.HEADERS,
                Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM), streamId), encoded));
    }

    private static void writeFrame(Socket socket, Http2FrameData frame) throws IOException {
        var output = socket.getOutputStream();
        output.write(frame.header().write().readBytes());
        output.write(frame.data().readBytes());
        output.flush();
    }

    private static Http2FrameData readFrame(Socket socket) throws IOException {
        var input = socket.getInputStream();
        byte[] headerBytes = input.readNBytes(Http2FrameHeader.LENGTH);
        assertThat("connection remains open for a complete frame header", headerBytes.length, is(Http2FrameHeader.LENGTH));
        var header = Http2FrameHeader.create(BufferData.create(headerBytes));
        byte[] payload = input.readNBytes(header.length());
        assertThat("complete frame payload for stream " + header.streamId(), payload.length, is(header.length()));
        assertThat("no connection-wide GOAWAY", header.type(), not(Http2FrameType.GO_AWAY));
        assertThat("no reset for stream " + header.streamId(), header.type(), not(Http2FrameType.RST_STREAM));
        if (header.type() == Http2FrameType.SETTINGS && !header.flags(Http2FrameTypes.SETTINGS).ack()) {
            writeFrame(socket, Http2Settings.create().toFrameData(Http2Settings.create(), 0,
                                                                Http2Flag.SettingsFlags.create(Http2Flag.ACK)));
        }
        return new Http2FrameData(header, BufferData.create(payload));
    }

    private static void assertResponse(ByteArrayOutputStream bytes) {
        var message = BufferData.create(bytes.toByteArray());
        assertThat("uncompressed response", message.read(), is(0));
        assertThat("response message length", message.readUnsignedInt32(), is(14L));
        assertThat("complete response payload", new String(message.readBytes(), StandardCharsets.UTF_8), is("small response"));
    }

    private static MethodDescriptor<String, String> descriptor(String method) {
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
                .setFullMethodName("test.Disconnect/" + method)
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private record InlineDataWriter(Http2StreamWriter delegate,
                                    AtomicReference<Thread> responseThread,
                                    AtomicReference<Thread> dataThread) implements Http2StreamWriter {
        @Override
        public void write(Http2FrameData frame) {
            delegate.write(frame);
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            dataThread.set(Thread.currentThread());
            assertThat("DATA stays on the response callback thread", Thread.currentThread(), is(responseThread.get()));
            assertThat("DATA uses a virtual stream thread", Thread.currentThread().isVirtual(), is(true));
            delegate.writeData(frame, flowControl);
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                FlowControl.Outbound flowControl) {
            return delegate.writeHeaders(headers, streamId, flags, flowControl);
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                Http2FrameData data, FlowControl.Outbound flowControl) {
            return delegate.writeHeaders(headers, streamId, flags, data, flowControl);
        }
    }

    private record PlatformReaderSelector(ServerConnectionSelector delegate,
                                          ExecutorService readers) implements ServerConnectionSelector {
        @Override
        public int bytesToIdentifyConnection() {
            return delegate.bytesToIdentifyConnection();
        }

        @Override
        public Support supports(BufferData data) {
            return delegate.supports(data);
        }

        @Override
        public Set<String> supportedApplicationProtocols() {
            return delegate.supportedApplicationProtocols();
        }

        @Override
        public ServerConnection connection(ConnectionContext ctx) {
            return new PlatformReaderConnection(delegate.connection(ctx), readers);
        }
    }

    private record PlatformReaderConnection(ServerConnection delegate,
                                            ExecutorService readers) implements ServerConnection, InterruptableTask<Void> {
        @Override
        public void handle(Limit limit) throws InterruptedException {
            var reader = readers.submit(() -> {
                delegate.handle(limit);
                return null;
            });
            try {
                reader.get();
            } catch (InterruptedException e) {
                delegate.close(true);
                reader.cancel(true);
                throw e;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof InterruptedException cause) {
                    throw cause;
                }
                if (e.getCause() instanceof RuntimeException cause) {
                    throw cause;
                }
                if (e.getCause() instanceof Error cause) {
                    throw cause;
                }
                throw new IllegalStateException("Fixture connection reader failed", e.getCause());
            }
        }

        @Override
        public Duration idleTime() {
            return delegate.idleTime();
        }

        @Override
        public void close(boolean interrupt) {
            delegate.close(interrupt);
        }

        @Override
        public boolean canInterrupt() {
            return delegate instanceof InterruptableTask<?> task && task.canInterrupt();
        }
    }
}
