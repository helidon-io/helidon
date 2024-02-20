package io.helidon.service.inject.api;

import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.ServiceInfo;

/**
 * Inject service registry SPI, to be used for scope handlers and other extension services.
 */
public interface InjectRegistrySpi extends InjectRegistry {
    /**
     * Type name of this interface
     */
    TypeName TYPE = TypeName.create(InjectRegistrySpi.class);

    /**
     * Create a registry for a specific scope.
     *
     * @param scope           scope of the registry
     * @param id              id of the scope instance (i.e. each request scope should have a unique id)
     * @param initialBindings bindings to bind to enable injection within this scope, such as server request for HTTP
     *                        request scope
     * @return a new scoped registry that takes care of lifecycle of service instances with the scope
     */
    ScopedRegistry createForScope(TypeName scope, String id, Map<ServiceInfo, Object> initialBindings);
}
