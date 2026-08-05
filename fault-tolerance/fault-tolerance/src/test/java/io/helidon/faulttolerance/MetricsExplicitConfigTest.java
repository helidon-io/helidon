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

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.faulttolerance.FaultTolerance.FT_METRICS_DEFAULT_ENABLED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
class MetricsExplicitConfigTest {
    private final MetricsFactory metricsFactory;
    private final MeterRegistry meterRegistry;

    MetricsExplicitConfigTest(MetricsFactory metricsFactory, MeterRegistry meterRegistry) {
        this.metricsFactory = metricsFactory;
        this.meterRegistry = meterRegistry;
    }

    @BeforeAll
    static void setupTest() {
        Services.set(Config.class,
                     Config.just(ConfigSources.create(Map.of(FT_METRICS_DEFAULT_ENABLED, "true"))));
    }

    @Test
    void testGlobalMetricsEnabledFromSetConfig() {
        Retry retry = Retry.builder()
                .name("explicit-config")
                .build();

        retry.invoke(() -> 0);

        Counter callsCounter = MetricsUtils.counter(meterRegistry,
                                                    Retry.FT_RETRY_CALLS_TOTAL,
                                                    MetricsUtils.tag(metricsFactory, "name", retry.name()));
        assertThat(callsCounter.count(), is(1L));
    }
}
