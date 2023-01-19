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

package io.helidon.pico.tools;

import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.pico.Contract;
import io.helidon.pico.ServiceInfoBasics;

/**
 * Provides the strategy used to determine which annotations cause interceptor creation. Only services that are pico-
 * activated may qualify for interception.
 *
 * @see io.helidon.pico.tools.spi.InterceptorCreatorProvider
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
         * A call to {@link #whiteListedAnnotationTypes()} will be used to determine which annotations qualify. The
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
     * Determines the strategy being applied
     *
     * @return the strategy being applied
     */
    default Strategy strategy() {
        return Strategy.BLENDED;
    }

    /**
     * Applicable when {@link Strategy#WHITE_LISTED} is in use.
     *
     * @return the set of type names that should trigger creation
     */
    default Set<String> whiteListedAnnotationTypes() {
        return Set.of();
    }

    /**
     * Applicable when {@link Strategy#CUSTOM} is in use.
     *
     * @param annotationType the annotation type name
     * @return true if the annotation type should trigger interceptor creation
     */
    default boolean isWhiteListed(
            String annotationType) {
        return whiteListedAnnotationTypes().contains(annotationType);
    }

    /**
     * After an annotation qualifies the enclosing service for interception, this method will be used to provide
     * the injection plan that applies to that service type.
     *
     * @param interceptedService        the service being intercepted
     * @param processingEnvironment     optionally, the processing environment (if being called by annotation processing)
     * @param annotationTypeTriggers    the set of annotation names that are associated with interception.
     * @return the injection plan, or empty for the implementation to use the default strategy for creating a plan
     */
    Optional<InterceptionPlan> createInterceptorPlan(
            ServiceInfoBasics interceptedService,
            ProcessingEnvironment processingEnvironment,
            Set<String> annotationTypeTriggers);

    /**
     * Returns the processor appropriate for the context revealed in the calling arguments, favoring reflection if
     * the serviceTypeElement is provided.
     *
     * @param interceptedService    the service being intercepted
     * @param delegateCreator       the "real" creator
     * @param processEnv            optionally, the processing environment (should be passed if in annotation processor)
     * @return the processor to use for the given arguments
     */
    InterceptorProcessor createInterceptorProcessor(
            ServiceInfoBasics interceptedService,
            InterceptorCreator delegateCreator,
            Optional<ProcessingEnvironment> processEnv);


    /**
     * Abstraction for interceptor processing.
     */
    interface InterceptorProcessor {

        /**
         * The set of annotation types that are trigger interception.
         *
         * @return the set of annotation types that are trigger interception
         */
        Set<String> allAnnotationTypeTriggers();

        /**
         * Creates the interception plan.
         *
         * @param interceptorAnnotationTriggers the annotation type triggering the interception creation.
         * @return the plan, or empty if there is no interception needed
         */
        Optional<InterceptionPlan> createInterceptorPlan(
                Set<String> interceptorAnnotationTriggers);

    }

}
