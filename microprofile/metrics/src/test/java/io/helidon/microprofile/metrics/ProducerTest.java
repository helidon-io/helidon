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

import org.eclipse.microprofile.metrics.MetricID;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Class ProducerTest.
 */
public class ProducerTest extends MetricsBaseTest {

    private final MetricID counter1 = new MetricID("counter1");
    private final MetricID counter2 = new MetricID("counter2");

    @Test
    public void testFieldProducer() {
        ProducerBean bean = newBean(ProducerBean.class);
        assertThat(getMetricRegistry().getCounters().keySet().contains(counter1), is(true));
        assertThat(getMetricRegistry().getCounters().get(counter1).getCount(), is(0L));
    }

    @Test
    public void testMethodProducer() {
        ProducerBean bean = newBean(ProducerBean.class);
        assertThat(getMetricRegistry().getCounters().keySet().contains(counter2), is(true));
        assertThat(getMetricRegistry().getCounters().get(counter2).getCount(), is(1L));
    }
}
