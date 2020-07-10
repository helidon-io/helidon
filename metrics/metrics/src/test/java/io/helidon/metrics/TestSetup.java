/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import org.eclipse.microprofile.metrics.MetricType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestSetup {

    @Test
    void testMetricToTypeMapForCompleteness() {
        for (MetricType mt : MetricType.values()) {
            if (!Registry.metricToTypeMap().containsValue(mt) && mt != MetricType.INVALID) {
                Assertions.fail("MetricType " + mt.name() + " is not represented in Registry.METRIC_TO_TYPE_MAP");
            }
        }
    }
}
