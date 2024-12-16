/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.packaging.se1;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.JsonValue;

class WebClientService implements HttpService {
    private static final Duration TRACE_TIMEOUT = Duration.ofSeconds(15);
    private static final System.Logger LOGGER = System.getLogger(WebClientService.class.getName());
    private final WebClient client;
    private final MockZipkinService zipkinService;
    private final String context;

    WebClientService(Config config, MockZipkinService zipkinService) {
        this.zipkinService = zipkinService;
        this.context = "http://localhost:" + config.get("server.port").asInt().orElse(7076);
        client = WebClient.builder()
                .baseUri(context)
                .addHeader(HeaderNames.ACCEPT, MediaTypes.APPLICATION_JSON.text())
                .config(config.get("client"))
                .build();
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/test", this::getTest)
                .get("/redirect", this::redirect)
                .get("/redirect/infinite", this::redirectInfinite)
                .get("/endpoint", this::getEndpoint);
    }

    private void redirect(ServerRequest request,
                          ServerResponse response) {
        response.headers().add(HeaderNames.LOCATION, context + "/wc/endpoint");
        response.status(Status.MOVED_PERMANENTLY_301).send();
    }

    private void redirectInfinite(ServerRequest serverRequest, ServerResponse response) {
        response.headers().add(HeaderNames.LOCATION, context + "/wc/redirect/infinite");
        response.status(Status.MOVED_PERMANENTLY_301).send();
    }

    private void getEndpoint(final ServerRequest request, final ServerResponse response) {
        response.send(new Animal(Animal.TYPE.BIRD, "Frank"));
    }

    private void getTest(final ServerRequest request, final ServerResponse response) {

        try {
            testTracedGet();
            testFollowRedirect();
            testFollowRedirectInfinite();

            response.send("ALL TESTS PASSED!\n");
        } catch (Exception e) {
            response.status(Status.INTERNAL_SERVER_ERROR_500);
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            response.send("Failed to process request: " + writer);
        }
    }

    public void testTracedGet() {
        Single<JsonValue> nextTrace = zipkinService.next();
        Animal animal = client.get()
                .path("/wc/endpoint")
                .requestEntity(Animal.class);

        assertTrue(animal, a -> "Frank".equals(a.getName()));
        //Wait for trace arrival to MockZipkin
        nextTrace.await(TRACE_TIMEOUT);
    }

    public void testFollowRedirect() {
        Animal animal = client.get()
                .path("/wc/redirect")
                .followRedirects(true)
                .requestEntity(Animal.class);

        assertTrue(animal, a -> "Frank".equals(a.getName()));

        try (HttpClientResponse response = client.get()
                .path("/wc/redirect")
                .followRedirects(false)
                .request()) {
            assertEquals(response.status(), Status.MOVED_PERMANENTLY_301);
        }
    }

    public void testFollowRedirectInfinite() {
        try {
            client.get()
                    .path("/wc/redirect/infinite")
                    .requestEntity(Animal.class);
            fail("This should have failed!");
        } catch (Exception e) {
            assertTrue(e.getMessage(), m -> m.startsWith("Maximum number of request redirections (5) reached."));
        }
    }

    private <T> void assertTrue(T value, Predicate<T> predicate) {
        if (!predicate.test(value)) {
            fail("for value: " + value);
        }
    }

    private void assertEquals(Object a, Object b) {
        if (!Objects.equals(a, b)) {
            fail("Expected " + a + " equals " + b);
        }
    }

    private void fail(String msg) {
        fail(new RuntimeException("Assertion error " + msg));
    }

    private void fail(Exception e) {
        LOGGER.log(Level.ERROR, e.getMessage());
        throw new RuntimeException("Assertion error!", e);
    }
}
