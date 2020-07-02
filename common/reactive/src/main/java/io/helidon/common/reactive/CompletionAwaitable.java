/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link CompletionStage} wrapper enriched with {@link io.helidon.common.reactive.Awaitable}.
 *
 * @param <T> payload type
 */
public class CompletionAwaitable<T> implements CompletionStage<T>, Awaitable<T> {

    private Supplier<CompletionStage<T>> originalStage;

    CompletionAwaitable(Supplier<CompletionStage<T>> originalStage, CompletionAwaitable<?> parent) {
        this.originalStage = originalStage;
    }

    CompletionAwaitable() {
    }

    void setOriginalStage(final Supplier<CompletionStage<T>> originalStage) {
        this.originalStage = originalStage;
    }

    @Override
    public <U> CompletionAwaitable<U> thenApply(final Function<? super T, ? extends U> fn) {
        CompletionStage<U> completionStage = originalStage.get().thenApply(fn);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
        CompletionStage<U> completionStage = originalStage.get().thenApplyAsync(fn);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> thenApplyAsync(final Function<? super T, ? extends U> fn, final Executor executor) {
        CompletionStage<U> completionStage = originalStage.get().thenApplyAsync(fn, executor);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> thenAccept(final Consumer<? super T> action) {
        CompletionStage<Void> completionStage = originalStage.get().thenAccept(action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);

    }

    @Override
    public CompletionAwaitable<Void> thenAcceptAsync(final Consumer<? super T> action) {
        CompletionStage<Void> completionStage = originalStage.get().thenAcceptAsync(action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> thenAcceptAsync(final Consumer<? super T> action, final Executor executor) {
        CompletionStage<Void> completionStage = originalStage.get().thenAcceptAsync(action, executor);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> thenRun(final Runnable action) {
        CompletionStage<Void> completionStage = originalStage.get().thenRun(action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> thenRunAsync(final Runnable action) {
        CompletionStage<Void> completionStage = originalStage.get().thenRunAsync(action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> thenRunAsync(final Runnable action, final Executor executor) {
        CompletionStage<Void> completionStage = originalStage.get().thenRunAsync(action, executor);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public <U, V> CompletionAwaitable<V> thenCombine(final CompletionStage<? extends U> other,
                                                     final BiFunction<? super T, ? super U, ? extends V> fn) {
        CompletionStage<V> completionStage = originalStage.get().thenCombine(other, fn);
        return new CompletionAwaitable<V>(() -> completionStage, this);
    }

    @Override
    public <U, V> CompletionAwaitable<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                          final BiFunction<? super T, ? super U, ? extends V> fn) {
        CompletionStage<V> completionStage = originalStage.get().thenCombineAsync(other, fn);
        return new CompletionAwaitable<V>(() -> completionStage, this);
    }

    @Override
    public <U, V> CompletionAwaitable<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                          final BiFunction<? super T, ? super U, ? extends V> fn,
                                                          final Executor executor) {
        CompletionStage<V> completionStage = originalStage.get().thenCombineAsync(other, fn, executor);
        return new CompletionAwaitable<V>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<Void> thenAcceptBoth(final CompletionStage<? extends U> other,
                                                        final BiConsumer<? super T, ? super U> action) {
        CompletionStage<Void> completionStage = originalStage.get().thenAcceptBoth(other, action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                             final BiConsumer<? super T, ? super U> action) {
        CompletionStage<Void> completionStage = originalStage.get().thenAcceptBothAsync(other, action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                             final BiConsumer<? super T, ? super U> action,
                                                             final Executor executor) {
        CompletionStage<Void> completionStage = originalStage.get().thenAcceptBothAsync(other, action, executor);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> runAfterBoth(final CompletionStage<?> other,
                                                  final Runnable action) {
        CompletionStage<Void> completionStage = originalStage.get().runAfterBoth(other, action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> runAfterBothAsync(final CompletionStage<?> other,
                                                       final Runnable action) {
        CompletionStage<Void> completionStage = originalStage.get().runAfterBothAsync(other, action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> runAfterBothAsync(final CompletionStage<?> other,
                                                       final Runnable action,
                                                       final Executor executor) {
        CompletionStage<Void> completionStage = originalStage.get().runAfterBothAsync(other, action, executor);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> applyToEither(final CompletionStage<? extends T> other,
                                                    final Function<? super T, U> fn) {
        CompletionStage<U> completionStage = originalStage.get().applyToEither(other, fn);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                         final Function<? super T, U> fn) {
        CompletionStage<U> completionStage = originalStage.get().applyToEitherAsync(other, fn);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                         final Function<? super T, U> fn,
                                                         final Executor executor) {
        CompletionStage<U> completionStage = originalStage.get().applyToEitherAsync(other, fn, executor);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> acceptEither(final CompletionStage<? extends T> other,
                                                  final Consumer<? super T> action) {
        CompletionStage<Void> completionStage = originalStage.get().acceptEither(other, action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                       final Consumer<? super T> action) {
        CompletionStage<Void> completionStage = originalStage.get().acceptEitherAsync(other, action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                       final Consumer<? super T> action,
                                                       final Executor executor) {
        CompletionStage<Void> completionStage = originalStage.get().acceptEitherAsync(other, action, executor);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> runAfterEither(final CompletionStage<?> other,
                                                    final Runnable action) {
        CompletionStage<Void> completionStage = originalStage.get().runAfterEither(other, action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> runAfterEitherAsync(final CompletionStage<?> other,
                                                         final Runnable action) {
        CompletionStage<Void> completionStage = originalStage.get().runAfterEitherAsync(other, action);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<Void> runAfterEitherAsync(final CompletionStage<?> other,
                                                         final Runnable action, final Executor executor) {
        CompletionStage<Void> completionStage = originalStage.get().runAfterEitherAsync(other, action, executor);
        return new CompletionAwaitable<Void>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
        CompletionStage<U> completionStage = originalStage.get().thenCompose(fn);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn) {
        CompletionStage<U> completionStage = originalStage.get().thenComposeAsync(fn);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> thenComposeAsync(final Function<? super T, ? extends CompletionStage<U>> fn,
                                                       final Executor executor) {
        CompletionStage<U> completionStage = originalStage.get().thenComposeAsync(fn, executor);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        CompletionStage<U> completionStage = originalStage.get().handle(fn);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn) {
        CompletionStage<U> completionStage = originalStage.get().handleAsync(fn);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public <U> CompletionAwaitable<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn,
                                                  final Executor executor) {
        CompletionStage<U> completionStage = originalStage.get().handleAsync(fn, executor);
        return new CompletionAwaitable<U>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        CompletionStage<T> completionStage = originalStage.get().whenComplete(action);
        return new CompletionAwaitable<T>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action) {
        CompletionStage<T> completionStage = originalStage.get().whenCompleteAsync(action);
        return new CompletionAwaitable<T>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action,
                                                    final Executor executor) {
        CompletionStage<T> completionStage = originalStage.get().whenCompleteAsync(action, executor);
        return new CompletionAwaitable<T>(() -> completionStage, this);
    }

    @Override
    public CompletionAwaitable<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        CompletionStage<T> completionStage = originalStage.get().exceptionally(fn);
        return new CompletionAwaitable<T>(() -> completionStage, this);
    }

    public CompletionAwaitable<T> exceptionallyAccept(final Consumer<Throwable> consumer) {
        return this.handle((item, t) -> {
            if (t != null) {
                consumer.accept(t);
            }
            return item;
        });
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return originalStage.get().toCompletableFuture();
    }
}
