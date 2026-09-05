/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;

import static io.helidon.faulttolerance.FaultTolerance.FT_METRICS_DEFAULT_ENABLED;

@SuppressWarnings("unchecked")
class MetricsUtils {

    private MetricsUtils() {
    }

    /**
     * Looks for the metrics enabled flag in config and caches result. FT metrics
     * are disabled by default.
     *
     * @return value of metrics flag
     */
    static boolean defaultEnabled() {
        if (!GlobalServiceRegistry.configured()) {
            return false;
        }

        return defaultEnabled(GlobalServiceRegistry.registry());
    }

    static boolean defaultEnabled(ServiceRegistry serviceRegistry) {
        return serviceRegistry
                .firstActive(Config.class)
                .map(config -> config.get(FT_METRICS_DEFAULT_ENABLED)
                        .asBoolean()
                        .orElse(false))
                .orElse(false);
    }

    static Tag tag(MetricsFactory metricsFactory, String name, String value) {
        return metricsFactory.tagCreate(name, value);
    }

    static <T extends Number> void gaugeBuilder(MetricsFactory metricsFactory,
                                                MeterRegistry meterRegistry,
                                                String name,
                                                Supplier<T> supplier,
                                                Tag... tags) {
        meterRegistry.getOrCreate(metricsFactory.gaugeBuilder(name, supplier)
                                          .tags(List.of(tags))
                                          .origin(FaultTolerance.class.getName()));
    }

    static Counter counterBuilder(MetricsFactory metricsFactory, MeterRegistry meterRegistry, String name, Tag... tags) {
        return meterRegistry.getOrCreate(metricsFactory.counterBuilder(name)
                                                 .tags(List.of(tags))
                                                 .origin(FaultTolerance.class.getName()));
    }

    static Timer timerBuilder(MetricsFactory metricsFactory, MeterRegistry meterRegistry, String name, Tag... tags) {
        return meterRegistry.getOrCreate(metricsFactory.timerBuilder(name)
                                                 .tags(List.of(tags))
                                                 .origin(FaultTolerance.class.getName()));
    }

}
