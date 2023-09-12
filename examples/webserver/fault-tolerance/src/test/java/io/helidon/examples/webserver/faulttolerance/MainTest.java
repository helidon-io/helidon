/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.faulttolerance;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RoutingTest
class MainTest {

    private final DirectClient client;

    MainTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void setup(HttpRouting.Builder routing) {
        Main.routing(routing);
    }

    @Test
    void testAsync() {
        try (Http1ClientResponse response = client.get("/ft/async").request()) {
            assertThat(response.as(String.class), is("blocked for 100 millis"));
        }
    }

    @Test
    void testBulkhead() throws InterruptedException {
        // bulkhead is configured for limit of 1 and queue of 1, so third
        // request should fail

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> client.get("/ft/bulkhead/10000").request().close());
        executor.submit(() -> client.get("/ft/bulkhead/10000").request().close());

        // I want to make sure the above is connected
        Thread.sleep(300);

        try (Http1ClientResponse response = client.get("/ft/bulkhead/10000").request()) {
            // registered an error handler in Main
            assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
            assertThat(response.as(String.class), is("bulkhead"));
        } finally {
            executor.close();
        }
    }

    @Test
    void testCircuitBreaker() {
        try (Http1ClientResponse response = client.get("/ft/circuitBreaker/true").request()) {
            assertThat(response.as(String.class), is("blocked for 100 millis"));
        }

        // error ratio is 20% within 10 request
        // should work after first
        try (Http1ClientResponse ignored = client.get("/ft/circuitBreaker/false").request();
             Http1ClientResponse response = client.get("/ft/circuitBreaker/true").request()) {

            assertThat(response.as(String.class), is("blocked for 100 millis"));
        }

        // should open after second
        client.get("/ft/circuitBreaker/false").request().close();

        try (Http1ClientResponse response = client.get("/ft/circuitBreaker/true").request()) {

            // registered an error handler in Main
            assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
            assertThat(response.as(String.class), is("circuit breaker"));
        }
    }

    @Test
    void testFallback() {
        try (Http1ClientResponse response = client.get("/ft/fallback/true").request()) {
            assertThat(response.as(String.class), is("blocked for 100 millis"));
        }

        try (Http1ClientResponse response = client.get("/ft/fallback/false").request()) {
            assertThat(response.as(String.class), is("Failed back because of failure"));
        }
    }

    @Test
    void testRetry() {
        try (Http1ClientResponse response = client.get("/ft/retry/1").request()) {
            assertThat(response.as(String.class), is("calls/failures: 1/0"));
        }

        try (Http1ClientResponse response = client.get("/ft/retry/2").request()) {
            assertThat(response.as(String.class), is("calls/failures: 2/1"));
        }

        try (Http1ClientResponse response = client.get("/ft/retry/3").request()) {
            assertThat(response.as(String.class), is("calls/failures: 3/2"));
        }

        try (Http1ClientResponse response = client.get("/ft/retry/4").request()) {
            // no error handler specified
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(response.as(String.class), is("java.lang.RuntimeException: failure"));
        }
    }

    @Test
    void testTimeout() {
        try (Http1ClientResponse response = client.get("/ft/timeout/10").request()) {
            assertThat(response.as(String.class), is("Slept for 10 ms"));
        }

        try (Http1ClientResponse response = client.get("/ft/timeout/1000").request()) {
            // error handler specified in Main
            assertThat(response.status(), is(Status.REQUEST_TIMEOUT_408));
            assertThat(response.as(String.class), is("timeout"));
        }
    }
}