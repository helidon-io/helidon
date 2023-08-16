/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Helper to handle async suppliers that return {@code CompletionStage}.
 */
public class SupplierHelper {

    private SupplierHelper() {
    }

    /**
     * Maps a supplier returning a {@code CompletionStage<T>} to a supplier returning {@code T}
     * by waiting on the stage to produce a value.
     *
     * @param supplier the async supplier
     * @param timeout time to wait
     * @param unit unit of time
     * @param <T> type produced by supplier
     * @return the supplier
     */
    public static <T> Supplier<T> toSyncSupplier(Supplier<? extends CompletionStage<T>> supplier,
                                                 long timeout, TimeUnit unit) {
        return () -> {
            try {
                CompletionStage<T> result = supplier.get();
                return result.toCompletableFuture().get(timeout, unit);
            } catch (Throwable t) {
                Throwable throwable = unwrapThrowable(t);
                throw toRuntimeException(throwable);
            }
        };
    }

    /**
     * Wraps a {@code Throwable} in a {@code RuntimeException} of type {@code SupplierException}
     * if necessary. Will never nest {@code SupplierException}s.
     *
     * @param t the throwable
     * @return the exception
     */
    public static RuntimeException toRuntimeException(Throwable t) {
        return t instanceof RuntimeException rt ? rt : new SupplierException(t);
    }

    /**
     * Gets the underlying cause of a {@code SupplierException} or of a {@code ExecutionException}.
     *
     * @param t the throwable
     * @return the cause or the same throwable
     */
    public static Throwable unwrapThrowable(Throwable t) {
        if (t instanceof SupplierException || t instanceof ExecutionException) {
            return t.getCause();
        }
        return t;
    }
}
