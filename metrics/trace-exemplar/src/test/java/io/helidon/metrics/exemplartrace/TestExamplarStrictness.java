/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.exemplartrace;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.tracing.TracerBuilder;

import io.opentracing.Span;
import io.opentracing.Tracer;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;


class TestExamplarStrictness {

    private static Context testContext;

    @BeforeAll
    static void prepareTracing() {
        Tracer tracer = TracerBuilder.create("test-service").build();
        Span span = tracer.buildSpan("test-span").start();
        tracer.activateSpan(span);
        testContext = Context.builder().id("test-context").build();
        testContext.register(span.context());
    }
    @Test
    void checkWithStrictExemplars() throws Exception {

        Contexts.runInContextWithThrow(testContext, () -> {

            boolean isStrictExemplars = true;
            MetricRegistry metricRegistry = RegistryFactory
                    .getInstance(MetricsSettings.builder().build())
                    .getRegistry(MetricRegistry.Type.APPLICATION);
            metricRegistry.removeMatching((id, metric) -> true);
            Counter counter = metricRegistry.counter("c1");
            SimpleTimer simpleTimer = metricRegistry.simpleTimer("st1");
            long incs = 3;

            Duration duration = Duration.ofSeconds(4);
            simpleTimer.update(duration);
            counter.inc(incs);

            assertThat("Updated counter", counter.getCount(), is(incs));
            assertThat("Updated simple timer count", simpleTimer.getCount(), is(1L));
            assertThat("Updated simple timer duration", simpleTimer.getElapsedTime(), is(duration));

            String promData = prometheusData(counter, "Counter", new MetricID("c1"), false, isStrictExemplars);
            String simpleTimerElapsedData = "application_st1_elapsedTime_seconds 4.0";
            String simpleTimerCounterData = "application_st1_total 1";

            assertThat("Prometheus counter output with strict exemplars", promData, containsString("# {trace_id"));

            promData = prometheusData(simpleTimer, "SimpleTimer", new MetricID("st1"), false, isStrictExemplars);


            assertThat("Prometheus simple timer output with strict exemplars",
                       promData,
                       containsString(simpleTimerElapsedData));
            assertThat("Prometheus simple timer output with strict exemplars",
                       promData,
                       not(containsString(simpleTimerElapsedData + " # {trace")));
            assertThat("Prometheus simple timer output with strict exemplars",
                       promData,
                       containsString(simpleTimerCounterData + " # {trace"));
            return null;
        });

    }

    @Test
    void checkWithLaxExemplars() throws Exception {

        Contexts.runInContextWithThrow(testContext, () -> {

            boolean isStrictExemplars = false;
            MetricRegistry metricRegistry = RegistryFactory
                    .getInstance(MetricsSettings.builder().strictExemplars(isStrictExemplars).build())
                    .getRegistry(MetricRegistry.Type.APPLICATION);
            metricRegistry.removeMatching((id, metric) -> true);
            Counter counter = metricRegistry.counter("c1");
            SimpleTimer simpleTimer = metricRegistry.simpleTimer("st1");

            long incs = 5;
            Duration duration = Duration.ofSeconds(4);

            simpleTimer.update(duration);
            counter.inc(incs);

            assertThat("Updated counter", counter.getCount(), is(incs));
            assertThat("Updated simple timer count", simpleTimer.getCount(), is(1L));
            assertThat("Updated simple timer duration", simpleTimer.getElapsedTime(), is(duration));

            String promData = prometheusData(counter, "Counter", new MetricID("c1"), false, isStrictExemplars);
            assertThat("Prometheus counter output with strict exemplars", promData, containsString("# {trace_id"));

            String simpleTimerElapsedData = "application_st1_elapsedTime_seconds 4.0";
            String simpleTimerCounterData = "application_st1_total 1";

            promData = prometheusData(simpleTimer, "SimpleTimer", new MetricID("st1"), false, isStrictExemplars);

            assertThat("Prometheus simple timer output with strict exemplars",
                       promData,
                       containsString(simpleTimerElapsedData));
            assertThat("Prometheus simple timer output with strict exemplars",
                       promData,
                       containsString(simpleTimerElapsedData + " # {trace"));
            assertThat("Prometheus simple timer output with strict exemplars",
                       promData,
                       containsString(simpleTimerCounterData + " # {trace"));
            return null;
        });

    }

    private String prometheusData(Metric metric,
                                  String classNameSuffix,
                                  MetricID metricId,
                                  boolean withHelpType,
                                  boolean isStrictExemplars)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> helidonClass = Class.forName("io.helidon.metrics.Helidon" + classNameSuffix);
        Method promMethod = helidonClass.getMethod("prometheusData",
                                                          StringBuilder.class, MetricID.class, boolean.class, boolean.class);
        promMethod.setAccessible(true);
        StringBuilder sb = new StringBuilder();
        promMethod.invoke(metric, sb, metricId, withHelpType, isStrictExemplars);
        return sb.toString();
    }

}
