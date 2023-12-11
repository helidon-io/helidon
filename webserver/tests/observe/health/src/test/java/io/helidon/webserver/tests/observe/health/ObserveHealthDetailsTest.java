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

package io.helidon.webserver.tests.observe.health;

import java.io.IOException;

import io.helidon.health.HealthCheckResponse;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.health.HealthCheckResponse.Status.DOWN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@ServerTest
class ObserveHealthDetailsTest {
    private static MyHealthCheck healthCheck;

    private final Http1Client httpClient;

    ObserveHealthDetailsTest(Http1Client httpClient) {
        this.httpClient = httpClient;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder builder) {
        healthCheck = new MyHealthCheck();
        builder.featuresDiscoverServices(false)
                .addFeature(ObserveFeature.just(HealthObserver
                                                        .builder()
                                                        .addCheck(healthCheck)
                                                        .details(true)
                                                        .build()));
    }
    @BeforeEach
    void resetStatus() {
        healthCheck.status(HealthCheckResponse.Status.UP);
    }

    @Test
    void testHealthAll() {
        try (Http1ClientResponse response = httpClient.get("/observe/health")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("UP"));
            JsonArray checks = json.getJsonArray("checks");
            assertThat(checks, notNullValue());
            assertThat(checks, hasSize(1));
        }

        try (Http1ClientResponse response = httpClient.method(Method.HEAD)
                .path("/observe/health")
                .request()) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat("Content returned", response.entity().inputStream().read(), is(-1));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health")
                .request()) {
            assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("DOWN"));
            JsonArray checks = json.getJsonArray("checks");
            assertThat(checks, notNullValue());
            assertThat(checks, hasSize(1));
        }

        try (Http1ClientResponse response = httpClient.method(Method.HEAD)
                .path("/observe/health")
                .request()) {

            assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
            assertThat("Content returned", response.entity().inputStream().read(), is(-1));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    void testHealthLive() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/live")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("UP"));
            JsonArray checks = json.getJsonArray("checks");
            assertThat(checks, notNullValue());
            assertThat(checks, hasSize(0));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health/live")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("UP"));
            JsonArray checks = json.getJsonArray("checks");
            assertThat(checks, notNullValue());
            assertThat(checks, hasSize(0));
        }
    }

    @Test
    void testHealthStart() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/started")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("UP"));
            JsonArray checks = json.getJsonArray("checks");
            assertThat(checks, notNullValue());
            assertThat(checks, hasSize(0));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health/started")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("UP"));
            JsonArray checks = json.getJsonArray("checks");
            assertThat(checks, notNullValue());
            assertThat(checks, hasSize(0));
        }
    }

    @Test
    void testHealthReady() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("UP"));
            JsonArray checks = json.getJsonArray("checks");
            assertThat(checks, notNullValue());
            assertThat(checks, hasSize(1));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready")
                .request()) {
            assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("DOWN"));
            JsonArray checks = json.getJsonArray("checks");
            assertThat(checks, notNullValue());
            assertThat(checks, hasSize(1));
        }
    }

    @Test
    void testHealthReadyOne() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready/mine1")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("UP"));
            assertThat(json.getString("name"), is("ready-1"));
            JsonObject data = json.getJsonObject("data");
            assertThat(data.getString("detail"), is("message"));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready/mine1")
                .request()) {
            assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("DOWN"));
            assertThat(json.getString("name"), is("ready-1"));
            JsonObject data = json.getJsonObject("data");
            assertThat(data.getString("detail"), is("message"));
        }
    }

    @Test
    void testHealthRootOne() {
        try (Http1ClientResponse response = httpClient.get("/observe/health/check/mine1")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("UP"));
            assertThat(json.getString("name"), is("ready-1"));
            JsonObject data = json.getJsonObject("data");
            assertThat(data.getString("detail"), is("message"));
        }

        healthCheck.status(DOWN);
        try (Http1ClientResponse response = httpClient.get("/observe/health/ready/mine1")
                .request()) {
            assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.getString("status"), is("DOWN"));
            assertThat(json.getString("name"), is("ready-1"));
            JsonObject data = json.getJsonObject("data");
            assertThat(data.getString("detail"), is("message"));
        }
    }
}
