package io.helidon.inject;

import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;

@Injection.Contract
interface ScopeHandler {
    TypeName supportedScope();

    Optional<Scope> currentScope();
}
