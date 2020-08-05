/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.hamcrest.core.IsSame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link Registry}.
 */
public class RegistryTest {

    private static Registry registry;

    private final static Tag tag1 = new Tag("name1", "value1");
    private final static Tag tag2 = new Tag("name2", "value2");

    @BeforeAll
    static void createInstance() {
        registry = new Registry(MetricRegistry.Type.BASE);
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
        assertThrows(IllegalArgumentException.class, () -> registry.meter("counter1", tag1));
    }

    @Test
    void testSameNameDifferentTagsDifferentTypes() {
        Metadata metadata1 = Metadata.builder()
                    .withName("counter2")
                    .withType(MetricType.COUNTER)
                    .build();
        Metadata metadata2 = Metadata.builder()
                    .withName("counter2")
                    .withType(MetricType.TIMER)
                    .build();
        registry.counter(metadata1, tag1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.timer(metadata2, tag2));
        assertThat(ex.getMessage(), containsString("already registered"));
    }

    @Test
    void testIncompatibleReuseNoTags() {
        Metadata metadata1 = Metadata.builder()
                    .withName("counter3")
                    .withDisplayName("display name")
                    .withDescription("description")
                    .withType(MetricType.COUNTER)
                    .withUnit(MetricUnits.NONE)
                    .reusable(true)
                    .build();
        Metadata metadata2 = Metadata.builder()
                    .withName("counter3")
                    .withDisplayName("display name")
                    .withDescription("description")
                    .withType(MetricType.COUNTER)
                    .withUnit(MetricUnits.NONE)
                    .reusable(false)
                    .build();

        registry.counter(metadata1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.counter(metadata2));
        assertThat(ex.getMessage(), containsString("already registered"));
    }

    @Test
    void testIncompatibleReuseWithTags() {
        Metadata metadata1 = Metadata.builder()
				.withName("counter4")
				.withDisplayName("display name")
				.withDescription("description")
				.withType(MetricType.COUNTER)
				.withUnit(MetricUnits.NONE)
				.reusable(true)
				.build();
        Metadata metadata2 = Metadata.builder()
				.withName("counter4")
				.withDisplayName("display name")
				.withDescription("description")
				.withType(MetricType.COUNTER)
				.withUnit(MetricUnits.NONE)
				.reusable(false)
				.build();

        registry.counter(metadata1, tag1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.counter(metadata2, tag1));
        assertThat(ex.getMessage(), containsString("already registered"));
    }

    @Test
    void testSameIDSameReuseDifferentOtherMetadata() {
        Metadata metadata1 = Metadata.builder()
				.withName("counter5")
				.withDisplayName("display name")
				.withDescription("description")
				.withType(MetricType.COUNTER)
				.withUnit(MetricUnits.NONE)
				.reusable(true)
				.build();
        Metadata metadata2 = Metadata.builder()
				.withName("counter5")
				.withDisplayName("OTHER display name")
				.withDescription("description")
				.withType(MetricType.COUNTER)
				.withUnit(MetricUnits.NONE)
				.reusable(true)
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
                .withType(MetricType.COUNTER)
                .build();
        registry.counter(metadata);
        registry.counter(metadata, tag1);
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
                .withType(MetricType.COUNTER)
                .build();

        registry.counter(metadata);
        registry.counter(metadata, tag1);
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
