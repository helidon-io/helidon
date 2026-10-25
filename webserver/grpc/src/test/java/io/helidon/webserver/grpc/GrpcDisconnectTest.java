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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.Services;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ProxyProtocolData;
import io.helidon.webserver.Router;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;

import io.grpc.Context;
import io.grpc.Deadline;
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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@Timeout(15)
class GrpcDisconnectTest {
    @Test
    void resetWithBlockedContextCancellationPreservesSiblingCall() throws Exception {
        resetWithBlockedCancellationPreservesSiblingCall(true);
    }

    @Test
    void resetWithBlockedServiceCancellationPreservesSiblingCall() throws Exception {
        resetWithBlockedCancellationPreservesSiblingCall(false);
    }

    @Test
    void resetCancelsContextWhileServiceCallbackWaitsWithoutDeadline() throws Exception {
        transportCancellationUnblocksServiceCallbackWithoutDeadline(false);
    }

    @Test
    void connectionCloseCancelsContextWhileServiceCallbackWaitsWithoutDeadline() throws Exception {
        transportCancellationUnblocksServiceCallbackWithoutDeadline(true);
    }

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
    void blockedCancellationTrailersDoNotDelayIndependentDeadline() throws Exception {
        var transportGate = new TransportGate();
        var largeWriting = new CompletableFuture<Void>();
        var largeFinished = new CompletableFuture<Void>();
        var blockedStarted = new CompletableFuture<Thread>();
        var blockedCancelled = new CompletableFuture<Thread>();
        var independentStarted = new CompletableFuture<Void>();
        var independentCancelled = new CompletableFuture<Thread>();
        var terminalAttempt = new CompletableFuture<Thread>();
        var terminalCompleted = new CompletableFuture<Void>();
        String largeResponse = "x".repeat(8 * 1024 * 1024);
        var service = ServerServiceDefinition.builder("test.Disconnect")
                .addMethod(descriptor("Large"), (call, _) -> new ServerCall.Listener<>() {
                    @Override
                    public void onHalfClose() {
                        call.sendHeaders(new Metadata());
                        largeWriting.complete(null);
                        try {
                            call.sendMessage(largeResponse);
                            call.close(Status.OK, new Metadata());
                        } finally {
                            largeFinished.complete(null);
                        }
                    }
                })
                .addMethod(descriptor("Blocked"), (_, _) -> {
                    Context.current().addListener(_ -> blockedCancelled.complete(Thread.currentThread()), Runnable::run);
                    blockedStarted.complete(Thread.currentThread());
                    return new ServerCall.Listener<>() { };
                })
                .addMethod(descriptor("Independent"), (_, _) -> {
                    Context.current().addListener(_ -> independentCancelled.complete(Thread.currentThread()), Runnable::run);
                    independentStarted.complete(null);
                    return new ServerCall.Listener<>() { };
                })
                .addMethod(descriptor("Small"), (call, _) -> new ServerCall.Listener<>() {
                    @Override
                    public void onHalfClose() {
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
                                         streamId == 3 ? new TerminalHeadersWriter(writer, terminalAttempt, terminalCompleted)
                                                 : writer,
                                         streamId, serverSettings, clientSettings, flowControl, state, router);
        var http2Selector = Http2ConnectionSelector.builder()
                .http2Config(Http2Config.create())
                .addSubProtocolSelector(checkedGrpcSelector)
                .build();
        var readers = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().daemon().name("grpc-test-reader-", 1).factory());
        // Preserve virtual stream writers while avoiding JDK-8334574 in the Windows connection reader.
        var gatedSelector = new GatedTransportSelector(http2Selector, transportGate);
        ServerConnectionSelector selector = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                ? new PlatformReaderSelector(gatedSelector, readers)
                : gatedSelector;
        var server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .connectionOptions(options -> options.socketSendBufferSize(64 * 1024))
                .addConnectionSelector(selector)
                .addRouting(GrpcRouting.builder().config(Config.empty()).service(service))
                .build()
                .start();
        // Release the fixture's transport gate before closing sockets, including on the unfixed timer's failure path.
        try (var blockedSocket = new Socket(); var independentSocket = new Socket(); transportGate) {
            initializeLargeWindowConnection(blockedSocket, server.port());
            startCall(blockedSocket, 1, "Large", "1H");
            largeWriting.get(5, TimeUnit.SECONDS);
            var responseTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            var decoder = Http2HuffmanDecoder.create();
            Http2FrameData frame;
            do {
                frame = readFrame(blockedSocket);
                if (frame.header().type() == Http2FrameType.HEADERS) {
                    Http2Headers.create(null, responseTable, decoder, frame);
                }
            } while (frame.header().type() != Http2FrameType.DATA);
            assertThat("large response started on stream 1", frame.header().streamId(), is(1));
            Thread largeThread = transportGate.entered.get(5, TimeUnit.SECONDS);
            assertThat("large DATA uses a virtual stream thread", largeThread.isVirtual(), is(true));

            // The second DATA write is now gated inside the real connection writer's serialization lock.
            startCall(blockedSocket, 3, "Blocked", "1S");
            Thread streamThread = blockedStarted.get(5, TimeUnit.SECONDS);
            Thread trailerThread = terminalAttempt.get(5, TimeUnit.SECONDS);
            assertThat("large response is still blocked in the transport writer", largeFinished.isDone(), is(false));
            assertThat("cancellation trailers are waiting for the connection writer", terminalCompleted.isDone(), is(false));

            initializeLargeWindowConnection(independentSocket, server.port());
            startCall(independentSocket, 1, "Independent", "1S");
            independentStarted.get(5, TimeUnit.SECONDS);
            Thread timerThread = independentCancelled.get(5, TimeUnit.SECONDS);
            var independentTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            int independentStatus = -1;
            while (independentStatus < 0) {
                frame = readFrame(independentSocket);
                if (frame.header().type() == Http2FrameType.HEADERS) {
                    var headers = Http2Headers.create(null, independentTable, decoder, frame).httpHeaders();
                    if (frame.header().flags(Http2FrameTypes.HEADERS).endOfStream()) {
                        independentStatus = headers.get(HeaderNames.create("grpc-status")).asInt().get();
                    }
                }
            }
            assertThat("independent connection reaches its own deadline", independentStatus, is(4));
            assertThat("independent deadline does not require draining the stalled connection", largeFinished.isDone(), is(false));
            assertThat("stalled trailers remain blocked during the independent deadline", terminalCompleted.isDone(), is(false));
            assertThat("cancellation trailers use the idle call's existing stream thread", trailerThread, is(streamThread));
            assertThat("cancellation trailers use a virtual stream thread", trailerThread.isVirtual(), is(true));
            Thread blockedExpiryThread = blockedCancelled.get(5, TimeUnit.SECONDS);
            assertThat("independent expiry uses a virtual thread", timerThread.isVirtual(), is(true));
            assertThat("blocked expiry uses a virtual thread", blockedExpiryThread.isVirtual(), is(true));
            assertThat("each expiry has its own cancellation thread", blockedExpiryThread, not(timerThread));
            assertThat("application cancellation is separate from the transport writer", blockedExpiryThread, not(trailerThread));

            // Retire the large stream, then release its transport write and read the sibling's trailers.
            var reset = BufferData.create(4);
            reset.writeInt32(8);
            writeFrame(blockedSocket, new Http2FrameData(Http2FrameHeader.create(4, Http2FrameTypes.RST_STREAM,
                                                                               Http2Flag.NoFlags.create(), 1), reset));
            transportGate.close();
            int blockedStatus = -1;
            while (blockedStatus < 0) {
                frame = readFrame(blockedSocket);
                if (frame.header().type() == Http2FrameType.HEADERS) {
                    var headers = Http2Headers.create(null, responseTable, decoder, frame).httpHeaders();
                    if (frame.header().streamId() == 3 && frame.header().flags(Http2FrameTypes.HEADERS).endOfStream()) {
                        blockedStatus = headers.get(HeaderNames.create("grpc-status")).asInt().get();
                    }
                }
            }
            assertThat("stalled sibling retains its deadline status", blockedStatus, is(4));
            terminalCompleted.get(5, TimeUnit.SECONDS);
            largeFinished.get(5, TimeUnit.SECONDS);

            startCall(blockedSocket, 5, "Small", null);
            var subsequentBody = new ByteArrayOutputStream();
            int subsequentStatus = -1;
            while (subsequentStatus < 0) {
                frame = readFrame(blockedSocket);
                if (frame.header().type() == Http2FrameType.DATA && frame.header().streamId() == 5) {
                    subsequentBody.write(frame.data().readBytes());
                } else if (frame.header().type() == Http2FrameType.HEADERS) {
                    var headers = Http2Headers.create(null, responseTable, decoder, frame).httpHeaders();
                    if (frame.header().streamId() == 5 && frame.header().flags(Http2FrameTypes.HEADERS).endOfStream()) {
                        subsequentStatus = headers.get(HeaderNames.create("grpc-status")).asInt().get();
                    }
                }
            }
            assertThat("connection remains usable after stalled cancellation", subsequentStatus, is(0));
            assertResponse(subsequentBody);
        } finally {
            try {
                server.stop();
            } finally {
                readers.shutdownNow();
                assertThat("fixture connection readers stopped", readers.awaitTermination(5, TimeUnit.SECONDS), is(true));
            }
        }
    }

    @Test
    void blockedDeadlineContextListenerDoesNotDelayIndependentDeadline() throws Exception {
        var blockedStarted = new CompletableFuture<Thread>();
        var blockedEntered = new CompletableFuture<Thread>();
        var releaseBlocked = new CompletableFuture<Void>();
        var blockedFinished = new CompletableFuture<Void>();
        var blockedContextFinished = new CompletableFuture<Void>();
        var independentFinished = new CompletableFuture<Thread>();
        var blockedContextCancellations = new AtomicInteger();
        var blockedServiceCancellations = new AtomicInteger();
        var independentCancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var service = ServerServiceDefinition.builder("test.Disconnect")
                .addMethod(descriptor("Blocked"), (_, _) -> {
                    Context.current().addListener(_ -> {
                        blockedContextCancellations.incrementAndGet();
                        blockedEntered.complete(Thread.currentThread());
                        releaseBlocked.join();
                        blockedContextFinished.complete(null);
                    }, Runnable::run);
                    blockedStarted.complete(Thread.currentThread());
                    return new ServerCall.Listener<>() {
                        @Override
                        public void onCancel() {
                            blockedServiceCancellations.incrementAndGet();
                            blockedFinished.complete(null);
                        }

                        @Override
                        public void onComplete() {
                            completions.incrementAndGet();
                        }
                    };
                })
                .addMethod(descriptor("Independent"), (_, _) -> new ServerCall.Listener<>() {
                    @Override
                    public void onCancel() {
                        independentCancellations.incrementAndGet();
                        independentFinished.complete(Thread.currentThread());
                    }
                })
                .addMethod(descriptor("Small"), (call, _) -> new ServerCall.Listener<>() {
                    @Override
                    public void onHalfClose() {
                        call.sendHeaders(new Metadata());
                        call.sendMessage("small response");
                        call.close(Status.OK, new Metadata());
                    }
                })
                .build();
        var server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .addRouting(GrpcRouting.builder().config(Config.empty()).service(service))
                .build()
                .start();
        // Release the application gate before socket cleanup, including when the shared timer stalls.
        AutoCloseable gate = () -> releaseBlocked.complete(null);
        try (var socket = new Socket(); var independentSocket = new Socket(); gate) {
            initializeLargeWindowConnection(socket, server.port());
            startCall(socket, 1, "Blocked", "1S");
            Thread streamThread = blockedStarted.get(5, TimeUnit.SECONDS);
            Thread expiryThread = blockedEntered.get(5, TimeUnit.SECONDS);

            initializeLargeWindowConnection(independentSocket, server.port());
            startCall(independentSocket, 1, "Independent", "1S");
            var decoder = Http2HuffmanDecoder.create();
            var independentTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            assertThat("independent connection reaches its deadline while the first listener blocks",
                       readResponse(independentSocket, 1, independentTable, decoder, new ByteArrayOutputStream()), is(4));
            independentFinished.get(5, TimeUnit.SECONDS);
            assertThat("first context cancellation listener remains blocked", blockedContextFinished.isDone(), is(false));
            assertThat("expiry cancellation uses a virtual thread", expiryThread.isVirtual(), is(true));
            assertThat("expiry cancellation is separate from the stream runner", expiryThread, not(streamThread));

            startCall(socket, 3, "Small", null);
            var responseTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            var siblingBody = new ByteArrayOutputStream();
            assertThat("same-connection sibling completes while the cancellation listener blocks",
                       readResponse(socket, 3, responseTable, decoder, siblingBody), is(0));
            assertResponse(siblingBody);
            assertThat("first context cancellation listener remains blocked after sibling completion",
                       blockedContextFinished.isDone(), is(false));
            releaseBlocked.complete(null);
            blockedContextFinished.get(5, TimeUnit.SECONDS);
            blockedFinished.get(5, TimeUnit.SECONDS);

            startCall(socket, 5, "Small", null);
            var subsequentBody = new ByteArrayOutputStream();
            assertThat("same connection remains usable after cancellation completes",
                       readResponse(socket, 5, responseTable, decoder, subsequentBody), is(0));
            assertResponse(subsequentBody);
            assertThat("blocked context listener is notified once", blockedContextCancellations.get(), is(1));
            assertThat("blocked service listener is notified once", blockedServiceCancellations.get(), is(1));
            assertThat("independent service listener is notified once", independentCancellations.get(), is(1));
            assertThat("deadline cancellation does not report successful completion", completions.get(), is(0));
        } finally {
            releaseBlocked.complete(null);
            server.stop();
        }
    }

    @Test
    void disconnectCancelsIdleCallWithLongDeadline() throws Exception {
        var started = new CompletableFuture<Context>();
        var contextCancelled = new CompletableFuture<Context>();
        var listenerCancelled = new CompletableFuture<Context>();
        var streamThread = new AtomicReference<Thread>();
        var contextCancellationThread = new AtomicReference<Thread>();
        var serviceCancellationThread = new AtomicReference<Thread>();
        var cancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var service = ServerServiceDefinition.builder("test.Disconnect")
                .addMethod(descriptor("Call"), (_, _) -> {
                    Context.current().addListener(context -> {
                        contextCancellationThread.set(Thread.currentThread());
                        contextCancelled.complete(context);
                    }, Runnable::run);
                    return new ServerCall.Listener<>() {
                        @Override
                        public void onReady() {
                            streamThread.set(Thread.currentThread());
                            started.complete(Context.current());
                        }

                        @Override
                        public void onCancel() {
                            cancellations.incrementAndGet();
                            serviceCancellationThread.set(Thread.currentThread());
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
            assertThat("disconnect context cancellation runs off the connection reader",
                       contextCancellationThread.get().isVirtual(), is(true));
            assertThat("disconnect service cancellation uses the existing stream runner",
                       serviceCancellationThread.get(), is(streamThread.get()));
            assertThat(cancellations.get(), is(1));
            assertThat(completions.get(), is(0));
        } finally {
            server.stop();
        }
    }

    @Test
    void successfulTrailersRecordMetricsBeforeConnectionClose() throws Exception {
        completedResponseMetrics(false);
    }

    @Test
    void connectionCloseAfterSuccessfulTrailersPreservesMetrics() throws Exception {
        completedResponseMetrics(true);
    }

    private static void initializeLargeWindowConnection(Socket socket, int port) throws IOException {
        socket.setReceiveBufferSize(64 * 1024);
        socket.connect(new InetSocketAddress("127.0.0.1", port));
        socket.setSoTimeout(5000);
        socket.getOutputStream().write(Http2Util.prefaceData().readBytes());
        var settings = Http2Settings.builder().add(Http2Setting.INITIAL_WINDOW_SIZE, 16L * 1024 * 1024).build();
        writeFrame(socket, settings.toFrameData(Http2Settings.create(), 0, Http2Flag.SettingsFlags.create(0)));
        var increment = BufferData.create(4);
        increment.writeInt32(16 * 1024 * 1024 - 65535);
        writeFrame(socket, new Http2FrameData(Http2FrameHeader.create(4, Http2FrameTypes.WINDOW_UPDATE,
                                                                    Http2Flag.NoFlags.create(), 0), increment));
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

    private static int readResponse(Socket socket,
                                   int streamId,
                                   Http2Headers.DynamicTable responseTable,
                                   Http2HuffmanDecoder decoder,
                                   ByteArrayOutputStream body) throws IOException {
        while (true) {
            var frame = readFrame(socket);
            if (frame.header().type() == Http2FrameType.DATA && frame.header().streamId() == streamId) {
                body.write(frame.data().readBytes());
            } else if (frame.header().type() == Http2FrameType.HEADERS) {
                var headers = Http2Headers.create(null, responseTable, decoder, frame).httpHeaders();
                if (frame.header().streamId() == streamId && frame.header().flags(Http2FrameTypes.HEADERS).endOfStream()) {
                    return headers.get(HeaderNames.create("grpc-status")).asInt().get();
                }
            }
        }
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

    private void completedResponseMetrics(boolean closeBeforePublicationReturns) throws Exception {
        var contextCancelled = new CompletableFuture<Context>();
        var callFinished = new CompletableFuture<Void>();
        var transportGate = new TransportGate(true);
        var metricsFactory = Services.get(MetricsFactory.class);
        var meterRegistry = metricsFactory.createMeterRegistry(MetricsConfig.create());
        var service = ServerServiceDefinition.builder("test.Disconnect")
                .addMethod(descriptor("CompletedMetrics"), (call, _) -> {
                    Context.current().addListener(contextCancelled::complete, Runnable::run);
                    return new ServerCall.Listener<>() {
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
                    };
                })
                .build();
        var grpcSelector = GrpcProtocolSelector.create(GrpcConfig.builder().enableMetrics(true).build());
        var http2Selector = Http2ConnectionSelector.builder()
                .http2Config(Http2Config.create())
                .addSubProtocolSelector(grpcSelector)
                .build();
        var readers = Executors.newThreadPerTaskExecutor(Thread.ofPlatform().daemon().name("grpc-metrics-reader-", 1).factory());
        var gatedSelector = new GatedTransportSelector(http2Selector, transportGate);
        ServerConnectionSelector selector = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                ? new PlatformReaderSelector(gatedSelector, readers)
                : gatedSelector;
        var server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .addConnectionSelector(selector)
                .addRouting(GrpcRouting.builder().config(Config.empty()).meterRegistry(() -> meterRegistry).service(service))
                .build();
        try (var socket = new Socket(); transportGate) {
            server.start();
            initializeLargeWindowConnection(socket, server.port());
            startCall(socket, 1, "CompletedMetrics", null);
            var responseTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            var body = new ByteArrayOutputStream();
            assertThat("client receives successful terminal trailers",
                       readResponse(socket, 1, responseTable, Http2HuffmanDecoder.create(), body), is(0));
            assertResponse(body);
            transportGate.entered.get(5, TimeUnit.SECONDS);
            assertThat("successful terminal headers are written before server completion returns", callFinished.isDone(), is(false));

            if (closeBeforePublicationReturns) {
                socket.close();
                assertThat("connection teardown reaches the still-publishing call",
                           contextCancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
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
                try {
                    readers.shutdownNow();
                    assertThat("fixture connection readers stopped", readers.awaitTermination(5, TimeUnit.SECONDS), is(true));
                } finally {
                    meterRegistry.close();
                }
            }
        }
    }

    private void resetWithBlockedCancellationPreservesSiblingCall(boolean blockContextListener) throws Exception {
        var started = new CompletableFuture<Thread>();
        var callbackEntered = new CompletableFuture<Thread>();
        var callbackReleased = new CompletableFuture<Void>();
        var callbackFinished = new CompletableFuture<Void>();
        var serviceFinished = new CompletableFuture<Void>();
        var contextCancellations = new AtomicInteger();
        var serviceCancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var service = ServerServiceDefinition.builder("test.Disconnect")
                .addMethod(descriptor("Blocked"), (_, _) -> {
                    Context.current().addListener(_ -> {
                        contextCancellations.incrementAndGet();
                        if (blockContextListener) {
                            callbackEntered.complete(Thread.currentThread());
                            callbackReleased.join();
                            callbackFinished.complete(null);
                        }
                    }, Runnable::run);
                    started.complete(Thread.currentThread());
                    return new ServerCall.Listener<>() {
                        @Override
                        public void onCancel() {
                            serviceCancellations.incrementAndGet();
                            if (!blockContextListener) {
                                callbackEntered.complete(Thread.currentThread());
                                callbackReleased.join();
                                callbackFinished.complete(null);
                            }
                            serviceFinished.complete(null);
                        }

                        @Override
                        public void onComplete() {
                            completions.incrementAndGet();
                        }
                    };
                })
                .addMethod(descriptor("Small"), (call, _) -> new ServerCall.Listener<>() {
                    @Override
                    public void onHalfClose() {
                        call.sendHeaders(new Metadata());
                        call.sendMessage("small response");
                        call.close(Status.OK, new Metadata());
                    }
                })
                .build();
        var server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .addRouting(GrpcRouting.builder().config(Config.empty()).service(service))
                .build()
                .start();
        try (var socket = new Socket()) {
            initializeLargeWindowConnection(socket, server.port());
            startCall(socket, 1, "Blocked", "1H");
            Thread streamThread = started.get(5, TimeUnit.SECONDS);
            var reset = BufferData.create(4);
            reset.writeInt32(8); // HTTP/2 CANCEL
            writeFrame(socket, new Http2FrameData(Http2FrameHeader.create(4, Http2FrameTypes.RST_STREAM,
                                                                        Http2Flag.NoFlags.create(), 1), reset));
            Thread cancellationThread = callbackEntered.get(5, TimeUnit.SECONDS);

            // A blocked application callback must leave this connection able to process another stream.
            startCall(socket, 3, "Small", null);
            var responseTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
            var decoder = Http2HuffmanDecoder.create();
            var siblingBody = new ByteArrayOutputStream();
            int siblingStatus = -1;
            while (siblingStatus < 0) {
                var frame = readFrame(socket);
                if (frame.header().type() == Http2FrameType.DATA && frame.header().streamId() == 3) {
                    siblingBody.write(frame.data().readBytes());
                } else if (frame.header().type() == Http2FrameType.HEADERS) {
                    var headers = Http2Headers.create(null, responseTable, decoder, frame).httpHeaders();
                    if (frame.header().streamId() == 3 && frame.header().flags(Http2FrameTypes.HEADERS).endOfStream()) {
                        siblingStatus = headers.get(HeaderNames.create("grpc-status")).asInt().get();
                    }
                }
            }
            assertThat("sibling completes while application cancellation is blocked", siblingStatus, is(0));
            assertResponse(siblingBody);
            assertThat("application cancellation remains blocked", callbackFinished.isDone(), is(false));
            if (blockContextListener) {
                assertThat("context cancellation runs off the connection reader", cancellationThread.isVirtual(), is(true));
            } else {
                assertThat("service cancellation uses the existing stream runner", cancellationThread, is(streamThread));
            }
            callbackReleased.complete(null);
            callbackFinished.get(5, TimeUnit.SECONDS);
            serviceFinished.get(5, TimeUnit.SECONDS);
            assertThat("context cancellation is delivered once", contextCancellations.get(), is(1));
            assertThat("service cancellation is delivered once", serviceCancellations.get(), is(1));
            assertThat("reset does not report successful completion", completions.get(), is(0));
        } finally {
            callbackReleased.complete(null);
            server.stop();
        }
    }

    private void transportCancellationUnblocksServiceCallbackWithoutDeadline(boolean closeConnection) throws Exception {
        var entered = new CompletableFuture<Thread>();
        var contextCancelled = new CompletableFuture<Thread>();
        var callbackFinished = new CompletableFuture<Void>();
        var serviceCancelled = new CompletableFuture<Thread>();
        var releaseCallback = new CompletableFuture<Void>();
        var callbackActive = new AtomicBoolean();
        var callbackOverlap = new AtomicBoolean();
        var deadline = new AtomicReference<Deadline>();
        var contextCancellations = new AtomicInteger();
        var serviceCancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var service = ServerServiceDefinition.builder("test.Disconnect")
                .addMethod(descriptor("Blocked"), (_, _) -> {
                    Context.current().addListener(_ -> {
                        contextCancellations.incrementAndGet();
                        contextCancelled.complete(Thread.currentThread());
                    }, Runnable::run);
                    return new ServerCall.Listener<>() {
                        @Override
                        public void onHalfClose() {
                            callbackActive.set(true);
                            deadline.set(Context.current().getDeadline());
                            entered.complete(Thread.currentThread());
                            try {
                                // The extra gate lets cleanup recover an implementation that cannot cancel this callback.
                                CompletableFuture.anyOf(contextCancelled, releaseCallback).join();
                            } finally {
                                callbackActive.set(false);
                                callbackFinished.complete(null);
                            }
                        }

                        @Override
                        public void onCancel() {
                            callbackOverlap.set(callbackActive.get());
                            serviceCancellations.incrementAndGet();
                            serviceCancelled.complete(Thread.currentThread());
                        }

                        @Override
                        public void onComplete() {
                            completions.incrementAndGet();
                        }
                    };
                })
                .addMethod(descriptor("Small"), (call, _) -> new ServerCall.Listener<>() {
                    @Override
                    public void onHalfClose() {
                        call.sendHeaders(new Metadata());
                        call.sendMessage("small response");
                        call.close(Status.OK, new Metadata());
                    }
                })
                .build();
        var server = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .addRouting(GrpcRouting.builder().config(Config.empty()).service(service))
                .build()
                .start();
        AutoCloseable gate = () -> releaseCallback.complete(null);
        try (var socket = new Socket(); gate) {
            initializeLargeWindowConnection(socket, server.port());
            startCall(socket, 1, "Blocked", null);
            Thread streamThread = entered.get(5, TimeUnit.SECONDS);
            assertThat("active call has no deadline to rescue transport cancellation", deadline.get(), nullValue());
            if (closeConnection) {
                socket.close();
            } else {
                var reset = BufferData.create(4);
                reset.writeInt32(8);
                writeFrame(socket, new Http2FrameData(Http2FrameHeader.create(4, Http2FrameTypes.RST_STREAM,
                                                                            Http2Flag.NoFlags.create(), 1), reset));
            }
            Thread cancellationThread = contextCancelled.get(5, TimeUnit.SECONDS);
            callbackFinished.get(5, TimeUnit.SECONDS);
            assertThat("transport cancellation uses a virtual thread", cancellationThread.isVirtual(), is(true));
            assertThat("context cancellation unblocks the active stream callback", cancellationThread, not(streamThread));
            assertThat("service cancellation stays on the stream runner",
                       serviceCancelled.get(5, TimeUnit.SECONDS), is(streamThread));
            assertThat("terminal service callback does not overlap the active callback", callbackOverlap.get(), is(false));
            assertThat("context is cancelled once", contextCancellations.get(), is(1));
            assertThat("service is cancelled once", serviceCancellations.get(), is(1));
            assertThat("transport cancellation does not report completion", completions.get(), is(0));
            if (!closeConnection) {
                startCall(socket, 3, "Small", null);
                var responseTable = Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
                var siblingBody = new ByteArrayOutputStream();
                assertThat("reset leaves the same connection usable",
                           readResponse(socket, 3, responseTable, Http2HuffmanDecoder.create(), siblingBody), is(0));
                assertResponse(siblingBody);
            }
        } finally {
            releaseCallback.complete(null);
            try {
                if (entered.isDone()) {
                    callbackFinished.get(5, TimeUnit.SECONDS);
                }
            } finally {
                server.stop();
            }
        }
    }

    private static class TransportGate implements AutoCloseable {
        private final boolean terminalHeaders;
        private final CompletableFuture<Thread> entered = new CompletableFuture<>();
        private final CompletableFuture<Void> released = new CompletableFuture<>();
        private final AtomicInteger dataWrites = new AtomicInteger();

        private TransportGate() {
            this(false);
        }

        private TransportGate(boolean terminalHeaders) {
            this.terminalHeaders = terminalHeaders;
        }

        @Override
        public void close() {
            released.complete(null);
        }

        private boolean beforeWrite(BufferData buffer) {
            if (terminalHeaders) {
                return buffer.available() >= Http2FrameHeader.LENGTH
                        && buffer.get(3) == 1
                        && (buffer.get(4) & Http2Flag.END_OF_STREAM) != 0
                        && buffer.get(5) == 0
                        && buffer.get(6) == 0
                        && buffer.get(7) == 0
                        && buffer.get(8) == 1;
            }
            if (buffer.available() >= Http2FrameHeader.LENGTH
                    && buffer.get(3) == 0
                    && buffer.get(5) == 0
                    && buffer.get(6) == 0
                    && buffer.get(7) == 0
                    && buffer.get(8) == 1
                    && dataWrites.incrementAndGet() == 2) {
                entered.complete(Thread.currentThread());
                released.join();
            }
            return false;
        }

        private void afterWrite(boolean terminal) {
            if (terminal) {
                entered.complete(Thread.currentThread());
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
            boolean terminal = gate.beforeWrite(buffer);
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
            return delegate.connection(new GatedConnectionContext(ctx, new GatedDataWriter(ctx.dataWriter(), gate)));
        }
    }

    private record TerminalHeadersWriter(Http2StreamWriter delegate,
                                         CompletableFuture<Thread> terminalAttempt,
                                         CompletableFuture<Void> terminalCompleted) implements Http2StreamWriter {
        @Override
        public void write(Http2FrameData frame) {
            delegate.write(frame);
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            delegate.writeData(frame, flowControl);
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                FlowControl.Outbound flowControl) {
            if (flags.endOfStream()) {
                terminalAttempt.complete(Thread.currentThread());
            }
            int result = delegate.writeHeaders(headers, streamId, flags, flowControl);
            if (flags.endOfStream()) {
                terminalCompleted.complete(null);
            }
            return result;
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                Http2FrameData data, FlowControl.Outbound flowControl) {
            if (flags.endOfStream()) {
                terminalAttempt.complete(Thread.currentThread());
            }
            int result = delegate.writeHeaders(headers, streamId, flags, data, flowControl);
            if (flags.endOfStream()) {
                terminalCompleted.complete(null);
            }
            return result;
        }
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
