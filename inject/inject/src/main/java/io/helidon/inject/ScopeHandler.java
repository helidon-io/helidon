package io.helidon.inject;

import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;

@Injection.Contract
interface ScopeHandler {
    TypeName TYPE_NAME = TypeName.create(ScopeHandler.class);

    TypeName supportedScope();

    Optional<Scope> currentScope();
}
