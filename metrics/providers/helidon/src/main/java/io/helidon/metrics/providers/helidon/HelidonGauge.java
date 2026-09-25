/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.helidon;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;

abstract class HelidonGauge<N extends Number> extends HelidonMeter implements Gauge<N> {
    private HelidonGauge(Meter.Id id, Builder<N> builder) {
        super(id, Type.GAUGE, builder);
    }

    static <N extends Number> Builder<N> builder(String name, Supplier<N> supplier) {
        return new SupplierBased.Builder<>(name, supplier);
    }

    static <T> Builder<Double> builder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return new FunctionBased.Builder<>(name, stateObject, fn);
    }

    static HelidonGauge<?> create(Meter.Id id, Builder<?> builder) {
        return builder.build(id);
    }

    @Override
    public String toString() {
        return stringPrefix() + "value=" + value() + "]";
    }

    abstract static class Builder<N extends Number> extends HelidonMeter.AbstractBuilder<Gauge.Builder<N>, Gauge<N>>
            implements Gauge.Builder<N> {

        private Builder(String name) {
            super(name);
        }

        abstract HelidonGauge<N> build(Meter.Id id);

        @Override
        Class<? extends Meter> meterType() {
            return Gauge.class;
        }
    }

    private static final class SupplierBased<N extends Number> extends HelidonGauge<N> {
        private final Supplier<N> supplier;

        private SupplierBased(Meter.Id id, Builder<N> builder) {
            super(id, builder);
            this.supplier = builder.supplier;
        }

        @Override
        public N value() {
            return Objects.requireNonNull(supplier.get());
        }

        private static final class Builder<N extends Number> extends HelidonGauge.Builder<N> {
            private final Supplier<N> supplier;

            private Builder(String name, Supplier<N> supplier) {
                super(name);
                this.supplier = Objects.requireNonNull(supplier);
            }

            @Override
            public Supplier<N> supplier() {
                return supplier;
            }

            @Override
            HelidonGauge<N> build(Meter.Id id) {
                return new SupplierBased<>(id, this);
            }
        }
    }

    private static final class FunctionBased<T> extends HelidonGauge<Double> {
        private final T stateObject;
        private final ToDoubleFunction<T> fn;

        private FunctionBased(Meter.Id id, Builder<T> builder) {
            super(id, builder);
            this.stateObject = builder.stateObject;
            this.fn = builder.fn;
        }

        @Override
        public Double value() {
            return fn.applyAsDouble(stateObject);
        }

        private static final class Builder<T> extends HelidonGauge.Builder<Double> {
            private final T stateObject;
            private final ToDoubleFunction<T> fn;

            private Builder(String name, T stateObject, ToDoubleFunction<T> fn) {
                super(name);
                this.stateObject = Objects.requireNonNull(stateObject);
                this.fn = Objects.requireNonNull(fn);
            }

            @Override
            public Supplier<Double> supplier() {
                return () -> fn.applyAsDouble(stateObject);
            }

            @Override
            HelidonGauge<Double> build(Meter.Id id) {
                return new FunctionBased<>(id, this);
            }
        }
    }
}
