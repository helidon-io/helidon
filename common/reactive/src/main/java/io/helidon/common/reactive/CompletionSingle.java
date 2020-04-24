/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.common.reactive;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Single as CompletionStage.
 *
 * @param <T> payload type
 */
public abstract class CompletionSingle<T> implements Single<T> {

    private final AtomicReference<CompletableFuture<T>> stageReference = new AtomicReference<>();
    private final CompletableFuture<Void> cancelFuture = new CompletableFuture<>();

    private CompletableFuture<T> getLazyStage() {
        stageReference.compareAndSet(null, this.toNullableStage());
        return stageReference.get();
    }

    private CompletableFuture<T> toNullableStage() {
        SingleToFuture<T> subscriber = new SingleToFuture<>(true);
        this.subscribe(subscriber);
        return subscriber;
    }

    @Override
    public Single<T> onCancel(final Runnable onCancel) {
        cancelFuture.thenRun(onCancel);
        return this;
    }

    @Override
    public Single<T> cancel() {
        Single<T> single = Single.super.cancel();
        this.cancelFuture.complete(null);
        return single;
    }

    @Override
    public <U> CompletionStage<U> thenApply(final Function<? super T, ? extends U> fn) {
        return getLazyStage().thenApply(fn);
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
        return getLazyStage().thenApplyAsync(fn);
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(final Function<? super T, ? extends U> fn, final Executor executor) {
        return getLazyStage().thenApplyAsync(fn, executor);
    }

    @Override
    public CompletionStage<Void> thenAccept(final Consumer<? super T> action) {
        return getLazyStage().thenAccept(action);
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(final Consumer<? super T> action) {
        return getLazyStage().thenAcceptAsync(action);
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(final Consumer<? super T> action, final Executor executor) {
        return getLazyStage().thenAcceptAsync(action, executor);
    }

    @Override
    public CompletionStage<Void> thenRun(final Runnable action) {
        return getLazyStage().thenRun(action);
    }

    @Override
    public CompletionStage<Void> thenRunAsync(final Runnable action) {
        return getLazyStage().thenRunAsync(action);
    }

    @Override
    public CompletionStage<Void> thenRunAsync(final Runnable action, final Executor executor) {
        return getLazyStage().thenRunAsync(action, executor);
    }

    @Override
    public <U, V> CompletionStage<V> thenCombine(final CompletionStage<? extends U> other,
                                                 final BiFunction<? super T, ? super U, ? extends V> fn) {
        return getLazyStage().thenCombine(other, fn);
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                      final BiFunction<? super T, ? super U, ? extends V> fn) {
        return getLazyStage().thenCombineAsync(other, fn);
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                      final BiFunction<? super T, ? super U, ? extends V> fn,
                                                      final Executor executor) {
        return getLazyStage().thenCombineAsync(other, fn, executor);
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBoth(final CompletionStage<? extends U> other,
                                                    final BiConsumer<? super T, ? super U> action) {
        return getLazyStage().thenAcceptBoth(other, action);
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                         final BiConsumer<? super T, ? super U> action) {
        return getLazyStage().thenAcceptBothAsync(other, action);
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                         final BiConsumer<? super T, ? super U> action,
                                                         final Executor executor) {
        return getLazyStage().thenAcceptBothAsync(other, action, executor);
    }

    @Override
    public CompletionStage<Void> runAfterBoth(final CompletionStage<?> other,
                                              final Runnable action) {
        return getLazyStage().runAfterBoth(other, action);
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(final CompletionStage<?> other,
                                                   final Runnable action) {
        return getLazyStage().runAfterBothAsync(other, action);
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(final CompletionStage<?> other,
                                                   final Runnable action,
                                                   final Executor executor) {
        return getLazyStage().runAfterBothAsync(other, action, executor);
    }

    @Override
    public <U> CompletionStage<U> applyToEither(final CompletionStage<? extends T> other,
                                                final Function<? super T, U> fn) {
        return getLazyStage().applyToEither(other, fn);
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                     final Function<? super T, U> fn) {
        return getLazyStage().applyToEitherAsync(other, fn);
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                     final Function<? super T, U> fn,
                                                     final Executor executor) {
        return getLazyStage().applyToEitherAsync(other, fn, executor);
    }

    @Override
    public CompletionStage<Void> acceptEither(final CompletionStage<? extends T> other,
                                              final Consumer<? super T> action) {
        return getLazyStage().acceptEither(other, action);
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                   final Consumer<? super T> action) {
        return getLazyStage().acceptEitherAsync(other, action);
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                   final Consumer<? super T> action,
                                                   final Executor executor) {
        return getLazyStage().acceptEitherAsync(other, action, executor);
    }

    @Override
    public CompletionStage<Void> runAfterEither(final CompletionStage<?> other,
                                                final Runnable action) {
        return getLazyStage().runAfterEither(other, action);
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(final CompletionStage<?> other,
                                                     final Runnable action) {
        return getLazyStage().runAfterEitherAsync(other, action);
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(final CompletionStage<?> other,
                                                     final Runnable action, final Executor executor) {
        return getLazyStage().runAfterEitherAsync(other, action, executor);
    }

    @Override
    public <U> CompletionStage<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return getLazyStage().thenCompose(fn);
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn) {
        return getLazyStage().thenComposeAsync(fn);
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn,
                                                   final Executor executor) {
        return getLazyStage().thenComposeAsync(fn, executor);
    }

    @Override
    public <U> CompletionStage<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return getLazyStage().handle(fn);
    }

    @Override
    public <U> CompletionStage<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn) {
        return getLazyStage().handleAsync(fn);
    }

    @Override
    public <U> CompletionStage<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn,
                                              final Executor executor) {
        return getLazyStage().handleAsync(fn, executor);
    }

    @Override
    public CompletionStage<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        return getLazyStage().whenComplete(action);
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action) {
        return getLazyStage().whenCompleteAsync(action);
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action,
                                                final Executor executor) {
        return getLazyStage().whenCompleteAsync(action, executor);
    }

    @Override
    public CompletionStage<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        return getLazyStage().exceptionally(fn);
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return getLazyStage().toCompletableFuture();
    }
}
