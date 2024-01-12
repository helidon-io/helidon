package io.helidon.tests.integration.declarative;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpFeature;

public class Main {
    public static void main(String[] args) {
        InjectionServices injectionServices = InjectionServices.create();
        Services services = injectionServices.services();
        List<HttpFeature> features = services.all(HttpFeature.class);

        WebServer server = WebServer.builder()
                .config(Config.create().get("server"))
                .routing(routing -> {
                    features.forEach(routing::addFeature);
                })
                .build()
                .start();
    }
}
