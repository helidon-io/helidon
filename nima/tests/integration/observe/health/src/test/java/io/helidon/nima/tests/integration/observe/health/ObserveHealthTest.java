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

package io.helidon.nima.tests.integration.observe.health;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.nima.observe.ObserveFeature;
import io.helidon.nima.observe.health.HealthFeature;
import io.helidon.nima.observe.health.HealthObserveProvider;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.health.HealthCheckResponse.Status.DOWN;
import static io.helidon.health.HealthCheckResponse.Status.UP;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ObserveHealthTest {
    private static MyHealthCheck healthCheck;

    private final Http1Client httpClient;

    ObserveHealthTest(Http1Client httpClient) {
        this.httpClient = httpClient;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        healthCheck = new MyHealthCheck();
        routing.addFeature(ObserveFeature.create(HealthObserveProvider.create(HealthFeature.create(healthCheck))));
    }

    @BeforeEach
    void resetStatus() {
        healthCheck.status(UP);
    }

    @Test
    void testHealthAll() {
        try (Http1ClientResponse response = httpClient.get("/observe/health")
                .request()) {

            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health")
                .request()) {
            assertThat(response.status(), is(Http.Status.SERVICE_UNAVAILABLE_503));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }
    }

    @Test
    void testHealthLive() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/live")
                .request()) {

            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health/live")
                .request()) {

            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }
    }

    @Test
    void testHealthStart() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/started")
                .request()) {

            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health/started")
                .request()) {

            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }
    }

    @Test
    void testHealthReady() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready")
                .request()) {

            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready")
                .request()) {
            assertThat(response.status(), is(Http.Status.SERVICE_UNAVAILABLE_503));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }
    }

    @Test
    void testHealthReadyOne() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready/mine1")
                .request()) {

            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready/mine1")
                .request()) {
            assertThat(response.status(), is(Http.Status.SERVICE_UNAVAILABLE_503));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }
    }

    @Test
    void testHealthRootOne() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/check/mine1")
                .request()) {

            assertThat(response.status(), is(Http.Status.NO_CONTENT_204));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready/mine1")
                .request()) {
            assertThat(response.status(), is(Http.Status.SERVICE_UNAVAILABLE_503));
            assertThat(response.headers(), hasHeader(HeaderValues.CONTENT_LENGTH_ZERO));
        }
    }

    @Test
    void testHealthRootOneNotFound() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/check/mine2")
                .request()) {

            assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
        }
    }

    @Test
    void testHealthRootReadyNotFound() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready/mine2")
                .request()) {

            assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
        }
    }

    @Test
    void testHealthLiveOneNotFound() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/live/mine2")
                .request()) {

            assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
        }
    }

    @Test
    void testHealthStartOneNotFound() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/startup/mine2")
                .request()) {

            assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
        }
    }
}
