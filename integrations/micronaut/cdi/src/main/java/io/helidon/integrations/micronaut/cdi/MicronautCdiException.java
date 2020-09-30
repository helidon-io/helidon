package io.helidon.integrations.micronaut.cdi;

public class MicronautCdiException extends RuntimeException {
    public MicronautCdiException(String message) {
        super(message);
    }

    public MicronautCdiException(Throwable cause) {
        super(cause);
    }

    public MicronautCdiException(String message, Throwable cause) {
        super(message, cause);
    }
}
