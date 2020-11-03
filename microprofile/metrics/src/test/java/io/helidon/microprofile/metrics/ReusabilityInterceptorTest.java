/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import javax.inject.Inject;

@HelidonTest
public class ReusabilityInterceptorTest extends MetricsBaseTest {

    @Inject
    private MetricRegistry metricRegistry;

    @Test
    public void testReusedMetricWithInterceptor() {
        ResourceWithReusedMetricForInvocation resource = newBean(ResourceWithReusedMetricForInvocation.class);
        resource.method1();

        Counter counter = metricRegistry.counter(ResourceWithReusedMetricForInvocation.OTHER_REUSED_NAME,
                MetricUtil.tags(new String[] {ResourceWithReusedMetricForInvocation.TAG_1,
                        ResourceWithReusedMetricForInvocation.TAG_2}));
        assertThat(counter.getCount(), is(1L));
    }
}
