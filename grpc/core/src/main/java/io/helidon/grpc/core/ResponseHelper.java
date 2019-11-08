/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.core;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.grpc.stub.StreamObserver;

/**
 * A number of helper methods to handle sending responses to a {@link StreamObserver}.
 */
public final class ResponseHelper {
    private ResponseHelper() {
    }

    /**
     * Complete a gRPC request.
     * <p>
     * The request will be completed by calling {@link StreamObserver#onNext(Object)} using the
     * specified value then calling {@link StreamObserver#onCompleted()}.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param value     the value to use when calling {@link StreamObserver#onNext(Object)}
     * @param <T>       they type of the request result
     */
    public static <T> void complete(StreamObserver<T> observer, T value) {
        StreamObserver<T> safe = SafeStreamObserver.ensureSafeObserver(observer);
        safe.onNext(value);
        safe.onCompleted();
    }

    /**
     * Complete a gRPC request based on the result of a {@link CompletionStage}.
     * <p>
     * The request will be completed by calling {@link StreamObserver#onNext(Object)} using the
     * result obtained on completion of the specified {@link CompletionStage} and then calling
     * {@link StreamObserver#onCompleted()}.
     * <p>
     * If the {@link CompletionStage} completes with an error then {@link StreamObserver#onError(Throwable)}
     * will be called.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param future    the {@link CompletionStage} to use to obtain the value to use to call
     *                  {@link StreamObserver#onNext(Object)}
     * @param <T>       they type of the request result
     */
    public static <T> void complete(StreamObserver<T> observer, CompletionStage<T> future) {
        future.whenComplete(completeWithResult(observer));
    }

    /**
     * Asynchronously complete a gRPC request based on the result of a {@link CompletionStage}.
     * <p>
     * The request will be completed by calling {@link StreamObserver#onNext(Object)} using the
     * result obtained on completion of the specified {@link CompletionStage} and then calling
     * {@link StreamObserver#onCompleted()}.
     * <p>
     * If the {@link CompletionStage} completes with an error then {@link StreamObserver#onError(Throwable)}
     * will be called.
     * <p>
     * The execution will take place asynchronously on the fork-join thread pool.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param future    the {@link CompletionStage} to use to obtain the value to use to call
     *                  {@link StreamObserver#onNext(Object)}
     * @param <T>       they type of the request result
     */
    public static <T> void completeAsync(StreamObserver<T> observer, CompletionStage<T> future) {
        future.whenCompleteAsync(completeWithResult(observer));
    }

    /**
     * Asynchronously complete a gRPC request based on the result of a {@link CompletionStage}.
     * <p>
     * The request will be completed by calling {@link StreamObserver#onNext(Object)} using the
     * result obtained on completion of the specified {@link CompletionStage} and then calling
     * {@link StreamObserver#onCompleted()}.
     * <p>
     * If the {@link CompletionStage} completes with an error then {@link StreamObserver#onError(Throwable)}
     * will be called.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param future    the {@link CompletionStage} to use to obtain the value to use to call
     *                  {@link StreamObserver#onNext(Object)}
     * @param executor  the {@link Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     */
    public static <T> void completeAsync(StreamObserver<T> observer, CompletionStage<T> future, Executor executor) {
        future.whenCompleteAsync(completeWithResult(observer), executor);
    }

    /**
     * Complete a gRPC request based on the result of a {@link Callable}.
     * <p>
     * The request will be completed by calling {@link StreamObserver#onNext(Object)} using the
     * result obtained on completion of the specified {@link Callable} and then calling
     * {@link StreamObserver#onCompleted()}.
     * <p>
     * If the {@link Callable#call()} method throws an exception then {@link StreamObserver#onError(Throwable)}
     * will be called.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param callable  the {@link Callable} to use to obtain the value to use to call
     *                  {@link StreamObserver#onNext(Object)}
     * @param <T>       they type of the request result
     */
    public static <T> void complete(StreamObserver<T> observer, Callable<T> callable) {
        try {
            observer.onNext(callable.call());
            observer.onCompleted();
        } catch (Throwable t) {
            observer.onError(t);
        }
    }

