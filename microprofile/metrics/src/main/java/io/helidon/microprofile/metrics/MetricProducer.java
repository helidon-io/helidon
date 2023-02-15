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

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

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
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Metric;

/**
 * Class MetricProducer.
 */
@ApplicationScoped
class MetricProducer {

    private static Metadata newMetadata(InjectionPoint ip, Metric metric, MetricType metricType) {
        return Metadata.builder()
                .withName(MetricUtil.metricName(metric, ip))
                .withDisplayName(MetricUtil.normalize(metric, Metric::displayName))
                .withDescription(MetricUtil.normalize(metric, Metric::description))
                .withType(metricType)
                .withUnit(metric == null ? MetricUtil.chooseDefaultUnit(metricType) : metric.unit())
                .build();
    }

    @Produces
    private Counter produceCounter(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, registry::getCounters, registry::counter, Counter.class);
    }

    @Produces
    private Meter produceMeter(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, registry::getMeters, registry::meter, Meter.class);
    }

    @Produces
    private Timer produceTimer(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, registry::getTimers, registry::timer, Timer.class);
    }

    @Produces
    private SimpleTimer produceSimpleTimer(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, registry::getSimpleTimers, registry::simpleTimer,
                SimpleTimer.class);
    }

    @Produces
    private Histogram produceHistogram(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, registry::getHistograms, registry::histogram, Histogram.class);
    }

    @Produces
    private ConcurrentGauge produceConcurrentGauge(MetricRegistry registry, InjectionPoint ip) {
        return produceMetric(registry, ip, registry::getConcurrentGauges, registry::concurrentGauge, ConcurrentGauge.class);
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
     * using the caller-provided function. If the injection point yields metadata inconsistent with an existing metric's
     * metadata then the method throws an {@code IllegalArgumentException}.
     *
     * @param <T> the type of the metric
     * @param registry metric registry to use
     * @param ip the injection point
     * @param getTypedMetricsFn caller-provided factory for creating the correct
     * type of metric (if there is no pre-existing one)
     * @param registerFn caller-provided function for registering a newly-created metric
     * @param clazz class for the metric type of interest
     * @return the existing metric (if any), or the newly-created and registered one
     */
    private <T extends org.eclipse.microprofile.metrics.Metric> T produceMetric(MetricRegistry registry,
            InjectionPoint ip, Supplier<Map<MetricID, T>> getTypedMetricsFn,
            BiFunction<Metadata, Tag[], T> registerFn, Class<T> clazz) {

        final Metric metricAnno = ip.getAnnotated().isAnnotationPresent(Metric.class)
                ? ip.getAnnotated().getAnnotation(Metric.class)
                : null;
        final Tag[] tags = MetricUtil.tags(metricAnno);
        final MetricID metricID = new MetricID(MetricUtil.metricName(metricAnno, ip), tags);

        T result = getTypedMetricsFn.get().get(metricID);
        if (result != null) {
            if (metricAnno == null) {
                return result;
            }
            Metadata existingMetadata = registry.getMetadata(metricID.getName());
            if (!MetricUtil.checkConsistentMetadata(metricID.getName(), existingMetadata, MetricType.from(clazz), metricAnno)) {
                throw new IllegalArgumentException(String.format("""
                                                                         Attempt to inject previously-registered metric %s with \
                                                                         metadata %s using inconsistent @Metric settings %s from \
                                                                         the injection site %s""",
                                                                 metricID.getName(),
                                                                 existingMetadata,
                                                                 metricAnno,
                                                                 ip.getAnnotated()));
            }
        } else {
            final Metadata newMetadata = newMetadata(ip, metricAnno, MetricType.from(clazz));
            result = registerFn.apply(newMetadata, tags);
        }
        return result;
    }
}
