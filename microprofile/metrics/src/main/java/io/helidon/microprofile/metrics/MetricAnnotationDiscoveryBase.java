/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.lang.reflect.Member;
import java.util.StringJoiner;

import jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

/**
 * Implementation of metrics annotation discovery event.
 * <p>
 *     The {@link jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator} and
 *     {@link jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator} interfaces share no common ancestor, so
 *     we have two subtypes of discovery, one for each:
 *     {@link MetricAnnotationDiscoveryBase.OfConstructor OfConstructor} and
 *     {@link MetricAnnotationDiscovery.OfMethod ofMethod}.
 * </p>
 *
 */
abstract class MetricAnnotationDiscoveryBase implements MetricAnnotationDiscovery {

    private final AnnotatedTypeConfigurator<?> annotatedTypeConfigurator;
    private final Annotation annotation;

    private boolean isActive = true;
    private boolean useDefaultInterceptor = true;

    private MetricAnnotationDiscoveryBase(AnnotatedTypeConfigurator<?> annotatedTypeConfigurator,
                                          Annotation annotation) {
        this.annotatedTypeConfigurator = annotatedTypeConfigurator;
        this.annotation = annotation;
    }

    static <C> MetricAnnotationDiscoveryBase create(
            AnnotatedTypeConfigurator<?> annotatedTypeConfigurator,
            C annotatedCallableConfigurator,
            Annotation annotation) {
        if (annotatedCallableConfigurator instanceof AnnotatedConstructorConfigurator<?>) {
            return new OfConstructor(annotatedTypeConfigurator,
                                     (AnnotatedConstructorConfigurator<?>) annotatedCallableConfigurator,
                                     annotation);
        } else if (annotatedCallableConfigurator instanceof AnnotatedMethodConfigurator) {
            return new OfMethod(annotatedTypeConfigurator,
                                (AnnotatedMethodConfigurator<?>) annotatedCallableConfigurator,
                                annotation);
        } else {
            throw new IllegalArgumentException(String.format("annotatedCallableConfigurator must be of type %s or %s",
                                                             AnnotatedConstructorConfigurator.class.getName(),
                                                             AnnotatedMethodConfigurator.class.getName()));
        }
    }

    @Override
    public AnnotatedTypeConfigurator<?> annotatedTypeConfigurator() {
        return annotatedTypeConfigurator;
    }

    @Override
    public Annotation annotation() {
        return annotation;
    }

    @Override
    public void deactivate() {
        isActive = false;
        disableDefaultInterceptor();
    }

    @Override
    public void disableDefaultInterceptor() {
        useDefaultInterceptor = false;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("annotatedConfigurator=" + annotatedMember())
                .add("annotatedTypeConfigurator=" + annotatedTypeConfigurator.getAnnotated().getJavaClass().getName())
                .add("annotation=" + annotation)
                .add("isActive=" + isActive)
                .add("useDefaultInterceptor=" + useDefaultInterceptor)
                .toString();
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    boolean shouldUseDefaultInterceptor() {
        return useDefaultInterceptor;
    }

    protected abstract Member annotatedMember();

    private static class OfConstructor extends MetricAnnotationDiscoveryBase
            implements MetricAnnotationDiscovery.OfConstructor {

        private final AnnotatedConstructorConfigurator<?> configurator;

        private OfConstructor(
                AnnotatedTypeConfigurator<?> annotatedTypeConfigurator,
                AnnotatedConstructorConfigurator<?> annotatedConstructorConfigurator,
                Annotation annotation) {
            super(annotatedTypeConfigurator, annotation);
            configurator = annotatedConstructorConfigurator;
        }

        @Override
        public AnnotatedConstructorConfigurator<?> configurator() {
            return configurator;
        }

        @Override
        protected Member annotatedMember() {
            return configurator.getAnnotated().getJavaMember();
        }
    }

    private static class OfMethod extends MetricAnnotationDiscoveryBase
            implements MetricAnnotationDiscovery.OfMethod {

        private final AnnotatedMethodConfigurator<?> configurator;

        private OfMethod(
                AnnotatedTypeConfigurator<?> annotatedTypeConfigurator,
                AnnotatedMethodConfigurator<?> annotatedMethodConfigurator,
                Annotation annotation) {
            super(annotatedTypeConfigurator, annotation);
            configurator = annotatedMethodConfigurator;
        }

        @Override
        public AnnotatedMethodConfigurator<?> configurator() {
            return configurator;
        }

        @Override
        protected Member annotatedMember() {
            return configurator.getAnnotated().getJavaMember();
        }
    }
}
