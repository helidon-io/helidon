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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.Timed;

@ApplicationScoped
public class MetricProducerMethodBean {

    @Inject
    private Meter hits;

    @Timed(name = "calls")
    public void cachedMethod(boolean hit) {
        if (hit) {
            hits.mark();
        }
    }

    @Produces
    @Metric(name = "cache-hits")
    Gauge<Double> cacheHitRatioGauge(final @Metric(name = "hits") Meter hits, final @Metric(name = "calls") Timer calls) {
        return new Gauge<Double>() {

            @Override
            public Double getValue() {
                return (double) hits.getCount() / (double) calls.getCount();
            }
        };
    }

    @Produces
    @Metric(name = "not_registered_metric")
    Counter notRegisteredMetric(MetricRegistry registry, InjectionPoint ip) {
        return registry.counter("not_registered_metric");
    }

    @Override
    public String toString() {
        return "MetricProducerMethodBean";
    }
}
