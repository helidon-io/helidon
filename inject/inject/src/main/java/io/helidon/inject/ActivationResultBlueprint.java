/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Represents the result of a service activation or deactivation.
 *
 * @see ManagedService
 **/
@Prototype.Blueprint
interface ActivationResultBlueprint {

    /**
     * The service provider undergoing activation or deactivation.
     *
     * @return the service provider generating the result
     */
    ManagedService<?> serviceProvider();

    /**
     * The activation phase that was found at onset of the phase transition.
     *
     * @return the starting phase
     */
    @Option.Default("INIT")
    Phase startingActivationPhase();

    /**
     * The activation phase that was requested at the onset of the phase transition.
     *
     * @return the target, desired, ultimate phase requested
     */
    @Option.Default("INIT")
    Phase targetActivationPhase();

    /**
     * The activation phase we finished successfully on, or are otherwise currently in if not yet finished.
     *
     * @return the finishing phase
     */
    Phase finishingActivationPhase();

    /**
     * How did the activation finish.
     *
     * @return the finishing status
     */
    ActivationStatus finishingStatus();

    /**
     * Any throwable/exceptions that were observed during activation.
     *
     * @return any captured error
     */
    Optional<Throwable> error();

    /**
     * Returns true if this result was successful.
     *
     * @return true if successful
     */
    default boolean success() {
        return finishingStatus() != ActivationStatus.FAILURE;
    }

    /**
     * Returns true if this result was unsuccessful.
     *
     * @return true if unsuccessful
     */
    default boolean failure() {
        return !success();
    }
}
