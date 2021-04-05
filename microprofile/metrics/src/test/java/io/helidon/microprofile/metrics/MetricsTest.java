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

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.helidon.metrics.MetricsSupport;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Timer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.lessThan;

/**
 * Class MetricsTest.
 */
public class MetricsTest extends MetricsBaseTest {

    private static final String PERF_TEST_PROP_PREFIX = "helidon.microprofile.metrics.perfTest.";
    private static final int PERF_TEST_COUNT = Integer.getInteger(PERF_TEST_PROP_PREFIX + "count", 10000);
    private static final long PERF_TEST_FAILURE_THRESHOLD_NS = Integer.getInteger(
            PERF_TEST_PROP_PREFIX + ".failureThresholdNS", 200 * 1000 * 1000); // roughly double informal expc

    @Test
    public void testCounted1() {
        CountedBean bean = newBean(CountedBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method1());
        Counter counter = getMetric(bean, "method1");
        assertThat(counter.getCount(), is(10L));
    }

    @Test
    public void testCounted3() {
        CountedBean bean = newBean(CountedBean.class);
        IntStream.range(0, 8).forEach(i -> bean.method3());
        Counter counter = getMetric(bean, "method3");
        assertThat(counter.getCount(), is(8L));
    }



    @Test
    @DisabledIfSystemProperty(named = PERF_TEST_PROP_PREFIX + "enabled", matches = "false")
    public void testCounted2Perf() {
        /*
         * Informal experience shows that, without the performance fix in MetricsInterceptorBase, this test measures more than 1 s
         *  to perform 10000 intercepted calls to the bean. With the fix, the time is around .06-.08 seconds.
         */
        CountedBean bean = newBean(CountedBean.class);
        long start = System.nanoTime();
        IntStream.range(0, PERF_TEST_COUNT).forEach(i -> bean.method2());
        long end = System.nanoTime();
        Counter counter = getMetric(bean, "method2");
        System.err.printf("Elapsed time for test (ms): %f%n", (end - start) / 1000.0 / 1000.0);
        assertThat(counter.getCount(), is((long) PERF_TEST_COUNT));
        assertThat(String.format("Elapsed time of %d tests (ms)", PERF_TEST_COUNT),
                (end - start) / 1000.0 / 1000.0,
                is(lessThan(PERF_TEST_FAILURE_THRESHOLD_NS / 1000.0 / 1000.0)));
    }

    @Test
    public void testMetered1() {
        MeteredBean bean = newBean(MeteredBean.class);
        IntStream.range(0, 9).forEach(i -> bean.method1());
        Meter meter = getMetric(bean, "method1");
        assertThat(meter.getCount(), is(9L));
        assertThat(meter.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testMetered2() {
        MeteredBean bean = newBean(MeteredBean.class);
        IntStream.range(0, 12).forEach(i -> bean.method2());
        Meter meter = getMetric(bean, "method2");
        assertThat(meter.getCount(), is(12L));
        assertThat(meter.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testTimed1() {
        TimedBean bean = newBean(TimedBean.class);
        IntStream.range(0, 11).forEach(i -> bean.method1());
        Timer timer = getMetric(bean, "method1");
        assertThat(timer.getCount(), is(11L));
        assertThat(timer.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testTimed2() {
        TimedBean bean = newBean(TimedBean.class);
        IntStream.range(0, 14).forEach(i -> bean.method2());
        Timer timer = getMetric(bean, "method2");
        assertThat(timer.getCount(), is(14L));
        assertThat(timer.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testSimplyTimed1() {
        SimplyTimedBean bean = newBean(SimplyTimedBean.class);
        IntStream.range(0, 7).forEach(i -> bean.method1());
        SimpleTimer simpleTimer = getMetric(bean, "method1");
        assertThat(simpleTimer.getCount(), is(7L));
        assertThat(simpleTimer.getElapsedTime().toNanos(), is(greaterThan(0L)));
    }

    @Test
    public void testSimplyTimed2() {
        SimplyTimedBean bean = newBean(SimplyTimedBean.class);
        IntStream.range(0, 15).forEach(i -> bean.method2());
        SimpleTimer simpleTimer = getMetric(bean, "method2");
        assertThat(simpleTimer.getCount(), is(15L));
        assertThat(simpleTimer.getElapsedTime().toNanos(), is(greaterThan(0L)));
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
                new MetricID(GaugedBean.LOCAL_INJECTABLE_GAUGE_NAME), gauge, true).trim();

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
