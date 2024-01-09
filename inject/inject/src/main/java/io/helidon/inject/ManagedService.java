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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServicesProvider;

/**
 * Activators are responsible for lifecycle creation and lazy activation of service providers.
 * They are responsible for taking the
 * {@link RegistryServiceProvider}'s managed service instance from {@link io.helidon.inject.Phase#PENDING}
 * through {@link io.helidon.inject.Phase#POST_CONSTRUCTING} (i.e., including any
 * {@link io.helidon.inject.service.Injection.PostConstruct} invocations, etc.), and finally into the
 * {@link io.helidon.inject.Phase#ACTIVE} phase.
 * <p>
 * Assumption:
 * <ol>
 *  <li>Each {@link RegistryServiceProvider} managing its backing service will have an activator strategy conforming
 *  to the DI
 *  specification.</li>
 * </ol>
 * Activation includes:
 * <ol>
 *  <li>Management of the service's {@link io.helidon.inject.Phase}.</li>
 *  <li>Control over creation (i.e., invoke the constructor non-reflectively).</li>
 *  <li>Control over gathering the service requisite dependencies (ctor, field, setters) and optional activation of those.</li>
 *  <li>Invocation of any {@link io.helidon.inject.service.Injection.PostConstruct} method.</li>
 * </ol>
 *
 * The activator also supports the inverse process of deactivation, where any
 * {@link io.helidon.inject.service.Injection.PreDestroy}
 * methods may be called, and which moves the service to a terminal {@link io.helidon.inject.Phase#DESTROYED phase}.
 *
 * @param <T> type of the service provided by the activated service provider
 */
interface ManagedService<T> {
    static <T> ManagedService<T> create(ServiceProvider<T> provider, T instance) {
        return new ManagedServices.FixedActivator<>(provider, instance);
    }

    static <T> ManagedService<T> create(Services services, ServiceProvider<T> provider) {
        ServiceDescriptor<T> descriptor = provider.descriptor();
        Set<TypeName> contracts = descriptor.contracts();

        if (descriptor.scope().equals(Injection.Service.TYPE_NAME)) {
            if (contracts.contains(ServicesProvider.TYPE_NAME)) {
                return new ManagedServicesPerLookup.ServicesProviderActivator<>(provider);
            }
            if (contracts.contains(InjectionPointProvider.TYPE_NAME)) {
                return new ManagedServicesPerLookup.IpProviderActivator<>(provider);
            }
            if (contracts.contains(TypeNames.SUPPLIER)) {
                return new ManagedServicesPerLookup.SupplierActivator<>(provider);
            }
            if (descriptor.drivenBy().isPresent()) {
                return new ManagedServicesPerLookup.DrivenByActivator<>(services, provider);
            }
            return new ManagedServicesPerLookup.SingleServiceActivator<>(provider);
        } else {
            if (contracts.contains(ServicesProvider.TYPE_NAME)) {
                return new ManagedServices.ServicesProviderActivator<>(provider);
            }
            if (contracts.contains(InjectionPointProvider.TYPE_NAME)) {
                return new ManagedServices.IpProviderActivator<>(provider);
            }
            if (contracts.contains(TypeNames.SUPPLIER)) {
                return new ManagedServices.SupplierActivator<>(provider);
            }
            if (descriptor.drivenBy().isPresent()) {
                return new ManagedServices.DrivenByActivator<>(services, provider);
            }
            return new ManagedServices.SingleServiceActivator<>(provider);
        }
    }

    ServiceDescriptor<T> descriptor();

    /**
     * Get instances from this managed service.
     *
     * @param lookup lookup to help with narrowing down the instances
     * @return empty optional if an instance is not available, supplier of qualified instances otherwise
     */
    Optional<List<QualifiedInstance<T>>> instances(Lookup lookup);

    /**
     * Activate a managed service/provider.
     *
     * @param activationRequest activation request
     * @return the result of the activation
     */
    ActivationResult activate(ActivationRequest activationRequest);

    /**
     * Deactivate a managed service. This will trigger any {@link io.helidon.inject.service.Injection.PreDestroy} method on the
     * underlying service type instance. The service will read terminal {@link io.helidon.inject.Phase#DESTROYED}
     * phase, regardless of its activation status.
     *
     * @param request deactivation request
     * @return the result
     */
    ActivationResult deactivate(DeActivationRequest request);

    /**
     * Current activation phase.
     *
     * @return phase of this activator
     */
    Phase phase();

    String description();
}
