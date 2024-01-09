package io.helidon.inject;

import java.util.Map;

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Service for starting a request scope.
 * Do not forget to call {@link io.helidon.inject.Scope#close()} when the scope should finish.
 */
@Injection.Contract
public interface RequestonControl {
    /**
     * Start request scope.
     */
    Scope startRequestScope(String id, Map<ServiceDescriptor<?>, Object> initialBindings);
}
