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

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcClientStreamsTest {
    private static final io.grpc.Context.Key<String> GRPC_VALUE = io.grpc.Context.key("test-value");
    private static final Metadata.Key<String> TRAILER = Metadata.Key.of("test-trailer", Metadata.ASCII_STRING_MARSHALLER);
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                                                                                         .name("grpc-client-stream-test-", 0)
                                                                                         .factory());

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void closingActiveServerResponseStreamCancelsCall() {
        TestClientCall<String, String> call = new TestClientCall<>(true);

        Stream<String> responses = GrpcClientStreams.serverStreaming(call, "request");
        responses.close();

        assertThat(call.cancelled.get(), is(true));
    }

    @Test
    void requestStreamWaitsForReadinessAndRetainsContexts() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(false);
        AtomicInteger advances = new AtomicInteger();
        AtomicBoolean emitted = new AtomicBoolean();
        AtomicReference<String> grpcValue = new AtomicReference<>();
        AtomicReference<String> helidonValue = new AtomicReference<>();
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch sourceClosed = new CountDownLatch(1);
        Context context = Context.create();
        context.register("test-value", "helidon");
        Stream<String> requests = StreamSupport.stream(new Spliterator<String>() {
            @Override
            public boolean tryAdvance(Consumer<? super String> action) {
                advances.incrementAndGet();
                threadName.set(Thread.currentThread().getName());
                grpcValue.set(GRPC_VALUE.get());
                helidonValue.set(Contexts.context()
                                         .flatMap(it -> it.get("test-value", String.class))
                                         .orElse(null));
                if (emitted.compareAndSet(false, true)) {
                    action.accept("request");
                    return true;
                }
                return false;
            }

            @Override
            public Spliterator<String> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return 1;
            }

            @Override
            public int characteristics() {
                return ORDERED | NONNULL;
            }
        }, false).onClose(sourceClosed::countDown);
        AtomicReference<Stream<String>> responses = new AtomicReference<>();
        io.grpc.Context.current()
                .withValue(GRPC_VALUE, "grpc")
                .run(() -> Contexts.runInContext(context,
                                                 () -> responses.set(GrpcClientStreams.bidirectional(call,
                                                                                                     requests,
                                                                                                     executor))));

        assertThat("source is not consumed while transport is not ready",
                   call.started.await(10, TimeUnit.SECONDS),
                   is(true));
        assertThat(advances.get(), is(0));

        call.ready(true);

        assertThat("request stream completed", call.halfClosed.await(10, TimeUnit.SECONDS), is(true));
        assertThat(call.requests, is(List.of("request")));
        assertThat("request stream closed", sourceClosed.await(10, TimeUnit.SECONDS), is(true));
        assertThat(grpcValue.get(), is("grpc"));
        assertThat(helidonValue.get(), is("helidon"));
        assertThat(threadName.get(), startsWith("grpc-client-stream-test-"));

        responses.get().close();
        assertThat(call.cancelled.get(), is(true));
    }

    @Test
    void queuedRequestSourceIsClosedWhenCancelledBeforeSenderStarts() throws Exception {
        ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        singleExecutor.submit(() -> {
            blockerStarted.countDown();
            releaseBlocker.await();
            return null;
        });
        assertThat(blockerStarted.await(10, TimeUnit.SECONDS), is(true));

        TestClientCall<String, String> call = new TestClientCall<>(true);
        AtomicInteger advances = new AtomicInteger();
        CountDownLatch sourceClosed = new CountDownLatch(1);
        Stream<String> requests = Stream.of("request")
                .peek(ignored -> advances.incrementAndGet())
                .onClose(sourceClosed::countDown);
        Stream<String> responses = GrpcClientStreams.bidirectional(call, requests, singleExecutor);

        responses.close();
        assertThat(sourceClosed.await(10, TimeUnit.SECONDS), is(true));
        assertThat(advances.get(), is(0));

        releaseBlocker.countDown();
        singleExecutor.close();
        assertThat(advances.get(), is(0));
    }

    @Test
    void rejectedInitialSenderClosesSourceAndCancelsCallOnce() {
        ExecutorService rejectingExecutor = Executors.newSingleThreadExecutor();
        rejectingExecutor.shutdown();
        TestClientCall<String, String> call = new TestClientCall<>(true);
        AtomicInteger sourceCloseCount = new AtomicInteger();
        IllegalStateException closeFailure = new IllegalStateException("close failed");
        Stream<String> requests = Stream.of("request").onClose(() -> {
            sourceCloseCount.incrementAndGet();
            throw closeFailure;
        });

        RejectedExecutionException failure = assertThrows(
                RejectedExecutionException.class,
                () -> GrpcClientStreams.bidirectional(call, requests, rejectingExecutor));

        assertThat(call.cancelCount.get(), is(1));
        assertThat(call.cancelCause.get(), sameInstance(failure));
        assertThat(sourceCloseCount.get(), is(1));
        assertThat(failure.getSuppressed().length, is(1));
        assertThat(failure.getSuppressed()[0], sameInstance(closeFailure));
    }

    @Test
    void rejectedReadinessResumeIsolatesSourceClose() throws Exception {
        ExecutorService senderExecutor = Executors.newSingleThreadExecutor();
        TestClientCall<String, String> call = new TestClientCall<>(false);
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        CountDownLatch closeCompleted = new CountDownLatch(1);
        AtomicInteger closeCount = new AtomicInteger();
        AtomicReference<String> closeThread = new AtomicReference<>();
        IllegalStateException closeFailure = new IllegalStateException("close failed");
        Stream<String> requests = Stream.of("request").onClose(() -> {
            closeCount.incrementAndGet();
            closeThread.set(Thread.currentThread().getName());
            closeStarted.countDown();
            try {
                releaseClose.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                closeCompleted.countDown();
            }
            throw closeFailure;
        });
        GrpcClientStreams.bidirectional(call, requests, senderExecutor);

        assertThat(call.started.await(10, TimeUnit.SECONDS), is(true));
        senderExecutor.submit(() -> { }).get(10, TimeUnit.SECONDS);
        senderExecutor.shutdown();
        CompletableFuture<Void> ready = CompletableFuture.runAsync(() -> call.ready(true), executor);
        try {
            ready.get(10, TimeUnit.SECONDS);
            assertThat(closeStarted.await(10, TimeUnit.SECONDS), is(true));
            assertThat(closeThread.get(), startsWith("helidon-grpc-client-request-stream-close-"));
            assertThat(closeCount.get(), is(1));
            assertThat(call.cancelCount.get(), is(1));
            assertThat(call.cancelCause.get(), instanceOf(RejectedExecutionException.class));
        } finally {
            releaseClose.countDown();
            senderExecutor.close();
        }
        assertThat(closeCompleted.await(10, TimeUnit.SECONDS), is(true));
        assertThat(closeCount.get(), is(1));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (call.cancelCause.get().getSuppressed().length == 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(call.cancelCause.get().getSuppressed().length, is(1));
        assertThat(call.cancelCause.get().getSuppressed()[0], sameInstance(closeFailure));
    }

    @Test
    void runningRequestSourceIsClosedPromptlyWhenCancelled() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        CountDownLatch iteratorStarted = new CountDownLatch(1);
        CountDownLatch releaseIterator = new CountDownLatch(1);
        CountDownLatch sourceClosed = new CountDownLatch(1);
        Stream<String> requests = StreamSupport.stream(new Spliterator<String>() {
            @Override
            public boolean tryAdvance(Consumer<? super String> action) {
                iteratorStarted.countDown();
                while (releaseIterator.getCount() != 0) {
                    try {
                        releaseIterator.await();
                    } catch (InterruptedException ignored) {
                        // Simulate a user source that does not cooperate with interruption.
                    }
                }
                return false;
            }

            @Override
            public Spliterator<String> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return 0;
            }

            @Override
            public int characteristics() {
                return ORDERED | NONNULL;
            }
        }, false).onClose(sourceClosed::countDown);
        Stream<String> responses = GrpcClientStreams.bidirectional(call, requests, executor);

        assertThat(iteratorStarted.await(10, TimeUnit.SECONDS), is(true));
        try {
            responses.close();
            assertThat("request source closed while iterator is blocked",
                       sourceClosed.await(10, TimeUnit.SECONDS),
                       is(true));
        } finally {
            releaseIterator.countDown();
        }
    }

    @Test
    void lastRequestHalfClosesWhenSendMakesCallNotReady() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        call.pauseAfterNextMessage();

        Stream<String> responses = GrpcClientStreams.bidirectional(call, Stream.of("request"), executor);

        assertThat(call.halfClosed.await(10, TimeUnit.SECONDS), is(true));
        assertThat(call.requests, is(List.of("request")));
        responses.close();
    }

    @Test
    void requestSenderStagesAtMostOneRequestWhileNotReady() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        call.pauseAfterNextMessage();
        AtomicInteger advances = new AtomicInteger();
        Stream<String> requests = Stream.of("one", "two", "three").peek(ignored -> advances.incrementAndGet());

        Stream<String> responses = GrpcClientStreams.bidirectional(call, requests, executor);

        assertThat(call.firstMessage.await(10, TimeUnit.SECONDS), is(true));
        assertThat(call.requests, is(List.of("one")));
        assertThat(advances.get(), is(2));
        assertThat(call.halfClosed.getCount(), is(1L));

        call.ready(true);
        assertThat(call.halfClosed.await(10, TimeUnit.SECONDS), is(true));
        assertThat(call.requests, is(List.of("one", "two", "three")));
        responses.close();
    }

    @Test
    void bidirectionalSendIsSerializedWithConcurrentPeerTermination() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        CountDownLatch sendEntered = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        call.blockNextMessage(sendEntered, releaseSend);

        Stream<String> responses = GrpcClientStreams.bidirectional(call, Stream.of("one", "two"), executor);
        assertThat("request send entered", sendEntered.await(10, TimeUnit.SECONDS), is(true));

        CompletableFuture<Void> terminal = CompletableFuture.runAsync(
                () -> call.close(Status.ABORTED, new Metadata()),
                executor);
        assertThrows(TimeoutException.class, () -> terminal.get(100, TimeUnit.MILLISECONDS));

        releaseSend.countDown();
        terminal.get(10, TimeUnit.SECONDS);

        assertThat(call.requests, is(List.of("one")));
        assertThat(call.halfClosed.getCount(), is(1L));
        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class,
                                                      () -> responses.iterator().hasNext());
        assertThat(failure.getStatus().getCode(), is(Status.Code.ABORTED));
    }

    @Test
    void emptyBidirectionalCompletionIsSerializedWithConcurrentPeerTermination() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        CountDownLatch halfCloseEntered = new CountDownLatch(1);
        CountDownLatch releaseHalfClose = new CountDownLatch(1);
        call.blockHalfClose(halfCloseEntered, releaseHalfClose);

        Stream<String> responses = GrpcClientStreams.bidirectional(call, Stream.empty(), executor);
        assertThat("half-close entered", halfCloseEntered.await(10, TimeUnit.SECONDS), is(true));

        CompletableFuture<Void> terminal = CompletableFuture.runAsync(
                () -> call.close(Status.ABORTED, new Metadata()),
                executor);
        assertThrows(TimeoutException.class, () -> terminal.get(100, TimeUnit.MILLISECONDS));

        releaseHalfClose.countDown();
        terminal.get(10, TimeUnit.SECONDS);
        assertThat(call.halfCloseCount.get(), is(1));
        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class,
                                                      () -> responses.iterator().hasNext());
        assertThat(failure.getStatus().getCode(), is(Status.Code.ABORTED));
    }

    @Test
    void clientStreamingRejectsMissingResponse() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        CompletableFuture<String> response = CompletableFuture.supplyAsync(
                () -> GrpcClientStreams.clientStreaming(call, Stream.empty()));

        assertThat(call.halfClosed.await(10, TimeUnit.SECONDS), is(true));
        call.close(Status.OK, new Metadata());

        CompletionException failure = assertThrows(CompletionException.class, response::join);
        assertThat(failure.getCause(), instanceOf(StatusRuntimeException.class));
        assertThat(((StatusRuntimeException) failure.getCause()).getStatus().getCode(), is(Status.Code.INTERNAL));
    }

    @Test
    void clientStreamingRejectsMultipleResponses() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        CompletableFuture<String> response = CompletableFuture.supplyAsync(
                () -> GrpcClientStreams.clientStreaming(call, Stream.empty()));

        assertThat(call.halfClosed.await(10, TimeUnit.SECONDS), is(true));
        call.message("first");
        StatusRuntimeException protocolFailure = assertThrows(StatusRuntimeException.class,
                                                              () -> call.message("second"));
        call.close(protocolFailure.getStatus(), new Metadata());

        CompletionException failure = assertThrows(CompletionException.class, response::join);
        assertThat(failure.getCause(), instanceOf(StatusRuntimeException.class));
        assertThat(((StatusRuntimeException) failure.getCause()).getStatus().getCode(), is(Status.Code.INTERNAL));
    }

    @Test
    void streamingResponseDeliversBufferedMessageBeforeTerminalError() {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        Stream<String> responses = GrpcClientStreams.serverStreaming(call, "request");
        Iterator<String> iterator = responses.iterator();

        call.message("response");
        call.close(Status.ABORTED, new Metadata());

        assertThat(iterator.next(), is("response"));
        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class, iterator::hasNext);
        assertThat(failure.getStatus().getCode(), is(Status.Code.ABORTED));
    }

    @Test
    void clientStreamingPreservesPeerStatusAndTrailers() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(false);
        CompletableFuture<String> response = CompletableFuture.supplyAsync(
                () -> GrpcClientStreams.clientStreaming(call, Stream.of("request")));
        Metadata trailers = new Metadata();
        trailers.put(TRAILER, "detail");

        assertThat(call.started.await(10, TimeUnit.SECONDS), is(true));
        call.close(Status.PERMISSION_DENIED, trailers);

        CompletionException failure = assertThrows(CompletionException.class, response::join);
        assertThat(failure.getCause(), instanceOf(StatusRuntimeException.class));
        StatusRuntimeException status = (StatusRuntimeException) failure.getCause();
        assertThat(status.getStatus().getCode(), is(Status.Code.PERMISSION_DENIED));
        assertThat(status.getTrailers().get(TRAILER), is("detail"));
    }

    @Test
    void clientStreamingConsumesRequestsOnCallingThreadWhenReady() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(false);
        AtomicReference<String> sourceThread = new AtomicReference<>();
        Stream<String> requests = Stream.of("request").peek(ignored -> sourceThread.set(Thread.currentThread().getName()));
        ExecutorService caller = Executors.newSingleThreadExecutor(Thread.ofPlatform()
                                                                           .name("grpc-client-stream-caller")
                                                                           .factory());
        try {
            CompletableFuture<String> response = CompletableFuture.supplyAsync(
                    () -> GrpcClientStreams.clientStreaming(call, requests),
                    caller);

            assertThat(call.started.await(10, TimeUnit.SECONDS), is(true));
            assertThat(call.requests, is(List.of()));

            call.ready(true);
            assertThat(call.halfClosed.await(10, TimeUnit.SECONDS), is(true));
            call.message("response");
            call.close(Status.OK, new Metadata());

            assertThat(response.get(10, TimeUnit.SECONDS), is("response"));
            assertThat(sourceThread.get(), is("grpc-client-stream-caller"));
        } finally {
            caller.close();
        }
    }

    @Test
    void clientStreamingStopsWithoutAdvancingSourceWhenPeerClosesDuringSend() {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        call.closeAfterNextMessage(Status.ABORTED);
        AtomicInteger advances = new AtomicInteger();
        Stream<String> requests = Stream.of("one", "two").peek(ignored -> advances.incrementAndGet());

        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class,
                                                       () -> GrpcClientStreams.clientStreaming(call, requests));

        assertThat(failure.getStatus().getCode(), is(Status.Code.ABORTED));
        assertThat(call.requests, is(List.of("one")));
        assertThat(advances.get(), is(1));
        assertThat(call.halfClosed.getCount(), is(1L));
    }

    @Test
    void clientStreamingDoesNotHalfCloseAfterConcurrentPeerTermination() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        CountDownLatch sourceEntered = new CountDownLatch(1);
        CountDownLatch releaseSource = new CountDownLatch(1);
        Stream<String> requests = StreamSupport.stream(new Spliterator<String>() {
            @Override
            public boolean tryAdvance(Consumer<? super String> action) {
                sourceEntered.countDown();
                try {
                    releaseSource.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                }
                return false;
            }

            @Override
            public Spliterator<String> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return 0;
            }

            @Override
            public int characteristics() {
                return ORDERED | NONNULL;
            }
        }, false);
        CompletableFuture<String> response = CompletableFuture.supplyAsync(
                () -> GrpcClientStreams.clientStreaming(call, requests),
                executor);

        assertThat(sourceEntered.await(10, TimeUnit.SECONDS), is(true));
        call.close(Status.ABORTED, new Metadata());
        releaseSource.countDown();

        CompletionException failure = assertThrows(CompletionException.class, response::join);
        assertThat(((StatusRuntimeException) failure.getCause()).getStatus().getCode(), is(Status.Code.ABORTED));
        assertThat(call.halfClosed.getCount(), is(1L));
    }

    @Test
    void peerTerminationDoesNotCloseRequestSourceOnCallbackThread() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(false);
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        AtomicBoolean closeInterrupted = new AtomicBoolean();
        Stream<String> requests = Stream.of("request").onClose(() -> {
            closeStarted.countDown();
            try {
                releaseClose.await();
            } catch (InterruptedException e) {
                closeInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        CompletableFuture<String> response = CompletableFuture.supplyAsync(
                () -> GrpcClientStreams.clientStreaming(call, requests),
                executor);

        assertThat(call.started.await(10, TimeUnit.SECONDS), is(true));
        CompletableFuture<Void> terminal = CompletableFuture.runAsync(
                () -> call.close(Status.ABORTED, new Metadata()),
                executor);
        try {
            terminal.get(10, TimeUnit.SECONDS);
            assertThat(closeStarted.await(10, TimeUnit.SECONDS), is(true));
        } finally {
            releaseClose.countDown();
        }

        CompletionException failure = assertThrows(CompletionException.class, response::join);
        assertThat(((StatusRuntimeException) failure.getCause()).getStatus().getCode(), is(Status.Code.ABORTED));
        assertThat(closeInterrupted.get(), is(false));
    }

    @Test
    void bidirectionalPeerTerminationIsolatesRequestSourceClose() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        CountDownLatch iteratorStarted = new CountDownLatch(1);
        CountDownLatch releaseIterator = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        CountDownLatch closeCompleted = new CountDownLatch(1);
        AtomicInteger closeCount = new AtomicInteger();
        AtomicReference<String> closeThread = new AtomicReference<>();
        AtomicReference<String> grpcValue = new AtomicReference<>();
        AtomicReference<String> helidonValue = new AtomicReference<>();
        Stream<String> requests = StreamSupport.stream(new Spliterator<String>() {
            @Override
            public boolean tryAdvance(Consumer<? super String> action) {
                iteratorStarted.countDown();
                while (releaseIterator.getCount() != 0) {
                    try {
                        releaseIterator.await();
                    } catch (InterruptedException ignored) {
                        // Simulate a user source that does not cooperate with interruption.
                    }
                }
                return false;
            }

            @Override
            public Spliterator<String> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return 0;
            }

            @Override
            public int characteristics() {
                return ORDERED | NONNULL;
            }
        }, false).onClose(() -> {
            closeCount.incrementAndGet();
            closeThread.set(Thread.currentThread().getName());
            grpcValue.set(GRPC_VALUE.get());
            helidonValue.set(Contexts.context()
                                     .flatMap(it -> it.get("test-value", String.class))
                                     .orElse(null));
            closeStarted.countDown();
            try {
                releaseClose.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                closeCompleted.countDown();
            }
        });
        Context context = Context.create();
        context.register("test-value", "helidon");
        AtomicReference<Stream<String>> responses = new AtomicReference<>();
        io.grpc.Context.current()
                .withValue(GRPC_VALUE, "grpc")
                .run(() -> Contexts.runInContext(context,
                                                 () -> responses.set(GrpcClientStreams.bidirectional(call,
                                                                                                     requests,
                                                                                                     executor))));

        assertThat(iteratorStarted.await(10, TimeUnit.SECONDS), is(true));
        CompletableFuture<Void> terminal = CompletableFuture.runAsync(
                () -> call.close(Status.ABORTED, new Metadata()),
                executor);
        try {
            terminal.get(10, TimeUnit.SECONDS);
            assertThat(closeStarted.await(10, TimeUnit.SECONDS), is(true));
            assertThat(closeThread.get(), startsWith("helidon-grpc-client-request-stream-close-"));
            assertThat(grpcValue.get(), is("grpc"));
            assertThat(helidonValue.get(), is("helidon"));
            assertThat(closeCount.get(), is(1));
            assertThat(call.requests, is(List.of()));
            assertThat(call.halfClosed.getCount(), is(1L));

            StatusRuntimeException failure = assertThrows(StatusRuntimeException.class,
                                                          responses.get().iterator()::hasNext);
            assertThat(failure.getStatus().getCode(), is(Status.Code.ABORTED));
        } finally {
            releaseClose.countDown();
            releaseIterator.countDown();
        }
        assertThat(closeCompleted.await(10, TimeUnit.SECONDS), is(true));
        assertThat(closeCount.get(), is(1));
    }

    @Test
    void failedRequestSourceCannotBeRescheduledBeforeTerminalCallback() throws Exception {
        TestClientCall<String, String> call = new TestClientCall<>(true);
        IllegalStateException sourceFailure = new IllegalStateException("source failed");
        CountDownLatch secondAdvance = new CountDownLatch(1);
        CountDownLatch sourceClosed = new CountDownLatch(1);
        AtomicInteger advances = new AtomicInteger();
        Stream<String> requests = StreamSupport.stream(new Spliterator<String>() {
            @Override
            public boolean tryAdvance(Consumer<? super String> action) {
                if (advances.incrementAndGet() > 1) {
                    secondAdvance.countDown();
                }
                throw sourceFailure;
            }

            @Override
            public Spliterator<String> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return 1;
            }

            @Override
            public int characteristics() {
                return ORDERED | NONNULL;
            }
        }, false).onClose(sourceClosed::countDown);
        Stream<String> responses = GrpcClientStreams.bidirectional(call, requests, executor);

        assertThat(call.cancelledSignal.await(10, TimeUnit.SECONDS), is(true));
        assertThat(sourceClosed.await(10, TimeUnit.SECONDS), is(true));
        call.ready(true);

        assertThat("failed source was rescheduled", secondAdvance.await(500, TimeUnit.MILLISECONDS), is(false));
        assertThat(advances.get(), is(1));
        assertThat(call.cancelCount.get(), is(1));
        responses.close();
    }

    @Test
    void clientStreamingClosesSourceAndCancelsCallWhenStartFails() {
        IllegalStateException startFailure = new IllegalStateException("start failed");
        TestClientCall<String, String> call = new TestClientCall<>(true, startFailure);
        AtomicBoolean sourceClosed = new AtomicBoolean();
        Stream<String> requests = Stream.of("request").onClose(() -> sourceClosed.set(true));

        assertThat(assertThrows(IllegalStateException.class,
                                () -> GrpcClientStreams.clientStreaming(call, requests)),
                   sameInstance(startFailure));
        assertThat(sourceClosed.get(), is(true));
        assertThat(call.cancelled.get(), is(true));
    }

    @Test
    void bidirectionalClosesSourceAndCancelsCallWhenStartFails() {
        IllegalStateException startFailure = new IllegalStateException("start failed");
        TestClientCall<String, String> call = new TestClientCall<>(true, startFailure);
        AtomicBoolean sourceClosed = new AtomicBoolean();
        Stream<String> requests = Stream.of("request").onClose(() -> sourceClosed.set(true));

        assertThat(assertThrows(IllegalStateException.class,
                                () -> GrpcClientStreams.bidirectional(call, requests, executor)),
                   sameInstance(startFailure));
        assertThat(sourceClosed.get(), is(true));
        assertThat(call.cancelled.get(), is(true));
    }

    @Test
    void resourceStreamDefaultsFailSynchronously() {
        GrpcServiceClient client = new UnsupportedServiceClient();
        AtomicBoolean clientRequestsClosed = new AtomicBoolean();
        AtomicBoolean bidiRequestsClosed = new AtomicBoolean();
        AtomicBoolean unownedRequestsClosed = new AtomicBoolean();

        assertThrows(NullPointerException.class, () -> client.serverStreaming(null, "request"));
        assertThrows(NullPointerException.class, () -> client.serverStreaming("Stream", null));
        assertThrows(NullPointerException.class, () -> client.clientStreaming("Stream", null));
        assertThrows(NullPointerException.class, () -> client.bidirectional("Stream", null));
        assertThrows(NullPointerException.class,
                     () -> client.clientStreaming(null,
                                                  Stream.of("request")
                                                          .onClose(() -> unownedRequestsClosed.set(true))));
        assertThat("a null method name is rejected before request ownership transfers",
                   unownedRequestsClosed.get(),
                   is(false));

        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
                                                              () -> client.serverStreaming("Stream", "request"));
        assertThat(failure.getMessage(), is("Resource-owning gRPC streams are not supported by this client."));
        assertThrows(UnsupportedOperationException.class,
                     () -> client.clientStreaming("Stream",
                                                  Stream.of("request")
                                                          .onClose(() -> clientRequestsClosed.set(true))));
        assertThrows(UnsupportedOperationException.class,
                     () -> client.bidirectional("Stream",
                                                Stream.of("request")
                                                        .onClose(() -> bidiRequestsClosed.set(true))));
        assertThat(clientRequestsClosed.get(), is(true));
        assertThat(bidiRequestsClosed.get(), is(true));
    }

    private static final class UnsupportedServiceClient implements GrpcServiceClient {

        @Override
        public String serviceName() {
            return "test";
        }

        @Override
        public <ReqT, ResT> ResT unary(String methodName, ReqT request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <ReqT, ResT> void unary(String methodName, ReqT request, StreamObserver<ResT> response) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <ReqT, ResT> Iterator<ResT> serverStream(String methodName, ReqT request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <ReqT, ResT> void serverStream(String methodName, ReqT request, StreamObserver<ResT> response) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <ReqT, ResT> ResT clientStream(String methodName, Iterator<ReqT> request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <ReqT, ResT> StreamObserver<ReqT> clientStream(String methodName, StreamObserver<ResT> response) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <ReqT, ResT> Iterator<ResT> bidi(String methodName, Iterator<ReqT> request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <ReqT, ResT> StreamObserver<ReqT> bidi(String methodName, StreamObserver<ResT> response) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestClientCall<ReqT, ResT> extends ClientCall<ReqT, ResT> {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CountDownLatch cancelledSignal = new CountDownLatch(1);
        private final AtomicInteger cancelCount = new AtomicInteger();
        private final AtomicReference<Throwable> cancelCause = new AtomicReference<>();
        private final AtomicBoolean ready = new AtomicBoolean();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch halfClosed = new CountDownLatch(1);
        private final CountDownLatch firstMessage = new CountDownLatch(1);
        private final List<ReqT> requests = new CopyOnWriteArrayList<>();
        private final AtomicBoolean pauseAfterNextMessage = new AtomicBoolean();
        private final AtomicInteger halfCloseCount = new AtomicInteger();
        private final RuntimeException startFailure;
        private volatile Status closeAfterNextMessage;
        private volatile Listener<ResT> listener;
        private volatile CountDownLatch sendEntered;
        private volatile CountDownLatch releaseSend;
        private volatile CountDownLatch halfCloseEntered;
        private volatile CountDownLatch releaseHalfClose;

        private TestClientCall(boolean ready) {
            this(ready, null);
        }

        private TestClientCall(boolean ready, RuntimeException startFailure) {
            this.ready.set(ready);
            this.startFailure = startFailure;
        }

        @Override
        public void start(Listener<ResT> responseListener, Metadata headers) {
            if (startFailure != null) {
                throw startFailure;
            }
            listener = responseListener;
            started.countDown();
        }

        @Override
        public void request(int count) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
            cancelled.set(true);
            cancelCount.incrementAndGet();
            cancelCause.compareAndSet(null, cause);
            cancelledSignal.countDown();
        }

        @Override
        public boolean isReady() {
            return ready.get();
        }

        @Override
        public void halfClose() {
            halfCloseCount.incrementAndGet();
            awaitRelease(halfCloseEntered, releaseHalfClose);
            halfClosed.countDown();
        }

        @Override
        public void sendMessage(ReqT message) {
            awaitRelease(sendEntered, releaseSend);
            requests.add(message);
            firstMessage.countDown();
            if (pauseAfterNextMessage.compareAndSet(true, false)) {
                ready.set(false);
            }
            Status closeStatus = closeAfterNextMessage;
            if (closeStatus != null) {
                closeAfterNextMessage = null;
                close(closeStatus, new Metadata());
            }
        }

        @Override
        public void setMessageCompression(boolean enable) {
        }

        private void message(ResT value) {
            listener.onMessage(value);
        }

        private void close(Status status, Metadata trailers) {
            listener.onClose(status, trailers);
        }

        private void ready(boolean ready) {
            this.ready.set(ready);
            listener.onReady();
        }

        private void pauseAfterNextMessage() {
            pauseAfterNextMessage.set(true);
        }

        private void closeAfterNextMessage(Status status) {
            closeAfterNextMessage = status;
        }

        private void blockNextMessage(CountDownLatch entered, CountDownLatch release) {
            sendEntered = entered;
            releaseSend = release;
        }

        private void blockHalfClose(CountDownLatch entered, CountDownLatch release) {
            halfCloseEntered = entered;
            releaseHalfClose = release;
        }

        private void awaitRelease(CountDownLatch entered, CountDownLatch release) {
            if (entered == null) {
                return;
            }
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        }
    }
}
