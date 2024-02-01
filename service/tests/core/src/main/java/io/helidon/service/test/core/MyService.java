package io.helidon.service.test.core;

import io.helidon.service.registry.Service;

@Service.Provider
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
