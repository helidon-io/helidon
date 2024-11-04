/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject.qualified.providers;

import java.util.Map;
import java.util.Optional;

import io.helidon.common.GenericType;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Injection.QualifiedFactory;
import io.helidon.service.inject.api.Injection.QualifiedInstance;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;

@Injection.Singleton
class SecondQualifiedProvider implements QualifiedFactory<QualifiedContract, SecondQualifier> {
    private final Map<String, QualifiedContract> values = Map.of("first", new QualifiedContractImpl("first"),
                                                                 "second", new QualifiedContractImpl("second"));

    @Override
    public Optional<QualifiedInstance<QualifiedContract>> first(Qualifier qualifier,
                                                                Lookup lookup,
                                                                GenericType<QualifiedContract> type) {
        return Optional.ofNullable(values.get(qualifier.value().orElse("not-defined")))
                .map(it -> QualifiedInstance.create(it, qualifier));
    }

    private final static class QualifiedContractImpl implements QualifiedContract {
        private final String value;

        private QualifiedContractImpl(String value) {
            this.value = value;
        }

        @Override
        public String name() {
            return value;
        }
    }

}
