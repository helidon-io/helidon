package io.helidon.service.tests.inject.events;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.Event;

@Injection.Singleton
class EventReceiver__Listener implements Event.Listener<EventObject> {
    private final EventReceiver receiver;

    @Injection.Inject
    EventReceiver__Listener(EventReceiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public void onEvent(EventObject eventObject) {
        receiver.event(eventObject);
    }
}
