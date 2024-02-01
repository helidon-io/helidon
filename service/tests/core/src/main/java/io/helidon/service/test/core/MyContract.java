package io.helidon.service.test.core;

import io.helidon.service.registry.Service;

@Service.Contract
public interface MyContract {
    String message();
}
