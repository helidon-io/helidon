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
     * @param serviceInfo the service to receive the injection plan.
     * @return the binder to use for binding the injection plan to the service provider
     */
    Binder bindTo(ServiceInfo serviceInfo);

    /**
     * Bind all discovered interceptors.
     *
     * @param serviceInfos interceptor services
     */
    void interceptors(ServiceInfo... serviceInfos);

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
         * @param serviceInfo    the service provider to bind to this identity.
         * @return the binder builder
         */
        Binder bind(Ip injectionPoint,
                    ServiceInfo serviceInfo);

        /**
         * Binds a single service supplier to the injection point identified by the id.
         *
         * @param injectionPoint the injection point identity
         * @param serviceInfo    the service provider to bind to this identity.
         * @return the binder builder
         */
        Binder bindProvider(Ip injectionPoint,
                            ServiceInfo serviceInfo);

        /**
         * Bind to an optional field, with zero or one services.
         *
         * @param injectionPoint injection point identity
         * @param serviceInfos   the service info to bind (zero or one)
         * @return the binder builder
         */
        Binder bindOptional(Ip injectionPoint,
                            ServiceInfo... serviceInfos);

        /**
         * Bind to an optional field that expects a {@link io.helidon.inject.service.ServiceProvider} or
         * {@link java.util.function.Supplier}, with zero or one services.
         *
         * @param injectionPoint injection point identity
         * @param serviceInfos   the service info to bind (zero or one)
         * @return the binder builder
         */
        Binder bindProviderOptional(Ip injectionPoint,
                                    ServiceInfo... serviceInfos);

        /**
         * Binds a list of services to the injection point identified by the id.
         *
         * @param injectionPoint the injection point identity
         * @param serviceInfos   service infos to bind to this identity (zero or more)
         * @return the binder builder
         */
        Binder bindList(Ip injectionPoint,
                        ServiceInfo... serviceInfos);

        /**
         * Binds a list of service suppliers to the injection point identified by the id.
         *
         * @param injectionPoint the injection point identity
         * @param serviceInfos   service infos to bind to this identity (zero or more)
         * @return the binder builder
         */
        Binder bindProviderList(Ip injectionPoint,
                                ServiceInfo... serviceInfos);

        /**
         * Represents a null bind.
         *
         * @param injectionPoint the injection point identity
         * @return the binder builder
         */
        Binder bindNull(Ip injectionPoint);

        /**
         * Represents injection points that cannot be bound at startup, and instead must rely on a
         * deferred resolver based binding. Typically, this represents some form of dynamic or configurable instance.
         *
         * @param injectionPoint the injection point identity
         * @param serviceInfo    the service info that is represented at runtime by a service provider with the runtime binding
         *                       capability (e.g. an
         *                       {@link io.helidon.inject.service.InjectionPointProvider} or
         *                       {@link io.helidon.inject.InjectionResolver})
         * @return the binder builder
         */
        Binder runtimeBind(Ip injectionPoint,
                           ServiceInfo serviceInfo);

        /**
         * Represents injection points that cannot be bound at startup, and instead must rely on a
         * deferred resolver based binding. Typically, this represents some form of dynamic or configurable instance.
         * This method represents a {@link io.helidon.inject.service.ServiceProvider} or {@link java.util.function.Supplier}
         * injection
         * point.
         *
         * @param injectionPoint the injection point identity
         * @param serviceInfo    the service info that is represented at runtime by a service provider with the runtime binding
         *                       capability (e.g. an
         *                       {@link io.helidon.inject.service.InjectionPointProvider} or
         *                       {@link io.helidon.inject.InjectionResolver})
         * @return the binder builder
         */
        Binder runtimeBindProvider(Ip injectionPoint,
                                   ServiceInfo serviceInfo);

        /**
         * Bind an {@link java.util.Optional} injection point at runtime.
         *
         * @param injectionPoint injection point id
         * @param serviceInfo    the service info that is represented at runtime by a service provider with the runtime binding
         *                       capability (e.g. an
         *                       {@link io.helidon.inject.service.InjectionPointProvider} or
         *                       {@link io.helidon.inject.InjectionResolver})
         * @return the binder builder
         */
        Binder runtimeBindOptional(Ip injectionPoint,
                                   ServiceInfo serviceInfo);

        /**
         * Bind an {@link java.util.Optional} {@link io.helidon.inject.service.ServiceProvider} or
         * {@link java.util.function.Supplier}
         * injection point at runtime.
         *
         * @param injectionPoint injection point id
         * @param serviceInfo    the service info that is represented at runtime by a service provider with the runtime binding
         *                       capability (e.g. an
         *                       {@link io.helidon.inject.service.InjectionPointProvider} or
         *                       {@link io.helidon.inject.InjectionResolver})
         * @return the binder builder
         */
        Binder runtimeBindProviderOptional(Ip injectionPoint,
                                           ServiceInfo serviceInfo);

        /**
         * Bind a {@link java.util.List} injection point at runtime.
         *
         * @param injectionPoint injection point id
         * @param serviceInfos   the service infos that are represented at runtime by a service provider with the runtime binding
         *                       capability (e.g. an
         *                       {@link io.helidon.inject.service.InjectionPointProvider} or
         *                       {@link io.helidon.inject.InjectionResolver})
         * @return the binder builder
         */
        Binder runtimeBindList(Ip injectionPoint,
                               ServiceInfo... serviceInfos);

        /**
         * Bind a {@link java.util.List} of {@link io.helidon.inject.service.ServiceProvider} or
         * {@link java.util.function.Supplier}
         * injection point at runtime.
         *
         * @param injectionPoint injection point id
         * @param serviceInfos   the service infos that are represented at runtime by a service provider with the runtime binding
         *                       capability (e.g. an
         *                       {@link io.helidon.inject.service.InjectionPointProvider} or
         *                       {@link io.helidon.inject.InjectionResolver})
         * @return the binder builder
         */
        Binder runtimeBindProviderList(Ip injectionPoint,
                                       ServiceInfo... serviceInfos);

        /**
         * Bind a nullable injection point at runtime.
         *
         * @param injectionPoint injection point id
         * @param serviceInfo    the service info that is represented at runtime by a service provider with the runtime binding
         *                       capability (e.g. an
         *                       {@link io.helidon.inject.service.InjectionPointProvider} or
         *                       {@link io.helidon.inject.InjectionResolver})
         * @return the binder builder
         */
        Binder runtimeBindNullable(Ip injectionPoint,
                                   ServiceInfo serviceInfo);

        /**
         * Bind a nullable {@link io.helidon.inject.service.ServiceProvider} or {@link java.util.function.Supplier} injection
         * point at
         * runtime.
         *
         * @param injectionPoint injection point id
         * @param serviceInfo    the service info that is represented at runtime by a service provider with the runtime binding
         *                       capability (e.g. an
         *                       {@link io.helidon.inject.service.InjectionPointProvider} or
         *                       {@link io.helidon.inject.InjectionResolver})
         * @return the binder builder
         */
        Binder runtimeBindProviderNullable(Ip injectionPoint,
                                           ServiceInfo serviceInfo);

        /**
         * Commits the bindings for this service provider.
         */
        void commit();

    }

}
