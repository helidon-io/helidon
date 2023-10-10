/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

@ServerTest
public class MainTest {

    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject TEST_JSON_OBJECT = JSON_BUILDER.createObjectBuilder()
                                                                   .add("greeting", "Hola")
                                                                   .build();

    private final Http1Client client;

    public MainTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder server) {
        Config.global(Config.create());
        server.routing(Main::routing);
    }

    @Test
    public void testHelloWorld() {
        try (Http1ClientResponse response = client.get("/greet").request()) {
            assertThat(response.as(JsonObject.class).getString("message"), is("Hello World!"));
        }

        try (Http1ClientResponse response = client.get("/greet/Joe").request()) {
            assertThat(response.as(JsonObject.class).getString("message"), is("Hello Joe!"));
        }

        try (Http1ClientResponse response = client.put("/greet/greeting").submit(TEST_JSON_OBJECT)) {
            assertThat(response.status().code(), is(204));
        }

        try (Http1ClientResponse response = client.get("/greet/Joe").request()) {
            assertThat(response.as(JsonObject.class).getString("message"), is("Hola Joe!"));
        }

        try (Http1ClientResponse response = client.get("/observe/metrics").request()) {
            assertThat(response.status().code(), is(200));
        }
    }

    @Disabled // because of intermittent pipeline failures; the assertLinesMatch exhausts the available lines
    @Test
    public void testMetrics() {
        try (Http1ClientResponse response = client.get("/greet").request()) {
            assertThat(response.as(String.class), containsString("Hello World!"));
        }

        try (Http1ClientResponse response = client.get("/greet/Joe").request()) {
            assertThat(response.as(String.class), containsString("Hello Joe!"));
        }

        try (Http1ClientResponse response = client.get("/observe/metrics/application")
                .accept(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .request()) {

            String openMetricsOutput = response.as(String.class);
            LineNumberReader reader = new LineNumberReader(new StringReader(openMetricsOutput));
            List<String> returnedLines = reader.lines()
                                               .collect(Collectors.toList());
            returnedLines.add("# extra line at end to make sure we do not deplete the actual lines");

            List<String> expected = List.of(">> skip to max >>",
                    "# TYPE " + GreetService.COUNTER_FOR_PERSONALIZED_GREETINGS + " counter",
                    "# HELP " + GreetService.COUNTER_FOR_PERSONALIZED_GREETINGS + ".*",
                    valueMatcher(GreetService.COUNTER_FOR_PERSONALIZED_GREETINGS,"", "total"),
                    ">> end of output >>");
            assertLinesMatch(expected, returnedLines, GreetService.TIMER_FOR_GETS + "_seconds_max TYPE and value");
        }
    }

    private static String valueMatcher(String meterName, String unit, String statName) {
        // counterForPersonalizedGreetings_total{scope="application"} 1.0 # {span_id="41d2a1755a2b8797",trace_id="41d2a1755a2b8797"} 1.0 1696888732.920
        return meterName
                + (unit == null || unit.isBlank() ? "" : "_" + unit)
                + "_" + statName + ".*? # .*?trace_id=\"[^\"]+\"\\} [\\d\\.]+ [\\d\\.]+";
    }

}
