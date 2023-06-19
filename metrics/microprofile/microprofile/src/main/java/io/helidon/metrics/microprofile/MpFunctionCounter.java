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
package io.helidon.metrics.microprofile;

import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;

class MpFunctionalCounter extends MpCounter {

    static <T> Builder<T> builder(MpMetricRegistry mpMetricRegistry, String name, T origin, ToDoubleFunction<T> fn) {
        return new Builder(mpMetricRegistry, name, origin, fn);
    }

    private MpFunctionalCounter(Builder builder) {
        super(delegate(builder));
    }

    private static <T> Counter delegate(Builder<T> builder) {
        return builder.mpMetricRegistry
                .meterRegistry()
                .more()
                .counter(new Meter.Id(builder.name,
                                      builder.tags,
                                      builder.baseUnit,
                                      builder.description,
                                      Meter.Type.COUNTER),
                         builder.origin,
                         builder.fn);
    }

    static class Builder<T> implements io.helidon.common.Builder<Builder<T>, MpFunctionalCounter> {

        private final MpMetricRegistry mpMetricRegistry;

        private final String name;
        private final ToDoubleFunction<T> fn;
        private final T origin;
        private Tags tags;
        private String description;
        private String baseUnit;

        private Builder(MpMetricRegistry mpMetricRegistry, String name, T origin, ToDoubleFunction<T> fn) {
            this.mpMetricRegistry = mpMetricRegistry;
            this.name = name;
            this.origin = origin;
            this.fn = fn;
        }

        Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        Builder<T> tags(Tags tags) {
            this.tags = tags;
            return this;
        }

        Builder<T> baseUnit(String baseUnit) {
            this.baseUnit = baseUnit;
            return this;
        }

        @Override
        public MpFunctionalCounter build() {
            return new MpFunctionalCounter(this);
        }


    }
}
