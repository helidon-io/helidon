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
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;

import static io.helidon.faulttolerance.FaultTolerance.FT_METRICS_DEFAULT_ENABLED;
import static io.helidon.metrics.api.Meter.Scope.VENDOR;

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
        return Services.get(Config.class)
                .get(FT_METRICS_DEFAULT_ENABLED)
                .asBoolean()
                .orElse(false);
    }

    static Tag tag(MetricsFactory metricsFactory, String name, String value) {
        return metricsFactory.tagCreate(name, value);
    }

    static <T extends Number> void gaugeBuilder(MetricsFactory metricsFactory, String name, Supplier<T> supplier, Tag... tags) {
        Gauge.Builder<T> builder = metricsFactory.gaugeBuilder(name, supplier).scope(VENDOR);
        List<Tag> tagList = List.of(tags);
        builder.tags(tagList);
        metricsFactory.globalRegistry().getOrCreate(builder);
    }

    static Counter counterBuilder(MetricsFactory metricsFactory, String name, Tag... tags) {
        Counter.Builder builder = metricsFactory.counterBuilder(name).scope(VENDOR);
        List<Tag> tagList = List.of(tags);
        builder.tags(tagList);
        return metricsFactory.globalRegistry().getOrCreate(builder);
    }

    static Timer timerBuilder(MetricsFactory metricsFactory, String name, Tag... tags) {
        Timer.Builder builder = metricsFactory.timerBuilder(name).scope(VENDOR);
        List<Tag> tagList = List.of(tags);
        builder.tags(tagList);
        return metricsFactory.globalRegistry().getOrCreate(builder);
    }

    static <T extends Number> Gauge<T> gauge(MetricsFactory metricsFactory, String name, Tag... tags) {
        return meter(metricsFactory, Gauge.class, Meter.Type.GAUGE, name, List.of(tags));
    }

    static Counter counter(MetricsFactory metricsFactory, String name, Tag... tags) {
        return meter(metricsFactory, Counter.class, Meter.Type.COUNTER, name, List.of(tags));
    }

    static Timer timer(MetricsFactory metricsFactory, String name, Tag... tags) {
        return meter(metricsFactory, Timer.class, Meter.Type.TIMER, name, List.of(tags));
    }

    private static <M extends Meter> M meter(MetricsFactory metricsFactory, Class<M> meterClass, Meter.Type meterType, String name, List<Tag> tags) {
        var registry = metricsFactory.globalRegistry();
        for (Meter meter : registry.meters(List.of(VENDOR))) {
            if (meterClass.isInstance(meter)
                    && meter.type() == meterType
                    && meter.id().name().equals(name)
                    && containsTags(meter, tags)) {
                return meterClass.cast(meter);
            }
        }
        throw new NoSuchElementException("No " + meterType + " meter found for " + name + " and tags " + tags);
    }

    private static boolean containsTags(Meter meter, List<Tag> tags) {
        return tags.stream()
                .allMatch(tag -> tag.value().equals(meter.id().tagsMap().get(tag.key())));
    }
}
