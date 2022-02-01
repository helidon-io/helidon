/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
class TestStereotypes {

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private BeanViaStereotypeA myBean;

    @Test
    void testClassLevelStereotype() {

        Counter counter = metricRegistry.getCounter(new MetricID(BeanViaStereotypeA.class.getName() + ".noOp"));
        assertThat("Counter registered via stereotype", counter, notNullValue());

        SimpleTimer simpleTimer = metricRegistry.getSimpleTimer(
                new MetricID(BeanViaStereotypeA.class.getPackageName() + "." + StereotypeA.SIMPLE_TIMER_NAME + ".noOp"));
        assertThat("Simple timer registered via stereotype", simpleTimer, notNullValue());

        myBean.noOp();

        assertThat("Counter value after one call", counter.getCount(), is(1L));
        assertThat("Simple timer value after one call", simpleTimer.getCount(), is(1L));
    }

    @Test
    void testMethodLevelStereotype() {
        Gauge<Long> gauge = (Gauge<Long>) metricRegistry.getGauge(new MetricID(StereotypeB.GAUGE_NAME));
        assertThat("Gauge registered via stereotype", gauge, notNullValue());

        long value = gauge.getValue();
        assertThat("Gauge value", value, allOf(greaterThanOrEqualTo(0L),
                                                      lessThanOrEqualTo(BeanViaStereotypeA.SPEED_BOUND)));

        SimpleTimer simpleTimer = metricRegistry.getSimpleTimer(new MetricID(StereotypeB.SIMPLE_TIMER_NAME));
        assertThat("Simple timer registered via stereotype", simpleTimer, notNullValue());
        assertThat("Simple timer count", simpleTimer.getCount(), equalTo(1L));
    }
}
