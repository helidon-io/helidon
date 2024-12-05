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

package io.helidon.service.registry;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Request to activate a service.
 */
@Prototype.Blueprint
interface ActivationRequestBlueprint {
    /**
     * The phase to start activation. Typically, this should be left as the default (i.e., PENDING).
     *
     * @return phase to start
     */
    Optional<ActivationPhase> startingPhase();

    /**
     * Ultimate target phase for activation.
     * <p>
     * Defaults to {@link ActivationPhase#ACTIVE}, unless configured otherwise (in the registry).
     *
     * @return phase to target
     */
    ActivationPhase targetPhase();

    /**
     * Whether to throw an exception on failure to activate, or return an error activation result on activation.
     *
     * @return whether to throw on failure
     */
    @Option.DefaultBoolean(true)
    boolean throwIfError();
}
