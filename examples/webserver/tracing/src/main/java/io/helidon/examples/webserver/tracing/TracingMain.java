/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.tracing;

import io.helidon.logging.common.LogConfig;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.tracing.WebClientTracing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.tracing.TracingObserver;

import static io.helidon.http.Method.GET;

/**
 * Tracing example.
 */
public class TracingMain {
    private TracingMain() {
    }

    /**
     * Main method.
     * @param args ignored
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Tracer tracer = TracerBuilder.create("helidon")
                .build();

        WebServer.builder()
                .port(8080)
                .host("127.0.0.1")
                .addFeature(ObserveFeature.builder()
                                    .addObserver(TracingObserver.create(tracer))
                                    .build())
                .routing(router -> router
                        .route(Http1Route.route(GET, "/versionspecific", new TracedHandler(tracer, "HTTP/1.1 route")))
                        .route(Http2Route.route(GET, "/versionspecific", new TracedHandler(tracer, "HTTP/2 route")))
                        .get("/client", new ClientHandler(tracer)))
                .build()
                .start();
    }

    private static class ClientHandler implements Handler {
        private final WebClient client;

        private ClientHandler(Tracer tracer) {
            this.client = WebClient.builder()
                    .baseUri("http://localhost:8080/versionspecific")
                    .servicesDiscoverServices(false)
                    .addService(WebClientTracing.create(tracer))
                    .build();
        }

        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.send(client.get()
                             .requestEntity(String.class));
        }
    }

    private static class TracedHandler implements Handler {
        private final Tracer tracer;
        private final String message;

        private TracedHandler(Tracer tracer, String message) {
            this.tracer = tracer;
            this.message = message;
        }

        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            Span span = tracer.spanBuilder("custom-span")
                    .start();
            try {
                span.addEvent("my nice log");
                res.send(message);
            } finally {
                span.end();
            }
        }
    }
}
