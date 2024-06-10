/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.tests.integration.nativeimage.mp2;

import io.helidon.common.Errors;
import io.helidon.microprofile.server.Server;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import static io.helidon.http.Status.OK_200;

/**
 * Main class of this integration test.
 */
public final class Mp2Main {
    /**
     * Cannot be instantiated.
     */
    private Mp2Main() {
    }

    /**
     * Application main entry point.
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        Server server = Server.create().start();

        boolean failed = false;
        long now = System.currentTimeMillis();
        try {
            test(server.port());
        } catch (Exception e) {
            e.printStackTrace(System.out);
            failed = true;
        }
        long time = System.currentTimeMillis() - now;
        System.out.println("Tests finished in " + time + " millis");

        server.stop();

        if (failed) {
            System.exit(-1);
        }
    }

    private static void test(int port) {
        WebTarget target;
        try (Client client = ClientBuilder.newClient()) {
            target = client.target("http://localhost:" + port);
            Errors.Collector collector = Errors.collector();

            // testing all modules
            validateJaxrs(collector, target);
            validateDb(collector, target);

            collector.collect()
                    .checkValid();
        }
    }

    private static void validateJaxrs(Errors.Collector collector, WebTarget target) {
        Response response = target.request().get();
        validateResponse(response, collector, "/", "Hello");
    }

    private static void validateDb(Errors.Collector collector, WebTarget target) {
        String path = "/db/Jack";
        WebTarget jack = target.path(path);

        Response response = jack.request().get();
        validateResponse(response, collector, path, "The Ripper");

        response = jack.request().post(Entity.text("Sparrow"));
        validateResponse(response, collector, path, "Jack");

        response = jack.request().get();
        validateResponse(response, collector, path, "Sparrow");

        response = jack.request().post(Entity.text("The Ripper"));
        validateResponse(response, collector, path, "Jack");

        response = jack.request().get();
        validateResponse(response, collector, path, "The Ripper");
    }

    private static void validateResponse(Response response, Errors.Collector collector, String path, String expected) {
        if (response.getStatus() == OK_200.code()) {
            String entity = response.readEntity(String.class);
            if (!expected.equals(entity)) {
                collector.fatal("Endpoint " + path + " should return \"" + expected + "\", but returned \"" + entity + "\"");
            }
        } else {
            collector.fatal("Endpoint " + path + " should be accessible. Status received: "
                                    + response.getStatus());
        }
    }
}

