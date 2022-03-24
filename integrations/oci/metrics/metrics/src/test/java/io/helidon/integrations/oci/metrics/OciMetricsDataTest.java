/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.integrations.oci.metrics;

import org.eclipse.microprofile.metrics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.metrics.api.RegistryFactory;

import org.eclipse.microprofile.metrics.MetricRegistry.Type;

import com.oracle.bmc.monitoring.model.MetricDataDetails;

public class OciMetricsDataTest {
    private final OciMetricsSupport.NameFormatter nameFormatter = new OciMetricsSupport.NameFormatter() { };
    private Type[] types = {Type.BASE, Type.VENDOR, Type.APPLICATION};
    private RegistryFactory registryFactory = RegistryFactory.getInstance();

    @BeforeEach
    private void beforeEach() {
        // clear all registry
        for (Type type: types) {
            MetricRegistry metricRegistry = registryFactory.getRegistry(type);
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
        String meterName = "DummyMeter";
        String timerName = "DummyTimer";

        Map<MetricRegistry, Type> metricRegistries = new HashMap<>();
        RegistryFactory rf = RegistryFactory.getInstance();
        MetricRegistry baseMetricRegistry = rf.getRegistry(Type.BASE);
        MetricRegistry vendorMetricRegistry = rf.getRegistry(Type.VENDOR);
        MetricRegistry appMetricRegistry = rf.getRegistry(Type.APPLICATION);
        metricRegistries.put(baseMetricRegistry, Type.BASE);
        metricRegistries.put(vendorMetricRegistry, Type.VENDOR);
        metricRegistries.put(appMetricRegistry, Type.APPLICATION);
        baseMetricRegistry.counter(counterName).inc();
        vendorMetricRegistry.meter(meterName).mark();
        appMetricRegistry.timer(timerName).time();
        System.out.println("MetricRegistry is " + baseMetricRegistry);
        OciMetricsData ociMetricsData = new OciMetricsData(
                metricRegistries, nameFormatter, "compartmentId", "namespace", "resourceGroup", false);
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        allMetricDataDetails.stream().forEach((c) -> {
            System.out.println(c.getName());
            if (c.getName().contains(counterName)) {
                assertThat(c.getDimensions().get(OciMetricsData.dimensionScopeName), is(equalTo(Type.BASE.getName())));
            } else if (c.getName().contains(meterName)) {
                assertThat(c.getDimensions().get(OciMetricsData.dimensionScopeName), is(equalTo(Type.VENDOR.getName())));
            } else if (c.getName().contains(timerName)) {
                assertThat(c.getDimensions().get(OciMetricsData.dimensionScopeName), is(equalTo(Type.APPLICATION.getName())));
            }
            else {
                fail("Unknown metric: " + c.getName());
            }
        });
        assertThat(allMetricDataDetails.size(), is(equalTo(3)));
    }

    @Test
    public void testOciMonitoringParameters() {
        String compartmentId = "dummy.compartmentId";
        String namespace = "dummy-namespace";
        String resourceGroup = "dummy_resourceGroup";

        Map<MetricRegistry, Type> metricRegistries = new HashMap<>();
        MetricRegistry metricRegistry = registryFactory.getRegistry(Type.BASE);
        metricRegistry.counter("dummy.counter").inc();
        metricRegistries.put(registryFactory.getRegistry(Type.BASE), Type.BASE);
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

        Map<MetricRegistry, Type> metricRegistries = new HashMap<>();
        MetricRegistry metricRegistry = registryFactory.getRegistry(Type.BASE);
        metricRegistry.counter("dummy.counter", new Tag(dummyTagName, dummyTagValue)).inc();
        metricRegistries.put(registryFactory.getRegistry(Type.BASE), Type.BASE);
        OciMetricsData ociMetricsData = new OciMetricsData(
                metricRegistries, nameFormatter, "compartmentId", "namespace", "resourceGroup", false);
        List<MetricDataDetails> allMetricDataDetails = ociMetricsData.getMetricDataDetails();
        MetricDataDetails metricDataDetails = allMetricDataDetails.get(0);
        Map<String, String> dimensions = metricDataDetails.getDimensions();
        assertThat(dimensions.get(OciMetricsData.dimensionScopeName), is(equalTo(Type.BASE.getName())));
        assertThat(dimensions.get(dummyTagName), is(equalTo(dummyTagValue)));
    }
}