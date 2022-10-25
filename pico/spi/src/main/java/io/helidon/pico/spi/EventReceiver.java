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

/**
 * A receiver of events from the {@link Services} registry.  Only {@link ServiceProvider}'s
 * that are bound to the global service registry are capable of receiving events.
 *
 * @see io.helidon.pico.spi.ServiceProviderBindable
 */
public interface EventReceiver {

    /**
     * Events issued from the framework.
     */
    enum Event {

        /**
         * Called after all modules and services from those modules are initially loaded into the service registry.
         */
        POST_BIND_ALL_MODULES,

        /**
         * Called after {@link #POST_BIND_ALL_MODULES} to resolve any latent bindings, prior to {@link #SERVICES_READY}.
         */
        FINAL_RESOLVE,

        /**
         * The service registry is fully populated and ready.
         */
        SERVICES_READY

    }

    /**
     * Called at the end of module and service bindings, when all the services in the service registry have been populated.
     *
     * @param event the event
     */
    void onEvent(Event event);

}
