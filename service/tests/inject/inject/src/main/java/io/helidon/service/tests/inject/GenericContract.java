package io.helidon.service.tests.inject;

import io.helidon.service.registry.Service;

@Service.Contract
interface GenericContract<T> {
    T get();
}
