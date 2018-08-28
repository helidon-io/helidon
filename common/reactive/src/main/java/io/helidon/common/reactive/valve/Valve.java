/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

import io.helidon.common.reactive.Flow;

/**
 * Represents a reactive source of data which can be {@link #pause() paused} and {@link #resume() resumed}.
 *
 * @param <T> a type of items produced by {@code Valve} API
 */
public interface Valve<T> extends Pausable {

    /**
     * Register data handlers (callbacks).
     *
     * @param onData     a callback for data chunks and {@link Pausable} representing a <i>faucet</i> of this {@link Valve}.
     * @param onError    a callback of errors or {@code null}
     * @param onComplete a callback for completion event or {@code null}
     * @throws NullPointerException  if {@code onData} parameter is {@code null}
     * @throws IllegalStateException if cannot register new callback. For example if instance accepts only a single handler.
     */
    void handle(BiConsumer<T, Pausable> onData, Consumer<Throwable> onError, Runnable onComplete);

    /**
     * Register data handlers (callbacks).
     *
     * @param onData a callback for data chunks and {@link Pausable} representing a <i>faucet</i> of this {@link Valve}.
     * @throws NullPointerException  if {@code onData} parameter is {@code null}
     * @throws IllegalStateException if cannot register new callback. For example if instance accepts only a single handler.
     */
    default void handle(BiConsumer<T, Pausable> onData) {
        handle(onData, null, null);
    }

    /**
     * Register data handlers (callbacks).
     *
     * @param onData  a callback for data chunks and {@link Pausable} representing a <i>faucet</i> of this {@link Valve}.
     * @param onError a callback of errors or {@code null}
     * @throws NullPointerException  if {@code onData} parameter is {@code null}
     * @throws IllegalStateException if cannot register new callback. For example if instance accepts only a single handler.
     */
    default void handle(BiConsumer<T, Pausable> onData, Consumer<Throwable> onError) {
        handle(onData, onError, null);
    }

    /**
     * Register data handlers (callbacks).
     *
     * @param onData     a callback for data chunks.
     * @param onError    a callback of errors or {@code null}
     * @param onComplete a callback for completion event or {@code null}
     * @throws NullPointerException  if {@code onData} parameter is {@code null}
     * @throws IllegalStateException if cannot register new callback. For example if instance accepts only a single handler.
     */
    default void handle(Consumer<T> onData, Consumer<Throwable> onError, Runnable onComplete) {
        handle((t, p) -> onData.accept(t), onError, onComplete);
    }

    /**
     * Register data handlers (callbacks).
     *
     * @param onData a callback for data chunks.
     * @throws NullPointerException  if {@code onData} parameter is {@code null}
     * @throws IllegalStateException if cannot register new callback. For example if instance accepts only a single handler.
     */
    default void handle(Consumer<T> onData) {
        handle(onData, null, null);
    }

    /**
     * Register data handlers (callbacks).
     *
     * @param onData  a callback for data chunks.
     * @param onError a callback of errors or {@code null}
     * @throws NullPointerException  if {@code onData} parameter is {@code null}
     * @throws IllegalStateException if cannot register new callback. For example if instance accepts only a single handler.
     */
    default void handle(Consumer<T> onData, Consumer<Throwable> onError) {
        handle(onData, onError, null);
    }

    /**
     * Returns a {@link Valve} consisting of the results of applying the given function to the elements of this {@link Valve}.
     *
     * @param <K>    the element type of the new {@link Valve}
     * @param mapper a stateless function to apply to each element
     * @return the new {@code Valve}
     */
    default <K> Valve<K> map(Function<T, K> mapper) {
        return new ValveFilter<>(this, onData -> (t, p) -> onData.accept(mapper.apply(t), p));
    }

    /**
     * Returns new {@code Valve} instance which combines all results into a single Valve.
     * <p>
     * If provided {@code mapFunction} returns {@code null} then this result is skipped.
     *
     * @param mapFunction maps an element into new {@link Valve} instance.
     * @param <K>         the element type of the new {@link Valve}
     * @return the new {@code Valve}
     */
    default <K> Valve<K> flatMap(Function<T, Valve<K>> mapFunction) {
        throw new UnsupportedOperationException("Not implemented!");
    }

