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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Contexts;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(15)
class GrpcClientDeadlineTest {
    @ParameterizedTest(name = "{0}, Helidon context={1}")
    @CsvSource({"CALL_DEADLINE, true", "CALL_DEADLINE, false", "GRPC_CONTEXT, true", "GRPC_CONTEXT, false",
                "DIRECT, true", "DIRECT, false"})
    void preservesContextsWhenStartingTransport(StartupMode mode, boolean withHelidonContext) throws Exception {
        WebServer server = server(HttpRouting.builder().post("/test.Deadline/Call", (_, res) -> {
            byte[] message = "response".getBytes(StandardCharsets.UTF_8);
            BufferData data = BufferData.create(5 + message.length);
            data.writeInt8(0);
            data.writeUnsignedInt32(message.length);
            data.write(message);
            res.header(HeaderValues.create(HeaderNames.CONTENT_TYPE, "application/grpc"));
            res.header(HeaderValues.create(HeaderNames.create("grpc-status"), "0"));
            res.send(data.readBytes());
        }));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var unreachableUri = ClientUri.create(URI.create("http://127.0.0.1:1"));
            var serverUri = ClientUri.create(URI.create("http://127.0.0.1:" + server.port()));
            var constructorContext = io.helidon.common.context.Context.create();
            constructorContext.register(unreachableUri);
            var startContext = io.helidon.common.context.Context.create();
            startContext.register(serverUri);
            var supplierContext = new CompletableFuture<Optional<io.helidon.common.context.Context>>();
            var supplierGrpcContext = new CompletableFuture<Context>();
            var suppliedUri = new CompletableFuture<ClientUri>();
            var client = GrpcClient.builder()
                    .baseUri(unreachableUri)
                    .tls(tls -> tls.enabled(false))
                    .readTimeout(Duration.ofSeconds(5))
                    .clientUriSupplier(new ClientUriSupplier() {
                        @Override
                        public boolean hasNext() {
                            return true;
                        }

                        @Override
                        public ClientUri next() {
                            var context = Contexts.context();
                            supplierContext.complete(context);
                            supplierGrpcContext.complete(Context.current());
                            ClientUri uri = context.flatMap(current -> current.get(ClientUri.class))
                                    .orElse(withHelidonContext ? unreachableUri : serverUri);
                            suppliedUri.complete(uri);
                            return uri;
                        }
                    })
                    .build();
            Context grpcContext = mode == StartupMode.GRPC_CONTEXT
                    ? Context.ROOT.withValue(Context.key("startup-context"), "captured") : Context.ROOT;
            CallOptions options = mode == StartupMode.CALL_DEADLINE
                    ? CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.MINUTES) : CallOptions.DEFAULT;
            ClientCall<String, String> call = Contexts.runInContext(constructorContext,
                                                                   () -> grpcContext.call(() -> client.channel()
                                                                           .newCall(descriptor(MethodDescriptor.MethodType.UNARY),
                                                                                    options)));
            var received = new CompletableFuture<String>();
            var status = new CompletableFuture<Status>();
            var listener = new ClientCall.Listener<String>() {
                @Override
                public void onMessage(String message) {
                    received.complete(message);
                }

                @Override
                public void onClose(Status result, Metadata trailers) {
                    status.complete(result);
                }
            };
            try {
                var invocation = executor.submit(() -> {
                    Runnable request = () -> grpcContext.run(() -> {
                        call.start(listener, new Metadata());
                        call.request(1);
                        call.sendMessage("request");
                        call.halfClose();
                    });
                    if (withHelidonContext) {
                        Contexts.runInContext(startContext, request);
                    } else {
                        request.run();
                    }
                    assertThat("the caller's gRPC context is restored", Context.current(), sameInstance(Context.ROOT));
                    return Contexts.context();
                });
                assertThat("the caller's Helidon context is restored", invocation.get(5, TimeUnit.SECONDS),
                           is(Optional.empty()));
                assertThat("URI selection uses the Helidon context from start",
                           supplierContext.get(5, TimeUnit.SECONDS),
                           is(withHelidonContext ? Optional.of(startContext) : Optional.empty()));
                assertThat("URI selection preserves the captured gRPC context",
                           supplierGrpcContext.get(5, TimeUnit.SECONDS), sameInstance(grpcContext));
                assertThat(suppliedUri.get(5, TimeUnit.SECONDS), sameInstance(serverUri));
                assertThat(status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.OK));
                assertThat(received.get(5, TimeUnit.SECONDS), is("response"));
            } finally {
                call.cancel("test cleanup", null);
            }
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(value = MethodDescriptor.MethodType.class,
                names = {"UNARY", "CLIENT_STREAMING", "SERVER_STREAMING", "BIDI_STREAMING"})
    void expiredDeadlineDoesNotConnect(MethodDescriptor.MethodType type) throws Exception {
        var call = client(1).channel()
                .newCall(descriptor(type), CallOptions.DEFAULT.withDeadlineAfter(-1, TimeUnit.SECONDS));
        var listener = new ResponseListener();

        call.start(listener, new Metadata());
        call.request(1);
        call.sendMessage("ignored");
        call.halfClose();
        call.cancel("already expired", null);

        assertThat(listener.status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.DEADLINE_EXCEEDED));
        assertThat(listener.closeCount.get(), is(1));
    }

    @ParameterizedTest
    @EnumSource(value = MethodDescriptor.MethodType.class,
                names = {"UNARY", "CLIENT_STREAMING", "SERVER_STREAMING", "BIDI_STREAMING"})
    void deadlineExpiresBeforeAnyRequestMessage(MethodDescriptor.MethodType type) throws Exception {
        var release = new CountDownLatch(1);
        WebServer server = server(HttpRouting.builder().post("/test.Deadline/Call", (_, res) -> {
            release.await(10, TimeUnit.SECONDS);
            res.send();
        }));
        try {
            var call = client(server.port()).channel()
                    .newCall(descriptor(type), CallOptions.DEFAULT.withDeadlineAfter(300, TimeUnit.MILLISECONDS));
            var listener = new ResponseListener();
            call.start(listener, new Metadata());

            assertThat(listener.status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.DEADLINE_EXCEEDED));
            call.cancel("already expired", null);
            assertThat(listener.closeCount.get(), is(1));
        } finally {
            release.countDown();
            server.stop();
        }
    }

    @Test
    void unaryDeadlineInterruptsWaitingForResponse() {
        var release = new CountDownLatch(1);
        WebServer server = server(HttpRouting.builder().post("/test.Deadline/Call", (_, res) -> {
            release.await(10, TimeUnit.SECONDS);
            res.send();
        }));
        try {
            var client = client(server.port());
            var options = CallOptions.DEFAULT.withDeadlineAfter(300, TimeUnit.MILLISECONDS);
            var failure = assertThrows(StatusRuntimeException.class,
                                       () -> ClientCalls.blockingUnaryCall(client.channel(),
                                                                          descriptor(MethodDescriptor.MethodType.UNARY),
                                                                          options,
                                                                          "request"));
            assertThat(failure.getStatus().getCode(), is(Status.Code.DEADLINE_EXCEEDED));
        } finally {
            release.countDown();
            server.stop();
        }
    }

    @Test
    void preservesServerDeadlineStatusWithoutResponseMessage() {
        WebServer server = server(HttpRouting.builder().post("/test.Deadline/Call", (_, res) -> {
            res.header(HeaderValues.create(HeaderNames.create("grpc-status"), "4"));
            res.send();
        }));
        try {
            var failure = assertThrows(StatusRuntimeException.class,
                                       () -> ClientCalls.blockingUnaryCall(client(server.port()).channel(),
                                                                          descriptor(MethodDescriptor.MethodType.UNARY),
                                                                          CallOptions.DEFAULT,
                                                                          "request"));

            assertThat(failure.getStatus().getCode(), is(Status.Code.DEADLINE_EXCEEDED));
        } finally {
            server.stop();
        }
    }

    @Test
    void deadlineInterruptsTlsHandshake() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             var listening = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var accepted = new CompletableFuture<Socket>();
            executor.submit(() -> {
                try {
                    accepted.complete(listening.accept());
                } catch (IOException e) {
                    accepted.completeExceptionally(e);
                }
            });
            var client = GrpcClient.builder()
                    .baseUri("https://localhost:" + listening.getLocalPort())
                    .build();
            var call = client.channel().newCall(descriptor(MethodDescriptor.MethodType.UNARY),
                                                CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.SECONDS));
            var listener = new ResponseListener();
            var started = executor.submit(() -> call.start(listener, new Metadata()));
            try (Socket connection = accepted.get(5, TimeUnit.SECONDS)) {
                // The peer accepts TCP but never answers the TLS handshake.
                started.get(5, TimeUnit.SECONDS);
                assertThat(listener.status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.DEADLINE_EXCEEDED));
                connection.setSoTimeout(5000);
                connection.getInputStream().readAllBytes();
                assertThat(listener.closeCount.get(), is(1));
            }
        }
    }

    @Test
    void deadlineDuringInitialHeadersDoesNotResetUnopenedStream() throws Exception {
        String loggerName = GrpcClientDeadlineTest.class.getName() + ".initialHeaders";
        Logger logger = Logger.getLogger(loggerName + ".cl-send");
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        var nanos = new AtomicLong();
        var deadline = Deadline.after(1, TimeUnit.SECONDS, new Deadline.Ticker() {
            @Override
            public long nanoTime() {
                return nanos.get();
            }
        });
        var barrier = new HeaderWriteBarrier(nanos);
        logger.setLevel(Level.FINE);
        logger.setUseParentHandlers(false);
        logger.addHandler(barrier);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             var listening = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             barrier) {
            listening.setSoTimeout(5000);
            var received = executor.submit(() -> {
                try (Socket peer = listening.accept()) {
                    peer.setSoTimeout(5000);
                    peer.getOutputStream().write(new byte[] {0, 0, 0, 4, 0, 0, 0, 0, 0});
                    return peer.getInputStream().readAllBytes();
                }
            });
            var client = WebClient.builder()
                    .baseUri("http://localhost:" + listening.getLocalPort())
                    .tls(tls -> tls.enabled(false))
                    .addProtocolConfig(Http2ClientProtocolConfig.builder()
                                               .log(log -> log.loggerName(loggerName))
                                               .build())
                    .build()
                    .client(GrpcClient.PROTOCOL);
            var call = client.channel().newCall(descriptor(MethodDescriptor.MethodType.UNARY),
                                                CallOptions.DEFAULT.withDeadline(deadline));
            var listener = new ResponseListener();
            var started = executor.submit(() -> call.start(listener, new Metadata()));
            try {
                barrier.reached.get(5, TimeUnit.SECONDS);
                assertThat(listener.status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.DEADLINE_EXCEEDED));
                started.get(5, TimeUnit.SECONDS);

                BufferData data = BufferData.create(received.get(5, TimeUnit.SECONDS));
                assertThat(data.readString(24, StandardCharsets.US_ASCII), is("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"));
                List<Http2FrameType> frames = new ArrayList<>();
                while (data.available() > 0) {
                    Http2FrameHeader frame = Http2FrameHeader.create(data);
                    frames.add(frame.type());
                    data.skip(frame.length());
                }
                assertThat("request HEADERS remain blocked", frames, not(hasItem(Http2FrameType.HEADERS)));
                assertThat("an unopened HTTP/2 stream must not be reset", frames, not(hasItem(Http2FrameType.RST_STREAM)));
                assertThat(listener.closeCount.get(), is(1));
            } finally {
                barrier.close();
                call.cancel("test complete", null);
            }
        } finally {
            logger.removeHandler(barrier);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
    }

    @ParameterizedTest
    @EnumSource(value = MethodDescriptor.MethodType.class, names = {"UNARY", "BIDI_STREAMING"})
    void explicitCancellationClosesOnlyOnce(MethodDescriptor.MethodType type) throws Exception {
        var release = new CountDownLatch(1);
        WebServer server = server(HttpRouting.builder().post("/test.Deadline/Call", (_, res) -> {
            release.await(10, TimeUnit.SECONDS);
            res.send();
        }));
        try {
            var call = client(server.port()).channel()
                    .newCall(descriptor(type), CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.MINUTES));
            var listener = new ResponseListener();
            call.start(listener, new Metadata());
            call.request(1);
            call.cancel("explicit cancellation", null);
            call.cancel("repeated cancellation", null);

            assertThat(listener.status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.CANCELLED));
            assertThat(listener.closeCount.get(), is(1));
        } finally {
            release.countDown();
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(CancellationSource.class)
    void cancellationClosesTransportWhileListenerIsBlocked(CancellationSource source, @TempDir Path directory) throws Exception {
        var nanos = new AtomicLong();
        var deadline = Deadline.after(1, TimeUnit.SECONDS, new Deadline.Ticker() {
            @Override
            public long nanoTime() {
                return nanos.get();
            }
        });
        CallOptions options = source == CancellationSource.DEADLINE
                ? CallOptions.DEFAULT.withDeadline(deadline) : CallOptions.DEFAULT;
        var accepted = new CompletableFuture<Socket>();
        var received = new CompletableFuture<String>();
        var release = new CompletableFuture<Void>();
        var listenerCancellationReturned = new CompletableFuture<Void>();
        var status = new CompletableFuture<Status>();
        var closeCount = new AtomicInteger();
        List<String> callbacks = new CopyOnWriteArrayList<>();
        Path terminalFile = directory.resolve("terminal-record");
        byte[] terminalRecord = "terminal record\n".getBytes(StandardCharsets.UTF_8);
        try (var context = Context.current().withCancellation();
             var executor = Executors.newVirtualThreadPerTaskExecutor();
             var listening = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listening.setSoTimeout(5000);
            var peer = executor.submit(() -> {
                try (Socket socket = listening.accept()) {
                    accepted.complete(socket);
                    socket.setSoTimeout(10000);
                    sendStreamingResponse(socket);
                    socket.getInputStream().readAllBytes();
                }
                return null;
            });
            var client = GrpcClient.builder()
                    .baseUri("http://127.0.0.1:" + listening.getLocalPort())
                    .tls(tls -> tls.enabled(false))
                    .readTimeout(Duration.ofSeconds(10))
                    .build();
            var call = context.call(() -> client.channel()
                    .newCall(descriptor(MethodDescriptor.MethodType.BIDI_STREAMING), options));
            try {
                call.start(new ClientCall.Listener<>() {
                    @Override
                    public void onMessage(String message) {
                        callbacks.add("onMessage entered");
                        received.complete(message);
                        try {
                            if (source == CancellationSource.ON_MESSAGE) {
                                call.cancel("cancelled by listener", null);
                                listenerCancellationReturned.complete(null);
                            }
                            // Cancellation can interrupt this thread. Keep the application callback blocked.
                            release.join();
                        } finally {
                            callbacks.add("onMessage returned");
                        }
                    }

                    @Override
                    public void onClose(Status result, Metadata trailers) {
                        callbacks.add("onClose");
                        closeCount.incrementAndGet();
                        try (var channel = FileChannel.open(terminalFile,
                                                            StandardOpenOption.CREATE_NEW,
                                                            StandardOpenOption.WRITE)) {
                            var record = ByteBuffer.wrap(terminalRecord);
                            while (record.hasRemaining()) {
                                channel.write(record);
                            }
                        } catch (IOException e) {
                            status.completeExceptionally(e);
                            return;
                        }
                        status.complete(result);
                    }
                }, new Metadata());
                call.request(1);
                call.sendMessage("request");
                assertThat(received.get(5, TimeUnit.SECONDS), is("response"));

                var cancellation = executor.submit(() -> {
                    switch (source) {
                        case CALL -> call.cancel("explicit cancellation", null);
                        case CONTEXT -> context.cancel(null);
                        case DEADLINE -> nanos.set(TimeUnit.SECONDS.toNanos(1));
                        case ON_MESSAGE -> listenerCancellationReturned.join();
                    }
                });
                // Both the cancellation operation and the peer's EOF must precede the callback's release.
                cancellation.get(5, TimeUnit.SECONDS);
                peer.get(5, TimeUnit.SECONDS);
                assertThat("onClose waits for onMessage to return", status.isDone(), is(false));
                assertThat("callbacks do not overlap", callbacks, is(List.of("onMessage entered")));

                release.complete(null);
                Status.Code expected = source == CancellationSource.DEADLINE
                        ? Status.Code.DEADLINE_EXCEEDED : Status.Code.CANCELLED;
                assertThat(status.get(5, TimeUnit.SECONDS).getCode(), is(expected));
                assertThat("terminal callback can write its record", Files.readAllBytes(terminalFile), is(terminalRecord));
                call.cancel("repeated cancellation", null);
                context.cancel(null);
                assertThat(closeCount.get(), is(1));
                assertThat("terminal callback follows the message callback",
                           callbacks, is(List.of("onMessage entered", "onMessage returned", "onClose")));
            } finally {
                release.complete(null);
                if (accepted.isDone()) {
                    accepted.join().close();
                }
                call.cancel("test cleanup", null);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminalCallbackPreservesCallerInterrupt(boolean failCallback) throws Exception {
        var call = client(1).channel().newCall(descriptor(MethodDescriptor.MethodType.UNARY), CallOptions.DEFAULT);
        call.cancel("cancelled before start", null);
        var callbackFailure = new IllegalStateException("terminal callback failed");
        var listener = new ClientCall.Listener<String>() {
            @Override
            public void onClose(Status status, Metadata trailers) {
                assertThat("terminal callback starts without an interrupt", Thread.currentThread().isInterrupted(), is(false));
                if (failCallback) {
                    throw callbackFailure;
                }
            }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var notified = executor.submit(() -> {
                Thread.currentThread().interrupt();
                try {
                    if (failCallback) {
                        assertThat(assertThrows(IllegalStateException.class, () -> call.start(listener, new Metadata())),
                                   sameInstance(callbackFailure));
                    } else {
                        call.start(listener, new Metadata());
                    }
                    return Thread.currentThread().isInterrupted();
                } finally {
                    Thread.interrupted();
                }
            });
            assertThat("the caller's interrupt is restored", notified.get(5, TimeUnit.SECONDS), is(true));
        }
    }

    @Test
    void contextCancellationBeforeStartDoesNotConnect() throws Exception {
        try (var context = Context.current().withCancellation()) {
            ClientCall<String, String> call = context.call(() -> client(1).channel()
                    .newCall(descriptor(MethodDescriptor.MethodType.UNARY), CallOptions.DEFAULT));
            context.cancel(null);
            var listener = new ResponseListener();

            call.start(listener, new Metadata());

            assertThat(listener.status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.CANCELLED));
            assertThat(listener.closeCount.get(), is(1));
        }
    }

    @ParameterizedTest
    @EnumSource(value = MethodDescriptor.MethodType.class, names = {"UNARY", "BIDI_STREAMING"})
    void explicitCancellationBeforeStartDoesNotConnect(MethodDescriptor.MethodType type) throws Exception {
        var call = client(1).channel().newCall(descriptor(type), CallOptions.DEFAULT);
        var listener = new ResponseListener();
        call.cancel("cancelled before start", null);

        call.start(listener, new Metadata());

        assertThat(listener.status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.CANCELLED));
        assertThat(listener.closeCount.get(), is(1));
    }

    @Test
    void contextDeadlineExpiresAfterCallCreation() throws Exception {
        var release = new CountDownLatch(1);
        WebServer server = server(HttpRouting.builder().post("/test.Deadline/Call", (_, res) -> {
            release.await(10, TimeUnit.SECONDS);
            res.send();
        }));
        try (var scheduler = Executors.newSingleThreadScheduledExecutor();
             var context = Context.current().withDeadlineAfter(300, TimeUnit.MILLISECONDS, scheduler)) {
            var client = client(server.port());
            ClientCall<String, String> call = context.call(() -> client.channel()
                    .newCall(descriptor(MethodDescriptor.MethodType.BIDI_STREAMING), CallOptions.DEFAULT));
            var listener = new ResponseListener();
            call.start(listener, new Metadata());

            assertThat(listener.status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.DEADLINE_EXCEEDED));
            assertThat(listener.closeCount.get(), is(1));
        } finally {
            release.countDown();
            server.stop();
        }
    }

    @Test
    void callerTimeoutMetadataDoesNotCreateDeadline() throws Exception {
        var receivedTimeout = new CompletableFuture<Boolean>();
        var release = new CountDownLatch(1);
        WebServer server = server(HttpRouting.builder().post("/test.Deadline/Call", (req, res) -> {
            receivedTimeout.complete(req.headers().contains(HeaderNames.create("grpc-timeout")));
            release.await(10, TimeUnit.SECONDS);
            res.send();
        }));
        try {
            var call = client(server.port()).channel()
                    .newCall(descriptor(MethodDescriptor.MethodType.BIDI_STREAMING), CallOptions.DEFAULT);
            var listener = new ResponseListener();
            var metadata = new Metadata();
            metadata.put(Metadata.Key.of("grpc-timeout", Metadata.ASCII_STRING_MARSHALLER), "1H");
            call.start(listener, metadata);
            call.sendMessage("request");
            call.halfClose();

            assertThat(receivedTimeout.get(5, TimeUnit.SECONDS), is(false));
            call.cancel("complete", null);
            assertThat(listener.status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.CANCELLED));
        } finally {
            release.countDown();
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(DeadlineSource.class)
    void propagatesEarlierDeadlineCapturedAtCallCreation(DeadlineSource source) throws Exception {
        var timeoutHeader = new CompletableFuture<String>();
        var release = new CountDownLatch(1);
        WebServer server = server(HttpRouting.builder().post("/test.Deadline/Call", (req, res) -> {
            timeoutHeader.complete(req.headers().get(HeaderNames.create("grpc-timeout")).get());
            release.await(10, TimeUnit.SECONDS);
            res.send();
        }));
        try (var scheduler = Executors.newSingleThreadScheduledExecutor();
             var context = Context.current().withDeadline(Deadline.after(source == DeadlineSource.CONTEXT ? 30 : 60,
                                                                         TimeUnit.SECONDS), scheduler)) {
            var client = client(server.port());
            ClientCall<String, String> call = context.call(() -> client.channel()
                    .newCall(descriptor(MethodDescriptor.MethodType.BIDI_STREAMING),
                             CallOptions.DEFAULT.withDeadlineAfter(source == DeadlineSource.CALL_OPTIONS ? 30 : 60,
                                                                   TimeUnit.SECONDS)));
            var listener = new ResponseListener();
            var metadata = new Metadata();
            metadata.put(Metadata.Key.of("grpc-timeout", Metadata.ASCII_STRING_MARSHALLER), "1H");
            call.start(listener, metadata);
            call.sendMessage("request");
            call.halfClose();

            String encoded = timeoutHeader.get(5, TimeUnit.SECONDS);
            long amount = Long.parseLong(encoded.substring(0, encoded.length() - 1));
            long nanos = switch (encoded.charAt(encoded.length() - 1)) {
                case 'n' -> amount;
                case 'u' -> TimeUnit.MICROSECONDS.toNanos(amount);
                case 'm' -> TimeUnit.MILLISECONDS.toNanos(amount);
                case 'S' -> TimeUnit.SECONDS.toNanos(amount);
                case 'M' -> TimeUnit.MINUTES.toNanos(amount);
                case 'H' -> TimeUnit.HOURS.toNanos(amount);
                default -> throw new AssertionError("Invalid timeout: " + encoded);
            };
            assertThat(nanos, greaterThan(0L));
            assertThat(nanos, lessThanOrEqualTo(TimeUnit.SECONDS.toNanos(30)));
            context.cancel(null);
            assertThat(listener.status.get(5, TimeUnit.SECONDS).getCode(), is(Status.Code.CANCELLED));
        } finally {
            release.countDown();
            server.stop();
        }
    }

    private static GrpcClient client(int port) {
        return GrpcClient.builder()
                .baseUri("http://localhost:" + port)
                .tls(tls -> tls.enabled(false))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static void sendStreamingResponse(Socket socket) throws IOException {
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
        byte[] message = "response".getBytes(StandardCharsets.UTF_8);
        Http2FrameHeader.create(5 + message.length, Http2FrameTypes.DATA, Http2Flag.DataFlags.create(0), streamId)
                .write().writeTo(output);
        BufferData prefix = BufferData.create(5);
        prefix.writeInt8(0);
        prefix.writeUnsignedInt32(message.length);
        prefix.writeTo(output);
        output.write(message);
        output.flush();
    }

    private static WebServer server(HttpRouting.Builder routing) {
        return WebServer.builder()
                .addConnectionSelector(Http2ConnectionSelector.builder().http2Config(Http2Config.create()).build())
                .addRouting(routing)
                .build()
                .start();
    }

    private static MethodDescriptor<String, String> descriptor(MethodDescriptor.MethodType type) {
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
                .setType(type)
                .setFullMethodName("test.Deadline/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private enum StartupMode {
        CALL_DEADLINE,
        GRPC_CONTEXT,
        DIRECT
    }

    private enum DeadlineSource {
        CONTEXT,
        CALL_OPTIONS
    }

    private enum CancellationSource {
        CALL,
        CONTEXT,
        DEADLINE,
        ON_MESSAGE
    }

    private static final class ResponseListener extends ClientCall.Listener<String> {
        private final CompletableFuture<Status> status = new CompletableFuture<>();
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public void onClose(Status status, Metadata trailers) {
            closeCount.incrementAndGet();
            this.status.complete(status);
        }
    }

    private static final class HeaderWriteBarrier extends Handler implements AutoCloseable {
        private final AtomicLong nanos;
        private final CompletableFuture<Void> reached = new CompletableFuture<>();
        private final CompletableFuture<Void> released = new CompletableFuture<>();

        private HeaderWriteBarrier(AtomicLong nanos) {
            this.nanos = nanos;
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getMessage().contains("headers:")) {
                // Expire only once the stream has an id but its initial HEADERS are not on the wire.
                nanos.set(TimeUnit.SECONDS.toNanos(1));
                reached.complete(null);
                // Deadline cancellation interrupts the starter. Keep HEADERS blocked until assertions finish.
                released.join();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            released.complete(null);
        }
    }
}
