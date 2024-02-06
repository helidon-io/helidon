package io.helidon.service.tests.inject.configdriven;

import io.helidon.service.registry.Service;

@Service.Contract
interface TheContract {
    String name();
    String value();
}
