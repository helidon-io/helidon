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
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;

@Service.Singleton
class QualifiedFactoryProvidedServiceProvider
        implements Service.QualifiedFactory<QualifiedFactoryProvidedService, QualifiedFactoryQualifier> {
    @Override
    public Optional<Service.QualifiedInstance<QualifiedFactoryProvidedService>> first(
            Qualifier qualifier,
            Lookup lookup,
            GenericType<QualifiedFactoryProvidedService> type) {
        return Optional.of(Service.QualifiedInstance.create(() -> "first", qualifier));
    }

    @Override
    @Validation.Collection.Size(min = 1)
    public List<Service.QualifiedInstance<QualifiedFactoryProvidedService>> list(
            Qualifier qualifier,
            Lookup lookup,
            GenericType<QualifiedFactoryProvidedService> type) {
        return List.of(Service.QualifiedInstance.create(() -> "list", qualifier));
    }
}
