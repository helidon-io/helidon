/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.reactive;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.mapper.Mapper;

/**
 * Single item publisher utility.
 *
 * @param <T> item type
 */
public interface Single<T> extends Subscribable<T> {

    /**
     * Map this {@link Single} instance to a new {@link Single} of another type using the given {@link Mapper}.
     *
     * @param <U>    mapped item type
     * @param mapper mapper
     * @return Single
     * @throws NullPointerException if mapper is {@code null}
     */
    default <U> Single<U> map(Mapper<T, U> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return new SingleMapperPublisher<>(this, mapper);
    }

    /**
     * Map this {@link Single} instance to a publisher using the given {@link Mapper}.
     *
     * @param <U>    mapped items type
     * @param mapper mapper
     * @return Publisher
     * @throws NullPointerException if mapper is {@code null}
     * @deprecated Use {@link Single#flatMap}
     */
    @Deprecated
    default <U> Multi<U> mapMany(Mapper<T, Publisher<U>> mapper) {
        return flatMap(mapper::map);
    }

    /**
     * Map this {@link Single} instance to a publisher using the given {@link Mapper}.
     *
     * @param <U>    mapped items type
     * @param mapper mapper
     * @return Publisher
     * @throws NullPointerException if mapper is {@code null}
     */
    default <U> Multi<U> flatMap(Function<? super T, ? extends Publisher<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return new SingleFlatMapMulti<>(this, mapper);
    }

    /**
     * Map this {@link Single} instance to a {@link Single} using the given {@link Mapper}.
     *
     * @param <U>    mapped items type
     * @param mapper mapper
     * @return Single
     * @throws NullPointerException if mapper is {@code null}
     */
    default <U> Single<U> flatMapSingle(Function<T, Single<U>> mapper) {
        return new SingleFlatMapSingle<>(this, mapper);
    }

