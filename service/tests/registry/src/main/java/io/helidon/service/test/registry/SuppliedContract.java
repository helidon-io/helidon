package io.helidon.service.test.registry;

import io.helidon.service.registry.Service;

@Service.Contract
interface SuppliedContract {
    String message();
}
