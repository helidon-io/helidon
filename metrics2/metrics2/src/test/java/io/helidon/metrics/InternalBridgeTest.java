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

import org.eclipse.microprofile.metrics.MetricRegistry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;

/**
 *
 */
public class InternalBridgeTest {

    private static io.helidon.common.metrics.InternalBridge ib;
    private static io.helidon.common.metrics.InternalBridge.MetricRegistry.RegistryFactory ibFactory;
    private static RegistryFactory factory;
    private static io.helidon.common.metrics.InternalBridge.MetricRegistry ibVendor;
    private static MetricRegistry vendor;

    public InternalBridgeTest() {
    }

    @BeforeAll
    private static void loadFactory() {
        ib = io.helidon.common.metrics.InternalBridge.INSTANCE;
        ibFactory = ib.getRegistryFactory();
        factory = RegistryFactory.getInstance();
        ibVendor = ibFactory.getBridgeRegistry(MetricRegistry.Type.VENDOR);
        vendor = factory.getRegistry(MetricRegistry.Type.VENDOR);
    }

    @Test
    public void testBridgeRegistryFactory() {
        assertSame(factory, ibFactory, "Factory and neutral factory do not match");
        assertSame(vendor, ibVendor, "Vendor registries via the two factories do not match");
    }
}
