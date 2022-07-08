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
 *
 */
package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;

import jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

/**
 * Implementation of metrics annotation discovery event.
 * <p>
 *     The {@link jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator} and
 *     {@link jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator} interfaces share no common ancestor, so
 *     we have two subtypes of discovery, one for each:
 *     {@link io.helidon.microprofile.metrics.MetricAnnotationDiscoveryImpl.OfConstructor OfConstructor} and
 *     {@link io.helidon.microprofile.metrics.MetricAnnotationDiscoveryObserver.MetricAnnotationDiscovery.OfMethod ofMethod}.
 * </p>
 *
 */
abstract class MetricAnnotationDiscoveryImpl implements MetricAnnotationDiscoveryObserver.MetricAnnotationDiscovery {

    static <C> MetricAnnotationDiscoveryImpl create(
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


    private final AnnotatedTypeConfigurator<?> annotatedTypeConfigurator;
    private final Annotation annotation;

    private boolean keepDiscovery = true;
    private boolean disableDefaultInterceptor = false;

    private MetricAnnotationDiscoveryImpl(AnnotatedTypeConfigurator<?> annotatedTypeConfigurator,
                                          Annotation annotation) {
        this.annotatedTypeConfigurator = annotatedTypeConfigurator;
        this.annotation = annotation;
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
    public void discard() {
        keepDiscovery = false;
    }

    @Override
    public void disableDefaultInterceptor() {
        disableDefaultInterceptor = true;
    }

    boolean isValid() {
        return keepDiscovery;
    }

    boolean isDisableDefaultInterceptor() {
        return disableDefaultInterceptor;
    }

    private static class OfConstructor extends MetricAnnotationDiscoveryImpl
            implements MetricAnnotationDiscoveryObserver.MetricAnnotationDiscovery.OfConstructor {

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
    }

    private static class OfMethod extends MetricAnnotationDiscoveryImpl
            implements MetricAnnotationDiscoveryObserver.MetricAnnotationDiscovery.OfMethod {

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
    }
}
