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

import io.helidon.metrics.api.Meter;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfigBlock;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@HelidonTest
@AddBean(CountedBean.class)
@AddConfigBlock("""
        metrics.scoping.scopes.0.name=application
        metrics.scoping.scopes.0.filter.exclude=.*oome.*"""
)
class TestSelectivelyDisabledMetric {

    @Test
    void testDisabledCounter() {
        RegistryFactory rf = RegistryFactory.getInstance();
        Registry reg = rf.registry(Meter.Scope.APPLICATION);

        MetricID metricID = new MetricID(CountedBean.DOOMED_COUNTER);
        Counter counter = reg.getCounter(metricID);
        assertThat("Disabled counter looked up", counter, is(nullValue()));

        counter = reg.counter(metricID);
        counter.inc();
        assertThat("Disabled (no-op) counter after increment", counter.getCount(), is(0L));
    }
}
