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
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.spi.MeterBuilderCustomizer;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

@Testing.Test
class LimitMetricsTest {
    private static final String[] REAL_METER_NAMES = {
            "test_fixed_concurrent_requests",
            "test_fixed_rtt",
            "test_throughput_concurrent_requests",
            "test_throughput_rtt",
            "test_aimd_concurrent_requests",
            "test_aimd_rtt",
            "test_aimd_limit",
            "test_disabled_concurrent_requests"
    };

    private final MetricsFactory metricsFactory;
    private final MeterRegistry meterRegistry;
    private final List<Tag> realMeterTags;

    LimitMetricsTest(MetricsFactory metricsFactory, MeterRegistry meterRegistry) {
        this.metricsFactory = metricsFactory;
        this.meterRegistry = meterRegistry;
        this.realMeterTags = List.of(metricsFactory.tagCreate("origin", "limit-metrics-test"));
    }

    @BeforeEach
    @AfterEach
    void cleanMeters() {
        for (String meterName : REAL_METER_NAMES) {
            meterRegistry.remove(meterName, realMeterTags);
        }
    }

    @Test
    void customContextTagsAreUsedForMetrics() {
        List<Tag> tags = List.of(metricsFactory.tagCreate("origin", "batch-import"),
                                 metricsFactory.tagCreate("component", "inventory"));
        CapturingSemaphoreMetrics metrics = new CapturingSemaphoreMetrics();

        metrics.init(Limit.InitializationContext.create("batch-import", tags));

        assertThat(metrics.capturedTags(), hasEntry("origin", "batch-import"));
        assertThat(metrics.capturedTags(), hasEntry("component", "inventory"));
        assertThat(metrics.capturedTags(), not(hasEntry("socketName", "batch-import")));
    }

    @Test
    void mapTagsAreConvertedToMetricTags() {
        CapturingSemaphoreMetrics metrics = new CapturingSemaphoreMetrics();

        metrics.init(Limit.InitializationContext.create("listener-quic",
                                                        List.of(metricsFactory.tagCreate("origin", "listener"),
                                                                metricsFactory.tagCreate("transport", "quic"))));

        assertThat(metrics.capturedTags(), hasEntry("origin", "listener"));
        assertThat(metrics.capturedTags(), hasEntry("transport", "quic"));
    }

    @Test
    void suppliedMeterRegistryIsUsedForMetrics() {
        CapturingSemaphoreMetrics metrics = new CapturingSemaphoreMetrics();
        AtomicInteger supplierCalls = new AtomicInteger();

        metrics.init(Limit.InitializationContext.create("managed",
                                                        realMeterTags,
                                                        () -> {
                                                            supplierCalls.incrementAndGet();
                                                            return meterRegistry;
                                                        }));

        assertThat(metrics.capturedMeterRegistry(), sameInstance(meterRegistry));
        assertThat(supplierCalls.get(), is(1));
    }

    @Test
    void legacyInitAddsSocketNameTagForNamedOrigin() {
        CapturingSemaphoreMetrics metrics = new CapturingSemaphoreMetrics();

        metrics.init("@admin");

        assertThat(metrics.capturedTags(), hasEntry("socketName", "@admin"));
    }

    @Test
    void legacyInitOmitsSocketNameTagForDefaultOrigin() {
        CapturingSemaphoreMetrics metrics = new CapturingSemaphoreMetrics();

        metrics.init(Service.Named.DEFAULT_NAME);

        assertThat(metrics.capturedTags().containsKey("socketName"), is(false));
    }

    @Test
    void publicContextInitRegistersRealMetersForBuiltInLimits() {
        Limit.InitializationContext context = Limit.InitializationContext.create("unit-test", realMeterTags);
        Limit fixed = FixedLimit.builder()
                .name("test_fixed")
                .permits(1)
                .enableMetrics(true)
                .build();
        Limit throughput = ThroughputLimit.builder()
                .name("test_throughput")
                .amount(1)
                .enableMetrics(true)
                .build();
        Limit aimd = AimdLimit.builder()
                .name("test_aimd")
                .minLimit(1)
                .initialLimit(1)
                .maxLimit(1)
                .enableMetrics(true)
                .build();

        fixed.init(context);
        fixed.init(context);
        throughput.init(context);
        aimd.init(context);

        assertThat(hasMeter(meterRegistry, Meter.Type.GAUGE, "test_fixed_concurrent_requests", realMeterTags), is(true));
        assertThat(hasMeter(meterRegistry, Meter.Type.TIMER, "test_fixed_rtt", realMeterTags), is(true));
        assertThat(hasMeter(meterRegistry, Meter.Type.GAUGE, "test_throughput_concurrent_requests", realMeterTags), is(true));
        assertThat(hasMeter(meterRegistry, Meter.Type.TIMER, "test_throughput_rtt", realMeterTags), is(true));
        assertThat(hasMeter(meterRegistry, Meter.Type.TIMER, "test_aimd_rtt", realMeterTags), is(true));
        assertThat(hasMeter(meterRegistry, Meter.Type.GAUGE, "test_aimd_limit", realMeterTags), is(true));
        assertThat(meterCount(meterRegistry, Meter.Type.GAUGE, "test_fixed_concurrent_requests", realMeterTags), is(1L));
    }

