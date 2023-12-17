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

package io.helidon.inject;

/**
 * A receiver of events from the {@link io.helidon.inject.Services} registry and providers held by the service registry.
 * <p>
 * Note that only {@link io.helidon.inject.ServiceProvider}'s implement this contract that are also bound to the global
 * {@link io.helidon.inject.Services} registry are currently capable of receiving events.
 *
 * @see io.helidon.inject.ServiceProviderBindable
 */
public interface ActivationPhaseReceiver {

    /**
     * Called when there is an event transition within the service registry.
     *
     * @param phase the phase
     */
    void onPhaseEvent(Phase phase);

}
