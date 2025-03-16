/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.faulttolerance;

import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import static io.helidon.faulttolerance.FaultTolerance.FT_METRICS_DEFAULT_ENABLED;
import static io.helidon.metrics.api.Meter.Scope.VENDOR;

@SuppressWarnings("unchecked")
class MetricsUtils {

    private static final LazyValue<MetricsFactory> METRICS_FACTORY = LazyValue.create(MetricsFactory::getInstance);
    private static final LazyValue<MeterRegistry> METRICS_REGISTRY = LazyValue.create(Metrics::globalRegistry);

    private MetricsUtils() {
    }

    /**
     * Looks for the metrics enabled flag in config and caches result. FT metrics
     * are disabled by default.
     *
     * @return value of metrics flag
     */
    static boolean defaultEnabled() {
        return FaultTolerance.config()
                .get(FT_METRICS_DEFAULT_ENABLED)
                .asBoolean()
                .orElse(false);
    }

    static <T extends Number> void gaugeBuilder(String name, Supplier<T> supplier, Tag... tags) {
        Gauge.Builder<T> builder = METRICS_FACTORY.get().gaugeBuilder(name, supplier).scope(VENDOR);
        List<Tag> tagList = List.of(tags);
        builder.tags(tagList);
        METRICS_REGISTRY.get().getOrCreate(builder);
        METRICS_REGISTRY.get().gauge(name, tagList).orElseThrow();
    }

    static Counter counterBuilder(String name, Tag... tags) {
        Counter.Builder builder = METRICS_FACTORY.get().counterBuilder(name).scope(VENDOR);
        List<Tag> tagList = List.of(tags);
        builder.tags(tagList);
        METRICS_REGISTRY.get().getOrCreate(builder);
        return METRICS_REGISTRY.get().counter(name, tagList).orElseThrow();
    }

    static Timer timerBuilder(String name, Tag... tags) {
        Timer.Builder builder = METRICS_FACTORY.get().timerBuilder(name).scope(VENDOR);
        List<Tag> tagList = List.of(tags);
        builder.tags(tagList);
        METRICS_REGISTRY.get().getOrCreate(builder);
        return METRICS_REGISTRY.get().timer(name, tagList).orElseThrow();
    }

    static <T extends Number> Gauge<T> gauge(String name, Tag... tags) {
        return METRICS_REGISTRY.get().gauge(name, List.of(tags)).orElseThrow();
    }

    static Counter counter(String name, Tag... tags) {
        return METRICS_REGISTRY.get().counter(name, List.of(tags)).orElseThrow();
    }

    static Timer timer(String name, Tag... tags) {
        return METRICS_REGISTRY.get().timer(name, List.of(tags)).orElseThrow();
    }
}
