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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CompletionStage with await functions, to achieve intentional blocking without checked exceptions.
 */
public interface MultiCompletionStage extends CompletionStage<Void> {

    /**
     * Create new MultiCompletableFuture.
     *
     * @return MultiCompletableFuture
     */
    static MultiCompletableFuture createFuture() {
        return new MultiCompletableFuture();
    }

    /**
     * Block until stage is completed, throws only unchecked exceptions.
     *
     * @throws java.util.concurrent.CancellationException if the computation was cancelled
     * @throws CompletionException                        if this future completed
     */
    default void await() {
        this.toCompletableFuture().join();
    }

    /**
     * Block until stage is completed, throws only unchecked exceptions.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @throws java.util.concurrent.CancellationException if this future was cancelled
     * @throws CompletionException                        if this future completed exceptionally,
     *                                                    was interrupted while waiting or the wait timed out
     */
    default void await(long timeout, TimeUnit unit) {
        try {
            this.toCompletableFuture().get(timeout, unit);
        } catch (InterruptedException | TimeoutException e) {
            throw new CompletionException(e);
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
    }

    /**
     * Cancel upstream.
     */
    void cancel();

    final class MultiCompletableFuture extends CompletableFuture<Void> implements MultiCompletionStage {

        private Runnable cancelCallback;

        void setCancelCallback(Runnable cancelCallback) {
            this.cancelCallback = cancelCallback;
        }

        @Override
        public void cancel() {
            cancelCallback.run();
        }
    }
}
