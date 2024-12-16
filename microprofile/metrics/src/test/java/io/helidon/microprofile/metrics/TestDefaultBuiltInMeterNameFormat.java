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

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@HelidonTest
@AddConfig(key = "metrics." + MetricsCdiExtension.REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME, value = "true")
@AddBean(HelloWorldResource.class)
class TestDefaultBuiltInMeterNameFormat {

    @Inject
    @RegistryScope(scope = MetricRegistry.BASE_SCOPE)
    private MetricRegistry baseRegistry;

    @Test
    void checkDefaultNames() {
        Map<MetricID, Metric> baseMetrics = baseRegistry.getMetrics();

        assertThat("Built-in metrics", baseMetrics.keySet(), allOf(
                hasItem(MetricIDMatcher.withName(equalTo("memory.usedHeap"))),
                not(hasItem(MetricIDMatcher.withName(equalTo("memory.used_heap"))))));

        Map<MetricID, Metric> metrics = baseRegistry.getMetrics();
        assertThat("REST.request unmapped exception metric", metrics.keySet(), allOf(
                hasItem(MetricIDMatcher.withName(equalTo(MetricsCdiExtension.SYNTHETIC_TIMER_METRIC_UNMAPPED_EXCEPTION_NAME))),
                not(hasItem(MetricIDMatcher.withName(equalTo(MetricsCdiExtension.SYNTHETIC_TIMER_METRIC_NAME +
                                                                     ".unmapped_exception.total"))))));

    }

}
