/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh5328;

import java.time.Duration;

import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.tracing.WebClientTracing;
import io.helidon.webserver.WebServer;

import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

public class OffendingExtension implements Extension {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private String response;

    private void configure(@Observes @RuntimeStart @Priority(PLATFORM_BEFORE + 2) Config config) {
        // start a local server and connect to it (random port)
        WebServer ws = WebServer.builder()
                .routing(it -> it.get("/", (req, res) -> res.send("Hello")))
                .build()
                .start();

        Http1Client wc = Http1Client.builder()
                // explicitly add tracing, so we need tracer at this moment
                .addService(WebClientTracing.create())
                .baseUri("http://localhost:" + ws.port())
                .build();

        response = wc.get().requestEntity(String.class);
    }

    String response() {
        return response;
    }
}
