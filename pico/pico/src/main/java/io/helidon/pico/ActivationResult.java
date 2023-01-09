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

package io.helidon.pico;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;

import io.helidon.builder.Builder;
import io.helidon.pico.spi.BasicInjectionPlan;

/**
 * Represents the result of a service activation or deactivation.
 *
 * @see Activator
 * @see DeActivator
 **/
@Builder
public interface ActivationResult {

    /**
     * The service provider undergoing activation or deactivation.
     *
     * @return the service provider generating the result
     */
    ServiceProvider<?> serviceProvider();

    /**
     * Optionally, given by the implementation provider to indicate the future completion when the provider's
     * {@link ActivationStatus} is {@link ActivationStatus#WARNING_SUCCESS_BUT_NOT_READY}.
     *
     * @return the future result, assuming how activation can be async in nature
     */
    Optional<Future<ActivationResult>> finishedActivationResult();

    /**
     * The activation phase that was found at onset of the phase transition.
     *
     * @return the starting phase
     */
    Phase startingActivationPhase();

    /**
     * The activation phase that was requested at the onset of the phase transition.
     *
     * @return the target, desired, ultimate phase requested
     */
    Phase targetActivationPhase();

    /**
     * The activation phase we finished successfully on, or are otherwise currently in if not yet finished.
     *
     * @return the finishing phase
     */
    Phase finishingActivationPhase();

    /**
     * How did the activation finish.
     * Will only be populated if the lifecycle event has completed - see {@link #finishedActivationResult()}.
     *
     * @return the finishing status
     */
    Optional<ActivationStatus> finishingStatus();

    /**
     * The injection plan that was found or determined, key'ed by each element's {@link ServiceProvider#id()}.
     *
     * @return the resolved injection plan map
     */
    Map<String, ? extends BasicInjectionPlan> injectionPlans();

    /**
     * The dependencies that were resolved or loaded, key'ed by each element's {@link ServiceProvider#id()}.
     *
     * @return the resolved dependency map
     */
    Map<String, Object> resolvedDependencies();

    /**
     * Set to true if the injection plan in {@link #resolvedDependencies()} has been resolved and can be "trusted" as being
     * complete and accurate.
     *
     * @return true if was resolved
     */
    boolean wasResolved();

    /**
     * Any throwable/exceptions that were observed during activation.
     *
     * @return any captured error
     */
    Optional<Throwable> error();

    /**
     * Returns true if this result is finished.
     *
     * @return true if finished
     */
    default boolean finished() {
        Future<ActivationResult> f = finishedActivationResult().orElse(null);
        return (Objects.isNull(f) || f.isDone());
    }

    /**
     * Returns true if this result was successful.
     *
     * @return true if successful
     */
    default boolean success() {
        return finishingStatus().orElse(null) != ActivationStatus.FAILURE;
    }

    /**
     * Returns true if this result was unsuccessful.
     *
     * @return true if unsuccessful
     */
    default boolean failure() {
        return !success();
    }

    /**
     * Creates a successful result.
     *
     * @param serviceProvider the service provider
     * @return the result
     */
    static ActivationResult createSuccess(
            ServiceProvider<?> serviceProvider) {
        Phase phase = serviceProvider.currentActivationPhase();
        return DefaultActivationResult.builder()
                .serviceProvider(serviceProvider)
                .startingActivationPhase(phase)
                .finishingActivationPhase(phase)
                .targetActivationPhase(phase)
                .finishingStatus(ActivationStatus.SUCCESS)
                .build();
    }

}
