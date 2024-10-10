package io.helidon.service.tests.inject.events.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import io.helidon.service.registry.Service;

/**
 * Injection event types.
 * <p>
 * To publish an event, inject an instance of {@link io.helidon.service.tests.inject.events.api.Event.Emitter}.
 * <p>
 * To receive events, implement a method (at least package private) with the event object as a parameter, annotated with
 * {@link io.helidon.service.tests.inject.events.api.Event.Observes}. The method can have any name, must be {@code void},
 * and have a single parameter.
 *
 */
public final class Event {
    private Event() {
    }

    @Target(ElementType.METHOD)
    public @interface Observes {
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface ObservesAsync {
    }

    /**
     *  To publish an event, simply inject an instance of this type (correctly typed with your event object) into your service,
     * and call {@link #emit(Object)} on it when needed.
     * <p>
     * A single service can inject more than one instance, if it wants to publish events of different types.
     * The type of the event is determining which events are published (i.e. qualifiers or any other annotation are not relevant,
     * only the type of the object).
     *
     * @param <T> type of the event object
     */
    @Service.Contract
    public interface Emitter<T> {
        /**
         * Publish an event.
         * The method blocks until all listeners are processed.
         *
         * @param eventObject event object to deliver to the listeners
         */
        void emit(T eventObject);
    }

    @Service.Contract
    public interface Listener<T> {
        void onEvent(T eventObject);
    }
}
