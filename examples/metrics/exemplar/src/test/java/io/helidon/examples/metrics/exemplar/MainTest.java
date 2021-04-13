/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.metrics.exemplar;

import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainTest {

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
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
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
        assertEquals("Hello World!", jsonObject.getString("message"));

        jsonObject = webClient.get()
                .path("/greet/Joe")
                .request(JsonObject.class)
                .await();
        assertEquals("Hello Joe!", jsonObject.getString("message"));

        response = webClient.put()
                .path("/greet/greeting")
                .submit(TEST_JSON_OBJECT)
                .await();
        assertEquals(204, response.status().code());

        jsonObject = webClient.get()
                .path("/greet/Joe")
                .request(JsonObject.class)
                .await();
        assertEquals("Hola Joe!", jsonObject.getString("message"));

        response = webClient.get()
                .path("/metrics")
                .request()
                .await();
        assertEquals(200, response.status().code());
    }

    @Test
    public void testMetrics() {
        WebClientResponse response;

        String get = webClient.get()
                .path("/greet")
                .request(String.class)
                .await();

        assertTrue(get.contains("Hello World!"));

        get = webClient.get()
                .path("/greet/Joe")
                .request(String.class)
                .await();

        assertTrue(get.contains("Hello Joe!"));

        String openMetricsOutput = webClient.get()
                .path("/metrics/application")
                .request(String.class)
                .await();

        LineNumberReader reader = new LineNumberReader(new StringReader(openMetricsOutput));
        List<String> returnedLines = reader.lines()
                .collect(Collectors.toList());

        List<String> expected = List.of(">> skip to timer total >>",
                "# TYPE application_" + GreetService.TIMER_FOR_GETS + "_mean_seconds gauge",
                valueMatcher("mean"),
                ">> end of output >>");
        assertLinesMatch(expected, returnedLines, GreetService.TIMER_FOR_GETS + "_mean_seconds TYPE and value");

        expected = List.of(">> skip to max >>",
                "# TYPE application_" + GreetService.TIMER_FOR_GETS + "_max_seconds gauge",
                valueMatcher("max"),
                ">> end of output >>");
        assertLinesMatch(expected, returnedLines, GreetService.TIMER_FOR_GETS + "_max_seconds TYPE and value");

    }

    private static String valueMatcher(String statName) {
        // application_timerForGets_mean_seconds 0.010275403147594316 # {trace_id="cfd13196e6a9fb0c"} 0.002189822 1617799841.963000
        return "application_" + GreetService.TIMER_FOR_GETS
                + "_" + statName + "_seconds [\\d\\.]+ # \\{trace_id=\"[^\"]+\"\\} [\\d\\.]+ [\\d\\.]+";
    }

}
