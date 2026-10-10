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

package io.helidon.telemetry.otelconfig;

import java.util.List;

import io.helidon.http.Status;
import io.helidon.telemetry.testing.tracing.JsonLogConverter;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.testing.junit5.DirectClient;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@RoutingTest
class TestConfigBasedTelemetryRouting {

    private static final String SERVICE_NAME = "test-config-telemetry-service";

    private final DirectClient client;

    TestConfigBasedTelemetryRouting(DirectClient client) {
        this.client = client;
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder builder) {
        builder.addFeature(ObserveFeature.create());
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.get("/greet", (req, res) -> res.send("Hello World!"));
    }

    @BeforeEach
    void resetGlobalOpenTelemetryBefore() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterEach
    void resetGlobalOpenTelemetryAfter() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void testConfigBasedTelemetrySpans() throws Exception {
        try (JsonLogConverter converter = JsonLogConverter.create()) {
            ClientResponseTyped<String> response = client.get("/greet").request(String.class);
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity(), is("Hello World!"));

            List<JsonLogConverter.LogResourceScopeSpans> scopeSpans = converter.resourceSpans(2);
            assertThat("Captured scope spans", scopeSpans, notNullValue());
            assertThat("Expected two resource spans", scopeSpans.size(), is(2));

            for (var resourceSpan : scopeSpans) {
                var resourceAttributes = resourceSpan.resource().attributes();
                assertThat("Resource attributes contains service name",
                           resourceAttributes,
                           hasEntry("service.name", SERVICE_NAME));

                var logSpans = resourceSpan.scopeSpans().getFirst().logSpans();
                assertThat("Expected span list", logSpans, notNullValue());
                assertThat("Expected at least one span in scope", logSpans.size(), is(1));

                var logSpan = logSpans.getFirst();
                assertThat("Span ID", logSpan.spanId(), notNullValue());
                assertThat("Span ID is not NoOp", logSpan.spanId(), not(containsString("0000000000000000")));
            }
        }
    }
}
