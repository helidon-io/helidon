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
package io.helidon.common.reactive;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A completion stage that allows processing of cases when the element
 * is present and when not.
 */
public interface OptionalCompletionStage<T> extends CompletionStage<Optional<T>> {
    OptionalCompletionStage<T> onEmpty(Runnable runnable);
    OptionalCompletionStage<T> onValue(Consumer<T> consumer);

    static <T> OptionalCompletionStage<T> create(CompletionStage<Optional<T>> originalStage) {
        return new OptionalCompletionStage<T>() {
            @Override
            public OptionalCompletionStage<T> onEmpty(Runnable runnable) {
                originalStage.thenAccept(original -> {
                    if (!original.isPresent()) {
                        runnable.run();
                    }
                });
                return this;
            }

            @Override
            public OptionalCompletionStage<T> onValue(Consumer<T> consumer) {
                originalStage.thenAccept(original -> {
                    original.ifPresent(consumer);
                });
                return this;
            }

            public <U> CompletionStage<U> thenApply(Function<? super Optional<T>, ? extends U> fn) {
                return originalStage.thenApply(fn);
            }

            public <U> CompletionStage<U> thenApplyAsync(Function<? super Optional<T>, ? extends U> fn) {
                return originalStage.thenApplyAsync(fn);
            }

            public <U> CompletionStage<U> thenApplyAsync(Function<? super Optional<T>, ? extends U> fn,
                                                         Executor executor) {
                return originalStage.thenApplyAsync(fn, executor);
            }

            public CompletionStage<Void> thenAccept(Consumer<? super Optional<T>> action) {
                return originalStage.thenAccept(action);
            }

            public CompletionStage<Void> thenAcceptAsync(Consumer<? super Optional<T>> action) {
                return originalStage.thenAcceptAsync(action);
            }

            public CompletionStage<Void> thenAcceptAsync(Consumer<? super Optional<T>> action,
                                                         Executor executor) {
                return originalStage.thenAcceptAsync(action, executor);
            }

            public CompletionStage<Void> thenRun(Runnable action) {
                return originalStage.thenRun(action);
            }

            public CompletionStage<Void> thenRunAsync(Runnable action) {
                return originalStage.thenRunAsync(action);
            }

            public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
                return originalStage.thenRunAsync(action, executor);
            }

            public <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other,
                                                         BiFunction<? super Optional<T>, ? super U, ? extends V> fn) {
                return originalStage.thenCombine(other, fn);
            }

            public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                              BiFunction<? super Optional<T>, ? super U, ? extends V> fn) {
                return originalStage.thenCombineAsync(other, fn);
            }

            public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                              BiFunction<? super Optional<T>, ? super U, ? extends V> fn,
                                                              Executor executor) {
                return originalStage.thenCombineAsync(other, fn, executor);
            }

            public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                                                            BiConsumer<? super Optional<T>, ? super U> action) {
                return originalStage.thenAcceptBoth(other, action);
            }

            public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                                 BiConsumer<? super Optional<T>, ? super U> action) {
                return originalStage.thenAcceptBothAsync(other, action);
            }

            public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                                 BiConsumer<? super Optional<T>, ? super U> action,
                                                                 Executor executor) {
                return originalStage.thenAcceptBothAsync(other, action, executor);
            }

            public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
                return originalStage.runAfterBoth(other, action);
            }

            public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
                return originalStage.runAfterBothAsync(other, action);
            }

            public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
                return originalStage.runAfterBothAsync(other, action, executor);
            }

            public <U> CompletionStage<U> applyToEither(CompletionStage<? extends Optional<T>> other,
                                                        Function<? super Optional<T>, U> fn) {
                return originalStage.applyToEither(other, fn);
            }

            public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends Optional<T>> other,
                                                             Function<? super Optional<T>, U> fn) {
                return originalStage.applyToEitherAsync(other, fn);
            }

            public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends Optional<T>> other,
                                                             Function<? super Optional<T>, U> fn,
                                                             Executor executor) {
                return originalStage.applyToEitherAsync(other, fn, executor);
            }

            public CompletionStage<Void> acceptEither(CompletionStage<? extends Optional<T>> other,
                                                      Consumer<? super Optional<T>> action) {
                return originalStage.acceptEither(other, action);
            }

            public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends Optional<T>> other,
                                                           Consumer<? super Optional<T>> action) {
                return originalStage.acceptEitherAsync(other, action);
            }

            public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends Optional<T>> other,
                                                           Consumer<? super Optional<T>> action,
                                                           Executor executor) {
                return originalStage.acceptEitherAsync(other, action, executor);
            }

            public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
                return originalStage.runAfterEither(other, action);
            }

            public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
                return originalStage.runAfterEitherAsync(other, action);
            }

            public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                             Runnable action,
                                                             Executor executor) {
                return originalStage.runAfterEitherAsync(other, action, executor);
            }

            public <U> CompletionStage<U> thenCompose(Function<? super Optional<T>, ? extends CompletionStage<U>> fn) {
                return originalStage.thenCompose(fn);
            }

            public <U> CompletionStage<U> thenComposeAsync(Function<? super Optional<T>, ? extends CompletionStage<U>> fn) {
                return originalStage.thenComposeAsync(fn);
            }

            public <U> CompletionStage<U> thenComposeAsync(Function<? super Optional<T>, ? extends CompletionStage<U>> fn,
                                                           Executor executor) {
                return originalStage.thenComposeAsync(fn, executor);
            }

            public CompletionStage<Optional<T>> exceptionally(Function<Throwable, ? extends Optional<T>> fn) {
                return originalStage.exceptionally(fn);
            }

            public CompletionStage<Optional<T>> whenComplete(BiConsumer<? super Optional<T>, ? super Throwable> action) {
                return originalStage.whenComplete(action);
            }

            public CompletionStage<Optional<T>> whenCompleteAsync(BiConsumer<? super Optional<T>, ? super Throwable> action) {
                return originalStage.whenCompleteAsync(action);
            }

            public CompletionStage<Optional<T>> whenCompleteAsync(BiConsumer<? super Optional<T>, ? super Throwable> action,
                                                                  Executor executor) {
                return originalStage.whenCompleteAsync(action, executor);
            }

            public <U> CompletionStage<U> handle(BiFunction<? super Optional<T>, Throwable, ? extends U> fn) {
                return originalStage.handle(fn);
            }

            public <U> CompletionStage<U> handleAsync(BiFunction<? super Optional<T>, Throwable, ? extends U> fn) {
                return originalStage.handleAsync(fn);
            }

            public <U> CompletionStage<U> handleAsync(BiFunction<? super Optional<T>, Throwable, ? extends U> fn,
                                                      Executor executor) {
                return originalStage.handleAsync(fn, executor);
            }

            public CompletableFuture<Optional<T>> toCompletableFuture() {
                return originalStage.toCompletableFuture();
            }
        };
    }
}
