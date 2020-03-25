/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
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
}
