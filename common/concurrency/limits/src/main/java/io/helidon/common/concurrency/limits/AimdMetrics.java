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

package io.helidon.common.concurrency.limits;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;

import static io.helidon.metrics.api.Meter.Scope.VENDOR;

class AimdMetrics extends SemaphoreMetrics {
    private final String name;
    private final AtomicInteger limit;

    AimdMetrics(boolean enableMetrics,
                Semaphore semaphore,
                String name,
                AtomicInteger rejectedRequests,
                AtomicInteger concurrentRequests,
                AtomicInteger limit) {
        super(enableMetrics, semaphore, name, concurrentRequests, rejectedRequests);
        this.name = name;

        this.limit = limit;
    }

    @Override
    void register(MetricsFactory metricsFactory, MeterRegistry meterRegistry, List<Tag> tags) {
        super.register(metricsFactory, meterRegistry, tags);

        // actual value of limit at this time
        Gauge.Builder<Integer> limitBuilder = metricsFactory.gaugeBuilder(name + "_limit", limit::get)
                .scope(VENDOR);

        limitBuilder.tags(tags);
        meterRegistry.getOrCreate(limitBuilder);
    }
}
