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
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.helidon.microprofile.metrics.MetricUtil.MatchingType;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

/**
 * Captures information and logic for dealing with the various metrics annotations. This allows the metrics handling to be
 * largely data-driven rather than requiring separate code for each type of metric.
 *
 * @param <A> the type of the specific metrics annotation
 */
class MetricAnnotationInfo<A extends Annotation, T extends Metric> {

    /**
     * Encapsulates information for preparing for a metric registration based on an annotation and an annotated element.
     */
    static class RegistrationPrep {
        private final String metricName;
        private final Metadata metadata;
        private final Tag[] tags;
        private final Registration<?> registration;
        private final Executable executable;
        private final Class<? extends Annotation> annotationType;
        private final String scope;


        static <A extends Annotation, E extends Member & AnnotatedElement, T extends Metric>
        RegistrationPrep create(A annotation,
                E annotatedElement,
                Class<?> clazz,
                MatchingType matchingType,
                Executable executable) {
            MetricAnnotationInfo<?, ?> info = ANNOTATION_TYPE_TO_INFO.get(annotation.annotationType());
            if (info == null || !info.annotationClass().isInstance(annotation)) {
                return null;
            }

            String metricName = MetricUtil.getMetricName(annotatedElement, clazz, matchingType, info.name(annotation),
                    info.absolute(annotation));
            MetadataBuilder metadataBuilder = Metadata.builder()
                    .withName(metricName)
                    .withUnit(info.unit(annotation)
                            .trim());

            String candidateDescription = info.description(annotation);
            if (candidateDescription != null && !candidateDescription.trim().isEmpty()) {
                metadataBuilder.withDescription(candidateDescription.trim());
            }
            return new RegistrationPrep(metricName,
                                          metadataBuilder.build(),
                                          info.tags(annotation),
                                          info.registerFunction,
                                          executable,
                                          annotation.annotationType(),
                                          info.scope(annotation)
                                          );
        }

        private RegistrationPrep(String metricName,
                                 Metadata metadata,
                                 Tag[] tags,
                                 Registration<?> registration,
                                 Executable executable,
                                 Class<? extends Annotation> annotationType,
                                 String scope) {
            this.metricName = metricName;
            this.metadata = metadata;
            this.tags = tags;
            this.registration = registration;
            this.executable = executable;
            this.annotationType = annotationType;
            this.scope = scope;
        }

        String metricName() {
            return metricName;
        }

        Tag[] tags() {
            return tags;
        }

        Executable executable() {
            return executable;
        }

        Class<? extends Annotation> annotationType() {
            return annotationType;
        }

        Metadata metadata() {
            return metadata;
        }

        String scope() {
            return scope;
        }

        Metric register(MetricRegistry registry) {
            return registration.register(registry, metadata, tags);
        }
    }

    static final Map<Class<? extends Annotation>, Class<? extends Metric>> ANNOTATION_TYPE_TO_METRIC_TYPE =
            Map.of(Counted.class, Counter.class,
                Timed.class, Timer.class);

    static final Map<Class<? extends Annotation>, MetricAnnotationInfo<?, ?>> ANNOTATION_TYPE_TO_INFO = Map.of(
            Counted.class, new MetricAnnotationInfo<>(
                    Counted.class,
                    Counted::name,
                    Counted::absolute,
                    Counted::description,
                    Counted::unit,
                    Counted::tags,
                    Counted::scope,
                    MetricRegistry::counter,
                    Counter.class),
            Timed.class, new MetricAnnotationInfo<>(
                    Timed.class,
                    Timed::name,
                    Timed::absolute,
                    Timed::description,
                    Timed::unit,
                    Timed::tags,
                    Timed::scope,
                    MetricRegistry::timer,
                    Timer.class)
    );

    private final Class<A> annotationClass;
    private final Function<A, String> annotationNameFunction;
    private final Function<A, Boolean> annotationAbsoluteFunction;
    private final Function<A, String> annotationDescriptorFunction;
    private final Function<A, String> annotationUnitsFunction;
    private final Function<A, String[]> annotationTagsFunction;
    private final Function<A, String> annotationScopeFunction;
    private final Registration<T> registerFunction;
    private final Class<? extends Metric> metricType;


    MetricAnnotationInfo(
            Class<A> annotationClass,
            Function<A, String> annotationNameFunction,
            Function<A, Boolean> annotationAbsoluteFunction,
            Function<A, String> annotationDescriptorFunction,
            Function<A, String> annotationUnitsFunction,
            Function<A, String[]> annotationTagsFunction,
            Function<A, String> annotationScopeFunction,
            Registration<T> registerFunction,
            Class<? extends Metric> metricType) {
        this.annotationClass = annotationClass;
        this.annotationNameFunction = annotationNameFunction;
        this.annotationAbsoluteFunction = annotationAbsoluteFunction;
        this.annotationDescriptorFunction = annotationDescriptorFunction;
        this.annotationUnitsFunction = annotationUnitsFunction;
        this.annotationTagsFunction = annotationTagsFunction;
        this.annotationScopeFunction = annotationScopeFunction;
        this.registerFunction = registerFunction;
        this.metricType = metricType;
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

    String name(Annotation a) {
        return annotationNameFunction.apply(annotationClass.cast(a));
    }

    boolean absolute(Annotation a) {
        return annotationAbsoluteFunction.apply(annotationClass.cast(a));
    }

    String description(Annotation a) {
        return annotationDescriptorFunction.apply(annotationClass.cast(a));
    }

    String unit(Annotation a) {
        return annotationUnitsFunction.apply(annotationClass.cast(a));
    }

    Tag[] tags(Annotation a) {
        return tags(annotationTagsFunction.apply(annotationClass.cast(a)));
    }

    String scope(Annotation a) {
        return annotationScopeFunction.apply(annotationClass.cast(a));
    }

    Class<? extends Metric> metricType() {
        return metricType;
    }

    @FunctionalInterface
    interface Registration<T extends Metric> {
        T register(MetricRegistry registry, Metadata metadata, Tag... tags);
    }
}
