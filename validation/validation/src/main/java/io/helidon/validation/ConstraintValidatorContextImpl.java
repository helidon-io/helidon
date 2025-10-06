package io.helidon.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class ConstraintValidatorContextImpl implements ConstraintValidatorContext {
    private final Object rootObject;
    private final Class<?> rootType;
    private volatile ConstraintViolation.Location currentLocation = ConstraintViolation.Location.CONSTRUCTOR;

    ConstraintValidatorContextImpl(Class<?> rootType, Object rootObject) {
        this.rootType = rootType;
        this.rootObject = rootObject;
    }

    @Override
    public Validation.ValidatorResponse response(Object invalidValue, String message) {
        ConstraintViolation violation = new ConstraintViolationImpl(rootType,
                                                                    rootObject,
                                                                    currentLocation,
                                                                    invalidValue,
                                                                    message);
        return new FailedResponse(violation);

    }

    @Override
    public Validation.ValidatorResponse response() {
        return OkResponse.INSTANCE;
    }

    @Override
    public void location(ConstraintViolation.Location location) {
        this.currentLocation = location;
    }

    private static class FailedResponse implements Validation.ValidatorResponse {

        private final List<ConstraintViolation> violations;

        private FailedResponse(ConstraintViolation violation) {
            this.violations = List.of(violation);
        }

        private FailedResponse(List<ConstraintViolation> violations) {
            this.violations = List.copyOf(violations);
        }

        @Override
        public List<ConstraintViolation> violations() {
            return violations;
        }

        @Override
        public boolean failed() {
            return true;
        }

        @Override
        public String message() {
            return violations.stream()
                    .map(ConstraintViolation::message)
                    .collect(Collectors.joining(", "));
        }

        @Override
        public Validation.ValidatorResponse merge(Validation.ValidatorResponse other) {
            List<ConstraintViolation> violations = new ArrayList<>(this.violations);
            violations.addAll(other.violations());
            return new FailedResponse(violations);
        }

        @Override
        public ValidationException toException() {
            throw new ValidationException(violations(), "Constraint validation failed: " + message());
        }
    }

    private static class OkResponse implements Validation.ValidatorResponse {
        private static final OkResponse INSTANCE = new OkResponse();

        private OkResponse() {
        }

        @Override
        public boolean failed() {
            return false;
        }

        @Override
        public String message() {
            return "";
        }

        @Override
        public Validation.ValidatorResponse merge(Validation.ValidatorResponse other) {
            return other;
        }

        @Override
        public ValidationException toException() {
            throw new IllegalStateException("Cannot create an exception for a response that did not fail");
        }

        @Override
        public List<ConstraintViolation> violations() {
            return List.of();
        }
    }
}
