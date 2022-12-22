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

/**
 * DeActivators are responsible for lifecycle, transitioning a {@link ServiceProvider} through its
 * {@link io.helidon.pico.ActivationPhase}'s, notably including any
 * {@link jakarta.annotation.PreDestroy} method invocations, and finally into the terminal
 * {@link ActivationPhase#DESTROYED} phase. These phase transitions are the inverse of {@link Activator}.
 *
 * @param <T> the type to deactivate
 * @see Activator
 */
public interface DeActivator<T> {

    /**
     * Deactivate a managed service. This will trigger any {@link jakarta.annotation.PreDestroy} method on the
     * underlying service type instance.
     *
     * @param request deactivation request
     * @return the result
     */
    ActivationResult<T> deactivate(DeActivationRequest<T> request);
}
