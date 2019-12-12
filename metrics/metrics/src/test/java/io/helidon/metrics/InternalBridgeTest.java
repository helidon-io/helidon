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
 *
 */
package io.helidon.metrics;

import java.util.Map;
import java.util.Optional;

import io.helidon.common.metrics.InternalBridge;
import io.helidon.common.metrics.InternalBridge.MetricID;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 *
 */
public class InternalBridgeTest {

    private static InternalBridge ib;
    private static InternalBridge.MetricRegistry.RegistryFactory ibFactory;
    private static RegistryFactory factory;
    private static InternalBridge.MetricRegistry ibVendor;
    private static MetricRegistry vendor;
    private static InternalBridge.MetricRegistry ibApp;
    private static MetricRegistry app;

    public InternalBridgeTest() {
    }

    @BeforeAll
    private static void loadFactory() {
        ib = InternalBridge.INSTANCE;
        ibFactory = ib.getRegistryFactory();
        factory = RegistryFactory.getInstance();
        ibVendor = ibFactory.getBridgeRegistry(MetricRegistry.Type.VENDOR);
        vendor = factory.getRegistry(MetricRegistry.Type.VENDOR);
        ibApp = ibFactory.getBridgeRegistry(MetricRegistry.Type.APPLICATION);
        app = factory.getRegistry(MetricRegistry.Type.APPLICATION);
    }

    @Test
    public void testBridgeRegistryFactory() {
        assertSame(factory, ibFactory, "Factory and neutral factory do not match");
        assertSame(vendor, ibVendor, "Vendor registries via the two factories do not match");
    }

    @Test
    public void testTags() {
        String globalTags = System.getenv(Metadata.GLOBAL_TAGS_VARIABLE);
        String expectedTags = (globalTags == null ? "" : globalTags + ",") + "t1=one,t2=two";
        Metadata metadata = new Metadata("MyCounter", "MyCounter display",
                "This is a test counter", MetricType.COUNTER, MetricUnits.NONE,
                expectedTags);
        Counter counter = app.counter(metadata);

        Optional<Map.Entry<? extends MetricID, ? extends Metric>> lookedUpCounter = ibApp.getBridgeMetric("MyCounter");
        assertEquals(expectedTags,
                lookedUpCounter
                    .map(entry -> entry.getKey().getTagsAsString())
                    .orElse("Counter lookup failed"));

    }

}
