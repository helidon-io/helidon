package io.helidon.inject.service;

/**
 * An exception marking a problem with service registry operations.
 */
public class ServiceRegistryException extends RuntimeException {
    /**
     * Create an exception with a descriptive message.
     *
     * @param message the message
     */
    public ServiceRegistryException(String message) {
        super(message);
    }

    /**
     * Create an exception with a descriptive message and a cause.
     *
     * @param message the message
     * @param cause throwable causing this exception
     */
    public ServiceRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
