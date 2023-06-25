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

import org.eclipse.microprofile.metrics.*;
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

import io.helidon.metrics.Registry;
import io.helidon.metrics.api.RegistryFactory;

import org.eclipse.microprofile.metrics.MetricRegistry.Type;

import com.oracle.bmc.monitoring.model.MetricDataDetails;

public class OciMetricsDataTest {
    private final OciMetricsSupport.NameFormatter nameFormatter = new OciMetricsSupport.NameFormatter() { };
    private final String[] types = {Registry.BASE_SCOPE, Registry.VENDOR_SCOPE, Registry.APPLICATION_SCOPE};
    private final String dimensionScopeName = "scope";

    private final RegistryFactory rf = RegistryFactory.getInstance();
    private final MetricRegistry baseMetricRegistry = rf.getRegistry(Registry.BASE_SCOPE);
    private final MetricRegistry vendorMetricRegistry = rf.getRegistry(Registry.VENDOR_SCOPE);
    private final MetricRegistry appMetricRegistry = rf.getRegistry(Registry.APPLICATION_SCOPE);

    @BeforeEach
    private void beforeEach() {
        // clear all registry
        for (String type: types) {
            MetricRegistry metricRegistry = rf.getRegistry(type);
            metricRegistry.removeMatching(new MetricFilter() {
                @Override
                public boolean matches(MetricID metricID, Metric metric) {
                    return true;
                }
            });
        }
    }

    @Test
    public void testMetricRegistries() {
        String counterName = "DummyCounter";
        String timerName = "DummyTimer";

        Map<MetricRegistry, String> metricRegistries = new HashMap<>();
        metricRegistries.put(baseMetricRegistry, Registry.BASE_SCOPE);
        metricRegistries.put(vendorMetricRegistry, Registry.VENDOR_SCOPE);
        metricRegistries.put(appMetricRegistry, Registry.APPLICATION_SCOPE);
        baseMetricRegistry.counter(counterName).inc();
        int counterMetricCount = 1;
        appMetricRegistry.timer(timerName).update(Duration.of(100, ChronoUnit.MILLIS));
        int timerMetricCount = 4;
        int totalMetricCount = counterMetricCount + timerMetricCount;
        OciMetricsData ociMetricsData = new OciMetricsData(
                metricRegistries, nameFormatter, "compartmentId", "namespace", "resourceGroup", false);
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        allMetricDataDetails.stream().forEach((c) -> {
            if (c.getName().contains(counterName)) {
                assertThat(c.getDimensions().get(dimensionScopeName), is(equalTo(Type.BASE.getName())));
            } else if (c.getName().contains(timerName)) {
                assertThat(c.getDimensions().get(dimensionScopeName), is(equalTo(Type.APPLICATION.getName())));
            }
            else {
                fail("Unknown metric: " + c.getName());
            }
        });
        assertThat(allMetricDataDetails.size(), is(equalTo(totalMetricCount)));
    }

    @Test
    public void testOciMonitoringParameters() {
        String compartmentId = "dummy.compartmentId";
        String namespace = "dummy-namespace";
        String resourceGroup = "dummy_resourceGroup";

        Map<MetricRegistry, String> metricRegistries = new HashMap<>();
        baseMetricRegistry.counter("dummy.counter").inc();
        metricRegistries.put(baseMetricRegistry, Registry.BASE_SCOPE);
        OciMetricsData ociMetricsData = new OciMetricsData(
                metricRegistries, nameFormatter, compartmentId, namespace, resourceGroup, false);
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        MetricDataDetails metricDataDetails = allMetricDataDetails.get(0);
        assertThat(metricDataDetails.getCompartmentId(), is(equalTo(compartmentId)));
        assertThat(metricDataDetails.getNamespace(), is(equalTo(namespace)));
        assertThat(metricDataDetails.getResourceGroup(), is(equalTo(resourceGroup)));
    }

    @Test
    public void testDimensions() {
        String dummyTagName = "DummyTag";
        String dummyTagValue = "DummyValue";

        Map<MetricRegistry, String> metricRegistries = new HashMap<>();
        baseMetricRegistry.counter("dummy.counter", new Tag(dummyTagName, dummyTagValue)).inc();
        metricRegistries.put(baseMetricRegistry, Registry.BASE_SCOPE);
        OciMetricsData ociMetricsData = new OciMetricsData(
                metricRegistries, nameFormatter, "compartmentId", "namespace", "resourceGroup", false);
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        MetricDataDetails metricDataDetails = allMetricDataDetails.get(0);
        Map<String, String> dimensions = metricDataDetails.getDimensions();
        assertThat(dimensions.get(dimensionScopeName), is(equalTo(Registry.BASE_SCOPE)));
        assertThat(dimensions.get(dummyTagName), is(equalTo(dummyTagValue)));
    }
}
