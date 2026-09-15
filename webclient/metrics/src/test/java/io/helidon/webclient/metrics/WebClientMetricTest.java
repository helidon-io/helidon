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

package io.helidon.webclient.metrics;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.Method;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebClientMetricTest {

    @Test
    void configuredMethodMatchingIsCaseSensitive() {
        var metric = builder()
                .config(Config.create(ConfigSources.create(Map.of("methods.0", "get"))))
                .build();

        assertExactLowercaseMatch(metric);
    }

    @Test
    void programmaticMethodMatchingIsCaseSensitive() {
        var metric = builder()
                .methods("get")
                .build();

        assertExactLowercaseMatch(metric);
    }

    private static WebClientMetric.Builder builder() {
        var meterRegistry = mock(MeterRegistry.class);
        when(meterRegistry.metricsFactory()).thenReturn(mock(MetricsFactory.class));
        return WebClientMetric.builder(WebClientMetricType.COUNTER)
                .meterRegistry(meterRegistry);
    }

    private static void assertExactLowercaseMatch(WebClientMetric metric) {
        assertThat("Standard uppercase method does not match", metric.handlesMethod(Method.GET), is(false));
        assertThat("Lowercase method matches exactly", metric.handlesMethod(Method.create("get")), is(true));
    }
}
