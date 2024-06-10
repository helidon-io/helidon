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
package io.helidon.docs.se;

import io.helidon.config.Config;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.config.ComponentTracingConfig;
import io.helidon.tracing.config.SpanLogTracingConfig;
import io.helidon.tracing.config.SpanTracingConfig;
import io.helidon.tracing.config.TracingConfig;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.tracing.WebClientTracing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.tracing.TracingObserver;

@SuppressWarnings("ALL")
class TracingSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        Tracer tracer = TracerBuilder.create("helidon") // <1>
                .build();

        WebServer.builder()
                .addFeature(ObserveFeature.builder()
                                    .addObserver(TracingObserver.create(tracer)) // <2>
                                    .build())
                .build()
                .start();
        // end::snippet_1[]
    }

    void snippet_2(Tracer tracer) {
        // tag::snippet_2[]
        Span span = tracer.spanBuilder("name") // <1>
                .tag("key", "value")
                .start();

        try { // <2>
            // do some work
            span.end();
        } catch (Throwable t) { // <3>
            span.end(t);
        }
        // end::snippet_2[]
    }

    void snippet_3() {
        // tag::snippet_3[]
        TracingConfig.builder()
                .addComponent(ComponentTracingConfig.builder("web-server")
                                      .addSpan(SpanTracingConfig.builder("HTTP Request")
                                                       .addSpanLog(SpanLogTracingConfig.builder("content-write")
                                                                           .enabled(false)
                                                                           .build())
                                                       .build())
                                      .build())
                .build();
        // end::snippet_3[]
    }

    void snippet_4(Config config, WebServerConfig.Builder server) {
        // tag::snippet_4[]
        Tracer tracer = TracerBuilder.create(config.get("tracing")).build(); // <1>
        server.addFeature(ObserveFeature.builder()
                                  .addObserver(TracingObserver.create(tracer)) // <2>
                                  .build());
        // end::snippet_4[]
    }

    void snippet_5(String uri) {
        // tag::snippet_5[]
        WebClient client = WebClient.builder()
                .addService(WebClientTracing.create())
                .build();

        String response = client.get()
                .uri(uri)
                .requestEntity(String.class);
        // end::snippet_5[]
    }
}
