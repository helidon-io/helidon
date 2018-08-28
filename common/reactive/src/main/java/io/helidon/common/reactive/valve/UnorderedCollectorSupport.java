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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BinaryOperator;
import java.util.stream.Collector;

/**
 * Use {@link java.util.stream.Collector} with {@code UNORDERED} characteristic to
 * collect data from {@link Valve}.
 * <p>
 * Implementation use {@link ThreadLocal} to decrease synchronization demand.
 */
class UnorderedCollectorSupport<T, A, R> {

    private static final int CONCURRENCY_LIMIT = 256;

    private final Collector<T, A, R> collector;
    private final CompletableFuture<R> resultFuture = new CompletableFuture<>();
    private final ThreadLocal<A> intermediate = new ThreadLocal<>();
    private final List<A> intermediates = new ArrayList<>(16);

    private A sharedIntermediate;

    /**
     * Creates new instance.
     *
     * @param collector a collector with {@code UNORDERED} characteristic to use
     * @throws NullPointerException if {@code collector} parameter is {@code null}
     * @throws IllegalArgumentException if {@code collector} doesn't have {@code UNORDERED} characteristic.
     */
    UnorderedCollectorSupport(Collector<T, A, R> collector) {
        Objects.requireNonNull(collector, "Parameter 'collector' is null");
        if (!collector.characteristics().contains(Collector.Characteristics.UNORDERED)) {
            throw new IllegalArgumentException("Collector parameter must have 'UNORDERED' characteristic.");
        }
        this.collector = collector;
    }

    /**
     * Returns a result which will be completed when {@link #complete()} or {@link #completeExceptionally(Throwable)}
     * is called.
     *
     * @return a completion stage of the result
     */
    CompletionStage<R> getResult() {
        return resultFuture;
    }

    /**
     * Add an item using collector.
     *
     * @param item a item to add
     */
    void add(T item) {
        A a = intermediate.get();
        if (a == null) {
            synchronized (intermediates) {
                if (intermediates.size() < CONCURRENCY_LIMIT) {
                    // Add an intermediate
                    a = collector.supplier().get();
                    intermediate.set(a);
                    intermediates.add(a);
                } else {
                    if (sharedIntermediate == null) {
                        sharedIntermediate = collector.supplier().get();
                        intermediates.add(sharedIntermediate);
                    }
                    collector.accumulator().accept(sharedIntermediate, item);
                    return;
                }
            }
        }
        // Add item
        collector.accumulator().accept(a, item);
    }

    @SuppressWarnings("unchecked")
    void complete() {
        synchronized (intermediates) {
            BinaryOperator<A> combiner = collector.combiner();
            A a = intermediates.isEmpty() ? collector.supplier().get() : intermediates.get(0);
            for (int i = 1; i < intermediates.size(); i++) {
                a = combiner.apply(a, intermediates.get(i));
            }
            if (collector.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH)) {
                resultFuture.complete((R) a);
            } else {
                resultFuture.complete(collector.finisher().apply(a));
            }
        }
    }

    void completeExceptionally(Throwable t) {
        resultFuture.completeExceptionally(t);
    }
}
