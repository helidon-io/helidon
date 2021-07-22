/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Makes sure that no synthetic SimpleTimer metrics are created for JAX-RS endpoints when
 * the config disables that feature.
 */
@HelidonTest
public class HelloWorldRestEndpointSimpleTimerDisabledTest {

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    MetricRegistry syntheticSimpleTimerRegistry;

    boolean isSyntheticSimpleTimerPresent() {
        return !syntheticSimpleTimerRegistry.getSimpleTimers((metricID, metric) ->
                metricID.equals(MetricsCdiExtension.SYNTHETIC_SIMPLE_TIMER_METRIC_NAME))
                .isEmpty();
    }

    @Test
    public void testSyntheticSimpleTimer() {
        assertThat("Synthetic simple timer for JAX-RS was created when that feature is turned off",
                isSyntheticSimpleTimerPresent(), is(false));
    }
}
