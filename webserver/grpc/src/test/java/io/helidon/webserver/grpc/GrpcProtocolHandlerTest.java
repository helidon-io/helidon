/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.SniMatchType;

import io.grpc.Drainable;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcProtocolHandlerTest {

    private static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.create("grpc-accept-encoding");
    private static final ExecutorService EXECUTOR = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

    @AfterAll
    static void closeExecutor() {
        EXECUTOR.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testIdentityCompressorFlag() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "identity");
        GrpcProtocolHandler handler = new GrpcProtocolHandler(new UnimplementedGrpcConnectionContext(),
                                                              Http2Headers.create(headers),
                                                              null,
                                                              1,
                                                              null,
                                                              Http2StreamState.OPEN,
                                                              null,
                                                              GrpcConfig.create());
        handler.initCompression(null, headers);
        assertThat(handler.identityCompressor(), is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGzipCompressor() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "gzip");
        GrpcProtocolHandler handler = new GrpcProtocolHandler(new UnimplementedGrpcConnectionContext(),
                                                              Http2Headers.create(headers),
                                                              null,
                                                              1,
                                                              null,
                                                              Http2StreamState.OPEN,
                                                              null,
                                                              GrpcConfig.create());
        handler.initCompression(null, headers);
        assertThat(handler.identityCompressor(), is(false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testIgnoreGzipCompressor() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "gzip");
        GrpcProtocolHandler handler = new GrpcProtocolHandler(new UnimplementedGrpcConnectionContext(),
                                                              Http2Headers.create(headers),
                                                              null,
                                                              1,
                                                              null,
                                                              Http2StreamState.OPEN,
                                                              null,
                                                              GrpcConfig.builder()
                                                                      .enableCompression(false)
                                                                      .build());
        handler.initCompression(null, headers);
        assertThat(handler.identityCompressor(), is(true));
    }

    @Test
    void testFromHalfCloseLocalTransition() {
        Http2StreamState next = GrpcProtocolHandler.nextStreamState(
                Http2StreamState.HALF_CLOSED_LOCAL, Http2StreamState.HALF_CLOSED_REMOTE);
        assertThat(next, is(Http2StreamState.CLOSED));
    }

    @Test
    void testFromHalfCloseRemoteTransition() {
        Http2StreamState next = GrpcProtocolHandler.nextStreamState(
                Http2StreamState.HALF_CLOSED_REMOTE, Http2StreamState.HALF_CLOSED_LOCAL);
        assertThat(next, is(Http2StreamState.CLOSED));
    }

    @Test
    void testClosedTransitionRemainsClosed() {
        Http2StreamState next = GrpcProtocolHandler.nextStreamState(
                Http2StreamState.CLOSED, Http2StreamState.HALF_CLOSED_LOCAL);
        assertThat(next, is(Http2StreamState.CLOSED));
    }

    @Test
    void testSendHeadersWrapsUncheckedIOException() {
        ServerCall<String, String> serverCall = createServerCall(headersFailingWriter());

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> serverCall.sendHeaders(new Metadata()));

        assertThat(exception.getCause(), instanceOf(UncheckedIOException.class));
    }

    @Test
    void testSendMessageWrapsUncheckedIOException() {
        ServerCall<String, String> serverCall = createServerCall(dataFailingWriter());

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> serverCall.sendMessage("hello"));

        assertThat(exception.getCause(), instanceOf(UncheckedIOException.class));
    }

    @Test
    void testCancelledPeerDoesNotLeakGrpcCancellationStacktrace() {
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onHalfClose() {
                throw Status.CANCELLED.asRuntimeException();
            }
        };

        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                                Http2Headers.create(WritableHeaders.create()),
                                                                                noOpWriter(),
                                                                                1,
                                                                                null,
                                                                                Http2StreamState.OPEN,
                                                                                route(listener),
                                                                                GrpcConfig.create());
        handler.init();
        handler.rstStream(new Http2RstStream(io.helidon.http.http2.Http2ErrorCode.CANCEL));
        assertThat(handler.streamState(), is(Http2StreamState.CLOSED));

        Http2FrameHeader header = Http2FrameHeader.create(0,
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                          1);

        assertDoesNotThrow(() -> handler.data(header, BufferData.empty()));
    }

    @Test
    void testLocalHalfCloseDrainsRemoteEndStream() {
        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                                Http2Headers.create(WritableHeaders.create()),
                                                                                noOpWriter(),
                                                                                1,
                                                                                null,
                                                                                Http2StreamState.HALF_CLOSED_LOCAL,
                                                                                null,
                                                                                GrpcConfig.create());

        Http2FrameHeader header = Http2FrameHeader.create(0,
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                          1);

        assertDoesNotThrow(() -> handler.data(header, BufferData.empty()));
        assertThat(handler.streamState(), is(Http2StreamState.CLOSED));
    }

    @Test
    void testReentrantDemandPreservesMessageAndHalfCloseOrder() {
        List<String> events = new ArrayList<>();
        AtomicReference<ServerCall<String, String>> callRef = new AtomicReference<>();
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onMessage(String message) {
                events.add(message);
                if ("one".equals(message)) {
                    callRef.get().request(1);
                }
            }

            @Override
            public void onHalfClose() {
                events.add("halfClose");
            }
        };
        ServerCallHandler<String, String> callHandler = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata ignored) {
                callRef.set(call);
                return listener;
            }
        };
        var handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                Http2Headers.create(WritableHeaders.create()),
                                                noOpWriter(),
                                                1,
                                                null,
                                                Http2StreamState.OPEN,
                                                route(callHandler),
                                                GrpcConfig.create());
        handler.init();
        callRef.get().request(2);
        sendData(handler, "one", false);
        sendData(handler, "two", false);
        sendData(handler, "three", false);
        sendData(handler, null, true);

        assertThat(events, is(List.of("one", "two", "three", "halfClose")));
    }

    @Test
    void testHalfCloseWaitsForConcurrentMessageCallback() throws Exception {
        CountDownLatch messageEntered = new CountDownLatch(1);
        CountDownLatch releaseMessage = new CountDownLatch(1);
        AtomicInteger halfCloseCount = new AtomicInteger();
        AtomicReference<ServerCall<String, String>> callRef = new AtomicReference<>();
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onMessage(String message) {
                messageEntered.countDown();
                try {
                    releaseMessage.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public void onHalfClose() {
                halfCloseCount.incrementAndGet();
            }
        };
        ServerCallHandler<String, String> callHandler = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata ignored) {
                callRef.set(call);
                return listener;
            }
        };
        var handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                Http2Headers.create(WritableHeaders.create()),
                                                noOpWriter(),
                                                1,
                                                null,
                                                Http2StreamState.OPEN,
                                                route(callHandler),
                                                GrpcConfig.create());
        handler.init();
        Thread dataThread = Thread.startVirtualThread(() -> {
            sendData(handler, "one", false);
            sendData(handler, null, true);
        });

        Thread requestThread = Thread.startVirtualThread(() -> callRef.get().request(1));
        assertThat("message callback entered", messageEntered.await(10, TimeUnit.SECONDS), is(true));
        assertThat("half-close must wait for message callback", halfCloseCount.get(), is(0));
        releaseMessage.countDown();
        requestThread.join(TimeUnit.SECONDS.toMillis(10));
        dataThread.join(TimeUnit.SECONDS.toMillis(10));

        assertThat("request thread completed", requestThread.isAlive(), is(false));
        assertThat("data thread completed", dataThread.isAlive(), is(false));
        assertThat(halfCloseCount.get(), is(1));
    }

    @Test
    void testCancelWaitsForConcurrentMessageCallback() throws Exception {
        List<String> events = new ArrayList<>();
        CountDownLatch messageEntered = new CountDownLatch(1);
        CountDownLatch releaseMessage = new CountDownLatch(1);
        AtomicReference<ServerCall<String, String>> callRef = new AtomicReference<>();
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onMessage(String message) {
                events.add("message-start");
                messageEntered.countDown();
                try {
                    releaseMessage.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                events.add("message-end");
            }

            @Override
            public void onCancel() {
                events.add("cancel");
            }
        };
        ServerCallHandler<String, String> callHandler = (call, ignored) -> {
            callRef.set(call);
            return listener;
        };
        var handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                Http2Headers.create(WritableHeaders.create()),
                                                noOpWriter(),
                                                1,
                                                null,
                                                Http2StreamState.OPEN,
                                                route(callHandler),
                                                GrpcConfig.create());
        handler.init();
        Thread dataThread = Thread.startVirtualThread(() -> sendData(handler, "one", false));

        Thread requestThread = Thread.startVirtualThread(() -> callRef.get().request(1));
        assertThat("message callback entered", messageEntered.await(10, TimeUnit.SECONDS), is(true));
        handler.rstStream(new Http2RstStream(io.helidon.http.http2.Http2ErrorCode.CANCEL));
        assertThat(events, is(List.of("message-start")));

        releaseMessage.countDown();
        requestThread.join(TimeUnit.SECONDS.toMillis(10));
        dataThread.join(TimeUnit.SECONDS.toMillis(10));

        assertThat("request thread completed", requestThread.isAlive(), is(false));
        assertThat("data thread completed", dataThread.isAlive(), is(false));
        assertThat(events, is(List.of("message-start", "message-end", "cancel")));
    }

    @Test
    void testInboundFloodWaitsForDemand() throws Exception {
        AtomicInteger messageCount = new AtomicInteger();
        AtomicReference<ServerCall<String, String>> callRef = new AtomicReference<>();
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onMessage(String message) {
                messageCount.incrementAndGet();
            }
        };
        ServerCallHandler<String, String> callHandler = (call, ignored) -> {
            callRef.set(call);
            return listener;
        };
        var handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                Http2Headers.create(WritableHeaders.create()),
                                                noOpWriter(),
                                                1,
                                                null,
                                                Http2StreamState.OPEN,
                                                route(callHandler),
                                                GrpcConfig.create());
        handler.init();

        int messageCountInFrame = 256;
        BufferData data = BufferData.growing(messageCountInFrame * 16);
        for (int i = 0; i < messageCountInFrame; i++) {
            byte[] message = Integer.toString(i).getBytes(StandardCharsets.UTF_8);
            data.write(0);
            data.writeUnsignedInt32(message.length);
            data.write(message);
        }
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(0),
                                                          1);
        CountDownLatch dataFinished = new CountDownLatch(1);
        Thread dataThread = Thread.startVirtualThread(() -> {
            try {
                handler.data(header, data);
            } finally {
                dataFinished.countDown();
            }
        });

        assertThat("flood must pause without demand", dataFinished.await(200, TimeUnit.MILLISECONDS), is(false));
        assertThat(messageCount.get(), is(0));
        callRef.get().request(1);
        assertThat(messageCount.get(), is(1));
        assertThat("flood must pause after consuming demand", dataFinished.await(200, TimeUnit.MILLISECONDS), is(false));

        callRef.get().request(messageCountInFrame - 1);
        assertThat("flood completed after demand", dataFinished.await(10, TimeUnit.SECONDS), is(true));
        dataThread.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(dataThread.isAlive(), is(false));
        assertThat(messageCount.get(), is(messageCountInFrame));
    }

    @Test
    void testCancelReleasesMessageWaitingForDemand() throws Exception {
        AtomicInteger messageCount = new AtomicInteger();
        AtomicInteger cancelCount = new AtomicInteger();
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onMessage(String message) {
                messageCount.incrementAndGet();
            }

            @Override
            public void onCancel() {
                cancelCount.incrementAndGet();
            }
        };
        var handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                Http2Headers.create(WritableHeaders.create()),
                                                noOpWriter(),
                                                1,
                                                null,
                                                Http2StreamState.OPEN,
                                                route(listener),
                                                GrpcConfig.create());
        handler.init();
        CountDownLatch dataFinished = new CountDownLatch(1);
        Thread dataThread = Thread.startVirtualThread(() -> {
            try {
                sendData(handler, "one", false);
            } finally {
                dataFinished.countDown();
            }
        });

        assertThat("message must wait for demand", dataFinished.await(200, TimeUnit.MILLISECONDS), is(false));
        handler.rstStream(new Http2RstStream(io.helidon.http.http2.Http2ErrorCode.CANCEL));
        assertThat("cancellation must release the data worker", dataFinished.await(10, TimeUnit.SECONDS), is(true));
        dataThread.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(dataThread.isAlive(), is(false));
        assertThat(cancelCount.get(), is(1));
        assertThat(messageCount.get(), is(0));
    }

    @Test
    void testLocalCloseReleasesEndStreamMessageWaitingForDemand() throws Exception {
        AtomicInteger messageCount = new AtomicInteger();
        AtomicInteger completeCount = new AtomicInteger();
        AtomicReference<ServerCall<String, String>> callRef = new AtomicReference<>();
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onMessage(String message) {
                messageCount.incrementAndGet();
            }

            @Override
            public void onComplete() {
                completeCount.incrementAndGet();
            }
        };
        ServerCallHandler<String, String> callHandler = (call, ignored) -> {
            callRef.set(call);
            return listener;
        };
        var handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                Http2Headers.create(WritableHeaders.create()),
                                                noOpWriter(),
                                                1,
                                                null,
                                                Http2StreamState.OPEN,
                                                route(callHandler),
                                                GrpcConfig.create());
        AtomicInteger streamCloseCount = new AtomicInteger();
        handler.onStreamClosed(streamCloseCount::incrementAndGet);
        handler.init();
        CountDownLatch dataFinished = new CountDownLatch(1);
        Thread dataThread = Thread.startVirtualThread(() -> {
            try {
                sendData(handler, "one", true);
            } finally {
                dataFinished.countDown();
            }
        });

        assertThat("message must wait for demand", dataFinished.await(200, TimeUnit.MILLISECONDS), is(false));
        callRef.get().close(Status.OK, new Metadata());
        assertThat("local close must release the data worker", dataFinished.await(10, TimeUnit.SECONDS), is(true));
        dataThread.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(dataThread.isAlive(), is(false));
        assertThat(handler.streamState(), is(Http2StreamState.CLOSED));
        assertThat(streamCloseCount.get(), is(1));
        assertThat(messageCount.get(), is(0));
        assertThat(completeCount.get(), is(1));
    }

    @Test
    void testLocalCloseBeforeStartCallReturnsCompletesListener() {
        AtomicInteger completeCount = new AtomicInteger();
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onComplete() {
                completeCount.incrementAndGet();
            }
        };
        ServerCallHandler<String, String> callHandler = (call, ignored) -> {
            call.close(Status.OK, new Metadata());
            return listener;
        };
        var handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                Http2Headers.create(WritableHeaders.create()),
                                                noOpWriter(),
                                                1,
                                                null,
                                                Http2StreamState.OPEN,
                                                route(callHandler),
                                                GrpcConfig.create());

        handler.init();

        assertThat(handler.streamState(), is(Http2StreamState.CLOSED));
        assertThat(completeCount.get(), is(1));
    }

    @Test
    void testOnlyOneTerminalCallback() {
        AtomicInteger completeCount = new AtomicInteger();
        AtomicInteger cancelCount = new AtomicInteger();
        AtomicReference<ServerCall<String, String>> callRef = new AtomicReference<>();
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onComplete() {
                completeCount.incrementAndGet();
            }

            @Override
            public void onCancel() {
                cancelCount.incrementAndGet();
            }
        };
        ServerCallHandler<String, String> callHandler = (call, ignored) -> {
            callRef.set(call);
            return listener;
        };
        var handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                Http2Headers.create(WritableHeaders.create()),
                                                noOpWriter(),
                                                1,
                                                null,
                                                Http2StreamState.OPEN,
                                                route(callHandler),
                                                GrpcConfig.create());
        handler.init();

        callRef.get().close(Status.OK, new Metadata());
        handler.rstStream(new Http2RstStream(io.helidon.http.http2.Http2ErrorCode.CANCEL));

        assertThat(completeCount.get(), is(1));
        assertThat(cancelCount.get(), is(0));
    }

    @Test
    void testHalfCloseExceptionSendsGrpcStatus() {
        AtomicInteger cancellations = new AtomicInteger();
        AtomicReference<Http2Headers> trailers = new AtomicReference<>();
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onHalfClose() {
                throw Status.INVALID_ARGUMENT.withDescription("bad request").asRuntimeException();
            }

            @Override
            public void onCancel() {
                cancellations.incrementAndGet();
            }
        };
        ServerCallHandler<String, String> callHandler = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
                call.request(1);
                return listener;
            }
        };
        BufferData data = grpcData("bad");
        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                                Http2Headers.create(WritableHeaders.create()),
                                                                                headersCapturingWriter(trailers),
                                                                                1,
                                                                                null,
                                                                                Http2StreamState.OPEN,
                                                                                route(callHandler),
                                                                                GrpcConfig.create());
        handler.init();
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                          1);

        assertDoesNotThrow(() -> handler.data(header, data));

        assertAll(
                () -> assertThat(cancellations.get(), is(0)),
                () -> assertThat(handler.streamState(), is(Http2StreamState.CLOSED)),
                () -> assertThat(trailers.get().httpHeaders().first(GrpcStatus.STATUS_NAME),
                                 is(Optional.of(String.valueOf(Status.Code.INVALID_ARGUMENT.value())))),
                () -> assertThat(trailers.get().httpHeaders().first(GrpcStatus.MESSAGE_NAME),
                                 is(Optional.of("bad request")))
        );
    }

    @Test
    void testCloseBeforeListenerAssignmentClosesStream() {
        ServerMethodDefinition<String, String> definition =
                ServerMethodDefinition.create(stringMethodDescriptor(), new ServerCallHandler<>() {
                    @Override
                    public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
                        call.close(Status.UNAUTHENTICATED, new Metadata());
                        return new ServerCall.Listener<>() {
                        };
                    }
                });
        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                                Http2Headers.create(WritableHeaders.create()),
                                                                                noOpWriter(),
                                                                                1,
                                                                                null,
                                                                                Http2StreamState.OPEN,
                                                                                GrpcRouteHandler.methodDefinition(definition,
                                                                                                                   null,
                                                                                                                   WeightedBag.create()),
                                                                                GrpcConfig.create());

        handler.init();

        assertThat(handler.streamState(), is(Http2StreamState.CLOSED));
    }

    @Test
    void bufferDataInputStreamReturnsMinusOneAtEof() throws IOException {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        BufferData bufferData = BufferData.create(content);
        try (var stream = new GrpcProtocolHandler.BufferDataInputStream(bufferData)) {
            // drain the stream
            for (int i = 0; i < content.length; i++) {
                assertThat("byte " + i, stream.read(), is(content[i] & 0xFF));
            }

            // InputStream contract: all three read overloads must return -1 at EOF
            assertAll(
                    () -> assertThat("read()", stream.read(), is(-1)),
                    () -> assertThat("read(byte[])", stream.read(new byte[8]), is(-1)),
                    () -> assertThat("read(byte[],off,len)", stream.read(new byte[8], 0, 8), is(-1))
            );
        }
    }

    @Test
    void bufferDataInputStreamReadAllBytes() throws IOException {
        byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
        BufferData bufferData = BufferData.create(content);
        try (var stream = new GrpcProtocolHandler.BufferDataInputStream(bufferData)) {
            // readAllBytes() internally loops on read(byte[],off,len) until -1.
            // Before the fix, this hung forever because the stream returned 0 instead of -1.
            byte[] result = stream.readAllBytes();

            assertThat(result, is(content));
        }
    }

    @Test
    void testCloseSuppressesTrailerWriteDisconnect() {
        ServerCall<String, String> serverCall = createServerCall(closeFailingWriter());
        serverCall.sendHeaders(new Metadata());

        assertDoesNotThrow(() -> serverCall.close(Status.OK, new Metadata()));
        assertThat(serverCall.isCancelled(), is(true));
    }

    @Test
    void exposesSniHostsInGrpcContext() {
        AtomicReference<GrpcConnectionContext> grpcConnectionContext = new AtomicReference<>();
        ServerCallHandler<String, String> callHandler = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
                grpcConnectionContext.set(ServerContextKeys.CONNECTION_CONTEXT.get(io.grpc.Context.current()));
                return new ServerCall.Listener<>() {
                };
            }
        };
        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(
                new UnimplementedGrpcConnectionContext(sniContext("api.example.com", "*.example.com")),
                Http2Headers.create(WritableHeaders.create()),
                noOpWriter(),
                1,
                null,
                Http2StreamState.OPEN,
                route(callHandler),
                GrpcConfig.create());

        handler.init();

        assertThat(grpcConnectionContext.get().sniRequestedHost(), is(Optional.of("api.example.com")));
        assertThat(grpcConnectionContext.get().sniMatchedHost(), is(Optional.of("*.example.com")));
    }

    private static ServerCall<String, String> createServerCall(Http2StreamWriter streamWriter) {
        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                                Http2Headers.create(WritableHeaders.create()),
                                                                                streamWriter,
                                                                                1,
                                                                                null,
                                                                                Http2StreamState.OPEN,
                                                                                route(new ServerCall.Listener<>() {
                                                                                }),
                                                                                GrpcConfig.create());
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "identity");
        handler.initCompression(null, headers);
        return handler.createServerCall();
    }

    private static GrpcRouteHandler<String, String> route(ServerCall.Listener<String> listener) {
        return route(new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
                return listener;
            }
        });
    }

    private static GrpcRouteHandler<String, String> route(ServerCallHandler<String, String> callHandler) {
        ServerMethodDefinition<String, String> definition =
                ServerMethodDefinition.create(stringMethodDescriptor(), callHandler);
        return GrpcRouteHandler.methodDefinition(definition, null, WeightedBag.create());
    }

    private static SniContext sniContext(String presentedHost, String matchedHost) {
        return new SniContext() {
            @Override
            public Optional<String> presentedHost() {
                return Optional.of(presentedHost);
            }

            @Override
            public Optional<String> matchedHost() {
                return Optional.of(matchedHost);
            }

            @Override
            public SniMatchType matchType() {
                return SniMatchType.WILDCARD;
            }

            @Override
            public AuthorityCheck checkAuthority(String authority) {
                return AuthorityCheck.ALLOWED;
            }
        };
    }

    private static void sendData(GrpcProtocolHandler<String, String> handler, String content, boolean endOfStream) {
        BufferData data = content == null ? BufferData.empty() : grpcData(content);
        int flags = endOfStream ? Http2Flag.END_OF_STREAM : 0;
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(flags),
                                                          1);
        handler.data(header, data);
    }

    private static BufferData grpcData(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        BufferData data = BufferData.create(5 + bytes.length);
        data.write(0);
        data.writeUnsignedInt32(bytes.length);
        data.write(bytes);
        return data;
    }

    private static MethodDescriptor<String, String> stringMethodDescriptor() {
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
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
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.Test/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private static Http2StreamWriter headersCapturingWriter(AtomicReference<Http2Headers> capturedHeaders) {
        return new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                capturedHeaders.set(headers);
                return 0;
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    Http2FrameData dataFrame,
                                    FlowControl.Outbound flowControl) {
                throw new UnsupportedOperationException("Unused");
            }
        };
    }

    private static Http2StreamWriter headersFailingWriter() {
        return new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                throw new UncheckedIOException(new IOException("Broken pipe"));
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    Http2FrameData dataFrame,
                                    FlowControl.Outbound flowControl) {
                throw new UnsupportedOperationException("Unused");
            }
        };
    }

    private static Http2StreamWriter dataFailingWriter() {
        return new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
                throw new UncheckedIOException(new IOException("Broken pipe"));
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                return 0;
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    Http2FrameData dataFrame,
                                    FlowControl.Outbound flowControl) {
                throw new UnsupportedOperationException("Unused");
            }
        };
    }

    private static Http2StreamWriter closeFailingWriter() {
        AtomicInteger headerWrites = new AtomicInteger();
        return new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                if (headerWrites.incrementAndGet() == 1) {
                    return 0;
                }
                throw new UncheckedIOException(new IOException("Broken pipe"));
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    Http2FrameData dataFrame,
                                    FlowControl.Outbound flowControl) {
                throw new UnsupportedOperationException("Unused");
            }
        };
    }

    private static Http2StreamWriter noOpWriter() {
        return new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                return 0;
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    Http2FrameData dataFrame,
                                    FlowControl.Outbound flowControl) {
                return 0;
            }
        };
    }

    @Nested
    class BufferDataInputStreamTest {

        @Test
        void drainsFullBuffer() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var stream = stream(content)) {
                int count = stream.drainTo(out);
                assertThat(count, is(content.length));
                assertThat(out.toByteArray(), is(content));
            }
        }

        @Test
        void drainsRemainingBytesAfterPartialRead() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var stream = stream(content)) {
                stream.read(); // consume 'g'
                stream.read(); // consume 'r'
                int count = stream.drainTo(out);
                assertThat(count, is(content.length - 2));
                assertThat(out.toByteArray(), is(Arrays.copyOfRange(content, 2, content.length)));
            }
        }

        @Test
        void drainsNothingFromEmptyBuffer() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var stream = stream(new byte[0])) {
                int count = stream.drainTo(out);
                assertThat(count, is(0));
                assertThat(out.toByteArray().length, is(0));
            }
        }

        @Test
        void readAllBytesReturnsRemainingAfterPartialRead() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            try (var stream = stream(content)) {
                stream.read(); // consume 'g'
                stream.read(); // consume 'r'
                assertThat(stream.readAllBytes(), is(Arrays.copyOfRange(content, 2, content.length)));
            }
        }

        @Test
        void readAllBytesOnExhaustedBufferReturnsEmptyArray() throws IOException {
            try (var stream = stream(new byte[0])) {
                assertThat(stream.readAllBytes().length, is(0));
            }
        }

        @Test
        void zeroLengthReadsReturnZeroAtEndOfStream() throws IOException {
            try (var stream = stream(new byte[0])) {
                byte[] target = new byte[1];
                assertThat(stream.read(new byte[0]), is(0));
                assertThat(stream.read(target, 1, 0), is(0));
            }
        }

        @Test
        void endOfStreamStillValidatesReadArguments() throws IOException {
            try (var stream = stream(new byte[0])) {
                assertThrows(NullPointerException.class, () -> stream.read(null));
                assertThrows(NullPointerException.class, () -> stream.read(null, 0, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[1], 1, 1));
            }
        }

        @Test
        void skipAdvancesPosition() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            try (var stream = stream(content)) {
                long skipped = stream.skip(4);
                assertThat(skipped, is(4L));
                assertThat(stream.readAllBytes(), is(Arrays.copyOfRange(content, 4, content.length)));
            }
        }

        @Test
        void skipClampsToAvailable() throws IOException {
            byte[] content = "hi".getBytes(StandardCharsets.UTF_8);
            try (var stream = stream(content)) {
                long skipped = stream.skip(100);
                assertThat(skipped, is((long) content.length));
                assertThat(stream.available(), is(0));
            }
        }

        @Test
        void skipZeroDoesNothing() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            try (var stream = stream(content)) {
                long skipped = stream.skip(0);
                assertThat(skipped, is(0L));
                assertThat(stream.available(), is(content.length));
            }
        }

        @Test
        void skipNegativeDoesNothing() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            try (var stream = stream(content)) {
                long skipped = stream.skip(-1);
                assertThat(skipped, is(0L));
                assertThat(stream.available(), is(content.length));
            }
        }

        private GrpcProtocolHandler.BufferDataInputStream stream(String content) {
            return stream(content.getBytes(StandardCharsets.UTF_8));
        }

        private GrpcProtocolHandler.BufferDataInputStream stream(byte[] content) {
            return new GrpcProtocolHandler.BufferDataInputStream(BufferData.create(content));
        }
    }

    private static class UnimplementedGrpcConnectionContext implements ConnectionContext {
        private final SniContext sniContext;

        private UnimplementedGrpcConnectionContext() {
            this(null);
        }

        private UnimplementedGrpcConnectionContext(SniContext sniContext) {
            this.sniContext = sniContext;
        }

        @Override
        public Optional<SniContext> sniContext() {
            return Optional.ofNullable(sniContext);
        }

        @Override
        public ListenerContext listenerContext() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ExecutorService executor() {
            return EXECUTOR;
        }

        @Override
        public DataWriter dataWriter() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public DataReader dataReader() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public Router router() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public PeerInfo remotePeer() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public PeerInfo localPeer() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public boolean isSecure() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public String socketId() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public String childSocketId() {
            throw new UnsupportedOperationException("Should not be called");
        }
    }
}
