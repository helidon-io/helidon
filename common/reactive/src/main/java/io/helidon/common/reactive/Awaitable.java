/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Makes intentional blocking when waiting for {@link CompletableFuture} more convenient with {@link Awaitable#await()}
 * and {@link Awaitable#await(long, java.util.concurrent.TimeUnit)} methods.
 *
 * @param <T> payload type
 */
public interface Awaitable<T> {

    /**
     * Returns a {@link java.util.concurrent.CompletableFuture} maintaining the same
     * completion properties as this stage. If this stage is already a
     * CompletableFuture, this method may return this stage itself.
     * Otherwise, invocation of this method may be equivalent in
     * effect to {@code thenApply(x -> x)}, but returning an instance
     * of type {@code CompletableFuture}.
     *
     * @return the CompletableFuture
     */
    CompletableFuture<T> toCompletableFuture();

    /**
     * Block until future is completed, throws only unchecked exceptions.
     *
     * @return T payload type
     * @throws java.util.concurrent.CancellationException if the computation was cancelled
     * @throws java.util.concurrent.CompletionException   if this future completed
     */
    default T await() {
        return this.toCompletableFuture().join();
    }

    /**
     * Block until future is completed, throws only unchecked exceptions.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the result value
     * @throws java.util.concurrent.CancellationException if this future was cancelled
     * @throws java.util.concurrent.CompletionException   if this future completed exceptionally,
     *                                                    was interrupted while waiting or the wait timed out
     */
    default T await(long timeout, TimeUnit unit) {
        try {
            return this.toCompletableFuture().get(timeout, unit);
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        } catch (InterruptedException | TimeoutException e) {
            throw new CompletionException(e);
        }
    }

    /**
     * Block until future is completed, throws only unchecked exceptions.
     *
     * @param duration the maximum time to wait
     * @return the result value
     * @throws java.util.concurrent.CancellationException if this future was cancelled
     * @throws java.util.concurrent.CompletionException   if this future completed exceptionally,
     *                                                    was interrupted while waiting or the wait timed out
     */
    default T await(Duration duration) {
        try {
            return this.toCompletableFuture().get(duration.toNanos(), TimeUnit.NANOSECONDS);
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        } catch (InterruptedException | TimeoutException e) {
            throw new CompletionException(e);
        }
    }
}
