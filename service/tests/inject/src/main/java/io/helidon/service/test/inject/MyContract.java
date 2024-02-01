package io.helidon.service.test.inject;

import io.helidon.service.registry.Service;

@Service.Contract
public interface MyContract {
    String message();
}
