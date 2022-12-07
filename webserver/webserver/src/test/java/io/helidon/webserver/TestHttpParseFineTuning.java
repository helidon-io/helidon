/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TestHttpParseFineTuning {

    private static final Duration TIME_OUT = Duration.ofSeconds(10);

    @Test
    void testDefaults() {
        // default is 8Kb for headers
        // and 4096 for initial line
        WebServer ws = WebServer.builder()
                .host("localhost")
                .routing(r -> r.any((req, res) -> res.send("any"))
                )
                .build()
                .start()
                .await(TIME_OUT);

        WebClient client = WebClient.builder()
                .baseUri("http://localhost:" + ws.port())
                .validateHeaders(false)
                .build();

        testHeader(client, 16000, true);
        testInitialLine(client, 10, true);

        testHeader(client, 17000, false);
        testHeader(client, 17000, false);

        // now test with big initial line
        testInitialLine(client, 5000, false);

        testHeaderName(client, "X_HEADER", true);
        testHeaderName(client, "X\tHEADER", false);
    }

    @Test
    void testCustom() {
        Config config = Config.create(ConfigSources.create(Map.of("validate-headers", "false")));

        WebServer ws = WebServer.builder()
                .host("localhost")
                .routing(r -> r.any((req, res) -> res.send("any")))
                .config(config)
                .maxHeaderSize(16400)
                .maxInitialLineLength(5100)
                .build()
                .start()
                .await(TIME_OUT);

        WebClient client = WebClient.builder()
                .baseUri("http://localhost:" + ws.port())
                .validateHeaders(false)
                .build();

        testHeader(client, 8000, true);
        testInitialLine(client, 10, true);

        testHeader(client, 8900, true);
        testHeader(client, 8900, true);

        // now test with big initial line
        testInitialLine(client, 5000, true);

        testHeaderName(client, "X_HEADER", true);
        testHeaderName(client, "X\tHEADER", true);
    }

    private void testHeaderName(WebClient client, String headerName, boolean success) {
        WebClientRequestBuilder builder = client.get();
        builder.headers().add(headerName, "some random value");
        WebClientResponse response = builder.path("/static/static-content.txt")
                .request()
                .await(TIME_OUT);

        if (success) {
            assertThat("Header '" + headerName + "' should have passed", response.status(), is(Http.Status.OK_200));
            assertThat("This request should return content 'any'", response.content()
                               .as(String.class)
                               .await(10, TimeUnit.SECONDS),
                       is("any"));
        } else {
            assertThat("Header '" + headerName + "' should have failed", response.status(), is(Http.Status.BAD_REQUEST_400));
        }
    }

    private void testInitialLine(WebClient client, int size, boolean success) {
        String line = longString(size);
        WebClientResponse response = client.get()
                .path("/long/" + line)
                .request()
                .await(TIME_OUT);

        if (success) {
            assertThat("Initial line of size " + size + " should have passed", response.status(), is(Http.Status.OK_200));
            assertThat("This request should return what is configured in routing", response.content()
                               .as(String.class)
                               .await(10, TimeUnit.SECONDS),
                       is("any"));
        } else {
            assertThat("Initial line of size " + size + " should have failed",
                       response.status(),
                       is(Http.Status.BAD_REQUEST_400));
        }
    }

    private void testHeader(WebClient client, int size, boolean success) {
        String headerValue = longString(size);
        WebClientRequestBuilder builder = client.get();
        builder.headers().add("X_HEADER", headerValue);
        WebClientResponse response = builder.path("/static/static-content.txt")
                .request()
                .await(TIME_OUT);

        if (success) {
            assertThat("Header of size " + size + " should have passed", response.status(), is(Http.Status.OK_200));
            assertThat("This request should return content 'any'", response.content()
                               .as(String.class)
                               .await(TIME_OUT),
                       is("any"));
        } else {
            assertThat("Header of size " + size + " should have failed", response.status(), is(Http.Status.BAD_REQUEST_400));
        }
    }

    private String longString(int size) {
        return "a".repeat(Math.max(0, size));
    }
}
