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
package io.helidon.metrics.providers.micrometer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.helidon.common.LazyValue;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;


/**
 * Adapter of a {@linkplain io.micrometer.core.instrument.Gauge Micrometer gauge} to the Helidon metrics API
 * {@link io.helidon.metrics.api.Gauge}.
 * <p>
 * The Helidon metrics API parameterizes its gauge type as {@code Gauge<N extends Number>} which is the type of
 * value the gauge reports via its {@linkplain io.helidon.metrics.api.Gauge#value()} method. To register a gauge, the developer
 * can pass us a supplier, the return value from which is parameterized with the subtype {@code N} of {@link Number}.
 * </p>
 * <p>
 * On the other hand, Micrometer gauges are not parameterized; each reports a double value.
 * Even though Micrometer permits callers to create a Micrometer gauge using a {@code Supplier<Number>},
 * the Micrometer gauge {@linkplain io.micrometer.core.instrument.Gauge#value() value method} returns a {@code double}.
 * </p>
 * <p>
 * As a result, given our parameterized API, we cannot effectively wrap Micrometer gauges. We do not have what we need to (easily)
 * instantiate the correct subtype of {@code Number}, set its value based on
 * the Micrometer delegate's {@code getValue()} {@code double} result, and return the correctly-typed and -assigned
 * value from our {@code value()} method.
 * </p>
 * <p>
 * Instead, we keep track ourselves of the function and target or supplier which report the gauge value.
 * Then our {@code value()} implementation simply invokes the function on the target or invokes the supplier rather
 * than delegating to the Micrometer gauge value() method (which would do exactly the same thing anyway).
 * </p>
 *
 * @param <N> subtype of {@link Number} this gauge instance reports.
 */
abstract class MGauge<N extends Number> extends MMeter<io.micrometer.core.instrument.Gauge> implements io.helidon.metrics.api.Gauge<N> {

