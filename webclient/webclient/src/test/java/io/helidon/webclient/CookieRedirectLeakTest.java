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
package io.helidon.webclient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.http.Http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class CookieRedirectLeakTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String TRUSTED_HOST = "127.0.0.1";
    private static final String COLLECTOR_HOST = "localhost";
    private static final String DEFAULT_COOKIE = "session=default-token";
    private static final String REQUEST_COOKIE_VALUE = "request-token";
    private static final String REQUEST_COOKIE = "session=" + REQUEST_COOKIE_VALUE;
    private static final String STORED_COOKIE = "session=trusted-token";
    private static final String SET_STORED_COOKIE = STORED_COOKIE + "; Path=/";
    private static final String TARGET_COOKIE = "target=collector-token";
    private static final String SET_TARGET_COOKIE = TARGET_COOKIE + "; Path=/";

    private static final AtomicReference<String> TRUSTED_COOKIE = new AtomicReference<>();
    private static final AtomicReference<String> LEAKED_STEP_COOKIE = new AtomicReference<>();
    private static final AtomicReference<String> LEAKED_COLLECT_COOKIE = new AtomicReference<>();

    private static ExecutorService executor;
    private static HttpServer redirector;
    private static HttpServer collector;
    private static WebClient storedCookieClient;
    private static WebClient defaultCookieClient;
    private static WebClient requestCookieClient;

    @BeforeAll
    static void beforeAll() throws IOException {
        executor = Executors.newFixedThreadPool(4);

        collector = HttpServer.create(new InetSocketAddress(0), 0);
        collector.createContext("/prime", CookieRedirectLeakTest::primeTargetHandler);
        collector.createContext("/step", CookieRedirectLeakTest::stepHandler);
        collector.createContext("/collect", CookieRedirectLeakTest::collectHandler);
        collector.setExecutor(executor);
        collector.start();

        redirector = HttpServer.create(new InetSocketAddress(0), 0);
        redirector.createContext("/prime", CookieRedirectLeakTest::primeTrustedHandler);
        redirector.createContext("/bounce", CookieRedirectLeakTest::redirectHandler);
        redirector.setExecutor(executor);
        redirector.start();

        String trustedUri = "http://" + TRUSTED_HOST + ":" + redirector.getAddress().getPort();
        storedCookieClient = WebClient.builder()
                .baseUri(trustedUri)
                .enableAutomaticCookieStore(true)
                .followRedirects(true)
                .build();
        defaultCookieClient = WebClient.builder()
                .baseUri(trustedUri)
                .addCookie("session", "default-token")
                .followRedirects(true)
                .build();
        requestCookieClient = WebClient.builder()
                .baseUri(trustedUri)
                .followRedirects(true)
                .build();
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        if (redirector != null) {
            redirector.stop(0);
        }
        if (collector != null) {
            collector.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void storedCookiesShouldFollowRedirectTargetScope() {
        resetCookies();

        assertOk(storedCookieClient.get().uri(collectorUri("/prime")));
        assertOk(storedCookieClient.get().path("/prime"));
        assertOk(storedCookieClient.get().path("/bounce"));

        assertThat(TRUSTED_COOKIE.get(), is(STORED_COOKIE));
        assertThat(LEAKED_STEP_COOKIE.get(), is(TARGET_COOKIE));
        assertThat(LEAKED_COLLECT_COOKIE.get(), is(TARGET_COOKIE));
    }

    @Test
    void defaultCookiesShouldNotCrossHostRedirectBoundary() {
        resetCookies();

        assertOk(defaultCookieClient.get().path("/bounce"));

        assertThat(TRUSTED_COOKIE.get(), is(DEFAULT_COOKIE));
        assertThat(LEAKED_STEP_COOKIE.get(), is(nullValue()));
        assertThat(LEAKED_COLLECT_COOKIE.get(), is(nullValue()));
    }

    @Test
    void requestCookiesShouldNotCrossHostRedirectBoundary() {
        resetCookies();

        WebClientRequestBuilder request = requestCookieClient.get().path("/bounce");
        request.headers().addCookie("session", REQUEST_COOKIE_VALUE);
        assertOk(request);

        assertThat(TRUSTED_COOKIE.get(), containsString(REQUEST_COOKIE));
        assertThat(LEAKED_STEP_COOKIE.get(), is(nullValue()));
        assertThat(LEAKED_COLLECT_COOKIE.get(), is(nullValue()));
    }

    private static void resetCookies() {
        TRUSTED_COOKIE.set(null);
        LEAKED_STEP_COOKIE.set(null);
        LEAKED_COLLECT_COOKIE.set(null);
    }

    private static void assertOk(WebClientRequestBuilder request) {
        WebClientResponse response = request.request().await(TIMEOUT);
        try {
            assertThat(response.status(), is(Http.Status.OK_200));
        } finally {
            response.close().await(TIMEOUT);
        }
    }

    private static void primeTrustedHandler(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add(Http.Header.SET_COOKIE, SET_STORED_COOKIE);
        send(exchange, Http.Status.OK_200, "trusted primed");
    }

    private static void primeTargetHandler(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add(Http.Header.SET_COOKIE, SET_TARGET_COOKIE);
        send(exchange, Http.Status.OK_200, "target primed");
    }

    private static void redirectHandler(HttpExchange exchange) throws IOException {
        TRUSTED_COOKIE.set(extractCookie(exchange));
        exchange.getResponseHeaders().add(Http.Header.LOCATION, collectorUri("/step"));
        send(exchange, Http.Status.FOUND_302, "");
    }

    private static void stepHandler(HttpExchange exchange) throws IOException {
        LEAKED_STEP_COOKIE.set(extractCookie(exchange));
        exchange.getResponseHeaders().add(Http.Header.LOCATION, collectorUri("/collect"));
        send(exchange, Http.Status.FOUND_302, "");
    }

    private static void collectHandler(HttpExchange exchange) throws IOException {
        LEAKED_COLLECT_COOKIE.set(extractCookie(exchange));
        send(exchange, Http.Status.OK_200, "collector reached");
    }

    private static String extractCookie(HttpExchange exchange) {
        List<String> values = exchange.getRequestHeaders().getOrDefault(Http.Header.COOKIE, List.of());
        return values.isEmpty() ? null : String.join("; ", values);
    }

    private static String collectorUri(String path) {
        return "http://" + COLLECTOR_HOST + ":" + collector.getAddress().getPort() + path;
    }

    private static void send(HttpExchange exchange, Http.Status status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status.code(), bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
