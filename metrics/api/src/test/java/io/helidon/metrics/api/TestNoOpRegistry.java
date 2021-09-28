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

import java.time.Duration;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.junit.jupiter.api.Assertions;
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
        RegistryFactory registryFactory = RegistryFactory.create(MetricsSettings.builder().enable(false).build());
        appRegistry = registryFactory.getRegistry(MetricRegistry.Type.APPLICATION);
    }

    @Test
    void checkUpdatesAreNoOps() {
        SimpleTimer simpleTimer = appRegistry.simpleTimer("testSimpleTimer");
        simpleTimer.update(Duration.ofSeconds(4));
        assertThat("Updated SimpleTimer count", simpleTimer.getCount(), is(0L));
    }

    @Test
    void checkDifferentInstances() {
        SimpleTimer simplerTimer = appRegistry.simpleTimer("testForUnsupportedOp");
        SimpleTimer otherSimpleTimer = appRegistry.simpleTimer("someOtherName");
        assertThat("Same metric instance for different names", otherSimpleTimer, is(not(sameInstance(simplerTimer))));
    }

    @Test
    void checkRegisterExistingMetric() {
        Metadata metadata = Metadata.builder()
                .withName("myOwnSimpleTimer")
                .withType(MetricType.SIMPLE_TIMER)
                .build();
        String metricName = "myOwnSimpleTimer";
        SimpleTimer simpleTimer = NoOpMetric.NoOpSimpleTimer.create(MetricRegistry.Type.APPLICATION.getName(), metadata);
        appRegistry.register(metricName, simpleTimer);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> appRegistry.register(metricName, simpleTimer),
                "Expected failure due to duplicate metric registration");
    }
}
