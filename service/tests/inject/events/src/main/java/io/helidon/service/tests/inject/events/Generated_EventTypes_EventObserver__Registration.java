package io.helidon.service.tests.inject.events;

import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.EventObserverRegistration;
import io.helidon.service.tests.inject.events.api.EventManager;

@Injection.Singleton
class Generated_EventTypes_EventObserver__Registration implements EventObserverRegistration {
    private static final ResolvedType EVENT_OBJECT = ResolvedType.create(EventTypes.EventObject.class);

    private final EventTypes.EventObserver eventObserver;

    @Injection.Inject
    Generated_EventTypes_EventObserver__Registration(EventTypes.EventObserver eventObserver) {
        this.eventObserver = eventObserver;
    }

    @Override
    public void register(EventManager manager) {
        manager.register(EVENT_OBJECT, eventObserver::event, Set.of());
    }
}
