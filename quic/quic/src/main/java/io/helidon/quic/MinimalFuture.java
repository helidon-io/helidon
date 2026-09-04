/*
 * Copyright (c) 2016, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.Api;

import static java.util.Objects.requireNonNull;

/**
 * {@link CompletableFuture} variant that disallows obtrusion.
 *
 * @param <T> future result type
 */
@Api.Internal
public final class MinimalFuture<T> extends CompletableFuture<T> {

    private static final AtomicLong TOKENS = new AtomicLong();
    private final long id;
    private MinimalFuture() {
        super();
        this.id = TOKENS.incrementAndGet();
    }

    /**
     * Create a completed minimal future.
     *
     * @param value completion value
     * @param <U>   future result type
     * @return completed future
     */
    public static <U> MinimalFuture<U> completedMinimalFuture(U value) {
        MinimalFuture<U> f = new MinimalFuture<>();
        f.complete(value);
        return f;
    }

    /**
     * Create a failed minimal future.
     *
     * @param ex  completion failure
     * @param <U> future result type
     * @return failed future
     */
    public static <U> CompletableFuture<U> failedMinimalFuture(Throwable ex) {
        requireNonNull(ex);
        MinimalFuture<U> f = new MinimalFuture<>();
        f.completeExceptionally(ex);
        return f;
    }

    /**
     * Create a minimal future.
     *
     * @param <U> future result type
     * @return a new minimal future
     */
    public static <U> MinimalFuture<U> create() {
        return new MinimalFuture<>();
    }

    /**
     * Adapt an existing completion stage to a {@code MinimalFuture}.
     *
     * @param stage stage to adapt
     * @param <U>   future result type
     * @return adapted minimal future
     */
    public static <U> MinimalFuture<U> of(CompletionStage<U> stage) {
        MinimalFuture<U> cf = new MinimalFuture<>();
        stage.whenComplete((r, t) -> complete(cf, r, t));
        return cf;
    }

    @Override
    public <U> MinimalFuture<U> newIncompleteFuture() {
        return new MinimalFuture<>();
    }

    @Override
    public void obtrudeValue(T value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void obtrudeException(Throwable ex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return super.toString() + " (id=" + id + ")";
    }

    private static <U> void complete(CompletableFuture<U> cf, U result, Throwable t) {
        if (t == null) {
            cf.complete(result);
        } else {
            cf.completeExceptionally(t);
        }
    }
}
