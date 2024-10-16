package io.helidon.service.tests.inject.events;

import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.tests.inject.events.api.EventObserverRegistration;
import io.helidon.service.tests.inject.events.api.EventManager;

@Injection.Singleton
class Generated_QualifiedEventTypes_EventObserver__Registration implements EventObserverRegistration {
    private static final ResolvedType EVENT_OBJECT = ResolvedType.create(QualifiedEventTypes.EventObject.class);
    private static final Set<Qualifier> QUALIFIERS = Set.of(
            Qualifier.create(TypeName.create("io.helidon.service.tests.inject.events.QualifiedEventTypes.EventQualifier"))
    );

    private final QualifiedEventTypes.EventObserver eventObserver;

    @Injection.Inject
    Generated_QualifiedEventTypes_EventObserver__Registration(QualifiedEventTypes.EventObserver eventObserver) {
        this.eventObserver = eventObserver;
    }

    @Override
    public void register(EventManager manager) {
        manager.register(EVENT_OBJECT, eventObserver::event, QUALIFIERS);
    }
}
