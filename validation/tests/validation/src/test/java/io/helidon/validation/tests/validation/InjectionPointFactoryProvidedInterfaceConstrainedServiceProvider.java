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

package io.helidon.validation.tests.validation;

import java.util.List;
import java.util.Optional;

import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

@Service.Singleton
class InjectionPointFactoryProvidedInterfaceConstrainedServiceProvider
        implements Service.InjectionPointFactory<InjectionPointFactoryProvidedInterfaceConstrainedService>,
                   InjectionPointFactoryDirectContract,
                   InjectionPointFactoryOverloadedDirectContract {
    @Override
    public Optional<Service.QualifiedInstance<InjectionPointFactoryProvidedInterfaceConstrainedService>> first(String name) {
        return Optional.of(Service.QualifiedInstance.create(new InjectionPointFactoryProvidedInterfaceConstrainedServiceImpl(),
                                                           Qualifier.createNamed(name)));
    }

    @Override
    public Optional<Service.QualifiedInstance<InjectionPointFactoryProvidedInterfaceConstrainedService>> first(
            Lookup lookup) {
        return Optional.of(list(lookup).getFirst());
    }

    @Override
    public List<Service.QualifiedInstance<InjectionPointFactoryProvidedInterfaceConstrainedService>> list(Lookup lookup) {
        return List.of(Service.QualifiedInstance.create(new InjectionPointFactoryProvidedInterfaceConstrainedServiceImpl()),
                       Service.QualifiedInstance.create(new InjectionPointFactoryProvidedInterfaceConstrainedServiceImpl()));
    }
}
