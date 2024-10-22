/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;
import org.junit.jupiter.api.AfterAll;

/**
 * Class MetricsMpServiceTest.
 */
@HelidonTest
@AddBean(HelloWorldResource.class)
public class MetricsMpServiceTest {

    @AfterAll
    public static void wrapupTest() {
        cleanUpSyntheticSimpleTimerRegistry();
    }

    static MetricRegistry cleanUpSyntheticSimpleTimerRegistry() {
        MetricRegistry result = RegistryFactory.getInstance().getRegistry(MetricRegistry.BASE_SCOPE);
        result.remove(MetricsCdiExtension.SYNTHETIC_TIMER_METRIC_NAME);
        return result;
    }

    @Inject
    private MetricRegistry registry;

    @Inject
    @RegistryScope(scope = MetricRegistry.BASE_SCOPE)
    private MetricRegistry baseRegistry;

    MetricRegistry syntheticTimerTimerRegistry() {
        return baseRegistry;
    }

    MetricRegistry registry() {
        return registry;
    }

    protected static void registerCounter(MetricRegistry registry, String name) {
        Metadata meta = Metadata.builder()
                        .withName(name)
                        .withDescription(name)
                        .withUnit(MetricUnits.NONE)
                        .build();
        registry.counter(meta);
    }

    protected void registerCounter(String name) {
        registerCounter(registry, name);
    }

    protected Counter getCounter(String name) {
        return registry.counter(name);
    }
}
