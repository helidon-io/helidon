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
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.mapper.Mapper;

/**
 * Represents a {@link Flow.Publisher} that may: signal one item then completes, complete without
 * an item or signal an error.
 *
 * @param <T> item type
 * @see Multi
 */
public interface Single<T> extends Subscribable<T>, CompletionStage<T>, Awaitable<T> {

    // --------------------------------------------------------------------------------------------------------
    // Factory (source-like) methods
    // --------------------------------------------------------------------------------------------------------

    /**
     * Call the given supplier function for each individual downstream Subscriber
     * to return a Flow.Publisher to subscribe to.
     * @param supplier the callback to return a Flow.Publisher for each Subscriber
     * @param <T> the element type of the sequence
     * @return Multi
     * @throws NullPointerException if {@code supplier} is {@code null}
     */
    static <T> Single<T> defer(Supplier<? extends Single<? extends T>> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        return new SingleDefer<>(supplier);
    }

    /**
     * Get a {@link Single} instance that completes immediately.
     *
     * @param <T> item type
     * @return Single
     */
    static <T> Single<T> empty() {
        return SingleEmpty.instance();
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
     * Wrap a CompletionStage into a Multi and signal its outcome non-blockingly.
     * <p>
     *     A null result from the CompletionStage will yield a
     *     {@link NullPointerException} signal.
     * </p>
     * @param completionStage the CompletionStage to
     * @param <T> the element type of the stage and result
     * @return Single
     * @see #create(CompletionStage, boolean)
     * @deprecated use {@link #create(java.util.concurrent.CompletionStage)} instead
     */
    @Deprecated
    static <T> Single<T> from(CompletionStage<T> completionStage) {
        return create(completionStage, false);
    }

    /**
     * Wrap a CompletionStage into a Multi and signal its outcome non-blockingly.
     * @param completionStage the CompletionStage to
     * @param nullMeansEmpty if true, a null result is interpreted to be an empty sequence
     *                       if false, the resulting sequence fails with {@link NullPointerException}
     * @param <T> the element type of the stage and result
     * @return Single
     * @deprecated use {@link #create(java.util.concurrent.CompletionStage, boolean)} instead
     */
    @Deprecated
    static <T> Single<T> from(CompletionStage<T> completionStage, boolean nullMeansEmpty) {
        Objects.requireNonNull(completionStage, "completionStage is null");
        return new SingleFromCompletionStage<>(completionStage, nullMeansEmpty);
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
     * @deprecated use {@link #create(java.util.concurrent.Flow.Publisher)} instead
     */
    @Deprecated
    static <T> Single<T> from(Publisher<T> source) {
        Objects.requireNonNull(source, "source is null!");
        if (source instanceof Single) {
            return (Single<T>) source;
        }
        return new SingleFromPublisher<>(source);
    }

    /**
     * Create a {@link Single} instance that publishes the first and only item received from the given {@link Single}.
     *
     * @param <T>    item type
     * @param single source {@link Single} publisher
     * @return Single
     * @throws NullPointerException if source is {@code null}
     * @deprecated use {@link #create(io.helidon.common.reactive.Single)} instead
     */
    @Deprecated
    static <T> Single<T> from(Single<T> single) {
        return create((Publisher<T>) single);
    }

   /**
     * Wrap a CompletionStage into a Multi and signal its outcome non-blockingly.
     * <p>
     *     A null result from the CompletionStage will yield a
     *     {@link NullPointerException} signal.
     * </p>
     * @param completionStage the CompletionStage to
     * @param <T> the element type of the stage and result
     * @return Single
     * @see #create(CompletionStage, boolean)
     */
    static <T> Single<T> create(CompletionStage<T> completionStage) {
        return create(completionStage, false);
    }

    /**
     * Wrap a CompletionStage into a Multi and signal its outcome non-blockingly.
     * @param completionStage the CompletionStage to
     * @param nullMeansEmpty if true, a null result is interpreted to be an empty sequence
     *                       if false, the resulting sequence fails with {@link NullPointerException}
     * @param <T> the element type of the stage and result
     * @return Single
     */
    static <T> Single<T> create(CompletionStage<T> completionStage, boolean nullMeansEmpty) {
        Objects.requireNonNull(completionStage, "completionStage is null");
        return new SingleFromCompletionStage<>(completionStage, nullMeansEmpty);
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
    static <T> Single<T> create(Publisher<T> source) {
        Objects.requireNonNull(source, "source is null!");
        if (source instanceof Single) {
            return (Single<T>) source;
        }
        return new SingleFromPublisher<>(source);
    }

    /**
     * Create a {@link Single} instance that publishes the first and only item received from the given {@link Single}.
     *
     * @param <T>    item type
     * @param single source {@link Single} publisher
     * @return Single
     * @throws NullPointerException if source is {@code null}
     */
    static <T> Single<T> create(Single<T> single) {
        return create((Publisher<T>) single);
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
     * Get a {@link Single} instance that never completes.
     *
     * @param <T> item type
     * @return Single
     */
    static <T> Single<T> never() {
        return SingleNever.instance();
    }

    /**
     * Signal 0L and complete the sequence after the given time elapsed.
     * @param time the time to wait before signaling 0L and completion
     * @param unit the unit of time
     * @param executor the executor to run the waiting on
     * @return Single
     * @throws NullPointerException if {@code unit} or {@code executor} is {@code null}
     */
    static Single<Long> timer(long time, TimeUnit unit, ScheduledExecutorService executor) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(executor, "executor is null");
        return new SingleTimer(time, unit, executor);
    }

    /**
     * Apply the given {@code converter} function to the current {@code Single} instance
     * and return the value returned by this function.
     * <p>
     *     Note that the {@code converter} function is executed upon calling this method
     *     immediately and not when the resulting sequence gets subscribed to.
     * </p>
     * @param converter the function that receives the current {@code Single} instance and
     *                  should return a value to be returned by the method
     * @param <U> the output type
     * @return the value returned by the function
     * @throws NullPointerException if {@code converter} is {@code null}
     */
    default <U> U to(Function<? super Single<T>, ? extends U> converter) {
        return converter.apply(this);
    }

    // --------------------------------------------------------------------------------------------------------
    // Instance Operators
    // --------------------------------------------------------------------------------------------------------

    /**
     * Apply the given {@code composer} function to the current {@code Single} instance and
     * return the {@code Single} returned by this function.
     * <p>
     *     Note that the {@code composer} function is executed upon calling this method
     *     immediately and not when the resulting sequence gets subscribed to.
     * </p>
     * @param composer the function that receives the current {@code Single} instance and
     *                 should return a {@code Single} to be returned by the method
     * @param <U> the output element type
     * @return Single
     * @throws NullPointerException if {@code composer} is {@code null}
     */
    @SuppressWarnings("unchecked")
    default <U> Single<U> compose(Function<? super Single<T>, ? extends Single<? extends U>> composer) {
        return (Single<U>) to(composer);
    }

    /**
     * Signals the default item if the upstream is empty.
     * @param defaultItem the item to signal if the upstream is empty
     * @return Single
     * @throws NullPointerException if {@code defaultItem} is {@code null}
     */
    default Single<T> defaultIfEmpty(T defaultItem) {
        Objects.requireNonNull(defaultItem, "defaultItem is null");
        return new SingleDefaultIfEmpty<>(this, defaultItem);
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
     * Maps the single upstream value into an {@link Iterable} and relays its
     * items to the downstream.
     * @param mapper the function that receives the single upstream value and
     *               should return an Iterable instance
     * @param <U> the result type
     * @return Multi
     * @throws NullPointerException if {@code mapper} is {@code null}
     */
    default <U> Multi<U> flatMapIterable(Function<? super T, ? extends Iterable<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return new SingleFlatMapIterable<>(this, mapper);
    }

    /**
     * Map this {@link Single} instance to a {@link Single} using the given {@link Mapper}.
     *
     * @param <U>    mapped items type
     * @param mapper mapper
     * @return Single
     * @throws NullPointerException if mapper is {@code null}
     */
    default <U> Single<U> flatMapSingle(Function<? super T, ? extends Single<? extends U>> mapper) {
        return new SingleFlatMapSingle<>(this, mapper);
    }

    /**
     * Map this {@link Single} instance to a new {@link Single} of another type using the given {@link Function}.
     *
     * @param <U>    mapped item type
     * @param mapper mapper
     * @return Single
     * @throws NullPointerException if mapper is {@code null}
     */
    default <U> Single<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return new SingleMapperPublisher<>(this, mapper);
    }

    /**
     * Re-emit the upstream's signals to the downstream on the given executor's thread.
     * @param executor the executor to signal the downstream from.
     * @return Single
     * @throws NullPointerException if {@code executor} is {@code null}
     */
    default Single<T> observeOn(Executor executor) {
        Objects.requireNonNull(executor, "executor is null");
        return new SingleObserveOn<>(this, executor);
    }

    /**
     * Executes given {@link java.lang.Runnable} when a cancel signal is received.
     *
     * @param onCancel {@link java.lang.Runnable} to be executed.
     * @return Single
     */
    default Single<T> onCancel(Runnable onCancel) {
        return new SingleTappedPublisher<>(this,
                null,
                null,
                null,
                null,
                null,
                onCancel);
    }

    /**
     * Executes given {@link java.lang.Runnable} when onComplete signal is received.
     *
     * @param onComplete {@link java.lang.Runnable} to be executed.
     * @return Single
     */
    default Single<T> onComplete(Runnable onComplete) {
        return new SingleTappedPublisher<>(this,
                null,
                null,
                null,
                onComplete,
                null,
                null);
    }

    /**
     * Executes given {@link java.util.function.Consumer} when onError signal is received.
     *
     * @param onErrorConsumer {@link java.util.function.Consumer} to be executed.
     * @return Single
     */
    default Single<T> onError(Consumer<? super Throwable> onErrorConsumer) {
        return new SingleTappedPublisher<>(this,
                null,
                null,
                onErrorConsumer,
                null,
                null,
                null);
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
    default Single<T> onErrorResumeWithSingle(Function<? super Throwable, ? extends Single<? extends T>> onError) {
        return new SingleOnErrorResumeWith<>(this, onError);
    }

    /**
     * Resume stream from supplied publisher if onError signal is intercepted.
     *
     * @param onError supplier of new stream publisher
     * @return Single
     */
    default Multi<T> onErrorResumeWith(Function<? super Throwable, ? extends Flow.Publisher<? extends T>> onError) {
        return new MultiOnErrorResumeWith<>(Multi.create(this), onError);
    }

    /**
     * Resume stream from single item if onComplete signal is intercepted. Effectively do an {@code append} to the stream.
     *
     * @param item one item to resume stream with
     * @return Multi
     */
    default Multi<T> onCompleteResume(T item) {
        Objects.requireNonNull(item, "item is null");
        return onCompleteResumeWith(Multi.singleton(item));
    }

    /**
     * Resume stream from supplied publisher if onComplete signal is intercepted.
     *
     * @param publisher new stream publisher
     * @return Multi
     */
    default Multi<T> onCompleteResumeWith(Flow.Publisher<? extends T> publisher) {
        return new MultiOnCompleteResumeWith<>(Multi.create(this), publisher);
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
     * Invoke provided consumer for the item in stream.
     *
     * @param consumer consumer to be invoked
     * @return Single
     */
    default Single<T> peek(Consumer<? super T> consumer) {
        return new SingleTappedPublisher<>(this, null, consumer,
                null, null, null, null);
    }

    /**
     * Retry a failing upstream at most the given number of times before giving up.
     * @param count the number of times to retry; 0 means no retry at all
     * @return Single
     * @throws IllegalArgumentException if {@code count} is negative
     * @see #retryWhen(BiFunction)
     */
    default Single<T> retry(long count) {
        if (count < 0L) {
            throw new IllegalArgumentException("count >= 0L required");
        }
        return new SingleRetry<>(this, count);
    }

    /**
     * Retry a failing upstream if the predicate returns true.
     * @param predicate the predicate that receives the latest failure {@link Throwable}
     *                  the number of times the retry happened so far (0-based) and
     *                  should return {@code true} to retry the upstream again or
     *                  {@code false} to signal the latest failure
     * @return Single
     * @throws NullPointerException if {@code predicate} is {@code null}
     * @see #retryWhen(BiFunction)
     */
    default Single<T> retry(BiPredicate<? super Throwable, ? super Long> predicate) {
        Objects.requireNonNull(predicate, "whenFunction is null");
        return new SingleRetry<>(this, predicate);
    }

    /**
     * Retry a failing upstream when the given function returns a publisher that
     * signals an item.
     * <p>
     *     If the publisher returned by the function completes, the repetition stops
     *     and this Single is completed as empty.
     *     If the publisher signals an error, the repetition stops
     *     and this Single will signal this error.
     * </p>
     * @param whenFunction the function that receives the latest failure {@link Throwable}
     *                     the number of times the retry happened so far (0-based) and
     *                     should return a {@link Flow.Publisher} that should signal an item
     *                     to retry again, complete to stop and complete this Single
     *                     or signal an error to have this Single emit that error as well.
     * @param <U> the element type of the retry-signal sequence
     * @return Single
     * @throws NullPointerException if {@code whenFunction} is {@code null}
     */
    default <U> Single<T> retryWhen(
            BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction) {
        Objects.requireNonNull(whenFunction, "whenFunction is null");
        return new SingleRetry<>(this, whenFunction);
    }

    /**
     * Switch to the other Single if the upstream is empty.
     * @param other the Single to switch to if the upstream is empty.
     * @return Single
     * @throws NullPointerException if {@code other} is {@code null}
     */
    default Single<T> switchIfEmpty(Single<T> other) {
        Objects.requireNonNull(other, "other is null");
        return new SingleSwitchIfEmpty<>(this, other);
    }

    /**
     * Relay upstream items until the other source signals an item or completes.
     * @param other the other sequence to signal the end of the main sequence
     * @param <U> the element type of the other sequence
     * @return Single
     * @throws NullPointerException if {@code other} is {@code null}
     */
    default <U> Single<T> takeUntil(Flow.Publisher<U> other) {
        Objects.requireNonNull(other, "other is null");
        return new SingleTakeUntilPublisher<>(this, other);
    }

    /**
     * Signals a {@link TimeoutException} if the upstream doesn't signal an item, error
     * or completion within the specified time.
     * @param timeout the time to wait for the upstream to signal
     * @param unit the time unit
     * @param executor the executor to use for waiting for the upstream signal
     * @return Single
     * @throws NullPointerException if {@code unit} or {@code executor} is {@code null}
     */
    default Single<T> timeout(long timeout, TimeUnit unit, ScheduledExecutorService executor) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(executor, "executor is null");
        return new SingleTimeout<>(this, timeout, unit, executor, null);
    }

    /**
     * Switches to a fallback single if the upstream doesn't signal an item, error
     * or completion within the specified time.
     * @param timeout the time to wait for the upstream to signal
     * @param unit the time unit
     * @param executor the executor to use for waiting for the upstream signal
     * @param fallback the Single to switch to if the upstream doesn't signal in time
     * @return Single
     * @throws NullPointerException if {@code unit}, {@code executor}
     *                              or {@code fallback} is {@code null}
     */
    default Single<T> timeout(long timeout, TimeUnit unit, ScheduledExecutorService executor, Single<T> fallback) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(executor, "executor is null");
        Objects.requireNonNull(fallback, "fallback is null");
        return new SingleTimeout<>(this, timeout, unit, executor, fallback);
    }

    // --------------------------------------------------------------------------------------------------------
    // Terminal operators
    // --------------------------------------------------------------------------------------------------------

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
     * Exposes this {@link Single} instance as a {@link Single} with {@code Optional<T>} return type
     * of the asynchronous operation.
     * If this {@link Single} completes without a value, the resulting {@link Single} completes with an
     * empty {@link Optional}.
     *
     * @return CompletionStage
     */
    default Single<Optional<T>> toOptionalSingle() {
        try {
            SingleToOptionalFuture<T> subscriber = new SingleToOptionalFuture<>();
            this.subscribe(subscriber);
            return Single.create(subscriber);
        } catch (Throwable ex) {
            return Single.error(ex);
        }
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
            SingleToFuture<T> subscriber = new SingleToFuture<>(false);
            this.subscribe(subscriber);
            return subscriber;
        } catch (Throwable ex) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            return future;
        }
    }

    /**
     * Terminal stage, invokes provided consumer when Single is completed.
     *
     * @param consumer consumer to be invoked
     * @return Single completed when the stream terminates
     */
    default CompletionAwaitable<Void> forSingle(Consumer<T> consumer) {
        return this.thenAccept(consumer);
    }

    /**
     * Cancel upstream.
     *
     * @return new {@link Single} for eventually received single value.
     */
    default Single<T> cancel() {
        CompletableFuture<T> future = new CompletableFuture<>();
        FunctionalSubscriber<T> subscriber = new FunctionalSubscriber<>(future::complete,
                future::completeExceptionally,
                () -> future.complete(null),
                Flow.Subscription::cancel
        );
        this.subscribe(subscriber);
        return Single.create(future);
    }

    @Override
    <U> CompletionAwaitable<U> thenApply(Function<? super T, ? extends U> fn);

    @Override
    <U> CompletionAwaitable<U> thenApplyAsync(Function<? super T, ? extends U> fn);

    @Override
    <U> CompletionAwaitable<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor);

    @Override
    CompletionAwaitable<Void> thenAccept(Consumer<? super T> action);

    @Override
    CompletionAwaitable<Void> thenAcceptAsync(Consumer<? super T> action);

    @Override
    CompletionAwaitable<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor);

    @Override
    CompletionAwaitable<Void> thenRun(Runnable action);

    @Override
    CompletionAwaitable<Void> thenRunAsync(Runnable action);

    @Override
    CompletionAwaitable<Void> thenRunAsync(Runnable action, Executor executor);

    @Override
    <U, V> CompletionAwaitable<V> thenCombine(CompletionStage<? extends U> other,
                                              BiFunction<? super T, ? super U, ? extends V> fn);

    @Override
    <U, V> CompletionAwaitable<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                   BiFunction<? super T, ? super U, ? extends V> fn);

