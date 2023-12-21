/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Class ProducerTest.
 */
public class ProducerTest extends MetricsBaseTest {

    @Inject @Red
    private Counter c1;

    @Inject @Green
    private Counter c2;

    @Inject
    @RegistryScope(scope = "special")
    private MetricRegistry specialRegistry;

    @Inject
    private MetricRegistry appRegistry;

    @Inject
    private RegistryFactory registryFactory;

    private final MetricID counter1 = new MetricID("counter1");
    private final MetricID counter2 = new MetricID("counter2");

    @Test
    public void testFieldProducer() {
        ProducerBean bean = newBean(ProducerBean.class);
        assertThat(getMetricRegistry().getCounters().containsKey(counter1), is(true));
        assertThat(getMetricRegistry().getCounters().get(counter1).getCount(), is(0L));
    }

    @Test
    public void testMethodProducer() {
        ProducerBean bean = newBean(ProducerBean.class);
        assertThat(getMetricRegistry().getCounters().containsKey(counter2), is(true));
        assertThat(getMetricRegistry().getCounters().get(counter2).getCount(), is(1L));
    }

    @Test
    void testRegistryProducer() {
        String name = "counterInInjectedRegistry";
        long appCounterIncr = 2;
        long specialCounterIncr = 3;

        Counter appCounter = appRegistry.counter(name);
        appCounter.inc(appCounterIncr);

        Counter specialCounter = specialRegistry.counter(name);
        specialCounter.inc(specialCounterIncr);

        assertThat("Counters are different", appCounter, is(not(sameInstance(specialCounter))));
        assertThat("App registry counter", appCounter.getCount(), is(appCounterIncr));
        assertThat("Special registry counter", specialCounter.getCount(), is(specialCounterIncr));
    }

    @Test
    void testRegistryFactoryProducer() {
        MetricRegistry customRegistry = registryFactory.getRegistry("myCustomScope");
        assertThat("Custom scoped MetricRegistry", customRegistry, notNullValue());
    }
}
