package io.helidon.service.tests.inject.events.api;

import io.helidon.service.registry.Service;

@Service.Contract
public interface EventObserverRegistration {
    void register(EventManager manager);
}
