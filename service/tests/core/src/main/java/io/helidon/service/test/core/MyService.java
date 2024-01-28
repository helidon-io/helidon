package io.helidon.service.test.core;

import io.helidon.service.core.Service;

@Service.Provider
class MyService implements MyContract {
    MyService() {
    }

    @Override
    public String message() {
        return "MyService";
    }
}