    /**
     * Asynchronously complete a gRPC request based on the result of a {@link Callable}.
     * <p>
     * The request will be completed by calling {@link StreamObserver#onNext(Object)} using the
     * result obtained on completion of the specified {@link Callable} and then calling
     * {@link StreamObserver#onCompleted()}.
     * <p>
     * If the {@link Callable#call()} method throws an exception then {@link StreamObserver#onError(Throwable)}
     * will be called.
     * <p>
     * The execution will take place asynchronously on the fork-join thread pool.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param callable  the {@link Callable} to use to obtain the value to use to call
     *                  {@link StreamObserver#onNext(Object)}
     * @param <T>       they type of the request result
     */
    public static <T> void completeAsync(StreamObserver<T> observer, Callable<T> callable) {
        completeAsync(observer, CompletableFuture.supplyAsync(createSupplier(callable)));
    }

    /**
     * Asynchronously complete a gRPC request based on the result of a {@link Callable}.
     * <p>
     * The request will be completed by calling {@link StreamObserver#onNext(Object)} using the
     * result obtained on completion of the specified {@link Callable} and then calling
     * {@link StreamObserver#onCompleted()}.
     * <p>
     * If the {@link Callable#call()} method throws an exception then {@link StreamObserver#onError(Throwable)}
     * will be called.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param callable  the {@link Callable} to use to obtain the value to use to call
     *                  {@link StreamObserver#onNext(Object)}
     * @param executor  the {@link Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     */
    public static <T> void completeAsync(StreamObserver<T> observer, Callable<T> callable, Executor executor) {
        completeAsync(observer, CompletableFuture.supplyAsync(createSupplier(callable), executor));
    }

    /**
     * Execute a {@link Runnable} task and on completion of the task complete the gRPC request by
     * calling {@link StreamObserver#onNext(Object)} using the specified result and then call
     * {@link StreamObserver#onCompleted()}.
     * <p>
     * If the {@link Runnable#run()} method throws an exception then {@link StreamObserver#onError(Throwable)}
     * will be called.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param task      the {@link Runnable} to execute
     * @param result    the result to pass to {@link StreamObserver#onNext(Object)}
     * @param <T>       they type of the request result
     */
    public static <T> void complete(StreamObserver<T> observer, Runnable task, T result) {
        complete(observer, Executors.callable(task, result));
    }

    /**
     * Asynchronously execute a {@link Runnable} task and on completion of the task complete the gRPC
     * request by calling {@link StreamObserver#onNext(Object)} using the specified result and then
     * call {@link StreamObserver#onCompleted()}.
     * <p>
     * If the {@link Runnable#run()} method throws an exception then {@link StreamObserver#onError(Throwable)}
     * will be called.
     * <p>
     * The task and and request completion will be executed on the fork-join thread pool.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param task      the {@link Runnable} to execute
     * @param result    the result to pass to {@link StreamObserver#onNext(Object)}
     * @param <T>       they type of the request result
     */
    public static <T> void completeAsync(StreamObserver<T> observer, Runnable task, T result) {
        completeAsync(observer, Executors.callable(task, result));
    }

    /**
     * Asynchronously execute a {@link Runnable} task and on completion of the task complete the gRPC
     * request by calling {@link StreamObserver#onNext(Object)} using the specified result and then
     * call {@link StreamObserver#onCompleted()}.
     * <p>
     * If the {@link Runnable#run()} method throws an exception then {@link StreamObserver#onError(Throwable)}
     * will be called.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param task      the {@link Runnable} to execute
     * @param result    the result to pass to {@link StreamObserver#onNext(Object)}
     * @param executor  the {@link Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     */
    public static <T> void completeAsync(StreamObserver<T> observer, Runnable task, T result, Executor executor) {
        completeAsync(observer, Executors.callable(task, result), executor);
    }

    /**
     * Send the values from a {@link Stream} to the {@link StreamObserver#onNext(Object)} method until the
     * {@link Stream} is exhausted call {@link StreamObserver#onCompleted()}.
     * <p>
     * If an error occurs whilst streaming results then {@link StreamObserver#onError(Throwable)} will be called.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param stream    the {@link Stream} of results to send to {@link StreamObserver#onNext(Object)}
     * @param <T>       they type of the request result
     */
    public static <T> void stream(StreamObserver<T> observer, Stream<? extends T> stream) {
        stream(observer, () -> stream);
    }

