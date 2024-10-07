package io.helidon.service.tests.inject.events;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.Event;

@Injection.Singleton
class EventReceiver {
    private volatile EventObject eventObject;

    @Event.Listener
    void event(EventObject eventObject) {
        this.eventObject = eventObject;
    }

    EventObject eventObject() {
        return eventObject;
    }
}
