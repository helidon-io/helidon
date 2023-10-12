/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import com.oracle.bmc.monitoring.model.MetricDataDetails;

class OciMetricsDataTest {
    private final OciMetricsSupport.NameFormatter nameFormatter = new OciMetricsSupport.NameFormatter() { };
    private final String dimensionScopeName = "scope";

    private final MeterRegistry meterRegistry = Metrics.globalRegistry();



    @BeforeEach
    void clearAllRegistry() {
        List<Meter> meters = meterRegistry.meters();
        meters.forEach(meterRegistry::remove);
    }

    @Test
    void testMetricRegistries() {
        String counterName = "DummyCounter";
        String timerName = "DummyTimer";

        meterRegistry.getOrCreate(Counter.builder(counterName)
                                          .scope(Meter.Scope.BASE))
                .increment();
        int counterMetricCount = 1;
        meterRegistry.getOrCreate(Timer.builder(timerName)
                                          .scope(Meter.Scope.APPLICATION))
                .record(Duration.of(100, ChronoUnit.MILLIS));
        int timerMetricCount = 3;
        int totalMetricCount = counterMetricCount + timerMetricCount;
        OciMetricsData ociMetricsData = new OciMetricsData(
                Meter.Scope.BUILT_IN_SCOPES, nameFormatter, "compartmentId", "namespace", "resourceGroup", false);
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        allMetricDataDetails.stream().forEach((c) -> {
            if (c.getName().contains(counterName)) {
                assertThat(c.getDimensions().get(dimensionScopeName), is(equalTo(Meter.Scope.BASE)));
            } else if (c.getName().contains(timerName)) {
                assertThat(c.getDimensions().get(dimensionScopeName), is(equalTo(Meter.Scope.APPLICATION)));
            }
            else {
                fail("Unknown metric: " + c.getName());
            }
        });
        assertThat(allMetricDataDetails.size(), is(equalTo(totalMetricCount)));
    }

    @Test
    void testOciMonitoringParameters() {
        String compartmentId = "dummy.compartmentId";
        String namespace = "dummy-namespace";
        String resourceGroup = "dummy_resourceGroup";

        meterRegistry.getOrCreate(Counter.builder("dummy.counter")
                                          .scope(Meter.Scope.BASE))
                .increment();

        OciMetricsData ociMetricsData = new OciMetricsData(
                Set.of(Meter.Scope.BASE), nameFormatter, compartmentId, namespace, resourceGroup, false);
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        MetricDataDetails metricDataDetails = allMetricDataDetails.get(0);
        assertThat(metricDataDetails.getCompartmentId(), is(equalTo(compartmentId)));
        assertThat(metricDataDetails.getNamespace(), is(equalTo(namespace)));
        assertThat(metricDataDetails.getResourceGroup(), is(equalTo(resourceGroup)));
    }

    @Test
    void testDimensions() {
        String dummyTagName = "DummyTag";
        String dummyTagValue = "DummyValue";

        meterRegistry.getOrCreate(Counter.builder("dummy.counter")
                                          .scope(Meter.Scope.BASE)
                                          .tags(Set.of(Tag.create(dummyTagName, dummyTagValue))))
                        .increment();
        OciMetricsData ociMetricsData = new OciMetricsData(
                Set.of(Meter.Scope.BASE), nameFormatter, "compartmentId", "namespace", "resourceGroup", false);
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        MetricDataDetails metricDataDetails = allMetricDataDetails.get(0);
        Map<String, String> dimensions = metricDataDetails.getDimensions();
        assertThat(dimensions.get(dimensionScopeName), is(equalTo(Meter.Scope.BASE)));
        assertThat(dimensions.get(dummyTagName), is(equalTo(dummyTagValue)));
    }
}
