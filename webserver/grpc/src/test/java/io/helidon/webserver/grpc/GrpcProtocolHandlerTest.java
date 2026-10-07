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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriAuthority;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.ConnectionFlowControl;
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
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.testing.junit5.Testing;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.SniMatchType;

import io.grpc.Context;
import io.grpc.Deadline;
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
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class GrpcProtocolHandlerTest {

    private static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.create("grpc-accept-encoding");
    private static final HeaderName GRPC_ENCODING = HeaderNames.create("grpc-encoding");
    private static final ExecutorService EXECUTOR = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

    @AfterAll
    static void closeExecutor() {
        EXECUTOR.close();
    }

    private final GrpcProtocolSelector.Metrics metrics;

    GrpcProtocolHandlerTest(MetricsFactory metricsFactory, MeterRegistry meterRegistry) {
        this.metrics = new GrpcProtocolSelector.Metrics(
                new AtomicReference<>(new GrpcProtocolSelector.MetricsOwner(metricsFactory, meterRegistry)),
                new ConcurrentHashMap<>());
    }

    @Test
    void testDefaultMaxReadBufferSize() {
        assertThat(GrpcConfig.create().maxReadBufferSize(), is(4 * 1024 * 1024));
    }

    @Test
    void testOversizedMessageSendsResourceExhausted() {
        List<LogRecord> records = captureLogRecords(() -> assertOversizedMessage(16 * 1024 + 1L, 16 * 1024));

        assertThat(records, hasSize(1));
        LogRecord record = records.getFirst();
        assertAll(
                () -> assertThat(record.getLevel(), is(Level.FINE)),
                () -> assertThat(record.getThrown(), is(nullValue()))
        );
    }

    @Test
    void testConfiguredLimitBelowInitialBufferCapacity() {
        assertOversizedMessage(1025, 1024);
    }

    @Test
    void testUnsignedMessageSizeAboveIntegerMax() {
        assertOversizedMessage((long) Integer.MAX_VALUE + 1, 4 * 1024 * 1024);
    }

    @Test
    void testDeadlineContextIsVisibleInEveryCallback() {
        var contexts = new ArrayList<Context>();
        var writer = new RecordingWriter();
        var callReference = new AtomicReference<ServerCall<String, String>>();
        Context previous = Context.current();
        var handler = deadlineHandler("1H", (call, _) -> {
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
        var handler = deadlineHandler("1S", (call, _) -> {
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
        var handler = deadlineHandler("0n", (_, _) -> {
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
        var handler = deadlineHandler("1S", (_, _) -> {
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
        var handler = deadlineHandler("1s", (_, _) -> {
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
        var handler = deadlineHandler(null, (_, _) -> {
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
        var handler = deadlineHandler("1H", (_, _) -> {
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
        var handler = deadlineHandler("1H", (_, _) -> {
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
        var handler = deadlineHandler("1H", (call, _) -> {
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
        handler.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
        assertThat("a late reset preserves successful completion", cancellations.get(), is(0));
        assertThat(completions.get(), is(1));
    }

    @Test
    void testPeerResetDuringTrailersCancelsListenerOnce() throws Exception {
        terminalWriteWithTransportClose(true);
    }

    @Test
    void testNormalStreamCleanupDuringTrailersPreservesCompletion() throws Exception {
        terminalWriteWithTransportClose(false);
    }

    @Test
    void testTransportCloseDoesNotInterruptSocketWriter() throws Exception {
        var writing = new CountDownLatch(1);
        var releaseWrite = new CountDownLatch(1);
        var listenerCancelled = new CompletableFuture<Context>();
        var callerInterrupted = new CompletableFuture<Boolean>();
        var writerInterrupted = new CompletableFuture<Boolean>();
        var contextCancelled = new CompletableFuture<Context>();
        RecordingWriter writer = new RecordingWriter() {
            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
                writing.countDown();
                try {
                    releaseWrite.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                } finally {
                    writerInterrupted.complete(Thread.currentThread().isInterrupted());
                }
            }
        };
        var handler = deadlineHandler("1H", (call, _) -> {
            Context.current().addListener(contextCancelled::complete, Runnable::run);
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
                contextCancelled.get(5, TimeUnit.SECONDS);
                releaseWrite.countDown();
                request.get(5, TimeUnit.SECONDS);
                assertThat(listenerCancelled.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
            } finally {
                releaseWrite.countDown();
            }
        }
        assertThat(callerInterrupted.get(5, TimeUnit.SECONDS), is(false));
        assertThat("transport closure must not interrupt a shared socket write",
                   writerInterrupted.get(5, TimeUnit.SECONDS), is(false));
        assertThat(writer.trailerWrites.get(), is(0));
    }

    @Test
    void testOversizedMessageCompletesResourceExhaustedAndNotifiesServiceOnce() throws Exception {
        var completed = new CompletableFuture<Context>();
        var cancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var writer = new RecordingWriter();
        var handler = deadlineHandler(null, (call, _) -> new ServerCall.Listener<>() {
            @Override
            public void onCancel() {
                cancellations.incrementAndGet();
            }

            @Override
            public void onComplete() {
                completions.incrementAndGet();
                call.close(Status.RESOURCE_EXHAUSTED, new Metadata());
                completed.complete(Context.current());
            }
        }, writer);
        handler.init();
        BufferData data = BufferData.create(5);
        data.write(0);
        data.writeUnsignedInt32(GrpcConfig.create().maxReadBufferSize() + 1);

        handler.data(Http2FrameHeader.create(data.available(), Http2FrameTypes.DATA,
                                             Http2Flag.DataFlags.create(0), 1), data);

        assertThat(writer.trailers.get(5, TimeUnit.SECONDS).httpHeaders()
                           .get(GrpcStatus.STATUS_NAME).asString().get(), is("8"));
        assertThat(completed.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
        assertThat(cancellations.get(), is(0));
        assertThat(completions.get(), is(1));
        assertThat(writer.trailerWrites.get(), is(1));
    }

    @Test
    void testDeadlineFinishesSocketWriteBeforeSendingTrailers() throws Exception {
        var writing = new CountDownLatch(1);
        var releaseWrite = new CountDownLatch(1);
        var writerExited = new CompletableFuture<Void>();
        var callerInterrupted = new CompletableFuture<Boolean>();
        var writerInterrupted = new CompletableFuture<Boolean>();
        var contextCancelled = new CompletableFuture<Context>();
        RecordingWriter writer = new RecordingWriter() {
            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
                writing.countDown();
                try {
                    releaseWrite.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                } finally {
                    writerInterrupted.complete(Thread.currentThread().isInterrupted());
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
        var handler = deadlineHandler("1S", (call, _) -> {
            Context.current().addListener(contextCancelled::complete, Runnable::run);
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
                contextCancelled.get(5, TimeUnit.SECONDS);
                releaseWrite.countDown();
                assertThat(writer.trailers.get(5, TimeUnit.SECONDS).httpHeaders()
                                   .get(GrpcStatus.STATUS_NAME).asString().get(), is("4"));
            } finally {
                releaseWrite.countDown();
            }
            request.get(5, TimeUnit.SECONDS);
        }
        assertThat(callerInterrupted.get(5, TimeUnit.SECONDS), is(false));
        assertThat("deadline must not interrupt a shared socket write",
                   writerInterrupted.get(5, TimeUnit.SECONDS), is(false));
        assertThat(writer.trailerWrites.get(), is(1));
    }

    @Test
    void testDeadlineCancelsStreamFlowControlWait() throws Exception {
        assertDeadlineCancelsFlowControlWait(false);
    }

    @Test
    void testDeadlineCancelsConnectionFlowControlWait() throws Exception {
        assertDeadlineCancelsFlowControlWait(true);
    }

    @Test
    void testBlockingCancellationListenerDoesNotBlockOtherDeadlines() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var firstWriter = new RecordingWriter();
        var first = deadlineHandler("1S", (_, _) -> {
            Context.current().addListener(_ -> {
                entered.countDown();
                await(release);
            }, Runnable::run);
            return new ServerCall.Listener<>() { };
        }, firstWriter);
        try {
            first.init();
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            var secondWriter = new RecordingWriter();
            var second = deadlineHandler("1S", (_, _) -> new ServerCall.Listener<>() { }, secondWriter);
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
                                                              GrpcConfig.create(),
                                                              metrics);
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
                                                              GrpcConfig.create(),
                                                              metrics);
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
                                                                      .build(),
                                                              metrics);
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
                                                                                GrpcConfig.create(),
                                                                                metrics);
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
                                                                                GrpcConfig.create(),
                                                                                metrics);

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
                                                GrpcConfig.create(),
                                                metrics);
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
                                                GrpcConfig.create(),
                                                metrics);
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
                                                GrpcConfig.create(),
                                                metrics);
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
                                                GrpcConfig.create(),
                                                metrics);
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
    void testCompressedMessageBodyMaySpanDataFrames() throws IOException {
        List<String> messages = new ArrayList<>();
        ServerCallHandler<String, String> callHandler = (call, _) -> {
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
                                                GrpcConfig.create(),
                                                metrics);
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
                                                GrpcConfig.create(),
                                                metrics);
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
                                                GrpcConfig.create(),
                                                metrics);
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
                                                GrpcConfig.create(),
                                                metrics);

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
                                                GrpcConfig.create(),
                                                metrics);
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
                                                                                GrpcConfig.create(),
                                                                                metrics);
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
    void testUnrelatedResourceExhaustedLogsErrorWithThrowable() {
        var failure = Status.RESOURCE_EXHAUSTED
                .withDescription("listener resource exhausted")
                .asRuntimeException();
        AtomicReference<Http2Headers> trailers = new AtomicReference<>();
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onHalfClose() {
                throw failure;
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
                                                                                GrpcConfig.create(),
                                                                                metrics);
        handler.init();
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                          1);

        List<LogRecord> records = captureLogRecords(() -> handler.data(header, data));

        assertThat(records, hasSize(1));
        LogRecord record = records.getFirst();
        assertAll(
                () -> assertThat(handler.streamState(), is(Http2StreamState.CLOSED)),
                () -> assertThat(trailers.get().httpHeaders().first(GrpcStatus.STATUS_NAME),
                                 is(Optional.of(String.valueOf(Status.Code.RESOURCE_EXHAUSTED.value())))),
                () -> assertThat(record.getLevel(), is(Level.SEVERE)),
                () -> assertThat(record.getThrown(), is(sameInstance(failure)))
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
                                                                                GrpcConfig.create(),
                                                                                metrics);

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
                GrpcConfig.create(),
                metrics);

        handler.init();

        assertThat(grpcConnectionContext.get().sniRequestedHost(), is(Optional.of("api.example.com")));
        assertThat(grpcConnectionContext.get().sniMatchedHost(), is(Optional.of("*.example.com")));
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

    private void requestFromWorkerWhileOnMessageIsActive(boolean queuedMessages, boolean withDeadline) throws Exception {
        var callReference = new AtomicReference<ServerCall<String, String>>();
        var requested = new CompletableFuture<CompletableFuture<Void>>();
        var release = new CompletableFuture<Void>();
        var secondReceived = new CompletableFuture<Void>();
        List<String> callbacks = new CopyOnWriteArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var handler = deadlineHandler(withDeadline ? "1H" : null, (call, _) -> {
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
                Future<?> input = null;
                if (queuedMessages) {
                    input = executor.submit(() -> {
                        sendStreamingRequest(handler, "first");
                        sendStreamingRequest(handler, "second");
                        sendStreamingRequest(handler, "third");
                    });
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
                    input = executor.submit(() -> sendStreamingRequest(handler, "third"));
                }
                assertThat("the third message still needs its own demand", callbacks, is(firstTwo));
                call.request(1);
                input.get(5, TimeUnit.SECONDS);
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

    private void terminalWriteWithTransportClose(boolean peerReset) throws Exception {
        var writing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var callReference = new AtomicReference<ServerCall<String, String>>();
        var cancellations = new AtomicInteger();
        var completions = new AtomicInteger();
        var terminal = new CompletableFuture<Context>();
        var writerInterrupted = new AtomicBoolean();
        RecordingWriter writer = new RecordingWriter() {
            @Override
            public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                if (flags.endOfStream()) {
                    writing.countDown();
                    await(release);
                    writerInterrupted.set(Thread.currentThread().isInterrupted());
                }
                return super.writeHeaders(headers, streamId, flags, flowControl);
            }
        };
        var handler = deadlineHandler("1H", (call, _) -> {
            callReference.set(call);
            return new ServerCall.Listener<>() {
                @Override
                public void onCancel() {
                    cancellations.incrementAndGet();
                    terminal.complete(Context.current());
                }

                @Override
                public void onComplete() {
                    completions.incrementAndGet();
                    terminal.complete(Context.current());
                }
            };
        }, writer);
        handler.init();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var closing = executor.submit(() -> callReference.get().close(Status.OK, new Metadata()));
            try {
                assertThat("the terminal write is blocked", writing.await(5, TimeUnit.SECONDS), is(true));
                var transportClosed = executor.submit(() -> {
                    if (peerReset) {
                        handler.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
                    } else {
                        // Normal HTTP/2 END_STREAM cleanup may close the sub-protocol inside writeHeaders.
                        handler.close();
                    }
                });
                transportClosed.get(5, TimeUnit.SECONDS);
                if (peerReset) {
                    assertThat("reset notifies cancellation before the shared writer is released",
                               terminal.get(5, TimeUnit.SECONDS).isCancelled(), is(true));
                    assertThat(cancellations.get(), is(1));
                    assertThat(completions.get(), is(0));
                } else {
                    assertThat("normal cleanup waits for terminal publication", terminal.isDone(), is(false));
                }
            } finally {
                release.countDown();
            }
            closing.get(5, TimeUnit.SECONDS);
        }
        terminal.get(5, TimeUnit.SECONDS);
        handler.rstStream(new Http2RstStream(Http2ErrorCode.CANCEL));
        callReference.get().close(Status.OK, new Metadata());
        assertThat(cancellations.get(), is(peerReset ? 1 : 0));
        assertThat(completions.get(), is(peerReset ? 0 : 1));
        assertThat("reset does not interrupt the shared transport writer", writerInterrupted.get(), is(false));
        assertThat(writer.trailerWrites.get(), is(1));
        assertThat(handler.streamState(), is(Http2StreamState.CLOSED));
    }

    private void closeFromWorkerWhileCallbackIsActive(String callback, boolean withDeadline) throws Exception {
        var callReference = new AtomicReference<ServerCall<String, String>>();
        var closing = new CompletableFuture<CompletableFuture<Void>>();
        var release = new CompletableFuture<Void>();
        List<String> callbacks = new CopyOnWriteArrayList<>();
        var writer = new RecordingWriter();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var handler = deadlineHandler(withDeadline ? "1H" : null, (call, _) -> {
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
                        callReference.get().request(1);
                        sendStreamingRequest(handler, "request");
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

    private void requestedMessagesPrecedeHalfClose(boolean withDeadline) throws Exception {
        var callReference = new AtomicReference<ServerCall<String, String>>();
        var requested = new CompletableFuture<CompletableFuture<Void>>();
        var release = new CompletableFuture<Void>();
        var writer = new RecordingWriter();
        List<String> callbacks = new CopyOnWriteArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var handler = deadlineHandler(withDeadline ? "1H" : null, (call, _) -> {
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
                assertThat("messages wait for demand", callbacks, is(List.of()));
                call.request(1);
                BufferData messages = BufferData.create(grpcData("first"), grpcData("second"), grpcData("third"));
                var delivery = executor.submit(() -> handler.data(
                        Http2FrameHeader.create(messages.available(), Http2FrameTypes.DATA,
                                               Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM), 1), messages));
                requested.get(5, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS);
                assertThat("callbacks wait for the active message callback",
                           callbacks, is(List.of("entered first")));
                release.complete(null);
                delivery.get(5, TimeUnit.SECONDS);

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

    private void inboundDataWaitsForActiveMessageCallback(boolean withDeadline) throws Exception {
        var callReference = new AtomicReference<ServerCall<String, String>>();
        var entered = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
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
                        return message;
                    }
                })
                .build();
        var writer = new RecordingWriter();
        var handler = deadlineHandler(withDeadline ? "1H" : null, (call, _) -> {
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
            call.request(1);
            BufferData data = BufferData.create(grpcData("first"), grpcData("second"), grpcData("third"));
            var inbound = executor.submit(() -> handler.data(
                    Http2FrameHeader.create(data.available(), Http2FrameTypes.DATA, Http2Flag.DataFlags.create(0), 1), data));
            entered.get(5, TimeUnit.SECONDS);
            executor.submit(() -> call.request(2)).get(5, TimeUnit.SECONDS);
            assertThat("request returns while the first callback remains active",
                       callbacks, is(List.of("entered first")));

            assertThrows(TimeoutException.class, () -> inbound.get(200, TimeUnit.MILLISECONDS),
                         "DATA must wait for the active callback instead of queuing the remaining messages");
            assertThat("the same DATA frame is not parsed ahead of the active callback",
                       parsed, is(List.of("first")));
            assertThat("message callbacks remain serialized", callbacks, is(List.of("entered first")));

            release.complete(null);
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


    private GrpcProtocolHandler<String, String> deadlineHandler(String timeout,
                                                                       ServerCallHandler<String, String> callHandler,
                                                                       Http2StreamWriter writer) {
        return deadlineHandler(timeout, callHandler, writer, stringMethodDescriptor());
    }

    private GrpcProtocolHandler<String, String> deadlineHandler(String timeout,
                                                                       ServerCallHandler<String, String> callHandler,
                                                                       Http2StreamWriter writer,
                                                                       MethodDescriptor<String, String> descriptor) {
        return deadlineHandler(timeout, callHandler, writer, descriptor, null);
    }

    private GrpcProtocolHandler<String, String> deadlineHandler(String timeout,
                                                                       ServerCallHandler<String, String> callHandler,
                                                                       Http2StreamWriter writer,
                                                                       MethodDescriptor<String, String> descriptor,
                                                                       StreamFlowControl flowControl) {
        WritableHeaders<?> headers = WritableHeaders.create();
        if (timeout != null) {
            headers.add(HeaderNames.create("grpc-timeout"), timeout);
        }
        return new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                         Http2Headers.create(headers),
                                         writer,
                                         1,
                                         flowControl,
                                         Http2StreamState.OPEN,
                                         GrpcRouteHandler.methodDefinition(ServerMethodDefinition.create(descriptor, callHandler),
                                                                          null,
                                                                          WeightedBag.create()),
                                         GrpcConfig.create(), metrics);
    }

    private void assertDeadlineCancelsFlowControlWait(boolean connectionWindow) throws Exception {
        var waiting = new CountDownLatch(1);
        var writerInterrupted = new CompletableFuture<Boolean>();
        var callerInterrupted = new CompletableFuture<Boolean>();
        var connection = ConnectionFlowControl.serverBuilder((_, _) -> { })
                .blockTimeout(Duration.ofMinutes(1))
                .build();
        var flowControl = connection.createStreamFlowControl(1, 65535, 16384);
        if (connectionWindow) {
            connection.outbound().decrementWindowSize(65535);
        } else {
            flowControl.outbound().resetStreamWindowSize(0);
        }
        RecordingWriter writer = new RecordingWriter() {
            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound outbound) {
                assertThat("outbound window is exhausted", outbound.getRemainingWindowSize(), is(0));
                waiting.countDown();
                try {
                    outbound.blockTillUpdate();
                    throw new AssertionError("cancelled flow-control wait must not resume DATA writes");
                } finally {
                    writerInterrupted.complete(Thread.currentThread().isInterrupted());
                }
            }
        };
        var handler = deadlineHandler("1S", (call, _) -> {
            call.request(1);
            return new ServerCall.Listener<>() {
                @Override
                public void onMessage(String message) {
                    call.sendMessage("response");
                    callerInterrupted.complete(Thread.currentThread().isInterrupted());
                }
            };
        }, writer, stringMethodDescriptor(), flowControl);
        handler.init();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = executor.submit(() -> sendRequest(handler));
            try {
                assertThat("writer reached exhausted flow control", waiting.await(5, TimeUnit.SECONDS), is(true));
                assertThat(writer.trailers.get(5, TimeUnit.SECONDS).httpHeaders()
                                   .get(GrpcStatus.STATUS_NAME).asString().get(), is("4"));
                request.get(5, TimeUnit.SECONDS);
            } finally {
                // Also release the real flow-control wait if an assertion fails before cancellation.
                connection.incrementOutboundConnectionWindowSize(65535);
                flowControl.outbound().incrementStreamWindowSize(65535);
                handler.close();
            }
        }
        assertThat("cancellation interrupt is consumed before leaving flow control",
                   writerInterrupted.get(5, TimeUnit.SECONDS), is(false));
        assertThat("application caller is not interrupted", callerInterrupted.get(5, TimeUnit.SECONDS), is(false));
        assertThat("one terminal status", writer.trailerWrites.get(), is(1));
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
            public AuthorityCheck checkAuthority(UriAuthority authority) {
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

    private static byte[] gzip(String content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
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

    private static List<LogRecord> captureLogRecords(Runnable task) {
        Logger logger = Logger.getLogger(GrpcProtocolHandler.class.getName());
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        try {
            task.run();
            return records;
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    private ServerCall<String, String> createServerCall(Http2StreamWriter streamWriter) {
        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                                Http2Headers.create(WritableHeaders.create()),
                                                                                streamWriter,
                                                                                1,
                                                                                null,
                                                                                Http2StreamState.OPEN,
                                                                                route(new ServerCall.Listener<>() {
                                                                                }),
                                                                                GrpcConfig.create(),
                                                                                metrics);
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "identity");
        handler.initCompression(null, headers);
        return handler.createServerCall();
    }

    private void assertOversizedMessage(long messageSize, int maxReadBufferSize) {
        AtomicReference<Http2Headers> trailers = new AtomicReference<>();
        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                                Http2Headers.create(WritableHeaders.create()),
                                                                                headersCapturingWriter(trailers),
                                                                                1,
                                                                                null,
                                                                                Http2StreamState.OPEN,
                                                                                route(new ServerCall.Listener<>() {
                                                                                }),
                                                                                GrpcConfig.builder()
                                                                                        .maxReadBufferSize(maxReadBufferSize)
                                                                                        .build(),
                                                                                metrics);
        handler.init();
        BufferData data = BufferData.create(5);
        data.write(0);
        data.writeUnsignedInt32(messageSize);
        Http2FrameHeader header = Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                          1);

        handler.data(header, data);

        assertAll(
                () -> assertThat(handler.streamState(), is(Http2StreamState.CLOSED)),
                () -> assertThat(trailers.get().httpHeaders().first(GrpcStatus.STATUS_NAME),
                                 is(Optional.of(String.valueOf(Status.Code.RESOURCE_EXHAUSTED.value())))),
                () -> assertThat(trailers.get().httpHeaders().first(GrpcStatus.MESSAGE_NAME),
                                 is(Optional.of("gRPC message exceeds maximum configured size")))
        );
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
