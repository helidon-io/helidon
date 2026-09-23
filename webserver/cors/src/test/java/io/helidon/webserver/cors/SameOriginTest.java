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
package io.helidon.webserver.cors;

import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

class SameOriginTest {

    private static final String HOST = "example.com";
    private static final String ALLOWED_ORIGIN = "http://trusted.example";

    @Test
    void rejectsHostnameSuffixForSimplePost() throws Exception {
        verifyRequest("POST", HOST, "http://example.com.attacker.invalid", 403, 0, false);
    }

    @Test
    void rejectsHostnameSuffixForPut() throws Exception {
        verifyRequest("PUT", HOST, "http://example.com.attacker.invalid", 403, 0, false);
    }

    @Test
    void rejectsHostnameSuffixForPreflight() throws Exception {
        verifyRequest("OPTIONS", HOST, "http://example.com.attacker.invalid", 403, 0, false);
    }

    @Test
    void rejectsDifferentScheme() throws Exception {
        verifyRequest("POST", HOST, "https://example.com", 403, 0, false);
    }

    @Test
    void rejectsDifferentPort() throws Exception {
        verifyRequest("POST", HOST, "http://example.com:8080", 403, 0, false);
    }

    @Test
    void rejectsPortSuffix() throws Exception {
        verifyRequest("POST", "example.com:808", "http://example.com:8080", 403, 0, false);
    }

    @Test
    void rejectsHostInUserInfo() throws Exception {
        verifyRequest("POST", HOST, "http://example.com@attacker.invalid", 403, 0, false);
    }

    @Test
    void rejectsHostInPath() throws Exception {
        verifyRequest("POST", HOST, "http://attacker.invalid/http://example.com", 403, 0, false);
    }

    @Test
    void permitsSameOrigin() throws Exception {
        verifyRequest("POST", HOST, "http://example.com", 200, 1, false);
    }

    @Test
    void permitsSameOriginWithUnderscore() throws Exception {
        verifyRequest("POST", "my_app.example", "http://my_app.example", 200, 1, false);
    }

    @Test
    void permitsExplicitDefaultHostPort() throws Exception {
        verifyRequest("POST", "example.com:80", "http://example.com", 200, 1, false);
    }

    @Test
    void permitsExplicitDefaultOriginPort() throws Exception {
        verifyRequest("POST", HOST, "http://example.com:80", 200, 1, false);
    }

    @Test
    void permitsSameNonDefaultPort() throws Exception {
        verifyRequest("POST", "example.com:8080", "http://example.com:8080", 200, 1, false);
    }

    @Test
    void permitsConfiguredCrossOrigin() throws Exception {
        verifyRequest("POST", HOST, ALLOWED_ORIGIN, 200, 1, true);
    }

    @Test
    void permitsConfiguredPreflightWithoutInvokingHandler() throws Exception {
        verifyRequest("OPTIONS", HOST, ALLOWED_ORIGIN, 200, 0, true);
    }

    @Test
    void permitsRequestWithoutOrigin() throws Exception {
        verifyRequest("POST", HOST, "", 200, 1, false);
    }

    @Test
    void rejectsUnrelatedOrigin() throws Exception {
        verifyRequest("POST", HOST, "http://unrelated.invalid", 403, 0, false);
    }

    private static void verifyRequest(String method,
                                      String host,
                                      String origin,
                                      int expectedStatus,
                                      int expectedHandlerHits,
                                      boolean expectedCorsHeaders) throws Exception {
        AtomicInteger handlerHits = new AtomicInteger();
        Handler handler = (request, response) -> {
            handlerHits.incrementAndGet();
            response.addHeader("X-Handler-Reached", "true");
            response.send(request.method().name() + " handler reached");
        };
        Routing.Builder routing = Routing.builder()
                .any("/probe", CorsSupport.builder()
                        .allowOrigins(ALLOWED_ORIGIN)
                        .allowMethods("POST", "PUT")
                        .build())
                .post("/probe", handler)
                .put("/probe", handler);

        WebServer server = WebServer.builder(routing)
                .bindAddress(InetAddress.getByName("127.0.0.1"))
                .port(0)
                .build();
        try {
            server.start().toCompletableFuture().get(10, TimeUnit.SECONDS);
            String response = send(server.port(), method, host, origin);
            String lowerResponse = response.toLowerCase(Locale.ROOT);

            assertThat(response, startsWith("HTTP/1.1 " + expectedStatus + " "));
            assertThat(handlerHits.get(), is(expectedHandlerHits));
            if (expectedHandlerHits == 0) {
                assertThat(lowerResponse, not(containsString("x-handler-reached:")));
            } else {
                assertThat(lowerResponse, containsString("x-handler-reached: true"));
                assertThat(response, containsString(method + " handler reached"));
            }
            if (expectedCorsHeaders) {
                assertThat(lowerResponse, containsString("access-control-allow-origin: " + ALLOWED_ORIGIN));
            } else {
                assertThat(lowerResponse, not(containsString("access-control-allow-origin:")));
            }
            if (expectedStatus == 403) {
                assertThat(response, containsString("CORS origin is not in allowed list"));
            }
        } finally {
            server.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    private static String send(int port, String method, String host, String origin) throws Exception {
        String body = method.equals("POST") ? "value=1" : "";
        String request = method + " /probe HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + (origin.isEmpty() ? "" : "Origin: " + origin + "\r\n")
                + (method.equals("POST") ? "Content-Type: application/x-www-form-urlencoded\r\n" : "")
                + (method.equals("OPTIONS") ? "Access-Control-Request-Method: PUT\r\n" : "")
                + "Content-Length: " + body.length() + "\r\n"
                + "Connection: close\r\n\r\n"
                + body;
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
