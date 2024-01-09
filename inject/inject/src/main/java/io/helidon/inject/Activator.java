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
 * They are responsible for taking the scope instance from {@link io.helidon.inject.Phase#INIT}
 * through {@link io.helidon.inject.Phase#POST_CONSTRUCTING} (i.e., including any
 * {@link io.helidon.inject.service.Injection.PostConstruct} invocations, etc.), and finally into the
 * {@link io.helidon.inject.Phase#ACTIVE} phase.
 * <p>
 * <ol>
 *  <li>Management of the service's {@link io.helidon.inject.Phase}.</li>
 *  <li>Control over creation (i.e., invoke the constructor non-reflectively).</li>
 *  <li>Control over injection (i.e. injecting fields, constructor parameters, and method parameters</li>
 *  <li>Invocation of any {@link io.helidon.inject.service.Injection.PostConstruct} method.</li>
 * </ol>
 *
 * The activator also supports the inverse process of deactivation, where any
 * {@link io.helidon.inject.service.Injection.PreDestroy}
 * methods may be called, and which moves the service to a terminal {@link io.helidon.inject.Phase#DESTROYED phase}.
 *
 * @param <T> type of the service provided by the activated service provider
 */
interface Activator<T> {
    static <T> Activator<T> create(ServiceProvider<T> provider, T instance) {
        return new Activators.FixedActivator<>(provider, instance);
    }

    static <T> Activator<T> create(Services services, ServiceProvider<T> provider) {
        ServiceDescriptor<T> descriptor = provider.descriptor();
        Set<TypeName> contracts = descriptor.contracts();

        if (descriptor.scope().equals(Injection.Service.TYPE_NAME)) {
            if (contracts.contains(ServicesProvider.TYPE_NAME)) {
                return new ActivatorsPerLookup.ServicesProviderActivator<>(provider);
            }
            if (contracts.contains(InjectionPointProvider.TYPE_NAME)) {
                return new ActivatorsPerLookup.IpProviderActivator<>(provider);
            }
            if (contracts.contains(TypeNames.SUPPLIER)) {
                return new ActivatorsPerLookup.SupplierActivator<>(provider);
            }
            if (descriptor.drivenBy().isPresent()) {
                return new ActivatorsPerLookup.DrivenByActivator<>(services, provider);
            }
            return new ActivatorsPerLookup.SingleServiceActivator<>(provider);
        } else {
            if (contracts.contains(ServicesProvider.TYPE_NAME)) {
                return new Activators.ServicesProviderActivator<>(provider);
            }
            if (contracts.contains(InjectionPointProvider.TYPE_NAME)) {
                return new Activators.IpProviderActivator<>(provider);
            }
            if (contracts.contains(TypeNames.SUPPLIER)) {
                return new Activators.SupplierActivator<>(provider);
            }
            if (descriptor.drivenBy().isPresent()) {
                return new Activators.DrivenByActivator<>(services, provider);
            }
            return new Activators.SingleServiceActivator<>(provider);
        }
    }

    /**
     * Service descriptor of this activator.
     *
     * @return service descriptor
     */
    ServiceDescriptor<T> descriptor();

    /**
     * Get instances from this managed service.
     * This method is called when we already know that this service matches the lookup, and we can safely instantiate everything.
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

    /**
     * Description of this activator, including the current phase.
     *
     * @return description of this activator
     */
    String description();
}
