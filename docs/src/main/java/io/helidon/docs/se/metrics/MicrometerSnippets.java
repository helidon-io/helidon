/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

@SuppressWarnings("ALL")
class MicrometerSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        MeterRegistry registry = Metrics.globalRegistry; // <1>
        MyService myService = new MyService(registry); // <2>

        WebServer.builder()
                .routing(r -> r.register("/myapp", myService)) // <3>
                .build();
        // end::snippet_1[]
    }

    // tag::snippet_2[]
    class MyService implements HttpService {

        final Counter requestCounter;

        MyService(MeterRegistry registry) {
            requestCounter = registry.counter("allRequests"); // <1>
        }

        @Override
        public void routing(HttpRules rules) {
            rules
                    .any(this::countRequests) // <2>
                    .get("/", this::myGet);
        }

        void countRequests(ServerRequest request, ServerResponse response) {
            requestCounter.increment(); // <3>
            response.next();
        }

        void myGet(ServerRequest request, ServerResponse response) {
            response.send("OK");
        }

    }
    // end::snippet_2[]
}
