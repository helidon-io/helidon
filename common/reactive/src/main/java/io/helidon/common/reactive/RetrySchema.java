/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

/**
 * Defines delay for next read/poll operation in a polling {@link java.util.concurrent.Flow.Publisher publisher}.
 * <p>
 * Schema defines a delay before next poll if the last one did not get new data.
 * <p>
 * It is possible to use included static factory methods for some standard schemas.
 */
@FunctionalInterface
public interface RetrySchema {

    /**
     * Returns a delay before next read if the last one poll did not get new data.
     *
     * @param retryCount a count of already poll retries. It is {@code 0} when called first time and resets every time
     *                   when poll returns some data.
     * @param lastDelay  a last returned value. {@code 0} in first call. Resets when some data are get.
     * @return a delay in milliseconds before next poll attempt. If less then zero then {@code Publisher} completes
     *         with {@code onError} message.
     */
    long nextDelay(int retryCount, long lastDelay);

    /**
     * Creates the retry schema with a linear delay increment. It returns {@code firstDelay} for the first call and then
     * it increments it's value for every call by {@code delayIncrement} until reach a limit defined by {@code maxDelay}.
     *
     * @param firstDelay a delay to returns for the first call
     * @param delayIncrement an increment of the returned value.
     * @param maxDelay the maximal value to return
     * @return computed retry delay with a linear increment
     */
    static RetrySchema linear(long firstDelay, long delayIncrement, long maxDelay) {
        return (retryCount, lastDelay) -> {
            long result = firstDelay + (delayIncrement * retryCount);
            return result < maxDelay ? result : maxDelay;
        };
    }

    /**
     * Creates the retry schema with a constant result.
     *
     * @param delay a constant delay to return
     * @return a {@code delay} value
     */
    static RetrySchema constant(long delay) {
        return (retryCount, lastDelay) -> delay;
    }

    /**
     * Creates the retry schema as a bounded geometric series.
     *
     * @param firstDelay a delay to returns for the first call
     * @param ratio a geometric series ratio
     * @param maxDelay the maximal value to return
     * @return computed retry delay
     */
    static RetrySchema geometric(long firstDelay, double ratio, long maxDelay) {
        return (retryCount, lastDelay) -> {
            if (retryCount == 0) {
                return firstDelay < maxDelay ? firstDelay : maxDelay;
            }
            long result = Math.round(lastDelay * ratio);
            return result < maxDelay ? result : maxDelay;
        };
    }
}
