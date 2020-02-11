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

package io.helidon.config.spi;

import java.util.function.Supplier;

/**
 * Mechanism for controlling retry of attempts to load data by a {@link io.helidon.config.spi.ConfigSource}.
 * <p>
 * When a {@link io.helidon.config.Config} attempts to load the underlying data
 * of a {@link io.helidon.config.spi.ConfigSource} it uses a {@code RetryPolicy} to govern if and how it
 * retries the load operation in case of errors.
 * <p>
 * The {@link #execute(java.util.function.Supplier) } method of each policy
 * implementation must perform at least one attempt to load the data, even if it
 * chooses not to retry in case of errors.
 */
public interface RetryPolicy {
    /**
     * Invokes the provided {@code Supplier} to read the source data and returns
     * that data.
     * <p>
     * The implementation of this method incorporates the retry logic.
     *
     * @param call supplier of {@code T}
     * @param <T> result type
     * @return loaded data returned by the provided {@code Supplier}, null is a permitted response
     */
    <T> T execute(Supplier<T> call);

    /**
     * Invokes the provided {@code Runnable}.
     * No need to implement this method, default implementation uses {@link #execute(java.util.function.Supplier)}
     *
     * @param runnable to run
     */
    default void execute(Runnable runnable) {
        execute(() -> {
            runnable.run();
            return null;
        });
    }
}
