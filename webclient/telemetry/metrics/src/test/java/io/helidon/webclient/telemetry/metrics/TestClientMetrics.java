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

package io.helidon.webclient.telemetry.metrics;

import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@ServerTest
class TestClientMetrics {

    private final WebServer server;

    TestClientMetrics(WebServer server) {
        this.server = server;
    }

    @SetUpRoute
    static void setup(HttpRouting.Builder builder) {
        builder.get("/greet", (req, resp) -> resp.send("Hello, World!"));
    }

    @Test
    void testClientMetrics() throws InterruptedException {
        try (TestLogHandler handler = TestLogHandler.create(Logger.getLogger(OtlpJsonLoggingMetricExporter.class.getName()))) {

            var client = WebClient.builder()
                    .baseUri("http://localhost:" + server.port())
                    .addService(WebClientTelemetryMetrics.create())
                    .build();

            var response = client.get("/greet")
                    .accept(MediaTypes.TEXT_PLAIN)
                    .request(String.class);

            assertThat("Response status", response.status().code(), is(200));

            List<String> messages = handler.messages(1);

            var patternText = ".*\"dataPoints\":.*\"count\":\"([^\"]+)\".*\"sum\":([^,]+),(.*)";

            var jsonPattern = Pattern.compile(patternText);
            var matcher = jsonPattern.matcher(messages.getFirst());

            assertThat("Matched log line", matcher, allOf(
                    matches(true),
                    hasGroupAsInteger(1, is(1)),
                    hasGroupAsDouble(2, not(equalTo(0D))),
                    hasGroupAsString(3, containsString("\"key\":\"" + WebClientTelemetryMetrics.URL_TEMPLATE + "\","
                                                                     + "\"value\":{\"stringValue\":\"/greet\""))));

        }
    }
    static Matcher<java.util.regex.Matcher> matches(boolean expected) {
        return new FeatureMatcher<>(is(expected), "matches text", "matches") {
            @Override
            protected Boolean featureValueOf(java.util.regex.Matcher actual) {
                return actual.matches();
            }
        };
    }

    static Matcher<java.util.regex.Matcher> hasGroupAsString(int groupNumber, Matcher<String> matcher) {
        return new FeatureMatcher<>(matcher, "matches group " + groupNumber, "matches") {
            @Override
            protected String featureValueOf(java.util.regex.Matcher actual) {
                return actual.group(groupNumber);
            }
        };
    }

    static Matcher<java.util.regex.Matcher> hasGroupAsInteger(int groupNumber, Matcher<Integer> matcher) {
        return new FeatureMatcher<>(matcher, "matches group " + groupNumber, "matches") {
            @Override
            protected Integer featureValueOf(java.util.regex.Matcher actual) {
                return Integer.parseInt(actual.group(groupNumber));
            }
        };
    }

    static Matcher<java.util.regex.Matcher> hasGroupAsDouble(int groupNumber, Matcher<Double> matcher) {
        return new FeatureMatcher<>(matcher, "matches group " + groupNumber, "matches") {
            @Override
            protected Double featureValueOf(java.util.regex.Matcher actual) {
                return Double.parseDouble(actual.group(groupNumber));
            }
        };
    }
}
