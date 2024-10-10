package io.helidon.service.tests.inject.events;

// arbitrary object
class EventObject {
    private final String message;

    EventObject(String message) {
        this.message = message;
    }

    String message() {
        return message;
    }
}
