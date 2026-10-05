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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ErrorCode;
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

import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcProtocolHandlerTest {

    private static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.create("grpc-accept-encoding");
    private static final HeaderName GRPC_ENCODING = HeaderNames.create("grpc-encoding");

    @Test
    void testDeadlineContextIsVisibleInEveryCallback() {
        var contexts = new ArrayList<Context>();
        var writer = new RecordingWriter();
        var callReference = new AtomicReference<ServerCall<String, String>>();
        Context previous = Context.current();
        var handler = deadlineHandler("1H", (call, metadata) -> {
            callReference.set(call);
            contexts.add(Context.current());
            call.request(1);
            return new ServerCall.Listener<>() {
                @Override
                public void onReady() {
                    contexts.add(Context.current());
                }

                @Override
                public void onMessage(String message) {
                    contexts.add(Context.current());
                }

                @Override
                public void onHalfClose() {
                    contexts.add(Context.current());
                }

                @Override
                public void onComplete() {
                    contexts.add(Context.current());
                }
            };
        }, writer);
        handler.init();
        sendRequest(handler);
        callReference.get().close(Status.OK, new Metadata());

        assertThat(contexts.size(), is(5));
        Deadline deadline = contexts.getFirst().getDeadline();
        assertThat(deadline, notNullValue());
        for (Context context : contexts) {
            assertThat(context.getDeadline(), sameInstance(deadline));
            assertThat(ServerContextKeys.CONNECTION_CONTEXT.get(context), notNullValue());
            assertThat(context.isCancelled(), is(true));
        }
        assertThat(Context.current(), sameInstance(previous));
        assertThat(writer.trailerWrites.get(), is(1));
    }

    @Test
    @Timeout(20)
    void testRequestFromWorkerWhileOnMessageIsActive() throws Exception {
        requestFromWorkerWhileOnMessageIsActive(false, false);
    }

    @Test
    @Timeout(20)
    void testRequestFromWorkerWhileOnMessageIsActiveWithDeadline() throws Exception {
        requestFromWorkerWhileOnMessageIsActive(false, true);
    }

    @Test
    @Timeout(20)
    void testRequestFromWorkerWithQueuedMessages() throws Exception {
        requestFromWorkerWhileOnMessageIsActive(true, false);
    }

    @Test
    @Timeout(20)
    void testRequestFromWorkerWithQueuedMessagesAndDeadline() throws Exception {
        requestFromWorkerWhileOnMessageIsActive(true, true);
    }

    @Test
    @Timeout(20)
    void testCloseFromWorkerWhileOnReadyIsActive() throws Exception {
        closeFromWorkerWhileCallbackIsActive("ready", false);
        closeFromWorkerWhileCallbackIsActive("ready", true);
    }

    @Test
    @Timeout(20)
    void testCloseFromWorkerWhileOnMessageIsActive() throws Exception {
        closeFromWorkerWhileCallbackIsActive("message", false);
        closeFromWorkerWhileCallbackIsActive("message", true);
    }

    @Test
    @Timeout(20)
    void testCloseFromWorkerWhileRequestedMessageIsActive() throws Exception {
        closeFromWorkerWhileCallbackIsActive("requested message", false);
        closeFromWorkerWhileCallbackIsActive("requested message", true);
    }

    @Test
    @Timeout(20)
    void testCloseFromWorkerWhileOnHalfCloseIsActive() throws Exception {
        closeFromWorkerWhileCallbackIsActive("half-close", false);
        closeFromWorkerWhileCallbackIsActive("half-close", true);
    }

    @Test
    @Timeout(20)
    void testRequestedMessagesPrecedeHalfClose() throws Exception {
        requestedMessagesPrecedeHalfClose(false);
    }

    @Test
    @Timeout(20)
    void testRequestedMessagesPrecedeHalfCloseWithDeadline() throws Exception {
        requestedMessagesPrecedeHalfClose(true);
    }

    @Test
    @Timeout(20)
    void testInboundDataWaitsForActiveMessageCallback() throws Exception {
        inboundDataWaitsForActiveMessageCallback(false);
    }

    @Test
    @Timeout(20)
    void testInboundDataWaitsForActiveMessageCallbackWithDeadline() throws Exception {
        inboundDataWaitsForActiveMessageCallback(true);
    }

    @Test
    void testDeadlineExpiresWhileServiceCallbackIsBlocked() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cancelled = new CompletableFuture<Context>();
        var callReference = new AtomicReference<ServerCall<String, String>>();
        var cancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var writer = new RecordingWriter();
        var handler = deadlineHandler("1S", (call, metadata) -> {
            callReference.set(call);
            call.request(1);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    entered.countDown();
                    await(release);
                    call.sendMessage("late response");
                    call.close(Status.OK, new Metadata());
                }

                @Override
                public void onCancel() {
                    cancellations.incrementAndGet();
                    cancelled.complete(Context.current());
                }

                @Override
                public void onComplete() {
                    completions.incrementAndGet();
                }
            };
        }, writer);
        handler.init();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = executor.submit(() -> sendRequest(handler));
            try {
                assertThat("service callback started", entered.await(5, TimeUnit.SECONDS), is(true));
                Http2Headers trailers = writer.trailers.get(5, TimeUnit.SECONDS);
                assertThat(trailers.httpHeaders().get(GrpcStatus.STATUS_NAME).asString().get(), is("4"));
                assertThat("cancel callback waits for the active callback", cancelled.isDone(), is(false));
                assertThat(callReference.get().isCancelled(), is(true));
            } finally {
                release.countDown();
            }
            request.get(5, TimeUnit.SECONDS);
        }
        Context context = cancelled.get(5, TimeUnit.SECONDS);
        handler.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
        assertThat(context.isCancelled(), is(true));
        assertThat(context.getDeadline().isExpired(), is(true));
        assertThat(cancellations.get(), is(1));
        assertThat(completions.get(), is(0));
        assertThat(writer.trailerWrites.get(), is(1));
        assertThat(writer.dataWrites.get(), is(0));
    }

    @Test
    void testExpiredDeadlineDoesNotStartService() throws Exception {
        var calls = new AtomicInteger();
        var writer = new RecordingWriter();
        var handler = deadlineHandler("0n", (call, metadata) -> {
            calls.incrementAndGet();
            return new ServerCall.Listener<>() { };
        }, writer);
        handler.init();

        assertThat(writer.trailers.get(5, TimeUnit.SECONDS).httpHeaders()
                           .get(GrpcStatus.STATUS_NAME).asString().get(), is("4"));
        assertThat(calls.get(), is(0));
        assertThat(writer.trailerWrites.get(), is(1));
    }

    @Test
    void testDeadlineExpiresBeforeStartCallReturns() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var cancelled = new CompletableFuture<Context>();
        var ready = new AtomicInteger();
        var writer = new RecordingWriter();
        var handler = deadlineHandler("1S", (call, metadata) -> {
            entered.countDown();
            await(release);
            return new ServerCall.Listener<>() {
                @Override
                public void onReady() {
                    ready.incrementAndGet();
                }

                @Override
                public void onCancel() {
                    cancelled.complete(Context.current());
                }
            };
        }, writer);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var initialization = executor.submit(handler::init);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
                assertThat(writer.trailers.get(5, TimeUnit.SECONDS).httpHeaders()
                                   .get(GrpcStatus.STATUS_NAME).asString().get(), is("4"));
            } finally {
                release.countDown();
            }
            initialization.get(5, TimeUnit.SECONDS);
        }
        assertThat(cancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
        assertThat(ready.get(), is(0));
        assertThat(writer.trailerWrites.get(), is(1));
    }

    @Test
    void testMalformedDeadlineRejectsCall() throws Exception {
        var calls = new AtomicInteger();
        var writer = new RecordingWriter();
        var handler = deadlineHandler("1s", (call, metadata) -> {
            calls.incrementAndGet();
            return new ServerCall.Listener<>() { };
        }, writer);
        handler.init();

        assertThat(writer.trailers.get(5, TimeUnit.SECONDS).httpHeaders()
                           .get(GrpcStatus.STATUS_NAME).asString().get(), is("3"));
        assertThat(calls.get(), is(0));
    }

    @Test
    void testResetCancelsContextWithoutDeadline() throws Exception {
        var contextReference = new AtomicReference<Context>();
        var cancellations = new AtomicInteger();
        var cancelled = new CompletableFuture<Context>();
        var writer = new RecordingWriter();
        var handler = deadlineHandler(null, (call, metadata) -> {
            contextReference.set(Context.current());
            return new ServerCall.Listener<>() {
                @Override
                public void onCancel() {
                    cancellations.incrementAndGet();
                    assertThat(Context.current().isCancelled(), is(true));
                    cancelled.complete(Context.current());
                }
            };
        }, writer);
        handler.init();
        assertThat(contextReference.get().getDeadline(), nullValue());
        handler.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
        handler.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));

        assertThat(cancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
        assertThat(cancellations.get(), is(1));
        assertThat(writer.trailerWrites.get(), is(0));
    }

    @Test
    void testTransportCloseBeforeInitializationDoesNotStartService() {
        var calls = new AtomicInteger();
        var writer = new RecordingWriter();
        var handler = deadlineHandler("1H", (call, metadata) -> {
            calls.incrementAndGet();
            return new ServerCall.Listener<>() { };
        }, writer);

        handler.close();
        handler.init();
        handler.close();

        assertThat(handler.streamState(), is(Http2StreamState.CLOSED));
        assertThat(calls.get(), is(0));
        assertThat(writer.trailerWrites.get(), is(0));
    }

    @Test
    void testTransportCloseBeforeStartCallReturnsCancelsListenerOnce() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var contextCancelled = new CompletableFuture<Context>();
        var listenerCancelled = new CompletableFuture<Context>();
        var cancellations = new AtomicInteger();
        var ready = new AtomicInteger();
        var writer = new RecordingWriter();
        var handler = deadlineHandler("1H", (call, metadata) -> {
            Context.current().addListener(contextCancelled::complete, Runnable::run);
            entered.countDown();
            await(release);
            return new ServerCall.Listener<>() {
                @Override
                public void onReady() {
                    ready.incrementAndGet();
                }

                @Override
                public void onCancel() {
                    cancellations.incrementAndGet();
                    listenerCancelled.complete(Context.current());
                }
            };
        }, writer);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var initialization = executor.submit(handler::init);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
                handler.close();
                assertThat(contextCancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
                handler.close();
            } finally {
                release.countDown();
            }
            initialization.get(5, TimeUnit.SECONDS);
        }

        assertThat(listenerCancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
        assertThat(cancellations.get(), is(1));
        assertThat(ready.get(), is(0));
        assertThat(writer.trailerWrites.get(), is(0));
    }

    @Test
    void testTransportClosePreservesSuccessfulCompletion() {
        var cancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var writer = new RecordingWriter();
        var handler = deadlineHandler("1H", (call, metadata) -> {
            call.close(Status.OK, new Metadata());
            return new ServerCall.Listener<>() {
                @Override
                public void onCancel() {
                    cancellations.incrementAndGet();
                }

                @Override
                public void onComplete() {
                    completions.incrementAndGet();
                }
            };
        }, writer);
        handler.init();
        handler.close();

        assertThat(cancellations.get(), is(0));
        assertThat(completions.get(), is(1));
        assertThat(writer.trailerWrites.get(), is(1));
    }

    @Test
    void testTransportCloseInterruptsOwnedWriterWithoutSendingTrailers() throws Exception {
        var writing = new CountDownLatch(1);
        var releaseWrite = new CountDownLatch(1);
        var listenerCancelled = new CompletableFuture<Context>();
        var callerInterrupted = new CompletableFuture<Boolean>();
        RecordingWriter writer = new RecordingWriter() {
            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
                writing.countDown();
                try {
                    releaseWrite.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        var handler = deadlineHandler("1H", (call, metadata) -> {
            call.request(1);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    call.sendMessage("response");
                    callerInterrupted.complete(Thread.currentThread().isInterrupted());
                }

                @Override
                public void onCancel() {
                    listenerCancelled.complete(Context.current());
                }
            };
        }, writer);
        handler.init();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = executor.submit(() -> sendRequest(handler));
            try {
                assertThat(writing.await(5, TimeUnit.SECONDS), is(true));
                handler.close();
                request.get(5, TimeUnit.SECONDS);
                assertThat(listenerCancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
            } finally {
                releaseWrite.countDown();
            }
        }
        assertThat(callerInterrupted.get(5, TimeUnit.SECONDS), is(false));
        assertThat(writer.trailerWrites.get(), is(0));
    }

    @Test
    void testOversizedMessageClosesTransportAndNotifiesServiceOnce() throws Exception {
        var cancelled = new CompletableFuture<Context>();
        var cancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var writer = new RecordingWriter();
        var handler = deadlineHandler(null, (call, metadata) -> new ServerCall.Listener<>() {
            @Override
            public void onCancel() {
                cancellations.incrementAndGet();
                call.close(Status.CANCELLED, new Metadata());
                cancelled.complete(Context.current());
            }

            @Override
            public void onComplete() {
                completions.incrementAndGet();
            }
        }, writer);
        handler.init();
        BufferData data = BufferData.create(5);
        data.write(0);
        data.writeUnsignedInt32(GrpcConfig.create().maxReadBufferSize() + 1);

        handler.data(Http2FrameHeader.create(data.available(), Http2FrameTypes.DATA,
                                             Http2Flag.DataFlags.create(0), 1), data);

        assertThat(writer.trailers.get(5, TimeUnit.SECONDS).httpHeaders()
                           .get(GrpcStatus.STATUS_NAME).asString().get(), is("1"));
        assertThat(cancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
        assertThat(cancellations.get(), is(1));
        assertThat(completions.get(), is(0));
        assertThat(writer.trailerWrites.get(), is(1));
    }

    @Test
    void testDeadlineInterruptsOwnedWriterBeforeSendingTrailers() throws Exception {
        var writing = new CountDownLatch(1);
        var releaseWrite = new CountDownLatch(1);
        var writerExited = new CompletableFuture<Void>();
        var callerInterrupted = new CompletableFuture<Boolean>();
        RecordingWriter writer = new RecordingWriter() {
            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
                writing.countDown();
                try {
                    releaseWrite.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    writerExited.complete(null);
                }
            }

            @Override
            public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                assertThat("writer exited before trailers", writerExited.isDone(), is(true));
                return super.writeHeaders(headers, streamId, flags, flowControl);
            }
        };
        var handler = deadlineHandler("1S", (call, metadata) -> {
            call.request(1);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    call.sendMessage("response");
                    callerInterrupted.complete(Thread.currentThread().isInterrupted());
                }
            };
        }, writer);
        handler.init();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = executor.submit(() -> sendRequest(handler));
            try {
                assertThat(writing.await(5, TimeUnit.SECONDS), is(true));
                assertThat(writer.trailers.get(5, TimeUnit.SECONDS).httpHeaders()
                                   .get(GrpcStatus.STATUS_NAME).asString().get(), is("4"));
            } finally {
                releaseWrite.countDown();
            }
            request.get(5, TimeUnit.SECONDS);
        }
        assertThat(callerInterrupted.get(5, TimeUnit.SECONDS), is(false));
        assertThat(writer.trailerWrites.get(), is(1));
    }

    @Test
    void testBlockingCancellationListenerDoesNotBlockOtherDeadlines() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var firstWriter = new RecordingWriter();
        var first = deadlineHandler("1S", (call, metadata) -> {
            Context.current().addListener(context -> {
                entered.countDown();
                await(release);
            }, Runnable::run);
            return new ServerCall.Listener<>() { };
        }, firstWriter);
        try {
            first.init();
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            var secondWriter = new RecordingWriter();
            var second = deadlineHandler("1S", (call, metadata) -> new ServerCall.Listener<>() { }, secondWriter);
            second.init();

            assertThat(secondWriter.trailers.get(5, TimeUnit.SECONDS).httpHeaders()
                               .get(GrpcStatus.STATUS_NAME).asString().get(), is("4"));
            assertThat(firstWriter.trailers.get(5, TimeUnit.SECONDS).httpHeaders()
                               .get(GrpcStatus.STATUS_NAME).asString().get(), is("4"));
        } finally {
            release.countDown();
        }
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
    void testCompressedMessageBodyMaySpanDataFrames() throws IOException {
        List<String> messages = new ArrayList<>();
        ServerCallHandler<String, String> callHandler = (call, ignored) -> {
            call.request(1);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    messages.add(message);
                }
            };
        };
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ENCODING, "gzip");
        var handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                Http2Headers.create(headers),
                                                noOpWriter(),
                                                1,
                                                null,
                                                Http2StreamState.OPEN,
                                                route(callHandler),
                                                GrpcConfig.create());
        handler.init();

        byte[] compressed = gzip("fragmented");
        int split = compressed.length / 2;
        BufferData first = BufferData.create(5 + split);
        first.write(1);
        first.writeUnsignedInt32(compressed.length);
        first.write(compressed, 0, split);
        BufferData second = BufferData.create(compressed.length - split);
        second.write(compressed, split, compressed.length - split);

        handler.data(Http2FrameHeader.create(first.available(),
                                             Http2FrameTypes.DATA,
                                             Http2Flag.DataFlags.create(0),
                                             1),
                     first);
        handler.data(Http2FrameHeader.create(second.available(),
                                             Http2FrameTypes.DATA,
                                             Http2Flag.DataFlags.create(0),
                                             1),
                     second);

        assertThat(messages, is(List.of("fragmented")));
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

    private static void requestFromWorkerWhileOnMessageIsActive(boolean queuedMessages, boolean withDeadline) throws Exception {
        var callReference = new AtomicReference<ServerCall<String, String>>();
        var requested = new CompletableFuture<CompletableFuture<Void>>();
        var release = new CompletableFuture<Void>();
        var secondReceived = new CompletableFuture<Void>();
        List<String> callbacks = new CopyOnWriteArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var handler = deadlineHandler(withDeadline ? "1H" : null, (call, metadata) -> {
                callReference.set(call);
                return new ServerCall.Listener<>() {
                    @Override
                    public void onMessage(String message) {
                        callbacks.add("entered " + message);
                        if (message.equals("first")) {
                            var request = CompletableFuture.runAsync(() -> call.request(1), executor);
                            requested.complete(request);
                            // The release future also lets cleanup recover an implementation that deadlocks in request.
                            CompletableFuture.anyOf(request, release).join();
                            release.join();
                        }
                        callbacks.add("returned " + message);
                        if (message.equals("second")) {
                            secondReceived.complete(null);
                        }
                    }
                };
            }, new RecordingWriter());
            handler.init();
            ServerCall<String, String> call = callReference.get();
            try {
                if (queuedMessages) {
                    sendStreamingRequest(handler, "first");
                    sendStreamingRequest(handler, "second");
                    sendStreamingRequest(handler, "third");
                    assertThat("messages wait for demand", callbacks, is(List.of()));
                } else {
                    call.request(1);
                }
                var delivery = executor.submit(() -> {
                    if (queuedMessages) {
                        call.request(1);
                    } else {
                        sendStreamingRequest(handler, "first");
                    }
                });

                requested.get(5, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS);
                assertThat("request returns while the first callback remains active",
                           callbacks, is(List.of("entered first")));
                release.complete(null);
                delivery.get(5, TimeUnit.SECONDS);

                if (!queuedMessages) {
                    sendStreamingRequest(handler, "second");
                }
                secondReceived.get(5, TimeUnit.SECONDS);
                List<String> firstTwo = List.of("entered first", "returned first", "entered second", "returned second");
                assertThat("worker demand delivers the second message after the first callback returns",
                           callbacks, is(firstTwo));

                if (!queuedMessages) {
                    sendStreamingRequest(handler, "third");
                }
                assertThat("the third message still needs its own demand", callbacks, is(firstTwo));
                call.request(1);
                assertThat("demand is retained and callbacks remain ordered",
                           callbacks,
                           is(List.of("entered first", "returned first", "entered second", "returned second",
                                      "entered third", "returned third")));
            } finally {
                release.complete(null);
                call.close(Status.OK, new Metadata());
            }
        }
    }

    private static void closeFromWorkerWhileCallbackIsActive(String callback, boolean withDeadline) throws Exception {
        var callReference = new AtomicReference<ServerCall<String, String>>();
        var closing = new CompletableFuture<CompletableFuture<Void>>();
        var release = new CompletableFuture<Void>();
        List<String> callbacks = new CopyOnWriteArrayList<>();
        var writer = new RecordingWriter();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var handler = deadlineHandler(withDeadline ? "1H" : null, (call, metadata) -> {
                callReference.set(call);
                if (!callback.equals("requested message")) {
                    call.request(1);
                }
                return new ServerCall.Listener<>() {
                    @Override
                    public void onReady() {
                        if (callback.equals("ready")) {
                            closeFromWorker();
                        }
                    }

                    @Override
                    public void onMessage(String message) {
                        if (callback.equals("message") || callback.equals("requested message")) {
                            closeFromWorker();
                        }
                    }

                    @Override
                    public void onHalfClose() {
                        if (callback.equals("half-close")) {
                            closeFromWorker();
                        }
                    }

                    @Override
                    public void onComplete() {
                        callbacks.add("complete");
                    }

                    @Override
                    public void onCancel() {
                        callbacks.add("cancel");
                    }

                    private void closeFromWorker() {
                        callbacks.add("entered " + callback);
                        var close = CompletableFuture.runAsync(() -> call.close(Status.OK, new Metadata()), executor);
                        closing.complete(close);
                        // Release also lets cleanup recover an implementation that deadlocks in close().
                        CompletableFuture.anyOf(close, release).join();
                        release.join();
                        callbacks.add("returned " + callback);
                    }
                };
            }, writer);
            try {
                var delivery = executor.submit(() -> {
                    handler.init();
                    if (callback.equals("requested message")) {
                        sendStreamingRequest(handler, "request");
                        callReference.get().request(1);
                    } else if (!callback.equals("ready")) {
                        sendRequest(handler);
                    }
                });
                closing.get(5, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS);
                assertThat("close returns before the active callback returns",
                           callbacks, is(List.of("entered " + callback)));
                release.complete(null);
                delivery.get(5, TimeUnit.SECONDS);

                callReference.get().close(Status.OK, new Metadata());
                handler.close();
                assertThat("completion follows the active callback exactly once",
                           callbacks, is(List.of("entered " + callback, "returned " + callback, "complete")));
                assertThat("one set of trailers", writer.trailerWrites.get(), is(1));
            } finally {
                release.complete(null);
                handler.close();
            }
        }
    }

    private static void requestedMessagesPrecedeHalfClose(boolean withDeadline) throws Exception {
        var callReference = new AtomicReference<ServerCall<String, String>>();
        var requested = new CompletableFuture<CompletableFuture<Void>>();
        var release = new CompletableFuture<Void>();
        var endStreamStarted = new CompletableFuture<Void>();
        var writer = new RecordingWriter();
        List<String> callbacks = new CopyOnWriteArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var handler = deadlineHandler(withDeadline ? "1H" : null, (call, metadata) -> {
                callReference.set(call);
                return new ServerCall.Listener<>() {
                    @Override
                    public void onMessage(String message) {
                        callbacks.add("entered " + message);
                        if (message.equals("first")) {
                            var request = CompletableFuture.runAsync(() -> call.request(2), executor);
                            requested.complete(request);
                            CompletableFuture.anyOf(request, release).join();
                            release.join();
                        }
                        callbacks.add("returned " + message);
                    }

                    @Override
                    public void onHalfClose() {
                        callbacks.add("half-close");
                        call.close(Status.OK, new Metadata());
                    }

                    @Override
                    public void onComplete() {
                        callbacks.add("complete");
                    }
                };
            }, writer);
            handler.init();
            ServerCall<String, String> call = callReference.get();
            try {
                sendStreamingRequest(handler, "first");
                sendStreamingRequest(handler, "second");
                assertThat("messages wait for demand", callbacks, is(List.of()));
                var delivery = executor.submit(() -> call.request(1));
                requested.get(5, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS);
                var endStream = executor.submit(() -> {
                    endStreamStarted.complete(null);
                    sendStreamingRequest(handler, "third", true);
                });
                endStreamStarted.get(5, TimeUnit.SECONDS);
                assertThat("callbacks wait for the active message callback",
                           callbacks, is(List.of("entered first")));
                release.complete(null);
                delivery.get(5, TimeUnit.SECONDS);
                endStream.get(5, TimeUnit.SECONDS);

                assertThat("requested messages precede half-close and survive completion",
                           callbacks,
                           is(List.of("entered first", "returned first", "entered second", "returned second",
                                      "entered third", "returned third", "half-close", "complete")));
                assertThat("half-close completes the call once", writer.trailerWrites.get(), is(1));
                assertThat(handler.streamState(), is(Http2StreamState.CLOSED));
            } finally {
                release.complete(null);
                call.close(Status.OK, new Metadata());
            }
        }
    }

    private static void inboundDataWaitsForActiveMessageCallback(boolean withDeadline) throws Exception {
        var callReference = new AtomicReference<ServerCall<String, String>>();
        var entered = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        var secondParsed = new CompletableFuture<Void>();
        List<String> parsed = new CopyOnWriteArrayList<>();
        List<String> callbacks = new CopyOnWriteArrayList<>();
        var descriptor = stringMethodDescriptor();
        var marshaller = descriptor.getRequestMarshaller();
        descriptor = descriptor.toBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                .setRequestMarshaller(new MethodDescriptor.Marshaller<String>() {
                    @Override
                    public InputStream stream(String value) {
                        return marshaller.stream(value);
                    }

                    @Override
                    public String parse(InputStream stream) {
                        String message = marshaller.parse(stream);
                        parsed.add(message);
                        if (message.equals("second")) {
                            secondParsed.complete(null);
                        }
                        return message;
                    }
                })
                .build();
        var writer = new RecordingWriter();
        var handler = deadlineHandler(withDeadline ? "1H" : null, (call, metadata) -> {
            callReference.set(call);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    callbacks.add("entered " + message);
                    if (message.equals("first")) {
                        entered.complete(null);
                        release.join();
                    }
                    callbacks.add("returned " + message);
                }

                @Override
                public void onComplete() {
                    callbacks.add("complete");
                }
            };
        }, writer, descriptor);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            handler.init();
            ServerCall<String, String> call = callReference.get();
            sendStreamingRequest(handler, "first");
            var delivery = executor.submit(() -> call.request(1));
            entered.get(5, TimeUnit.SECONDS);
            executor.submit(() -> call.request(2)).get(5, TimeUnit.SECONDS);
            assertThat("request returns while the first callback remains active",
                       callbacks, is(List.of("entered first")));

            byte[] second = "second".getBytes(StandardCharsets.UTF_8);
            byte[] third = "third".getBytes(StandardCharsets.UTF_8);
            BufferData data = BufferData.create(5 + second.length + 5 + third.length);
            for (byte[] message : List.of(second, third)) {
                data.write(0);
                data.writeUnsignedInt32(message.length);
                data.write(message);
            }
            var inbound = executor.submit(() -> handler.data(
                    Http2FrameHeader.create(data.available(), Http2FrameTypes.DATA, Http2Flag.DataFlags.create(0), 1), data));
            secondParsed.get(5, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> inbound.get(200, TimeUnit.MILLISECONDS),
                         "DATA must wait for the active callback instead of queuing the remaining messages");
            assertThat("the same DATA frame is not parsed ahead of the active callback",
                       parsed, is(List.of("first", "second")));
            assertThat("message callbacks remain serialized", callbacks, is(List.of("entered first")));

            release.complete(null);
            delivery.get(5, TimeUnit.SECONDS);
            inbound.get(5, TimeUnit.SECONDS);
            assertThat("all messages are parsed after the callback returns", parsed, is(List.of("first", "second", "third")));
            assertThat("outstanding demand delivers each message in order",
                       callbacks,
                       is(List.of("entered first", "returned first", "entered second", "returned second",
                                  "entered third", "returned third")));
            call.close(Status.OK, new Metadata());
            assertThat("completion follows the final message", callbacks.getLast(), is("complete"));
            assertThat("the call completes once", writer.trailerWrites.get(), is(1));
        } finally {
            release.complete(null);
            executor.shutdownNow();
            handler.close();
            assertThat("DATA and demand workers stopped", executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
        }
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

    private static GrpcProtocolHandler<String, String> deadlineHandler(String timeout,
                                                                       ServerCallHandler<String, String> callHandler,
                                                                       Http2StreamWriter writer) {
        return deadlineHandler(timeout, callHandler, writer, stringMethodDescriptor());
    }

    private static GrpcProtocolHandler<String, String> deadlineHandler(String timeout,
                                                                       ServerCallHandler<String, String> callHandler,
                                                                       Http2StreamWriter writer,
                                                                       MethodDescriptor<String, String> descriptor) {
        WritableHeaders<?> headers = WritableHeaders.create();
        if (timeout != null) {
            headers.add(HeaderNames.create("grpc-timeout"), timeout);
        }
        return new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                         Http2Headers.create(headers),
                                         writer,
                                         1,
                                         null,
                                         Http2StreamState.OPEN,
                                         GrpcRouteHandler.methodDefinition(ServerMethodDefinition.create(descriptor, callHandler),
                                                                          null,
                                                                          WeightedBag.create()),
                                         GrpcConfig.create());
    }

    private static void sendRequest(GrpcProtocolHandler<String, String> handler) {
        byte[] message = "request".getBytes(StandardCharsets.UTF_8);
        BufferData data = BufferData.create(5 + message.length);
        data.write(0);
        data.writeUnsignedInt32(message.length);
        data.write(message);
        handler.data(Http2FrameHeader.create(data.available(), Http2FrameTypes.DATA,
                                             Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM), 1), data);
    }

    private static void sendStreamingRequest(GrpcProtocolHandler<String, String> handler, String message) {
        sendStreamingRequest(handler, message, false);
    }

    private static void sendStreamingRequest(GrpcProtocolHandler<String, String> handler, String message, boolean endOfStream) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        BufferData data = BufferData.create(5 + bytes.length);
        data.write(0);
        data.writeUnsignedInt32(bytes.length);
        data.write(bytes);
        handler.data(Http2FrameHeader.create(data.available(), Http2FrameTypes.DATA,
                                             Http2Flag.DataFlags.create(endOfStream ? Http2Flag.END_OF_STREAM : 0), 1), data);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("callback released", latch.await(10, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static GrpcRouteHandler<String, String> route(ServerCall.Listener<String> listener) {
        ServerMethodDefinition<String, String> definition =
                ServerMethodDefinition.create(stringMethodDescriptor(), new ServerCallHandler<>() {
                    @Override
                    public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
                        return listener;
                    }
                });
        return GrpcRouteHandler.methodDefinition(definition, null, WeightedBag.create());
    }

    private static GrpcRouteHandler<String, String> route(ServerCallHandler<String, String> callHandler) {
        ServerMethodDefinition<String, String> definition =
                ServerMethodDefinition.create(stringMethodDescriptor(), callHandler);
        return GrpcRouteHandler.methodDefinition(definition, null, WeightedBag.create());
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

    private static byte[] gzip(String content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
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

    private static class RecordingWriter implements Http2StreamWriter {
        private final CompletableFuture<Http2Headers> trailers = new CompletableFuture<>();
        private final AtomicInteger trailerWrites = new AtomicInteger();
        private final AtomicInteger dataWrites = new AtomicInteger();

        @Override
        public void write(Http2FrameData frame) {
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            dataWrites.incrementAndGet();
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                FlowControl.Outbound flowControl) {
            if (flags.endOfStream()) {
                trailerWrites.incrementAndGet();
                trailers.complete(headers);
            }
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame, FlowControl.Outbound flowControl) {
            throw new UnsupportedOperationException("Unused");
        }
    }

    private static class UnimplementedGrpcConnectionContext implements ConnectionContext {
        @Override
        public ListenerContext listenerContext() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ExecutorService executor() {
            throw new UnsupportedOperationException("Should not be called");
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
