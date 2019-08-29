/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import io.helidon.common.metrics.InternalBridge.MetricID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Class ProducerTest.
 */
public class ProducerTest extends MetricsBaseTest {

    @Test
    public void testFieldProducer() throws Exception {
        ProducerBean bean = newBean(ProducerBean.class);
        MetricID metricID = new MetricID("counter1");
        assertThat(getMetricRegistry().getBridgeCounters().keySet().contains(metricID), is(true));
        assertThat(getMetricRegistry().getBridgeCounters().get(metricID).getCount(), is(0L));
    }

    @Test
    public void testMethodProducer() throws Exception {
        ProducerBean bean = newBean(ProducerBean.class);
        MetricID metricID = new MetricID("counter2");
        assertThat(getMetricRegistry().getBridgeCounters().keySet().contains(metricID), is(true));
        assertThat(getMetricRegistry().getBridgeCounters().get(metricID).getCount(), is(1L));
    }
}
