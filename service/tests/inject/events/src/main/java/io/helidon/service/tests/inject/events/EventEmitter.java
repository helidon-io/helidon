package io.helidon.service.tests.inject.events;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.Event;

@Injection.Singleton
class EventEmitter {
    private final Event.Emitter<EventObject> event;

    @Injection.Inject
    EventEmitter(Event.Emitter<EventObject> event) {
        this.event = event;
    }

    void emit(EventObject eventObject) {
        this.event.emit(eventObject);
    }
}
