package io.helidon.service.inject.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.ServiceInfo;

/**
 * Service metadata.
 */
public interface InjectServiceInfo extends io.helidon.service.registry.ServiceInfo {
    /**
     * List of injection points required by this service (and possibly by its supertypes).
     * Each dependency is a point of injection of one instance into
     * constructor, method parameter, or a field.
     *
     * @return required dependencies
     */
    default List<Ip> injectionPoints() {
        return List.of();
    }

    @Override
    default List<Dependency> dependencies() {
        return List.copyOf(injectionPoints());
    }

    /**
     * Service qualifiers.
     *
     * @return qualifiers
     */
    default Set<Qualifier> qualifiers() {
        return Set.of();
    }

    /**
     * Run level of this service.
     *
     * @return run level
     */
    default int runLevel() {
        return Injection.RunLevel.NORMAL;
    }

    /**
     * Scope of this service.
     *
     * @return scope of the service
     */
    TypeName scope();

    /**
     * Returns the instance of the core service descriptor.
     * As we use identity, this is a required method that MUST return the singleton instance of the service descriptor.
     *
     * @return singleton instance of the underlying service descriptor
     */
    default io.helidon.service.registry.ServiceInfo coreInfo() {
        // for all injection based service descriptors this is enough
        return this;
    }
}
