/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

import io.helidon.common.http.Headers;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.WebClientResponseHeaders;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.contains;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Make sure the default CorsSupport behavior is correct (basically, wildcarded sharing).
 */
public class TestDefaultCorsSupport {

    @Test
    void testGetWithoutCors() throws ExecutionException, InterruptedException {
        WebServer server = null;
        WebClient client;
        try {
            server = WebServer.create(prepRouting(false)).start().toCompletableFuture().get();
            client = WebClient.builder()
                    .baseUri("http://localhost:" + server.port())
                    .get();

            WebClientResponse response = client.get()
                    .path("/greet")
                    .submit()
                    .toCompletableFuture()
                    .get();

            String greeting= response.content().as(String.class).toCompletableFuture().get();
            assertThat(greeting, is("Hello World!"));
        } finally {
            if (server != null) {
                server.shutdown();
            }
        }
    }

    @Test
    void testOptionsWithCors() throws ExecutionException, InterruptedException {
        WebServer server = null;
        WebClient client;
        try {
            server = WebServer.create(prepRouting(true)).start().toCompletableFuture().get();
            client = WebClient.builder()
                    .baseUri("http://localhost:" + server.port())
                    .get();

            WebClientRequestBuilder reqBuilder = client.method("OPTIONS")
                    .path("/greet");

            Headers h = reqBuilder.headers();
            h.add("Origin", "http://foo.com");
            h.add("Host", "bar.com");
            WebClientResponse response = reqBuilder
                    .submit()
                    .toCompletableFuture()
                    .get();

            WebClientResponseHeaders headers = response.headers();
            List<String> allowOrigins = headers.values(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
            assertThat(allowOrigins, contains("*"));
        } finally {
            if (server != null) {
                server.shutdown();
            }
        }
    }

    @Test
    void testOptionsWithoutCors() throws ExecutionException, InterruptedException {
        WebServer server = null;
        WebClient client;
        try {
            server = WebServer.create(prepRouting(false)).start().toCompletableFuture().get();
            client = WebClient.builder()
                    .baseUri("http://localhost:" + server.port())
                    .get();

            WebClientRequestBuilder reqBuilder = client.method("OPTIONS")
                    .path("/greet");

            Headers h = reqBuilder.headers();
            h.add("Origin", "http://foo.com");
            h.add("Host", "bar.com");
            WebClientResponse response = reqBuilder
                    .submit()
                    .toCompletableFuture()
                    .get();

            WebClientResponseHeaders headers = response.headers();
            List<String> allowOrigins = headers.values(CrossOriginConfig.ACCESS_CONTROL_ALLOW_ORIGIN);
            assertThat(allowOrigins.size(), is(0));
        } finally {
            if (server != null) {
                server.shutdown();
            }
        }
    }

    static Routing.Builder prepRouting(boolean withCors) {
        Routing.Builder builder = Routing.builder();
        if (withCors) {
            builder.any(CorsSupport.create()); // Here is where we insert the default CorsSupport.
        }
        return builder
                .get("/greet", (req, res) -> res.send("Hello World!"))
                .options("/greet", (req, res) -> res.status(200).send());
    }
}
