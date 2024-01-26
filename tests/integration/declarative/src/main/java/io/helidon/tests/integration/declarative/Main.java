package io.helidon.tests.integration.declarative;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.inject.ManagedRegistry;
import io.helidon.inject.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpFeature;

public class Main {
    public static void main(String[] args) {
        ManagedRegistry injectionServices = ManagedRegistry.create();

        WebServer server = WebServer.builder()
                .serviceRegistry(injectionServices.registry())
                .build()
                .start();
    }
}
