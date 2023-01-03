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

package io.helidon.pico;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;

import io.helidon.builder.Builder;

/**
 * Represents the result of a service activation or deactivation.
 *
 * @see Activator
 * @see DeActivator
 *
 * @param <T> The type of the associated activator
 */
@Builder
public interface ActivationResult<T> {

    /**
     * The service provider undergoing activation or deactivation.
     *
     * @return the service provider generating the result
     */
    ServiceProvider<T> serviceProvider();

    /**
     * Optionally, given by the implementation provider to indicate the future completion when the provider's
     * {@link ActivationStatus} is {@link ActivationStatus#WARNING_SUCCESS_BUT_NOT_READY}.
     *
     * @return the future result, assuming how activation can be async in nature
     */
    Optional<Future<ActivationResult<T>>> finishedActivationResult();

    /**
     * The activation phase that was found at onset of the phase transition.
     *
     * @return the starting phase
     */
    ActivationPhase startingActivationPhase();

    /**
     * The activation phase that was requested at the onset of the phase transition.
     *
     * @return the target, desired, ultimate phase requested
     */
    ActivationPhase ultimateTargetActivationPhase();

    /**
     * The activation phase we finished successfully on.
     *
     * @return the actual finishing phase
     */
    ActivationPhase finishingActivationPhase();

    /**
     * How did the activation finish.
     *
     * @return the finishing status
     */
    ActivationStatus finishingStatus();

    /**
     * The containing activation log that tracked this result.
     *
     * @return the activation log
     */
    Optional<ActivationLog> activationLog();

    /**
     * The services registry that was used.
     *
     * @return the services registry
     */
    Optional<Services> services();

    /**
     * Any vendor/provider implementation specific codes.
     *
     * @return the status code, 0 being the normal/default value
     */
    int statusCode();

    /**
     * Any vendor/provider implementation specific description.
     *
     * @return a developer friendly description (useful if an error occurs)
     */
    Optional<String> statusDescription();

    /**
     * Any throwable/exceptions that were observed during activation.
     *
     * @return the captured error
     */
    Optional<Throwable> error();

    /**
     * Returns true if this result is finished.
     *
     * @return true if finished
     */
    default boolean finished() {
        Future<ActivationResult<T>> f = finishedActivationResult().orElse(null);
        return (Objects.isNull(f) || f.isDone());
    }

    /**
     * Returns true if this result is successful.
     *
     * @return true if successful
     */
    default boolean success() {
        return finishingStatus() != ActivationStatus.FAILURE;
    }

}
