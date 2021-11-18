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
package io.helidon.metrics;

import io.helidon.metrics.api.MetricsSettings;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

class TestDisabledEntirely {

    @Test
    void testDisabledCompletely() {
        MetricsSettings metricsSettings = MetricsSettings.builder()
                .enabled(false)
                .build();

        io.helidon.metrics.api.RegistryFactory registryFactory =
                io.helidon.metrics.api.RegistryFactory.getInstance(metricsSettings);

        assertThat("Disabled RegistryFactory impl class",
                   registryFactory,
                   not(instanceOf(io.helidon.metrics.RegistryFactory.class)));
    }
}
