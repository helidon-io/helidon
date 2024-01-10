package io.helidon.inject.tests.lookup;

import java.util.List;

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServicesProvider;

@Injection.Service
class NoScopeServicesProviderExample implements ServicesProvider<ContractNoScope> {
    static final Qualifier FIRST_QUALI = Qualifier.create(FirstQuali.class);
    static final Qualifier SECOND_QUALI = Qualifier.create(SecondQuali.class);

    @Override
    public List<QualifiedInstance<ContractNoScope>> services() {
        return List.of(
                QualifiedInstance.create(new FirstClass(), FIRST_QUALI),
                QualifiedInstance.create(new SecondClass(), SECOND_QUALI)
        );
    }

    @Injection.Qualifier
    @interface FirstQuali {
    }

    @Injection.Qualifier
    @interface SecondQuali {
    }

    static class FirstClass implements ContractNoScope {

    }

    static class SecondClass implements ContractNoScope {

    }
}
