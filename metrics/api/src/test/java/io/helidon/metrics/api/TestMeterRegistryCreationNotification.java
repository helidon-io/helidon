/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestMeterRegistryCreationNotification {
    @Test
    void legacyProviderDelegatesWithoutClaimingCreation() {
        // The default overload cannot distinguish creation from reuse, so a legacy provider must not grant ownership.
        AtomicInteger customizations = new AtomicInteger();
        AtomicInteger creations = new AtomicInteger();
        var factory = NoOpMetricsFactory.create(MetricsConfig.create(),
                                               List.of(_ -> customizations.incrementAndGet()));
        try {
            MeterRegistry registry = factory.globalRegistry();
            Counter counter = registry.getOrCreate(factory.counterBuilder("legacy"), _ -> creations.incrementAndGet());

            assertThat(counter.id().name(), is("legacy"));
            assertThat(customizations.get(), is(1));
            assertThat(creations.get(), is(0));
            assertThrows(NullPointerException.class, () -> registry.getOrCreate(factory.counterBuilder("null"), null));
        } finally {
            factory.close();
        }
    }
}
