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
 * Responsible for registering the injection plan to the services in the service registry.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public interface ServiceInjectionPlanBinder {

    /**
     * Bind an injection plan to a service provider instance.
     *
     * @param serviceProvider the service provider to receive the injection plan.
     * @return the binder to use for binding the injection plan to the service provider
     */
    Binder bindTo(ServiceProvider<?> serviceProvider);


    /**
     * The binder builder for the service plan.
     *
     * @see InjectionPointInfo
     */
    interface Binder {

        /**
         * Binds a single service provider to the injection point identified by {@link InjectionPointInfo#id()}.
         * It is assumed that the caller of this is aware of the proper cardinality for each injection point.
         *
         * @param id                the injection point identity
         * @param serviceProvider   the service provider to bind to this identity.
         * @return the binder builder
         */
        Binder bind(String id,
                    ServiceProvider<?> serviceProvider);

        /**
        * Binds a list of service providers to the injection point identified by {@link InjectionPointInfo#id()}.
        * It is assumed that the caller of this is aware of the proper cardinality for each injection point.
        *
        * @param id                 the injection point identity
        * @param serviceProviders   the list of service providers to bind to this identity
        * @return the binder builder
        */
        Binder bindMany(String id,
                        ServiceProvider<?>... serviceProviders);

        /**
         * Represents a void / null bind, only applicable for an Optional injection point.
         *
         * @param id the injection point identity
         * @return the binder builder
         */
        Binder bindVoid(String id);

        /**
         * Represents injection points that cannot be bound at startup, and instead must rely on a
         * deferred resolver based binding. Typically, this represents some form of dynamic or configurable instance.
         *
         * @param id            the injection point identity
         * @param serviceType   the service type needing to be resolved
         * @return the binder builder
         */
        Binder resolvedBind(String id,
                            Class<?> serviceType);

        /**
         * Commits the bindings for this service provider.
         */
        void commit();

    }

}
