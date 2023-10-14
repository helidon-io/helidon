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
package io.helidon.metrics.exemplartrace;

import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.List;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Metrics;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

@ServerTest
class ExemplarTest {

    private static final String COUNTER_NAME = "testCounter";

    private final Http1Client client;

    ExemplarTest(Http1Client http1Client) {
        client = http1Client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.get("/test", (req, res) -> {
                        Metrics.globalRegistry().getOrCreate(Counter.builder(COUNTER_NAME)).increment();
                        res.send();
                    });
    }

    @Test
    @Disabled("Intermittently failing")
    void checkForExemplarsInOpenMetricsOutput() {

        try (Http1ClientResponse response = client.get("/test")
                .request()) {
            assertThat("Ping status", response.status().code(), is(200));
        }

        try (Http1ClientResponse response = client.get("/observe/metrics")
                .accept(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .request()) {

            assertThat("Metrics response", response.status().code(), is(200));
            String openMetricsOutput = response.as(String.class);
            // Expected output example:
            // testCounter_total{scope="application"} 2.0 # {span_id="0000000000000000",trace_id="00000000000000000000000000000000"} 1.0 1696881384.311

            LineNumberReader reader = new LineNumberReader(new StringReader(openMetricsOutput));
            List<String> returnedLines = reader.lines()
                            .toList();

            List<String> expected = List.of(">> skip to counter >>",
                                            "testCounter_total.*?#.*?span_id=.*",
                                            ">> end of output >>");
            assertLinesMatch(expected, returnedLines, "Counter output with exemplar");
        }
    }
}
