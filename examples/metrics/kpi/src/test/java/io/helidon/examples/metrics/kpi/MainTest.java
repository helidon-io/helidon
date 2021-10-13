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

package io.helidon.examples.metrics.kpi;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class MainTest {

    private static final MetricRegistry.Type KPI_REGISTRY_TYPE = MetricRegistry.Type.VENDOR;
    private static WebServer webServer;
    private static WebClient webClient;
    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject TEST_JSON_OBJECT;

    static {
        TEST_JSON_OBJECT = JSON_BUILDER.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
    }

    @BeforeAll
    public static void startTheServer() {
        webServer = Main.startServer().await();

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    public static void stopServer() {
        if (webServer != null) {
            webServer.shutdown()
                    .await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testHelloWorld() {
        JsonObject jsonObject;
        WebClientResponse response;

        jsonObject = webClient.get()
                .path("/greet")
                .request(JsonObject.class)
                .await();
        assertThat("Returned generic message", jsonObject.getString("message"), is("Hello World!"));

        jsonObject = webClient.get()
                .path("/greet/Joe")
                .request(JsonObject.class)
                .await();
        assertThat("Returned personalized message", jsonObject.getString("message"), is("Hello Joe!"));

        response = webClient.put()
                .path("/greet/greeting")
                .submit(TEST_JSON_OBJECT)
                .await();
        assertThat("Response status from setting greeting", response.status().code(), is(204));

        jsonObject = webClient.get()
                .path("/greet/Joe")
                .request(JsonObject.class)
                .await();
        assertThat("Response statuc after changing greeting", jsonObject.getString("message"), is("Hola Joe!"));

        response = webClient.get()
                .path("/metrics")
                .request()
                .await();
        assertThat("Response code from metrics", response.status().code(), is(200));
    }

    @Test
    public void testMetrics() {
        WebClientResponse response;

        String get = webClient.get()
                .path("/greet")
                .request(String.class)
                .await();

        assertThat("Response from generic greeting", get, containsString("Hello World!"));

        get = webClient.get()
                .path("/greet/Joe")
                .request(String.class)
                .await();

        assertThat("Response body from personalized greeting", get, containsString("Hello Joe!"));

        String openMetricsOutput = webClient.get()
                .path("/metrics/" + KPI_REGISTRY_TYPE.getName())
                .request(String.class)
                .await();

        assertThat("Returned metrics output",
                   openMetricsOutput,
                   chooseMatcher());
    }

    private static Matcher<String> chooseMatcher() {
        Matcher<String> contains = containsString("# TYPE " + KPI_REGISTRY_TYPE.getName() + "_requests_inFlight_current");
        return Main.USE_CONFIG
                ? contains
                : not(contains);
    }
}
