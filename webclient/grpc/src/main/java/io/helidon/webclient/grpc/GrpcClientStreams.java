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
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import io.grpc.ClientCall;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ClientResponseObserver;

final class GrpcClientStreams {
    private static final ThreadFactory REQUEST_STREAM_THREAD_FACTORY =
            Thread.ofVirtual().name("helidon-grpc-client-request-stream-", 0).factory();
    private static final Executor REQUEST_STREAM_EXECUTOR =
            task -> REQUEST_STREAM_THREAD_FACTORY.newThread(task).start();
    private static final ThreadFactory REQUEST_STREAM_CLOSE_THREAD_FACTORY =
            Thread.ofVirtual().name("helidon-grpc-client-request-stream-close-", 0).factory();

    private GrpcClientStreams() {
    }

    static <ReqT, ResT> Stream<ResT> serverStreaming(ClientCall<ReqT, ResT> call,
                                                      ReqT request) {
        Objects.requireNonNull(call);
        Objects.requireNonNull(request);
        var responses = new StreamingResponse<ReqT, ResT>();
        ClientCalls.asyncServerStreamingCall(call, request, responses);
        return responses.stream();
    }

    static <ReqT, ResT> ResT clientStreaming(ClientCall<ReqT, ResT> call,
                                              Stream<ReqT> requests) {
        Objects.requireNonNull(call);
        Objects.requireNonNull(requests);
        var response = new ScalarResponse<ReqT, ResT>();
        try {
            ClientCalls.asyncClientStreamingCall(call, response);
        } catch (RuntimeException | Error t) {
            startFailed(call, requests, t);
            throw t;
        }
        response.sendRequests(requests);
        return response.await();
    }

    static <ReqT, ResT> Stream<ResT> bidirectional(ClientCall<ReqT, ResT> call,
                                                    Stream<ReqT> requests) {
        return bidirectional(call, requests, REQUEST_STREAM_EXECUTOR);
    }

    static <ReqT, ResT> Stream<ResT> bidirectional(ClientCall<ReqT, ResT> call,
                                                    Stream<ReqT> requests,
                                                    Executor executor) {
        Objects.requireNonNull(call);
        Objects.requireNonNull(requests);
        Objects.requireNonNull(executor);
        var responses = new StreamingResponse<ReqT, ResT>();
        try {
            ClientCalls.asyncBidiStreamingCall(call, responses);
        } catch (RuntimeException | Error t) {
            startFailed(call, requests, t);
            throw t;
        }
        responses.startSender(requests, executor);
        return responses.stream();
    }

    private static void startFailed(ClientCall<?, ?> call, Stream<?> requests, Throwable failure) {
        try {
            call.cancel("Request stream failed to start.", failure);
        } catch (Throwable t) {
            failure.addSuppressed(t);
        }
        closeRequests(requests, failure);
    }

    static void closeRequests(Stream<?> requests, Throwable failure) {
        try {
            requests.close();
        } catch (Throwable t) {
            failure.addSuppressed(t);
        }
    }

    private enum State {
        ACTIVE,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    private interface Sender {
        void start();

        void ready();

        void stop();
    }

    private static final class CallControl<T> {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicReference<Throwable> sourceFailure = new AtomicReference<>();
        private ClientCallStreamObserver<T> call;
        private Sender sender;
        private boolean senderCancelled;

        private void initialize(ClientCallStreamObserver<T> call, boolean streamingResponse) {
            this.call = call;
            if (streamingResponse) {
                call.disableAutoRequestWithInitial(0);
            }
            call.setOnReadyHandler(this::signalReady);
        }

        private ClientCallStreamObserver<T> call() {
            return Objects.requireNonNull(call, "gRPC call");
        }

        private void sender(Sender sender) {
            boolean stop;
            lock.lock();
            try {
                this.sender = sender;
                stop = senderCancelled;
            } finally {
                lock.unlock();
            }
            if (stop) {
                sender.stop();
            } else {
                sender.start();
            }
        }

        private void stopSender() {
            Sender sender;
            lock.lock();
            try {
                senderCancelled = true;
                sender = this.sender;
            } finally {
                lock.unlock();
            }
            if (sender != null) {
                sender.stop();
            }
        }

        private void cancel(String message, Throwable cause) {
            stopSender();
            call().cancel(message, cause);
        }

        private void sourceFailure(Throwable throwable) {
            sourceFailure.compareAndSet(null, throwable);
        }

        private Throwable failure(Throwable fallback) {
            Throwable failure = sourceFailure.get();
            return failure == null ? fallback : failure;
        }

        private boolean senderCancelled() {
            lock.lock();
            try {
                return senderCancelled;
            } finally {
                lock.unlock();
            }
        }

