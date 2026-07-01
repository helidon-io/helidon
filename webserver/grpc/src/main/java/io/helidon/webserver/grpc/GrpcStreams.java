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

import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.common.Api;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.grpc.core.ContextKeys;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/**
 * Utilities used by generated declarative gRPC routes.
 */
@Api.Internal
public final class GrpcStreams {
    private static final ThreadFactory THREAD_FACTORY = Thread.ofVirtual()
            .name("helidon-grpc-server-stream-", 0)
            .factory();

    private GrpcStreams() {
    }

    /**
     * Send stream server-streaming responses.
     *
     * @param handler endpoint handler
     * @param responseObserver response observer
     * @param <ResT> response type
     */
    public static <ResT> void serverStreaming(Supplier<Stream<ResT>> handler, StreamObserver<ResT> responseObserver) {
        Objects.requireNonNull(handler);
        Objects.requireNonNull(responseObserver);
        Outbound outbound = new Outbound(responseObserver, () -> { });
        ContextRunner contextRunner = ContextRunner.create();
        Future<?> worker = contextRunner.submit(() -> {
            boolean completed;
            try {
                Stream<ResT> responses = Objects.requireNonNull(handler.get(), "gRPC response stream");
                if (!outbound.source(responses)) {
                    return;
                }
                try {
                    completed = sendResponses(responses, responseObserver, outbound);
                } finally {
                    outbound.closeSource();
                }
            } catch (Throwable t) {
                if (outbound.claimTerminal()) {
                    responseObserver.onError(unwrap(t));
                }
                return;
            }
            if (completed && outbound.claimTerminal()) {
                responseObserver.onCompleted();
            }
        });
        outbound.worker(worker);
    }

    /**
     * Create a client-streaming request observer backed by a non-collecting stream.
     *
     * @param handler endpoint handler
     * @param responseObserver response observer
     * @param <ReqT> request type
     * @param <ResT> response type
     * @return request observer
     */
    public static <ReqT, ResT> StreamObserver<ReqT> clientStreaming(Function<Stream<ReqT>, ResT> handler,
                                                                    StreamObserver<ResT> responseObserver) {
        Objects.requireNonNull(handler);
        Objects.requireNonNull(responseObserver);
        StreamingRequest<ReqT> request = new StreamingRequest<>(responseObserver);
        Outbound outbound = new Outbound(responseObserver, request::cancel);
        ContextRunner contextRunner = ContextRunner.create();
        Future<?> worker = contextRunner.submit(() -> {
            ResT response;
            try (Stream<ReqT> stream = request.stream()) {
                response = Objects.requireNonNull(handler.apply(stream), "gRPC response");
            } catch (Throwable t) {
                if (outbound.claimTerminal()) {
                    responseObserver.onError(unwrap(t));
                }
                return;
            }
            if (outbound.cancelled()) {
                return;
            }
            try {
                responseObserver.onNext(response);
            } catch (Throwable t) {
                if (outbound.claimTerminal()) {
                    responseObserver.onError(unwrap(t));
                }
                return;
            }
            if (outbound.claimTerminal()) {
                responseObserver.onCompleted();
            }
        });
        outbound.worker(worker);
        return request.observer();
    }

    /**
     * Create a bidirectional request observer backed by a non-collecting request stream.
     *
     * @param handler endpoint handler
     * @param responseObserver response observer
     * @param <ReqT> request type
     * @param <ResT> response type
     * @return request observer
     */
    public static <ReqT, ResT> StreamObserver<ReqT> bidirectional(Function<Stream<ReqT>, Stream<ResT>> handler,
                                                                  StreamObserver<ResT> responseObserver) {
        Objects.requireNonNull(handler);
        Objects.requireNonNull(responseObserver);
        StreamingRequest<ReqT> request = new StreamingRequest<>(responseObserver);
        Outbound outbound = new Outbound(responseObserver, request::cancel);
        ContextRunner contextRunner = ContextRunner.create();
        Future<?> worker = contextRunner.submit(() -> {
            boolean completed;
            try (Stream<ReqT> stream = request.stream()) {
                Stream<ResT> responses = Objects.requireNonNull(handler.apply(stream), "gRPC response stream");
                if (!outbound.source(responses)) {
                    return;
                }
                try {
                    completed = sendResponses(responses, responseObserver, outbound);
                } finally {
                    outbound.closeSource();
                }
            } catch (Throwable t) {
                if (outbound.claimTerminal()) {
                    responseObserver.onError(unwrap(t));
                }
                return;
            }
            if (completed && outbound.claimTerminal()) {
                responseObserver.onCompleted();
            }
        });
        outbound.worker(worker);
        return request.observer();
    }

