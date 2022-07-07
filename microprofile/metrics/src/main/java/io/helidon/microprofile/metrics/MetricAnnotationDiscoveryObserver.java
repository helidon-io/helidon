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
 * Observer of the discovery of metric annotations which are applied to constructors and methods.
 * <p>
 *     Implementations make themselves known via the Java service loader mechanism.
 * </p>
 * <p>
 *     Once registered, the observer is notified later, during {@code ProcessAnnotatedType}, for each metric annotation that is
 *     discovered to apply to an executable, via a
 *     {@link io.helidon.microprofile.metrics.MetricAnnotationDiscoveryObserver.MetricAnnotationDiscovery} event.
 * </p>
 */
public interface MetricAnnotationDiscoveryObserver {

   /**
     * Notifies the observer that a metric annotation has been discovered to apply to a constructor or method.
     *
     * @param metricAnnotationDiscovery the discovery event
     */
    void onDiscovery(MetricAnnotationDiscovery metricAnnotationDiscovery);

    /**
     * Conveys information about the discovery of a metric annotation as it applies to an executable.
     * <p>
     *     The discovery event describes the executable to which the metric annotation <em>applies</em>.
     *     This is not necessarily where the annotation <em>appears</em>, because a metric annotation which appears on the
     *     type applies to all methods and constructors  on that type.
     *     In that case, the discovery event describes the discovery of the metric as applied
     *     to the method or constructor, not to the type itself.
     *     Further, a metric annotation declared at the type level triggers a separate discovery event for each constructor
     *     and method on the type.
     * </p>
     */
    interface MetricAnnotationDiscovery {

        /**
         * Returns the configurator for the annotated type containing the site to which the metric annotation applies.
         *
         * @return the configurator for the annotated type
         */
        AnnotatedTypeConfigurator<?> annotatedTypeConfigurator();

        /**
         * Returns the {@link java.lang.annotation.Annotation} object for the metric annotation discovered.
         *
         * @return the annotation object for the metrics annotation
         */
        Annotation annotation();

        /**
         * Requests that the default metrics interceptor not be used for the metric corresponding to the indicated annotation
         * which appears on this method.
         */
        void disableDefaultInterceptor();

        /**
         * Discovery of an annotation of interest on a constructor.
         */
        interface OfConstructor extends MetricAnnotationDiscovery {

            /**
             *
             * @return the configurator for the constructor on which an annotation of interest appears
             */
            AnnotatedConstructorConfigurator<?> configurator();
        }

        /**
         * Discovery of an annotation of interest on a method.
         */
        interface OfMethod extends MetricAnnotationDiscovery {

            /**
             *
             * @return the configurotor for the method on which an annotation of interest appears
             */
            AnnotatedMethodConfigurator<?> configurator();
        }
    }
}
