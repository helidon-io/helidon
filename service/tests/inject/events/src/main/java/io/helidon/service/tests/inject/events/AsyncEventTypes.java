package io.helidon.service.tests.inject.events;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.tests.inject.events.api.Event;

public class AsyncEventTypes {
    private AsyncEventTypes() {
    }

    @Injection.Singleton
    static class EventEmitter {
        private final Event.Emitter<EventObject> event;

        @Injection.Inject
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

    @Injection.Singleton
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

    @Injection.Singleton
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
