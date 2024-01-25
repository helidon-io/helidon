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
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.config.Config;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

@SuppressWarnings("ALL")
class HealthSnippets {

    // stub
    class Main {
        static void routing(HttpRouting.Builder routing) {
        }
    }

    // tag::snippet_1[]
    void snippet1(Config config) {
        AtomicLong serverStartTime = new AtomicLong();  // <1>

        HealthObserver healthObserver = HealthObserver.builder() // <2>
                .details(true) // <3>
                .addCheck(() -> HealthCheckResponse.builder() // <4>
                                  .status(System.currentTimeMillis() - serverStartTime.get() >= 8000)
                                  .detail("time", System.currentTimeMillis())
                                  .build(),
                          HealthCheckType.STARTUP,
                          "warmedUp")
                .build();

        ObserveFeature observe = ObserveFeature.builder()
                .config(config.get("server.features.observe")) // <5>
                .addObserver(healthObserver) // <6>
                .build();

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .addFeature(observe)            // <7>
                .routing(Main::routing)
                .build()
                .start();

        serverStartTime.set(System.currentTimeMillis()); // <8>

        // end::snippet_1[]
    }

    void snippet2() {
        // tag::snippet_2[]
        HealthObserver.builder()
                .useSystemServices(false)
                //...
                .build();
        // end::snippet_2[]
    }

    void snippet3(Config config) {
        // tag::snippet_3[]
        HealthObserver healthObserver = HealthObserver.builder()
                .endpoint("/myhealth") // <1>
                // ...
                .build();
        // end::snippet_3[]
    }

    void snippet4(Config config) {
        // tag::snippet_4[]
        HealthObserver healthObserver = HealthObserver.builder()
                // ...
                .config(config.get("server.features.observe.observers.health")) // <1>
                .build();
        // end::snippet_4[]
    }

    // tag::snippet_5[]
    private static AtomicLong readyTime = new AtomicLong(0);
    // end::snippet_5[]

    void snippet6(Config config) {
        // tag::snippet_6[]
        ObserveFeature observe = ObserveFeature.builder()
                .config(config.get("server.features.observe"))
                .addObserver(HealthObserver.builder()
                                     .useSystemServices(true) // <1>
                                     .addCheck(() -> HealthCheckResponse.builder()
                                             .status(readyTime.get() != 0)
                                             .detail("time", readyTime.get())
                                             .build(), HealthCheckType.READINESS) // <2>
                                     .addCheck(() -> HealthCheckResponse.builder()
                                             .status(readyTime.get() != 0
                                                     && Duration.ofMillis(System.currentTimeMillis()
                                                                          - readyTime.get())
                                                                .getSeconds() >= 3)
                                             .detail("time", readyTime.get())
                                             .build(), HealthCheckType.STARTUP) // <3>
                                     .addCheck(() -> HealthCheckResponse.builder()
                                             .status(HealthCheckResponse.Status.UP)
                                             .detail("time", System.currentTimeMillis())
                                             .build(), HealthCheckType.LIVENESS) // <4>
                                     .build())
                .build();
        // end::snippet_6[]
    }

}
