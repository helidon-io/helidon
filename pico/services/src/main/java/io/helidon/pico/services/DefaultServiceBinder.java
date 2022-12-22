/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceBinder;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.ServiceProviderBindable;

/**
 * The default implementation for {@link ServiceBinder}.
 */
class DefaultServiceBinder implements ServiceBinder {
    private final PicoServices picoServices;
    private final DefaultServices serviceRegistry;
    private final String moduleName;

    DefaultServiceBinder(PicoServices picoServices,
                         DefaultServices serviceRegistry,
                         String moduleName) {
        this.picoServices = picoServices;
        this.serviceRegistry = serviceRegistry;
        this.moduleName = moduleName;
    }

    @Override
    public void bind(ServiceProvider<?> sp) {
        Optional<ServiceProviderBindable<?>> bindableSp = toBindableProvider(sp);

        if (moduleName != null) {
            bindableSp.ifPresent(it -> it.moduleName(moduleName));
        }

        serviceRegistry.bind(picoServices, sp);

        bindableSp.ifPresent(it -> it.picoServices(picoServices));
    }

    static Optional<ServiceProviderBindable<?>> toBindableProvider(ServiceProvider<?> sp) {
        return Optional.ofNullable((sp instanceof ServiceProviderBindable) ? (ServiceProviderBindable<?>) sp : null);
    }

}
