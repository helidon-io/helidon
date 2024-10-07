package io.helidon.service.tests.inject.events;

import io.helidon.service.tests.inject.events.api.Event;

@Event.EventObject
class EventObject {
    private final String message;

    EventObject(String message) {
        this.message = message;
    }

    String message() {
        return message;
    }
}
