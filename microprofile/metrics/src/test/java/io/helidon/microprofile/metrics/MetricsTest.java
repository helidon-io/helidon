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

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.providers.micrometer.MicrometerPrometheusFormatter;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Timer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
    public void testTimed1() {
        TimedBean bean = newBean(TimedBean.class);
        IntStream.range(0, 11).forEach(i -> bean.method1());
        Timer timer = getMetric(bean, "method1");
        assertThat(timer.getCount(), is(11L));
        assertThat(timer.getSnapshot().getMean(), is(greaterThan(0.0)));
    }

    @Test
    public void testTimed2() {
        TimedBean bean = newBean(TimedBean.class);
        IntStream.range(0, 14).forEach(i -> bean.method2());
        Timer timer = getMetric(bean, "method2");
        assertThat(timer.getCount(), is(14L));
        assertThat(timer.getSnapshot().getMean(), is(greaterThan(0.0)));
    }

    @Test
    public void testInjection() {
        InjectedBean bean = newBean(InjectedBean.class);
        assertThat(bean.counter, notNullValue());
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
        MicrometerPrometheusFormatter formatter = MicrometerPrometheusFormatter.builder(Metrics.globalRegistry())
                .scopeTagName("mp_scope")
                .build();
        Optional<Object> outputOpt = formatter.format();

        assertThat("Output", outputOpt, OptionalMatcher.optionalPresent());
        assertThat("Output", outputOpt.get(), is(instanceOf(String.class)));

        String promData = (String) outputOpt.get();

        // The @Gauge overrides the default units. Plus, the Prometheus output from Micrometer now includes the mp_scope tag and
        // the value formatted as a double (that's Prometheus exposition format standard).
        assertThat(promData, containsString("# TYPE gaugeForInjectionTest_minutes gauge"));
        assertThat(promData, containsString("\n# HELP gaugeForInjectionTest_minutes"));
        assertThat(promData, containsString("\ngaugeForInjectionTest_minutes{mp_scope=\"application\",} "
                + (double) expectedValue));
    }

    @Test
    public void testAbsoluteGaugeBeanName() {
        Set<String> gauges =  getMetricRegistry().getGauges()
                .keySet().stream().map(MetricID::getName).collect(Collectors.toSet());
        assertThat(gauges, CoreMatchers.hasItem("secondsSinceBeginningOfTime"));
    }

    @Test
    void testOmittedDisplayName() {
        TimedBean bean = newBean(TimedBean.class);
        String metricName = TimedBean.class.getName() + ".method1";
        Metadata metadata = getMetricRegistry().getMetadata().get(metricName);
        assertThat("Metadata for meter of annotated method", metadata, is(notNullValue()));

        Metadata newMetadata = Metadata.builder()
                .withName(metadata.getName())
                .withUnit(metadata.getUnit())
                .build();
        // Should return the existing meter. Throws exception if metadata is mismatched.
        Timer timer = getMetricRegistry().timer(newMetadata);
    }
}