    /**
     * Exposes this {@link Single} instance as a {@link CompletionStage}.
     * Note that if this {@link Single} completes without a value, the resulting {@link CompletionStage} will be completed
     * exceptionally with an {@link IllegalStateException}
     *
     * @return CompletionStage
     */
    default CompletionStage<T> toStage() {
        try {
            SingleToFuture<T> subscriber = new SingleToFuture<>();
            this.subscribe(subscriber);
            return subscriber;
        } catch (Throwable ex) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            return future;
        }
    }

    /**
     * Exposes this {@link Single} instance as a {@link CompletionStage} with {@code Optional<T>} return type
     * of the asynchronous operation.
     * Note that if this {@link Single} completes without a value, the resulting {@link CompletionStage} will be completed
     * exceptionally with an {@link IllegalStateException}
     *
     * @return CompletionStage
     */
    default CompletionStage<Optional<T>> toOptionalStage() {
        try {
            SingleToOptionalFuture<T> subscriber = new SingleToOptionalFuture<>();
            this.subscribe(subscriber);
            return subscriber;
        } catch (Throwable ex) {
            CompletableFuture<Optional<T>> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            return future;
        }
    }

    /**
     * Short-hand for {@code  toFuture().toCompletableFuture().get()}.
     *
     * @return T
     * @throws InterruptedException if the current thread was interrupted while waiting
     * @throws ExecutionException   if the future completed exceptionally
     */
    default T get() throws InterruptedException, ExecutionException {
        return toStage().toCompletableFuture().get();
    }

    /**
     * Short-hand for {@code toFuture().toCompletableFuture().get()}.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return T
     * @throws InterruptedException if the current thread was interrupted while waiting
     * @throws ExecutionException   if the future completed exceptionally
     * @throws TimeoutException     if the wait timed out
     */
    default T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return toStage().toCompletableFuture().get(timeout, unit);
    }

    /**
     * Create a {@link Single} instance that publishes the first and only item received from the given publisher. Note that if the
     * publisher publishes more than one item, the resulting {@link Single} will hold an error. Use {@link Multi#first()} instead
     * in order to get the first item of a publisher that may publish more than one item.
     *
     * @param <T>    item type
     * @param source source publisher
     * @return Single
     * @throws NullPointerException if source is {@code null}
     */
    static <T> Single<T> from(Publisher<T> source) {
        Objects.requireNonNull(source, "source is null!");
        if (source instanceof Single) {
            return (Single<T>) source;
        }
        return new SingleFromPublisher<>(source);
    }

    /**
     * Create a {@link Single} instance that publishes the given item to its subscriber(s).
     *
     * @param <T>  item type
     * @param item item to publish
     * @return Single
     * @throws NullPointerException if item is {@code null}
     */
    static <T> Single<T> just(T item) {
        return new SingleJust<>(item);
    }

    /**
     * Create a {@link Single} instance that reports the given given exception to its subscriber(s). The exception is reported by
     * invoking {@link Subscriber#onError(java.lang.Throwable)} when {@link Publisher#subscribe(Subscriber)} is called.
     *
     * @param <T>   item type
     * @param error exception to hold
     * @return Single
     * @throws NullPointerException if error is {@code null}
     */
    static <T> Single<T> error(Throwable error) {
        return new SingleError<>(error);
    }

    /**
     * Get a {@link Single} instance that completes immediately.
     *
     * @param <T> item type
     * @return Single
     */
    static <T> Single<T> empty() {
        return SingleEmpty.<T>instance();
    }

    /**
     * Get a {@link Single} instance that never completes.
     *
     * @param <T> item type
     * @return Single
     */
    static <T> Single<T> never() {
        return SingleNever.<T>instance();
    }

    /**
     * Executes given {@link java.lang.Runnable} when any of signals onComplete, onCancel or onError is received.
     *
     * @param onTerminate {@link java.lang.Runnable} to be executed.
     * @return Single
     */
    default Single<T> onTerminate(Runnable onTerminate) {
        return new SingleTappedPublisher<>(this,
                null,
                null,
                e -> onTerminate.run(),
                onTerminate,
                null,
                onTerminate);
    }

    /**
     * Executes given {@link java.lang.Runnable} when onComplete signal is received.
     *
     * @param onTerminate {@link java.lang.Runnable} to be executed.
     * @return Single
     */
    default Single<T> onComplete(Runnable onTerminate) {
        return new SingleTappedPublisher<>(this,
                null,
                null,
                null,
                onTerminate,
                null,
                null);
    }

    /**
     * Executes given {@link java.lang.Runnable} when onError signal is received.
     *
     * @param onErrorConsumer {@link java.lang.Runnable} to be executed.
     * @return Single
     */
    default Single<T> onError(Consumer<Throwable> onErrorConsumer) {
        return new SingleTappedPublisher<>(this,
                null,
                null,
                onErrorConsumer,
                null,
                null,
                null);
    }

    /**
     * Invoke provided consumer for the item in stream.
     *
     * @param consumer consumer to be invoked
     * @return Single
     */
    default Single<T> peek(Consumer<T> consumer) {
        return new SingleTappedPublisher<>(this, null, consumer,
                null, null, null, null);
    }

    /**
     * {@link java.util.function.Function} providing one item to be submitted as onNext in case of onError signal is received.
     *
     * @param onError Function receiving {@link java.lang.Throwable} as argument and producing one item to resume stream with.
     * @return Single
     */
    default Single<T> onErrorResume(Function<? super Throwable, ? extends T> onError) {
        return new SingleOnErrorResume<>(this, onError);
    }


    /**
     * Resume stream from supplied publisher if onError signal is intercepted.
     *
     * @param onError supplier of new stream publisher
     * @return Single
     */
    default Single<T> onErrorResumeWith(Function<? super Throwable, ? extends Single<? extends T>> onError) {
        return new SingleOnErrorResumeWith<>(this, onError);
    }
}
