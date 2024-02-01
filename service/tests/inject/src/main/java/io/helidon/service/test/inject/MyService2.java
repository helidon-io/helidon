package io.helidon.service.test.inject;

import io.helidon.common.Weight;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.Service;

@Injection.Singleton
@Weight(102)
class MyService2 implements MyContract {
    static int instances;

    private final MyService service;

    @Injection.Inject
    MyService2(MyService service) {
        instances++;
        this.service = service;
    }

    @Override
    public String message() {
        return service.message() + ":MyService2";
    }
}
