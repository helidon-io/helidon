/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webclient;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.reactive.CompletionAwaitable;
import io.helidon.common.reactive.CompletionSingle;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

class SingleWithContext<T> extends CompletionSingle<T> {

    private final Single<T> delegate;
    private final Context context;

    SingleWithContext(Single<T> delegate, Context context) {
        this.delegate = Objects.requireNonNull(delegate);
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public T await() {
        return delegate.await();
    }

    @Override
    public T await(long timeout, TimeUnit unit) {
        return delegate.await(timeout, unit);
    }

    @Override
    public T await(Duration duration) {
        return delegate.await(duration);
    }

    @Override
    public <U> U to(Function<? super Single<T>, ? extends U> converter) {
        return delegate.to(converter);
    }

    @Override
    public <U> Single<U> compose(Function<? super Single<T>, ? extends Single<? extends U>> composer) {
        return delegate.compose(composer);
    }

    @Override
    public Single<T> defaultIfEmpty(T defaultItem) {
        return delegate.defaultIfEmpty(defaultItem);
    }

    @Override
    public Single<T> defaultIfEmpty(Supplier<? extends T> supplier) {
        return delegate.defaultIfEmpty(supplier);
    }

    @Override
    public <U> Multi<U> flatMap(Function<? super T, ? extends Flow.Publisher<? extends U>> mapper) {
        return delegate.flatMap(mapper);
    }

    @Override
    public <U> Multi<U> flatMapIterable(Function<? super T, ? extends Iterable<? extends U>> mapper) {
        return delegate.flatMapIterable(mapper);
    }

    @Override
    public <U> Single<U> flatMapSingle(Function<? super T, ? extends Single<? extends U>> mapper) {
        return delegate.flatMapSingle(mapper);
    }

    @Override
    public <U> Single<U> flatMapCompletionStage(Function<? super T, ? extends CompletionStage<? extends U>> mapper) {
        return delegate.flatMapCompletionStage(mapper);
    }

    @Override
    public <U> Single<U> flatMapOptional(Function<? super T, Optional<? extends U>> mapper) {
        return delegate.flatMapOptional(mapper);
    }

    @Override
    public <U> Single<U> map(Function<? super T, ? extends U> mapper) {
        return delegate.map(mapper);
    }

    @Override
    public Single<T> observeOn(Executor executor) {
        return delegate.observeOn(executor);
    }

    @Override
    public Single<T> onCancel(Runnable onCancel) {
        return delegate.onCancel(onCancel);
    }

    @Override
    public Single<T> onComplete(Runnable onComplete) {
        return delegate.onComplete(onComplete);
    }

    @Override
    public Single<T> onError(Consumer<? super Throwable> onErrorConsumer) {
        return delegate.onError(onErrorConsumer);
    }

    @Override
    public Single<T> onErrorResume(Function<? super Throwable, ? extends T> onError) {
        return delegate.onErrorResume(onError);
    }

    @Override
    public Single<T> onErrorResumeWithSingle(Function<? super Throwable, ? extends Single<? extends T>> onError) {
        return delegate.onErrorResumeWithSingle(onError);
    }

    @Override
    public Multi<T> onErrorResumeWith(Function<? super Throwable, ? extends Flow.Publisher<? extends T>> onError) {
        return delegate.onErrorResumeWith(onError);
    }

    @Override
    public Multi<T> onCompleteResume(T item) {
        return delegate.onCompleteResume(item);
    }

    @Override
    public Multi<T> onCompleteResumeWith(Flow.Publisher<? extends T> publisher) {
        return delegate.onCompleteResumeWith(publisher);
    }

    @Override
    public Single<T> onCompleteResumeWithSingle(Function<Optional<T>, ? extends Single<? extends T>> onComplete) {
        return delegate.onCompleteResumeWithSingle(onComplete);
    }

    @Override
    public Single<T> onTerminate(Runnable onTerminate) {
        return delegate.onTerminate(onTerminate);
    }

    @Override
    public Single<T> ifEmpty(Runnable ifEmpty) {
        return delegate.ifEmpty(ifEmpty);
    }

    @Override
    public Single<T> peek(Consumer<? super T> consumer) {
        return delegate.peek(consumer);
    }

    @Override
    public Single<T> log() {
        return delegate.log();
    }

    @Override
    public Single<T> log(Level level) {
        return delegate.log(level);
    }

    @Override
    public Single<T> log(Level level, String loggerName) {
        return delegate.log(level, loggerName);
    }

    @Override
    public Single<T> log(Level level, boolean trace) {
        return delegate.log(level, trace);
    }

    @Override
    public Single<T> retry(long count) {
        return delegate.retry(count);
    }

    @Override
    public Single<T> retry(BiPredicate<? super Throwable, ? super Long> predicate) {
        return delegate.retry(predicate);
    }

    @Override
    public <U> Single<T> retryWhen(BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction) {
        return delegate.retryWhen(whenFunction);
    }

    @Override
    public Single<T> switchIfEmpty(Single<T> other) {
        return delegate.switchIfEmpty(other);
    }

    @Override
    public <U> Single<T> takeUntil(Flow.Publisher<U> other) {
        return delegate.takeUntil(other);
    }

    @Override
    public Single<T> timeout(long timeout, TimeUnit unit, ScheduledExecutorService executor) {
        return delegate.timeout(timeout, unit, executor);
    }

    @Override
    public Single<T> timeout(long timeout, TimeUnit unit, ScheduledExecutorService executor, Single<T> fallback) {
        return delegate.timeout(timeout, unit, executor, fallback);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return delegate.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.get(timeout, unit);
    }

    @Override
    public Single<Optional<T>> toOptionalSingle() {
        return delegate.toOptionalSingle();
    }

    @Override
    public CompletionStage<T> toStage() {
        return delegate.toStage();
    }

    @Override
    public CompletionStage<T> toStage(boolean completeWithoutValue) {
        return delegate.toStage(completeWithoutValue);
    }

    @Override
    public CompletionAwaitable<Void> forSingle(Consumer<T> consumer) {
        return delegate.forSingle(consumer);
    }

    @Override
    public CompletionAwaitable<Void> ignoreElement() {
        return delegate.ignoreElement();
    }

    @Override
    public Single<T> cancel() {
        return delegate.cancel();
    }

    @Override
    public <U> CompletionAwaitable<U> thenApply(Function<? super T, ? extends U> fn) {
        return delegate.thenApply(wrapFunction(fn));
    }

    @Override
    public <U> CompletionAwaitable<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return delegate.thenApplyAsync(wrapFunction(fn));
    }

