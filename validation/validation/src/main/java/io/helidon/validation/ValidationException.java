package io.helidon.validation;

import java.util.List;

public class ValidationException extends RuntimeException {
    private final List<ConstraintViolation> violations;

    public ValidationException(String message) {
        super(message);
        this.violations = List.of();
    }

    public <T> ValidationException(List<ConstraintViolation> violations, String message) {
        super(message);
        this.violations = List.copyOf(violations);
    }

    public List<ConstraintViolation> violations() {
        return violations;
    }
}
