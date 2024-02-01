package io.helidon.service.test.inject;

import io.helidon.service.inject.api.Injection;

@Injection.Singleton
class MyService implements MyContract {
    static int instances = 0;

    MyService() {
        instances++;
    }

    @Override
    public String message() {
        return "MyService";
    }
}
