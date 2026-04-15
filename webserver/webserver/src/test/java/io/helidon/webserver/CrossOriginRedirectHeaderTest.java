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
package io.helidon.webserver;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.WebClientServiceRequest;
import io.helidon.webclient.spi.WebClientService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class CrossOriginRedirectHeaderTest {
    private static final Duration TIME_OUT = Duration.ofSeconds(5);
    private static final String API_KEY_HEADER = "X-Api-Key";
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
                .port(0)
                .addRouting(redirectTargetRouting())
                .build();
        redirectTargetServer.start().await(TIME_OUT);

        trustedServer = WebServer.builder()
                .port(0)
                .addRouting(trustedRouting())
                .build();
        trustedServer.start().await(TIME_OUT);
    }

    @AfterAll
    static void afterAll() {
        if (trustedServer != null) {
            trustedServer.shutdown().await(TIME_OUT);
        }
        if (redirectTargetServer != null) {
            redirectTargetServer.shutdown().await(TIME_OUT);
        }
    }

    @BeforeEach
    void resetCapturedHeaders() {
        SAME_ORIGIN_CAPTURE.set(null);
        CROSS_ORIGIN_CAPTURE.set(null);
    }

    @Test
    void stripsAuthorizationOnCrossOriginRedirect() {
        WebClientResponse response = newClient().get()
                .path("/redirect/cross-origin")
                .request()
                .await(TIME_OUT);

        assertSuccessfulResponse(response);

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(API_KEY));
    }

    @Test
    void stripsConfiguredHeadersOnCrossOriginRedirect() {
        WebClientResponse response = newClient(true, true).get()
                .path("/redirect/cross-origin")
                .request()
                .await(TIME_OUT);

        assertSuccessfulResponse(response);

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
    }

    @Test
    void preservesConfiguredHeadersOnSameOriginRedirect() {
        WebClientResponse response = newClient(true, true).get()
                .path("/redirect/same-origin")
                .request()
                .await(TIME_OUT);

        assertSuccessfulResponse(response);

        CapturedHeaders captured = SAME_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(AUTHORIZATION));
        assertThat(captured.apiKey(), is(API_KEY));
    }

    @Test
    void preservesHeadersWhenRedirectFilteringDisabled() {
        WebClientResponse response = newClient(true, false).get()
                .path("/redirect/cross-origin")
                .request()
                .await(TIME_OUT);

        assertSuccessfulResponse(response);

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(AUTHORIZATION));
        assertThat(captured.apiKey(), is(API_KEY));
    }

    @Test
    void stripsConfiguredHeadersAcrossCrossOriginRoundTripRedirects() {
        WebClientResponse response = newClient(true, true).get()
                .path("/redirect/cross-origin-round-trip")
                .request()
                .await(TIME_OUT);

        assertSuccessfulResponse(response);

        CapturedHeaders captured = SAME_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
    }

    @Test
    void stripsConfiguredHeadersAcrossCrossOriginFollowUpRedirectsOnTheSameTarget() {
        WebClientResponse response = newClient(true, true).get()
                .path("/redirect/cross-origin-same-target")
                .request()
                .await(TIME_OUT);

        assertSuccessfulResponse(response);

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
    }

    private static WebClient newClient() {
        return newClient(false, true);
    }

    private static WebClient newClient(boolean stripApiKeyOnRedirect, boolean filterRedirectHeaders) {
        WebClient.Builder builder = WebClient.builder()
                .baseUri("http://127.0.0.1:" + trustedServer.port())
                .followRedirects(true)
                .keepAlive(false)
                .addHeader(API_KEY_HEADER, API_KEY)
                .addService(new AuthorizationService())
                .filterRedirectHeaders(filterRedirectHeaders);
        if (stripApiKeyOnRedirect) {
            builder.addRedirectSensitiveHeader(API_KEY_HEADER_CONFIG_NAME);
        }
        return builder.build();
    }

    private static void assertSuccessfulResponse(WebClientResponse response) {
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.content().as(String.class).await(TIME_OUT), is("captured"));
        response.close().await(TIME_OUT);
    }

    private static Routing trustedRouting() {
        return Routing.builder()
                .get("/redirect/cross-origin",
                     (req, res) -> redirect(res, "http://localhost:" + redirectTargetServer.port() + "/capture"))
                .get("/redirect/cross-origin-same-target",
                     (req, res) -> redirect(res,
                                            "http://localhost:" + redirectTargetServer.port()
                                                    + "/redirect/within-cross-origin"))
                .get("/redirect/same-origin", (req, res) -> redirect(res, "/capture"))
                .get("/redirect/cross-origin-round-trip",
                     (req, res) -> redirect(res,
                                            "http://localhost:" + redirectTargetServer.port()
                                                    + "/redirect/back-to-trusted"))
                .get("/capture", (req, res) -> {
                    SAME_ORIGIN_CAPTURE.set(capturedHeaders(req));
                    res.send("captured");
                })
                .build();
    }

    private static Routing redirectTargetRouting() {
        return Routing.builder()
                .get("/capture", (req, res) -> {
                    CROSS_ORIGIN_CAPTURE.set(capturedHeaders(req));
                    res.send("captured");
                })
                .get("/redirect/within-cross-origin", (req, res) -> redirect(res, "/capture"))
                .get("/redirect/back-to-trusted",
                     (req, res) -> redirect(res, "http://127.0.0.1:" + trustedServer.port() + "/capture"))
                .build();
    }

    private static CapturedHeaders capturedHeaders(ServerRequest request) {
        return new CapturedHeaders(request.headers().first(Http.Header.AUTHORIZATION).orElse(null),
                                   request.headers().first(API_KEY_HEADER).orElse(null));
    }

    private static void redirect(ServerResponse response, String location) {
        response.status(Http.Status.FOUND_302);
        response.headers().add(Http.Header.LOCATION, location);
        response.send();
    }

    private record AuthorizationService() implements WebClientService {
        @Override
        public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
            request.headers().put(Http.Header.AUTHORIZATION, AUTHORIZATION);
            return Single.just(request);
        }
    }

    private record CapturedHeaders(String authorization, String apiKey) {
    }
}
