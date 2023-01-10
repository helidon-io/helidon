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

import java.util.Optional;

import io.helidon.pico.ServiceInjectionPlanBinder;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;

class DefaultInjectionPlanBinder implements ServiceInjectionPlanBinder, ServiceInjectionPlanBinder.Binder {

    private final DefaultServices services;

    DefaultInjectionPlanBinder(
            DefaultServices services) {
        this.services = services;
    }

    @Override
    public Binder bindTo(
            ServiceProvider<?> serviceProvider) {
        // don't trust what we get, but instead lookup the service provider that we carry in our services registry
        serviceProvider = services.serviceProviderFor(serviceProvider.serviceInfo().serviceTypeName());
        Optional<ServiceProviderBindable<?>> bindable = DefaultServiceBinder.toBindableProvider(serviceProvider);
        Optional<Binder> binder = (bindable.isPresent()) ? bindable.get().injectionPlanBinder() : Optional.empty();
        if (binder.isEmpty()) {
            DefaultPicoServices.LOGGER.log(System.Logger.Level.WARNING,
                       "service provider is not capable of being bound to injection points: " + serviceProvider);
        }
        return this;
    }

    @Override
    public Binder bind(
            String id,
            ServiceProvider<?> serviceProvider) {
        // NOP
        return this;
    }

    @Override
    public Binder bindMany(
            String id,
            ServiceProvider<?>... serviceProviders) {
        // NOP
        return this;
    }

    @Override
    public Binder bindVoid(
            String ipIdentity) {
        // NOP
        return this;
    }

    @Override
    public Binder resolvedBind(
            String ipIdentity,
            Class<?> serviceType) {
        // NOP
        return this;
    }

    @Override
    public void commit() {
        // NOP
    }

}
