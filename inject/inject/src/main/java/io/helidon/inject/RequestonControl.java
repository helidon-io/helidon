package io.helidon.inject;

import java.util.Map;

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ServiceDescriptor;

@Injection.Contract
public interface RequestonControl {
    /**
     * Start request scope.
     */
    Scope startRequestScope(String id, Map<ServiceDescriptor<?>, Object> initialBindings);
}
