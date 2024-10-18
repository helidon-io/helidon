package io.helidon.service.inject.api;

import java.util.Objects;

/**
 * This exception is thrown when event dispatching fails.
 */
public class EventDispatchException extends RuntimeException {
    /**
     * Create an exception with the first encountered exception as the cause.
     * Additional exceptions (if encountered) are added as {@link #getSuppressed()}.
     *
     * @param message descriptive message
     * @param cause cause of the failure
     */
    public EventDispatchException(String message, Throwable cause) {
        super(Objects.requireNonNull(message),
              Objects.requireNonNull(cause));
    }
}
