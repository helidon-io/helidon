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

package io.helidon.webclient.tests.http2;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class CrossOriginRedirectHeaderTest {
    private static final HeaderName API_KEY_HEADER = HeaderNames.create("X-Api-Key");
    private static final String API_KEY_HEADER_CONFIG_NAME = "x-API-key";
    private static final String AUTHORIZATION = "Bearer secret-token";
    private static final String API_KEY = "key-0xDEADBEEF";

    private static final AtomicReference<CapturedHeaders> SAME_ORIGIN_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<CapturedHeaders> CROSS_ORIGIN_CAPTURE = new AtomicReference<>();

    private static WebServer trustedServer;
    private static WebServer redirectTargetServer;

    @BeforeAll
    static void beforeAll() {
        redirectTargetServer = WebServer.builder()
                .host("127.0.0.1")
                .port(-1)
                .routing(rules -> rules.get("/capture", (req, res) -> {
                    CROSS_ORIGIN_CAPTURE.set(capturedHeaders(req));
                    res.send("captured");
                }).get("/redirect/back-to-trusted", (req, res) -> {
                    res.status(Status.FOUND_302)
                            .header(HeaderNames.LOCATION,
                                    "http://127.0.0.1:" + trustedServer.port() + "/capture")
                            .send();
                }))
                .build()
                .start();

        trustedServer = WebServer.builder()
                .host("127.0.0.1")
                .port(-1)
                .routing(rules -> rules.get("/redirect/cross-origin", (req, res) -> {
                            res.status(Status.FOUND_302)
                                    .header(HeaderNames.LOCATION,
                                            "http://localhost:" + redirectTargetServer.port() + "/capture")
                                    .send();
                        })
                        .get("/redirect/same-origin", (req, res) -> {
                            res.status(Status.FOUND_302)
                                    .header(HeaderNames.LOCATION, "/capture")
                                    .send();
                        })
                        .put("/redirect/cross-origin-round-trip", (req, res) -> {
                            res.status(Status.FOUND_302)
                                    .header(HeaderNames.LOCATION,
                                            "http://localhost:" + redirectTargetServer.port() + "/redirect/back-to-trusted")
                                    .send();
                        })
                        .get("/capture", (req, res) -> {
                            SAME_ORIGIN_CAPTURE.set(capturedHeaders(req));
                            res.send("captured");
                        }))
                .build()
                .start();
    }

    @AfterAll
    static void afterAll() {
        if (trustedServer != null) {
            trustedServer.stop();
        }
        if (redirectTargetServer != null) {
            redirectTargetServer.stop();
        }
    }

    @BeforeEach
    void resetCapturedHeaders() {
        SAME_ORIGIN_CAPTURE.set(null);
        CROSS_ORIGIN_CAPTURE.set(null);
    }

    @Test
    void stripsAuthorizationOnCrossOriginRedirect() {
        try (Http2ClientResponse response = newClient().get("/redirect/cross-origin").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(API_KEY));
    }

    @Test
    void stripsConfiguredHeadersOnCrossOriginRedirect() {
        try (Http2ClientResponse response = newClient(true, true).get("/redirect/cross-origin").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
    }

    @Test
    void preservesConfiguredHeadersOnSameOriginRedirect() {
        try (Http2ClientResponse response = newClient(true, true).get("/redirect/same-origin").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = SAME_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(AUTHORIZATION));
        assertThat(captured.apiKey(), is(API_KEY));
    }

    @Test
    void preservesHeadersWhenRedirectFilteringDisabled() {
        try (Http2ClientResponse response = newClient(true, false).get("/redirect/cross-origin").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(AUTHORIZATION));
        assertThat(captured.apiKey(), is(API_KEY));
    }

    @Test
    void stripsConfiguredHeadersAcrossCrossOriginRoundTripRedirects() {
        try (Http2ClientResponse response = newClient(true, true).put("/redirect/cross-origin-round-trip")
                .outputStream(it -> {
                    it.write(API_KEY.getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = SAME_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
    }

    private static Http2Client newClient() {
        return newClient(false, true);
    }

    private static Http2Client newClient(boolean stripApiKeyOnRedirect, boolean filterRedirectHeaders) {
        var builder = Http2Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + trustedServer.port())
                .protocolConfig(it -> it.priorKnowledge(true))
                .addHeader(API_KEY_HEADER, API_KEY)
                .addService(new AuthorizationService())
                .filterRedirectHeaders(filterRedirectHeaders);
        if (stripApiKeyOnRedirect) {
            builder.addRedirectSensitiveHeader(API_KEY_HEADER_CONFIG_NAME);
        }
        return builder.build();
    }

    private record AuthorizationService() implements WebClientService {
        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            request.headers().set(HeaderNames.AUTHORIZATION, AUTHORIZATION);
            return chain.proceed(request);
        }
    }

    private static CapturedHeaders capturedHeaders(ServerRequest request) {
        return new CapturedHeaders(request.headers().first(HeaderNames.AUTHORIZATION).orElse(null),
                                   request.headers().first(API_KEY_HEADER).orElse(null));
    }

    private record CapturedHeaders(String authorization, String apiKey) {
    }
}
