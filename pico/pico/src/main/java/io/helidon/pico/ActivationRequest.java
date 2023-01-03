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

import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Request to activate a service.
 *
 * @param <T> service type
 */
@Builder
public interface ActivationRequest<T> {

    /**
     * Target service provider.
     *
     * @return service provider
     */
    ServiceProvider<T> serviceProvider();

    /**
     * Injection point context information.
     *
     * @return injection point info
     */
    Optional<InjectionPointInfo> injectionPoint();

    /**
     * Ultimate target phase for activation.
     *
     * @return phase to target
     */
    ActivationPhase targetPhase();

    /**
     * Whether to throw an exception on failure to activate, or return an error activation result on activation.
     *
     * @return whether to throw on failure
     */
    @ConfiguredOption("true")
    boolean throwOnFailure();

}
