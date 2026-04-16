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
