/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.helidon.metrics.MetricsSupport;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Timer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.greaterThan;

/**
 * Class MetricsTest.
 */
public class MetricsTest extends MetricsBaseTest {

    @Test
    public void testCounted1() {
        CountedBean bean = newBean(CountedBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method1());
        Counter counter = getMetric(bean, "method1");
        assertThat(counter.getCount(), is(10L));
    }

    @Test
    public void testCounted2() {
        CountedBean bean = newBean(CountedBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method2());
        Counter counter = getMetric(bean, "method1");
        assertThat(counter.getCount(), is(10L));
    }

    @Test
    public void testMetered1() {
        MeteredBean bean = newBean(MeteredBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method1());
        Meter meter = getMetric(bean, "method1");
        assertThat(meter.getCount(), is(10L));
        assertThat(meter.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testMetered2() {
        MeteredBean bean = newBean(MeteredBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method2());
        Meter meter = getMetric(bean, "method2");
        assertThat(meter.getCount(), is(10L));
        assertThat(meter.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testTimed1() {
        TimedBean bean = newBean(TimedBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method1());
        Timer timer = getMetric(bean, "method1");
        assertThat(timer.getCount(), is(10L));
        assertThat(timer.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testTimed2() {
        TimedBean bean = newBean(TimedBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method2());
        Timer timer = getMetric(bean, "method2");
        assertThat(timer.getCount(), is(10L));
        assertThat(timer.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testInjection() {
        InjectedBean bean = newBean(InjectedBean.class);
        assertThat(bean.counter, notNullValue());
        assertThat(bean.meter, notNullValue());
        assertThat(bean.timer, notNullValue());
        assertThat(bean.histogram, notNullValue());
        assertThat(bean.gaugeForInjectionTest, notNullValue());
    }

    @Test
    public void testGauge() {
        final int EXPECTED_VALUE = 42;
        GaugedBean bean = newBean(GaugedBean.class);
        bean.setValue(EXPECTED_VALUE);
        Gauge<Integer> gauge = getMetric(bean, "reportValue");
        int valueViaGauge = gauge.getValue();
        assertThat(valueViaGauge, is(EXPECTED_VALUE));

        Gauge<Integer> otherGauge = getMetric(bean, "retrieveValue");
        valueViaGauge = otherGauge.getValue();
        assertThat(valueViaGauge, is(EXPECTED_VALUE));

        Gauge<GaugedBean.MyValue> customTypeGauge = getMetric(bean, "getMyValue");
        GaugedBean.MyValue valueViaCustomGauge = customTypeGauge.getValue();
        assertThat(valueViaCustomGauge.intValue(), is(EXPECTED_VALUE));
    }

    @Test
    public void testGaugeMetadata() {
        final int expectedValue = 42;
        GaugedBean bean = newBean(GaugedBean.class);
        bean.setValue(expectedValue);

        Gauge<Integer> gauge = getMetric(bean, GaugedBean.LOCAL_INJECTABLE_GAUGE_NAME);
        String promData = MetricsSupport.toPrometheusData(
                new MetricID(GaugedBean.LOCAL_INJECTABLE_GAUGE_NAME), gauge).trim();

        assertThat(promData, containsString("# TYPE application_gaugeForInjectionTest_seconds gauge"));
        assertThat(promData, containsString("\n# HELP application_gaugeForInjectionTest_seconds"));
        assertThat(promData, containsString("\napplication_gaugeForInjectionTest_seconds "
                + (expectedValue * 60)));
    }

    @Test
    public void testAbsoluteGaugeBeanName() {
        Set<String> gauges =  getMetricRegistry().getGauges()
                .keySet().stream().map(MetricID::getName).collect(Collectors.toSet());
        assertThat(gauges, CoreMatchers.hasItem("secondsSinceBeginningOfTime"));
    }
}