    @Override
    public <U> CompletionAwaitable<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return delegate.thenApplyAsync(wrapFunction(fn), executor);
    }

    @Override
    public CompletionAwaitable<Void> thenAccept(Consumer<? super T> action) {
        return delegate.thenAccept(wrapConsumer(action));
    }

    @Override
    public CompletionAwaitable<Void> thenAcceptAsync(Consumer<? super T> action) {
        return delegate.thenAcceptAsync(wrapConsumer(action));
    }

    @Override
    public CompletionAwaitable<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return delegate.thenAcceptAsync(wrapConsumer(action), executor);
    }

    @Override
    public CompletionAwaitable<Void> thenRun(Runnable action) {
        return delegate.thenRun(wrapRunnable(action));
    }

    @Override
    public CompletionAwaitable<Void> thenRunAsync(Runnable action) {
        return delegate.thenRunAsync(wrapRunnable(action));
    }

    @Override
    public CompletionAwaitable<Void> thenRunAsync(Runnable action, Executor executor) {
        return delegate.thenRunAsync(wrapRunnable(action), executor);
    }

    @Override
    public <U, V> CompletionAwaitable<V> thenCombine(CompletionStage<? extends U> other,
                                                     BiFunction<? super T, ? super U, ? extends V> fn) {
        return delegate.thenCombine(other, wrapBiFunction(fn));
    }

    @Override
    public <U, V> CompletionAwaitable<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                          BiFunction<? super T, ? super U, ? extends V> fn) {
        return delegate.thenCombineAsync(other, wrapBiFunction(fn));
    }

    @Override
    public <U, V> CompletionAwaitable<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                          BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return delegate.thenCombineAsync(other, wrapBiFunction(fn), executor);
    }

    @Override
    public <U> CompletionAwaitable<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                                                        BiConsumer<? super T, ? super U> action) {
        return delegate.thenAcceptBoth(other, wrapBiConsumer(action));
    }

    @Override
    public <U> CompletionAwaitable<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                             BiConsumer<? super T, ? super U> action) {
        return delegate.thenAcceptBothAsync(other, wrapBiConsumer(action));
    }

    @Override
    public <U> CompletionAwaitable<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                             BiConsumer<? super T, ? super U> action, Executor executor) {
        return delegate.thenAcceptBothAsync(other, wrapBiConsumer(action), executor);
    }

    @Override
    public CompletionAwaitable<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return delegate.runAfterBoth(other, wrapRunnable(action));
    }

    @Override
    public CompletionAwaitable<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return delegate.runAfterBothAsync(other, wrapRunnable(action));
    }

    @Override
    public CompletionAwaitable<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return delegate.runAfterBothAsync(other, wrapRunnable(action), executor);
    }

    @Override
    public <U> CompletionAwaitable<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return delegate.applyToEither(other, wrapFunction(fn));
    }

    @Override
    public <U> CompletionAwaitable<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return delegate.applyToEitherAsync(other, wrapFunction(fn));
    }

    @Override
    public <U> CompletionAwaitable<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn,
                                                         Executor executor) {
        return delegate.applyToEitherAsync(other, wrapFunction(fn), executor);
    }

    @Override
    public CompletionAwaitable<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return delegate.acceptEither(other, wrapConsumer(action));
    }

    @Override
    public CompletionAwaitable<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return delegate.acceptEitherAsync(other, wrapConsumer(action));
    }

    @Override
    public CompletionAwaitable<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action,
                                                       Executor executor) {
        return delegate.acceptEitherAsync(other, wrapConsumer(action), executor);
    }

    @Override
    public CompletionAwaitable<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return delegate.runAfterEither(other, wrapRunnable(action));
    }

    @Override
    public CompletionAwaitable<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return delegate.runAfterEitherAsync(other, wrapRunnable(action));
    }

    @Override
    public CompletionAwaitable<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return delegate.runAfterEitherAsync(other, wrapRunnable(action), executor);
    }

    @Override
    public <U> CompletionAwaitable<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return delegate.thenCompose(wrapFunction(fn));
    }

    @Override
    public <U> CompletionAwaitable<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return delegate.thenComposeAsync(wrapFunction(fn));
    }

    @Override
    public <U> CompletionAwaitable<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return delegate.thenComposeAsync(wrapFunction(fn), executor);
    }

    @Override
    public <U> CompletionAwaitable<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return delegate.handle(wrapBiFunctionThrowable(fn));
    }

    @Override
    public <U> CompletionAwaitable<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return delegate.handleAsync(wrapBiFunctionThrowable(fn));
    }

    @Override
    public <U> CompletionAwaitable<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return delegate.handleAsync(wrapBiFunctionThrowable(fn), executor);
    }

    @Override
    public CompletionAwaitable<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return delegate.whenComplete(wrapBiConsumer(action));
    }

    @Override
    public CompletionAwaitable<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return delegate.whenCompleteAsync(wrapBiConsumer(action));
    }

    @Override
    public CompletionAwaitable<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return delegate.whenCompleteAsync(wrapBiConsumer(action), executor);
    }

    @Override
    public CompletionAwaitable<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return delegate.exceptionally(wrapFunctionThrowable(fn));
    }

    @Override
    public CompletionAwaitable<T> exceptionallyAccept(Consumer<Throwable> consumer) {
        return delegate.exceptionallyAccept(wrapConsumerThrowable(consumer));
    }

    @Override
    public void subscribe(Consumer<? super T> consumer) {
        delegate.subscribe(wrapConsumer(consumer));
    }

    @Override
    public void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer) {
        delegate.subscribe(wrapConsumer(consumer), wrapConsumerThrowable(errorConsumer));
    }

    @Override
    public void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer, Runnable completeConsumer) {
        delegate.subscribe(wrapConsumer(consumer), wrapConsumerThrowable(errorConsumer), wrapRunnable(completeConsumer));
    }

    @Override
    public void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer,
                          Runnable completeConsumer, Consumer<? super Flow.Subscription> subscriptionConsumer) {
        delegate.subscribe(wrapConsumer(consumer),
                wrapConsumerThrowable(errorConsumer),
                wrapRunnable(completeConsumer),
                t -> Contexts.runInContext(context, () -> subscriptionConsumer.accept(t)));
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        delegate.subscribe(subscriber);
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return delegate.toCompletableFuture();
    }

    // -- Wrapper functions ---------------------------------------------------

    private Consumer<T> wrapConsumer(Consumer<? super T> action) {
        return t -> Contexts.runInContext(context, () -> action.accept(t));
    }

    private <U extends Throwable> Consumer<U> wrapConsumerThrowable(Consumer<? super Throwable> action) {
        return t -> Contexts.runInContext(context, () -> action.accept(t));
    }

    private Runnable wrapRunnable(Runnable action) {
        return () -> Contexts.runInContext(context, action);
    }

    private <U> Function<? super T, ? extends U> wrapFunction(Function<? super T, ? extends U> fn) {
        return t -> Contexts.runInContext(context, () -> fn.apply(t));
    }

    private Function<Throwable, ? extends T> wrapFunctionThrowable(Function<Throwable, ? extends T> fn) {
        return t -> Contexts.runInContext(context, () -> fn.apply(t));
    }

    private <U, V, R extends V> BiFunction<? super T, ? super U, R> wrapBiFunction(
            BiFunction<? super T, ? super U, R> fn) {
        return (t, u) -> Contexts.runInContext(context, () -> fn.apply(t, u));
    }

    private <U, V, R extends V> BiFunction<? super T, Throwable, R> wrapBiFunctionThrowable(
            BiFunction<? super T, Throwable, R> fn) {
        return (t, u) -> Contexts.runInContext(context, () -> fn.apply(t, u));
    }

    private <U> BiConsumer<? super T, ? super U> wrapBiConsumer(BiConsumer<? super T, ? super U> action) {
        return (t, u) -> Contexts.runInContext(context, () -> action.accept(t, u));
    }
}
