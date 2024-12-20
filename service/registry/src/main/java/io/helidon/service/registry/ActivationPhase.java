/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

/**
 * Progression of activation of a managed service instance.
 */
public enum ActivationPhase {
    /**
     * Starting state before anything happens activation-wise. Service registry is aware.
     * Initialization may be done here.
     */
    INIT(false),
    /**
     * Starting to be activated.
     */
    ACTIVATION_STARTING(true),
    /**
     * Constructing.
     */
    CONSTRUCTING(true),

    /**
     * Injecting (fields then methods).
     */
    INJECTING(true),

    /**
     * Calling any post construct method.
     */
    POST_CONSTRUCTING(true),
    /**
     * Finishing post construct method.
     */
    ACTIVATION_FINISHING(true),
    /**
     * Service is active.
     */
    ACTIVE(true),
    /**
     * About to call pre-destroy.
     */
    PRE_DESTROYING(true),
    /**
     * Destroyed (after calling any pre-destroy).
     * This is a final state.
     */
    DESTROYED(false);
    /**
     * True if this phase is eligible for deactivation/shutdown.
     */
    private final boolean eligibleForDeactivation;

    ActivationPhase(boolean eligibleForDeactivation) {
        this.eligibleForDeactivation = eligibleForDeactivation;
    }

    /**
     * Determines whether this phase passes the gate for whether deactivation (PreDestroy) can be called.
     *
     * @return true if this phase is eligible to be included in shutdown processing
     * @see io.helidon.service.registry.ServiceRegistryManager#shutdown()
     */
    boolean eligibleForDeactivation() {
        return eligibleForDeactivation;
    }
}
