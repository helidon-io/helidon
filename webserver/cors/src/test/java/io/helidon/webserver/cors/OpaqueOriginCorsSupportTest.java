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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.cors.CrossOriginConfig;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_METHODS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.helidon.http.HeaderNames.ACCESS_CONTROL_REQUEST_METHOD;
import static io.helidon.http.HeaderNames.ORIGIN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

@ServerTest
class OpaqueOriginCorsSupportTest {
    private static final String DENIED_PATH = "/opaque-origin/denied";
    private static final String ALLOWED_PATH = "/opaque-origin/allowed";
    private static final String WILDCARD_PATH = "/opaque-origin/wildcard";
    private static final String DEFAULT_PATH = "/opaque-origin/default";
    private static final String WILDCARD_AND_NULL_PATH = "/opaque-origin/wildcard-and-null";
    private static final String ORDINARY_ORIGIN = "https://other.example";
    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final AtomicInteger REQUESTS = new AtomicInteger();

    private final Http1Client client;

    OpaqueOriginCorsSupportTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    @SuppressWarnings("removal")
    static void routing(HttpRules rules) {
        rules.any(DENIED_PATH,
                  corsSupport("https://trusted.example"),
                  (req, res) -> res.send(req.path().path() + "=" + REQUESTS.incrementAndGet()));
        rules.any(ALLOWED_PATH,
                  corsSupport("null"),
                  (req, res) -> res.send(req.path().path() + "=" + REQUESTS.incrementAndGet()));
        rules.any(WILDCARD_PATH,
                  corsSupport("*"),
                  (req, res) -> res.send(req.path().path() + "=" + REQUESTS.incrementAndGet()));
        rules.any(DEFAULT_PATH,
                  CorsSupport.create(),
                  (req, res) -> res.send(req.path().path() + "=" + REQUESTS.incrementAndGet()));
        rules.any(WILDCARD_AND_NULL_PATH,
                  corsSupport("*", "null"),
                  (req, res) -> res.send(req.path().path() + "=" + REQUESTS.incrementAndGet()));
    }

    @BeforeEach
    void resetCounters() {
        REQUESTS.set(0);
    }

    @Test
    void opaqueOriginPostRejectedByConcreteOrigin() {
        assertOpaquePostDenied(DENIED_PATH);
    }

    @Test
    void opaqueOriginPostRejectedByWildcard() {
        assertOpaquePostDenied(WILDCARD_PATH);
    }

    @Test
    void opaqueOriginPostRejectedByDefault() {
        assertOpaquePostDenied(DEFAULT_PATH);
    }

