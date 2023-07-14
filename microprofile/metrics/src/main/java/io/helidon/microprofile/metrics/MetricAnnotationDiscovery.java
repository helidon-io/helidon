/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

/**
 * Conveys information about the discovery of a metric annotation as it applies to an executable.
 * <p>
 * The discovery event describes the executable to which the metric annotation <em>applies</em>.
 * This is not necessarily where the annotation <em>appears</em>, because a metric annotation which appears on the
 * type applies to all methods and constructors  on that type.
 * In that case, the discovery event describes the discovery of the metric as applied
 * to the method or constructor, not to the type itself.
 * Further, a metric annotation declared at the type level triggers a separate discovery event for each constructor
 * and method on the type.
 * </p>
 */
public interface MetricAnnotationDiscovery {

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
     * Requests that the discovery be deactivated, thereby preventing it from triggering a metric registration.
     */
    void deactivate();

    /**
     * Requests that the default metrics interceptor not be used for the metric corresponding to the indicated annotation
     * which appears on this method.
     */
    void disableDefaultInterceptor();

    /**
     * Returns whether the discover is active (i.e., has not been deactivated).
     *
     * @return if the discovery is active
     */
    boolean isActive();

    /**
     * Discovery of an annotation of interest on a constructor.
     */
    interface OfConstructor extends MetricAnnotationDiscovery {

        /**
         * @return the configurator for the constructor on which an annotation of interest appears
         */
        AnnotatedConstructorConfigurator<?> configurator();
    }

    /**
     * Discovery of an annotation of interest on a method.
     */
    interface OfMethod extends MetricAnnotationDiscovery {

        /**
         * Returns the configurator for the method on which an annotation of interest appears.
         *
         * @return the configurator
         */
        AnnotatedMethodConfigurator<?> configurator();
    }
}
