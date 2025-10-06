package io.helidon.validation;

public interface ConstraintValidatorContext {
    static ConstraintValidatorContext create(Class<?> rootType) {
        return new ConstraintValidatorContextImpl(rootType, null);
    }

    static ConstraintValidatorContext create(Class<?> rootType, Object rootObject) {
        return new ConstraintValidatorContextImpl(rootType, rootObject);
    }

    Validation.ValidatorResponse response(Object invalidValue, String message);

    Validation.ValidatorResponse response();

    void location(ConstraintViolation.Location location);
}
