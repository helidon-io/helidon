/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.services;

import java.util.Objects;
import java.util.Optional;

import io.helidon.pico.Phase;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;
import io.helidon.pico.Services;

/**
 * The default implementation for {@link ServiceBinder}.
 */
public class DefaultServiceBinder implements ServiceBinder {
    private final PicoServices picoServices;
    private final ServiceBinder serviceRegistry;
    private final String moduleName;

    private DefaultServiceBinder(
            PicoServices picoServices,
            String moduleName) {
        this.picoServices = picoServices;
        this.serviceRegistry = (ServiceBinder) picoServices.services();
        this.moduleName = moduleName;
    }

    /**
     * Creates an instance of the default services binder.
     *
     * @param picoServices the pico services instance
     * @param moduleName the module name
     * @return the newly created service binder
     */
    public static DefaultServiceBinder create(
            PicoServices picoServices,
            String moduleName) {
        Objects.requireNonNull(picoServices);
        Objects.requireNonNull(moduleName);
        return new DefaultServiceBinder(picoServices, moduleName);
    }

    @Override
    public void bind(
            ServiceProvider<?> sp) {
        DefaultServices.assertPermitsDynamic(picoServices.config());

        Optional<ServiceProviderBindable<?>> bindableSp = toBindableProvider(sp);

        if (moduleName != null) {
            bindableSp.ifPresent(it -> it.moduleName(moduleName));
        }

        Services services = picoServices.services();
        if (services instanceof DefaultServices && sp instanceof ServiceProviderBindable) {
            Phase currentPhase = ((DefaultServices) services).currentPhase();
            if (currentPhase.ordinal() >= Phase.SERVICES_READY.ordinal()) {
                // deferred binding (e.g., to allow PicoTestSupport to programmatically register/bind service providers
                ((ServiceProviderBindable) sp).picoServices(Optional.of(picoServices));
            }
        }

        serviceRegistry.bind(sp);
        bindableSp.ifPresent(it -> it.picoServices(Optional.ofNullable(picoServices)));
    }

    /**
     * Returns the bindable service provider for what is passed if available.
     *
     * @param sp the service provider
     * @return the bindable service provider if available, otherwise empty
     */
    public static Optional<ServiceProviderBindable<?>> toBindableProvider(
            ServiceProvider<?> sp) {
        Objects.requireNonNull(sp);
        if (sp instanceof ServiceProviderBindable) {
            return Optional.of((ServiceProviderBindable<?>) sp);
        }
        return Optional.of((ServiceProviderBindable<?>) sp.serviceProviderBindable());
    }

    /**
     * Returns the root provider of the service provider passed.
     *
     * @param sp the service provider
     * @return the root provider of the service provider, falling back to the service provider passed
     */
    public static ServiceProvider<?> toRootProvider(
            ServiceProvider<?> sp) {
        Optional<ServiceProviderBindable<?>> bindable = toBindableProvider(sp);
        if (bindable.isPresent()) {
            sp = bindable.get();
        }

        ServiceProvider<?> rootProvider = ((ServiceProviderBindable<?>) sp).rootProvider().orElse(null);
        return (rootProvider != null) ? rootProvider : sp;
    }

}
