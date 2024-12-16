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

package io.helidon.service.inject.api;

import java.util.List;
import java.util.Optional;

/**
 * Activator is responsible for lifecycle management of a service instance within a scope.
 *
 * @param <T> type of the instance
 */
public interface Activator<T> {
    /**
     * Service descriptor of this activator.
     *
     * @return service descriptor
     */
    InjectServiceDescriptor<T> descriptor();

    /**
     * Get instances from this managed service.
     * This method is called when we already know that this service matches the lookup, and we can safely instantiate everything.
     *
     * @param lookup lookup to help with narrowing down the instances
     * @return empty optional if an instance is not available, supplier of qualified instances otherwise
     */
    Optional<List<Injection.QualifiedInstance<T>>> instances(Lookup lookup);

    /**
     * Activate a managed service/factory.
     *
     * @param activationRequest activation request
     * @return the result of activation
     */
    ActivationResult activate(ActivationRequest activationRequest);

    /**
     * Deactivate a managed service. This will trigger any {@link io.helidon.service.registry.Service.PreDestroy}
     * method on the underlying service instance. The service will reach terminal
     * {@link Activator.Phase#DESTROYED} phase, regardless of result of this call.
     *
     * @return the result of deactivation
     */
    ActivationResult deactivate();

    /**
     * Current activation phase.
     *
     * @return phase of this activator
     */
    Phase phase();

    /**
     * Description of this activator, including the current phase.
     *
     * @return description of this activator
     */
    String description();

    /**
     * Progression of activation of a managed service instance (each service may have more than one managed instance, depending on
     * its scope).
     */
    enum Phase {
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

        Phase(boolean eligibleForDeactivation) {
            this.eligibleForDeactivation = eligibleForDeactivation;
        }

        /**
         * Determines whether this phase passes the gate for whether deactivation (PreDestroy) can be called.
         *
         * @return true if this phase is eligible to be included in shutdown processing
         * @see io.helidon.service.registry.ServiceRegistryManager#shutdown()
         */
        public boolean eligibleForDeactivation() {
            return eligibleForDeactivation;
        }
    }
}
