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

import io.helidon.common.types.ResolvedType;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Lookup;

/*
Management of contracts, to return correct contracts for services created from other services,
such as a InjectionPointProvider, ServicesProvider, or Supplier
 */
final class Contracts {
    private Contracts() {
    }

    static ContractLookup create(InjectServiceInfo descriptor) {
        Set<ResolvedType> contracts = descriptor.contracts();

        return switch (descriptor.factoryType()) {
            case NONE, SERVICE -> new FixedContracts(contracts);
            default -> new ProviderContracts(contracts, descriptor.factoryContracts());
        };
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
        private final Set<ResolvedType> contracts;
        private final Set<ResolvedType> factoryContracts;

        ProviderContracts(Set<ResolvedType> contracts, Set<ResolvedType> factoryContracts) {
            this.contracts = contracts;
            this.factoryContracts = factoryContracts;
        }

        @Override
        public Set<ResolvedType> contracts(Lookup lookup) {
            return contracts;
        }
    }
}
