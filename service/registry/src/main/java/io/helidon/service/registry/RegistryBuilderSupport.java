package io.helidon.service.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Methods used from generated code in builders when service registry (without Config) is used.
 */
public class RegistryBuilderSupport {
    private RegistryBuilderSupport() {
    }

    /**
     * Discover services from the registry.
     *
     * @param registry    service registry to use, if provided explicitly
     * @param contract    contract that is requested
     * @param useRegistry whether to use the service registry at all
     * @param <T>         type of the contract
     * @return a list of contract implementation from the registry
     */
    public static <T> List<T> serviceList(Optional<ServiceRegistry> registry,
                                          TypeName contract,
                                          boolean useRegistry) {
        if (!useRegistry) {
            return List.of();
        }

        List<T> result = new ArrayList<>();
        if (registry.isPresent()) {
            result.addAll(registry.get().all(contract));
        } else {
            result.addAll(Services.all(contract));
        }

        return List.copyOf(result);
    }

    /**
     * Discover services from the registry.
     *
     * @param registry    service registry to use, if provided explicitly
     * @param contract    contract that is requested
     * @param useRegistry whether to use the service registry at all
     * @param <T>         type of the contract
     * @return a set of contract implementation
     */
    public static <T> Set<T> serviceSet(Optional<ServiceRegistry> registry,
                                        TypeName contract,
                                        boolean useRegistry) {
        if (!useRegistry) {
            return Set.of();
        }

        Set<T> result = new HashSet<>();
        if (registry.isPresent()) {
            result.addAll(registry.get().all(contract));
        } else {
            result.addAll(Services.all(contract));
        }

        return Set.copyOf(result);
    }

    /**
     * Get the first service from the registry if not configured in the builder.
     *
     * @param registry      service registry to use, if provided explicitly
     * @param contract      contract that is requested
     * @param existingValue current values configured on the builder
     * @param useRegistry   whether to use the service registry at all
     * @param <T>           type of the contract
     * @return a list of contract implementation, combined from what user provided in builder and what was discovered in registry
     */
    public static <T> Optional<T> service(Optional<ServiceRegistry> registry,
                                          TypeName contract,
                                          Optional<T> existingValue,
                                          boolean useRegistry) {
        if (existingValue.isPresent() || !useRegistry) {
            return existingValue;
        }

        if (registry.isPresent()) {
            return (registry.get().first(contract));
        } else {
            return Services.first(contract);
        }
    }
}
