package io.helidon.service.tests.inject.events;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.tests.inject.events.api.Event;
import io.helidon.service.tests.inject.events.api.EventManager;

/*
Specific service for a single injection point.
The descriptor generator for QualifiedEventTypes.EventEmitter must use this type as the
injection point contract, to ensure it will be used (and none other)
 */
@Injection.Singleton
@QualifiedEventTypes.EventQualifier
class Generated_QualifiedEvenTypes_EventEmitter__Emitter implements Event.Emitter<QualifiedEventTypes.EventObject> {
    private static final ResolvedType EVENT_OBJECT = ResolvedType.create(QualifiedEventTypes.EventObject.class);
    private static final Set<Qualifier> QUALIFIERS = Set.of(
            Qualifier.create(TypeName.create("io.helidon.service.tests.inject.events.QualifiedEventTypes.EventQualifier"))
    );

    private final EventManager manager;

    @Injection.Inject
    Generated_QualifiedEvenTypes_EventEmitter__Emitter(EventManager manager) {
        this.manager = manager;
    }

    @Override
    public void emit(QualifiedEventTypes.EventObject eventObject, Qualifier... qualifiers) {
        manager.emit(EVENT_OBJECT, eventObject, mergeQualifiers(qualifiers));
    }

    @Override
    public CompletionStage<QualifiedEventTypes.EventObject> emitAsync(QualifiedEventTypes.EventObject eventObject, Qualifier... qualifiers) {
        return manager.emitAsync(EVENT_OBJECT, eventObject, mergeQualifiers(qualifiers));
    }

    private static Set<Qualifier> mergeQualifiers(Qualifier... qualifiers) {
        if (qualifiers.length == 0) {
            return QUALIFIERS;
        }
        var qualifierSet = new HashSet<>(QUALIFIERS);
        qualifierSet.addAll(Set.of(qualifiers));
        return qualifierSet;
    }
}
