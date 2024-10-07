package io.helidon.service.tests.inject.events;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.Event;

@Injection.Singleton
class EventProducer {
    private final Event.Publisher<EventObject> event;

    @Injection.Inject
    EventProducer(Event.Publisher<EventObject> event) {
        this.event = event;
    }

    void publish(EventObject eventObject) {
        this.event.publish(eventObject);
    }
}
