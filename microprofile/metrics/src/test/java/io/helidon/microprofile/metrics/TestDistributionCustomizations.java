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

import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.greaterThan;

@HelidonTest
@AddConfig(key = "mp.metrics.distribution.percentiles-histogram.enabled",
           value = "alpha.*=true;alpha.nope=false")
class TestDistributionCustomizations {

    @Inject
    private MetricRegistry metricRegistry;

    @Test
    void checkDefaultedSummaryWithNoCustomization() {
        Histogram histogram = metricRegistry.histogram("alpha.useDefaults");
        /*
         Micrometer provides the buckets automatically. Just make sure there are some.
         */
        assertThat("alpha.useDefaults",
                   histogram.getSnapshot().bucketValues(),
                   arrayWithSize(greaterThan(0)));

        Timer timer = metricRegistry.timer("alpha.timer");
        assertThat("alpha.timer",
                   timer.getSnapshot().bucketValues(),
                   arrayWithSize(greaterThan(0)));
    }

    @Test
    void checkNoDefaultDueToExclusion() {
        Histogram histogram = metricRegistry.histogram("alpha.nope");
        assertThat("alpha.nope",
                   histogram.getSnapshot().bucketValues(),
                   arrayWithSize(0));

        metricRegistry.remove(new MetricID("alpha.nope"));

        Timer timer = metricRegistry.timer("alpha.nope");
        assertThat("alpha.nope",
                   timer.getSnapshot().bucketValues(),
                   arrayWithSize(0));

        timer = metricRegistry.timer("beta.anything");
        assertThat("beta.anything",
                   timer.getSnapshot().bucketValues(),
                   arrayWithSize(0));
    }
}
