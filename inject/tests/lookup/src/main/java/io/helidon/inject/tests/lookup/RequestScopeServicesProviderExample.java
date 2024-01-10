package io.helidon.inject.tests.lookup;

import java.util.List;

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServicesProvider;

@Injection.Service
class RequestScopeServicesProviderExample implements ServicesProvider<ContractRequestScope> {
    static final Qualifier FIRST_QUALI = Qualifier.create(FirstQuali.class);
    static final Qualifier SECOND_QUALI = Qualifier.create(SecondQuali.class);

    @Override
    public List<QualifiedInstance<ContractRequestScope>> services() {
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

    static class FirstClass implements ContractRequestScope {

    }

    static class SecondClass implements ContractRequestScope {

    }
}
