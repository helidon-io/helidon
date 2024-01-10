package io.helidon.inject.tests.lookup;

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.service.Injection;

@Injection.Service
@Weight(Weighted.DEFAULT_WEIGHT + 1) // higher than other no-scope, lower than singleton supplier
class NoScopeSupplierExample implements Supplier<ContractNoScope> {
    private static final ContractNoScope FIRST = new First();

    @Override
    public ContractNoScope get() {
        return FIRST;
    }

    static class First implements ContractNoScope {
    }
}
