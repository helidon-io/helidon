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
         *
         * @param injectionPoint the injection point identity
         * @param descriptor     the service provider to bind to this identity.
         * @return the binder builder
         */
        Binder bind(Ip injectionPoint,
                    ServiceInfo descriptor);

        /**
         * Binds a single service supplier to the injection point identified by the id.
         *
         * @param injectionPoint the injection point identity
         * @param descriptor     the service provider to bind to this identity.
         * @return the binder builder
         */
        Binder bindSupplier(Ip injectionPoint,
                            ServiceInfo descriptor);

        /**
         * Bind to an optional field, with zero or one services.
         *
         * @param injectionPoint injection point identity
         * @param descriptors    the service info to bind (zero or one)
         * @return the binder builder
         */
        Binder bindOptional(Ip injectionPoint,
                            ServiceInfo... descriptors);

        /**
         * Bind to an optional field that expects a {@link io.helidon.inject.service.ServiceProvider} or
         * {@link java.util.function.Supplier}, with zero or one services.
         *
         * @param injectionPoint injection point identity
         * @param descriptors    the service info to bind (zero or one)
         * @return the binder builder
         */
        Binder bindOptionalSupplier(Ip injectionPoint,
                                    ServiceInfo... descriptors);

        /**
         * Binds a list of services to the injection point identified by the id.
         *
         * @param injectionPoint the injection point identity
         * @param descriptors    service infos to bind to this identity (zero or more)
         * @return the binder builder
         */
        Binder bindList(Ip injectionPoint,
                        ServiceInfo... descriptors);

        /**
         * Binds a list of service suppliers to the injection point identified by the id.
         *
         * @param injectionPoint the injection point identity
         * @param descriptors    service infos to bind to this identity (zero or more)
         * @return the binder builder
         */
        Binder bindListSupplier(Ip injectionPoint,
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
