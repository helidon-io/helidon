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
 */

package io.helidon.webserver.examples.faulttolerance;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MainTest {
    private static WebServer server;
    private static WebClient client;

    @BeforeAll
    static void initClass() throws ExecutionException, InterruptedException {
        server = Main.startServer(0)
                .await(10, TimeUnit.SECONDS);

        client = WebClient.builder()
                .baseUri("http://localhost:" + server.port() + "/ft")
                .build();
    }

    @AfterAll
    static void destroyClass() {
        server.shutdown()
                .await(5, TimeUnit.SECONDS);
    }

    @Test
    void testAsync() {
        String response = client.get()
                .path("/async")
                .request(String.class)
                .await(5, TimeUnit.SECONDS);

        assertThat(response, is("blocked for 100 millis"));
    }

    @Test
    void testBulkhead() throws InterruptedException {
        // bulkhead is configured for limit of 1 and queue of 1, so third
        // request should fail
        client.get()
                .path("/bulkhead/100000")
                .request()
        .thenRun(() -> {});

        client.get()
                .path("/bulkhead/100000")
                .request()
                .thenRun(() -> {});

        // I want to make sure the above is connected
        Thread.sleep(100);

        WebClientResponse third = client.get()
                .path("/bulkhead/10000")
                .request()
                .await(1, TimeUnit.SECONDS);

        // registered an error handler in Main
        assertThat(third.status(), is(Http.Status.SERVICE_UNAVAILABLE_503));
        assertThat(third.content().as(String.class).await(1, TimeUnit.SECONDS), is("bulkhead"));
    }

    @Test
    void testCircuitBreaker() {
        String response = client.get()
                .path("/circuitBreaker/true")
                .request(String.class)
                .await(1, TimeUnit.SECONDS);

        assertThat(response, is("blocked for 100 millis"));

        // error ratio is 20% within 10 request
        client.get()
                .path("/circuitBreaker/false")
                .request()
                .await(1, TimeUnit.SECONDS);

        // should work after first
        response = client.get()
                .path("/circuitBreaker/true")
                .request(String.class)
                .await(1, TimeUnit.SECONDS);

        assertThat(response, is("blocked for 100 millis"));

        // should open after second
        client.get()
                .path("/circuitBreaker/false")
                .request()
                .await(1, TimeUnit.SECONDS);

        WebClientResponse clientResponse = client.get()
                .path("/circuitBreaker/true")
                .request()
                .await(1, TimeUnit.SECONDS);
        response = clientResponse.content().as(String.class).await(1, TimeUnit.SECONDS);

        // registered an error handler in Main
        assertThat(clientResponse.status(), is(Http.Status.SERVICE_UNAVAILABLE_503));
        assertThat(response, is("circuit breaker"));
    }

    @Test
    void testFallback() {
        String response = client.get()
                .path("/fallback/true")
                .request(String.class)
                .await(1, TimeUnit.SECONDS);

        assertThat(response, is("blocked for 100 millis"));

        response = client.get()
                .path("/fallback/false")
                .request(String.class)
                .await(1, TimeUnit.SECONDS);

        assertThat(response, is("Failed back because of reactive failure"));
    }

    @Test
    void testRetry() {
        String response = client.get()
                .path("/retry/1")
                .request(String.class)
                .await(1, TimeUnit.SECONDS);

        assertThat(response, is("calls/failures: 1/0"));

        response = client.get()
                .path("/retry/2")
                .request(String.class)
                .await(1, TimeUnit.SECONDS);

        assertThat(response, is("calls/failures: 2/1"));

        response = client.get()
                .path("/retry/3")
                .request(String.class)
                .await(1, TimeUnit.SECONDS);

        assertThat(response, is("calls/failures: 3/2"));

        WebClientResponse clientResponse = client.get()
                .path("/retry/4")
                .request()
                .await(1, TimeUnit.SECONDS);

        response = clientResponse.content().as(String.class).await(1, TimeUnit.SECONDS);
        // no error handler specified
        assertThat(clientResponse.status(), is(Http.Status.INTERNAL_SERVER_ERROR_500));
        assertThat(response, is("java.lang.RuntimeException: reactive failure"));
    }

    @Test
    void testTimeout() {
        String response = client.get()
                .path("/timeout/50")
                .request(String.class)
                .await(1, TimeUnit.SECONDS);

        assertThat(response, is("Slept for 50 ms"));

        WebClientResponse clientResponse = client.get()
                .path("/timeout/105")
                .request()
                .await(1, TimeUnit.SECONDS);

        response = clientResponse.content().as(String.class).await(1, TimeUnit.SECONDS);
        // error handler specified in Main
        assertThat(clientResponse.status(), is(Http.Status.REQUEST_TIMEOUT_408));
        assertThat(response, is("timeout"));
    }
}