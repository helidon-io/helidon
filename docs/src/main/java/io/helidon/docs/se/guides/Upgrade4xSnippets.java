/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se.guides;

import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@SuppressWarnings("ALL")
class Upgrade4xSnippets {

    /*
    // tag::snippet_1[]
    static Single<WebServer> startServer() {
        Config config = Config.create();

        WebServer server = WebServer.builder(createRouting(config))
                .config(config.get("server"))
                .addMediaSupport(JsonpSupport.create())
                .build();

        Single<WebServer> webserver = server.start(); // <1>

        webserver.thenAccept(ws -> { // <2>
                    System.out.println("WEB server is up! http://localhost:" + ws.port() + "/greet");
                    ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionallyAccept(t -> { // <3>
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                });

        return webserver;
    }
    // end::snippet_1[]
    */

    class Main {

        // tag::snippet_2[]
        public static void main(String[] args) {

            Config config = Config.create();
            Config.global(config);

            WebServer server = WebServer.builder() // <1>
                    .config(config.get("server"))
                    .routing(Main::routing)
                    .build()
                    .start(); // <2>

            System.out.println("WEB server is up! http://localhost:" + server.port() + "/greet"); // <3>
        }
        // end::snippet_2[]

        static void routing(HttpRouting.Builder routing) {
        }
    }

    /*
    // tag::snippet_3[]
    private static Routing createRouting(Config config) {

        MetricsSupport metrics = MetricsSupport.create(); // <1>
        HealthSupport health = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks())
                .build();

        GreetService greetService = new GreetService(config); // <2>

        return Routing.builder()
                .register(health) // <3>
                .register(metrics)
                .register("/greet", greetService) // <4>
                .build();
    }
    // end::snippet_3[]
    */

    // stub
    static class GreetService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
        }
    }

    // tag::snippet_4[]
    static void routing(HttpRouting.Builder routing) {
        routing.register("/greet", new GreetService()); // <1>
    }
    // end::snippet_4[]

    /*
    // tag::snippet_5[]
    public class GreetService implements Service {

        @Override
        public void update(Routing.Rules rules) { // <1>
            rules
                    .get("/", this::getDefaultMessageHandler)
                    .get("/{name}", this::getMessageHandler)
                    .put("/greeting", this::updateGreetingHandler);
        }

        private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) { // <2>
            sendResponse(response, "World");
        }

        // other methods omitted
    }
    // end::snippet_5[]
    */

    class Snippet6 {

        // tag::snippet_6[]
        public class GreetService implements HttpService { // <1>

            @Override
            public void routing(HttpRules rules) { // <2>
                rules.get("/", this::getDefaultMessageHandler)
                        .get("/{name}", this::getMessageHandler)
                        .put("/greeting", this::updateGreetingHandler);
            }

            private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) { // <3>
                sendResponse(response, "World");
            }

            private void getMessageHandler(ServerRequest request, ServerResponse response) {
                // ...
            }

            private void updateGreetingHandler(ServerRequest request, ServerResponse response) { // <3>
                // ...
            }
        }
        // end::snippet_6[]
    }

    static void sendResponse(ServerResponse response, String str) {
    }

    void snippet_7() {
        // tag::snippet_7[]
        Config config = Config.create();  // Uses default config sources
        Config.global(config);
        // end::snippet_7[]
    }

    void snippet_8() {
        // tag::snippet_8[]
        Config config = Config.global();
        // end::snippet_8[]
    }
}
