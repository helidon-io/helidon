package io.helidon.service.tests.inject;

import io.helidon.common.Weight;
import io.helidon.service.inject.api.Injection;

@Injection.Singleton
@Weight(90)
class MyService3 extends MyService2 {
    @Injection.Inject
    MyService3(MyService service) {
        super(service, false);
    }

    @Override
    public String message() {
        return super.message() + ":MyService3";
    }
}
