/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.service.test.registry;

import java.util.List;
import java.util.Set;

import io.helidon.service.registry.Service;

@Service.Singleton
class SingletonServicesFactory implements Service.ServicesFactory<SuppliedContract> {
    private final List<Service.QualifiedInstance<SuppliedContract>> services;
    private int servicesCalls;

    @Service.Inject
    SingletonServicesFactory() {
        services = List.of();
    }

    SingletonServicesFactory(SuppliedContract service) {
        services = List.of(Service.QualifiedInstance.create(service, Set.of()));
    }

    @Override
    public List<Service.QualifiedInstance<SuppliedContract>> services() {
        servicesCalls++;
        return services;
    }

    int servicesCalls() {
        return servicesCalls;
    }
}
