package io.helidon.service.tests.inject.events;

import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.EventObserverRegistration;
import io.helidon.service.tests.inject.events.api.EventManager;

@Injection.Singleton
class Generated_AsyncEventTypes_EventObserver__Registration implements EventObserverRegistration {
    private static final ResolvedType EVENT_OBJECT = ResolvedType.create(AsyncEventTypes.EventObject.class);

    private final AsyncEventTypes.EventObserver eventObserver;

    @Injection.Inject
    Generated_AsyncEventTypes_EventObserver__Registration(AsyncEventTypes.EventObserver eventObserver) {
        this.eventObserver = eventObserver;
    }

    @Override
    public void register(EventManager manager) {
        manager.registerAsync(EVENT_OBJECT, eventObserver::event, Set.of());
    }
}
