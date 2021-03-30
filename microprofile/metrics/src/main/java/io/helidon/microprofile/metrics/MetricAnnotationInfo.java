/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.helidon.microprofile.metrics.MetricUtil.MatchingType;

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

    /**
     * Encapulates information for preparing for a metric registration based on an annotation and an annotated element.
     *
     * @param <T> type of the metric to be registered
     */
    static class RegistrationPrep<T extends Metric> {
        private final String metricName;
        private final Metadata metadata;
        private final Tag[] tags;
        private final Registration<T> registration;

        static <A extends Annotation, E extends Member & AnnotatedElement, T extends Metric>
        RegistrationPrep<?> create(A annotation,
                E annotatedElement,
                Class<?> clazz,
                MatchingType matchingType) {
            MetricAnnotationInfo<?, ?> info = ANNOTATION_TYPE_TO_INFO.get(annotation.annotationType());
            if (info != null && info.annotationClass().isInstance(annotation)) {
                MetricAnnotationInfo<A, T> typedInfo = (MetricAnnotationInfo<A, T>) info;

                String metricName = MetricUtil.getMetricName(annotatedElement, clazz, matchingType, typedInfo.name(annotation),
                        typedInfo.absolute(annotation));
                String candidateDisplayName = typedInfo.displayName(annotation);
                Metadata metadata = Metadata.builder()
                        .withName(metricName)
                        .withDisplayName(candidateDisplayName.isEmpty() ? metricName : candidateDisplayName)
                        .withDescription(typedInfo.description(annotation)
                                .trim())
                        .withType(ANNOTATION_TYPE_TO_METRIC_TYPE.get(annotation.annotationType()))
                        .withUnit(typedInfo.unit(annotation)
                                .trim())
                        .reusable(typedInfo.reusable(annotation))
                        .build();
                return new RegistrationPrep<>(metricName, metadata, typedInfo.tags(annotation), typedInfo.registerFunction);
            }
            return null;
        }

        private RegistrationPrep(String metricName, Metadata metadata, Tag[] tags, Registration<T> registration) {
            this.metricName = metricName;
            this.metadata = metadata;
            this.tags = tags;
            this.registration = registration;
        }

        String metricName() {
            return metricName;
        }

        Tag[] tags() {
            return tags;
        }

        T register(MetricRegistry registry) {
            return registration.register(registry, metadata, tags);
        }
    }

    static final Map<Class<? extends Annotation>, MetricType> ANNOTATION_TYPE_TO_METRIC_TYPE =
            Map.of(ConcurrentGauge.class, MetricType.CONCURRENT_GAUGE,
                Counted.class, MetricType.COUNTER,
                Metered.class, MetricType.METERED,
                SimplyTimed.class, MetricType.SIMPLE_TIMER,
                Timed.class, MetricType.TIMER);

    static final Map<Class<? extends Annotation>, MetricAnnotationInfo<?, ?>> ANNOTATION_TYPE_TO_INFO = Map.of(
            Counted.class, new MetricAnnotationInfo<>(
                    Counted.class,
                    Counted::name,
                    Counted::absolute,
                    Counted::description,
                    Counted::displayName,
                    Counted::reusable,
                    Counted::unit,
                    Counted::tags,
                    MetricRegistry::counter),
            Metered.class, new MetricAnnotationInfo<>(
                    Metered.class,
                    Metered::name,
                    Metered::absolute,
                    Metered::description,
                    Metered::displayName,
                    Metered::reusable,
                    Metered::unit,
                    Metered::tags,
                    MetricRegistry::meter),
            Timed.class, new MetricAnnotationInfo<>(
                    Timed.class,
                    Timed::name,
                    Timed::absolute,
                    Timed::description,
                    Timed::displayName,
                    Timed::reusable,
                    Timed::unit,
                    Timed::tags,
                    MetricRegistry::timer),
            ConcurrentGauge.class, new MetricAnnotationInfo<>(
                    ConcurrentGauge.class,
                    ConcurrentGauge::name,
                    ConcurrentGauge::absolute,
                    ConcurrentGauge::description,
                    ConcurrentGauge::displayName,
                    ConcurrentGauge::reusable,
                    ConcurrentGauge::unit,
                    ConcurrentGauge::tags,
                    MetricRegistry::concurrentGauge),
            SimplyTimed.class, new MetricAnnotationInfo<>(
                    SimplyTimed.class,
                    SimplyTimed::name,
                    SimplyTimed::absolute,
                    SimplyTimed::description,
                    SimplyTimed::displayName,
                    SimplyTimed::reusable,
                    SimplyTimed::unit,
                    SimplyTimed::tags,
                    MetricRegistry::simpleTimer)
    );

    private final Class<A> annotationClass;
    private final Function<A, String> annotationNameFunction;
    private final Function<A, Boolean> annotationAbsoluteFunction;
    private final Function<A, String> annotationDescriptorFunction;
    private final Function<A, String> annotationDisplayNameFunction;
    private final Function<A, Boolean> annotationReusableFunction;
    private final Function<A, String> annotationUnitsFunction;
    private final Function<A, String[]> annotationTagsFunction;
    private final Registration<T> registerFunction;


    MetricAnnotationInfo(
            Class<A> annotationClass,
            Function<A, String> annotationNameFunction,
            Function<A, Boolean> annotationAbsoluteFunction,
            Function<A, String> annotationDescriptorFunction,
            Function<A, String> annotationDisplayNameFunction,
            Function<A, Boolean> annotationReusableFunction,
            Function<A, String> annotationUnitsFunction,
            Function<A, String[]> annotationTagsFunction,
            Registration<T> registerFunction) {
        this.annotationClass = annotationClass;
        this.annotationNameFunction = annotationNameFunction;
        this.annotationAbsoluteFunction = annotationAbsoluteFunction;
        this.annotationDescriptorFunction = annotationDescriptorFunction;
        this.annotationDisplayNameFunction = annotationDisplayNameFunction;
        this.annotationReusableFunction = annotationReusableFunction;
        this.annotationUnitsFunction = annotationUnitsFunction;
        this.annotationTagsFunction = annotationTagsFunction;
        this.registerFunction = registerFunction;
    }

    static Tag[] tags(String[] tagStrings) {
        final List<Tag> result = new ArrayList<>();
        for (String tagString : tagStrings) {
            final int eq = tagString.indexOf("=");
            if (eq > 0) {
                final String tagName = tagString.substring(0, eq);
                final String tagValue = tagString.substring(eq + 1);
                result.add(new Tag(tagName, tagValue));
            }
        }
        return result.toArray(new Tag[0]);
    }

    Class<A> annotationClass() {
        return annotationClass;
    }

    A annotationOnMethod(AnnotatedElement ae) {
        return ae.getAnnotation(annotationClass);
    }

