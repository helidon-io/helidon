/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server;

import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.Http.HeaderNames;
import io.helidon.common.http.ServerRequestHeaders;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.api.HttpClientResponse;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServerConfig;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http1.Http1Config;
import io.helidon.nima.webserver.http1.Http1ConnectionSelector;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test the following configured limits:
 * <ul>
 *     <li>Max initial line (prologue) size</li>
 *     <li>Max headers size</li>
 * </ul>
 */
@ServerTest
class ConfiguredLimitsTest {
    private static final HeaderName CUSTOM_HEADER = HeaderNames.create("X_HEADER");

    private final Http1Client client;

    ConfiguredLimitsTest(Http1Client client) {
        this.client = client;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        ServerConnectionSelector http1 = Http1ConnectionSelector.builder()
                .config(Http1Config.builder()
                                .maxHeadersSize(1024)
                                .maxPrologueLength(512)
                                .build())
                .build();

        server.addConnectionSelector(http1);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.any(ConfiguredLimitsTest::handleRequest);
    }

    @Test
    void testOkHeader() {
        testHeader(100, true);
    }

    @Test
    void testLongHeader() {
        // we call server twice, to make sure the connection is not retained in case of bad request (should be closed)
        testHeader(1025, false);
        testHeader(1025, false);
    }

    @Test
    void testOkPrologue() {
        testInitialLine(512, true);
    }

    @Test
    void testLongPrologue() {
        // now test with big initial line
        testInitialLine(513, false);
        testInitialLine(513, false);
    }

    void testInitialLine(int size, boolean success) {
        // this is an exact size (until new line)
        /*
        3  11  x    1    8   1
        GET /mmmmmmm HTTP/1.1\r
         */
        int pathLength = size - 15;
        String path = "/" + "m".repeat(pathLength);

        try (HttpClientResponse response = client.get(path)
                .request()) {
            if (success) {
                assertThat("Initial line of size " + size + " should have passed",
                           response.status(),
                           is(Http.Status.OK_200));
                assertThat("This request should return what is configured in routing",
                           response.entity().as(String.class),
                           is("any"));
            } else {
                assertThat("Initial line of size " + size + " should have failed",
                           response.status(),
                           is(Http.Status.BAD_REQUEST_400));
            }
        }
    }

    void testHeader(int size, boolean success) {
        // headers size is combined size of all headers, we just create an additional header that increases the size
        // not an exact size of all headers!
        String headerValue = "m".repeat(size);

        try (Http1ClientResponse response = client.get("any")
                .header(Http.HeaderNames.create(CUSTOM_HEADER, headerValue))
                .request()) {
            if (success) {
                assertThat("Header of size " + size + " should have passed",
                           response.status(),
                           is(Http.Status.OK_200));
                assertThat("This request should return content of " + CUSTOM_HEADER + " header",
                           response.entity().as(String.class),
                           is(headerValue));
            } else {
                assertThat("Header of size " + size + " should have failed",
                           response.status(),
                           is(Http.Status.BAD_REQUEST_400));
            }
        }
    }

    private static void handleRequest(ServerRequest request, ServerResponse response) {
        ServerRequestHeaders headers = request.headers();
        if (headers.contains(CUSTOM_HEADER)) {
            response.send(headers.get(CUSTOM_HEADER).value());
            return;
        }

        response.send("any");
    }
}
