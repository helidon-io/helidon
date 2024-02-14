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
package io.helidon.docs.se.metrics;

import io.helidon.config.Config;
// tag::snippet_2[]
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Metrics;
// end::snippet_2[]
// tag::snippet_4[]
import io.helidon.metrics.prometheus.PrometheusSupport;
// end::snippet_4[]
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.metrics.MetricsObserver;

@SuppressWarnings("ALL")
class MetricsSnippets {

    // stub
    class Main {
        static void routing(HttpRouting.Builder routing) {
        }
    }

    void snippet_1(Config config) {
        // tag::snippet_1[]
        ObserveFeature observe = ObserveFeature.builder()
                .config(config.get("server.features.observe"))
                .addObserver(MetricsObserver.create())
                .build();

        WebServer server = WebServer.builder()
                .config(Config.global().get("server"))
                .featuresDiscoverServices(false)
                .addFeature(observe)
                .routing(Main::routing)
                .build()
                .start();
        // end::snippet_1[]
    }

    // tag::snippet_3[]
    public class GreetService implements HttpService {

        private final Counter accessCtr = Metrics.globalRegistry() // <1>
                .getOrCreate(Counter.builder("accessctr")); // <2>

        @Override
        public void routing(HttpRules rules) {
            rules
                    .any(this::countAccess) // <3>
                    .get("/", this::getDefaultMessageHandler)
                    .get("/{name}", this::getMessageHandler)
                    .put("/greeting", this::updateGreetingHandler);

        }

        void countAccess(ServerRequest request,
                         ServerResponse response) {

            accessCtr.increment(); // <4>
            response.next();
        }

        void getDefaultMessageHandler(ServerRequest request,
                                      ServerResponse response) {
            // ...
        }

        void getMessageHandler(ServerRequest request,
                               ServerResponse response) {
            // ...
        }

        void updateGreetingHandler(ServerRequest request,
                                   ServerResponse response) {
            // ...
        }
    }
    // end::snippet_3[]

    // stub
    class MyService implements HttpService {

        @Override
        public void routing(HttpRules rules) {
        }
    }

    void snippet_5(HttpRouting.Builder routing) {
        // tag::snippet_5[]
        routing
                .addFeature(PrometheusSupport.create())
                .register("/myapp", new MyService());
        // end::snippet_5[]
    }
}
