package io.helidon.service.tests.inject.events.api;

/**
 * This exception is thrown when event dispatching fails.
 */
public class EventDispatchException extends RuntimeException {
    public EventDispatchException(String message) {
        super(message);
    }

    public EventDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
