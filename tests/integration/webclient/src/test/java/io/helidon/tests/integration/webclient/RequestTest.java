/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.webclient;

import java.net.URI;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClientException;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests of basic requests.
 */
public class RequestTest extends TestParent {

    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject JSON_NEW_GREETING;
    private static final JsonObject JSON_OLD_GREETING;

    static {
        JSON_NEW_GREETING = JSON_BUILDER.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
        JSON_OLD_GREETING = JSON_BUILDER.createObjectBuilder()
                .add("greeting", CONFIG.get("app.greeting").asString().orElse("Hello"))
                .build();
    }

    @Test
    public void testHelloWorld() throws ExecutionException, InterruptedException {
        webClient.get()
                .request(JsonObject.class)
                .thenAccept(jsonObject -> Assertions.assertEquals("Hello World!", jsonObject.getString("message")))
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testIncorrect() throws ExecutionException, InterruptedException {
        webClient.get()
                .path("/incorrect")
                .request()
                .thenAccept(response -> {
                    if (response.status() != Http.Status.NOT_FOUND_404) {
                        fail("This request should be 404!");
                    }
                    response.close();
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testFollowRedirect() throws ExecutionException, InterruptedException {
        webClient.get()
                .path("/redirect")
                .request(JsonObject.class)
                .thenAccept(jsonObject -> Assertions.assertEquals("Hello World!", jsonObject.getString("message")))
                .toCompletableFuture()
                .get();

        WebClientResponse response = webClient.get()
                .path("/redirect")
                .followRedirects(false)
                .request()
                .toCompletableFuture()
                .get();
        assertThat(response.status(), is(Http.Status.MOVED_PERMANENTLY_301));
    }

    @Test
    public void testFollowRedirectPath() {
        JsonObject jsonObject = webClient.get()
                .path("/redirectPath")
                .request(JsonObject.class)
                .await();
        Assertions.assertEquals("Hello World!", jsonObject.getString("message"));
    }

    @Test
    public void testFollowRedirectInfinite() {
        try {
            webClient.get()
                    .path("/redirect/infinite")
                    .request(JsonObject.class)
                    .thenAccept(jsonObject -> fail("This should have failed!"))
                    .toCompletableFuture()
                    .get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof WebClientException) {
                WebClientException clientException = (WebClientException) e.getCause();
                assertThat(clientException.getMessage(), startsWith("Max number of redirects extended! (5)"));
            } else {
                fail(e);
            }
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void testPut() throws Exception {
        webClient.put()
                .path("/greeting")
                .submit(JSON_NEW_GREETING)
                .thenAccept(response -> Assertions.assertEquals(204, response.status().code()))
                .thenCompose(nothing -> webClient.get()
                        .request(JsonObject.class))
                .thenAccept(jsonObject -> Assertions.assertEquals("Hola World!", jsonObject.getString("message")))
                .thenCompose(nothing -> webClient.put()
                        .path("/greeting")
                        .submit(JSON_OLD_GREETING))
                .thenAccept(response -> assertThat(response.status().code(), is(204)))
                .exceptionally(throwable -> {
                    fail(throwable);
                    return null;
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testEntityNotHandled() {
        try {
            webClient.get()
                    .path("/incorrect")
                    .request(JsonObject.class)
                    .toCompletableFuture()
                    .get();
        } catch (ExecutionException e) {
            WebClientException ce = (WebClientException) e.getCause();
            assertThat(ce.getMessage(), startsWith("Request failed with code 404"));
            return;
        } catch (Exception e) {
            fail(e);
        }
        fail("This request entity process should have failed.");
    }

    @Test
    public void testResponseLastUri() throws Exception {
        URI defaultTemplate = URI.create("http://localhost:" + Main.serverPort + "/greet");
        URI redirectTemplate = URI.create("http://localhost:" + Main.serverPort + "/greet/redirect");

        WebClientResponse response = webClient.get()
                .path("/redirect")
                .request()
                .toCompletableFuture()
                .get();

        assertThat(response.lastEndpointURI(), is(defaultTemplate));
        response.close();

        response = webClient.get()
                .path("/redirect")
                .followRedirects(false )
                .request()
                .toCompletableFuture()
                .get();

        assertThat(response.lastEndpointURI(), is(redirectTemplate));
        response.close();

        response = webClient.get()
                .request()
                .toCompletableFuture()
                .get();

        assertThat(response.lastEndpointURI(), is(defaultTemplate));
        response.close();
    }

    @Test
    public void reuseRequestBuilder() {
        WebClientRequestBuilder requestBuilder = webClient.get();
        JsonObject response = requestBuilder
                .request(JsonObject.class)
                .await();
        assertThat(response, notNullValue());
        assertThat(response.getString("message"), is("Hello World!"));
        response = requestBuilder
                .request(JsonObject.class)
                .await();
        assertThat(response, notNullValue());
        assertThat(response.getString("message"), is("Hello World!"));
    }

}
