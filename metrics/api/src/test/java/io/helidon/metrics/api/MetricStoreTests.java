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
 */
package io.helidon.metrics.api;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricStoreTests {

    private static final RegistrySettings REGISTRY_SETTINGS = RegistrySettings.create();

    private static final Tag[] NO_TAGS = new Tag[0];

    @Test
    void testConflictingMetadata() {
        Metadata meta1 = Metadata.builder()
                .withName("a")
                .withType(MetricType.SIMPLE_TIMER)
                .build();

        Metadata meta2 = Metadata.builder(meta1)
                .withType(MetricType.COUNTER)
                .build();

        NoOpMetricRegistry registry = NoOpMetricRegistry.create(MetricRegistry.Type.APPLICATION);

        MetricStore<NoOpMetric> store = MetricStore.create(REGISTRY_SETTINGS,
                                                           NoOpMetricRegistry.NO_OP_METRIC_FACTORIES,
                                                           null,
                                                           null,
                                                           MetricRegistry.Type.APPLICATION,
                                                           NoOpMetric.class,
                                                           registry::toImpl);

        store.getOrRegisterMetric(meta1, SimpleTimer.class, NO_TAGS);

        assertThrows(IllegalArgumentException.class, () ->
                store.getOrRegisterMetric(meta2, Counter.class, NO_TAGS));
    }
}
