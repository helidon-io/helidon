/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import io.helidon.metrics.MetricsSupport;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Timer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.greaterThan;

/**
 * Class MetricsTest.
 */
public class MetricsTest extends MetricsBaseTest {

    @Test
    public void testCounted1() throws Exception {
        CountedBean bean = newBean(CountedBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method1());
        Counter counter = getMetric(bean, "method1");
        assertThat(counter.getCount(), is(10L));
    }

    @Test
    public void testCounted2() throws Exception {
        CountedBean bean = newBean(CountedBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method2());
        Counter counter = getMetric(bean, "method1");
        assertThat(counter.getCount(), is(10L));
    }

    @Test
    public void testMetered1() throws Exception {
        MeteredBean bean = newBean(MeteredBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method1());
        Meter meter = getMetric(bean, "method1");
        assertThat(meter.getCount(), is(10L));
        assertThat(meter.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testMetered2() throws Exception {
        MeteredBean bean = newBean(MeteredBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method2());
        Meter meter = getMetric(bean, "method2");
        assertThat(meter.getCount(), is(10L));
        assertThat(meter.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testTimed1() throws Exception {
        TimedBean bean = newBean(TimedBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method1());
        Timer timer = getMetric(bean, "method1");
        assertThat(timer.getCount(), is(10L));
        assertThat(timer.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testTimed2() throws Exception {
        TimedBean bean = newBean(TimedBean.class);
        IntStream.range(0, 10).forEach(i -> bean.method2());
        Timer timer = getMetric(bean, "method2");
        assertThat(timer.getCount(), is(10L));
        assertThat(timer.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testInjection() throws Exception {
        InjectedBean bean = newBean(InjectedBean.class);
        assertThat(bean.counter, notNullValue());
        assertThat(bean.meter, notNullValue());
        assertThat(bean.timer, notNullValue());
        assertThat(bean.histogram, notNullValue());
        assertThat(bean.gaugeForInjectionTest, notNullValue());
    }

    @Test
    public void testGauge() throws Exception {
        final int EXPECTED_VALUE = 42;
        GaugedBean bean = newBean(GaugedBean.class);
        bean.setValue(EXPECTED_VALUE);
        Gauge<Integer> gauge = getMetric(bean, "reportValue");
        int valueViaGauge = gauge.getValue();
        assertThat(valueViaGauge, is(EXPECTED_VALUE));

        Gauge<Integer> otherGauge = getMetric(bean, "retrieveValue");
        valueViaGauge = otherGauge.getValue();
        assertThat(valueViaGauge, is(EXPECTED_VALUE));
    }

    @Test
    public void testGaugeMetadata() {
        final int EXPECTED_VALUE = 42;
        GaugedBean bean = newBean(GaugedBean.class);
        bean.setValue(EXPECTED_VALUE);

        Gauge<Integer> gauge = getMetric(bean, GaugedBean.LOCAL_INJECTABLE_GAUGE_NAME);
        String promData = MetricsSupport.toPrometheusData(gauge);

        Pattern prometheusDataPattern = Pattern.compile("(?s)#\\s+TYPE\\s+(\\w+):(\\w+)\\s*gauge.*#\\s*HELP.*\\{([^\\}]*)\\}\\s*(\\d*).*");
        Matcher m = prometheusDataPattern.matcher(promData);
        assertThat("Prometheus data " + promData + " for gauge bean did not match regex pattern", m.matches(), is(true));
        assertThat("Expected to find metric metadata and data in Prometheus data as 4 groups", m.groupCount(), is(4));
        String beanScope = m.group(1);
        String promName = m.group(2);
        String tags = m.group(3);
        String value = m.group(4);
        String gaugeUnitsFromName = promName.substring(promName.lastIndexOf('_')+1);
        assertThat("Unexpected bean scope for injected gauge", beanScope, is("application"));
        assertThat("Unexpected units for injected gauge in Prometheus data", gaugeUnitsFromName, is(MetricUnits.SECONDS));

        assertThat("Unexpected tags for injected gauge in Prometheus data",
                   tagsStringToMap(tags),
                   is(tagsStringToMap(GaugedBean.TAGS)));

        assertThat("Unexpected gauge value (in seconds)",
                   Integer.parseInt(value),
                   is(EXPECTED_VALUE * 60));
        /*
         * Here is an example of the Prometheus data:
         *
# TYPE application:io_helidon_microprofile_metrics_cdi_gauged_bean_gauge_for_injection_test_seconds gauge
# HELP application:io_helidon_microprofile_metrics_cdi_gauged_bean_gauge_for_injection_test_seconds
application:io_helidon_microprofile_metrics_cdi_gauged_bean_gauge_for_injection_test_seconds{tag1="valA",tag2="valB"} 2520
         *
         */
    }

    @Test
    public void testAbsoluteGaugeBeanName() {
        Set<String> gauges =  getMetricRegistry().getGauges().keySet();
        assertThat(gauges, CoreMatchers.hasItem("secondsSinceBeginningOfTime"));
    }

    /**
     * Converts a tag string to a Map.
     * <p>
     * The tag string contains comma-separated substrings of the form key=value
     * Each value can (but does not have to be) enclosed in double quotes (key="value")
     *
     * @param tagString String as described above
     * @return Map
     */
    private Map<String, String> tagsStringToMap(String tagString) {
        Map<String, String> result = new HashMap<>();
        Pattern tagPattern = Pattern.compile("(\\w+)=\"?(\\w+)\"?");
        Matcher m = tagPattern.matcher(tagString);
        while (m.find()) {
            result.put(m.group(1), m.group(2));
        }
        return result;
    }
}
