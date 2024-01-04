package io.helidon.inject.tests.scopes;

import io.helidon.inject.service.Injection;

@Injection.Singleton
class SingletonService implements SingletonContract {
    private final RequestScopedContract contract;

    @Injection.Inject
    SingletonService(RequestScopedContract contract) {
        this.contract = contract;
    }

    @Override
    public int id() {
        return contract.id();
    }
}
