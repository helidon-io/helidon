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

import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

class MGauge extends MMeter<Gauge> implements io.helidon.metrics.api.Gauge {

    static <T> MGauge.Builder<T> builder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return new MGauge.Builder<>(name, stateObject, fn);
    }
    static MGauge of(Gauge gauge) {
        return new MGauge(gauge);
    }

    private MGauge(Gauge delegate) {
        super(delegate);
    }

    @Override
    public double value() {
        return delegate().value();
    }

    static class Builder<T> extends MMeter.Builder<Gauge.Builder<T>, MGauge.Builder<T>, MGauge>
            implements io.helidon.metrics.api.Gauge.Builder<T> {

        private Builder(String name,  T stateObject, ToDoubleFunction<T> fn) {
            super(name, Gauge.builder(name, stateObject, fn));
            prep(delegate()::tags,
                 delegate()::description,
                 delegate()::baseUnit);
        }

        @Override
        MGauge register(MeterRegistry meterRegistry) {
            return MGauge.of(delegate().register(meterRegistry));
        }
    }
}
