package io.helidon.inject.tests.scopes;

import java.util.function.Supplier;

import io.helidon.inject.service.Injection;

@Injection.Singleton
class SingletonService implements SingletonContract {
    private final Supplier<RequestScopedContract> contract;

    @Injection.Inject
    SingletonService(Supplier<RequestScopedContract> contract) {
        this.contract = contract;
    }

    @Override
    public int id() {
        return contract.get().id();
    }
}
