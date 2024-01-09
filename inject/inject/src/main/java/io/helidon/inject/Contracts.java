package io.helidon.inject;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServicesProvider;

class Contracts {
    public static <T> ContractLookup create(ServiceDescriptor<T> descriptor) {
        /*
        Service implements Contract (Fixed)
        Service implements Supplier<Contract> (Provider)
        Service implements InjectionPointProvider<Contract> (IpProvider)
        Service implements ServicesProvider (ServicesProvider)
         */
        Set<TypeName> contracts = descriptor.contracts();
        if (contracts.contains(ServicesProvider.TYPE_NAME)) {
            return new ProviderContracts(contracts, ServicesProvider.TYPE_NAME);
        }
        if (contracts.contains(InjectionPointProvider.TYPE_NAME)) {
            return new ProviderContracts(contracts, InjectionPointProvider.TYPE_NAME);
        }
        if (contracts.contains(TypeNames.SUPPLIER)) {
            return new ProviderContracts(contracts, TypeNames.SUPPLIER);
        }
        return new FixedContracts(contracts);
    }

    interface ContractLookup {
        Set<TypeName> contracts(Lookup lookup);
    }

    static class FixedContracts implements ContractLookup {
        private final Set<TypeName> contracts;

        FixedContracts(Set<TypeName> contracts) {
            this.contracts = contracts;
        }

        @Override
        public Set<TypeName> contracts(Lookup lookup) {
            return contracts;
        }
    }

    private static class ProviderContracts implements ContractLookup {
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
