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
 *
 */
package io.helidon.microprofile.metrics;

import java.util.Map;
import java.util.stream.IntStream;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@AddBean(CountedConstructorTestBean.class)
public class CountedConstructorTest extends MetricsBaseTest {

    @Test
    public void checkConstructorMetric() {
        String expectedName = CountedConstructorTestBean.class.getName() + "." + CountedConstructorTestBean.CONSTRUCTOR_COUNTER;

        CountedConstructorTestBean bean = newBean(CountedConstructorTestBean.class);
        bean.inc();

        MetricID metricID = new MetricID(expectedName);
        Map<MetricID, Counter> counters = getMetricRegistry().getCounters();

        assertThat("Counters in registry", counters, hasKey(metricID));
        Counter counter = counters.get(metricID);
        assertThat("Matching counter", counter, is(notNullValue()));
        assertThat("Invocations of constructor", counter.getCount(), is(1L));
    }
}
