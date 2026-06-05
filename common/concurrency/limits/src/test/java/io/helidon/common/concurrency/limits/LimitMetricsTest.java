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
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Services;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;

class LimitMetricsTest {
    private static final MetricsFactory METRICS_FACTORY = Services.get(MetricsFactory.class);
    private static final List<Tag> REAL_METER_TAGS = List.of(METRICS_FACTORY.tagCreate("origin", "limit-metrics-test"));
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

    @BeforeEach
    @AfterEach
    void cleanMeters() {
        MeterRegistry meterRegistry = METRICS_FACTORY.globalRegistry();
        for (String meterName : REAL_METER_NAMES) {
            meterRegistry.remove(meterName, REAL_METER_TAGS, Meter.Scope.VENDOR);
        }
    }

    @Test
    void customContextTagsAreUsedForMetrics() {
        List<Tag> tags = List.of(METRICS_FACTORY.tagCreate("origin", "batch-import"),
                                 METRICS_FACTORY.tagCreate("component", "inventory"));
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
                                                        List.of(METRICS_FACTORY.tagCreate("origin", "listener"),
                                                                METRICS_FACTORY.tagCreate("transport", "quic"))));

        assertThat(metrics.capturedTags(), hasEntry("origin", "listener"));
        assertThat(metrics.capturedTags(), hasEntry("transport", "quic"));
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
        Limit.InitializationContext context = Limit.InitializationContext.create("unit-test", REAL_METER_TAGS);
        MeterRegistry meterRegistry = Services.get(MetricsFactory.class).globalRegistry();

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

        assertThat(hasMeter(meterRegistry, Meter.Type.GAUGE, "test_fixed_concurrent_requests", REAL_METER_TAGS), is(true));
        assertThat(hasMeter(meterRegistry, Meter.Type.TIMER, "test_fixed_rtt", REAL_METER_TAGS), is(true));
        assertThat(hasMeter(meterRegistry, Meter.Type.GAUGE, "test_throughput_concurrent_requests", REAL_METER_TAGS), is(true));
        assertThat(hasMeter(meterRegistry, Meter.Type.TIMER, "test_throughput_rtt", REAL_METER_TAGS), is(true));
        assertThat(hasMeter(meterRegistry, Meter.Type.TIMER, "test_aimd_rtt", REAL_METER_TAGS), is(true));
        assertThat(hasMeter(meterRegistry, Meter.Type.GAUGE, "test_aimd_limit", REAL_METER_TAGS), is(true));
        assertThat(meterCount(meterRegistry, Meter.Type.GAUGE, "test_fixed_concurrent_requests", REAL_METER_TAGS), is(1L));
    }

    @Test
    void disabledMetricsDoNotRegisterRealMeters() {
        Limit disabled = FixedLimit.builder()
                .name("test_disabled")
                .permits(1)
                .enableMetrics(false)
                .build();

        disabled.init(Limit.InitializationContext.create("unit-test", REAL_METER_TAGS));

        MeterRegistry meterRegistry = Services.get(MetricsFactory.class).globalRegistry();
        assertThat(hasMeter(meterRegistry, Meter.Type.GAUGE, "test_disabled_concurrent_requests", REAL_METER_TAGS), is(false));
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
                .collect(java.util.stream.Collectors.toMap(Tag::key, Tag::value));

        long count = 0;
        for (Meter meter : meterRegistry.meters(List.of(Meter.Scope.VENDOR))) {
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

        CapturingSemaphoreMetrics() {
            super(true, null, "test", new AtomicInteger(), new AtomicInteger());
        }

        @Override
        void register(MetricsFactory metricsFactory, MeterRegistry meterRegistry, List<Tag> tags) {
            capturedTags = tags.stream()
                    .collect(java.util.stream.Collectors.toMap(Tag::key, Tag::value));
        }

        Map<String, String> capturedTags() {
            return capturedTags;
        }
    }
}
