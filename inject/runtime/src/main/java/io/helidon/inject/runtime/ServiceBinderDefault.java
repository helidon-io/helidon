/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.runtime;

import java.util.Objects;
import java.util.Optional;

import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.Phase;
import io.helidon.inject.api.ServiceBinder;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.ServiceProviderBindable;
import io.helidon.inject.api.Services;

/**
 * The default implementation for {@link ServiceBinder}.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class ServiceBinderDefault implements ServiceBinder {
    private final InjectionServices injectionServices;
    private final ServiceBinder serviceRegistry;
    private final String moduleName;
    private final boolean trusted;

    private ServiceBinderDefault(InjectionServices injectionServices,
                                 String moduleName,
                                 boolean trusted) {
        this.injectionServices = injectionServices;
        this.serviceRegistry = (ServiceBinder) injectionServices.services();
        this.moduleName = moduleName;
        this.trusted = trusted;
    }

    /**
     * Creates an instance of the default services binder.
     *
     * @param injectionServices the services registry instance
     * @param moduleName the module name
     * @param trusted are we in trusted mode (typically only set during early initialization sequence)
     * @return the newly created service binder
     */
    public static ServiceBinderDefault create(InjectionServices injectionServices,
                                              String moduleName,
                                              boolean trusted) {
        Objects.requireNonNull(injectionServices);
        Objects.requireNonNull(moduleName);
        return new ServiceBinderDefault(injectionServices, moduleName, trusted);
    }

    @Override
    public void bind(ServiceProvider<?> sp) {
        if (!trusted) {
            DefaultServices.assertPermitsDynamic(injectionServices.config());
        }

        Optional<ServiceProviderBindable<?>> bindableSp = toBindableProvider(sp);
        if (bindableSp.isPresent() && alreadyBoundToThisInjectionServices(bindableSp.get(), injectionServices)) {
            return;
        }

        if (moduleName != null) {
            bindableSp.ifPresent(it -> it.moduleName(moduleName));
        }

        Services services = injectionServices.services();
        if (services instanceof DefaultServices && sp instanceof ServiceProviderBindable) {
            Phase currentPhase = ((DefaultServices) services).currentPhase();
            if (currentPhase.ordinal() >= Phase.SERVICES_READY.ordinal()) {
                // deferred binding (e.g., to allow InjectionTestSupport to programmatically register/bind service providers
                ((ServiceProviderBindable<?>) sp).injectionServices(Optional.of(injectionServices));
            }
        }

        serviceRegistry.bind(sp);
        bindableSp.ifPresent(it -> it.injectionServices(Optional.of(injectionServices)));
    }

    private boolean alreadyBoundToThisInjectionServices(ServiceProviderBindable<?> serviceProvider,
                                                        InjectionServices injectionServices) {
        InjectionServices assigned = serviceProvider.injectionServices().orElse(null);
        return (assigned == injectionServices);
    }

    /**
     * Returns the bindable service provider for what is passed if available.
     *
     * @param sp the service provider
     * @return the bindable service provider if available, otherwise empty
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional<ServiceProviderBindable<?>> toBindableProvider(ServiceProvider<?> sp) {
        Objects.requireNonNull(sp);
        if (sp instanceof ServiceProviderBindable) {
            return Optional.of((ServiceProviderBindable<?>) sp);
        }
        return (Optional) sp.serviceProviderBindable();
    }

    /**
     * Returns the root provider of the service provider passed.
     *
     * @param sp the service provider
     * @return the root provider of the service provider, falling back to the service provider passed
     */
    public static ServiceProvider<?> toRootProvider(ServiceProvider<?> sp) {
        Optional<ServiceProviderBindable<?>> bindable = toBindableProvider(sp);
        if (bindable.isPresent()) {
            sp = bindable.get();
        }

        ServiceProvider<?> rootProvider = ((ServiceProviderBindable<?>) sp).rootProvider().orElse(null);
        return (rootProvider != null) ? rootProvider : sp;
    }

}
