package io.helidon.examples.webserver.serviceregistry;

import io.helidon.inject.ManagedRegistry;
import io.helidon.inject.service.ServiceRegistry;
import io.helidon.webserver.WebServer;

public class Main {
    public static void main(String[] args) {
        ServiceRegistry registry = ManagedRegistry.create()
                .registry();

        WebServer server = WebServer.builder()
                .routing(routing -> routing.get("/imperative", (req, res) -> res.send("Hello from imperative.")))
                .serviceRegistry(registry)
                .build()
                .start();

        System.out.println("Started server on http://localhost:" + server.port());
    }
}
