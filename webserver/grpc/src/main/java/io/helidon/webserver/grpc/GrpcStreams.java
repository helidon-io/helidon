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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/**
 * Utilities used by generated declarative gRPC routes.
 */
public final class GrpcStreams {
    private GrpcStreams() {
    }

    /**
     * Send iterable server-streaming responses.
     *
     * @param responses responses
     * @param responseObserver response observer
     * @param <ResT> response type
     */
    public static <ResT> void serverStreaming(Iterable<ResT> responses, StreamObserver<ResT> responseObserver) {
        Objects.requireNonNull(responses);
        Objects.requireNonNull(responseObserver);
        try {
            for (ResT response : responses) {
                responseObserver.onNext(response);
            }
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(t);
        }
    }

    /**
     * Send stream server-streaming responses.
     *
     * @param responses responses
     * @param responseObserver response observer
     * @param <ResT> response type
     */
    public static <ResT> void serverStreaming(Stream<ResT> responses, StreamObserver<ResT> responseObserver) {
        Objects.requireNonNull(responses);
        Objects.requireNonNull(responseObserver);
        try (responses) {
            responses.forEach(responseObserver::onNext);
            responseObserver.onCompleted();
        } catch (Throwable t) {
            responseObserver.onError(t);
        }
    }

    /**
     * Create a client-streaming request observer that collects all inbound requests before invoking the handler.
     *
     * @param handler endpoint handler
     * @param responseObserver response observer
     * @param <ReqT> request type
     * @param <ResT> response type
     * @return request observer
     */
    public static <ReqT, ResT> StreamObserver<ReqT> clientStreaming(Function<Iterable<ReqT>, ResT> handler,
                                                                    StreamObserver<ResT> responseObserver) {
        Objects.requireNonNull(handler);
        Objects.requireNonNull(responseObserver);
        List<ReqT> requests = new ArrayList<>();
        return new StreamObserver<>() {
            @Override
            public void onNext(ReqT request) {
                requests.add(request);
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                try {
                    responseObserver.onNext(handler.apply(List.copyOf(requests)));
                    responseObserver.onCompleted();
                } catch (Throwable t) {
                    responseObserver.onError(t);
                }
            }
        };
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
    public static <ReqT, ResT> StreamObserver<ReqT> clientStreamingStream(Function<Stream<ReqT>, ResT> handler,
                                                                          StreamObserver<ResT> responseObserver) {
        Objects.requireNonNull(handler);
        Objects.requireNonNull(responseObserver);
        StreamingRequest<ReqT> request = new StreamingRequest<>(responseObserver);
        Thread.startVirtualThread(() -> {
            try (Stream<ReqT> stream = request.stream()) {
                responseObserver.onNext(handler.apply(stream));
                responseObserver.onCompleted();
            } catch (Throwable t) {
                responseObserver.onError(unwrap(t));
            }
        });
        return request.observer();
    }

    /**
     * Create a bidirectional request observer that collects all inbound requests before invoking the handler.
     *
     * @param handler endpoint handler
     * @param responseObserver response observer
     * @param <ReqT> request type
     * @param <ResT> response type
     * @return request observer
     */
    public static <ReqT, ResT> StreamObserver<ReqT> bidirectional(Function<Iterable<ReqT>, Iterable<ResT>> handler,
                                                                  StreamObserver<ResT> responseObserver) {
        Objects.requireNonNull(handler);
        Objects.requireNonNull(responseObserver);
        List<ReqT> requests = new ArrayList<>();
        return new StreamObserver<>() {
            @Override
            public void onNext(ReqT request) {
                requests.add(request);
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                serverStreaming(handler.apply(List.copyOf(requests)), responseObserver);
            }
        };
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
    public static <ReqT, ResT> StreamObserver<ReqT> bidirectionalStream(Function<Stream<ReqT>, Stream<ResT>> handler,
                                                                        StreamObserver<ResT> responseObserver) {
        Objects.requireNonNull(handler);
        Objects.requireNonNull(responseObserver);
        StreamingRequest<ReqT> request = new StreamingRequest<>(responseObserver);
        Thread.startVirtualThread(() -> {
            try (Stream<ReqT> stream = request.stream()) {
                serverStreaming(handler.apply(stream), responseObserver);
            } catch (Throwable t) {
                responseObserver.onError(unwrap(t));
            }
        });
        return request.observer();
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
                serverObserver.setOnCancelHandler(bridge::cancel);
            }
        }

        private Stream<T> stream() {
            return StreamSupport.stream(bridge, false).onClose(bridge::cancel);
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
                if (error != null) {
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
}
