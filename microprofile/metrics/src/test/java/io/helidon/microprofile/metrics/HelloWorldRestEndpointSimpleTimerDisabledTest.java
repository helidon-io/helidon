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

package io.helidon.microprofile.metrics;

import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Makes sure that no synthetic SimpleTimer metrics are created for JAX-RS endpoints when
 * the config disables that feature.
 *
 * Currently, by default, Helidon does not enable the feature to generate a MP REST.request SimpleTimer for each
 * JAX-RS endpoint. So without setting configuration to enable that feature explicitly, we should not find any
 * metrics named REST.request in the BASE registry (where these synthetic metrics reside, according to the MP metrics spec).
 */
public class HelloWorldRestEndpointSimpleTimerDisabledTest extends HelloWorldTest {

    @BeforeAll
    public static void initializeServer() {
        HelloWorldTest.initializeServer(new Properties());
    }

    @Test
    public void testSyntheticSimpleTimer() {
        Map<MetricID,SimpleTimer> restEndpointSimplyTimedMetrics = MetricsCdiExtension.getRegistryForSyntheticSimpleTimers()
                .getSimpleTimers(new MetricFilter() {

                    @Override
                    public boolean matches(MetricID metricID, Metric metric) {
                        return metricID.getName().equals(MetricsCdiExtension.SYNTHETIC_SIMPLE_TIMER_METRIC_NAME);
                    }
                });

        assertThat("Automatic REST endpoint simple timers were disabled by config but were created anyway",
                restEndpointSimplyTimedMetrics.isEmpty(), is(true));
    }
}
