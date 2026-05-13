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

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

@Service.Singleton
@Service.RunLevel(43.0)
@Weight(42.0)
class QualifiedFactoryProvidedInterfaceConstrainedServiceProvider
        implements Service.QualifiedFactory<QualifiedFactoryProvidedInterfaceConstrainedService, Service.Named>,
                   QualifiedFactoryDirectContract {
    @Override
    public Optional<Service.QualifiedInstance<QualifiedFactoryProvidedInterfaceConstrainedService>> first(
            Qualifier qualifier,
            Lookup lookup,
            GenericType<QualifiedFactoryProvidedInterfaceConstrainedService> type) {
        return Optional.of(list(qualifier, lookup, type).getFirst());
    }

    @Override
    public List<Service.QualifiedInstance<QualifiedFactoryProvidedInterfaceConstrainedService>> list(
            Qualifier qualifier,
            Lookup lookup,
            GenericType<QualifiedFactoryProvidedInterfaceConstrainedService> type) {
        return List.of(Service.QualifiedInstance.create(new QualifiedFactoryProvidedInterfaceConstrainedServiceImpl(),
                                                        qualifier),
                       Service.QualifiedInstance.create(new QualifiedFactoryProvidedInterfaceConstrainedServiceImpl(),
                                                        qualifier));
    }
}