    /**
     * Asynchronously send the values from a {@link Stream} to the {@link StreamObserver#onNext(Object)} method until
     * the {@link Stream} is exhausted call {@link StreamObserver#onCompleted()}.
     * <p>
     * If an error occurs whilst streaming results then {@link StreamObserver#onError(Throwable)} will be called.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param stream    the {@link Stream} of results to send to {@link StreamObserver#onNext(Object)}
     * @param executor  the {@link Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     */
    public static <T> void streamAsync(StreamObserver<T> observer, Stream<? extends T> stream, Executor executor) {
        executor.execute(() -> stream(observer, () -> stream));
    }

    /**
     * Send the values from a {@link Stream} to the {@link StreamObserver#onNext(Object)} method until the
     * {@link Stream} is exhausted call {@link StreamObserver#onCompleted()}.
     * <p>
     * If an error occurs whilst streaming results then {@link StreamObserver#onError(Throwable)} will be called.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param supplier  the {@link Supplier} of the {@link Stream} of results to send to {@link StreamObserver#onNext(Object)}
     * @param <T>       they type of the request result
     */
    public static <T> void stream(StreamObserver<T> observer, Supplier<Stream<? extends T>> supplier) {
        StreamObserver<T> safe = SafeStreamObserver.ensureSafeObserver(observer);
        Throwable thrown = null;

        try {
            supplier.get().forEach(safe::onNext);
        } catch (Throwable t) {
            thrown = t;
        }

        if (thrown == null) {
            safe.onCompleted();
        } else {
            safe.onError(thrown);
        }
    }

    /**
     * Asynchronously send the values from a {@link Stream} to the {@link StreamObserver#onNext(Object)} method
     * until the {@link Stream} is exhausted call {@link StreamObserver#onCompleted()}.
     * <p>
     * If an error occurs whilst streaming results then {@link StreamObserver#onError(Throwable)} will be called.
     *
     * @param observer  the {@link StreamObserver} to complete
     * @param supplier  the {@link Supplier} of the {@link Stream} of results to send to {@link StreamObserver#onNext(Object)}
     * @param executor  the {@link Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     */
    public static <T> void streamAsync(StreamObserver<T> observer, Supplier<Stream<? extends T>> supplier, Executor executor) {
        executor.execute(() -> stream(observer, supplier));
    }


    /**
     * Obtain a {@link Consumer} that can be used to send values to the {@link StreamObserver#onNext(Object)} method until
     * the {@link CompletionStage} completes then call {@link StreamObserver#onCompleted()}.
     * <p>
     * If the {@link CompletionStage} completes with an error then {@link StreamObserver#onError(Throwable)}
     * will be called instead of {@link StreamObserver#onCompleted()}.
     *
     * @param observer  the {@link StreamObserver} to send values to and complete when the {@link CompletionStage} completes
     * @param stage     the {@link CompletionStage} to await completion of
     * @param <T>       they type of the request result
     *
     * @return a {@link Consumer} that can be used to send values to the {@link StreamObserver#onNext(Object)} method
     */
    // todo: a bit of a chicken or egg when used with Coherence streaming methods, isn't it?
    public static <T> Consumer<T> stream(StreamObserver<T> observer, CompletionStage<Void> stage) {
        StreamObserver<T> safe = SafeStreamObserver.ensureSafeObserver(observer);
        stage.whenComplete(completeWithoutResult(safe));
        return safe::onNext;
    }

    /**
     * Obtain a {@link Consumer} that can be used to send values to the {@link StreamObserver#onNext(Object)} method until
     * the {@link CompletionStage} completes then asynchronously call {@link StreamObserver#onCompleted()} using the
     * fork-join thread pool.
     * <p>
     * If the {@link CompletionStage} completes with an error then {@link StreamObserver#onError(Throwable)}
     * will be called instead of {@link StreamObserver#onCompleted()}.
     *
     * @param observer  the {@link StreamObserver} to send values to and complete when the {@link CompletionStage} completes
     * @param stage     the {@link CompletionStage} to await completion of
     * @param <T>       they type of the request result
     *
     * @return a {@link Consumer} that can be used to send values to the {@link StreamObserver#onNext(Object)} method
     */
    public static <T> Consumer<T> streamAsync(StreamObserver<T> observer, CompletionStage<Void> stage) {
        StreamObserver<T> safe = SafeStreamObserver.ensureSafeObserver(observer);
        stage.whenCompleteAsync(completeWithoutResult(safe));
        return value -> CompletableFuture.runAsync(() -> safe.onNext(value));
    }

