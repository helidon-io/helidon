/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import org.eclipse.microprofile.metrics.MetricType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestSetup {

    @Test
    void testMetricToTypeMapForCompleteness() {
        // Attempts to detect if a new metric type has been added but we haven't fully implemented it.
        Registry registry = (Registry)
                io.helidon.metrics.api.RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);
        for (MetricType mt : MetricType.values()) {
            if (!registry.metricFactories().containsKey(mt) && mt != MetricType.INVALID && mt != MetricType.GAUGE) {
                Assertions.fail("MetricType " + mt.name() + " is not represented in Registry metricFactories map");
            }
        }
    }
}
