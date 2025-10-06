package io.helidon.validation;

import java.util.Optional;

public interface ConstraintViolation {

    /**
     * Descriptive message of the failure.
     *
     * @return message
     */
    String message();

    /**
     * Location of the failure.
     *
     * @return location
     */
    Location location();

    /**
     * Root validated object.
     * When validating an instance, this returns the same instance,
     * when validating a method, field, parameter etc., this returns the instance containing the validated
     * element.
     * When validating a constructor, this returns empty.
     *
     * @return the root of validation, or empty if not available
     */
    Optional<Object> rootObject();

    /**
     * The type of the root validated object, or the class containing the element that is validated.
     *
     * @return root type
     */
    Class<?> rootType();

    /**
     * The value that failed validation.
     * <p>
     * Note: this method may return {@code null}!
     *
     * @return the value that failed validation
     */
    Object invalidValue();

    enum Location {
        RECORD_COMPONENT,
        RETURN_VALUE,
        PARAMETER,
        FIELD,
        METHOD,
        CONSTRUCTOR
    }
}
