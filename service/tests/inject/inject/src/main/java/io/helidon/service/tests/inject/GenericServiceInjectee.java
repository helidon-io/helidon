package io.helidon.service.tests.inject;

import io.helidon.service.inject.api.Injection;

@Injection.Singleton
class GenericServiceInjectee {
    @Injection.Inject
    GenericServiceInjectee(GenericContract<String> contract) {
    }
}
