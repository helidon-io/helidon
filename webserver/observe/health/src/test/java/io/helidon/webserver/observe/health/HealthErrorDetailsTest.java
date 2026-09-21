/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.health;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@ServerTest
class HealthErrorDetailsTest {
    private static final String SENSITIVE_MESSAGE = "jdbc:test://database.example:5432/private?password=health-secret";

    private final Http1Client client;

    HealthErrorDetailsTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        Supplier<HealthCheckResponse> failingCheck = () -> {
            throw new IllegalStateException(SENSITIVE_MESSAGE);
        };
        routing.register("/failure", healthService(failingCheck, "failing", true))
                .register("/status-only", healthService(failingCheck, "failing", false))
                .register("/null-message", healthService(() -> {
                    throw new IllegalStateException();
                }, "null-message", true))
                .register("/application", healthService(() -> HealthCheckResponse.builder()
                        .status(HealthCheckResponse.Status.DOWN)
                        .detail("message", "Retrying")
                        .detail("error", "temporary")
                        .detail("attempts", 2)
                        .build(), "application", true));
    }

    @Test
    void aggregateErrorsOmitExceptionDetails() {
        for (String path : List.of("/failure", "/failure/ready")) {
            try (Http1ClientResponse response = client.get(path).request()) {
                assertThat(path, response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
                JsonObject json = response.as(JsonObject.class);
                assertThat(json.stringValue("status"), is(Optional.of("ERROR")));
                List<JsonValue> checks = json.arrayValue("checks").orElseThrow().values();
                assertThat(checks, hasSize(1));
                assertErrorCheck(checks.getFirst().asObject(), "failing");
            }
        }
    }

    @Test
    void singleErrorsOmitExceptionDetails() {
        for (String path : List.of("/failure/check/failing", "/failure/ready/failing")) {
            try (Http1ClientResponse response = client.get(path).request()) {
                assertThat(path, response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
                assertErrorCheck(response.as(JsonObject.class), "failing");
            }
        }
    }

    @Test
    void exceptionsWithoutMessagesKeepTheHealthResponse() {
        try (Http1ClientResponse response = client.get("/null-message").request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.stringValue("status"), is(Optional.of("ERROR")));
            List<JsonValue> checks = json.arrayValue("checks").orElseThrow().values();
            assertThat(checks, hasSize(1));
            assertErrorCheck(checks.getFirst().asObject(), "null-message");
        }
        try (Http1ClientResponse response = client.get("/null-message/check/null-message").request()) {
            assertThat(response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertErrorCheck(response.as(JsonObject.class), "null-message");
        }
    }

    @Test
    void applicationDetailsArePreserved() {
        try (Http1ClientResponse response = client.get("/application").request()) {
            assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json.stringValue("status"), is(Optional.of("DOWN")));
            List<JsonValue> checks = json.arrayValue("checks").orElseThrow().values();
            assertThat(checks, hasSize(1));
            assertApplicationCheck(checks.getFirst().asObject());
        }
        try (Http1ClientResponse response = client.get("/application/check/application").request()) {
            assertThat(response.status(), is(Status.SERVICE_UNAVAILABLE_503));
            assertApplicationCheck(response.as(JsonObject.class));
        }
    }

    @Test
    void disabledDetailsReturnOnlyStatus() {
        for (String path : List.of("/status-only", "/status-only/check/failing")) {
            try (Http1ClientResponse response = client.get(path).request()) {
                assertThat(path, response.status(), is(Status.INTERNAL_SERVER_ERROR_500));
                assertThat(path, response.entity().hasEntity(), is(false));
            }
        }
    }

    private static void assertErrorCheck(JsonObject check, String name) {
        assertThat(check.stringValue("name"), is(Optional.of(name)));
        assertThat(check.stringValue("status"), is(Optional.of("ERROR")));
        assertThat(check.value("data"), is(Optional.empty()));
    }

    private static void assertApplicationCheck(JsonObject check) {
        assertThat(check.stringValue("name"), is(Optional.of("application")));
        assertThat(check.stringValue("status"), is(Optional.of("DOWN")));
        JsonObject data = check.objectValue("data").orElseThrow();
        assertThat(data.stringValue("message"), is(Optional.of("Retrying")));
        assertThat(data.stringValue("error"), is(Optional.of("temporary")));
        assertThat(data.intValue("attempts"), is(Optional.of(2)));
    }

    private static HealthService healthService(Supplier<HealthCheckResponse> check, String name, boolean details) {
        HealthObserver observer = HealthObserver.builder()
                .useSystemServices(false)
                .details(details)
                .addCheck(check, HealthCheckType.READINESS, name)
                .build();
        return new HealthService(observer.prototype(), observer.all());
    }
}
