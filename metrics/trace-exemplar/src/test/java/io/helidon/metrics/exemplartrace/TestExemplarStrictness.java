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
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.metrics.api.MetricsSettings;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.metrics.api.RegistrySettings;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;


class TestExemplarStrictness {
    private static Context testContext;
    private static Span span;
    private static Scope scope;

    @BeforeAll
    static void prepareTracing() {
        Tracer tracer = TracerBuilder.create("test-service").build();
        span = tracer.spanBuilder("test-span").start();
        scope = span.activate();

        testContext = Context.builder().id("test-context").build();
        testContext.register(span.context());
    }

    @AfterAll
    static void finish() {
        if (scope != null) {
            scope.close();
        }
        if (span != null) {
            span.end();
        }
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
            Timer timer = metricRegistry.timer("t1");

            long incs = 3;
            long timerDuration = 3000L;

            Duration duration = Duration.ofSeconds(4);
            simpleTimer.update(duration);
            counter.inc(incs);
            timer.update(Duration.ofNanos(timerDuration));
            timer.update(Duration.ofNanos(timerDuration));

            assertThat("Updated counter", counter.getCount(), is(incs));
            assertThat("Updated simple timer count", simpleTimer.getCount(), is(1L));
            assertThat("Updated simple timer duration", simpleTimer.getElapsedTime(), is(duration));
            assertThat("Updated timer count", timer.getCount(), is(2L));
            assertThat("Updated timer duration", timer.getSnapshot().getMax(), is(timerDuration));

            String promData = prometheusData(counter, "Counter", new MetricID("c1"), false, isStrictExemplars);
            String simpleTimerElapsedData = "application_st1_elapsedTime_seconds 4.0";
            String simpleTimerCounterData = "application_st1_total 1";
            String timerCounterData = "application_t1_seconds_count 2";

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

            promData = prometheusData(timer, "Timer", new MetricID("t1"), false, isStrictExemplars);
            assertThat("Prometheus timer output with strict exemplars",
                       promData,
                       containsString(timerCounterData));
            assertThat("Prometheus timer output with strict exemplars",
                       promData,
                       not(containsString(timerCounterData) + " # {trace"));

            return null;
        });

    }

    @Test
    void checkWithLaxExemplars() throws Exception {

        Contexts.runInContextWithThrow(testContext, () -> {

            boolean isStrictExemplars = false;
            MetricRegistry metricRegistry = RegistryFactory
                    .getInstance(MetricsSettings.builder()
                                         .registrySettings(MetricRegistry.Type.APPLICATION,
                                                           RegistrySettings.builder()
                                                                   .strictExemplars(isStrictExemplars)
                                                                   .build())
                                         .build())
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
