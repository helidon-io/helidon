package io.helidon.service.tests.inject.events;

import io.helidon.service.inject.api.Event;
import io.helidon.service.inject.api.Injection;

class QualifiedEventTypes {
    private QualifiedEventTypes() {
    }

    @Injection.Qualifier
    @interface EventQualifier {
    }

    @Injection.Singleton
    static class EventEmitter {
        private final Event.Emitter<EventObject> unqualifiedEvent;
        private final Event.Emitter<EventObject> event;


        @Injection.Inject
        EventEmitter(@EventQualifier Event.Emitter<EventObject> event,
                     Event.Emitter<EventObject> unqualifiedEvent) {
            this.event = event;
            this.unqualifiedEvent = unqualifiedEvent;
        }

        void emit(EventObject eventObject) {
            this.event.emit(eventObject);
            this.unqualifiedEvent.emit(eventObject);
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
        private volatile EventObject unqualifiedEventObject;

        @Event.Observer
        @EventQualifier
        void event(EventObject eventObject) {
            this.eventObject = eventObject;
        }

        @Event.Observer
        void unqualifiedEvent(EventObject object) {
            this.unqualifiedEventObject = object;
        }

        EventObject eventObject() {
            return eventObject;
        }

        EventObject unqualifiedEventObject() {
            return unqualifiedEventObject;
        }
    }
}