    /**
     * Returns a {@link Valve} consisting of the elements of this {@link Valve} that match the given predicate.
     *
     * @param predicate a stateless predicate to apply to each element to determine if it should be included
     * @return the new {@code Valve}
     */
    default Valve<T> filter(Predicate<T> predicate) {
        return new ValveFilter<>(this, onData -> (t, p) -> {
            if (predicate.test(t)) {
                onData.accept(t, p);
            }
        });
    }

    /**
     * Returns a {@link Valve} consisting of the elements of this {@link Valve}, additionally
     * performing the provided action on each element as elements are consumed from the resulting {@link Valve}.
     *
     * @param action an action to perform on the elements as they are consumed from the {@code Valve}
     * @return the new {@code Valve}
     */
    default Valve<T> peek(Consumer<T> action) {
        return new ValveFilter<>(this, onData -> (element, p) -> {
            action.accept(element);
            onData.accept(element, p);
        });
    }

    /**
     * Returns a {@link CompletionStage} which will be completed when this {@link Valve} is completed and result is a collected
     * value.
     * <p>
     * Result completes exceptionally if this {@code Valve} completes exceptionally.
     *
     * @param collector a collector to use
     * @param <A>       the intermediate accumulation type of the {@code Collector}
     * @param <R>       the type of the result
     * @return a completion stage of collected result
     */
    default <A, R> CompletionStage<R> collect(Collector<? super T, A, R> collector) {
        Set<Collector.Characteristics> characteristics = collector.characteristics();
        if (characteristics.contains(Collector.Characteristics.CONCURRENT)) {
            A intermediateCollection = collector.supplier().get();
            BiConsumer<A, ? super T> accumulator = collector.accumulator();
            CompletableFuture<R> result = new CompletableFuture<>();
            handle(item -> accumulator.accept(intermediateCollection, item),
                   result::completeExceptionally,
                   () -> result.complete(collector.finisher().apply(intermediateCollection)));
            return result;
        } else if (characteristics.contains(Collector.Characteristics.UNORDERED)) {
            UnorderedCollectorSupport<? super T, A, R> support = new UnorderedCollectorSupport<>(collector);
            handle(support::add, support::completeExceptionally, support::complete);
            return support.getResult();
        } else {
            A intermediateCollection = collector.supplier().get();
            BiConsumer<A, ? super T> accumulator = collector.accumulator();
            CompletableFuture<R> result = new CompletableFuture<>();
            handle(item -> {
                       synchronized (intermediateCollection) {
                           accumulator.accept(intermediateCollection, item);
                       }
                   },
                   result::completeExceptionally,
                   () -> result.complete(collector.finisher().apply(intermediateCollection)));
            return result;
        }
    }

    /**
     * Transforms this {@code Valve} into {@link io.helidon.common.reactive.Flow.Publisher} representation. Resulting {@code
     * Publisher}
     * accepts only single {@link io.helidon.common.reactive.Flow.Subscriber}.
     *
     * @return a {@code Publisher} representation
     */
    default Flow.Publisher<T> toPublisher() {
        return new ValvePublisher<>(this);
    }

    /**
     * Returns new {@code Valve} which defer all handlers to provided {@link ExecutorService}. Each data chunk will
     * be still called sequentially.
     *
     * @param executorService an executor service to use
     * @return a new {@code Valve} instance
     * @throws NullPointerException if {@code executorService} parameter is {@code null}.
     */
    default Valve<T> executeOn(ExecutorService executorService) {
        Objects.requireNonNull(executorService, "Parameter 'executorService' is null!");
        return new DetachedValve<>(this, executorService);
    }

    /**
     * Transforms this {@code Valve} into {@link ValveIterator} representation. It transforms reactive approach to the blocking
     * polling {@link java.util.Iterator} API.
     *
     * @return an iterator instance
     * @throws IllegalStateException if this instance has already registered handlers
     */
    default ValveIterator<T> toIterator() {
        return new ValveIterator<>(this);
    }
}
