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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * Class MetricProducer.
 */
@ApplicationScoped
class MetricProducer {

    private static Metadata newMetadata(InjectionPoint ip, Metric metric, MetricType metricType) {
        return metric == null ? new Metadata(getName(ip),
                                             "",
                                             "",
                                             metricType,
                                             MetricUnits.NONE)
                : new Metadata(getName(metric, ip),
                               metric.displayName(),
                               metric.description(),
                               metricType,
                               metric.unit(),
                               toTags(metric.tags()));
    }

    private static String toTags(String[] tags) {
        return tags.length == 0 ? "" : String.join(",", tags);
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
        boolean isAbsolute = metric != null && metric.absolute();
        String prefix = isAbsolute ? "" : ip.getMember().getDeclaringClass().getName() + ".";
        String shortName = metric != null && !metric.name().isEmpty() ? metric.name() : ip.getMember().getName();
        String ctorSuffix = ip.getMember() instanceof Constructor ? ".new" : "";
        String fullName = prefix + shortName + ctorSuffix;
        return fullName;
    }

    @Produces
    private Counter produceCounterDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceCounter(registry, ip);
    }

    @Produces
    @VendorDefined
    private Counter produceCounter(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, Counted.class, registry::getCounters, registry::counter,
                Counter.class);
    }

    @Produces
    private Meter produceMeterDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceMeter(registry, ip);
    }

    @Produces
    @VendorDefined
    private Meter produceMeter(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, Metered.class, registry::getMeters, registry::meter,
                Meter.class);
    }

    @Produces
    private Timer produceTimerDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceTimer(registry, ip);
    }

    @Produces
    @VendorDefined
    private Timer produceTimer(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, Timed.class, registry::getTimers, registry::timer,
                Timer.class);
    }

    @Produces
    private Histogram produceHistogramDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceHistogram(registry, ip);
    }

    @Produces
    @VendorDefined
    private Histogram produceHistogram(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, null, registry::getHistograms, registry::histogram,
                Histogram.class);
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
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        return (Gauge<T>) registry.getGauges().entrySet().stream()
                .filter(entry -> entry.getKey().equals(metric.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not produce Gauge for injection point " + ip.toString()))
                .getValue();
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

    private <T extends org.eclipse.microprofile.metrics.Metric, U extends Annotation> T produceMetric(MetricRegistry registry,
            InjectionPoint ip, Class<U> annotationClass, Supplier<Map<String, T>> getTypedMetricsFn,
            Function<Metadata, T> registerFn, Class<T> clazz) {

        Metric metricAnno = ip.getAnnotated().getAnnotation(Metric.class);
        final String metricName = getName(metricAnno, ip);
        T result = getTypedMetricsFn.get().get(metricName);
        final Metadata newMetadata = newMetadata(ip, metricAnno, MetricType.from(clazz));
        /*
         * If the injection point does not include the corresponding metric  annotation which would
         * declare the metric, then we do not need to enforce reuse restrictions because an @Inject
         * or a @Metric by itself on an injection point is lookup-or-register.
         */
        if (result != null) {
            final Annotation specificMetricAnno = annotationClass == null ? null
                    : ip.getAnnotated().getAnnotation(annotationClass);
            if (specificMetricAnno == null) {
                return result;
            }
            final Metadata existingMetadata = registry.getMetadata().get(metricName);
            enforceReusability(metricName, existingMetadata, newMetadata);
        }
        if (result == null) {
            result = registerFn.apply(newMetadata);
        }
        return result;
    }

    private static void enforceReusability(String metricName, Metadata existingMetadata,
            Metadata newMetadata) {
        if (existingMetadata.isReusable() != newMetadata.isReusable()) {
            throw new IllegalArgumentException("Attempt to reuse metric " + metricName
                    + " with inconsistent isReusable setting");
        }
        if (!newMetadata.isReusable()) {
            throw new IllegalArgumentException("Attempting to reuse metric "
                    + metricName + " that is not reusable");
        }
    }
}
