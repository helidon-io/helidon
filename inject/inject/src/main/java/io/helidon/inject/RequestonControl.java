package io.helidon.inject;

import java.util.Map;

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ServiceInfo;

@Injection.Contract
public interface RequestonControl {
    /**
     * Start request scope.
     */
    Scope startRequestScope(Map<ServiceInfo, Object> initialBindings);
}
