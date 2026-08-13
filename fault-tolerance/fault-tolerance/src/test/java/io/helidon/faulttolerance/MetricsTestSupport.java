/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;

import static io.helidon.metrics.api.Meter.Scope.VENDOR;

@SuppressWarnings("unchecked")
final class MetricsTestSupport {
    private MetricsTestSupport() {
    }

    static void activateConfig() {
        Services.get(Config.class);
    }

    static <T extends Number> Gauge<T> gauge(MeterRegistry meterRegistry,
                                             String name,
                                             Tag... tags) {
        return meter(meterRegistry, Gauge.class, Meter.Type.GAUGE, name, List.of(tags));
    }

    static Counter counter(MeterRegistry meterRegistry, String name, Tag... tags) {
        return meter(meterRegistry, Counter.class, Meter.Type.COUNTER, name, List.of(tags));
    }

    static Timer timer(MeterRegistry meterRegistry, String name, Tag... tags) {
        return meter(meterRegistry, Timer.class, Meter.Type.TIMER, name, List.of(tags));
    }

    private static <M extends Meter> M meter(MeterRegistry meterRegistry,
                                             Class<M> meterClass,
                                             Meter.Type meterType,
                                             String name,
                                             List<Tag> tags) {
        for (Meter meter : meterRegistry.meters(List.of(VENDOR))) {
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
