/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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
@FunctionalInterface
public interface RetryPolicy extends Supplier<RetryPolicy> {
    /**
     * Invokes the provided {@code Supplier} to read the source data and returns
     * that data.
     * <p>
     * The implementation of this method incorporates the retry logic.
     *
     * @param call supplier of {@code T}
     * @param <T> result type
     * @return loaded data returned by the provided {@code Supplier}
     */
    <T> T execute(Supplier<T> call);

    /**
     * Cancels the current use of the retry policy.
     * <p>
     * Implementations should correctly handle three cases:
     * <ol>
     * <li>{@code cancel} invoked when no invocation of {@code execute} is in
     * progress,</li>
     * <li>{@code cancel} invoked when an invocation of {@code execute} is
     * active but no attempted load is actually in progress (e.g., a prior
     * attempt failed and the retry policy is waiting for some time to pass
     * before trying again), and</li>
     * <li>{@code cancel} invoked while a load attempt is in progress.</li>
     * </ol>
     *
     * @param mayInterruptIfRunning whether an in-progress load attempt should
     * be interrupted
     * @return {@code false} if the task could not be canceled, typically
     * because it has already completed; {@code true} otherwise
     */
    default boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    default RetryPolicy get() {
        return this;
    }
}
