/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

import java.util.HashSet;
import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;

/*
Management of contracts, to return correct contracts for services created from other services,
such as a InjectionPointProvider, ServicesProvider, or Supplier
 */
final class Contracts {
    private Contracts() {
    }

    static ContractLookup create(ServiceInfo descriptor) {
        Set<ResolvedType> contracts = descriptor.contracts();

        return switch (descriptor.factoryType()) {
            case NONE, SERVICE -> new FixedContracts(contracts);
            default -> new ProviderContracts(descriptor.serviceType(),
                                             descriptor.factoryType(),
                                             contracts,
                                             descriptor.factoryContracts());
        };
    }

    static boolean requestedProvider(Lookup lookup, ServiceInfo descriptor, FactoryType factoryType) {
        return requestedProvider(lookup,
                                 descriptor.serviceType(),
                                 factoryType,
                                 descriptor.contracts(),
                                 descriptor.factoryContracts());
    }

    private static boolean requestedProvider(Lookup lookup,
                                             TypeName serviceType,
                                             FactoryType factoryType,
                                             Set<ResolvedType> contracts,
                                             Set<ResolvedType> factoryContracts) {
        if (lookup.factoryTypes().contains(factoryType)) {
            return true;
        }
        if (lookup.contracts().size() == 1) {
            ResolvedType requestedContract = lookup.contracts().iterator().next();
            if (requestedContract.equals(ResolvedType.create(serviceType))) {
                return true;
            }
            if (factoryContracts.contains(requestedContract)
                    && !contracts.contains(requestedContract)) {
                return true;
            }
        }
        return lookup.serviceType()
                .map(serviceType::equals)
                .orElse(false);
    }

    interface ContractLookup {
        Set<ResolvedType> contracts(Lookup lookup);
    }

    private static final class FixedContracts implements ContractLookup {
        private final Set<ResolvedType> contracts;

        FixedContracts(Set<ResolvedType> contracts) {
            this.contracts = contracts;
        }

        @Override
        public Set<ResolvedType> contracts(Lookup lookup) {
            return contracts;
        }
    }

    private static final class ProviderContracts implements ContractLookup {
        private final TypeName serviceType;
        private final FactoryType factoryType;
        private final Set<ResolvedType> contracts;
        private final Set<ResolvedType> factoryContracts;
        private final Set<ResolvedType> providerContracts;

        ProviderContracts(TypeName serviceType,
                          FactoryType factoryType,
                          Set<ResolvedType> contracts,
                          Set<ResolvedType> factoryContracts) {
            this.serviceType = serviceType;
            this.factoryType = factoryType;
            this.contracts = contracts;
            this.factoryContracts = factoryContracts;
            HashSet<ResolvedType> providerContracts = new HashSet<>(factoryContracts);
            providerContracts.add(ResolvedType.create(serviceType));
            this.providerContracts = Set.copyOf(providerContracts);
        }

        @Override
        public Set<ResolvedType> contracts(Lookup lookup) {
            if (requestedProvider(lookup, serviceType, factoryType, contracts, factoryContracts)) {
                return providerContracts;
            }
            return contracts;
        }
    }
}
