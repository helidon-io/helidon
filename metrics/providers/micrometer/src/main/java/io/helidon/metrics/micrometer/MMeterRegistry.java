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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.common.config.Config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

class MMeterRegistry implements io.helidon.metrics.api.MeterRegistry {

    static MMeterRegistry create(Config helidonConfig) {
        return new MMeterRegistry(new PrometheusMeterRegistry(new PrometheusConfig() {
            @Override
            public String get(String key) {
                return helidonConfig.get(key).asString().orElse(null);
            }
        }));
    }

    private final MeterRegistry delegate;

    private MMeterRegistry(MeterRegistry delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public List<? extends io.helidon.metrics.api.Meter> meters() {
        return delegate.getMeters()
                .stream()
                .map(MMeter::of)
                .toList();
    }

    @Override
    public Collection<? extends io.helidon.metrics.api.Meter> meters(Predicate<io.helidon.metrics.api.Meter> filter) {
        return delegate.getMeters()
                .stream()
                .map(MMeter::of)
                .toList();
    }

    @Override
    public <M extends io.helidon.metrics.api.Meter,
            B extends io.helidon.metrics.api.Meter.Builder<B, M>> M getOrCreate(B builder) {
        if (builder instanceof MCounter.Builder cBuilder) {
            return (M) cBuilder.register(delegate);
        } else if (builder instanceof MDistributionSummary.Builder sBuilder) {
            return (M) sBuilder.delegate().register(delegate);
        } else if (builder instanceof MGauge.Builder<?> gBuilder) {
            return (M) gBuilder.delegate().register(delegate);
        } else if (builder instanceof MTimer.Builder tBuilder) {
            return (M) tBuilder.delegate().register(delegate);
        } else {
            throw new IllegalArgumentException(String.format("Unexpected builder type %s, expected one of %s",
                                                             builder.getClass().getName(),
                                                             List.of(MCounter.Builder.class.getName(),
                                                                     MDistributionSummary.Builder.class.getName(),
                                                                     MGauge.Builder.class.getName(),
                                                                     MTimer.Builder.class.getName())));
        }
    }

    @Override
    public <M extends io.helidon.metrics.api.Meter> Optional<M> get(Class<M> mClass,
                                                                    String name,
                                                                    Iterable<io.helidon.metrics.api.Tag> tags) {
        return Optional.empty();
    }

    @Override
    public io.helidon.metrics.api.Meter remove(io.helidon.metrics.api.Meter meter) {
        return null;
    }

    @Override
    public io.helidon.metrics.api.Meter remove(io.helidon.metrics.api.Meter.Id id) {
        return null;
    }

    @Override
    public io.helidon.metrics.api.Meter remove(String name,
                                               Iterable<io.helidon.metrics.api.Tag> tags) {
        return null;
    }

    MeterRegistry delegate() {
        return delegate;
    }
}
