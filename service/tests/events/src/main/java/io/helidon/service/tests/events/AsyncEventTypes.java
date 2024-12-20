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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.service.registry.Event;
import io.helidon.service.registry.Service;

class AsyncEventTypes {
    private AsyncEventTypes() {
    }

    @Service.Singleton
    static class EventEmitter {
        private final Event.Emitter<EventObject> event;

        @Service.Inject
        EventEmitter(Event.Emitter<EventObject> event) {
            this.event = event;
        }

        CompletionStage<EventObject> emit(EventObject eventObject) {
            return this.event.emitAsync(eventObject);
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
    static class SyncEventObserver {
        private volatile String threadName;
        private volatile EventObject eventObject;
        private volatile CountDownLatch latch = new CountDownLatch(1);

        @Event.Observer
        void event(EventObject eventObject) {
            this.threadName = Thread.currentThread().getName();
            this.eventObject = eventObject;
            latch.countDown();
        }

        EventObject eventObject() throws InterruptedException {
            latch.await(10, TimeUnit.SECONDS);
            latch = new CountDownLatch(1);
            return eventObject;
        }

        String threadName() {
            return threadName;
        }
    }

    @Service.Singleton
    static class EventObserver {
        private volatile String threadName;
        private volatile EventObject eventObject;
        private volatile CountDownLatch latch = new CountDownLatch(1);

        @Event.AsyncObserver
        void event(EventObject eventObject) {
            this.threadName = Thread.currentThread().getName();
            this.eventObject = eventObject;
            latch.countDown();
        }

        EventObject eventObject() throws InterruptedException {
            latch.await(10, TimeUnit.SECONDS);
            latch = new CountDownLatch(1);
            return eventObject;
        }

        String threadName() {
            return threadName;
        }
    }
}
