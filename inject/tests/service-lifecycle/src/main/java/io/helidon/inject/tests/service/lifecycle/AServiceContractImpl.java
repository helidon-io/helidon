package io.helidon.inject.tests.service.lifecycle;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.inject.service.Injection;

@Injection.Service
class AServiceContractImpl implements AServiceContract {
    static final AtomicInteger INSTANCES = new AtomicInteger();
    static final AtomicInteger INJECTIONS = new AtomicInteger();

    private InjectedService injectedService;

    AServiceContractImpl() {
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
