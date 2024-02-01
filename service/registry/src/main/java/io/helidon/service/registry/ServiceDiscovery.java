package io.helidon.service.registry;

import java.util.List;

public interface ServiceDiscovery {
    static ServiceDiscovery instance() {
        return CoreServiceDiscovery.instance();
    }

    static ServiceDiscovery noop() {
        return CoreServiceDiscovery.noop();
    }

    List<DescriptorMetadata> allMetadata();
}
