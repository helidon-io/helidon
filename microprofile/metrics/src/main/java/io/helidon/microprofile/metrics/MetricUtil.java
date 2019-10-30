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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * Class MetricUtil.
 */
public final class MetricUtil {

    private static final Logger LOGGER = Logger.getLogger(MetricUtil.class.getName());

    private MetricUtil() {
    }

    @SuppressWarnings("unchecked")
    static <E extends Member & AnnotatedElement, A extends Annotation>
    LookupResult<A> lookupAnnotation(E element, Class<? extends Annotation> annotClass, Class<?> clazz) {
        // First check annotation on element
        A annotation = (A) element.getAnnotation(annotClass);
        if (annotation != null) {
            return new LookupResult<>(MatchingType.METHOD, annotation);
        }
        // Finally check annotations on class
        annotation = (A) element.getDeclaringClass().getAnnotation(annotClass);
        if (annotation == null) {
            annotation = (A) clazz.getAnnotation(annotClass);
        }
        return annotation == null ? null : new LookupResult<>(MatchingType.CLASS, annotation);
    }

    /**
     * Determine the name to use for a metric.
     *
     * @param element the annotated element
     * @param clazz the annotated class
     * @param matchingType the type that is annotated
     * @param explicitName the optional explicit name to use
     * @param absolute {@code true} if the name is absolute, {@code false} if it is relative
     * @param <E> the type of the annotated element
     *
     * @return the name to use for a metric
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
                result = declaringClass.getName() + "." + result;
            }
        } else if (matchingType == MatchingType.CLASS) {
            if (explicitName == null || explicitName.isEmpty()) {
                result = getElementName(element, clazz);
                if (!absolute) {
                    result = clazz.getName() + "." + result;
                }
            } else {
                // absolute?
                result = clazz.getPackage().getName() + "." + explicitName
                        + "." + getElementName(element, clazz);
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
        MetricRegistry registry = getMetricRegistry();

        if (annotation instanceof Counted) {
            Counted counted = (Counted) annotation;
            String metricName = getMetricName(element, clazz, type, counted.name(), counted.absolute());
            Metadata meta = new Metadata(metricName,
                                         counted.displayName(),
                                         counted.description(),
                                         MetricType.COUNTER,
                                         counted.unit(),
                                         toTags(counted.tags()));
            registry.counter(meta);
            LOGGER.log(Level.FINE, () -> "### Registered counter " + metricName);
        } else if (annotation instanceof Metered) {
            Metered metered = (Metered) annotation;
            String metricName = getMetricName(element, clazz, type, metered.name(), metered.absolute());
            Metadata meta = new Metadata(metricName,
                                         metered.displayName(),
                                         metered.description(),
                                         MetricType.METERED,
                                         metered.unit(),
                                         toTags(metered.tags()));
            registry.meter(meta);
            LOGGER.log(Level.FINE, () -> "### Registered meter " + metricName);
        } else if (annotation instanceof Timed) {
            Timed timed = (Timed) annotation;
            String metricName = getMetricName(element, clazz, type, timed.name(), timed.absolute());
            Metadata meta = new Metadata(metricName,
                                         timed.displayName(),
                                         timed.description(),
                                         MetricType.TIMER,
                                         timed.unit(),
                                         toTags(timed.tags()));
            registry.timer(meta);
            LOGGER.log(Level.FINE, () -> "### Registered timer " + metricName);
        }
    }

    private static MetricRegistry getMetricRegistry() {
        return RegistryProducer.getDefaultRegistry();
    }

    static String toTags(String[] tags) {
        if (null == tags || tags.length == 0) {
            return "";
        }
        return String.join(",", tags);
    }

    static <E extends Member & AnnotatedElement>
    String getElementName(E element, Class<?> clazz) {
        return element instanceof Constructor ? clazz.getSimpleName() : element.getName();
    }

    /**
     * An enum used to indicate whether a metric annotation
     * applies to a class or a method.
     */
    public enum MatchingType {
        /**
         * The metric annotation applies to a method.
         */
        METHOD,
        /**
         * The metric annotation applies to a class.
         */
        CLASS
    }

    static class LookupResult<A extends Annotation> {

        private final MatchingType type;

        private final A annotation;

        /**
         * Constructor.
         *
         * @param type       The type of matching.
         * @param annotation The annotation.
         */
        LookupResult(MatchingType type, A annotation) {
            this.type = type;
            this.annotation = annotation;
        }

        public MatchingType getType() {
            return type;
        }

        public A getAnnotation() {
            return annotation;
        }
    }
}
