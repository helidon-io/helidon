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
package io.helidon.metrics.api;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricStoreTests {

    private static final RegistrySettings REGISTRY_SETTINGS = RegistrySettings.create();

    private static final Tag[] NO_TAGS = new Tag[0];

    @Test
    void testConflictingMetadata() {
        Metadata meta1 = Metadata.builder()
                .withName("a")
                .build();

        Metadata meta2 = Metadata.builder(meta1)
                .build();

        NoOpMetricRegistry registry = NoOpMetricRegistry.create(MetricRegistry.APPLICATION_SCOPE);

        MetricStore store = MetricStore.create(REGISTRY_SETTINGS,
                                               new NoOpMetricFactory(),
                                               MetricRegistry.APPLICATION_SCOPE,
                                               registry::doRemove);

        store.getOrRegisterMetric(meta1, Timer.class, NO_TAGS);

        assertThrows(IllegalArgumentException.class, () ->
                store.getOrRegisterMetric(meta2, Counter.class, NO_TAGS));
    }

    @Test
    void testSameNameNoTags() {
        Metadata metadata = Metadata.builder()
                .withName("a")
                .build();

        NoOpMetricRegistry registry = NoOpMetricRegistry.create(MetricRegistry.APPLICATION_SCOPE);

        MetricStore store = MetricStore.create(REGISTRY_SETTINGS,
                                               new NoOpMetricFactory(),
                                               MetricRegistry.APPLICATION_SCOPE,
                                               registry::doRemove);

        Counter counter1 = store.getOrRegisterMetric(metadata, Counter.class, NO_TAGS);
        Counter counter2 = store.getOrRegisterMetric(metadata, Counter.class, NO_TAGS);
        assertThat("Counters with no tags", counter1, is(counter2));
    }

    @Test
    void testSameNameSameTwoTags() {
        Tag[] tags = {new Tag("foo", "1"), new Tag("bar", "1")};
        Metadata metadata = Metadata.builder()
                .withName("a")
                .build();

        NoOpMetricRegistry registry = NoOpMetricRegistry.create(MetricRegistry.APPLICATION_SCOPE);

        MetricStore store = MetricStore.create(REGISTRY_SETTINGS,
                                               new NoOpMetricFactory(),
                                               MetricRegistry.APPLICATION_SCOPE,
                                               registry::doRemove);

        Counter counter1 = store.getOrRegisterMetric(metadata, Counter.class, tags);
        Counter counter2 = store.getOrRegisterMetric(metadata, Counter.class, tags);
        assertThat("Counters with same two tags", counter1, is(counter2));
    }

    @Test
    void testSameNameOverlappingButDifferentTags() {
        Tag[] tags1 = {new Tag("foo", "1"), new Tag("bar", "1"), new Tag("baz", "1")};
        Tag[] tags2 = {new Tag("foo", "1"), new Tag("bar", "1")};
        Metadata metadata = Metadata.builder()
                .withName("a")
                .build();

        NoOpMetricRegistry registry = NoOpMetricRegistry.create(MetricRegistry.APPLICATION_SCOPE);

        MetricStore store = MetricStore.create(REGISTRY_SETTINGS,
                                               new NoOpMetricFactory(),
                                               MetricRegistry.APPLICATION_SCOPE,
                                               registry::doRemove);

        store.getOrRegisterMetric(metadata, Counter.class, tags1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () ->store.getOrRegisterMetric(metadata, Counter.class, tags2));
        assertThat("Exception due to inconsistent tag sets", ex.getMessage(), containsString("Inconsistent"));
    }
}
