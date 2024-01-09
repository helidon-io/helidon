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

package io.helidon.inject;

import io.helidon.inject.service.Ip;
import io.helidon.inject.service.ServiceInfo;

/**
 * Responsible for registering the injection plan to the services in the service registry.
 * <p>
 * IMPORTANT: all methods must be called with {@link io.helidon.inject.service.ServiceDescriptor} singleton
 * instances for {@link io.helidon.inject.service.ServiceInfo} parameter, as the registry depends on instance
 * equality. All generated code is done this way.
 */
public interface ServiceInjectionPlanBinder {

    /**
     * Bind an injection plan to a service provider instance.
     *
     * @param descriptor the service to receive the injection plan.
     * @return the binder to use for binding the injection plan to the service provider
     */
    Binder bindTo(ServiceInfo descriptor);

    /**
     * Bind all discovered interceptors.
     *
     * @param descriptors interceptor services
     */
    void interceptors(ServiceInfo... descriptors);

    /**
     * The binder builder for the service plan.
     * The caller must be aware of cardinality and type (whether to inject {@link java.util.function.Supplier} or instance)
     * of injections.
     *
     * @see io.helidon.inject.service.Ip
     */
    interface Binder {

        /**
         * Binds a single service to the injection point identified by the id.
         * The injection point expects a single service instance.
         *
         * @param injectionPoint the injection point identity
         * @param descriptor     the service descriptor to bind to this identity.
         * @return the binder builder
         */
        Binder bind(Ip injectionPoint,
                    ServiceInfo descriptor);

        /**
         * Bind to an optional field, with zero or one services.
         * The injection point expects an {@link java.util.Optional} of service instance.
         *
         * @param injectionPoint injection point identity
         * @param descriptors    the service descriptor to bind (zero or one)
         * @return the binder builder
         */
        Binder bindOptional(Ip injectionPoint,
                            ServiceInfo... descriptors);

        /**
         * Binds to a list field, with zero or more services.
         * The injection point expects a {@link java.util.List} of service instances.
         *
         * @param injectionPoint the injection point identity
         * @param descriptors    service descriptors to bind to this identity (zero or more)
         * @return the binder builder
         */
        Binder bindList(Ip injectionPoint,
                        ServiceInfo... descriptors);

        /**
         * Binds to a supplier field.
         * The injection point expects a {@link java.util.function.Supplier} of service.
         *
         * @param injectionPoint the injection point identity
         * @param descriptor     the service descriptor to bind to this identity.
         * @return the binder builder
         */
        Binder bindSupplier(Ip injectionPoint,
                            ServiceInfo descriptor);

        /**
         * Bind to a supplier of optional field.
         * The injection point expects a {@link java.util.function.Supplier} of {@link java.util.Optional} of service.
         *
         * @param injectionPoint injection point identity
         * @param descriptor     the service descriptor to bind (zero or one)
         * @return the binder builder
         */
        Binder bindSupplierOfOptional(Ip injectionPoint,
                                      ServiceInfo... descriptor);

        /**
         * Bind to an optional supplier field.
         * The injection point expects a {@link java.util.function.Supplier} of {@link java.util.Optional} of service.
         *
         * @param injectionPoint injection point identity
         * @param descriptor     the service descriptor to bind (zero or one)
         * @return the binder builder
         */
        Binder bindOptionalOfSupplier(Ip injectionPoint,
                                      ServiceInfo... descriptor);

        /**
         * Bind to a supplier of list.
         * The injection point expects a {@link java.util.function.Supplier} of {@link java.util.List} of services.
         *
         * @param injectionPoint the injection point identity
         * @param descriptors    service descriptor to bind to this identity (zero or more)
         * @return the binder builder
         */
        Binder bindSupplierOfList(Ip injectionPoint,
                                  ServiceInfo... descriptors);

        /**
         * Bind to a list of suppliers.
         * The injection point expects a {@link java.util.List} of {@link java.util.function.Supplier Suppliers} of service.
         *
         * @param injectionPoint the injection point identity
         * @param descriptors    service descriptor to bind to this identity (zero or more)
         * @return the binder builder
         */
        Binder bindListOfSuppliers(Ip injectionPoint,
                                   ServiceInfo... descriptors);

        /**
         * Represents a null bind.
         *
         * @param injectionPoint the injection point identity
         * @return the binder builder
         */
        Binder bindNull(Ip injectionPoint);

        /**
         * Commits the bindings for this service provider.
         */
        void commit();

    }

}
