/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import java.util.Map;

import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

@HelidonTest
@AddConfig(key = "mp.metrics.gc-time-type", value = "gauge")
@Deprecated(since = "4.1", forRemoval = true)
class TestGcTimeGauge {

    @Inject
    @RegistryScope(scope = MetricRegistry.BASE_SCOPE)
    private MetricRegistry baseRegistry;

    @Test
    void checkForGauge() {

        Map<MetricID, Metric> gcTimeMetrics =
                baseRegistry.getMetrics((metricID, metric) -> metricID.getName().equals("gc.time"));

        assertThat("gc.time metric IDs", gcTimeMetrics.keySet(), not(empty()));
        assertThat("gc.time metric", gcTimeMetrics.values().stream().findFirst().orElseThrow(), instanceOf(Gauge.class));
    }
}
