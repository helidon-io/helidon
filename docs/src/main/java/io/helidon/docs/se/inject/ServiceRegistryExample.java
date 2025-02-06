package io.helidon.docs.se.inject;

import io.helidon.service.registry.ServiceRegistryManager;

class ServiceRegistryExample {

    public void programmatic() {
        // tag::snippet_1[]
        // create an instance of a registry manager - can be configured and shut down
        var registryManager = ServiceRegistryManager.create();
        // get the associated service registry
        var registry = registryManager.registry();
        // end::snippet_1[]
    }

}
