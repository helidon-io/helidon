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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * Class MetricProducer.
 */
@ApplicationScoped
class MetricProducer {

    private static Metadata newMetadata(InjectionPoint ip,
                                        Metric metric,
                                        Class<? extends org.eclipse.microprofile.metrics.Metric> metricType) {
        return metric == null ? Metadata.builder()
                .withName(getName(ip))
                .withDescription("")
                .withUnit(chooseDefaultUnit(metricType))
                .build()
                : Metadata.builder()
                        .withName(getName(metric, ip))
                        .withDescription(metric.description())
                        .withUnit(metric.unit())
                        .build();
    }

    private static String chooseDefaultUnit(Class<? extends org.eclipse.microprofile.metrics.Metric> metricType) {
        return Timer.class.isAssignableFrom(metricType) ? MetricUnits.NANOSECONDS : MetricUnits.NONE;
    }

    private static Tag[] tags(Metric metric) {
        if (metric == null || metric.tags() == null) {
            return null;
        }
        final List<Tag> result = new ArrayList<>();
        for (String tag : metric.tags()) {
            if (tag != null) {
                final int eq = tag.indexOf("=");
                if (eq > 0) {
                    result.add(new Tag(tag.substring(0, eq), tag.substring(eq + 1)));
                }
            }
        }
        return result.toArray(new Tag[result.size()]);
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
    private Counter produceCounter(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, Counted.class, registry::getCounters,
                registry::counter, Counter.class);
    }

    @Produces
    private Timer produceTimer(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, Timed.class, registry::getTimers, registry::timer, Timer.class);
    }

    @Produces
    private Histogram produceHistogram(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, null, registry::getHistograms,
                registry::histogram, Histogram.class);
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
    @SuppressWarnings("unchecked")
    private <T extends Number> Gauge<T> produceGauge(MetricRegistry registry, InjectionPoint ip) {
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        return (Gauge<T>) registry.getGauges().entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals(metric.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not produce Gauge for injection point " + ip.toString()))
                .getValue();
    }

    /**
     * Returns an existing metric if one exists that matches the injection point
     * criteria, or if there is none registers and returns a new one
     * using the caller-provided function.
     *
     * @param <T> the type of the metric
     * @param <U> the type of the annotation which marks a registration of the metric type
     * @param registry metric registry to use
     * @param ip the injection point
     * @param annotationClass annotation which represents a declaration of a metric
     * @param getTypedMetricsFn caller-provided factory for creating the correct
     * type of metric (if there is no pre-existing one)
     * @param registerFn caller-provided function for registering a newly-created metric
     * @param clazz class for the metric type of interest
     * @return the existing metric (if any), or the newly-created and registered one
     */
    private <T extends org.eclipse.microprofile.metrics.Metric, U extends Annotation> T produceMetric(MetricRegistry registry,
            InjectionPoint ip, Class<U> annotationClass, Supplier<Map<MetricID, T>> getTypedMetricsFn,
            BiFunction<Metadata, Tag[], T> registerFn, Class<T> clazz) {

        final Metric metricAnno = ip.getAnnotated().getAnnotation(Metric.class);
        final Tag[] tags = tags(metricAnno);
        final MetricID metricID = new MetricID(getName(metricAnno, ip), tags);

        T result = getTypedMetricsFn.get().get(metricID);
        if (result != null) {
            final Annotation specificMetricAnno = annotationClass == null ? null
                    : ip.getAnnotated().getAnnotation(annotationClass);
            if (specificMetricAnno == null) {
                return result;
            }

        } else {
            final Metadata newMetadata = newMetadata(ip, metricAnno, clazz);
            result = registerFn.apply(newMetadata, tags);
        }
        return result;
    }
}
