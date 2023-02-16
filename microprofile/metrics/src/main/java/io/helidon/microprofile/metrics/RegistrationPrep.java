/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Encapsulates information for a pending metric registration based on an annotation and an annotated element.
 * <p>
 *     We need several concrete implementations because we need to get information from different AnnotatedXXX classes
 *     which share no common ancestry.
 * </p>
 */
abstract class RegistrationPrep {

    /**
     * Creates a pending registration for an intercepted executable (constructor or method annotated with one of the
     * metrics annotations in force: @Counted, etc.). The annotation might appear at the containing type level.
     *
     * @param annotation the metrics annotation which applies to the executable
     * @param annotatedElement the element annotated (constructor, method, type)
     * @param clazz the class of the metric (e.g., Counter)
     * @param matchingType which type of matching located the annotation (class, method, etc.)
     * @param executable the annotated constructor or method
     * @return the new intercept deferred registration
     * @param <A> the type of the annotation
     * @param <E> the type of the annotated element
     */
    static <A extends Annotation, E extends Member & AnnotatedElement>
    InterceptRegistrationPrep<A, E> create(A annotation,
                                     E annotatedElement,
                                     Class<?> clazz,
                                     MetricUtil.MatchingType matchingType,
                                     Executable executable) {

        MetricAnnotationInfo<?, ?> info = MetricAnnotationInfo.infoFromAnnotationType(annotation.annotationType());

        return new InterceptRegistrationPrep<>(info,
                                               annotation,
                                               annotatedElement,
                                               clazz,
                                               matchingType,
                                               executable);
    }

    /**
     * Creates am {@code Optional} around a deferred registration for an injected field, provided the injected field's type
     * is a metric; an empty {@code Optional} otherwise.
     *
     * @param annotatedField the injected field
     * @return {@code Optional} of the new deferred registration or {@code empty} if none
     */
    static Optional<InjectRegistrationPrep> create(AnnotatedField<?> annotatedField, Supplier<MetricRegistry> registry) {

        MetricAnnotationInfo<?, ?> info = MetricAnnotationInfo.info(annotatedField.getJavaMember().getType());
        if (info == null) {
            return Optional.empty();
        }

        return Optional.of(new InjectRegistrationPrep(info,
                  MetricUtil.metricName(annotatedField),
                  MetricUtil.tags(annotatedField.getAnnotation(org.eclipse.microprofile.metrics.annotation.Metric.class)),
                  annotatedField,
                  registry));
    }

    /**
     * Creates am {@code Optional} around a deferred registration for an annotated parameter, provided the parameter's type
     * is a metric; an empty {@code Optional} otherwise.
     *
     * @param annotatedParameter the annotated field
     * @return {@code Optional} of the new deferred registration or {@code empty} if none
     */
    static Optional<InjectRegistrationPrep> create(AnnotatedParameter<?> annotatedParameter, Supplier<MetricRegistry> registry) {
        MetricAnnotationInfo<?, ?> info = MetricAnnotationInfo.info(annotatedParameter.getJavaParameter().getType());
        if (info == null) {
            return Optional.empty();
        }
        return Optional.of(new InjectRegistrationPrep(info,
                  MetricUtil.metricName(annotatedParameter),
                  MetricUtil.tags(annotatedParameter.getAnnotation(org.eclipse.microprofile.metrics.annotation.Metric.class)),
                  annotatedParameter,
                  registry));
    }

    private final MetricAnnotationInfo<?, ?> info;
    private final String metricName;
    private final Tag[] tags;

    /**
     * Common constructor for all concrete implementations of deferred registrations.
     *
     * @param info the metric annotation info object describing the annotation
     * @param metricName name of the metric derived from the annotated item
     * @param tags tags derived from the annotated item
     */
    protected RegistrationPrep(MetricAnnotationInfo<?, ?> info,
                               String metricName,
                               Tag[] tags) {
        this.info = info;
        this.metricName = metricName;
        this.tags = tags;
    }

    /**
     *
     * @return the metric name for the deferred registration
     */
    String metricName() {
        return metricName;
    }

    /**
     *
     * @return the tags for the deferred registration (non-null)
     */
    Tag[] tags() {
        return tags;
    }