    private static <ResT> boolean sendResponses(Stream<ResT> responses,
                                                StreamObserver<ResT> responseObserver,
                                                Outbound outbound) {
        var iterator = responses.iterator();
        while (!outbound.cancelled() && iterator.hasNext()) {
            if (!outbound.awaitReady()) {
                return false;
            }
            responseObserver.onNext(Objects.requireNonNull(iterator.next(), "gRPC response"));
        }
        return !outbound.cancelled();
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof CompletionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    private static final class StreamingRequest<T> {
        private final SingleItemBridge<T> bridge = new SingleItemBridge<>();

        private StreamingRequest(StreamObserver<?> responseObserver) {
            if (responseObserver instanceof ServerCallStreamObserver<?> serverObserver) {
                serverObserver.disableAutoRequest();
                bridge.requester(() -> serverObserver.request(1));
            }
        }

        private Stream<T> stream() {
            return StreamSupport.stream(bridge, false).onClose(bridge::cancel);
        }

        private void cancel() {
            bridge.cancel();
        }

        private StreamObserver<T> observer() {
            return new StreamObserver<>() {
                @Override
                public void onNext(T request) {
                    bridge.item(request);
                }

                @Override
                public void onError(Throwable throwable) {
                    bridge.error(throwable);
                }

                @Override
                public void onCompleted() {
                    bridge.complete();
                }
            };
        }
    }

    private static final class SingleItemBridge<T> implements Spliterator<T> {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition changed = lock.newCondition();
        private Runnable request = () -> { };
        private T item;
        private Throwable error;
        private boolean hasItem;
        private boolean requested;
        private boolean complete;
        private boolean cancelled;

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            T next;
            lock.lock();
            try {
                requestOne();
                while (!hasItem && !complete && error == null && !cancelled) {
                    changed.await();
                }
                if (!hasItem) {
                    if (error != null) {
                        throw new CompletionException(error);
                    }
                    return false;
                }
                next = item;
                item = null;
                hasItem = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } finally {
                lock.unlock();
            }
            action.accept(next);
            return true;
        }

        private void requestOne() {
            if (!requested && !complete && error == null && !cancelled) {
                requested = true;
                request.run();
            }
        }

        private void requester(Runnable request) {
            this.request = request;
        }

        private void item(T item) {
            lock.lock();
            try {
                requested = false;
                if (hasItem) {
                    error = new IllegalStateException("Received gRPC message before previous message was consumed.");
                } else {
                    this.item = item;
                    hasItem = true;
                }
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private void error(Throwable error) {
            lock.lock();
            try {
                requested = false;
                this.error = error;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private void complete() {
            lock.lock();
            try {
                requested = false;
                complete = true;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        private void cancel() {
            lock.lock();
            try {
                requested = false;
                cancelled = true;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Spliterator<T> trySplit() {
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
    }

    private record ContextRunner(io.grpc.Context grpcContext, Optional<Context> helidonContext) {
        private static ContextRunner create() {
            io.grpc.Context grpcContext = io.grpc.Context.current();
            Optional<Context> helidonContext = Optional.ofNullable(ContextKeys.HELIDON_CONTEXT.get(grpcContext))
                    .or(Contexts::context);
            return new ContextRunner(grpcContext, helidonContext);
        }

        private Future<?> submit(Runnable task) {
            FutureTask<Void> future = new FutureTask<>(() -> grpcContext.run(() -> {
                if (helidonContext.isPresent()) {
                    Contexts.runInContext(helidonContext.get(), task);
                } else {
                    task.run();
                }
            }), null);
            THREAD_FACTORY.newThread(future).start();
            return future;
        }
    }

    private static final class Outbound {
        private final ServerCallStreamObserver<?> serverObserver;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition ready = lock.newCondition();
        private volatile boolean cancelled;
        private volatile Future<?> worker;
        private Stream<?> source;
        private boolean terminal;

        private Outbound(StreamObserver<?> responseObserver, Runnable onCancel) {
            if (responseObserver instanceof ServerCallStreamObserver<?> observer) {
                this.serverObserver = observer;
                observer.setOnReadyHandler(this::signalReady);
                observer.setOnCancelHandler(() -> {
                    cancel();
                    onCancel.run();
                });
            } else {
                this.serverObserver = null;
            }
        }

        private boolean awaitReady() {
            if (serverObserver == null) {
                return true;
            }
            lock.lock();
            try {
                while (!serverObserver.isReady() && !serverObserver.isCancelled() && !cancelled) {
                    ready.await();
                }
                return !serverObserver.isCancelled() && !cancelled;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } finally {
                lock.unlock();
            }
        }

        private boolean cancelled() {
            return serverObserver != null && (serverObserver.isCancelled() || cancelled);
        }

        private void worker(Future<?> worker) {
            this.worker = worker;
            if (cancelled()) {
                worker.cancel(true);
            }
        }

        private boolean source(Stream<?> source) {
            boolean close;
            lock.lock();
            try {
                close = cancelled;
                if (!close) {
                    this.source = source;
                }
            } finally {
                lock.unlock();
            }
            if (close) {
                try {
                    source.close();
                } catch (Throwable ignored) {
                    // The peer has cancelled; there is no remaining observer to report close failures to.
                }
            }
            return !close;
        }

        private void closeSource() {
            Stream<?> source;
            lock.lock();
            try {
                source = this.source;
                this.source = null;
            } finally {
                lock.unlock();
            }
            if (source != null) {
                source.close();
            }
        }

        private boolean claimTerminal() {
            lock.lock();
            try {
                if (terminal || cancelled || (serverObserver != null && serverObserver.isCancelled())) {
                    return false;
                }
                terminal = true;
                return true;
            } finally {
                lock.unlock();
            }
        }

        private void cancel() {
            Future<?> worker;
            Stream<?> source;
            lock.lock();
            try {
                if (terminal) {
                    return;
                }
                cancelled = true;
                ready.signalAll();
                worker = this.worker;
                source = this.source;
                this.source = null;
            } finally {
                lock.unlock();
            }
            if (worker != null) {
                worker.cancel(true);
            }
            if (source != null) {
                THREAD_FACTORY.newThread(() -> {
                    try {
                        source.close();
                    } catch (Throwable ignored) {
                        // The peer has cancelled; there is no remaining observer to report close failures to.
                    }
                }).start();
            }
        }

        private void signalReady() {
            lock.lock();
            try {
                ready.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
