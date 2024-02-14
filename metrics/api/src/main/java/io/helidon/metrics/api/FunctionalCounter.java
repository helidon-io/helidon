/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.function.Function;

/**
 * A read-only counter which wraps some other object that provides the counter value via a function.
 */
public interface FunctionalCounter extends Meter {

    /**
     * Returns a builder for registering or locating a functional counter.
     *
     * @param name        functional counter name
     * @param stateObject object which provides the counter value on demand
     * @param fn          function which, when applied to the state object, returns the counter value
     * @param <T>         type of the state object
     * @return new builder
     */
    static <T> Builder<?> builder(String name, T stateObject, Function<T, Long> fn) {
        return MetricsFactory.getInstance().functionalCounterBuilder(name, stateObject, fn);
    }

    /**
     * Returns the counter value.
     *
     * @return counter value
     */
    long count();

    /**
     * Builder for a {@link io.helidon.metrics.api.FunctionalCounter}.
     *
     * @see MeterRegistry#getOrCreate(Meter.Builder)
     *
     * @param <T> type of the state object
     */
    interface Builder<T> extends Meter.Builder<Builder<T>, FunctionalCounter> {

        /**
         * Returns the state object which would supply the counter value.
         *
         * @return state object
         */
        T stateObject();

        /**
         * Returns the function which, when applied to the state object, returns the counter value.
         *
         * @return function returning the counter value
         */
        Function<T, Long> fn();
    }
}