    /**
     *
     * @return the metric annotation info for the deferred registration
     */
    MetricAnnotationInfo<?, ?> info() {
        return info;
    }

    /**
     * Deferred registration for metrics inferred from metrics annotations (e.g., {@code Counted}).
     *
     * @param <A> type of the annotation
     * @param <E> type of the annotated item
     */
    static class InterceptRegistrationPrep<A extends Annotation, E extends Member & AnnotatedElement>
            extends RegistrationPrep {

        private final A annotation;
        private final Executable executable;

        private final LazyValue<Metadata> metadata = LazyValue.create(this::buildMetadata);

        private InterceptRegistrationPrep(MetricAnnotationInfo<?, ?> info,
                                          A annotation,
                                          E annotatedElement,
                                          Class<?> clazz,
                                          MetricUtil.MatchingType matchingType,
                                          Executable executable) {
            super(info,
                  MetricUtil.getMetricName(annotatedElement,
                                           clazz,
                                           matchingType,
                                           info.name(annotation),
                                           info.absolute(annotation)),
                  info.tags(annotation));

            this.annotation = annotation;
            this.executable = executable;
        }

        Metadata metadata() {
            return metadata.get();
        }

        /**
         *
         * @return the {@code Executable} corresponding to the metric
         */
        Executable executable() {
            return executable;
        }

        /**
         *
         * @return the type for the annotation applied to the executable triggering the deferred registration
         */
        Class<? extends Annotation> annotationType() {
            return annotation.annotationType();
        }

        /**
         * Returns the previously-registered metric with the same name and tags or a new metric derived from the injected
         * or annotated site.
         *
         * @param registry the metric registry in which to register the metric
         * @return the registered or pre-existing metric
         */
        Metric register(MetricRegistry registry) {
            // Registrations via annotated sites must be fully compatible with any prior registration of the same metric,
            // including consistency of metadata including reuse settings. All that checking occurs in the registry's logic,
            // so this code simply attempts the registration without redoing that checking here.

            return info().registerFunction().register(registry, metadata(), tags());
        }

        private Metadata buildMetadata() {
            return Metadata.builder()
                    .withName(metricName())
                    .withType(info().metricType())
                    .withUnit(info().unit(annotation).trim())
                    .withDescription(info().description(annotation))
                    .withDisplayName(info().displayName(annotation))
                    .build();
        }
    }

    /**
     * Common behavior of deferred registrations inferred from injection (fields or parameters).
     */
    static class InjectRegistrationPrep extends RegistrationPrep {

        private static final Logger LOGGER = System.getLogger(InjectRegistrationPrep.class.getName());

        private final Annotated annotated;

        private final LazyValue<MetricRegistry> registry;

        /**
         * Creates a new deferred registration for an injected parameter or field.
         *
         * @param info metric annotation information for the injected site
         * @param metricName name of the metric
         * @param tags tags for the metric implied by the injected site
         */
        protected InjectRegistrationPrep(MetricAnnotationInfo<?, ?> info,
                                         String metricName,
                                         Tag[] tags,
                                         Annotated annotated,
                                         Supplier<MetricRegistry> registry) {
            super(info, metricName, tags);
            this.annotated = annotated;
            this.registry = LazyValue.create(registry);
        }

        /**
         * Returns the previously-registered metric with the same name and tags or a new metric derived from the injected
         * or annotated site.
         *
         * @param registry the metric registry in which to register the metric
         * @param beanManager bean manager to use for fetching injected metrics to force their registration
         */
        void register(MetricRegistry registry, BeanManager beanManager, InjectionPoint injectionPoint) {

            // Have CDI use producers to warm up the injected metrics to use developer-provided producers, if any.
            Bean<?> bean = injectionPoint.getBean();
            CreationalContext<?> cc = beanManager.createCreationalContext(bean);
            String force = beanManager.getInjectableReference(injectionPoint, cc).toString();
            if (LOGGER.isLoggable(Logger.Level.DEBUG)) {
                LOGGER.log(Logger.Level.DEBUG, "Pre-fetched injected metric " + force);
            }
        }
    }
}
