/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.metrics;

import java.util.Map;

import io.helidon.metrics.api.RegistrySettings;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.hamcrest.core.IsSame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link Registry}.
 */
public class RegistryTest {

    private static Registry registry;

    private final static Tag tag1 = new Tag("name1", "value1");
    private final static Tag tag1v2 = new Tag("name1", "value2");
    private final static Tag tag2 = new Tag("name2", "value2");

    @BeforeAll
    static void createInstance() {
        registry = new Registry(MetricRegistry.BASE_SCOPE, RegistrySettings.create());
    }

    @Test
    void testSameIDAndType() {
        Counter c1 = registry.counter("counter", tag1);
        Counter c2 = registry.counter("counter", tag1);
        assertThat(c1, IsSame.sameInstance(c2));
    }

    @Test
    void testSameIDDifferentType() {
        registry.counter("counter1", tag1);
        assertThrows(IllegalArgumentException.class, () -> registry.timer("counter1", tag1));
    }

    @Test
    void testSameNameDifferentTagsDifferentTypes() {
        Metadata metadata1 = Metadata.builder()
                    .withName("counter2")
                    .build();
        Metadata metadata2 = Metadata.builder()
                    .withName("counter2")
                    .build();
        registry.counter(metadata1, tag1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.timer(metadata2, tag2));
        assertThat(ex.getMessage(), containsString("Inconsistent"));
    }

    @Test
    void testSameIDSameReuseDifferentOtherMetadata() {
        Metadata metadata1 = Metadata.builder()
				.withName("counter5")
				.withDescription("description")
				.withUnit(MetricUnits.NONE)
				.build();
        Metadata metadata2 = Metadata.builder()
				.withName("counter5")
				.withDescription("other description")
				.withUnit(MetricUnits.NONE)
				.build();

        registry.counter(metadata1, tag1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.counter(metadata2, tag1));
        assertThat(ex.getMessage(), containsString("conflicts with"));
    }

    @Test
    void testRemovalOfExistingMetricByName() {
        Metadata metadata = Metadata.builder()
                .withName("counter6")
                .build();
        registry.counter(metadata, tag1);
        registry.counter(metadata, tag1v2);
        // Removing by name should remove all metrics with that name, regardless of tags.
        boolean result = registry.remove(metadata.getName());
        assertThat(result, is(true));
        Map<MetricID, Counter> counters = registry.getCounters(new MetricNameFilter(metadata.getName()));
        assertThat(counters.size(), is(0));
    }

    @Test
    void testRemovalOfExistingMetricByMetricID() {
        Metadata metadata = Metadata.builder()
                .withName("counter7")
                .build();

        registry.counter(metadata, tag1);
        registry.counter(metadata, tag1v2);
        MetricID metricID = new MetricID(metadata.getName(), tag1);
        // Removing by MetricID should leave other like-named metrics intact.
        boolean result = registry.remove(metricID);
        assertThat(result, is(true));
        Map<MetricID, Counter> counters = registry.getCounters(new MetricNameFilter(metadata.getName()));
        assertThat(counters.size(), is(1));
    }

    @Test
    void testRemovalOfMissingMetricByName() {
        boolean result = registry.remove("NOT_THERE");
        assertThat(result, is(false));
    }

    @Test
    void testRemovalOfMissingMetricByID() {
        MetricID metricID = new MetricID("NOT_THERE");
        boolean result = registry.remove(metricID);
        assertThat(result, is(false));
    }

    @Test
    void testGetCounterWithoutCreate() {
        MetricID metricID = new MetricID("counter8", tag1);
        Counter shouldBeMissing = registry.getCounter(metricID);
        assertThat("Counter get without create", shouldBeMissing, is(nullValue()));
    }

    @Test
    void testGetCounterAfterCreate() {
        MetricID metricID = new MetricID("counter9", tag1);
        Counter counter = registry.counter(metricID);
        Counter shouldBePresent = registry.getCounter(metricID);
        assertThat("Counter get after create", shouldBePresent, is(sameInstance(counter)));
    }

