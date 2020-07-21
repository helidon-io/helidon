/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link RegistryFactory}.
 */
public class RegistryFactoryTest {
    private static final MetricID METRIC_USED_HEAP = new MetricID("memory.usedHeap");

    private static RegistryFactory configured;
    private static RegistryFactory unconfigured;
    private static MetricRegistry baseUn;
    private static MetricRegistry appUn;
    private static MetricRegistry vendorUn;
    private static MetricRegistry base;
    private static MetricRegistry app;
    private static MetricRegistry vendor;
    private static Registry vendorMod;

    @BeforeAll
    static void createInstance() {
        unconfigured = RegistryFactory.create();
        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of(
                        "base." + METRIC_USED_HEAP.getName() + ".enabled",
                        "false")))
                .build();
        configured = RegistryFactory.create(config);

        baseUn = unconfigured.getRegistry(MetricRegistry.Type.BASE);
        appUn = unconfigured.getRegistry(MetricRegistry.Type.APPLICATION);
        vendorUn = unconfigured.getRegistry(MetricRegistry.Type.VENDOR);

        base = configured.getRegistry(MetricRegistry.Type.BASE);
        app = configured.getRegistry(MetricRegistry.Type.APPLICATION);
        vendor = configured.getRegistry(MetricRegistry.Type.VENDOR);

        vendorMod = configured.getARegistry(MetricRegistry.Type.VENDOR);
    }

    @Test
    void testBaseMetric() {
        Gauge gauge = base.getGauges().get(METRIC_USED_HEAP);
        assertThat(METRIC_USED_HEAP + " should be disabled for configured base registry", gauge, nullValue());

        gauge = baseUn.getGauges().get(METRIC_USED_HEAP);
        assertThat(METRIC_USED_HEAP + " should be available by default", gauge, notNullValue());
    }

    @Test
    void testVendorModifiable() {
        Counter c1 = vendor.counter("new.counter");
        Counter c2 = vendorUn.counter("new.counter");

        assertThat(c1, notNullValue());
        assertThat(c2, notNullValue());
        assertNotSame(c1, c2);

        //replace c2 with a counter from the same registry
        c2 = vendor.counter("new.counter");
        assertSame(c1, c2);
    }

    @Test
    void testAppModifiable() {
        Counter c1 = app.counter("new.counter");
        Counter c2 = appUn.counter("new.counter");

        assertThat(c1, notNullValue());
        assertThat(c2, notNullValue());
        assertNotSame(c1, c2);

        //replace c2 with a counter from the same registry
        c2 = app.counter("new.counter");
        assertSame(c1, c2);
    }

    @Test
    void testPackageVendorModifiable() {
        Counter c1 = vendorMod.counter("new.counter");
        assertThat(c1, notNullValue());
        c1.inc();
        assertThat(c1.getCount(), is(1L));
    }
}
