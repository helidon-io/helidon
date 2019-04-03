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

package io.helidon.grpc.server;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.grpc.core.SafeStreamObserver;

import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.server.BindableServiceImpl.completeWithResult;
import static io.helidon.grpc.server.BindableServiceImpl.completeWithoutResult;
import static io.helidon.grpc.server.BindableServiceImpl.createSupplier;

/**
 * A Helidon gRPC service.
 *
 * @author Aleksandar Seovic
 */
public interface GrpcService {

    /**
     * Update service configuration.
     *
     * @param config configuration to update
     */
    void update(ServiceDescriptor.Config config);

    /**
     * Obtain the name of this service.
     * <p>
     * The default implementation returns the implementation class's {@link Class#getSimpleName()}.
     *
     * @return  the name of this service
     */
    default String name() {
        return getClass().getSimpleName();
    }

    // ---- convenience methods ---------------------------------------------

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
    default <T> void complete(StreamObserver<T> observer, T value) {
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
    default <T> void complete(StreamObserver<T> observer, CompletionStage<T> future) {
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
    default <T> void completeAsync(StreamObserver<T> observer, CompletionStage<T> future) {
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
     * @param executor  the {@link java.util.concurrent.Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     */
    default <T> void completeAsync(StreamObserver<T> observer, CompletionStage<T> future, Executor executor) {
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
    default <T> void complete(StreamObserver<T> observer, Callable<T> callable) {
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
    default <T> void completeAsync(StreamObserver<T> observer, Callable<T> callable) {
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
     * @param executor  the {@link java.util.concurrent.Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     */
    default <T> void completeAsync(StreamObserver<T> observer, Callable<T> callable, Executor executor) {
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
    default <T> void complete(StreamObserver<T> observer, Runnable task, T result) {
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
    default <T> void completeAsync(StreamObserver<T> observer, Runnable task, T result) {
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
     * @param executor  the {@link java.util.concurrent.Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     */
    default <T> void completeAsync(StreamObserver<T> observer, Runnable task, T result, Executor executor) {
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
    default <T> void stream(StreamObserver<T> observer, Stream<? extends T> stream) {
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
     * @param executor  the {@link java.util.concurrent.Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     */
    default <T> void streamAsync(StreamObserver<T> observer, Stream<? extends T> stream, Executor executor) {
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
    default <T> void stream(StreamObserver<T> observer, Supplier<Stream<? extends T>> supplier) {
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
     * @param executor  the {@link java.util.concurrent.Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     */
    default <T> void streamAsync(StreamObserver<T> observer, Supplier<Stream<? extends T>> supplier, Executor executor) {
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
    default <T> Consumer<T> stream(StreamObserver<T> observer, CompletionStage<Void> stage) {
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
    default <T> Consumer<T> streamAsync(StreamObserver<T> observer, CompletionStage<Void> stage) {
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
     * @param executor  the {@link java.util.concurrent.Executor} on which to execute the asynchronous
     *                  request completion
     * @param <T>       they type of the request result
     *
     * @return a {@link Consumer} that can be used to send values to the {@link StreamObserver#onNext(Object)} method
     */
    default <T> Consumer<T> streamAsync(StreamObserver<T> observer, CompletionStage<Void> stage, Executor executor) {
        StreamObserver<T> safe = SafeStreamObserver.ensureSafeObserver(observer);
        stage.whenCompleteAsync(completeWithoutResult(safe), executor);
        return value -> CompletableFuture.runAsync(() -> safe.onNext(value), executor);
    }
}
