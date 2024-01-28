package io.helidon.service.test.core;

import io.helidon.service.core.Service;

@Service.Contract
public interface MyContract {
    String message();
}
