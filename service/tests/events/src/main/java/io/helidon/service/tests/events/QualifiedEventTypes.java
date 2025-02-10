/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.service.tests.events;

import io.helidon.service.registry.Event;
import io.helidon.service.registry.Service;

class QualifiedEventTypes {
    private QualifiedEventTypes() {
    }

    @Service.Qualifier
    @interface EventQualifier {
    }

    @Service.Singleton
    static class EventEmitter {
        private final Event.Emitter<EventObject> unqualifiedEvent;
        private final Event.Emitter<EventObject> event;

        @Service.Inject
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

    @Service.Singleton
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