    @Test
    void disabledMetricsDoNotRegisterRealMeters() {
        AtomicInteger supplierCalls = new AtomicInteger();
        Limit disabled = FixedLimit.builder()
                .name("test_disabled")
                .permits(1)
                .enableMetrics(false)
                .build();

        disabled.init(Limit.InitializationContext.create("unit-test",
                                                         realMeterTags,
                                                         () -> {
                                                             supplierCalls.incrementAndGet();
                                                             return meterRegistry;
                                                         }));

        assertThat(hasMeter(meterRegistry, Meter.Type.GAUGE, "test_disabled_concurrent_requests", realMeterTags), is(false));
        assertThat(supplierCalls.get(), is(0));
    }

    @Test
    void customizerCanDistinguishSemaphoreAndAimdMetrics() {
        AtomicReference<MetricsFactory> metricsFactoryRef = new AtomicReference<>();
        MeterBuilderCustomizer customizer = new MeterBuilderCustomizer() {
            @Override
            public void customize(Meter.Builder<?, ?> builder, String origin) {
                if (origin.equals(SemaphoreMetrics.class.getName()) || origin.equals(AimdMetrics.class.getName())) {
                    builder.addTag(metricsFactoryRef.get().tagCreate("metric-origin", origin));
                }
            }
        };
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(MeterBuilderCustomizer.class,
                                                                                                     customizer)
                                                                                .build());
        try {
            MetricsFactory metricsFactory = manager.registry().get(MetricsFactory.class);
            metricsFactoryRef.set(metricsFactory);
            MeterRegistry meterRegistry = manager.registry().get(MeterRegistry.class);
            AimdMetrics metrics = new AimdMetrics(true,
                                                  new Semaphore(1),
                                                  "origin_aware",
                                                  new AtomicInteger(),
                                                  new AtomicInteger(),
                                                  new AtomicInteger(1));

            metrics.register(metricsFactory, meterRegistry, List.of());

            Tag semaphoreOrigin = metricsFactory.tagCreate("metric-origin", SemaphoreMetrics.class.getName());
            Tag aimdOrigin = metricsFactory.tagCreate("metric-origin", AimdMetrics.class.getName());
            assertThat("Semaphore meter uses the semaphore origin",
                       hasMeter(meterRegistry,
                                Meter.Type.GAUGE,
                                "origin_aware_concurrent_requests",
                                List.of(semaphoreOrigin)),
                       is(true));
            assertThat("AIMD meter uses the AIMD origin",
                       hasMeter(meterRegistry, Meter.Type.GAUGE, "origin_aware_limit", List.of(aimdOrigin)),
                       is(true));
        } finally {
            manager.shutdown();
        }
    }

    private static boolean hasMeter(MeterRegistry meterRegistry,
                                    Meter.Type meterType,
                                    String meterName,
                                    List<Tag> tags) {
        return meterCount(meterRegistry, meterType, meterName, tags) > 0;
    }

    private static long meterCount(MeterRegistry meterRegistry,
                                   Meter.Type meterType,
                                   String meterName,
                                   List<Tag> tags) {
        Map<String, String> expectedTags = tags.stream()
                .collect(Collectors.toMap(Tag::key, Tag::value));

        long count = 0;
        for (Meter meter : meterRegistry.meters()) {
            if (meter.id().name().equals(meterName)
                    && meter.type() == meterType
                    && meter.id().tagsMap().entrySet().containsAll(expectedTags.entrySet())) {
                count++;
            }
        }
        return count;
    }

    private static class CapturingSemaphoreMetrics extends SemaphoreMetrics {
        private Map<String, String> capturedTags;
        private MeterRegistry capturedMeterRegistry;

        CapturingSemaphoreMetrics() {
            super(true, null, "test", new AtomicInteger(), new AtomicInteger());
        }

        @Override
        void register(MetricsFactory metricsFactory, MeterRegistry meterRegistry, List<Tag> tags) {
            capturedMeterRegistry = meterRegistry;
            capturedTags = tags.stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value));
        }

        Map<String, String> capturedTags() {
            return capturedTags;
        }

        MeterRegistry capturedMeterRegistry() {
            return capturedMeterRegistry;
        }
    }
}
