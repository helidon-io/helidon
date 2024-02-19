package io.helidon.service.registry;

import java.util.List;

/**
 * Access to discovered service metadata.
 */
public interface ServiceDiscovery {
    /**
     * Create a new instance that discovers service descriptors from classpath.
     *
     * @return service discovery based on classpath
     */
    static ServiceDiscovery create() {
        return CoreServiceDiscovery.create();
    }

    /**
     * Service discovery that does not discover anything.
     *
     * @return a no-op service discovery
     */
    static ServiceDiscovery noop() {
        return CoreServiceDiscovery.noop();
    }

    /**
     * All discovered metadata of this service discovery.
     *
     * @return all discovered metadata
     */
    List<DescriptorMetadata> allMetadata();
}
