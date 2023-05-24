/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Patterned after the TCK test of the same name, to make sure we register metrics inspired by @Metric annotations
 * in the correct order.
 */
@Disabled
@HelidonTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MetricProducerMethodBeanTest {

    private final static String CALLS_METRIC = MetricRegistry.name(MetricProducerMethodBean.class, "calls");

    private static MetricID callsMID;

    private final static String HITS_METRIC = MetricRegistry.name(MetricProducerMethodBean.class, "hits");

    private static MetricID hitsMID;

    private final static String CACHE_HITS_METRIC = MetricRegistry.name(MetricProducerMethodBean.class, "cacheHitRatioGauge");

    private static MetricID notRegisteredMetricMID;

    private final static String NOT_REGISTERED_METRIC = MetricRegistry.name(MetricProducerMethodBean.class,
                                                                            "notRegisteredMetric");

    private static MetricID cacheHitsMID;

    @Inject
    private MetricRegistry registry;

    @Inject
    private MetricProducerMethodBean bean;

   @BeforeAll
    public static void instantiateApplicationScopedBean() {
        /*
         * With earlier MP metrics releases, the MetricID relied on the MicroProfile Config API.
         * Running a managed arquillian container would result
         * in the MetricID being created in a client process
         * that did not contain the MPConfig impl.
         *
         * That would cause client instantiated MetricIDs to
         * throw an exception. (i.e the global MetricIDs)
         *
         * Beginning with MP metrics 3.0, the MetricID no longer uses config to assign tags, so the problem
         * should not occur. But we might as well leave the declarations here.
         */
        callsMID = new MetricID(CALLS_METRIC);
        hitsMID = new MetricID(HITS_METRIC);
        cacheHitsMID = new MetricID(CACHE_HITS_METRIC);
        notRegisteredMetricMID = new MetricID(NOT_REGISTERED_METRIC);
    }

    @Test
    @Order(1)
    public void cachedMethodNotCalledYet() {
        assertThat("Metric registry before producers have been invoked",
                   registry.getMetrics(),
                   allOf(
                           hasKey(callsMID),
                           hasKey(hitsMID),
                           not(hasKey(cacheHitsMID)),
                           not(hasKey(notRegisteredMetricMID))
                   )
        );
    }

    @Test
    @Order(2)
    public void callCachedMethodMultipleTimes() {

        Timer calls = registry.getTimers().get(callsMID);
        Meter hits = registry.getMeters().get(hitsMID);
        long count = 10 + Math.round(Math.random() * 10);
        for (int i = 0; i < count; i++) {
            bean.cachedMethod((Math.random() < 0.5));
        }

        assertThat("Metrics registry after triggering gauge producer", registry.getMetrics(),
                   allOf(
                           hasKey(callsMID),
                           hasKey(hitsMID),
                           hasKey(cacheHitsMID),
                           not(hasKey(notRegisteredMetricMID))
                   )
        );

        @SuppressWarnings("unchecked")
        Gauge<Double> gauge = (Gauge<Double>) registry.getGauges().get(cacheHitsMID);

        assertThat("Gauge value is incorrect",
                   gauge.getValue(),
                   is(equalTo((double) hits.getCount() / (double) calls.getCount())));
    }
}
