/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import java.time.Duration;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

public class TestNoOpRegistry {

    private static MetricRegistry appRegistry;

    @BeforeAll
    static void setUpRegistry() {
        RegistryFactory registryFactory = RegistryFactory.create(MetricsSettings.builder().enabled(false).build());
        appRegistry = registryFactory.getRegistry(Registry.APPLICATION_SCOPE);
    }

    @Test
    void checkUpdatesAreNoOps() {
        Timer timer = appRegistry.timer("testSimpleTimer");
        timer.update(Duration.ofSeconds(4));
        assertThat("Updated Timer count", timer.getCount(), is(0L));
    }

    @Test
    void checkDifferentInstances() {
        Timer timer = appRegistry.timer("testForUnsupportedOp");
        Timer otherTimer = appRegistry.timer("someOtherName");
        assertThat("Same metric instance for different names", otherTimer, is(not(sameInstance(timer))));
    }
}
