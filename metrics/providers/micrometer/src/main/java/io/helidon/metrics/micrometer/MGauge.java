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
package io.helidon.metrics.micrometer;

import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;

class MGauge extends MMeter<Gauge> implements io.helidon.metrics.api.Gauge {

    /**
     * Creates a new builder for a wrapper around a Micrometer gauge that will be registered later, typically if the
     * developer is creating a gauge using the Helidon API.
     *
     * @param name name of the new gauge
     * @return new builder for a wrapper gauge
     */
    static <T> MGauge.Builder<T> builder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return new MGauge.Builder<>(name, stateObject, fn);
    }

    static <N extends Number> MGauge.Builder<N> builder(String name, N number) {
        return new MGauge.Builder<>(name, number, Number::doubleValue);
    }

    static <N extends Number> MGauge.Builder<?> builder(String name, Supplier<N> supplier) {
        return builder(name, supplier, s -> s.get().doubleValue());
    }

    /**
     * Creates a new wrapper gauge around an existing Micrometer gauge, typically if the developer has registered a
     * gauge directly using the Micrometer API rather than through the Helidon adapter but we need to expose the gauge
     * via a wrapper.
     *
     * @param gauge the Micrometer gauge
     * @return new wrapper around the gauge
     */
    static MGauge create(Gauge gauge) {
        return new MGauge(gauge);
    }

    private MGauge(Gauge delegate) {
        super(delegate);
    }

    private <T> MGauge(Gauge delegate, Builder<T> builder) {
        super(delegate, builder);
    }

    @Override
    public double value() {
        return delegate().value();
    }

    static class Builder<T> extends MMeter.Builder<Gauge.Builder<T>, Gauge, MGauge.Builder<T>, MGauge>
                implements io.helidon.metrics.api.Gauge.Builder<T> {

        private final T stateObject;
        private final ToDoubleFunction<T> fn;

        private Builder(String name, T stateObject, ToDoubleFunction<T> fn) {
            super(name, Gauge.builder(name, stateObject, fn));
            this.stateObject = stateObject;
            this.fn = fn;
        }

        @Override
        protected Builder<T> delegateTags(Iterable<Tag> tags) {
            delegate().tags(tags);
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
        public T stateObject() {
            return stateObject;
        }

        @Override
        public ToDoubleFunction<T> fn() {
            return fn;
        }

        @Override
        protected MGauge build(Gauge meter) {
            return new MGauge(meter, this);
        }
    }
}
