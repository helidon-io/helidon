/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.common.OptionalHelper;
import io.helidon.common.http.Http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TestHttpParsingDefaults {
    static final String GOOD_HEADER_NAME = "X_HEADER";
    static final String BAD_HEADER_NAME = "X\tHEADER";

    private static Client client;
    private static WebServer webServer;
    private static WebTarget target;

    @BeforeAll
    static void initClass() throws InterruptedException, ExecutionException, TimeoutException {
        client = ClientBuilder.newClient();

        webServer = WebServer.builder(Routing.builder()
                                              .any(TestHttpParsingDefaults::handleRequest)
                                              .build())
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        target = client.target("http://localhost:" + webServer.port());
    }

    @AfterAll
    static void destroyClass() throws InterruptedException, ExecutionException, TimeoutException {
        if (client != null) {
            client.close();
        }
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void testOkHeader() {
        testHeader(target, 8000, true);
    }

    @Test
    void testLongHeader() {
        testHeader(target, 8900, false);
    }

    @Test
    void testOkInitialLine() {
        testInitialLine(target, 10, true);
    }

    @Test
    void testLongInitialLine() {
        // now test with big initial line
        testInitialLine(target, 5000, false);
    }

    @Test
    void testGoodHeaderName() {
        testHeaderName(target, GOOD_HEADER_NAME, true);
    }

    @Test
    void testBadHeaderName() {
        testHeaderName(target, BAD_HEADER_NAME, false);
    }

    static void handleRequest(ServerRequest request, ServerResponse response) {
        RequestHeaders headers = request.headers();
        String value = OptionalHelper.from(headers.value(GOOD_HEADER_NAME))
                .or(() -> headers.value(BAD_HEADER_NAME))
                .asOptional()
                .orElse("any");
        response.send(value);
    }

    static void testHeaderName(WebTarget target, String headerName, boolean success) {
        String value = "some random value";

        Response response = target.path("/some/path")
                .request()
                .header(headerName, value)
                .get();

        if (success) {
            assertThat("Header '" + headerName + "' should have passed",
                       response.getStatus(),
                       is(Http.Status.OK_200.code()));
            assertThat("This request should return content of the provided header",
                       response.readEntity(String.class),
                       is(value));
        } else {
            assertThat("Header '" + headerName + "' should have failed",
                       response.getStatus(),
                       is(Http.Status.BAD_REQUEST_400.code()));
        }
    }

    static void testInitialLine(WebTarget target, int size, boolean success) {
        String line = longString(size);
        Response response = target.path("/long/" + line)
                .request()
                .get();

        if (success) {
            assertThat("Initial line of size " + size + " should have passed",
                       response.getStatus(),
                       is(Http.Status.OK_200.code()));
            assertThat("This request should return what is configured in routing", response.readEntity(String.class),
                       is("any"));
        } else {
            assertThat("Initial line of size " + size + " should have failed",
                       response.getStatus(),
                       is(Http.Status.BAD_REQUEST_400.code()));
        }
    }

    static void testHeader(WebTarget target, int size, boolean success) {
        String headerValue = longString(size);

        Response response = target.path("/any")
                .request()
                .header(GOOD_HEADER_NAME, headerValue)
                .get();

        if (success) {
            assertThat("Header of size " + size + " should have passed",
                       response.getStatus(),
                       is(Http.Status.OK_200.code()));
            assertThat("This request should return content of " + GOOD_HEADER_NAME + " header",
                       response.readEntity(String.class),
                       is(headerValue));
        } else {
            assertThat("Header of size " + size + " should have failed",
                       response.getStatus(),
                       is(Http.Status.BAD_REQUEST_400.code()));
        }
    }

    private static String longString(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append("a");
        }
        return sb.toString();
    }
}
