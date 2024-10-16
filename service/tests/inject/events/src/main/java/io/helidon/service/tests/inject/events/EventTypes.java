package io.helidon.service.tests.inject.events;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.Event;

class EventTypes {
    private EventTypes() {
    }

    @Injection.Singleton
    static class EventEmitter {
        private final Event.Emitter<EventObject> event;

        @Injection.Inject
        EventEmitter(Event.Emitter<EventObject> event) {
            this.event = event;
        }

        void emit(EventObject eventObject) {
            this.event.emit(eventObject);
        }
    }

    @Injection.Singleton
    static class EventEmitter2 {
        private final Event.Emitter<EventObject> event;

        @Injection.Inject
        EventEmitter2(Event.Emitter<EventObject> event) {
            this.event = event;
        }

        void emit(EventObject eventObject) {
            this.event.emit(eventObject);
        }
    }

    static class EventObject {
        private final String message;

        EventObject(String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
    }

    @Injection.Singleton
    static class EventObserver {
        private volatile EventObject eventObject;

        @Event.Observer
        void event(EventObject eventObject) {
            this.eventObject = eventObject;
        }

        EventObject eventObject() {
            return eventObject;
        }
    }
}
