package io.helidon.inject.tests.lookup;

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.service.Injection;

@Injection.Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 2) // the only weighted one, should be first
class SingletonSupplierExample implements Supplier<ContractSingleton> {
    private static final ContractSingleton FIRST = new First();

    @Override
    public ContractSingleton get() {
        return FIRST;
    }

    static class First implements ContractSingleton {
    }
}
