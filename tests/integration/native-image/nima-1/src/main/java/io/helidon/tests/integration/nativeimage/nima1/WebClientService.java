/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.nativeimage.nima1;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.nima.webclient.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import jakarta.json.JsonValue;

class WebClientService implements HttpService {
    private static final Duration TRACE_TIMEOUT = Duration.ofSeconds(15);
    private static final Logger LOGGER = Logger.getLogger(WebClientService.class.getName());
    private final Http1Client client;
    private final MockZipkinService zipkinService;
    private final String context;

    public WebClientService(Config config, MockZipkinService zipkinService) {
        this.zipkinService = zipkinService;
        this.context = "http://localhost:" + config.get("port").asInt().orElse(7076);
        client = WebClient.builder()
                .baseUri(context)
                // TODO: improvement
                // .addHeader(Http.Header.ACCEPT, MediaTypes.APPLICATION_JSON)
                // .config(config.get("client"))
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
        response.headers().add(Http.Header.LOCATION, context + "/wc/endpoint");
        response.status(Http.Status.MOVED_PERMANENTLY_301).send();
    }

    private void redirectInfinite(ServerRequest serverRequest, ServerResponse response) {
        response.headers().add(Http.Header.LOCATION, context + "/wc/redirect/infinite");
        response.status(Http.Status.MOVED_PERMANENTLY_301).send();
    }

    private void getEndpoint(final ServerRequest request, final ServerResponse response) {
        response.send(new Animal(Animal.TYPE.BIRD, "Frank"));
    }

    private void getTest(final ServerRequest request, final ServerResponse response) {

        try {
            testTracedGet();
            //testFollowRedirect();
            //testFollowRedirectInfinite();

            response.send("ALL TESTS PASSED!\n");
        } catch (Exception e) {
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500);
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            response.send("Failed to process request: " + writer);
        }
    }

    public void testTracedGet() {
        Single<JsonValue> nextTrace = zipkinService.next();
        Animal animal = client.get()
                .path("/wc/endpoint")
                .request(Animal.class);

        assertTrue(animal, a -> "Frank".equals(a.getName()));
        //Wait for trace arrival to MockZipkin
        nextTrace.await(TRACE_TIMEOUT);
    }

    public void testFollowRedirect() {
        /*
        client.get()
                .path("/wc/redirect")
                // TODO improvement
                .followRedirects(true)
                .context(ctx)
                .request(Animal.class)
                .thenAccept(animal -> assertTrue(animal, a -> "Frank".equals(a.getName())))
                .await(15, TimeUnit.SECONDS);

        WebClientResponse response = client.get()
                .path("/wc/redirect")
                .followRedirects(false)
                .context(ctx)
                .request()
                .await(15, TimeUnit.SECONDS);
        assertEquals(response.status(), Http.Status.MOVED_PERMANENTLY_301);
        */
    }

    public void testFollowRedirectInfinite() {
        // TODO improvement
        /*
        try {
            client.get()
                    .path("/wc/redirect/infinite")
                    .context(ctx)
                    .request(Animal.class)
                    .thenAccept(a -> fail("This should have failed!"))
                    .await(15, TimeUnit.SECONDS);
            fail("This should have failed!");
        } catch (Exception e) {
            if (e.getCause() instanceof WebClientException) {
                WebClientException clientException = (WebClientException) e.getCause();
                assertTrue(clientException.getMessage(), m -> m.startsWith("Max number of redirects extended! (5)"));
            } else {
                fail(e);
            }
        }
         */
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
        LOGGER.severe(e.getMessage());
        throw new RuntimeException("Assertion error!", e);
    }
}
