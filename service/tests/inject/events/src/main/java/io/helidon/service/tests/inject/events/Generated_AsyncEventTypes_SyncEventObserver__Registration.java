package io.helidon.service.tests.inject.events;

import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.EventManager;
import io.helidon.service.tests.inject.events.api.EventObserverRegistration;

@Injection.Singleton
class Generated_AsyncEventTypes_SyncEventObserver__Registration implements EventObserverRegistration {
    private static final ResolvedType EVENT_OBJECT = ResolvedType.create(AsyncEventTypes.EventObject.class);

    private final AsyncEventTypes.SyncEventObserver eventObserver;

    @Injection.Inject
    Generated_AsyncEventTypes_SyncEventObserver__Registration(AsyncEventTypes.SyncEventObserver eventObserver) {
        this.eventObserver = eventObserver;
    }

    @Override
    public void register(EventManager manager) {
        manager.register(EVENT_OBJECT, eventObserver::event, Set.of());
    }
}
