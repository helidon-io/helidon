package io.helidon.validation.validators;

import java.util.function.Predicate;

import io.helidon.common.types.Annotation;
import io.helidon.validation.ConstraintValidatorContext;
import io.helidon.validation.Validation;

public class BaseValidator implements Validation.ConstraintValidator {
    private final Predicate<Object> check;
    private final String messageFormat;
    private final boolean sendNulls;

    /**
     * Create a validator that will not receive {@code null} values - these are considered valid.
     *
     * @param annotation     annotation to obtain a custom message
     * @param defaultMessage default message to use if no custom message is provided in the annotation
     * @param check          predicate that returns {@code true} if the value is valid, {@code false} otherwise
     */
    protected BaseValidator(Annotation annotation, String defaultMessage, Predicate<Object> check) {
        this.messageFormat = annotation.stringValue("message")
                .orElse(defaultMessage);
        this.check = check;
        sendNulls = false;
    }

    /**
     * Create a validator.
     *
     * @param annotation     annotation to obtain a custom message
     * @param defaultMessage default message to use if no custom message is provided in the annotation
     * @param check          predicate that returns {@code true} if the value is valid, {@code false} otherwise
     * @param sendNulls      whether to send {@code null} values to the validator
     */
    protected BaseValidator(Annotation annotation, String defaultMessage, Predicate<Object> check, boolean sendNulls) {
        this.messageFormat = annotation.stringValue("message")
                .orElse(defaultMessage);
        this.check = check;
        this.sendNulls = sendNulls;
    }

    @Override
    public Validation.ValidatorResponse check(ConstraintValidatorContext context, Object value) {
        if (value == null && !sendNulls) {
            return context.response();
        }

        if (check.test(value)) {
            return context.response();
        }

        return context.response(value, formatMessage(convertValue(value)));
    }

    /**
     * Format the error message.
     *
     * @param parameter the invalid value
     * @return string to use in constraint violation
     */
    protected String formatMessage(Object parameter) {
        return messageFormat.formatted(parameter);
    }

    /**
     * Possibility to convert the value before printing an error message.
     *
     * @param object actual object received
     * @return converted object
     */
    protected Object convertValue(Object object) {
        return object;
    }
}
