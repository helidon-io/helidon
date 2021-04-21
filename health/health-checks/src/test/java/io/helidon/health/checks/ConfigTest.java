/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.health.checks;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.json.JsonObject;
import javax.json.JsonValue;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.health.HealthSupport;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class ConfigTest {

    private static final Config testConfig = Config.create(ConfigSources.classpath("/testConfig.yaml"));

    static WebClient.Builder webClientBuilder(WebServer webServer) {
        return WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/")
                .addMediaSupport(JsonpSupport.create());
    }

    static WebServer startServer(
           Service... services) throws
            InterruptedException, ExecutionException, TimeoutException {
        return WebServer.builder(
                Routing.builder()
                        .register(services)
                        .build())
                .port(0)
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }

    static void shutdownServer(WebServer server) {
        server.shutdown();
    }

    @Test
    void bothFail() throws InterruptedException, ExecutionException, TimeoutException {
        JsonObject health = runWithConfig("bothFail", Http.Status.SERVICE_UNAVAILABLE_503.code());
        JsonObject diskSpace = getLivenessCheck(health, "diskSpace");

        assertThat("Disk space liveness return data", diskSpace, is(notNullValue()));
        assertThat("Disk space liveness check status", diskSpace.getString("status"), is("DOWN"));

        JsonObject heapMemory = getLivenessCheck(health, "heapMemory");

        assertThat("Heap memory liveness return data", heapMemory, is(notNullValue()));
        assertThat("Heap memory liveness check status", heapMemory.getString("status"), is("DOWN"));
    }

    @Test
    void bothPass() throws InterruptedException, ExecutionException, TimeoutException {
        JsonObject health = runWithConfig("bothPass", Http.Status.OK_200.code());
        JsonObject diskSpace = getLivenessCheck(health, "diskSpace");

        assertThat("Disk space liveness return data", diskSpace, is(notNullValue()));
        assertThat("Disk space liveness check status", diskSpace.getString("status"), is("UP"));

        JsonObject heapMemory = getLivenessCheck(health, "heapMemory");

        assertThat("Heap memory liveness return data", heapMemory, is(notNullValue()));
        assertThat("Heap memory liveness check status", heapMemory.getString("status"), is("UP"));
    }

    private JsonObject runWithConfig(String configKey, int expectedStatus) throws InterruptedException, ExecutionException,
            TimeoutException {
        HealthSupport healthSupport = HealthSupport.builder()
                .addLiveness(HealthChecks.healthChecks(testConfig.get(configKey + ".helidon.health")))
                .build();
        WebServer webServer = null;
        try {
            webServer = startServer(healthSupport);
            WebClientResponse response = webClientBuilder(webServer)
                    .build()
                    .get()
                    .accept(MediaType.APPLICATION_JSON)
                    .path("health/live")
                    .submit()
                    .await();

            assertThat("Normal health URL HTTP response", response.status().code(), is(expectedStatus));

            return response.content()
                    .as(JsonObject.class)
                    .await();
        } finally {
            if (webServer != null) {
                shutdownServer(webServer);
            }
        }
    }

    private JsonObject getLivenessCheck(JsonObject health, String checkName) {
        return health.getJsonArray("checks").stream()
                .map(JsonValue::asJsonObject)
                .filter(jo -> jo.getString("name").equals(checkName))
                .findFirst()
                .orElse(null);
    }
}
