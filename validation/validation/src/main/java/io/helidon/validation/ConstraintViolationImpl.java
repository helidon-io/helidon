package io.helidon.validation;

import java.util.Optional;

class ConstraintViolationImpl implements ConstraintViolation {
    private final Class<?> rootType;
    private final Object rootObject;
    private final Location currentLocation;
    private final Object invalidValue;
    private final String message;

    ConstraintViolationImpl(Class<?> rootType,
                            Object rootObject,
                            Location currentLocation,
                            Object invalidValue,
                            String message) {
        this.rootType = rootType;
        this.rootObject = rootObject;
        this.currentLocation = currentLocation;
        this.invalidValue = invalidValue;
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public Location location() {
        return currentLocation;
    }

    @Override
    public Optional<Object> rootObject() {
        return Optional.ofNullable(rootObject);
    }

    @Override
    public Class<?> rootType() {
        return rootType;
    }

    @Override
    public Object invalidValue() {
        return invalidValue;
    }
}
