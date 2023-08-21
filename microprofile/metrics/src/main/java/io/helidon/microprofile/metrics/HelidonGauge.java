/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Gauge implementation.
 */
abstract class HelidonGauge<N extends Number> extends MetricImpl implements Gauge<N> {
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

    private final io.micrometer.core.instrument.Gauge delegate;

    static <T, N extends Number> FunctionBased<N, T> create(String scope,
                                                            Metadata metadata,
                                                            T target,
                                                            Function<T, N> function,
                                                            Tag... tags) {
        return create(Metrics.globalRegistry,
                      scope,
                      metadata,
                      target,
                      function,
                      tags);
    }

    static <T, N extends Number> FunctionBased<N, T> create(MeterRegistry meterRegistry,
                                                            String scope,
                                                            Metadata metadata,
                                                            T target,
                                                            Function<T, N> function,
                                                            Tag... tags) {
        return new FunctionBased<>(scope,
                                   metadata,
                                   target,
                                   function,
                                   io.micrometer.core.instrument.Gauge
                                           .builder(metadata.getName(), target, t -> function.apply(t).doubleValue())
                                           .description(metadata.getDescription())
                                           .tags(allTags(scope, tags))
                                           .baseUnit(sanitizeUnit(metadata.getUnit()))
                                           .strongReference(true)
                                           .register(meterRegistry));
    }

    static <N extends Number> SupplierBased<N> create(String scope,
                                                      Metadata metadata,
                                                      Supplier<N> supplier,
                                                      Tag... tags) {
        return create(Metrics.globalRegistry,
                      scope,
                      metadata,
                      supplier,
                      tags);

    }
    static <N extends Number> SupplierBased<N> create(MeterRegistry meterRegistry,
                                                      String scope,
                                                      Metadata metadata,
                                                      Supplier<N> supplier,
                                                      Tag... tags) {
        return new SupplierBased<>(scope,
                                   metadata,
                                   supplier,
                                   io.micrometer.core.instrument.Gauge
                                           .builder(metadata.getName(), (Supplier<Number>) supplier)
                                           .baseUnit(metadata.getUnit())
                                           .description(metadata.getDescription())
                                           .strongReference(true)
                                           .tags(allTags(scope, tags))
                                           .register(meterRegistry));
    }

    static <T> DoubleFunctionBased<T> create(String scope,
                                             Metadata metadata,
                                             T target,
                                             ToDoubleFunction<T> fn,
                                             Tag... tags) {
        return create(Metrics.globalRegistry,
                      scope,
                      metadata,
                      target,
                      fn,
                      tags);
    }

    static <T> DoubleFunctionBased<T> create(MeterRegistry meterRegistry,
                                             String scope,
                                             Metadata metadata,
                                             T target,
                                             ToDoubleFunction<T> fn,
                                             Tag... tags) {
        return new DoubleFunctionBased<>(scope,
                                         metadata,
                                         target,
                                         fn,
                                         io.micrometer.core.instrument.Gauge
                                                 .builder(metadata.getName(), target, fn)
                                                 .description(metadata.getDescription())
                                                 .baseUnit(metadata.getUnit())
                                                 .tags(allTags(scope, tags))
                                                 .strongReference(true)
                                                 .register(meterRegistry));

    }

    protected HelidonGauge(String scope, Metadata metadata, io.micrometer.core.instrument.Gauge delegate) {
        super(scope, metadata);
        this.delegate = delegate;
    }

    @Override
    public io.micrometer.core.instrument.Gauge delegate() {
        return delegate;
    }

    static class FunctionBased<N extends Number, T> extends HelidonGauge<N> {

        private final T target;
        private final Function<T, N> function;

        private FunctionBased(String scope,
                              Metadata metadata,
                              T target,
                              Function<T, N> function,
                              io.micrometer.core.instrument.Gauge delegate) {
            super(scope, metadata, delegate);
            this.target = target;
            this.function = function;
        }

        @Override
        public N getValue() {
            return (N) function.apply(target);
        }
    }

    static class DoubleFunctionBased<T> extends HelidonGauge<Double> {

        private final T target;
        private final ToDoubleFunction<T> fn;

        protected DoubleFunctionBased(String scope,
                                      Metadata metadata,
                                      T target,
                                      ToDoubleFunction<T> fn,
                                      io.micrometer.core.instrument.Gauge delegate) {
            super(scope, metadata, delegate);
            this.target = target;
            this.fn = fn;
        }

        @Override
        public Double getValue() {
            return fn.applyAsDouble(target);
        }
    }

    static class SupplierBased<N extends Number> extends HelidonGauge<N> {

        private final Supplier<N> supplier;

        private SupplierBased(String scope,
                              Metadata metadata,
                              Supplier<N> supplier,
                              io.micrometer.core.instrument.Gauge delegate) {
            super(scope, metadata, delegate);
            this.supplier = supplier;
        }

        @Override
        public N getValue() {
            return supplier.get();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        HelidonGauge<?> that = (HelidonGauge<?>) o;
        return getValue().equals(that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getValue());
    }

    @Override
    protected String toStringDetails() {
        return ", value='" + getValue() + '\'';
    }
}
