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

package io.helidon.service.inject;

import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.ServiceInfo;

/**
 * Responsible for registering the injection plan to the services in the service registry.
 * <p>
 * IMPORTANT: all methods must be called with {@link io.helidon.service.registry.GeneratedService.Descriptor} singleton
 * instances for {@link io.helidon.service.registry.ServiceInfo} parameter, as the registry depends on instance
 * equality. All generated code is done this way.
 */
public interface InjectionPlanBinder {

    /**
     * Bind an injection plan to a service provider instance.
     *
     * @param descriptor the service to receive the injection plan.
     * @return the binder to use for binding the injection plan to the service provider
     */
    Binder bindTo(io.helidon.service.registry.ServiceInfo descriptor);

    /**
     * Bind all discovered interceptors.
     *
     * @param descriptors interceptor services
     */
    void interceptors(io.helidon.service.registry.ServiceInfo... descriptors);

    /**
     * The binder builder for the service plan.
     * The caller must be aware of cardinality and type (whether to inject {@link java.util.function.Supplier} or instance)
     * of injections.
     *
     * @see io.helidon.service.inject.api.Ip
     */
    interface Binder {

        /**
         * Binds a single service to the injection point identified by the id.
         * The injection point expects a single service instance.
         *
         * @param dependency the injection point identity
         * @param descriptor the service descriptor to bind to this identity
         * @return the binder builder
         */
        Binder bind(Dependency dependency,
                    ServiceInfo descriptor);

        /**
         * Bind to an optional field, with zero or one services.
         * The injection point expects an {@link java.util.Optional} of service instance.
         *
         * @param dependency  injection point identity
         * @param descriptors the service descriptor to bind (zero or one)
         * @return the binder builder
         */
        Binder bindOptional(Dependency dependency,
                            ServiceInfo... descriptors);

        /**
         * Binds to a list field, with zero or more services.
         * The injection point expects a {@link java.util.List} of service instances.
         *
         * @param dependency  the injection point identity
         * @param descriptors service descriptors to bind to this identity (zero or more)
         * @return the binder builder
         */
        Binder bindList(Dependency dependency,
                        ServiceInfo... descriptors);

        /**
         * Binds to a supplier field.
         * The injection point expects a {@link java.util.function.Supplier} of service.
         *
         * @param dependency the injection point identity
         * @param descriptor the service descriptor to bind to this identity.
         * @return the binder builder
         */
        Binder bindSupplier(Dependency dependency,
                            ServiceInfo descriptor);

        /**
         * Bind to a supplier of optional field.
         * The injection point expects a {@link java.util.function.Supplier} of {@link java.util.Optional} of service.
         *
         * @param dependency injection point identity
         * @param descriptor the service descriptor to bind (zero or one)
         * @return the binder builder
         */
        Binder bindSupplierOfOptional(Dependency dependency,
                                      ServiceInfo... descriptor);

        /**
         * Bind to an optional supplier field.
         * The injection point expects a {@link java.util.function.Supplier} of {@link java.util.Optional} of service.
         *
         * @param dependency injection point identity
         * @param descriptor the service descriptor to bind (zero or one)
         * @return the binder builder
         */
        Binder bindOptionalOfSupplier(Dependency dependency,
                                      ServiceInfo... descriptor);

        /**
         * Bind to a supplier of list.
         * The injection point expects a {@link java.util.function.Supplier} of {@link java.util.List} of services.
         *
         * @param dependency  the injection point identity
         * @param descriptors service descriptor to bind to this identity (zero or more)
         * @return the binder builder
         */
        Binder bindSupplierOfList(Dependency dependency,
                                  ServiceInfo... descriptors);

        /**
         * Bind to a list of suppliers.
         * The injection point expects a {@link java.util.List} of {@link java.util.function.Supplier Suppliers} of service.
         *
         * @param dependency  the injection point identity
         * @param descriptors service descriptor to bind to this identity (zero or more)
         * @return the binder builder
         */
        Binder bindListOfSuppliers(Dependency dependency,
                                   ServiceInfo... descriptors);

        /**
         * Represents a null bind.
         *
         * @param dependency the injection point identity
         * @return the binder builder
         */
        Binder bindNull(Dependency dependency);

        /**
         * Bind service instance.
         *
         * @param dependency the injection point identity
         * @param descriptor the service descriptor to bind
         * @return the binder builder
         */
        Binder bindServiceInstance(Dependency dependency, ServiceInfo descriptor);

        /**
         * Bind to a list of service instances.
         *
         * @param dependency the injection point identity
         * @param descriptors the service descriptors to bind (zero or more)
         * @return the binder builder
         */
        Binder bindServiceInstanceList(Dependency dependency, ServiceInfo... descriptors);

        /**
         * Bind to an optional of service instance.
         *
         * @param dependency the injection point identity
         * @param descriptor the service descriptor to bind (zero or one)
         * @return the binder builder
         */
        Binder bindOptionalOfServiceInstance(Dependency dependency, ServiceInfo... descriptor);

        /**
         * Commits the bindings for this service provider.
         */
        void commit();

    }

}
