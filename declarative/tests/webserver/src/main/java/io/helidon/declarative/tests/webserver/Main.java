package io.helidon.declarative.tests.webserver;

import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.webserver.WebServer;

public class Main {
    public static void main(String[] args) {
        InjectRegistryManager injectionServices = InjectRegistryManager.create();

        WebServer server = WebServer.builder()
                .serviceRegistry(injectionServices.registry())
                .build()
                .start();
    }
}
