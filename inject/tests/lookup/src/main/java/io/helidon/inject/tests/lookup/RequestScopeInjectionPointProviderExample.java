package io.helidon.inject.tests.lookup;

import java.util.Optional;

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.Qualifier;

@Injection.Service
class RequestScopeInjectionPointProviderExample implements InjectionPointProvider<ContractRequestScope> {
    static final Qualifier FIRST_QUALI = Qualifier.create(SingletonServicesProviderExample.FirstQuali.class);
    static final Qualifier SECOND_QUALI = Qualifier.create(SingletonServicesProviderExample.SecondQuali.class);

    @Override
    public Optional<QualifiedInstance<ContractRequestScope>> first(Lookup query) {
        if (query.qualifiers().contains(FIRST_QUALI)) {
            return Optional.of(QualifiedInstance.create(new FirstClass(), FIRST_QUALI));
        }
        if (query.qualifiers().contains(SECOND_QUALI)) {
            return Optional.of(QualifiedInstance.create(new SecondClass(), SECOND_QUALI));
        }
        return Optional.empty();
    }

    @Injection.Qualifier
    @interface FirstQuali {
    }

    @Injection.Qualifier
    @interface SecondQuali {
    }

    static class FirstClass implements ContractRequestScope {

    }

    static class SecondClass implements ContractRequestScope {

    }
}
