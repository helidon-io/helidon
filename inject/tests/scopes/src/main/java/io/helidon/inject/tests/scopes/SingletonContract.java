package io.helidon.inject.tests.scopes;

import io.helidon.inject.service.Injection;

@Injection.Contract
interface SingletonContract {
    int id();
}
