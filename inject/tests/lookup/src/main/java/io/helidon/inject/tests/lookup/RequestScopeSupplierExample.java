package io.helidon.inject.tests.lookup;

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.service.Injection;

@Injection.Service
@Weight(Weighted.DEFAULT_WEIGHT + 1) // higher than other no-scope, lower than singleton supplier
class RequestScopeSupplierExample implements Supplier<ContractRequestScope> {
    private static final ContractRequestScope FIRST = new First();

    @Override
    public ContractRequestScope get() {
        return FIRST;
    }

    static class First implements ContractRequestScope {
    }
}
