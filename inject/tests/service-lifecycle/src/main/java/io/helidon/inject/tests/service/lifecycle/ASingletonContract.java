package io.helidon.inject.tests.service.lifecycle;

import io.helidon.inject.service.Injection;

@Injection.Contract
interface ASingletonContract {
    InjectedService service();
}
