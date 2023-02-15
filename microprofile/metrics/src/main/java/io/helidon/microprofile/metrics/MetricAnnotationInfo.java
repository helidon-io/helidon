/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * Captures information and logic for dealing with the various metrics annotations. This allows the metrics handling to be
 * largely data-driven rather than requiring separate code for each type of metric.
 *
 * @param <A> the type of the specific metrics annotation
 */
class MetricAnnotationInfo<A extends Annotation, T extends Metric> {

    static MetricAnnotationInfo<?, ?> info(MetricType metricType) {
        return METRIC_TYPE_TO_INFO.get(metricType);
    }

    static MetricAnnotationInfo<?, ?> info(Class<?> metricClass) {
        return info(MetricType.from(metricClass));
    }

    static MetricAnnotationInfo<?, ?> infoFromAnnotationType(Class<? extends Annotation> annotationClass) {
        return METRIC_TYPE_TO_INFO.get(ANNOTATION_TYPE_TO_METRIC_TYPE.get(annotationClass));
    }

    private static final Map<Class<? extends Annotation>, MetricType> ANNOTATION_TYPE_TO_METRIC_TYPE =
            Map.of(ConcurrentGauge.class, MetricType.CONCURRENT_GAUGE,
                   Counted.class, MetricType.COUNTER,
                   Metered.class, MetricType.METERED,
                   SimplyTimed.class, MetricType.SIMPLE_TIMER,
                   Timed.class, MetricType.TIMER);

    private static final EnumMap<MetricType, MetricAnnotationInfo<?, ?>> METRIC_TYPE_TO_INFO = new EnumMap<>(MetricType.class) {
        {
            put(MetricType.COUNTER, new MetricAnnotationInfo<>(
                    Counted.class,
                    Counted::name,
                    Counted::absolute,
                    Counted::description,
                    Counted::displayName,
                    Counted::unit,
                    Counted::tags,
                    MetricRegistry::counter,
                    MetricType.COUNTER));
            put(MetricType.METERED, new MetricAnnotationInfo<>(
                    Metered.class,
                    Metered::name,
                    Metered::absolute,
                    Metered::description,
                    Metered::displayName,
                    Metered::unit,
                    Metered::tags,
                    MetricRegistry::meter,
                    MetricType.METERED));
            put(MetricType.TIMER, new MetricAnnotationInfo<>(
                    Timed.class,
                    Timed::name,
                    Timed::absolute,
                    Timed::description,
                    Timed::displayName,
                    Timed::unit,
                    Timed::tags,
                    MetricRegistry::timer,
                    MetricType.TIMER));
            put(MetricType.CONCURRENT_GAUGE, new MetricAnnotationInfo<>(
                    ConcurrentGauge.class,
                    ConcurrentGauge::name,
                    ConcurrentGauge::absolute,
                    ConcurrentGauge::description,
                    ConcurrentGauge::displayName,
                    ConcurrentGauge::unit,
                    ConcurrentGauge::tags,
                    MetricRegistry::concurrentGauge,
                    MetricType.CONCURRENT_GAUGE));
            put(MetricType.SIMPLE_TIMER, new MetricAnnotationInfo<>(
                    SimplyTimed.class,
                    SimplyTimed::name,
                    SimplyTimed::absolute,
                    SimplyTimed::description,
                    SimplyTimed::displayName,
                    SimplyTimed::unit,
                    SimplyTimed::tags,
                    MetricRegistry::simpleTimer,
                    MetricType.SIMPLE_TIMER));
            put(MetricType.HISTOGRAM, new MetricAnnotationInfo<>(
                    MetricRegistry::histogram,
                    MetricType.HISTOGRAM));
        }
    };

    private final Class<A> annotationClass;
    private final Function<A, String> annotationNameFunction;
    private final Function<A, Boolean> annotationAbsoluteFunction;
    private final Function<A, String> annotationDescriptorFunction;
    private final Function<A, String> annotationDisplayNameFunction;
    private final Function<A, String> annotationUnitsFunction;
    private final Function<A, String[]> annotationTagsFunction;
    private final Registration<T> registerFunction;
    private final MetricType metricType;

    /**
     * Creates a new {@code MetricAnnotationInfo} for a metric type with no annotation support (e.g., histogram).
     *
     * @param registerFunction function for registering a metric of the specified type
     * @param metricType {@code MetricType} of interest
     */
    MetricAnnotationInfo(Registration<T> registerFunction,
                         MetricType metricType) {
        this(null, null, null, null, null, null, null, registerFunction, metricType);
    }

    /**
     * Creates a new {@code MetricAnnotationInfo} for a metric type that has a corresponding annotation.
     *
     * @param annotationClass               the annotation class (e.g., {@code Counted}
     * @param annotationNameFunction        function which returns the name from the annotation
     * @param annotationAbsoluteFunction    function which returns whether the annotation specifies absolute naming
     * @param annotationDescriptorFunction  function which returns the description from the annotation
     * @param annotationDisplayNameFunction function which returns the display name from the annotation
     * @param annotationUnitsFunction       function which returns the units specified by the annotation
     * @param annotationTagsFunction        function which returns the tags specified by the annotation
     * @param registerFunction              function which registers a metric of the indicated type
     * @param metricType                    the {@code MetricType} of interest
     */
    MetricAnnotationInfo(
            Class<A> annotationClass,
            Function<A, String> annotationNameFunction,
            Function<A, Boolean> annotationAbsoluteFunction,
            Function<A, String> annotationDescriptorFunction,
            Function<A, String> annotationDisplayNameFunction,
            Function<A, String> annotationUnitsFunction,
            Function<A, String[]> annotationTagsFunction,
            Registration<T> registerFunction,
            MetricType metricType) {
        this.annotationClass = annotationClass;
        this.annotationNameFunction = annotationNameFunction;
        this.annotationAbsoluteFunction = annotationAbsoluteFunction;
        this.annotationDescriptorFunction = annotationDescriptorFunction;
        this.annotationDisplayNameFunction = annotationDisplayNameFunction;
        this.annotationUnitsFunction = annotationUnitsFunction;
        this.annotationTagsFunction = annotationTagsFunction;
        this.registerFunction = registerFunction;
        this.metricType = metricType;
    }

    Class<A> annotationClass() {
        return annotationClass;
    }

    A annotationOnMethod(AnnotatedElement ae) {
        return ae.getAnnotation(annotationClass);
    }

    String name(Annotation a) {
        return annotationNameFunction.apply(annotationClass.cast(a));
    }

    boolean absolute(Annotation a) {
        return annotationAbsoluteFunction.apply(annotationClass.cast(a));
    }

    String displayName(Annotation a) {
        return annotationDisplayNameFunction.apply(annotationClass.cast(a));
    }

    String description(Annotation a) {
        return annotationDescriptorFunction.apply(annotationClass.cast(a));
    }

    String unit(Annotation a) {
        return annotationUnitsFunction.apply(annotationClass.cast(a));
    }

    Tag[] tags(Annotation a) {
        return MetricUtil.tags(annotationTagsFunction.apply(annotationClass.cast(a)));
    }

    MetricType metricType() {
        return metricType;
    }

    Registration<T> registerFunction() {
        return registerFunction;
    }

    @FunctionalInterface
    interface Registration<T extends Metric> {
        T register(MetricRegistry registry, Metadata metadata, Tag... tags);
    }
}
