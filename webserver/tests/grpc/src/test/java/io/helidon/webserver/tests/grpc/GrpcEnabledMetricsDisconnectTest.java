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

package io.helidon.webserver.tests.grpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.task.InterruptableTask;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
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
import io.helidon.http.http2.Http2Util;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.testing.junit5.Testing;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.Router;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcConfig;
import io.helidon.webserver.grpc.GrpcProtocolSelector;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test(perMethod = true)
@Timeout(15)
class GrpcEnabledMetricsDisconnectTest {
    @Test
    void successfulTrailersRecordMetricsBeforeConnectionClose(MetricsFactory metricsFactory,
                                                             MeterRegistry meterRegistry) throws Exception {
        completedResponseMetrics(false, metricsFactory, meterRegistry);
    }

    @Test
    void connectionCloseAfterSuccessfulTrailersPreservesMetrics(MetricsFactory metricsFactory,
                                                               MeterRegistry meterRegistry) throws Exception {
        completedResponseMetrics(true, metricsFactory, meterRegistry);
    }

    private static void startCall(Socket socket, int port) throws IOException {
        socket.connect(new InetSocketAddress("127.0.0.1", port));
        socket.setSoTimeout(5000);
        socket.getOutputStream().write(Http2Util.prefaceData().readBytes());
        writeFrame(socket, Http2Settings.create().toFrameData(Http2Settings.create(), 0,
                                                            Http2Flag.SettingsFlags.create(0)));
        var writable = WritableHeaders.create()
                .set(HeaderNames.CONTENT_TYPE, "application/grpc")
                .set(HeaderNames.TE, "trailers");
        var headers = Http2Headers.create(writable);
        headers.method(Method.POST);
        headers.path("/test.Disconnect/CompletedMetrics");
        headers.scheme("http");
        headers.authority("localhost");
        var encoded = BufferData.growing(512);
        headers.write(Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue()),
                      Http2HuffmanEncoder.create(), encoded);
        writeFrame(socket, new Http2FrameData(Http2FrameHeader.create(encoded.available(), Http2FrameTypes.HEADERS,
                Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM), 1), encoded));
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

    private static void readResponse(Socket socket) throws IOException {
        var responseTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
        var decoder = Http2HuffmanDecoder.create();
        var body = new ByteArrayOutputStream();
        while (true) {
            var frame = readFrame(socket);
            if (frame.header().type() == Http2FrameType.DATA && frame.header().streamId() == 1) {
                body.write(frame.data().readBytes());
            } else if (frame.header().type() == Http2FrameType.HEADERS) {
                var headers = Http2Headers.create(null, responseTable, decoder, frame).httpHeaders();
                if (frame.header().streamId() == 1 && frame.header().flags(Http2FrameTypes.HEADERS).endOfStream()) {
                    assertThat("client receives successful terminal trailers",
                               headers.get(HeaderNames.create("grpc-status")).asInt().get(), is(0));
                    break;
                }
            }
        }
        var message = BufferData.create(body.toByteArray());
        assertThat("uncompressed response", message.read(), is(0));
        assertThat("response message length", message.readUnsignedInt32(), is(14L));
        assertThat("complete response payload", new String(message.readBytes(), StandardCharsets.UTF_8), is("small response"));
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
                .setFullMethodName("test.Disconnect/CompletedMetrics")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private void completedResponseMetrics(boolean closeBeforePublicationReturns,
                                          MetricsFactory metricsFactory,
                                          MeterRegistry meterRegistry) throws Exception {
        var callFinished = new CompletableFuture<Void>();
        var transportGate = new TransportGate();
        var service = ServerServiceDefinition.builder("test.Disconnect")
                .addMethod(descriptor(), (call, _) -> new ServerCall.Listener<>() {
                    @Override
                    public void onHalfClose() {
                        try {
                            call.sendHeaders(new Metadata());
                            call.sendMessage("small response");
                            call.close(Status.OK, new Metadata());
                            callFinished.complete(null);
                        } catch (RuntimeException | Error failure) {
                            callFinished.completeExceptionally(failure);
                            throw failure;
                        }
                    }
                })
                .build();
        var grpcSelector = GrpcProtocolSelector.create(GrpcConfig.builder().enableMetrics(true).build());
        var http2Selector = Http2ConnectionSelector.builder()
                .http2Config(Http2Config.create())
                .addSubProtocolSelector(grpcSelector)
                .build();
        var readers = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().daemon().name("grpc-metrics-reader-", 1).factory());
        var gatedSelector = new GatedTransportSelector(http2Selector, transportGate);
        // Preserve virtual stream writers while avoiding JDK-8334574 in the Windows connection reader.
        ServerConnectionSelector selector = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                ? new PlatformReaderSelector(gatedSelector, readers)
                : gatedSelector;
        var server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .addConnectionSelector(selector)
                .addRouting(GrpcRouting.builder().config(Config.empty()).service(service))
                .build();
        try (var socket = new Socket(); transportGate) {
            server.start();
            startCall(socket, server.port());
            readResponse(socket);
            transportGate.headersWritten.get(5, TimeUnit.SECONDS);
            assertThat("successful terminal headers are written before server completion returns", callFinished.isDone(), is(false));

            if (closeBeforePublicationReturns) {
                socket.close();
                transportGate.connectionHandled.get(5, TimeUnit.SECONDS);
                assertThat("teardown occurs before terminal publication returns", callFinished.isDone(), is(false));
            }
            transportGate.close();
            callFinished.get(5, TimeUnit.SECONDS);

            var methodTag = metricsFactory.tagCreate("grpc.method", "test.Disconnect/CompletedMetrics");
            var okTag = metricsFactory.tagCreate("grpc.status", "OK");
            var started = meterRegistry.counter("grpc.server.call.started", List.of(methodTag)).orElseThrow();
            var duration = meterRegistry.timer("grpc.server.call.duration", List.of(methodTag, okTag)).orElseThrow();
            var sentSize = meterRegistry.summary("grpc.server.call.sent_total_compressed_message_size",
                                                 List.of(methodTag, okTag)).orElseThrow();
            assertThat("the successful client response has one started call", started.count(), is(1L));
            assertThat("the successful client response retains its call duration metric; sent message count=" + sentSize.count(),
                       duration.count(), is(1L));
            assertThat("the successful client response retains its sent message metric", sentSize.count(), is(1L));
        } finally {
            transportGate.close();
            try {
                server.stop();
            } finally {
                readers.shutdownNow();
                assertThat("fixture connection readers stopped", readers.awaitTermination(5, TimeUnit.SECONDS), is(true));
            }
        }
    }

    private static class TransportGate implements AutoCloseable {
        private final CompletableFuture<Void> headersWritten = new CompletableFuture<>();
        private final CompletableFuture<Void> connectionHandled = new CompletableFuture<>();
        private final CompletableFuture<Void> released = new CompletableFuture<>();

        @Override
        public void close() {
            released.complete(null);
        }

        private boolean terminalHeaders(BufferData buffer) {
            return buffer.available() >= Http2FrameHeader.LENGTH
                    && buffer.get(3) == 1
                    && (buffer.get(4) & Http2Flag.END_OF_STREAM) != 0
                    && buffer.get(5) == 0
                    && buffer.get(6) == 0
                    && buffer.get(7) == 0
                    && buffer.get(8) == 1;
        }

        private void afterWrite(boolean terminal) {
            if (terminal) {
                headersWritten.complete(null);
                released.join();
            }
        }
    }

    private record GatedDataWriter(DataWriter delegate, TransportGate gate) implements DataWriter {
        @Override
        public void write(BufferData... buffers) {
            delegate.write(buffers);
        }

        @Override
        public void write(BufferData buffer) {
            delegate.write(buffer);
        }

        @Override
        public void writeNow(BufferData... buffers) {
            writeNow(BufferData.create(buffers));
        }

        @Override
        public void writeNow(BufferData buffer) {
            boolean terminal = gate.terminalHeaders(buffer);
            delegate.writeNow(buffer);
            gate.afterWrite(terminal);
        }

        @Override
        public void flush() {
            delegate.flush();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private record GatedConnectionContext(ConnectionContext delegate, DataWriter dataWriter) implements ConnectionContext {
        @Override
        public ListenerContext listenerContext() {
            return delegate.listenerContext();
        }

        @Override
        public ExecutorService executor() {
            return delegate.executor();
        }

        @Override
        public DataReader dataReader() {
            return delegate.dataReader();
        }

        @Override
        public Router router() {
            return delegate.router();
        }

        @Override
        public Optional<ProxyProtocolData> proxyProtocolData() {
            return delegate.proxyProtocolData();
        }

        @Override
        public Optional<SniContext> sniContext() {
            return delegate.sniContext();
        }

        @Override
        public HelidonSocket serverSocket() {
            return delegate.serverSocket();
        }

        @Override
        public PeerInfo remotePeer() {
            return delegate.remotePeer();
        }

        @Override
        public PeerInfo localPeer() {
            return delegate.localPeer();
        }

        @Override
        public boolean isSecure() {
            return delegate.isSecure();
        }

        @Override
        public String socketId() {
            return delegate.socketId();
        }

        @Override
        public String childSocketId() {
            return delegate.childSocketId();
        }
    }

    private record GatedTransportSelector(ServerConnectionSelector delegate,
                                          TransportGate gate) implements ServerConnectionSelector {
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
            var connection = delegate.connection(new GatedConnectionContext(ctx, new GatedDataWriter(ctx.dataWriter(), gate)));
            return new HandledConnection(connection, gate.connectionHandled);
        }
    }

    private record HandledConnection(ServerConnection delegate,
                                     CompletableFuture<Void> handled) implements ServerConnection, InterruptableTask<Void> {
        @Override
        public void handle(Limit limit) throws InterruptedException {
            try {
                delegate.handle(limit);
            } finally {
                handled.complete(null);
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
