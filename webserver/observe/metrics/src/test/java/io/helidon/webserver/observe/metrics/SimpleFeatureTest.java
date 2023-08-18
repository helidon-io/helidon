/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.observe.metrics;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

class SimpleFeatureTest {

// TODO remove once we do not have selective building based on the system property
    @Test
    void testSelection() {
        MetricsFeature metricsFeature = MetricsFeature.builder().build();
        assertThat("Feature selection", metricsFeature, not(instanceOf(MetricsFeature4.class)));

        try {
            System.setProperty("newMetricsAPI", "true");

            metricsFeature = MetricsFeature.builder().build();
            assertThat("Feature selection", metricsFeature, instanceOf(MetricsFeature4.class));
        } finally {
            System.clearProperty("newMetricsAPI");
        }
    }
}
