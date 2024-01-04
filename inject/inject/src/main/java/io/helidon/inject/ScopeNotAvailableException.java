package io.helidon.inject;

import io.helidon.common.types.TypeName;

public class ScopeNotAvailableException extends InjectionException {
    private final TypeName scope;

    public ScopeNotAvailableException(String msg, TypeName scope) {
        super(msg);
        this.scope = scope;
    }

    public TypeName scope() {
        return scope;
    }
}
