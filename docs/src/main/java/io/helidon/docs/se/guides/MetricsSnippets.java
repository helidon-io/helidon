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

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Random;

import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.metrics.MetricsObserver;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

@SuppressWarnings("ALL")
class MetricsSnippets {

    // stub
    class Main {
        static void routing(HttpRouting.Builder routing) {
        }
    }

    void snippet_1() {
        // tag::snippet_1[]
        ObserveFeature observe = ObserveFeature.builder()   // <1>
                .addObserver(MetricsObserver.builder() // <2>
                                     .enabled(false) // <3>
                                     .build()) // <4>
                .build(); // <5>

        WebServer server = WebServer.builder() // <6>
                .config(Config.global().get("server"))
                .addFeature(observe)
                .routing(Main::routing)
                .build()
                .start();
    }
    // end::snippet_1[]

    void snippet_2(Config config) {
        // tag::snippet_2[]
        KeyPerformanceIndicatorMetricsConfig kpiConfig =
                KeyPerformanceIndicatorMetricsConfig.builder() // <1>
                        .extended(true) // <2>
                        .longRunningRequestThreshold(Duration.ofSeconds(4)) // <3>
                        .build();

        MetricsObserver metrics = MetricsObserver.builder()
                .metricsConfig(MetricsConfig.builder() // <4>
                                       .keyPerformanceIndicatorMetricsConfig(kpiConfig)) // <5>
                .build();

        ObserveFeature observe = ObserveFeature.builder()
                .config(config.get("server.features.observe"))
                .addObserver(metrics) // <6>
                .build();

        WebServer server = WebServer.builder() // <7>
                .config(Config.global().get("server"))
                .addFeature(observe)
                .routing(Main::routing)
                .build()
                .start();
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        public class GreetingCards implements HttpService {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

            private final Counter cardCounter; // <1>

            GreetingCards() {
                cardCounter = Metrics.globalRegistry()
                        .getOrCreate(Counter.builder("cardCount")
                                             .description("Counts card retrievals")); // <2>
            }

            @Override
            public void routing(HttpRules rules) {
                rules.get("/", this::getDefaultMessageHandler);
            }

            private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
                cardCounter.increment(); // <3>
                sendResponse(response, "Here are some cards ...");
            }

            private void sendResponse(ServerResponse response, String msg) {
                JsonObject returnObject = JSON.createObjectBuilder().add("message", msg).build();
                response.send(returnObject);
            }
        }
        // end::snippet_3[]
    }

    // stub
    static class GreetService implements HttpService {

        @Override
        public void routing(HttpRules rules) {
        }
    }

    // stub
    static class GreetingCards implements HttpService {

        @Override
        public void routing(HttpRules rules) {
        }
    }

    // tag::snippet_4[]
    static void routing(HttpRouting.Builder routing) {
        Config config = Config.global();

        routing
                .register("/greet", new GreetService())
                .register("/cards", new GreetingCards()) // <1>
                .get("/simple-greet", (req, res) -> res.send("Hello World!"));
    }
    // end::snippet_4[]

    class Snippet5 {

        // tag::snippet_5[]
        public class GreetingCards implements HttpService {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
            private final Timer cardTimer; // <1>

            GreetingCards() {
                cardTimer = Metrics.globalRegistry()
                        .getOrCreate(Timer.builder("cardTimer") // <2>
                                             .description("Times card retrievals"));
            }

            @Override
            public void routing(HttpRules rules) {
                rules.get("/", this::getDefaultMessageHandler);
            }

            private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
                Timer.Sample timerSample = Timer.start(); // <3>
                sendResponse(response, "Here are some cards ...");
                response.whenSent(() -> timerSample.stop(cardTimer)); // <4>
            }

            private void sendResponse(ServerResponse response, String msg) {
                JsonObject returnObject = JSON.createObjectBuilder().add("message", msg).build();
                response.send(returnObject);
            }
        }
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        public class GreetingCards implements HttpService {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
            private final DistributionSummary cardSummary; // <1>

            GreetingCards() {
                cardSummary = Metrics.globalRegistry()
                        .getOrCreate(DistributionSummary.builder("cardDist")
                                             .description("random card distribution")); // <2>
            }

            @Override
            public void routing(HttpRules rules) {
                rules.get("/", this::getDefaultMessageHandler);
            }

            private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
                Random r = new Random(); // <3>
                for (int i = 0; i < 1000; i++) {
                    cardSummary.record(1 + r.nextDouble());
                }
                sendResponse(response, "Here are some cards ...");
            }

            private void sendResponse(ServerResponse response, String msg) {
                JsonObject returnObject = JSON.createObjectBuilder().add("message", msg).build();
                response.send(returnObject);
            }
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        // tag::snippet_7[]
        public class GreetingCards implements HttpService {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

            GreetingCards() {
                Random r = new Random();
                Metrics.globalRegistry()
                        .getOrCreate(Gauge.builder("temperature",
                                                   () -> r.nextDouble(100.0))
                                             .description("Ambient temperature")); // <1>
            }

            @Override
            public void routing(HttpRules rules) {
                rules.get("/", this::getDefaultMessageHandler);
            }

            private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
                sendResponse(response, "Here are some cards ...");
            }

            private void sendResponse(ServerResponse response, String msg) {
                JsonObject returnObject = JSON.createObjectBuilder().add("message", msg).build();
                response.send(returnObject);
            }
        }
        // end::snippet_7[]
    }
}
