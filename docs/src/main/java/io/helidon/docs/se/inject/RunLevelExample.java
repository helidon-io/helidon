package io.helidon.docs.se.inject;

import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;

/**
 * A example that illustrates {@link Service.RunLevel}
 */
class RunLevelExample {

    // tag::snippet_1[]
    @Service.RunLevel(1)
    @Service.Singleton
    class Level1 {

        @Service.PostConstruct
        void onCreate() {
            System.out.println("level1 created");
        }
    }

    @Service.RunLevel(2)
    @Service.Singleton
    class Level2 {

        @Service.PostConstruct
        void onCreate() {
            System.out.println("level2 created");
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    public static void main(String[] args) {
        ServiceRegistryManager registryManager = ServiceRegistryManager.start();
        registryManager.shutdown();
    }
    // end::snippet_2[]
}
