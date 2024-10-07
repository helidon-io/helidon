package io.helidon.service.tests.inject.events;

import java.util.List;
import java.util.function.Supplier;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.Event;

@Injection.Singleton
class EventObject__EventHandler implements Event.Publisher<EventObject> {
    private final Supplier<List<Event.Consumer<EventObject>>> consumers;

    @Injection.Inject
    EventObject__EventHandler(Supplier<List<Event.Consumer<EventObject>>> consumers) {
        this.consumers = consumers;
    }

    @Override
    public void publish(EventObject eventObject) {
        consumers.get().forEach(it -> it.consume(eventObject));
    }
}
