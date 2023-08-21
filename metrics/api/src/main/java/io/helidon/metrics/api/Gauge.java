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

import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Measures a value that can increase or decrease and is updated by external logic, not by explicit invocations
 * of methods on this type.
 */
public interface Gauge extends Meter {

    /**
     * Creates a builder for creating a new gauge.
     *
     * @param name gauge name
     * @param stateObject state object which maintains the gauge value
     * @param fn function which, when applied to the state object, returns the gauge value
     * @return new builder
     * @param <T> type of the state object
     */
    static <T> Builder<T> builder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return MetricsFactory.getInstance().gaugeBuilder(name, stateObject, fn);
    }

    /**
     * Creates a builder for a new gauge based on an instance of a subtype of {@link Number}.
     *
     * @param name gauge name
     * @param number {@code Number} which provides the gauge value
     * @return new builder
     * @param <N> subtype of {@code Number} which the gauge wraps
     */
    static <N extends Number> Builder<?> builder(String name, N number) {
        return MetricsFactory.getInstance().gaugeBuilder(name, number);
    }

    /**
     * Creates a builder for a supplier-based gauge.
     *
     * @param name gauge name
     * @param numberSupplier {@link java.util.function.Supplier} for an instance of a type which extends {@link Number}
     * @return new builder
     * @param <N> subtype of {@code Number} which the supplier provides
     */
    static <N extends Number> Builder<?> builder(String name, Supplier<N> numberSupplier) {
        return MetricsFactory.getInstance().gaugeBuilder(name, numberSupplier);
    }

    /**
     * Returns the value of the gauge.
     * <p>
     *     Invoking this method triggers the sampling of the value or invocation of the function provided when the gauge was
     *     registered.
     * </p>
     *
     * @return current value of the gauge
     */
    double value();

    /**
     * Builder for a new gauge.
     *
     * @param <T> type of the state object which exposes the gauge value.
     */
    interface Builder<T> extends Meter.Builder<Builder<T>, Gauge> {

        /**
         * Returns the state object to be used in the resulting gauge.
         *
         * @return the state object
         */
        T stateObject();

        /**
         * Returns the function which, when applied to the state object, returns the gauge value.
         *
         * @return function which yields the gauge value
         */
        ToDoubleFunction<T> fn();
    }
}
