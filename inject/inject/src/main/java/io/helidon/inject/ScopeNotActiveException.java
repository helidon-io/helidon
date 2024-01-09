package io.helidon.inject;

import io.helidon.common.types.TypeName;

/**
 * An attempt was done to get a service instance from a scope that is not active.
 */
public class ScopeNotActiveException extends InjectionException {
    private final TypeName scope;

    public ScopeNotActiveException(String msg, TypeName scope) {
        super(msg);
        this.scope = scope;
    }

    public TypeName scope() {
        return scope;
    }
}
