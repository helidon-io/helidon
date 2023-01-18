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

import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.common.LazyValue;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Request to activate a service.
 */
@Builder
public interface ActivationRequest {

    /**
     * Default request.
     */
    LazyValue<ActivationRequest> DEFAULT = LazyValue.create(() -> DefaultActivationRequest.builder().build());

    /**
     * Optionally, the injection point context information.
     *
     * @return injection point info
     */
    Optional<InjectionPointInfo> injectionPoint();

    /**
     * The phase to start activation. Typically, this should be left as the default (i.e., PENDING).
     *
     * @return phase to start
     */
    Optional<Phase> startingPhase();

    /**
     * Ultimate target phase for activation.
     *
     * @return phase to target
     */
    @ConfiguredOption("ACTIVE")
    Phase targetPhase();

    /**
     * Whether to throw an exception on failure to activate, or return an error activation result on activation.
     *
     * @return whether to throw on failure
     */
    @ConfiguredOption("true")
    boolean throwIfError();

    /**
     * Creates a new activation request.
     *
     * @param targetPhase       the target phase
     * @return the activation request
     */
    static ActivationRequest create(
            Phase targetPhase) {
        return DefaultActivationRequest.builder()
                .targetPhase(targetPhase)
                .build();
    }

}
