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

package io.helidon.inject.api;

/**
 * DeActivators are responsible for lifecycle, transitioning a {@link ServiceProvider} through its
 * {@link Phase}'s, notably including any
 * {@link jakarta.annotation.PreDestroy} method invocations, and finally into the terminal
 * {@link Phase#DESTROYED} phase. These phase transitions are the inverse of {@link Activator}.
 *
 * @see Activator
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface DeActivator {

    /**
     * Deactivate a managed service. This will trigger any {@link jakarta.annotation.PreDestroy} method on the
     * underlying service type instance.
     *
     * @param request deactivation request
     * @return the result
     */
    ActivationResult deactivate(DeActivationRequest request);

}
