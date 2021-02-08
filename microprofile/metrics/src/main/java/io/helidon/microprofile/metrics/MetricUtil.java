/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import io.helidon.common.servicesupport.cdi.LookupResult;
import io.helidon.common.servicesupport.cdi.MatchingType;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * Class MetricUtil.
 */
public final class MetricUtil {
    private static final Logger LOGGER = Logger.getLogger(MetricUtil.class.getName());

    private MetricUtil() {
    }

    /**
     * This method is intended only for other Helidon components.
     *
     * @param element such as method
     * @param clazz class
     * @param matchingType type to match
     * @param explicitName name
     * @param absolute if absolute
     * @param <E> type of element
     *
     * @return name of the metric
     */
    public static <E extends Member & AnnotatedElement>
    String getMetricName(E element, Class<?> clazz, MatchingType matchingType, String explicitName, boolean absolute) {
        String result;
        if (matchingType == MatchingType.METHOD) {
            result = explicitName == null || explicitName.isEmpty()
                    ? getElementName(element, clazz) : explicitName;
            if (!absolute) {
                Class<?> declaringClass = clazz;
                if (element instanceof Method) {
                    // We need to find the declaring class if a method
                    List<Method> methods = Arrays.asList(declaringClass.getDeclaredMethods());
                    while (!methods.contains(element)) {
                        declaringClass = declaringClass.getSuperclass();
                        methods = Arrays.asList(declaringClass.getDeclaredMethods());
                    }
                }
                result = declaringClass.getName() + '.' + result;
            }
        } else if (matchingType == MatchingType.CLASS) {
            if (explicitName == null || explicitName.isEmpty()) {
                result = getElementName(element, clazz);
                if (!absolute) {
                    result = clazz.getName() + '.' + result;
                }
            } else {
                // Absolute must be false at class level, issue warning here
                if (absolute) {
                    LOGGER.warning(() -> "Attribute 'absolute=true' in metric annotation ignored at class level");
                }
                result = clazz.getPackage().getName() + '.' + explicitName
                        + '.' + getElementName(element, clazz);
            }
        } else {
            throw new InternalError("Unknown matching type");
        }
        return result;
    }

    /**
     * Register a metric.
     *
     * @param element the annotated element
     * @param clazz the annotated class
     * @param lookupResult the annotation lookup result
     * @param <E> the annotated element type
     */
    public static <E extends Member & AnnotatedElement>
    void registerMetric(E element, Class<?> clazz, LookupResult<? extends Annotation> lookupResult) {
        registerMetric(element, clazz, lookupResult.getAnnotation(), lookupResult.getType());
    }

    /**
     * Register a metric.
     *
     * @param element the annotated element
     * @param clazz the annotated class
     * @param annotation the annotation to register
     * @param type the {@link MatchingType} indicating the type of annotated element
     * @param <E> the annotated element type
     */
    public static <E extends Member & AnnotatedElement>
    void registerMetric(E element, Class<?> clazz, Annotation annotation, MatchingType type) {
        registerMetric(getMetricRegistry(), element, clazz, annotation, type);
    }

    /**
     * Register a metric.
     *
     * @param registry the metric registry in which to register the metric
     * @param element the annotated element
     * @param clazz the annotated class
     * @param annotation the annotation to register
     * @param type the {@link MatchingType} indicating the type of annotated element
     * @param <E> the annotated element type
     */
    public static <E extends Member & AnnotatedElement>
    void registerMetric(MetricRegistry registry, E element, Class<?> clazz, Annotation annotation, MatchingType type) {

        if (annotation instanceof Counted) {
            Counted counted = (Counted) annotation;
            String metricName = getMetricName(element, clazz, type, counted.name().trim(), counted.absolute());
            String displayName = counted.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(counted.description().trim())
                    .withType(MetricType.COUNTER)
                    .withUnit(counted.unit().trim())
                    .reusable(counted.reusable()).build();
            registry.counter(meta, tags(counted.tags()));
            LOGGER.fine(() -> "### Registered counter " + metricName);
        } else if (annotation instanceof Metered) {
            Metered metered = (Metered) annotation;
            String metricName = getMetricName(element, clazz, type, metered.name().trim(), metered.absolute());
            String displayName = metered.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(metered.description().trim())
                    .withType(MetricType.METERED)
                    .withUnit(metered.unit().trim())
                    .reusable(metered.reusable()).build();
            registry.meter(meta, tags(metered.tags()));
            LOGGER.fine(() -> "### Registered meter " + metricName);
        } else if (annotation instanceof ConcurrentGauge) {
            ConcurrentGauge concurrentGauge = (ConcurrentGauge) annotation;
            String metricName = getMetricName(element, clazz, type, concurrentGauge.name().trim(),
                    concurrentGauge.absolute());
            String displayName = concurrentGauge.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(concurrentGauge.description().trim())
                    .withType(MetricType.METERED)
                    .withUnit(concurrentGauge.unit().trim()).build();
            registry.concurrentGauge(meta, tags(concurrentGauge.tags()));
            LOGGER.fine(() -> "### Registered ConcurrentGauge " + metricName);
        } else if (annotation instanceof Timed) {
            Timed timed = (Timed) annotation;
            String metricName = getMetricName(element, clazz, type, timed.name().trim(), timed.absolute());
            String displayName = timed.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(timed.description().trim())
                    .withType(MetricType.TIMER)
                    .withUnit(timed.unit().trim())
                    .reusable(timed.reusable()).build();
            registry.timer(meta, tags(timed.tags()));
            LOGGER.fine(() -> "### Registered timer " + metricName);
        } else if (annotation instanceof SimplyTimed) {
            SimplyTimed simplyTimed = (SimplyTimed) annotation;
            String metricName = getMetricName(element, clazz, type, simplyTimed.name().trim(), simplyTimed.absolute());
            String displayName = simplyTimed.displayName().trim();
            Metadata meta = Metadata.builder()
                    .withName(metricName)
                    .withDisplayName(displayName.isEmpty() ? metricName : displayName)
                    .withDescription(simplyTimed.description().trim())
                    .withType(MetricType.SIMPLE_TIMER)
                    .withUnit(simplyTimed.unit().trim())
                    .reusable(simplyTimed.reusable()).build();
            registry.simpleTimer(meta, tags(simplyTimed.tags()));
            LOGGER.fine(() -> "### Registered simple timer " + metricName);
        }
    }

    private static MetricRegistry getMetricRegistry() {
        return RegistryProducer.getDefaultRegistry();
    }

    static <E extends Member & AnnotatedElement>
    String getElementName(E element, Class<?> clazz) {
        return element instanceof Constructor ? clazz.getSimpleName() : element.getName();
    }

    static Tag[] tags(String[] tagStrings) {
        final List<Tag> result = new ArrayList<>();
        for (int i = 0; i < tagStrings.length; i++) {
            final int eq = tagStrings[i].indexOf("=");
            if (eq > 0) {
                final String tagName = tagStrings[i].substring(0, eq);
                final String tagValue = tagStrings[i].substring(eq + 1);
                result.add(new Tag(tagName, tagValue));
            }
        }
        return result.toArray(new Tag[result.size()]);
    }

}
