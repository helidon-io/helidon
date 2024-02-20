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

package io.helidon.service.inject;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.registry.ServiceInfo;

/*
Management of contracts, to return correct contracts for services created from other services,
such as a InjectionPointProvider, ServicesProvider, or Supplier
 */
final class Contracts {
    private Contracts() {
    }

    static <T> ContractLookup create(ServiceInfo descriptor) {
        Set<TypeName> contracts = descriptor.contracts();
        if (contracts.contains(Injection.ServicesProvider.TYPE)) {
            return new ProviderContracts(contracts, Injection.ServicesProvider.TYPE);
        }
        if (contracts.contains(Injection.InjectionPointProvider.TYPE)) {
            return new ProviderContracts(contracts, Injection.InjectionPointProvider.TYPE);
        }
        if (contracts.contains(TypeNames.SUPPLIER)) {
            return new ProviderContracts(contracts, TypeNames.SUPPLIER);
        }
        return new FixedContracts(contracts);
    }

    interface ContractLookup {
        Set<TypeName> contracts(Lookup lookup);
    }

    private static final class FixedContracts implements ContractLookup {
        private final Set<TypeName> contracts;

        FixedContracts(Set<TypeName> contracts) {
            this.contracts = contracts;
        }

        @Override
        public Set<TypeName> contracts(Lookup lookup) {
            return contracts;
        }
    }

    private static final class ProviderContracts implements ContractLookup {
        private final Set<TypeName> all;
        private final Set<TypeName> noProvider;
        private final TypeName provider;

        ProviderContracts(Set<TypeName> contracts, TypeName provider) {
            this.all = contracts;
            this.provider = provider;
            this.noProvider = contracts.stream()
                    .filter(Predicate.not(provider::equals))
                    .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public Set<TypeName> contracts(Lookup lookup) {
            if (lookup.contracts().contains(provider)) {
                return all;
            }
            return noProvider;
        }
    }
}
