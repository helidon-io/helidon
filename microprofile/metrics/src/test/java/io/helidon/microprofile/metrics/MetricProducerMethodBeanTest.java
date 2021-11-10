/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

/**
 * Patterned after the TCK test of the same name, to make sure we register metrics inspired by @Metric annotations
 * in the correct order.
 */
@HelidonTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MetricProducerMethodBeanTest {

    private final static String CALLS_METRIC = MetricRegistry.name(MetricProducerMethodBean.class, "calls");

    private static MetricID callsMID;

    private final static String HITS_METRIC = MetricRegistry.name(MetricProducerMethodBean.class, "hits");

    private static MetricID hitsMID;

    private final static String CACHE_HITS_METRIC = MetricRegistry.name(MetricProducerMethodBean.class, "cache-hits");

    private static MetricID cacheHitsMID;

    @Inject
    private MetricRegistry registry;

    @Inject
    private MetricProducerMethodBean bean;

    @BeforeAll
    public static void instantiateApplicationScopedBean() {
        /*
         * The MetricID relies on the MicroProfile Config API.
         * Running a managed arquillian container will result
         * with the MetricID being created in a client process
         * that does not contain the MPConfig impl.
         *
         * This will cause client instantiated MetricIDs to
         * throw an exception. (i.e the global MetricIDs)
         */
        callsMID = new MetricID(CALLS_METRIC);
        hitsMID = new MetricID(HITS_METRIC);
        cacheHitsMID = new MetricID(CACHE_HITS_METRIC);
    }

    @Test
    @Order(1)
    public void cachedMethodNotCalledYet() {
        assertThat("Metrics are not registered correctly", registry.getMetrics(),
                   allOf(
                           hasKey(callsMID),
                           hasKey(hitsMID),
                           hasKey(cacheHitsMID)
                   )
        );
        Timer calls = registry.getTimers().get(callsMID);
        Meter hits = registry.getMeters().get(hitsMID);
        @SuppressWarnings("unchecked")
        Gauge<Double> gauge = (Gauge<Double>) registry.getGauges().get(cacheHitsMID);

        assertThat("Gauge value is incorrect",
                   gauge.getValue(),
                   is(equalTo(((double) hits.getCount() / (double) calls.getCount()))));
    }

    @Test
    @Order(2)
    public void callCachedMethodMultipleTimes() {
        assertThat("Metrics are not registered correctly", registry.getMetrics(),
                   allOf(
                           hasKey(callsMID),
                           hasKey(hitsMID),
                           hasKey(cacheHitsMID)
                   )
        );
        Timer calls = registry.getTimers().get(callsMID);
        Meter hits = registry.getMeters().get(hitsMID);
        @SuppressWarnings("unchecked")
        Gauge<Double> gauge = (Gauge<Double>) registry.getGauges().get(cacheHitsMID);

        long count = 10 + Math.round(Math.random() * 10);
        for (int i = 0; i < count; i++) {
            bean.cachedMethod((Math.random() < 0.5));
        }

        assertThat("Gauge value is incorrect",
                   gauge.getValue(),
                   is(equalTo((double) hits.getCount() / (double) calls.getCount())));
    }
}
