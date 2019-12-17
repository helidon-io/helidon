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

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

import io.helidon.common.metrics.InternalBridge;
import io.helidon.common.metrics.InternalBridge.Metadata;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
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

    private static io.helidon.common.metrics.InternalBridge ib;
    private static io.helidon.common.metrics.InternalBridge.MetricRegistry.RegistryFactory ibFactory;
    private static RegistryFactory factory;
    private static io.helidon.common.metrics.InternalBridge.MetricRegistry ibVendor;
    private static MetricRegistry vendor;
    private static io.helidon.common.metrics.InternalBridge.MetricRegistry ibApp;
    private static MetricRegistry app;

    public InternalBridgeTest() {
    }

    @BeforeAll
    private static void loadFactory() {
        ib = io.helidon.common.metrics.InternalBridge.INSTANCE;
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
        String globalTags = System.getenv(MetricID.GLOBAL_TAGS_VARIABLE);
        Map<String, String> expectedTags = new HashMap<>();
        expectedTags.put("t1", "one");
        expectedTags.put("t2", "two");

        if (globalTags != null) {
            Arrays.stream(globalTags.split(","))
                    .map(expr -> {
                        final int eq = expr.indexOf("=");
                        if (eq <= 0) {
                            return null;
                        }
                        String tag = expr.substring(0, eq);
                        String value = expr.substring(eq + 1);
                        return new AbstractMap.SimpleEntry<>(tag, value);
                    })
                    .filter(entry -> entry != null)
                    .forEach(entry -> expectedTags.put(entry.getKey(), entry.getValue()));

        }

        org.eclipse.microprofile.metrics.Tag[] expectedTagsArray =
                expectedTags.entrySet().stream()
                .map(entry -> new org.eclipse.microprofile.metrics.Tag(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList())
                .toArray(new org.eclipse.microprofile.metrics.Tag[0]);

        Metadata internalMetadata = InternalBridge.newMetadataBuilder()
                .withName("MyCounter")
                .withDisplayName("MyCounter display")
                .withDescription("This is a test counter")
                .withType(MetricType.COUNTER)
                .withUnit(MetricUnits.NONE)
                .build();

        org.eclipse.microprofile.metrics.Metadata metadata = new org.eclipse.microprofile.metrics.MetadataBuilder()
                .withName("MyCounter")
                .withDisplayName("MyCounter display")
                .withDescription("This is a test counter")
                .withType(MetricType.COUNTER)
                .withUnit(MetricUnits.NONE)
                .build();

        Counter counter = ibApp.counter(internalMetadata, expectedTags);

        org.eclipse.microprofile.metrics.MetricID expectedMetricID =
                new org.eclipse.microprofile.metrics.MetricID("MyCounter", expectedTagsArray);

        SortedMap<MetricID, Counter> matchedCounters = app.getCounters(
            (metricID, metric) -> metricID.getName().equals("MyCounter"));

        assertEquals(1, matchedCounters.size());
        assertEquals(counter, matchedCounters.get(expectedMetricID));

    }
}