    /**
     * Obtain a {@link Consumer} that can be used to send values to the {@link StreamObserver#onNext(Object)} method until
     * the {@link CompletionStage} completes then asynchronously call {@link StreamObserver#onCompleted()} using the executor
     * thread.
     * <p>
     * If the {@link CompletionStage} completes with an error then {@link StreamObserver#onError(Throwable)}
     * will be called instead of {@link StreamObserver#onCompleted()}.
     *
     * @param observer  the {@link StreamObserver} to send values to and complete when the {@link CompletionStage} completes
     * @param stage     the {@link CompletionStage} to await completion of
     * @param executor  the {@link Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     *
     * @return a {@link Consumer} that can be used to send values to the {@link StreamObserver#onNext(Object)} method
     */
    public static <T> Consumer<T> streamAsync(StreamObserver<T> observer, CompletionStage<Void> stage, Executor executor) {
        StreamObserver<T> safe = SafeStreamObserver.ensureSafeObserver(observer);
        stage.whenCompleteAsync(completeWithoutResult(safe), executor);
        return value -> CompletableFuture.runAsync(() -> safe.onNext(value), executor);
    }

    /**
     * Obtain a {@link Consumer} that can be used to send values to the {@link StreamObserver#onNext(Object)} method.
     * @param observer  the {@link StreamObserver} to complete
     * @param <T>  the type of the result
     * @param <U>  the type of the response
     * @return a {@link Consumer} that can be used to send values to the {@link StreamObserver#onNext(Object)} method
     */
    public static <T, U> BiConsumer<T, Throwable> completeWithResult(StreamObserver<U> observer) {
        return new CompletionAction<>(observer, true);
    }

    /**
     * Obtain a {@link Consumer} that can be used to complete a {@link StreamObserver}.
     * @param observer  the {@link StreamObserver} to complete
     * @param <U>  the type of the response
     * @return a {@link Consumer} that can be used to complete a {@link StreamObserver}
     */
    public static <U> BiConsumer<Void, Throwable> completeWithoutResult(StreamObserver<U> observer) {
        return new CompletionAction<>(observer, false);
    }

    /**
     * Convert a {@link Callable} to a {@link Supplier}.
     * @param callable  the {@link Callable} to convert
     * @param <T>  the result returned by the {@link Callable}
     * @return  a {@link Supplier} that wraps the {@link Callable}
     */
    public static <T> Supplier<T> createSupplier(Callable<T> callable) {
        return new CallableSupplier<>(callable);
    }

    /**
     * A {@link BiConsumer} that is used to handle completion of a
     * {@link java.util.concurrent.CompletionStage} by forwarding
     * the result to a {@link io.grpc.stub.StreamObserver}.
     *
     * @param <T>  the type of the {@link java.util.concurrent.CompletionStage}'s result
     * @param <U>  the type of result expected by the {@link io.grpc.stub.StreamObserver}
     */
    private static class CompletionAction<T, U> implements BiConsumer<T, Throwable> {
        private StreamObserver<U> observer;
        private boolean sendResult;

        CompletionAction(StreamObserver<U> observer, boolean sendResult) {
            this.observer = observer;
            this.sendResult = sendResult;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void accept(T result, Throwable error) {
            if (error != null) {
                observer.onError(error);
            } else {
                if (sendResult) {
                    observer.onNext((U) result);
                }
                observer.onCompleted();
            }
        }
    }

    /**
     * A class that converts a {@link Callable} to a {@link Supplier}.
     * @param <T>  the type of result returned from the callable
     */
    private static class CallableSupplier<T> implements Supplier<T> {
        private Callable<T> callable;

        CallableSupplier(Callable<T> callable) {
            this.callable = callable;
        }

        @Override
        public T get() {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new CompletionException(e.getMessage(), e);
            }
        }
    }
}
