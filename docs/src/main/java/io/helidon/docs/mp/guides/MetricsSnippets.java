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
package io.helidon.docs.mp.guides;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.Timed;

@SuppressWarnings("ALL")
class MetricsSnippets {

    class Snippet1 {

        // tag::snippet_1[]
        @Path("/cards") //<1>
        @RequestScoped // <2>
        public class GreetingCards {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

            @GET
            @Produces(MediaType.APPLICATION_JSON)
            @Counted(name = "any-card")  // <3>
            public JsonObject anyCard() throws InterruptedException {
                return createResponse("Here are some random cards ...");
            }

            private JsonObject createResponse(String msg) {
                return JSON.createObjectBuilder().add("message", msg).build();
            }
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @Path("/cards")
        @RequestScoped
        public class GreetingCards {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

            @GET
            @Produces(MediaType.APPLICATION_JSON)
            @Counted(name = "cardCount", absolute = true) //<1>
            @Timed(name = "cardTimer", absolute = true, unit = MetricUnits.MILLISECONDS) //<2>
            public JsonObject anyCard() {
                return createResponse("Here are some random cards ...");
            }

            private JsonObject createResponse(String msg) {
                return JSON.createObjectBuilder().add("message", msg).build();
            }
        }

        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @Path("/cards")
        @RequestScoped
        public class GreetingCards {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

            @GET
            @Produces(MediaType.APPLICATION_JSON)
            @Counted(name = "anyCard", absolute = true)
            public JsonObject anyCard() throws InterruptedException {
                return createResponse("Here are some cards ...");
            }

            @GET
            @Path("/birthday")
            @Produces(MediaType.APPLICATION_JSON)
            @Counted(name = "specialEventCard", absolute = true)  // <1>
            public JsonObject birthdayCard() throws InterruptedException {
                return createResponse("Here are some birthday cards ...");
            }

            @GET
            @Path("/wedding")
            @Produces(MediaType.APPLICATION_JSON)
            @Counted(name = "specialEventCard", absolute = true)  // <2>
            public JsonObject weddingCard() throws InterruptedException {
                return createResponse("Here are some wedding cards ...");
            }

            private JsonObject createResponse(String msg) {
                return JSON.createObjectBuilder().add("message", msg).build();
            }
        }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @Path("/cards")
        @RequestScoped
        @Counted(name = "totalCards") // <1>
        public class GreetingCards {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

            @GET
            @Produces(MediaType.APPLICATION_JSON)
            @Counted(absolute = true) // <2>
            public JsonObject anyCard() throws InterruptedException {
                return createResponse("Here are some random cards ...");
            }

            @Path("/birthday")
            @GET
            @Produces(MediaType.APPLICATION_JSON)
            @Counted(absolute = true) // <3>
            public JsonObject birthdayCard() throws InterruptedException {
                return createResponse("Here are some birthday cards ...");
            }

            private JsonObject createResponse(String msg) {
                return JSON.createObjectBuilder().add("message", msg).build();
            }
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @Path("/cards")
        @RequestScoped
        @Counted(name = "totalCards")
        public class GreetingCards {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

            @Inject
            @Metric(name = "cacheHits", absolute = true) // <1>
            private Counter cacheHits;

            @GET
            @Produces(MediaType.APPLICATION_JSON)
            @Counted(absolute = true)
            public JsonObject anyCard() throws InterruptedException {
                updateStats(); // <2>
                return createResponse("Here are some random cards ...");
            }

            @Path("/birthday")
            @GET
            @Produces(MediaType.APPLICATION_JSON)
            @Counted(absolute = true)
            public JsonObject birthdayCard() throws InterruptedException {
                updateStats();  // <3>
                return createResponse("Here are some birthday cards ...");
            }

            private JsonObject createResponse(String msg) {
                return JSON.createObjectBuilder().add("message", msg).build();
            }

            private void updateStats() {
                if (new Random().nextInt(3) == 1) {
                    cacheHits.inc(); // <4>
                }
            }
        }
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        @ApplicationScoped // <1>
        public class GreetingCardsAppMetrics {

            private AtomicLong startTime = new AtomicLong(0); // <2>

            public void onStartUp(@Observes @Initialized(ApplicationScoped.class) Object init) {
                startTime = new AtomicLong(System.currentTimeMillis()); // <3>
            }

            @Gauge(unit = "TimeSeconds")
            public long appUpTimeSeconds() {
                return Duration.ofMillis(System.currentTimeMillis() - startTime.get()).getSeconds();  // <4>
            }
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        // tag::snippet_7[]
        @Path("/cards")
        @RequestScoped
        public class GreetingCards {

            private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

            @GET
            @Produces(MediaType.APPLICATION_JSON)
            @Counted(name = "cardCount", absolute = true)
            public JsonObject anyCard() throws InterruptedException {
                return createResponse("Here are some random cards ...");
            }

            private JsonObject createResponse(String msg) {
                return JSON.createObjectBuilder().add("message", msg).build();
            }
        }
        // end::snippet_7[]
    }

}
