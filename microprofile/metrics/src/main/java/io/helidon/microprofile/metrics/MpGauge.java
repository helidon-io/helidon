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
package io.helidon.microprofile.metrics;

import java.util.function.Function;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

abstract class MpGauge<N extends Number> extends MpMetric<Gauge> implements org.eclipse.microprofile.metrics.Gauge<N> {

    /*
     * The MicroProfile metrics API parameterizes its gauge type as Gauge<N extends Number> which is the type of
     * value the gauge reports via its getValue() method. To register a gauge, the developer passes us a function-plus-target or
     * a supplier with the return value from the function or supplier similarly parameterized with the subtype of Number.
     *
     * On the other hand, each Micrometer gauge is not parameterized and reports a double value.
     *
     * As a result, we do not have what we need to (easily) instantiate the correct subtype of Number, set its value based on
     * the Micrometer delegate's value() result, and return the correctly-typed and -assigned value from our getValue() method.
     *
     * To work around this, we keep track ourselves of the function and target or supplier which report the gauge value.
     * Then our getValue() implementation simply invokes the function on the target or the supplier rather than delegating to
     * the Micrometer gauge value() method (which would do exactly the same thing anyway).
     *
     * This way the typing works out (with the expected unchecked cast).
     */
    private MpGauge(Gauge delegate, MeterRegistry meterRegistry) {
        super(delegate, meterRegistry);
    }

    static <T, N extends Number> FunctionBased<N, T> create(T target,
                                                            Function<T, N> function,
                                                            Gauge delegate,
                                                            MeterRegistry meterRegistry) {
        return new FunctionBased<>(target, function, delegate, meterRegistry);
    }

    static <N extends Number> SupplierBased<N> create(Supplier<N> supplier,
                                                            Gauge delegate,
                                                            MeterRegistry meterRegistry) {
        return new SupplierBased<>(supplier, delegate, meterRegistry);
    }

    static class FunctionBased<N extends Number, T> extends MpGauge<N> {

        private final T target;
        private final Function<T, N> function;



        private FunctionBased(T target, Function<T, N> function, Gauge delegate, MeterRegistry meterRegistry) {
            super(delegate, meterRegistry);
            this.target = target;
            this.function = function;
        }

        @Override
        public N getValue() {
            return (N) function.apply(target);
        }
    }

    static class SupplierBased<N extends Number> extends MpGauge<N> {

        private final Supplier<N> supplier;

        private SupplierBased(Supplier<N> supplier, Gauge delegate, MeterRegistry meterRegistry) {
            super(delegate, meterRegistry);
            this.supplier = supplier;
        }

        @Override
        public N getValue() {
            return supplier.get();
        }
    }
}
