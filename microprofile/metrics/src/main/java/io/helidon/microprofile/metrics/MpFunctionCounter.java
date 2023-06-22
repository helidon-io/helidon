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

import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.eclipse.microprofile.metrics.Counter;

class MpFunctionCounter extends MpMetric<FunctionCounter> implements Meter, Counter {

    static <T> Builder<T> builder(MpMetricRegistry mpMetricRegistry, String name, T origin, ToDoubleFunction<T> fn) {
        return new Builder<>(mpMetricRegistry, name, origin, fn);
    }

    private MpFunctionCounter(Builder<?> builder) {
        super(delegate(builder), builder.mpMetricRegistry.meterRegistry());
    }

    @Override
    public Id getId() {
        return delegate().getId();
    }

    @Override
    public Iterable<Measurement> measure() {
        return delegate().measure();
    }

    @Override
    public void inc() {
        throw new UnsupportedOperationException("Not allowed on " + MpFunctionCounter.class.getName());
    }

    @Override
    public void inc(long l) {
        throw new UnsupportedOperationException("Not allowed on " + MpFunctionCounter.class.getName());
    }

    @Override
    public long getCount() {
        return (long) delegate().count();
    }

    private static <T> FunctionCounter delegate(Builder<T> builder) {
        return builder.mpMetricRegistry
                .meterRegistry()
                .more()
                .counter(builder.name,
                         builder.tags,
                         builder.origin,
                         builder.fn);
    }

    static class Builder<T> implements io.helidon.common.Builder<Builder<T>, MpFunctionCounter> {

        private final MpMetricRegistry mpMetricRegistry;

        private final String name;
        private final ToDoubleFunction<T> fn;
        private final T origin;
        private Tags tags;

        private Builder(MpMetricRegistry mpMetricRegistry, String name, T origin, ToDoubleFunction<T> fn) {
            this.mpMetricRegistry = mpMetricRegistry;
            this.name = name;
            this.origin = origin;
            this.fn = fn;
        }

        Builder<T> tags(Tags tags) {
            this.tags = tags;
            return this;
        }

        @Override
        public MpFunctionCounter build() {
            return new MpFunctionCounter(this);
        }
    }
}