    /*
     * The Helidon metrics API parameterizes its gauge type as Gauge<N extends Number> which is the type of
     * value the gauge reports via its getValue() method. To register a gauge, the developer passes us a function-plus-target or
     * a supplier with the return value from the function or supplier similarly parameterized with the subtype of Number.
     *
     * On the other hand, Micrometer gauges are not parameterized; each reports a double value.
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

    private <N extends Number> MGauge(Meter.Id id, io.micrometer.core.instrument.Gauge delegate, Builder<?, N> builder) {
        super(id, delegate, builder);
    }

    private MGauge(Meter.Id id, io.micrometer.core.instrument.Gauge delegate, Optional<String> scope) {
        super(id, delegate, scope);
    }

    /**
     * Creates a new builder for a wrapper around a Micrometer gauge that will be registered later, typically if the
     * developer is creating a gauge using the Helidon API.
     *
     * @param name name of the new gauge
     * @return new builder for a wrapper gauge
     */
    static <T> Builder<?, Double> builder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return new FunctionBased.Builder<>(name, stateObject, fn);
    }

    /**
     * Creates a new builder for a wrapper around a Micrometer gauge based on a {@link Supplier} of a
     * {@link java.lang.Number}.
     *
     * @param name     gauge name
     * @param supplier supplier operation to provide a {@code Number}
     * @param <N>      subtype of {@code Number} the gauge provides
     * @return new builder for a wrapper gauge
     */
    static <N extends Number> SupplierBased.Builder<N> builder(String name, Supplier<N> supplier) {
        return new SupplierBased.Builder<>(name, supplier);
    }

    static <N extends Number> SupplierBased.Builder<N>  builderFrom(Gauge.Builder<N> gBuilder) {
        return builder(gBuilder.name(), gBuilder.supplier())
                .from(gBuilder);
    }

    /**
     * Creates a new wrapper gauge around an existing Micrometer gauge, typically if the developer has registered a
     * gauge directly using the Micrometer API rather than through the Helidon adapter but we need to expose the gauge
     * via a wrapper.
     *
     * @param gauge the Micrometer gauge
     * @param scope scope to apply
     * @return new wrapper around the gauge
     */
    static MGauge<Double> create(Meter.Id id, io.micrometer.core.instrument.Gauge gauge, Optional<String> scope) {
        return new MGauge<>(id, gauge, scope) {
            @Override
            public Double value() {
                return gauge.value();
            }
        };
    }

    @Override
    public String toString() {
        return stringJoiner()
                .add("value=" + delegate().value())
                .toString();
    }

    abstract static class Builder<HB extends Builder<HB, N>, N extends Number>
            extends MMeter.Builder<io.micrometer.core.instrument.Gauge.Builder<?>, io.micrometer.core.instrument.Gauge, HB, MGauge<N>> implements io.helidon.metrics.api.Gauge.Builder<N> {

        protected Builder(String name, io.micrometer.core.instrument.Gauge.Builder<?> delegate) {
            super(name, delegate);
        }
    }

    static class SupplierBased<N extends Number> extends MGauge<N> {

        private final Supplier<N> supplier;

        private SupplierBased(Meter.Id id, io.micrometer.core.instrument.Gauge gauge, Builder<N> builder) {
            super(id, gauge, builder);
            this.supplier = builder.supplier;
        }

        /**
         * Returns the gauge value.
         *
         * <p>
         * Note that we do not invoke the delegate. Delegate gauges from Micrometer only return
         * double values, and supplier-based gauges can report any subtype of Number. So we just
         * invoke the supplier ourselves rather than invoke Micrometer's delegate. We already know
         * the correct subtype to return and that's baked into the supplier itself thanks to the
         * parameterized type.
         * </p>
         *
         * @return gauge value
         */
        @Override
        public N value() {
            return supplier.get();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SupplierBased<?> that)) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            return Objects.equals(supplier, that.supplier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), supplier);
        }

        static class Builder<N extends Number> extends MGauge.Builder<Builder<N>, N>
                implements io.helidon.metrics.api.Gauge.Builder<N> {

            private final Supplier<N> supplier;

            private Builder(String name, Supplier<N> supplier) {
                super(name, io.micrometer.core.instrument.Gauge.builder(name, (Supplier<Number>) supplier));
                this.supplier = supplier;
            }

            @Override
            protected Builder<N> delegateTag(String key, String value) {
                delegate().tag(key, value);
                return identity();
            }

            @Override
            protected Builder<N> delegateTags(Iterable<io.micrometer.core.instrument.Tag> tags) {
                delegate().tags(tags);
                return identity();
            }

            @Override
            protected Builder<N> delegateDescription(String description) {
                delegate().description(description);
                return identity();
            }

            @Override
            protected Builder<N> delegateBaseUnit(String baseUnit) {
                delegate().baseUnit(baseUnit);
                return identity();
            }

            @Override
            protected MGauge<N> build(Meter.Id id, io.micrometer.core.instrument.Gauge gauge) {
                return new SupplierBased<>(id, gauge, this);
            }

            @Override
            public Supplier<N> supplier() {
                return supplier;
            }
        }
    }

    static class FunctionBased<T> extends MGauge<Double> {

        private final T stateObject;
        private final ToDoubleFunction<T> fn;

        private FunctionBased(Meter.Id id, io.micrometer.core.instrument.Gauge gauge, Builder<T> builder) {
            super(id, gauge, builder);
            stateObject = builder.stateObject;
            fn = builder.fn;
        }

        @Override
        public Double value() {
            return fn.applyAsDouble(stateObject);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FunctionBased<?> that)) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            return Objects.equals(stateObject, that.stateObject) && Objects.equals(fn, that.fn);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), stateObject, fn);
        }

        static class Builder<T> extends MGauge.Builder<Builder<T>, Double>
                implements io.helidon.metrics.api.Gauge.Builder<Double> {

            private final T stateObject;
            private final ToDoubleFunction<T> fn;

            private Builder(String name, T stateObject, ToDoubleFunction<T> fn) {
                super(name, io.micrometer.core.instrument.Gauge.builder(name, stateObject, fn));
                this.stateObject = stateObject;
                this.fn = fn;
                delegate().strongReference(true);
            }

            @Override
            protected Builder<T> delegateTags(Iterable<io.micrometer.core.instrument.Tag> tags) {
                delegate().tags(tags);
                return identity();
            }

            @Override
            protected Builder<T> delegateTag(String key, String value) {
                delegate().tag(key, value);
                return identity();
            }

            @Override
            protected Builder<T> delegateDescription(String description) {
                delegate().description(description);
                return identity();
            }

            @Override
            protected Builder<T> delegateBaseUnit(String baseUnit) {
                delegate().baseUnit(baseUnit);
                return identity();
            }

            @Override
            protected MGauge<Double> build(Meter.Id id, io.micrometer.core.instrument.Gauge gauge) {
                return new FunctionBased<>(id, gauge, this);
            }

            @Override
            public Supplier<Double> supplier() {
                return () -> fn.applyAsDouble(stateObject);
            }
        }
    }
}