//    String name(AnnotatedElement ae) {
//        return name(ae.getAnnotation(annotationClass));
//    }

    String name(A a) {
        return annotationNameFunction.apply(a);
    }

    boolean absolute(A a) {
        return annotationAbsoluteFunction.apply(a);
    }

    String displayName(A a) {
        return annotationDisplayNameFunction.apply(a);
    }

    String description(A a) {
        return annotationDescriptorFunction.apply(a);
    }

    boolean reusable(A a) {
        return annotationReusableFunction.apply(a);
    }

    String unit(A a) {
        return annotationUnitsFunction.apply(a);
    }

    Tag[] tags(A a) {
        return tags(annotationTagsFunction.apply(a));
    }

//    boolean absolute(AnnotatedElement ae) {
//        return absolute(ae.getAnnotation(annotationClass));
//    }
//
//    String displayName(AnnotatedElement ae) {
//        return annotationDisplayNameFunction.apply(ae.getAnnotation(annotationClass));
//    }

//    String description(AnnotatedElement ae) {
//        return annotationDescriptorFunction.apply(ae.getAnnotation(annotationClass));
//    }

//    boolean reusable(AnnotatedElement ae) {
//        return annotationReusableFunction.apply(ae.getAnnotation(annotationClass));
//    }
//
//    String unit(AnnotatedElement ae) {
//        return annotationUnitsFunction.apply(ae.getAnnotation(annotationClass));
//    }
//
//    Tag[] tags(AnnotatedElement ae) {
//        return tags(annotationTagsFunction.apply(ae.getAnnotation(annotationClass)));
//    }

//    T register(MetricRegistry registry, Metadata metadata, AnnotatedElement ae) {
//        return registerFunction.register(registry, metadata, tags(ae));
//    }

    @FunctionalInterface
    interface Registration<T> {
        T register(MetricRegistry registry, Metadata metadata, Tag... tags);
    }
}
