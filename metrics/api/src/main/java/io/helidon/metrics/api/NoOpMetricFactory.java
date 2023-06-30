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

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import io.helidon.metrics.api.spi.MetricFactory;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

class NoOpMetricFactory implements MetricFactory {
    @Override
    public Counter counter(String scope, Metadata metadata, Tag... tags) {
        return NoOpMetricImpl.NoOpCounterImpl.create(scope, metadata);
    }

    @Override
    public <T> Counter counter(String scope, Metadata metadata, T origin, ToDoubleFunction<T> function, Tag... tags) {
        return NoOpMetricImpl.NoOpFunctionalCounterImpl.create(scope, metadata);
    }

    @Override
    public Timer timer(String scope, Metadata metadata, Tag... tags) {
        return NoOpMetricImpl.NoOpTimerImpl.create(scope, metadata);
    }

    @Override
    public Histogram summary(String scope, Metadata metadata, Tag... tags) {
        return NoOpMetricImpl.NoOpHistogramImpl.create(scope, metadata);
    }

    @Override
    public <N extends Number> Gauge<N> gauge(String scope, Metadata metadata, Supplier<N> supplier, Tag... tags) {
        return NoOpMetricImpl.NoOpGaugeImpl.create(scope, metadata, supplier);
    }

    @Override
    public <N extends Number, T> Gauge<N> gauge(String scope, Metadata metadata, T target, Function<T, N> fn, Tag... tags) {
        return NoOpMetricImpl.NoOpGaugeImpl.create(scope, metadata, target, fn);
    }

    @Override
    public <T> Gauge<Double> gauge(String scope, Metadata metadata, T target, ToDoubleFunction<T> fn, Tag... tags) {
        return NoOpMetricImpl.NoOpGaugeImpl.create(scope, metadata, target, fn);
    }
}
