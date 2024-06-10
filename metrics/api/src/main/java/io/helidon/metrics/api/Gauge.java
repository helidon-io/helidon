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

import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Measures a value that can increase or decrease and is updated by external logic, not by explicit invocations
 * of methods on this type.
 *
 * @param <N> subtype of {@link Number} which a specific gauge reports
 */
public interface Gauge<N extends Number> extends Meter {

    /**
     * Creates a builder for creating a new gauge based on applying a function to a state object.
     *
     * @param name        gauge name
     * @param stateObject state object which maintains the gauge value
     * @param fn          function which, when applied to the state object, returns the gauge value
     * @param <T>         type of the state object
     * @return new builder
     */
    static <T> Builder<Double> builder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return MetricsFactory.getInstance().gaugeBuilder(name, stateObject, fn);
    }

    /**
     * Creates a builder for a supplier-based gauge.
     *
     * @param name           gauge name
     * @param numberSupplier {@link java.util.function.Supplier} for an instance of a type which extends {@link Number}
     * @param <N>            subtype of {@code Number} which the supplier provides
     * @return new builder
     */
    static <N extends Number> Builder<N> builder(String name, Supplier<N> numberSupplier) {
        return MetricsFactory.getInstance().gaugeBuilder(name, numberSupplier);
    }

    /**
     * Returns the value of the gauge.
     * <p>
     * Invoking this method triggers the sampling of the value or invocation of the function provided when the gauge was
     * registered.
     * </p>
     *
     * @return current value of the gauge
     */
    N value();

    /**
     * Builder for a new gauge.
     *
     * @see MeterRegistry#getOrCreate(Meter.Builder)
     *
     * @param <N> specific subtype of {@code Number} for the gauge this builder will produce
     */
    interface Builder<N extends Number> extends Meter.Builder<Builder<N>, Gauge<N>> {

        /**
         * Returns a {@link java.util.function.Supplier} for the values the gauge will produce.
         *
         * @return supplier for the values
         */
        Supplier<N> supplier();
    }
}
