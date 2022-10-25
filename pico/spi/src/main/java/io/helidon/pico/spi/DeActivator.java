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

package io.helidon.pico.spi;

import io.helidon.pico.api.Contract;

/**
 * DeActivators are responsible for lifecycle, transitioning a {@link ServiceProvider}'s from its current phase through any
 * {@link jakarta.annotation.PreDestroy} method invocations, and into the
 * {@link ActivationPhase#DESTROYED} phase. These are inverse agents of {@link Activator}.
 *
 * @param <T> the type to deactivate
 * @see io.helidon.pico.spi.Activator
 */
@Contract
public interface DeActivator<T> {

    /**
     * Deactivate a managed service. This will trigger any {@link jakarta.annotation.PreDestroy} method on the
     * underlying service type instance.
     *
     * @param targetServiceProvider the service provider responsible for calling deactivate
     * @param throwOnFailure        indicates whether the provider should throw if an error is observed
     * @return the result
     */
    ActivationResult<T> deactivate(ServiceProvider<T> targetServiceProvider,
                                   boolean throwOnFailure);

    /**
     * Deactivate a managed service. This will trigger any {@link jakarta.annotation.PreDestroy} method on the
     * underlying service type instance.
     *
     * @param targetServiceProvider the service provider responsible for calling deactivate
     * @return the result
     */
    default ActivationResult<T> deactivate(ServiceProvider<T> targetServiceProvider) {
        return deactivate(targetServiceProvider, true);
    }

}
