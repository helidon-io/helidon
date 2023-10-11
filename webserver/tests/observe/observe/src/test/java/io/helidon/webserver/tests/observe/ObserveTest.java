/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.observe;

import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.http.Status;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;
import io.helidon.webserver.observe.info.InfoObserver;
import io.helidon.webserver.observe.metrics.MetricsObserver;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class ObserveTest {
    private static TestHealthCheck healthCheck;

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        Config config = GlobalConfig.config();

        // quite often we need to pass something to the health check, so this represents a real usage
        healthCheck = new TestHealthCheck("message");
        // possible customization of metrics
        MeterRegistry meterRegistry = Metrics.globalRegistry();

        InfoObserver info = InfoObserver.builder()
                .putValue("name", "ObserveTest")
                .putValue("description", "Test for observability features")
                .putValue("version", "1.0.0")
                .endpoint("myInfo")
                .build();
        MetricsObserver metrics = MetricsObserver.builder()
                .meterRegistry(meterRegistry)
                .build();

        HealthObserver health = HealthObserver.create(healthCheck);

        server.featuresDiscoverServices(false)
                .addFeature(ObserveFeature.builder()
                                    .addObserver(health)
                                    .addObserver(info)
                                    .addObserver(metrics)
                                    .config(config.get("observe"))
                                    .build());
    }

    @Test
    void testInfoObserver(WebClient client) {
        JsonObject jsonObject = client.get("/observe/myInfo/name")
                .requestEntity(JsonObject.class);
        assertThat("JSON: " + jsonObject, jsonObject.getString("name"), is("name"));
        assertThat("JSON: " + jsonObject, jsonObject.getString("value"), is("ObserveTest"));

        jsonObject = client.get("/observe/myInfo")
                .requestEntity(JsonObject.class);
        assertThat("JSON: " + jsonObject, jsonObject.getString("name"), is("ObserveTest"));
        assertThat("JSON: " + jsonObject, jsonObject.getString("description"), is("Test for observability features"));
        assertThat("JSON: " + jsonObject, jsonObject.getString("version"), is("1.0.0"));
    }

    @Test
    void testInfoNotTwice(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/info")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.NOT_FOUND_404));
    }

    @Test
    void testMetricsObserver(WebClient client) {
        ClientResponseTyped<String> response = client.get("/observe/metrics")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat("Entity: " + response.entity(), response.entity(), startsWith("# HELP"));
    }

    @Test
    void testHealthObserver(WebClient client) {
        healthCheck.reset();
        ClientResponseTyped<String> response = client.get("/observe/health")
                .request(String.class);

        assertThat(response.status(), is(Status.NO_CONTENT_204));
        assertThat(healthCheck.calls(), is(1));
    }

    @Test
    void testLogObserver(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/log/loggers")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));
        JsonObject entity = response.entity();
        assertThat("Entity: " + entity, entity.getJsonArray("levels").getString(0), is("OFF"));
    }

    @Test
    void testLogObserverLogger(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/log/loggers/io.helidon.webserver.ServerListener")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));
        JsonObject entity = response.entity();
        assertThat("Entity: " + entity, entity.getJsonObject("io.helidon.webserver.ServerListener"), notNullValue());
    }

    @Test
    void testConfigObserver(WebClient client) {
        /*
        endpoint for config observer is customized in application.yaml
         */
        ClientResponseTyped<String> response = client.get("/observe/myConfig/profile")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        String entity = response.entity();
        assertThat("Entity: " + entity, entity, not("should not be seen"));
    }

    @Test
    void testConfigObserverCustomSecret(WebClient client) {
        ClientResponseTyped<String> response = client.get("/observe/myConfig/values/app.some-secret-text")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        String entity = response.entity();
        assertThat("Entity: " + entity, entity, not(containsString("should not be seen")));
    }

    @Test
    void testConfigObserverBuiltInSecret(WebClient client) {
        ClientResponseTyped<String> response = client.get("/observe/myConfig/values/app.some-password")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        String entity = response.entity();
        assertThat("Entity: " + entity, entity, not(containsString("should not be seen")));
    }

    @Test
    void testConfigObserverValue(WebClient client) {
        ClientResponseTyped<String> response = client.get("/observe/myConfig/values/app.text")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        String entity = response.entity();
        assertThat("Entity: " + entity, entity, containsString("should be seen"));
    }
}
