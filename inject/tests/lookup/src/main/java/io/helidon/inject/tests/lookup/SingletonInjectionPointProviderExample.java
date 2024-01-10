package io.helidon.inject.tests.lookup;

import java.util.Optional;

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.Qualifier;

@Injection.Singleton
class SingletonInjectionPointProviderExample implements InjectionPointProvider<ContractSingleton> {
    static final Qualifier FIRST_QUALI = Qualifier.create(SingletonServicesProviderExample.FirstQuali.class);
    static final Qualifier SECOND_QUALI = Qualifier.create(SingletonServicesProviderExample.SecondQuali.class);
    static final QualifiedInstance<ContractSingleton> FIRST = QualifiedInstance.create(new FirstClass(), FIRST_QUALI);
    static final QualifiedInstance<ContractSingleton> SECOND = QualifiedInstance.create(new SecondClass(), SECOND_QUALI);

    @Override
    public Optional<QualifiedInstance<ContractSingleton>> first(Lookup query) {
        if (query.qualifiers().contains(FIRST_QUALI)) {
            return Optional.of(FIRST);
        }
        if (query.qualifiers().contains(SECOND_QUALI)) {
            return Optional.of(SECOND);
        }
        return Optional.empty();
    }

    @Injection.Qualifier
    @interface FirstQuali {
    }

    @Injection.Qualifier
    @interface SecondQuali {
    }

    static class FirstClass implements ContractSingleton {

    }

    static class SecondClass implements ContractSingleton {

    }
}
