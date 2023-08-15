/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

/**
 * Reports absolute time (and, therefore, is also useful in computing elapsed times).
 */
public interface Clock extends Wrapped {

    /**
     * Returns the system clock for the Helidon metrics implementation.
     * <p>
     *     The system clock methods are functionally equivalent to {@link System#currentTimeMillis()}
     *     and {@link System#nanoTime()}.
     * </p>
     *
     * @return the system clock
     */
    static Clock system() {
        return MetricsFactoryManager.getInstance().clockSystem();
    }

    /**
     * Returns the current wall time in milliseconds since the epoch.
     *
     * <p>
     *     Typically equivalent to {@link System#currentTimeMillis()}. Should not be used to determine durations.
     *     For that use {@link #monotonicTime()} instead.
     * </p>
     *
     * @return wall time in milliseconds
     */
    long wallTime();

    /**
     * Returns the current time in nanoseconds from a monotonic clock source.
     *
     * <p>
     *     The value is only meaningful when compared with another value returned from this method to determine the elapsed time
     *     for an operation. The difference between two samples will have a unit of nanoseconds. The returned value is
     *     typically equivalent to {@link System#nanoTime()}.
     * </p>
     *
     * @return monotonic time in nanoseconds
     */
    long monotonicTime();

    /**
     * Unwraps the clock to the specified type (typically not needed for custom clocks).
     *
     * @param c {@link Class} to which to cast this object
     * @return unwrapped clock
     * @param <R> the type of the unwrapped clock
     */
    @Override
    default <R> R unwrap(Class<? extends R> c) {
        throw new UnsupportedOperationException();
    }
}
