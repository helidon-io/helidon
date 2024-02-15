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

import io.helidon.common.media.type.MediaTypes;
import io.helidon.integrations.micrometer.MeterRegistryFactory;
import io.helidon.integrations.micrometer.MeterRegistryFactory.BuiltInRegistryType;
import io.helidon.integrations.micrometer.MicrometerFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

@SuppressWarnings("ALL")
class MicrometerSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        MicrometerFeature micrometerFeature = MicrometerFeature.create(); // <1>

        HttpRouting.builder()
                .addFeature(micrometerFeature) // <2>
                .register("/myapp", new MyService(micrometerFeature.registry())) // <3>
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

    void snippet_3(PrometheusConfig myPrometheusConfig) {
        // tag::snippet_3[]
        MeterRegistryFactory meterRegistryFactory = MeterRegistryFactory.builder()
                .enrollBuiltInRegistry(BuiltInRegistryType.PROMETHEUS, myPrometheusConfig) // <1>
                .build();
        MicrometerFeature micrometerFeature = MicrometerFeature.builder()
                .meterRegistryFactorySupplier(meterRegistryFactory)
                .build();
        // end::snippet_3[]
    }

    void snippet_4(PrometheusConfig myPrometheusConfig) {
        // tag::snippet_4[]
        PrometheusMeterRegistry myRegistry = new PrometheusMeterRegistry(myPrometheusConfig); // <1>
        MeterRegistryFactory meterRegistryFactory = MeterRegistryFactory.builder()
                .enrollRegistry(myRegistry, request -> {
                    return request // <2>
                            .headers()
                            .bestAccepted(MediaTypes.TEXT_PLAIN)
                            .map(mt -> (req, resp) -> resp.send(myRegistry.scrape())); // <3>
                })
                .build();
        MicrometerFeature micrometerFeature = MicrometerFeature.builder()
                .meterRegistryFactorySupplier(meterRegistryFactory)
                .build();
        // end::snippet_4[]
    }
}
