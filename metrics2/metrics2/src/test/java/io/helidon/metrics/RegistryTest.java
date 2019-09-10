/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

import org.hamcrest.core.IsSame;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        Metadata metadata1 = new HelidonMetadata("counter2", MetricType.COUNTER);
        Metadata metadata2 = new HelidonMetadata("counter2", MetricType.TIMER);
        registry.counter(metadata1, tag1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.timer(metadata2, tag2));
        assertThat(ex.getMessage(), containsString("already registered"));
    }

    @Test
    void testIncompatibleReuseNoTags() {
        Metadata metadata1 = new HelidonMetadata("counter3", "display name",
                "description", MetricType.COUNTER, MetricUnits.NONE, true);
        Metadata metadata2 = new HelidonMetadata("counter3", "display name",
                "description", MetricType.COUNTER, MetricUnits.NONE, false);

        registry.counter(metadata1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.counter(metadata2));
        assertThat(ex.getMessage(), containsString("already registered"));
    }

    @Test
    void testIncompatibleReuseWithTags() {
        Metadata metadata1 = new HelidonMetadata("counter4", "display name",
                "description", MetricType.COUNTER, MetricUnits.NONE, true);
        Metadata metadata2 = new HelidonMetadata("counter4", "display name",
                "description", MetricType.COUNTER, MetricUnits.NONE, false);

        registry.counter(metadata1, tag1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.counter(metadata2, tag1));
        assertThat(ex.getMessage(), containsString("already registered"));
    }

    @Test
    void testSameIDSameReuseDifferentOtherMetadata() {
        Metadata metadata1 = new HelidonMetadata("counter5", "display name",
                "description", MetricType.COUNTER, MetricUnits.NONE, true);
        Metadata metadata2 = new HelidonMetadata("counter5", "OTHER display name",
                "description", MetricType.COUNTER, MetricUnits.NONE, true);

        registry.counter(metadata1, tag1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.counter(metadata2, tag1));
        assertThat(ex.getMessage(), containsString("conflicts with"));
    }

    @Test
    void testInvalidReregistration() {
        Metadata metadata1 = new HelidonMetadata("counter6", "display name",
        "description", MetricType.COUNTER, MetricUnits.NONE, false);
        registry.counter(metadata1, tag1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.counter(metadata1,tag1));
        assertThat(ex.getMessage(), containsString("re-register"));
        assertThat(ex.getMessage(), containsString("already registered as non-reusable"));
    }

    @Test
    void testCompatibleFlexibleMetadata() {
        Metadata flex1 = HelidonMetadata.newFlexible("counter7", "random DN", "random descr",
                MetricType.TIMER, MetricUnits.MINUTES);
        Metadata flex2 = HelidonMetadata.newFlexible("counter7", "other DN", "other descr",
                MetricType.TIMER, MetricUnits.HOURS);
        Metadata hard = new HelidonMetadata("counter7", "my DN", "my descr", MetricType.TIMER, MetricUnits.DAYS);

        assertThat(Registry.metadataMatches(flex1, hard), is(true));
        assertThat(Registry.metadataMatches(hard, flex1), is(true));
        assertThat(Registry.metadataMatches(flex1, flex2), is(true));
    }

    @Test
    void testIncompatibleFlexibleMetadata() {
        Metadata flex1 = HelidonMetadata.newFlexible("differentName", "random DN", "random descr",
                MetricType.TIMER, MetricUnits.MINUTES);
        Metadata flex2 = HelidonMetadata.newFlexible("myMetric", "other DN", "other descr",
                MetricType.COUNTER, MetricUnits.NONE);
        Metadata hard = new HelidonMetadata("myMetric", "my DN", "my descr", MetricType.TIMER, MetricUnits.DAYS);

        assertThat(Registry.metadataMatches(flex1, hard), is(false));
        assertThat(Registry.metadataMatches(hard, flex1), is(false));
        assertThat(Registry.metadataMatches(flex2, hard), is(false));
        assertThat(Registry.metadataMatches(flex1, flex2), is(false));
    }
}