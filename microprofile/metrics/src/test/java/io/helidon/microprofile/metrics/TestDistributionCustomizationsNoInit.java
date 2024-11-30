/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

class TestDistributionCustomizationsNoInit {

    private static MetricRegistry metricRegistry;

    @BeforeAll
    static void initRegistry() {
        metricRegistry = RegistryFactory.getInstance().getRegistry(MetricRegistry.APPLICATION_SCOPE);
    }

    @Test
    void checkDistributionCustomizations() {
        // Without the change, the following triggers an NPE because this test does not use @HelidonTest
        // and therefore the normal metrics CDI extension initialization code --which sets up the distribution
        // customizations -- does not run.
        Timer timer = metricRegistry.timer("testTimer");
        assertThat("Timer", timer, notNullValue());
    }
}
