/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.examples.nima.tracing;

import io.helidon.common.LogConfig;
import io.helidon.nima.http2.webserver.Http2Route;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http1.Http1Route;
import io.helidon.nima.webserver.tracing.TracingSupport;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import static io.helidon.common.http.Http.Method.GET;

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

        Tracer tracer = TracerBuilder.create("nima")
                .build();

        WebServer.builder()
                .port(8080)
                .host("127.0.0.1")
                .routing(router -> router
                        .update(TracingSupport.create(tracer)::register)
                        .route(Http1Route.route(GET, "/versionspecific", new TracedHandler(tracer, "HTTP/1.1 route")))
                        .route(Http2Route.route(GET, "/versionspecific", new TracedHandler(tracer, "HTTP/2 route")))
                )
                .build()
                .start();
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