    @Test
    void testGetType() {
        assertThat("Registry type", registry.getScope(), is(MetricRegistry.BASE_SCOPE));

        MetricRegistry vendorRegistry = io.helidon.metrics.api.RegistryFactory.getInstance()
                .getRegistry(MetricRegistry.VENDOR_SCOPE);
        assertThat("Registry type for vendor registry", vendorRegistry.getScope(), is(MetricRegistry.VENDOR_SCOPE));

        MetricRegistry baseRegistry = io.helidon.metrics.api.RegistryFactory.getInstance()
                .getRegistry(MetricRegistry.BASE_SCOPE);
        assertThat("Registry type for base registry", baseRegistry.getScope(), is(MetricRegistry.BASE_SCOPE));
    }

    @Test
    void testGetMetric() {
        MetricID metricID = new MetricID("counter10", tag1);
        Counter counter = registry.counter(metricID);

        MetricID otherMetricID = new MetricID("counter10", tag1);
        Metric retrievedMetric = registry.getMetric(otherMetricID);
        assertThat("Retrieved metric", retrievedMetric, is(sameInstance(counter)));

        Counter retrievedCounter = registry.getMetric(otherMetricID, Counter.class);
        assertThat("Retrieved counter", retrievedCounter, is(sameInstance(counter)));
    }

    @Test
    void testGetMetricWithoutCreate() {
        MetricID metricID = new MetricID("NOT_THERE");
        Metric missingMetric = registry.getMetric(metricID);
        assertThat("Missing metric", missingMetric, is(nullValue()));
    }

    @Test
    void testGetMetadata() {
        Metadata metadata = Metadata.builder()
                .withName("counter11")
                .build();

        Counter counter = registry.counter(metadata, tag1);

        Metadata retrievedMetadata = registry.getMetadata("counter11");
        assertThat("Retrieved metadata", retrievedMetadata, is(equalTo(metadata)));
    }

    @Test
    void testGetMetadataWithoutCreate() {
        Metadata retrievedMetadata = registry.getMetadata("NOT_THERE");
        assertThat("Missing metadata", retrievedMetadata, is(nullValue()));
    }

    @Test
    void testGetMetricsWithFilter() {
        MetricID metricIDa = new MetricID("counter12", tag1);
        MetricID metricIDb = new MetricID("counter13", tag1);

        Counter counterA = registry.counter(metricIDa);
        Counter counterB = registry.counter(metricIDb);

        Map<MetricID, Metric> matchingMetrics = registry.getMetrics(new MetricNameFilter(metricIDb.getName()));
        assertThat("Metrics matching filter", matchingMetrics, hasEntry(metricIDb, counterB));
        assertThat("Metrics not matching filter", matchingMetrics, not(hasEntry(metricIDa, counterA)));
    }

    @Test
    void testGetMetricsWithFilterAndType() {
        MetricID metricIDa = new MetricID("counter14", tag1);
        MetricID metricIDb = new MetricID("simpleTimer1", tag1);

        Counter counter = registry.counter(metricIDa);
        Timer simpleTimer = registry.timer(metricIDb);

        Map<MetricID, Timer> matchingMetrics = registry.getMetrics(Timer.class,
                                                                   new MetricNameFilter(metricIDb.getName()));
        assertThat("Metrics matching filter", matchingMetrics, hasEntry(metricIDb, simpleTimer));
        assertThat("Metrics not matching filter", matchingMetrics, not(hasEntry(metricIDa, counter)));
    }

    private static class MetricNameFilter implements MetricFilter {

        private final String name;

        private MetricNameFilter(String name) {
            this.name = name;
        }

        @Override
        public boolean matches(MetricID metricID, Metric metric) {
            return metricID.getName().equals(name);
        }
    }
}
