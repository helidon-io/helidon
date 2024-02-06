package io.helidon.service.test.registry;

import io.helidon.service.registry.Service;

@Service.Contract
public interface MyContract {
    String message();
}
