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
import java.util.function.BiFunction;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * Class MetricProducer.
 */
@ApplicationScoped
class MetricProducer {

    private static Metadata newMetadata(InjectionPoint ip, Metric metric, MetricType metricType) {
        return metric == null ? Metadata.builder()
                    .withName(getName(ip))
                    .withDisplayName("")
                    .withDescription("")
                    .withType(metricType)
                    .withUnit(chooseDefaultUnit(metricType))
                    .build()
                : Metadata.builder()
                    .withName(getName(metric, ip))
                    .withDisplayName(metric.displayName())
                    .withDescription(metric.description())
                    .withType(metricType)
                    .withUnit(metric.unit())
                    .build();
    }

    private static String chooseDefaultUnit(MetricType metricType) {
        String result;
        switch (metricType) {
            case METERED:
                result = MetricUnits.PER_SECOND;
                break;

            case TIMER:
                result = MetricUnits.NANOSECONDS;
                break;

            case SIMPLE_TIMER:
                result = MetricUnits.SECONDS;
                break;

            default:
                result = MetricUnits.NONE;
        }
        return result;
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
        return produceMetric(registry, ip, Counted.class, registry::counter, Counter.class);
    }

    @Produces
    private Meter produceMeter(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, Metered.class, registry::meter, Meter.class);
    }

    @Produces
    private Timer produceTimer(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, Timed.class, registry::timer, Timer.class);
    }

    @Produces
    private SimpleTimer produceSimpleTimer(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, SimplyTimed.class, registry::simpleTimer, SimpleTimer.class);
    }

    @Produces
    private Histogram produceHistogram(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, null, registry::histogram, Histogram.class);
    }

    @Produces
    private ConcurrentGauge produceConcurrentGauge(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, org.eclipse.microprofile.metrics.annotation.ConcurrentGauge.class,
                registry::concurrentGauge, ConcurrentGauge.class);
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
     * type of metric (if there is no pre-existing one)
     * @param registerFn caller-provided function for registering a newly-created metric
     * @param clazz class for the metric type of interest
     * @return the existing metric (if any), or the newly-created and registered one
     */
    @SuppressWarnings("unchecked")
    <T extends org.eclipse.microprofile.metrics.Metric, U extends Annotation> T produceMetric(MetricRegistry registry,
            InjectionPoint ip, Class<U> annotationClass, BiFunction<Metadata, Tag[], T> registerFn, Class<T> clazz) {

        final Metric metricAnno = ip.getAnnotated().getAnnotation(Metric.class);
        final Tag[] tags = tags(metricAnno);
        final MetricID metricID = new MetricID(getName(metricAnno, ip), tags);

        T result = (T) registry.getMetric(metricID);
        if (result != null) {
            final Annotation specificMetricAnno = annotationClass == null ? null
                    : ip.getAnnotated().getAnnotation(annotationClass);
            if (specificMetricAnno == null) {
                return result;
            }

        } else {
            final Metadata newMetadata = newMetadata(ip, metricAnno, MetricType.from(clazz));
            result = registerFn.apply(newMetadata, tags);
        }
        return result;
    }
}
