/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import io.helidon.common.metrics.InternalBridge;
import io.helidon.common.metrics.InternalBridge.Metadata;
import io.helidon.common.metrics.InternalBridge.MetricID;
import io.helidon.common.metrics.InternalBridge.MetricRegistry;


import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Metric;

/**
 * Class MetricProducer.
 */
@ApplicationScoped
public class MetricProducer {

    private static Metadata newMetadata(InjectionPoint ip, Metric metric, MetricType metricType) {
        return metric == null ? InternalBridge.Metadata.newMetadata(getName(ip),
                                             "",
                                             "",
                                             metricType,
                                             MetricUnits.NONE)
                : InternalBridge.Metadata.newMetadata(getName(metric, ip),
                               metric.displayName(),
                               metric.description(),
                               metricType,
                               metric.unit(),
                               toTags(metric.tags()));
    }

    static Map<String, String> toTags(String[] tags) {
        return Arrays.stream(tags)
                .map(expr -> {
                    final int eq = expr.indexOf("=");
                    if (eq <= 0) {
                        return null;
                    }
                    return new AbstractMap.SimpleEntry<>(expr.substring(0, eq),
                                expr.substring(eq + 1));
                })
                .filter(Objects::isNull)
                .collect(HashMap::new,
                     (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                     Map::putAll);

    }

    private static String getName(InjectionPoint ip) {
        StringBuilder fullName = new StringBuilder();
        fullName.append(ip.getMember().getDeclaringClass().getName());
        fullName.append('.');
        fullName.append(ip.getMember().getName());
        if (ip.getMember() instanceof Constructor) {
            fullName.append("new");
        }
        return fullName.toString();
    }

    private static String getName(Metric metric, InjectionPoint ip) {
        StringBuilder fullName =
                new StringBuilder(metric.absolute() ? "" : ip.getMember().getDeclaringClass().getName() + ".");
        if (metric.name().isEmpty()) {
            fullName.append(ip.getMember().getName());
            if (ip.getMember() instanceof Constructor) {
                fullName.append(".new");
            }
        } else {
            fullName.append(metric.name());
        }
        return fullName.toString();
    }

    @Produces
    private Counter produceCounterDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceCounter(registry, ip);
    }

    @Produces
    @VendorDefined
    private Counter produceCounter(MetricRegistry registry, InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        return registry.counter(newMetadata(ip, metric, MetricType.COUNTER));
    }

    @Produces
    private Meter produceMeterDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceMeter(registry, ip);
    }

    @Produces
    @VendorDefined
    private Meter produceMeter(MetricRegistry registry, InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        return registry.meter(newMetadata(ip, metric, MetricType.METERED));
    }

    @Produces
    private Timer produceTimerDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceTimer(registry, ip);
    }

    @Produces
    @VendorDefined
    private Timer produceTimer(MetricRegistry registry, InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        return registry.timer(newMetadata(ip, metric, MetricType.TIMER));
    }

    @Produces
    private Histogram produceHistogramDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceHistogram(registry, ip);
    }

    @Produces
    @VendorDefined
    private Histogram produceHistogram(MetricRegistry registry, InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        return registry.histogram(newMetadata(ip, metric, MetricType.HISTOGRAM));
    }

    /**
     * Returns the {@link Gauge} matching the criteria from the injection point.
     *
     * @param <T>      type of the {@code Gauge}
     * @param registry metric registry
     * @param ip       injection point being resolved
     * @return requested gauge
     */
    @Produces
    @VendorDefined
    @SuppressWarnings("unchecked")
    private <T> Gauge<T> produceGauge(MetricRegistry registry, InjectionPoint ip) {
        String metricName = getName(ip);
        // TODO - need to get tags from anno and add to MetricID
        MetricID metricID = new MetricID(metricName);
        Gauge<T> result = (Gauge<T>) registry.getBridgeGauges().get(metricID);
        if (result == null) {
            throw new IllegalArgumentException("Could not produce Gauge for injection point " + ip.toString());
        }
        return result;
    }

    /**
     * Returns the default {@link Gauge} matching the criteria.
     *
     * @param <T>      type of the {@code Gauge}
     * @param registry metric registry
     * @param ip       injection point being resolved
     * @return requested gauge
     */
    @Produces
    private <T> Gauge<T> produceGaugeDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceGauge(registry, ip);
    }
}