    @Override
    <U, V> CompletionAwaitable<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                   BiFunction<? super T, ? super U, ? extends V> fn, Executor executor);

    @Override
    <U> CompletionAwaitable<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                                                 BiConsumer<? super T, ? super U> action);

    @Override
    <U> CompletionAwaitable<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                      BiConsumer<? super T, ? super U> action);

    @Override
    <U> CompletionAwaitable<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                      BiConsumer<? super T, ? super U> action, Executor executor);

    @Override
    CompletionAwaitable<Void> runAfterBoth(CompletionStage<?> other, Runnable action);

    @Override
    CompletionAwaitable<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action);

    @Override
    CompletionAwaitable<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor);

    @Override
    <U> CompletionAwaitable<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn);

    @Override
    <U> CompletionAwaitable<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn);

    @Override
    <U> CompletionAwaitable<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn,
                                                  Executor executor);

    @Override
    CompletionAwaitable<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action);

    @Override
    CompletionAwaitable<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action);

    @Override
    CompletionAwaitable<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action,
                                                Executor executor);

    @Override
    CompletionAwaitable<Void> runAfterEither(CompletionStage<?> other, Runnable action);

    @Override
    CompletionAwaitable<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action);

    @Override
    CompletionAwaitable<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor);

    @Override
    <U> CompletionAwaitable<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

    @Override
    <U> CompletionAwaitable<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn);

    @Override
    <U> CompletionAwaitable<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor);

    @Override
    <U> CompletionAwaitable<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

    @Override
    <U> CompletionAwaitable<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn);

    @Override
    <U> CompletionAwaitable<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor);

    @Override
    CompletionAwaitable<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    @Override
    CompletionAwaitable<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action);

    @Override
    CompletionAwaitable<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor);

    @Override
    CompletionAwaitable<T> exceptionally(Function<Throwable, ? extends T> fn);
}
