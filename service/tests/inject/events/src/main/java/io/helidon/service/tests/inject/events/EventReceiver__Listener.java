package io.helidon.service.tests.inject.events;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.Event;

@Injection.Singleton
public class EventReceiver__Listener implements Event.Consumer<EventObject> {
    private final EventReceiver receiver;

    EventReceiver__Listener(EventReceiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public void consume(EventObject eventObject) {
        receiver.event(eventObject);
    }
}
