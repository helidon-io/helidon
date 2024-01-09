package io.helidon.inject.tests.service.lifecycle;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.inject.service.Injection;

@Injection.Singleton
class ASingletonContractImpl implements ASingletonContract {
    static final AtomicInteger INSTANCES = new AtomicInteger();
    static final AtomicInteger INJECTIONS = new AtomicInteger();

    private InjectedService injectedService;

    ASingletonContractImpl() {
        INSTANCES.incrementAndGet();
    }

    @Injection.Inject
    void setInjectedService(InjectedService injectedService) {
        this.injectedService = injectedService;
        INJECTIONS.incrementAndGet();
    }

    @Override
    public InjectedService service() {
        return injectedService;
    }
}
