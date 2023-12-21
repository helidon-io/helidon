package io.helidon.inject;

import io.helidon.inject.service.Injection;

@Injection.Contract
public interface RequestonControl {
    /**
     * Start request scope.
     */
    Scope startRequestScope();
}
