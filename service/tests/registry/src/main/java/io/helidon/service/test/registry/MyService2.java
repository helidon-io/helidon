package io.helidon.service.test.registry;

import io.helidon.common.Weight;
import io.helidon.service.registry.Service;

@Service.Provider
@Weight(102)
class MyService2 implements MyContract {
    static int instances;

    private final MyService service;

    MyService2(MyService service) {
        instances++;
        this.service = service;
    }

    @Override
    public String message() {
        return service.message() + ":MyService2";
    }
}
