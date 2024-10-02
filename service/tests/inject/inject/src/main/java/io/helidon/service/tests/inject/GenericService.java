package io.helidon.service.tests.inject;

import io.helidon.service.inject.api.Injection;

@Injection.Singleton
class GenericService implements GenericContract<String> {

    @Override
    public String get() {
        return "Hello";
    }
}
