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

package io.helidon.pico.tools.creator;

import java.util.Collections;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.pico.Contract;
import io.helidon.pico.ServiceInfoBasics;

/**
 * Provides the strategy used to determine which annotations cause interceptor creation. Only services that are pico-
 * activated may qualify for interception.
 */
@Contract
public interface InterceptorCreator {

    /**
     * The strategy applied for resolving annotations that trigger interception.
     */
    enum Strategy {
        /**
         * Meta-annotation based. Only annotations annotated with {@link io.helidon.pico.InterceptedTrigger} will
         * qualify.
         */
        EXPLICIT,

        /**
         * All annotations marked as {@link java.lang.annotation.RetentionPolicy#RUNTIME} will qualify, which implicitly
         * will also cover all usages of {@link #EXPLICIT}.
         */
        ALL_RUNTIME,

        /**
         * A call to {@link #getWhiteListedAnnotationTypes()} will be used to determine which annotations qualify. The
         * implementation may then cache this result internally for optimal processing.
         */
        WHITE_LISTED,

        /**
         * A call to {@link #isWhiteListed(String)} will be used on a case-by-case basis to check which annotation
         * types qualify.
         */
        CUSTOM,

        /**
         * No annotations will qualify in triggering interceptor creation.
         */
        NONE,

        /**
         * Applies a blend of {@link #EXPLICIT} and {@link #CUSTOM} to determine which annotations qualify (i.e., if
         * the annotation is not explicitly marked, then a call is still issued to {@link #isWhiteListed(String)}. This
         * strategy is typically the default strategy type in use.
         */
        BLENDED
    }

    /**
     * Determines the strategy in use.
     *
     * @return the strategy being applied.
     */
    default Strategy getStrategy() {
        return Strategy.BLENDED;
    }

    /**
     * Applicable when {@link io.helidon.pico.tools.creator.InterceptorCreator.Strategy#WHITE_LISTED} is in use.
     *
     * @return the set of Class<? extends Annotation> type names that should trigger creation.
     */
    default Set<String> getWhiteListedAnnotationTypes() {
        return Collections.emptySet();
    }

    /**
     * Applicable when {@link io.helidon.pico.tools.creator.InterceptorCreator.Strategy#CUSTOM} is in use.
     *
     * @param annotationType the annotation type name
     * @return true if the annotation type should trigger interceptor creation.
     */
    default boolean isWhiteListed(String annotationType) {
        return getWhiteListedAnnotationTypes().contains(annotationType);
    }

    /**
     * After an annotation qualifies the enclosing service for interception, this method will be used to provide
     * the injection plan that applies to that service type.
     *
     * @param interceptedService        the service being intercepted
     * @param processingEnvironment     optionally, the processing environment (if being called by annotation processing)
     * @param annotationTypeTriggers    the set of annotation names that are associated with interception.
     * @return the injection plan, or null for the implementation to use the default strategy for creating a plan
     */
    default InterceptionPlan createInterceptorPlan(ServiceInfoBasics interceptedService,
                                                   ProcessingEnvironment processingEnvironment,
                                                   Set<String> annotationTypeTriggers) {
        return null;
    }

}