        private void signalReady() {
            Sender sender;
            lock.lock();
            try {
                sender = this.sender;
            } finally {
                lock.unlock();
            }
            if (sender != null) {
                sender.ready();
            }
        }
    }

    private static final class BlockingRequestSender<T> implements Sender {
        private final CallControl<T> control;
        private final Stream<T> requests;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition ready = lock.newCondition();
        private Thread sendingThread;
        private volatile boolean stopped;
        private boolean sourceClosed;

        private BlockingRequestSender(CallControl<T> control, Stream<T> requests) {
            this.control = control;
            this.requests = requests;
        }

        @Override
        public void start() {
            try {
                lock.lock();
                try {
                    sendingThread = Thread.currentThread();
                    if (stopped) {
                        return;
                    }
                } finally {
                    lock.unlock();
                }
                Iterator<T> iterator = requests.iterator();
                if (!iterator.hasNext()) {
                    completeIfActive();
                    return;
                }
                T staged = Objects.requireNonNull(iterator.next(), "gRPC request");
                while (true) {
                    lock.lock();
                    try {
                        while (!stopped && !control.call().isReady()) {
                            ready.await();
                        }
                        if (stopped) {
                            return;
                        }
                        control.call().onNext(staged);
                        if (stopped) {
                            return;
                        }
                    } finally {
                        lock.unlock();
                    }
                    boolean hasNext = iterator.hasNext();
                    if (stopped) {
                        return;
                    }
                    if (!hasNext) {
                        completeIfActive();
                        return;
                    }
                    staged = Objects.requireNonNull(iterator.next(), "gRPC request");
                }
            } catch (Throwable t) {
                if (t instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (!control.senderCancelled()) {
                    control.sourceFailure(t);
                    control.call().cancel(t instanceof InterruptedException
                                                  ? "Request stream interrupted."
                                                  : "Request stream failed.",
                                          t);
                }
            } finally {
                closeSource();
                lock.lock();
                try {
                    sendingThread = null;
                } finally {
                    lock.unlock();
                }
            }
        }

        @Override
        public void ready() {
            lock.lock();
            try {
                ready.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void stop() {
            Thread sendingThread;
            boolean closeSource;
            lock.lock();
            try {
                if (stopped) {
                    return;
                }
                stopped = true;
                sendingThread = this.sendingThread;
                closeSource = !sourceClosed;
                ready.signalAll();
            } finally {
                lock.unlock();
            }
            if (closeSource && sendingThread != Thread.currentThread()) {
                REQUEST_STREAM_CLOSE_THREAD_FACTORY.newThread(this::closeSource).start();
            }
        }

        private void completeIfActive() {
            lock.lock();
            try {
                if (!stopped) {
                    control.call().onCompleted();
                }
            } finally {
                lock.unlock();
            }
        }

        private void closeSource() {
            lock.lock();
            try {
                if (sourceClosed) {
                    return;
                }
                sourceClosed = true;
            } finally {
                lock.unlock();
            }
            try {
                requests.close();
            } catch (Throwable t) {
                if (!control.senderCancelled()) {
                    control.sourceFailure(t);
                    control.call().cancel("Request stream failed to close.", t);
                }
            }
        }
    }

    private static final class AsyncRequestSender<T> implements Sender {
        private final CallControl<T> control;
        private final Stream<T> requests;
        private final Executor executor;
        private final ContextRunner contextRunner = ContextRunner.create();
        private final ReentrantLock lock = new ReentrantLock();
        private Iterator<T> iterator;
        private T staged;
        private Future<?> future;
        private Thread runnerThread;
        private boolean hasStaged;
        private boolean scheduled;
        private boolean running;
        private volatile boolean stopRequested;
        private boolean stopped;
        private boolean completed;
        private boolean failed;
        private boolean isolatedCloseScheduled;
        private boolean sourceClosed;

        private AsyncRequestSender(CallControl<T> control, Stream<T> requests, Executor executor) {
            this.control = control;
            this.requests = requests;
            this.executor = executor;
        }

        @Override
        public void start() {
            schedule(true);
        }

        @Override
        public void ready() {
            schedule(false);
        }

        private void schedule(boolean initial) {
            FutureTask<Void> task;
            lock.lock();
            try {
                if (stopRequested || stopped || completed || failed || scheduled || running) {
                    return;
                }
                task = new FutureTask<>(contextRunner.wrap(this::send), null);
                future = task;
                scheduled = true;
            } finally {
                lock.unlock();
            }
            try {
                executor.execute(task);
            } catch (RuntimeException | Error t) {
                boolean reportFailure = false;
                boolean closeSource = false;
                lock.lock();
                try {
                    if (future == task) {
                        future = null;
                        scheduled = false;
                        if (!stopped) {
                            stopped = true;
                            reportFailure = true;
                            closeSource = !sourceClosed && !initial && !isolatedCloseScheduled;
                            isolatedCloseScheduled = isolatedCloseScheduled || closeSource;
                        }
                    }
                } finally {
                    lock.unlock();
                }
                if (reportFailure) {
                    control.sourceFailure(t);
                    try {
                        control.cancel("Request stream failed to start.", t);
                    } catch (Throwable cancelFailure) {
                        if (cancelFailure != t) {
                            t.addSuppressed(cancelFailure);
                        }
                    }
                }
                if (initial) {
                    closeSource(t);
                } else if (closeSource) {
                    REQUEST_STREAM_CLOSE_THREAD_FACTORY.newThread(contextRunner.wrap(() -> closeSource(t))).start();
                }
                if (initial) {
                    throw t;
                }
            }
        }

        @Override
        public void stop() {
            stopRequested = true;
            Future<?> future;
            Thread runnerThread;
            boolean closeSource;
            lock.lock();
            try {
                if (stopped) {
                    return;
                }
                stopped = true;
                future = this.future;
                runnerThread = this.runnerThread;
                closeSource = !sourceClosed && !isolatedCloseScheduled;
                isolatedCloseScheduled = isolatedCloseScheduled || closeSource;
            } finally {
                lock.unlock();
            }
            if (future != null && runnerThread != Thread.currentThread()) {
                future.cancel(true);
            }
            if (closeSource) {
                REQUEST_STREAM_CLOSE_THREAD_FACTORY.newThread(contextRunner.wrap(() -> closeSource(null))).start();
            }
        }

        private void send() {
            lock.lock();
            try {
                scheduled = false;
                if (stopRequested || stopped) {
                    future = null;
                    return;
                }
                running = true;
                runnerThread = Thread.currentThread();
            } finally {
                lock.unlock();
            }
            try {
                ClientCallStreamObserver<T> call = control.call();
                while (call.isReady()) {
                    if (!hasStaged) {
                        if (iterator == null) {
                            iterator = requests.iterator();
                        }
                        if (!iterator.hasNext()) {
                            completeIfActive(call);
                            return;
                        }
                        staged = Objects.requireNonNull(iterator.next(), "gRPC request");
                        hasStaged = true;
                    }
                    lock.lock();
                    try {
                        if (stopRequested || stopped) {
                            return;
                        }
                        call.onNext(staged);
                        staged = null;
                        hasStaged = false;
                        if (stopRequested || stopped) {
                            return;
                        }
                    } finally {
                        lock.unlock();
                    }
                    if (!iterator.hasNext()) {
                        completeIfActive(call);
                        return;
                    }
                    staged = Objects.requireNonNull(iterator.next(), "gRPC request");
                    hasStaged = true;
                }
            } catch (Throwable t) {
                lock.lock();
                try {
                    failed = true;
                } finally {
                    lock.unlock();
                }
                if (t instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (!control.senderCancelled()) {
                    control.sourceFailure(t);
                    control.call().cancel(t instanceof InterruptedException
                                                  ? "Request stream interrupted."
                                                  : "Request stream failed.",
                                          t);
                }
            } finally {
                boolean close;
                boolean resume;
                lock.lock();
                try {
                    running = false;
                    runnerThread = null;
                    future = null;
                    close = (stopRequested || stopped || completed || failed) && !isolatedCloseScheduled;
                    resume = !close && control.call().isReady();
                } finally {
                    lock.unlock();
                }
                if (close) {
                    closeSource(null);
                } else if (resume) {
                    schedule(false);
                }
            }
        }

        private void completeIfActive(ClientCallStreamObserver<T> call) {
            lock.lock();
            try {
                if (!stopRequested && !stopped) {
                    completed = true;
                    call.onCompleted();
                }
            } finally {
                lock.unlock();
            }
        }

        private void closeSource(Throwable primaryFailure) {
            lock.lock();
            try {
                if (sourceClosed) {
                    return;
                }
                sourceClosed = true;
            } finally {
                lock.unlock();
            }
            try {
                requests.close();
            } catch (Throwable t) {
                if (primaryFailure != null) {
                    if (t != primaryFailure) {
                        primaryFailure.addSuppressed(t);
                    }
                } else if (!control.senderCancelled()) {
                    control.sourceFailure(t);
                    control.call().cancel("Request stream failed to close.", t);
                }
            }
        }
    }

    private static final class ScalarResponse<ReqT, ResT> implements ClientResponseObserver<ReqT, ResT> {
        private final CallControl<ReqT> control = new CallControl<>();
        private final CompletableFuture<ResT> response = new CompletableFuture<>();
        private ResT value;
        private int responseCount;

        @Override
        public void beforeStart(ClientCallStreamObserver<ReqT> requestStream) {
            control.initialize(requestStream, false);
        }

        @Override
        public void onNext(ResT value) {
            if (responseCount++ == 0) {
                this.value = Objects.requireNonNull(value, "gRPC response");
                return;
            }
            var failure = Status.INTERNAL
                    .withDescription("Client-streaming RPC returned more than one response.")
                    .asRuntimeException();
            response.completeExceptionally(failure);
            control.cancel("Client-streaming RPC returned more than one response.", failure);
        }

        @Override
        public void onError(Throwable throwable) {
            Throwable failure = control.failure(throwable);
            control.stopSender();
            response.completeExceptionally(failure);
        }

        @Override
        public void onCompleted() {
            control.stopSender();
            if (responseCount == 1) {
                response.complete(value);
            } else if (responseCount == 0) {
                response.completeExceptionally(Status.INTERNAL
                                                         .withDescription("Client-streaming RPC returned no response.")
                                                         .asRuntimeException());
            }
        }

        private void sendRequests(Stream<ReqT> requests) {
            control.sender(new BlockingRequestSender<>(control, requests));
        }

        private ResT await() {
            try {
                return response.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                control.cancel("Interrupted while awaiting gRPC response.", e);
                throw new CompletionException(e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new CompletionException(cause);
            }
        }
    }

    private static final class StreamingResponse<ReqT, ResT>
            implements ClientResponseObserver<ReqT, ResT>, Spliterator<ResT> {
        private final CallControl<ReqT> control = new CallControl<>();
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition changed = lock.newCondition();
        private State state = State.ACTIVE;
        private ResT item;
        private Throwable error;
        private boolean hasItem;
        private boolean requested;

        @Override
        public void beforeStart(ClientCallStreamObserver<ReqT> requestStream) {
            control.initialize(requestStream, true);
        }

        @Override
        public void onNext(ResT value) {
            lock.lock();
            try {
                if (state != State.ACTIVE) {
                    return;
                }
                requested = false;
                if (hasItem) {
                    error = new IllegalStateException("Received gRPC message before previous message was consumed.");
                    state = State.FAILED;
                } else {
                    item = Objects.requireNonNull(value, "gRPC response");
                    hasItem = true;
                }
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            lock.lock();
            try {
                if (state == State.ACTIVE) {
                    error = control.failure(throwable);
                    state = State.FAILED;
                    changed.signalAll();
                }
            } finally {
                lock.unlock();
            }
            control.stopSender();
        }

        @Override
        public void onCompleted() {
            lock.lock();
            try {
                if (state == State.ACTIVE) {
                    state = State.SUCCEEDED;
                    changed.signalAll();
                }
            } finally {
                lock.unlock();
            }
            control.stopSender();
        }

        @Override
        public boolean tryAdvance(Consumer<? super ResT> action) {
            Objects.requireNonNull(action);
            ResT next;
            lock.lock();
            try {
                if (!hasItem && state == State.ACTIVE && !requested) {
                    requested = true;
                    control.call().request(1);
                }
                while (!hasItem && state == State.ACTIVE) {
                    changed.await();
                }
                if (!hasItem && error != null) {
                    if (error instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (error instanceof Error itemError) {
                        throw itemError;
                    }
                    throw new CompletionException(error);
                }
                if (!hasItem) {
                    return false;
                }
                next = item;
                item = null;
                hasItem = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel("Interrupted while awaiting gRPC response.", e);
                throw new CompletionException(e);
            } finally {
                lock.unlock();
            }
            action.accept(next);
            return true;
        }

        @Override
        public Spliterator<ResT> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL;
        }

        private void startSender(Stream<ReqT> requests, Executor executor) {
            control.sender(new AsyncRequestSender<>(control, requests, executor));
        }

        private Stream<ResT> stream() {
            return StreamSupport.stream(this, false).onClose(() -> cancel("Response stream closed.", null));
        }

        private void cancel(String message, Throwable cause) {
            boolean cancel;
            lock.lock();
            try {
                cancel = state == State.ACTIVE;
                if (cancel) {
                    state = State.CANCELLED;
                    changed.signalAll();
                }
            } finally {
                lock.unlock();
            }
            if (cancel) {
                control.cancel(message, cause);
            }
        }
    }

    private record ContextRunner(io.grpc.Context grpcContext, Optional<Context> helidonContext) {
        private static ContextRunner create() {
            return new ContextRunner(io.grpc.Context.current(), Contexts.context());
        }

        private Runnable wrap(Runnable task) {
            return () -> grpcContext.run(() -> {
                if (helidonContext.isPresent()) {
                    Contexts.runInContext(helidonContext.get(), task);
                } else {
                    task.run();
                }
            });
        }
    }
}
