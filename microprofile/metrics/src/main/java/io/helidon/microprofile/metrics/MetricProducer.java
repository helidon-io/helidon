/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

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
import org.eclipse.microprofile.metrics.Tag;
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
    private Counter produceCounterDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceCounter(registry, ip);
    }

    @Produces
    @VendorDefined
    private Counter produceCounter(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, Counted.class, registry::getCounters,
                registry::counter, Counter.class);
    }

    @Produces
    private Meter produceMeterDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceMeter(registry, ip);
    }

    @Produces
    @VendorDefined
    private Meter produceMeter(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, Metered.class, registry::getMeters,
                registry::meter, Meter.class);
    }

    @Produces
    private Timer produceTimerDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceTimer(registry, ip);
    }

    @Produces
    @VendorDefined
    private Timer produceTimer(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, Timed.class, registry::getTimers, registry::timer, Timer.class);
    }

    @Produces
    private Histogram produceHistogramDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceHistogram(registry, ip);
    }

    @Produces
    @VendorDefined
    private Histogram produceHistogram(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, null, registry::getHistograms,
                registry::histogram, Histogram.class);
    }

    @Produces
    private ConcurrentGauge produceConcurrentGaugeDefault(MetricRegistry registry, InjectionPoint ip) {
        return produceConcurrentGauge(registry, ip);
    }

    @Produces
    @VendorDefined
    private ConcurrentGauge produceConcurrentGauge(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, org.eclipse.microprofile.metrics.annotation.ConcurrentGauge.class,
                registry::getConcurrentGauges, registry::concurrentGauge, ConcurrentGauge.class);
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
    private <T /* extends Number */> Gauge<T> produceGauge(MetricRegistry registry, InjectionPoint ip) {
        // TODO uncomment preceding clause once MP metrics enforces restriction
        Metric metric = ip.getAnnotated().getAnnotation(Metric.class);
        return (Gauge<T>) registry.getGauges().entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals(metric.name()))
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
    private <T /* extends Number */> Gauge<T> produceGaugeDefault(MetricRegistry registry,
            InjectionPoint ip) {
        // TODO uncomment preceding clause once MP metrics enforces restrictions
        return produceGauge(registry, ip);
    }

    /**
     * Returns an existing metric if one exists that matches the injection point
     * criteria and is also reusable, or if there is none registers and returns a new one
     * using the caller-provided function. If the caller refers to an existing metric that is
     * not reusable then the method throws an {@code IllegalArgumentException}.
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
            final Metadata existingMetadata = registry.getMetadata().get(metricID.getName());
            enforceReusability(metricID, existingMetadata, newMetadata);
        } else {
            result = registerFn.apply(newMetadata, tags);
        }
        return result;
    }

    private static void enforceReusability(MetricID metricID, Metadata existingMetadata,
              Metadata newMetadata, Tag... tags) {
        if (existingMetadata.isReusable() != newMetadata.isReusable()) {
            throw new IllegalArgumentException("Attempt to reuse metric " + metricID
                    + " with inconsistent isReusable setting");
        }
        if (!newMetadata.isReusable()) {
            throw new IllegalArgumentException("Attempting to reuse metric "
                    + metricID + " that is not reusable");
        }
    }
}
