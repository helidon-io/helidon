package io.helidon.service.tests.inject;

import io.helidon.common.Weight;
import io.helidon.service.inject.api.Injection;

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

    MyService2(MyService service, boolean increaseInstances) {
        this.service = service;
        if (increaseInstances) {
            instances++;
        }
    }

    @Override
    public String message() {
        return service.message() + ":MyService2";
    }
}