    @Test
    void opaqueOriginPostAllowedWhenExplicitlyConfigured() {
        int expectedRequests = 0;
        for (String path : List.of(ALLOWED_PATH, WILDCARD_AND_NULL_PATH)) {
            expectedRequests++;
            try (Http1ClientResponse response = submit(path, "null")) {
                assertThat(path, response.status(), is(Status.OK_200));
                assertThat(path, response.as(String.class), is(path + "=" + expectedRequests));
                assertThat(path, response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "null"));
            }
            assertThat(path, REQUESTS.get(), is(expectedRequests));
        }
    }

    @Test
    void opaqueOriginPreflightRejectedByConcreteOrigin() {
        assertOpaquePreflightDenied(DENIED_PATH);
    }

    @Test
    void opaqueOriginPreflightRejectedByWildcard() {
        assertOpaquePreflightDenied(WILDCARD_PATH);
    }

    @Test
    void opaqueOriginPreflightRejectedByDefault() {
        assertOpaquePreflightDenied(DEFAULT_PATH);
    }

    @Test
    void opaqueOriginPreflightAllowedWhenExplicitlyConfigured() {
        for (String path : List.of(ALLOWED_PATH, WILDCARD_AND_NULL_PATH)) {
            try (Http1ClientResponse response = client.method(Method.OPTIONS)
                    .uri(path)
                    .header(ORIGIN, "null")
                    .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-allowed")
                    .request()) {
                assertThat(path, response.status(), is(Status.OK_200));
                assertThat(path, response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "null"));
                assertThat(path, response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "POST"));
                assertThat(path, response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_HEADERS, "X-allowed"));
            }
            assertThat(path, REQUESTS.get(), is(0));
        }
    }

    @Test
    void explicitOpaqueOriginPreflightRejectsUnlistedMethod() {
        try (Http1ClientResponse response = client.method(Method.OPTIONS)
                .uri(ALLOWED_PATH)
                .header(ORIGIN, "null")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
                .request()) {
            assertAll(() -> assertThat(response.status(), is(Status.FORBIDDEN_403)),
                      () -> assertThat(response.headers(), noHeader(ACCESS_CONTROL_ALLOW_ORIGIN)),
                      () -> assertThat(REQUESTS.get(), is(0)));
        }
    }

    @Test
    void explicitOpaqueOriginPreflightRejectsUnlistedHeader() {
        try (Http1ClientResponse response = client.method(Method.OPTIONS)
                .uri(ALLOWED_PATH)
                .header(ORIGIN, "null")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(ACCESS_CONTROL_REQUEST_HEADERS, "X-denied")
                .request()) {
            assertAll(() -> assertThat(response.status(), is(Status.FORBIDDEN_403)),
                      () -> assertThat(response.headers(), noHeader(ACCESS_CONTROL_ALLOW_ORIGIN)),
                      () -> assertThat(REQUESTS.get(), is(0)));
        }
    }

    @Test
    void ordinaryOriginPostAllowedByWildcardAndDefault() {
        int expectedRequests = 0;
        for (String path : List.of(WILDCARD_PATH, DEFAULT_PATH, WILDCARD_AND_NULL_PATH)) {
            expectedRequests++;
            try (Http1ClientResponse response = submit(path, ORDINARY_ORIGIN)) {
                assertThat(path, response.status(), is(Status.OK_200));
                assertThat(path, response.as(String.class), is(path + "=" + expectedRequests));
                assertThat(path, response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
            }
            assertThat(path, REQUESTS.get(), is(expectedRequests));
        }
    }

    @Test
    void ordinaryOriginPreflightAllowedByWildcardAndDefault() {
        for (String path : List.of(WILDCARD_PATH, DEFAULT_PATH, WILDCARD_AND_NULL_PATH)) {
            try (Http1ClientResponse response = client.method(Method.OPTIONS)
                    .uri(path)
                    .header(ORIGIN, ORDINARY_ORIGIN)
                    .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .request()) {
                assertThat(path, response.status(), is(Status.OK_200));
                assertThat(path, response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_ORIGIN, ORDINARY_ORIGIN));
                assertThat(path, response.headers(), hasHeader(ACCESS_CONTROL_ALLOW_METHODS, "POST"));
            }
            assertThat(path, REQUESTS.get(), is(0));
        }
    }

    @Test
    void missingOriginIsProcessedAsNonCors() {
        int expectedRequests = 0;
        for (String path : List.of(DENIED_PATH, ALLOWED_PATH, WILDCARD_PATH, DEFAULT_PATH, WILDCARD_AND_NULL_PATH)) {
            expectedRequests++;
            try (Http1ClientResponse response = client.post(path)
                    .header(HeaderNames.CONTENT_TYPE, FORM_CONTENT_TYPE)
                    .submit("x=opaque".getBytes(StandardCharsets.US_ASCII))) {
                assertThat(path, response.status(), is(Status.OK_200));
                assertThat(path, response.as(String.class), is(path + "=" + expectedRequests));
                assertThat(path, response.headers(), noHeader(ACCESS_CONTROL_ALLOW_ORIGIN));
            }
            assertThat(path, REQUESTS.get(), is(expectedRequests));
        }
    }

    @SuppressWarnings("removal")
    private static CorsSupport corsSupport(String... allowedOrigins) {
        return CorsSupport.builder()
                .addCrossOrigin(CrossOriginConfig.builder()
                                        .allowOrigins(allowedOrigins)
                                        .allowMethods("POST")
                                        .allowHeaders("X-allowed")
                                        .build())
                .build();
    }

    private void assertOpaquePostDenied(String path) {
        try (Http1ClientResponse response = submit(path, "null")) {
            assertAll(() -> assertThat(path, response.status(), is(Status.FORBIDDEN_403)),
                      () -> assertThat(path, response.headers(), noHeader(ACCESS_CONTROL_ALLOW_ORIGIN)),
                      () -> assertThat(path, REQUESTS.get(), is(0)));
        }
    }

    private void assertOpaquePreflightDenied(String path) {
        try (Http1ClientResponse response = client.method(Method.OPTIONS)
                .uri(path)
                .header(ORIGIN, "null")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .request()) {
            assertAll(() -> assertThat(path, response.status(), is(Status.FORBIDDEN_403)),
                      () -> assertThat(path, response.headers(), noHeader(ACCESS_CONTROL_ALLOW_ORIGIN)),
                      () -> assertThat(path, REQUESTS.get(), is(0)));
        }
    }

    private Http1ClientResponse submit(String path, String origin) {
        return client.post(path)
                .header(ORIGIN, origin)
                .header(HeaderNames.CONTENT_TYPE, FORM_CONTENT_TYPE)
                .submit("x=opaque".getBytes(StandardCharsets.US_ASCII));
    }
}
