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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

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
        return result.toArray(new Tag[0]);
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
    private Counter produceCounter(InjectionPoint ip) {
        return produceMetric(ip, Counted.class, MetricRegistry::counter, Counter.class);
    }

    @Produces
    private Timer produceTimer(InjectionPoint ip) {
        return produceMetric(ip, Timed.class, MetricRegistry::timer, Timer.class);
    }

    @Produces
    private Histogram produceHistogram(InjectionPoint ip) {
        return produceMetric(ip, null, MetricRegistry::histogram, Histogram.class);
    }

    /**
     * Returns the {@link Gauge} matching the criteria from the injection point.
     *
     * <p>
     *     We cannot create a gauge on demand because gauges wrap externally-managed values and we cannot tell, from the
     *     injection point alone, how to create the value which a gauge wraps. So we insist that the gauge implied by the
     *     injection point exist.
     * </p>
     *
     * @param ip       injection point being resolved
     * @param <N> subtype of {@code Number} for the gauge
     * @return requested gauge
     */
    @Produces
    private <N extends Number> Gauge<N> produceGauge(InjectionPoint ip) {
        MetricLocator locator = MetricLocator.create(ip);
        Gauge<N> result = (Gauge<N>) locator.registry.getGauge(locator.metricId);
        if (result == null) {
            throw new IllegalArgumentException("Could not produce Gauge for injection point " + ip.toString());
        }
        return result;
    }

    /**
     * Returns an existing metric if one exists that matches the injection point
     * criteria, or if there is none registers and returns a new one
     * using the caller-provided function.
     *
     * @param <T> the type of the metric
     * @param <U> the type of the annotation which marks a registration of the metric type
     * @param ip the injection point
     * @param annotationClass annotation which represents a declaration of a metric
     * type of metric (if there is no pre-existing one)
     * @param registerFn caller-provided function for registering a newly-created metric
     * @param clazz class for the metric type of interest
     * @return the existing metric (if any), or the newly-created and registered one
     */
    private <T extends org.eclipse.microprofile.metrics.Metric, U extends Annotation> T produceMetric(
            InjectionPoint ip, Class<U> annotationClass,
            RegisterFunction<T> registerFn, Class<T> clazz) {

        MetricLocator locator = MetricLocator.create(ip);

        T result = locator.registry.getMetric(locator.metricId, clazz);
        if (result != null) {
            final Annotation specificMetricAnno = annotationClass == null ? null
                    : ip.getAnnotated().getAnnotation(annotationClass);
            if (specificMetricAnno == null) {
                return result;
            }

        } else {
            final Metadata newMetadata = newMetadata(ip, locator.metricAnno, clazz);
            result = registerFn.apply(locator.registry, newMetadata, locator.metricId.getTagsAsArray());
        }
        return result;
    }

    @FunctionalInterface
    private interface RegisterFunction<T extends org.eclipse.microprofile.metrics.Metric> {

        T apply(MetricRegistry metricRegistry, Metadata metadata, Tag[] tags);
    }

    private static Iterable<String> iterable(String value) {
        return () -> new Iterator<>() {

            private boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                hasNext = false;
                return value;
            }
        };
    }

    record MetricLocator(Metric metricAnno, Registry registry, MetricID metricId) {

        static MetricLocator create(InjectionPoint ip) {
            final Metric metricAnno = ip.getAnnotated().getAnnotation(Metric.class);
            final Tag[] tags = tags(metricAnno);
            final String scope = metricAnno == null ? MetricRegistry.APPLICATION_SCOPE : metricAnno.scope();
            Registry registry = RegistryFactory.getInstance().registry(scope);
            final MetricID metricID = new MetricID(getName(metricAnno, ip), tags);
            return new MetricLocator(metricAnno, registry, metricID);
        }
    }
}
