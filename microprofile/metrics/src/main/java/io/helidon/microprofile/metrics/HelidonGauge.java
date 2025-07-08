/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.MeterRegistry;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Gauge implementation.
 */
abstract class HelidonGauge<N extends Number> extends MetricImpl<io.helidon.metrics.api.Gauge<N>> implements Gauge<N> {

    private final io.helidon.metrics.api.Gauge<N> delegate;

    protected HelidonGauge(String scope, Metadata metadata, io.helidon.metrics.api.Gauge<N> delegate) {
        super(scope, metadata);
        this.delegate = delegate;
    }

    static <N extends Number> SupplierBased<N> create(MeterRegistry meterRegistry,
                                                      String scope,
                                                      Metadata metadata,
                                                      Supplier<N> supplier,
                                                      Tag... tags) {
        return new SupplierBased<>(scope,
                                   metadata,
                                   supplier,
                                   meterRegistry.getOrCreate(io.helidon.metrics.api.Gauge
                                                                     .builder(metadata.getName(), supplier)
                                                                     .scope(scope)
                                                                     .baseUnit(metadata.getUnit())
                                                                     .description(metadata.getDescription())
                                                                     .tags(allTags(scope, tags))));
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
                                         meterRegistry.getOrCreate(io.helidon.metrics.api.Gauge
                                                                           .builder(metadata.getName(),
                                                                                    target,
                                                                                    fn)
                                                                           .scope(scope)
                                                                           .description(metadata.getDescription())
                                                                           .baseUnit(metadata.getUnit())
                                                                           .tags(allTags(scope, tags))));

    }

    static <N extends Number> HelidonGauge<N> create(String scope,
                                                     Metadata metadata,
                                                     io.helidon.metrics.api.Gauge<N> delegate) {
        return new HelidonGauge.DelegateBased<>(scope,
                                                metadata,
                                                delegate);
    }

    static <N extends Number> HelidonGauge<N> create(io.helidon.metrics.api.Gauge<N> delegate) {
        return new HelidonGauge.DelegateBased<>(resolvedScope(delegate),
                                                Registry.metadata(delegate),
                                                delegate);
    }

    static HelidonGauge<Long> create(String scope,
                                     Metadata metadata,
                                     io.helidon.metrics.api.FunctionalCounter delegate) {
        return new HelidonGauge.FunctionalCounterBased(scope,
                                                       metadata,
                                                       delegate);
    }

    @Override
    public io.helidon.metrics.api.Gauge<N> delegate() {
        return delegate;
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

    @Override
    public Class<?> delegateType() {
        return io.helidon.metrics.api.Gauge.class;
    }

    static class DoubleFunctionBased<T> extends HelidonGauge<Double> {

        private final T target;
        private final ToDoubleFunction<T> fn;

        protected DoubleFunctionBased(String scope,
                                      Metadata metadata,
                                      T target,
                                      ToDoubleFunction<T> fn,
                                      io.helidon.metrics.api.Gauge<Double> delegate) {
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
                              io.helidon.metrics.api.Gauge<N> delegate) {
            super(scope, metadata, delegate);
            this.supplier = supplier;
        }

        @Override
        public N getValue() {
            return supplier.get();
        }
    }

    static class DelegateBased<N extends Number> extends HelidonGauge<N> {

        private DelegateBased(String scope, Metadata metadata, io.helidon.metrics.api.Gauge<N> delegate) {
            super(scope, metadata, delegate);

        }

        @Override
        public N getValue() {
            return delegate().value();
        }
    }

    static class FunctionalCounterBased extends HelidonGauge<Long> {

        private final FunctionalCounter functionalCounter;

        private FunctionalCounterBased(String scope, Metadata metadata, io.helidon.metrics.api.FunctionalCounter delegate) {
            super(scope, metadata, null);
            functionalCounter = delegate;
        }

        @Override
        public Long getValue() {
            return functionalCounter.count();
        }

        @Override
        public boolean removeViaDelegate(MeterRegistry meterRegistry) {
            return functionalCounter != null && meterRegistry.remove(functionalCounter).isPresent();
        }
    }
}
