package io.helidon.microprofile.jwt.auth.cdi;

import org.eclipse.microprofile.jwt.ClaimValue;

/**
 * Created by David Kral.
 */
class ClaimValueWrapper<T> implements ClaimValue<T> {

    private final String name;
    private final T value;

    ClaimValueWrapper(String name, T value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public T getValue() {
        return value;
    }
}
