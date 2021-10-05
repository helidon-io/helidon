/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

public class TestNoOp {

    private static RegistryFactory factory;

    @BeforeAll
    static void setUpFactory() {
        factory = RegistryFactory.create(MetricsSettings.builder().enabled(false).build());
    }

    @Test
    void testCounterAndGetCounters() {
        MetricRegistry appRegistry = factory.getRegistry(MetricRegistry.Type.APPLICATION);

        Counter counter = appRegistry.counter("disabledCounter");
        counter.inc();

        assertThat("Updated counter's count", counter.getCount(), is(0L));
        assertThat("Expected metric in registry", appRegistry.counter("disabledCounter"), is(sameInstance(counter)));
    }
}
