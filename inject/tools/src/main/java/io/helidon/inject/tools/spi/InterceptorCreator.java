/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tools.spi;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.common.types.TypeName;
import io.helidon.inject.api.Contract;
import io.helidon.inject.api.InterceptedTrigger;
import io.helidon.inject.api.ServiceInfoBasics;
import io.helidon.inject.tools.InterceptionPlan;
import io.helidon.inject.tools.InterceptorCreatorProvider;

/**
 * Provides the strategy used to determine which annotations cause interceptor creation. Only services that are injection-
 * activated may qualify for interception.
 *
 * @see InterceptorCreatorProvider
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Contract
public interface InterceptorCreator {

    /**
     * Determines the strategy being applied.
     *
     * @return the strategy being applied
     */
    default Strategy strategy() {
        return Strategy.BLENDED;
    }

    /**
     * Applicable when {@link InterceptorCreator.Strategy#ALLOW_LISTED} is in use.
     *
     * @return the set of type names that should trigger creation
     */
    default Set<TypeName> allowListedAnnotationTypes() {
        return Set.of();
    }

    /**
     * Applicable when {@link InterceptorCreator.Strategy#CUSTOM} is in use.
     *
     * @param annotationType the annotation type name
     * @return true if the annotation type should trigger interceptor creation
     */
    default boolean isAllowListed(TypeName annotationType) {
        Objects.requireNonNull(annotationType);
        return allowListedAnnotationTypes().contains(annotationType);
    }

    /**
     * Returns the reflection based interceptor processor.
     *
     * @param interceptedService    the service being intercepted
     * @param delegateCreator       the "real" creator
     * @return the processor to use for the given arguments
     */
    InterceptorProcessor createInterceptorProcessor(ServiceInfoBasics interceptedService,
                                                    InterceptorCreator delegateCreator);

    /**
     * Returns the annotation based interceptor processor.
     *
     * @param interceptedService    the service being intercepted
     * @param delegateCreator       the "real" creator
     * @param processEnv            the processing environment (should be passed if in annotation processor)
     * @return the processor to use for the given arguments
     */
    InterceptorProcessor createInterceptorProcessor(ServiceInfoBasics interceptedService,
                                                    InterceptorCreator delegateCreator,
                                                    ProcessingEnvironment processEnv);

    /**
     * The strategy applied for resolving annotations that trigger interception.
     */
    enum Strategy {

        /**
         * Meta-annotation based. Only annotations annotated with {@link InterceptedTrigger} will
         * qualify.
         */
        EXPLICIT,

        /**
         * All annotations marked as {@link java.lang.annotation.RetentionPolicy#RUNTIME} will qualify, which implicitly
         * will also cover all usages of {@link #EXPLICIT}.
         */
        ALL_RUNTIME,

        /**
         * A call to {@link #allowListedAnnotationTypes()} will be used to determine which annotations qualify. The
         * implementation may then cache this result internally for optimal processing.
         */
        ALLOW_LISTED,

        /**
         * A call to {@link #isAllowListed(io.helidon.common.types.TypeName)} will be used on a case-by-case basis to check
         * which annotation types qualify.
         */
        CUSTOM,

        /**
         * No annotations will qualify in triggering interceptor creation.
         */
        NONE,

        /**
         * Applies a blend of {@link #EXPLICIT} and {@link #CUSTOM} to determine which annotations qualify (i.e., if
         * the annotation is not explicitly marked, then a call is still issued to
         * {@link #isAllowListed(io.helidon.common.types.TypeName)}. This
         * strategy is typically the default strategy type in use.
         */
        BLENDED

    }

    /**
     * Abstraction for interceptor processing.
     */
    interface InterceptorProcessor {

        /**
         * The set of annotation types that are trigger interception.
         *
         * @return the set of annotation types that are trigger interception
         */
        Set<TypeName> allAnnotationTypeTriggers();

        /**
         * Creates the interception plan.
         *
         * @param interceptorAnnotationTriggers the annotation type triggering the interception creation.
         * @return the plan, or empty if there is no interception needed
         */
        Optional<InterceptionPlan> createInterceptorPlan(Set<TypeName> interceptorAnnotationTriggers);

    }

}
