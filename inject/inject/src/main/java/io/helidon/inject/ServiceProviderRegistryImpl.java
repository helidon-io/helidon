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

package io.helidon.inject;

import java.util.List;

import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ServiceInfo;

class ServiceProviderRegistryImpl implements ServiceProviderRegistry {
    private final ServicesImpl services;

    ServiceProviderRegistryImpl(ServicesImpl services) {
        this.services = services;
    }

    @Override
    public <T> List<RegistryServiceProvider<T>> all(Lookup lookup) {
        return services.allProviders(lookup);
    }

    @Override
    public <T> RegistryServiceProvider<T> get(ServiceInfo serviceInfo) {
        return services.serviceProvider(serviceInfo);
    }
}
