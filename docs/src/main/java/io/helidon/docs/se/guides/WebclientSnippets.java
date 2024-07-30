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
import io.helidon.http.Method;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.metrics.WebClientMetrics;
import io.helidon.webclient.spi.WebClientService;

import jakarta.json.Json;
import jakarta.json.JsonObject;

@SuppressWarnings("ALL")
class WebclientSnippets {

    // tag::snippet_1[]
    public class ClientExample {

        public static void main(String[] args) {

        }
    }
    // end::snippet_1[]

    void snippet_2() {
        // tag::snippet_2[]
        WebClient webClient = WebClient.builder()
                .baseUri("http://localhost:8080") // <1>
                .build();
        // end::snippet_2[]
    }

    void snippet_3(WebClient webClient) {
        // tag::snippet_3[]
        ClientResponseTyped<String> response = webClient.get() // <1>
                .path("/greet") // <2>
                .request(String.class); // <3>
        String entityString = response.entity(); // <4>
        System.out.println(entityString);
        // end::snippet_3[]
    }

    void snippet_4(WebClient webClient) {
        // tag::snippet_4[]
        ClientResponseTyped<JsonObject> response = webClient.get()
                .path("/greet/David")
                .request(JsonObject.class); // <1>
        String value = response.entity().getString("message"); // <2>
        System.out.println(value);
        // end::snippet_4[]
    }

    void snippet_5(WebClient webClient) {
        // tag::snippet_5[]
        JsonObject entity = Json.createObjectBuilder() // <1>
                .add("greeting", "Bonjour")
                .build();
        webClient.put() // <2>
                .path("/greet/greeting")
                .submit(entity); // <3>
        ClientResponseTyped<JsonObject> response = webClient.get() // <4>
                .path("/greet/David")
                .request(JsonObject.class);
        String entityString = response.entity().getString("message"); // <5>
        System.out.println(entityString);
        // end::snippet_5[]
    }

    void snippet_6_7_8() {
        // tag::snippet_6[]
        MeterRegistry METER_REGISTRY = Metrics.globalRegistry();

        String metricName = "counter.GET.localhost"; // <1>

        Counter counter = METER_REGISTRY.getOrCreate(Counter.builder(metricName)); // <2>
        System.out.println(metricName + ": " + counter.count());

        WebClientService clientServiceMetric = WebClientMetrics.counter()
                .methods(Method.GET)                // OPTIONAL
                .success(true)                      // OPTIONAL
                .errors(true)                       // OPTIONAL
                .description("Metric Description")  // OPTIONAL
                .nameFormat("counter.%1$s.%2$s") // <3>
                .build(); // <4>
        // end::snippet_6[]

        // tag::snippet_7[]
        WebClient webClient = WebClient.builder()
                .baseUri("http://localhost:8080")
                .addService(clientServiceMetric) // <1>
                .build();

        webClient.get().path("/greet").request(); // <2>
        // end::snippet_7[]

        // tag::snippet_8[]
        System.out.println(metricName + ": " + counter.count());
        // end::snippet_8[]
    }

    void snippet_9(String[] args) {
        // tag::snippet_9[]
        MeterRegistry METER_REGISTRY = Metrics.globalRegistry();

        String counterName = "counter.GET.localhost"; // <1>

        Counter counter = METER_REGISTRY.getOrCreate(Counter.builder(counterName)); // <2>
        System.out.println(counterName + ": " + counter.count());

        Config config = Config.create(); // <3>

        WebClient webClient = WebClient.builder()
                .baseUri("http://localhost:8080")
                .config(config.get("client")) // <4>
                .build();
        webClient.get().path("/greet").request(); // <5>
        System.out.println(counterName + ": " + counter.count()); // <6>
        // end::snippet_9[]
    }
}
