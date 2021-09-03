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

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TestHttpParsingDefaults {
    static final String GOOD_HEADER_NAME = "X_HEADER";
    static final String BAD_HEADER_NAME = "X\tHEADER";

    private static WebServer webServer;
    private static WebClient client;

    @BeforeAll
    static void initClass() throws InterruptedException, ExecutionException, TimeoutException {
        LogConfig.configureRuntime();

        webServer = WebServer.builder(Routing.builder()
                                              .any(TestHttpParsingDefaults::handleRequest)
                                              .build())
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        client = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port())
                .validateHeaders(false)
                .keepAlive(true)
                .build();
    }

    @AfterAll
    static void destroyClass() throws InterruptedException, ExecutionException, TimeoutException {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void testOkHeader() {
        testHeader(8000, true);
    }

    @Test
    void testLongHeader() {
        testHeader(8900, false);
        testHeader(8900, false);
    }

    @Test
    void testOkInitialLine() {
        testInitialLine(10, true);
    }

    @Test
    void testLongInitialLine() {
        // now test with big initial line
        testInitialLine(5000, false);
        testInitialLine(5000, false);
    }

    @Test
    void testGoodHeaderName() {
        testHeaderName(GOOD_HEADER_NAME, true);
    }

    @Test
    void testBadHeaderName() {
        testHeaderName(BAD_HEADER_NAME, false);
        testHeaderName(BAD_HEADER_NAME, false);
    }

    static void handleRequest(ServerRequest request, ServerResponse response) {
        RequestHeaders headers = request.headers();
        String value = headers.value(GOOD_HEADER_NAME)
                .or(() -> headers.value(BAD_HEADER_NAME))
                .orElse("any");
        response.send(value);
    }

    static void testHeaderName(String headerName, boolean success) {
        String value = "some random value";

        WebClientResponse response = client.get()
                .path("/some/path")
                .headers(headers -> {
                    headers.add(headerName, value);
                    return headers;
                })
                .request()
                .await(10, TimeUnit.SECONDS);

        try {
            if (success) {
                assertThat("Header '" + headerName + "' should have passed",
                           response.status(),
                           is(Http.Status.OK_200));
                assertThat("This request should return content of the provided header",
                           response.content().as(String.class).await(10, TimeUnit.SECONDS),
                           is(value));
            } else {
                assertThat("Header '" + headerName + "' should have failed",
                           response.status(),
                           is(Http.Status.BAD_REQUEST_400));
            }
        } finally {
            response.close();
        }
    }

    static void testInitialLine(int size, boolean success) {
        String line = longString(size);

        WebClientResponse response = client.get()
                .path("/long/" + line)
                .request()
                .await(10, TimeUnit.SECONDS);

        try {
            if (success) {
                assertThat("Initial line of size " + size + " should have passed",
                           response.status(),
                           is(Http.Status.OK_200));
                assertThat("This request should return what is configured in routing",
                           response.content().as(String.class).await(10, TimeUnit.SECONDS),
                           is("any"));
            } else {
                assertThat("Initial line of size " + size + " should have failed",
                           response.status(),
                           is(Http.Status.BAD_REQUEST_400));
            }
        } finally {
            response.close();
        }
    }

    static void testHeader(int size, boolean success) {
        String headerValue = longString(size);

        WebClientResponse response = client.get()
                .path("/any")
                .headers(headers -> {
                    headers.add(GOOD_HEADER_NAME, headerValue);
                    return headers;
                })
                .request()
                //                .await(10, TimeUnit.SECONDS);
                .await();

        try {
            if (success) {
                assertThat("Header of size " + size + " should have passed",
                           response.status(),
                           is(Http.Status.OK_200));
                assertThat("This request should return content of " + GOOD_HEADER_NAME + " header",
                           response.content().as(String.class).await(10, TimeUnit.SECONDS),
                           is(headerValue));
            } else {
                assertThat("Header of size " + size + " should have failed",
                           response.status(),
                           is(Http.Status.BAD_REQUEST_400));
            }
        } finally {
            response.close().await(10, TimeUnit.SECONDS);
        }
    }

    private static String longString(int size) {
        return "a".repeat(size);
    }
}
