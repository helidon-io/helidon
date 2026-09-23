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

package io.helidon.microprofile.health;

import java.io.StringReader;
import java.util.List;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@HelidonTest
@AddBean(value = HealthErrorDetailsIT.FailingReadinessCheck.class, scope = Dependent.class)
@AddConfig(key = "mp.health.disable-default-procedures", value = "true")
class HealthErrorDetailsIT {
    private static final String SENSITIVE_MESSAGE = "jdbc:test://database.example:5432/private?password=health-secret";

    @Inject
    private WebTarget webTarget;

    @Test
    void defaultDetailedHealthOmitsExceptionDetails() {
        for (String path : List.of("health", "health/ready")) {
            try (Response response = webTarget.path(path).request(MediaType.APPLICATION_JSON).get()) {
                assertThat(path, response.getStatus(), is(500));
                String body = response.readEntity(String.class);
                assertThat(path, body, not(containsString(SENSITIVE_MESSAGE)));
                assertThat(path, body, not(containsString(IllegalStateException.class.getName())));
                try (JsonReader reader = Json.createReader(new StringReader(body))) {
                    JsonObject json = reader.readObject();
                    List<JsonObject> failedChecks = json.getJsonArray("checks").stream()
                            .map(JsonObject.class::cast)
                            .filter(check -> !"UP".equals(check.getString("status")))
                            .toList();
                    assertThat(path, failedChecks, hasSize(1));
                    assertSanitizedCheck(path, failedChecks.get(0));
                }
            }
        }
    }

    @Test
    void headReportsFailureWithoutDetails() {
        for (String path : List.of("health", "health/ready")) {
            try (Response response = webTarget.path(path).request().head()) {
                assertThat(path, response.getStatus(), is(500));
                assertThat(path, response.hasEntity(), is(false));
            }
        }
    }

    private static void assertSanitizedCheck(String path, JsonObject check) {
        assertThat(path, check.keySet(), containsInAnyOrder("name", "status", "data"));
        assertThat(path, check.getString("name"), not(is("")));
        assertThat(path, check.getString("status"), is("DOWN"));
        JsonObject data = check.getJsonObject("data");
        assertThat(path, data.keySet(), containsInAnyOrder("message"));
        assertThat(path, data.getString("message"), is("Failed to compute health. Error logged"));
    }

    @Readiness
    public static class FailingReadinessCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            throw new IllegalStateException(SENSITIVE_MESSAGE);
        }
    }
}
