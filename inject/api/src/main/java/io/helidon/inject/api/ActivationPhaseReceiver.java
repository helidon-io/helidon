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
 * A receiver of events from the {@link Services} registry and providers held by the service registry.
 * <p>
 * Note that only {@link ServiceProvider}'s implement this contract that are also bound to the global
 * {@link Services} registry are currently capable of receiving events.
 *
 * @see ServiceProviderBindable
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface ActivationPhaseReceiver {

    /**
     * Called when there is an event transition within the service registry.
     *
     * @param event the event
     * @param phase the phase
     */
    void onPhaseEvent(Event event,
                      Phase phase);

}
