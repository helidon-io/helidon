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

import io.helidon.common.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import static io.helidon.metrics.api.Meter.Scope.VENDOR;

@SuppressWarnings("unchecked")
class MetricsUtils {

    private static final String FT_METRICS_ENABLED = "ft.metrics.enabled";
    private static final MetricsFactory METRICS_FACTORY = MetricsFactory.getInstance();
    private static final MeterRegistry METRICS_REGISTRY = Metrics.globalRegistry();

    private static volatile Boolean metricsEnabled;

    private MetricsUtils() {
    }

    /**
     * Looks for the metrics enabled flag in config and caches result. FT metrics
     * are disabled by default.
     *
     * @return value of metrics flag
     */
    static boolean metricsEnabled() {
        if (metricsEnabled == null) {
            ServiceRegistry registry = ServiceRegistryManager.create().registry();
            Config config = registry.get(Config.class);
            metricsEnabled = config.get(FT_METRICS_ENABLED).asBoolean().orElse(false);
        }
        return metricsEnabled;
    }

    static <T extends Number> Gauge<T> gaugeBuilder(String name, Supplier<T> supplier, Tag... tags) {
        Gauge.Builder<T> builder = METRICS_FACTORY.gaugeBuilder(name, supplier).scope(VENDOR);
        List<Tag> tagList = List.of(tags);
        builder.tags(tagList);
        METRICS_REGISTRY.getOrCreate(builder);
        return METRICS_REGISTRY.gauge(name, tagList).orElseThrow();
    }

    static Counter counterBuilder(String name, Tag... tags) {
        Counter.Builder builder = METRICS_FACTORY.counterBuilder(name).scope(VENDOR);
        List<Tag> tagList = List.of(tags);
        builder.tags(tagList);
        METRICS_REGISTRY.getOrCreate(builder);
        return METRICS_REGISTRY.counter(name, tagList).orElseThrow();
    }

    static Timer timerBuilder(String name, Tag... tags) {
        Timer.Builder builder = METRICS_FACTORY.timerBuilder(name).scope(VENDOR);
        List<Tag> tagList = List.of(tags);
        builder.tags(tagList);
        METRICS_REGISTRY.getOrCreate(builder);
        return METRICS_REGISTRY.timer(name, tagList).orElseThrow();
    }

    static <T extends Number> Gauge<T> gauge(String name, Tag... tags) {
        return METRICS_REGISTRY.gauge(name, List.of(tags)).orElseThrow();
    }

    static Counter counter(String name, Tag... tags) {
        return METRICS_REGISTRY.counter(name, List.of(tags)).orElseThrow();
    }

    static Timer timer(String name, Tag... tags) {
        return METRICS_REGISTRY.timer(name, List.of(tags)).orElseThrow();
    }
}
