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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.GenericType;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrossOriginRedirectHeaderTest {
    private static final HeaderName API_KEY_HEADER = HeaderNames.create("X-Api-Key");
    private static final HeaderName WRITER_HEADER = HeaderNames.create("X-Writer-Metadata");
    private static final HeaderName WRITER_OVERRIDE_HEADER = HeaderNames.create("X-Writer-Override");
    private static final String API_KEY_HEADER_CONFIG_NAME = "x-API-key";
    private static final String AUTHORIZATION = "Bearer secret-token";
    private static final String PROXY_AUTHORIZATION = "Basic proxy-secret";
    private static final String API_KEY = "key-0xDEADBEEF";
    private static final String REQUEST_BODY = "sensitive-body";
    private static final String BLOCKED_REDIRECT_MESSAGE = "Cross-origin redirect with request entity is disabled.";
    private static final String WRITER_AUTHORITY = "writer.example";

    private static final AtomicReference<CapturedHeaders> SAME_ORIGIN_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<CapturedHeaders> CROSS_ORIGIN_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<String> CROSS_ORIGIN_BODY_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<String> CROSS_ORIGIN_PROTOCOL_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<Method> CROSS_ORIGIN_METHOD_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<String> CROSS_ORIGIN_CONTENT_TYPE_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<Boolean> CROSS_ORIGIN_EXPECT_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<String> CROSS_ORIGIN_AUTHORITY_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<CapturedWriterHeaders> WRITER_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<CapturedWriterFinalHeaders> WRITER_FINAL_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<String> PRESERVED_WRITER_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<String> DISCARDED_WRITER_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<String> PATH_COOKIE_CAPTURE = new AtomicReference<>();

    private static WebServer trustedServer;
    private static WebServer redirectTargetServer;

    @BeforeAll
    static void beforeAll() {
        redirectTargetServer = WebServer.builder()
                .host("127.0.0.1")
                .port(-1)
                .routing(rules -> rules.get("/capture", (req, res) -> {
                    CROSS_ORIGIN_CAPTURE.set(capturedHeaders(req));
                    CROSS_ORIGIN_PROTOCOL_CAPTURE.set(req.prologue().protocolVersion());
                    CROSS_ORIGIN_EXPECT_CAPTURE.set(req.headers().contains(HeaderNames.EXPECT));
                    res.send("captured");
                }).put("/capture-body", (req, res) -> {
                    CROSS_ORIGIN_CAPTURE.set(capturedHeaders(req));
                    CROSS_ORIGIN_PROTOCOL_CAPTURE.set(req.prologue().protocolVersion());
                    CROSS_ORIGIN_EXPECT_CAPTURE.set(req.headers().contains(HeaderNames.EXPECT));
                    CROSS_ORIGIN_AUTHORITY_CAPTURE.set(req.requestedUri().authority());
                    try (InputStream inputStream = req.content().inputStream()) {
                        CROSS_ORIGIN_BODY_CAPTURE.set(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                        res.send("captured");
                    } catch (Exception e) {
                        res.status(Status.INTERNAL_SERVER_ERROR_500)
                                .send(e.getMessage());
                    }
                }).route(Method.QUERY, "/capture-query", (req, res) -> {
                    CROSS_ORIGIN_CAPTURE.set(capturedHeaders(req));
                    CROSS_ORIGIN_METHOD_CAPTURE.set(req.prologue().method());
                    CROSS_ORIGIN_CONTENT_TYPE_CAPTURE.set(req.headers().get(HeaderNames.CONTENT_TYPE).get());
                    try (InputStream inputStream = req.content().inputStream()) {
                        CROSS_ORIGIN_BODY_CAPTURE.set(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                        res.send("captured");
                    } catch (Exception e) {
                        res.status(Status.INTERNAL_SERVER_ERROR_500)
                                .send(e.getMessage());
                    }
                }).put("/capture-entity", (req, res) -> {
                    WRITER_CAPTURE.set(new CapturedWriterHeaders(
                            req.headers().first(HeaderNames.AUTHORIZATION).orElse(null),
                            req.headers().first(HeaderNames.COOKIE).orElse(null),
                            req.headers().first(WRITER_HEADER).orElse(null),
                            req.headers().first(HeaderNames.CONTENT_TYPE).orElse(null),
                            req.content().as(String.class)));
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
                                            "http://localhost:" + redirectTargetServer.port()
                                                    + "/redirect/back-to-trusted")
                                    .send();
                        })
                        .put("/redirect/cross-origin-change-method", (req, res) -> {
                            res.status(Status.FOUND_302)
                                    .header(HeaderNames.LOCATION,
                                            "http://localhost:" + redirectTargetServer.port() + "/capture")
                                    .send();
                        })
                        .put("/redirect/cross-origin-keep-method", (req, res) -> {
                            res.status(Status.TEMPORARY_REDIRECT_307)
                                    .header(HeaderNames.LOCATION,
                                            "http://localhost:" + redirectTargetServer.port() + "/capture-body")
                                    .send();
                        })
                        .put("/redirect/cross-origin-keep-method-308", (req, res) -> {
                            res.status(Status.PERMANENT_REDIRECT_308)
                                    .header(HeaderNames.LOCATION,
                                            "http://localhost:" + redirectTargetServer.port() + "/capture-body")
                                    .send();
                        })
                        .route(Method.QUERY, "/redirect/cross-origin-query-301", (req, res) -> {
                            res.status(Status.MOVED_PERMANENTLY_301)
                                    .header(HeaderNames.LOCATION,
                                            "http://localhost:" + redirectTargetServer.port() + "/capture-query")
                                    .send();
                        })
                        .route(Method.QUERY, "/redirect/cross-origin-query-302", (req, res) -> {
                            res.status(Status.FOUND_302)
                                    .header(HeaderNames.LOCATION,
                                            "http://localhost:" + redirectTargetServer.port() + "/capture-query")
                                    .send();
                        })
                        .put("/redirect/cross-origin-entity", (req, res) -> {
                            req.content().as(String.class);
                            res.status(Status.TEMPORARY_REDIRECT_307)
                                    .header(HeaderNames.LOCATION,
                                            "http://localhost:" + redirectTargetServer.port() + "/capture-entity")
                                    .send();
                        })
                        .put("/redirect/writer-preserve", (req, res) -> {
                            req.content().as(String.class);
                            res.status(Status.TEMPORARY_REDIRECT_307)
                                    .header(HeaderNames.LOCATION, "/redirect/writer-to-get")
                                    .send();
                        })
                        .put("/redirect/writer-to-get", (req, res) -> {
                            PRESERVED_WRITER_CAPTURE.set(req.headers()
                                                                 .first(WRITER_OVERRIDE_HEADER)
                                                                 .orElse(null));
                            req.content().as(String.class);
                            res.status(Status.SEE_OTHER_303)
                                    .header(HeaderNames.LOCATION, "/capture-writer-get")
                                    .send();
                        })
                        .get("/capture-writer-get", (req, res) -> {
                            DISCARDED_WRITER_CAPTURE.set(req.headers()
                                                                 .first(WRITER_OVERRIDE_HEADER)
                                                                 .orElse(null));
                            res.send();
                        })
                        .put("/source/path-cookie-redirect", (req, res) -> {
                            req.content().as(String.class);
                            res.status(Status.TEMPORARY_REDIRECT_307)
                                    .header(HeaderNames.LOCATION, "/other/path-cookie-target")
                                    .send();
                        })
                        .put("/other/path-cookie-target", (req, res) -> {
                            PATH_COOKIE_CAPTURE.set(req.headers().contains(HeaderNames.COOKIE)
                                                            ? String.join("; ",
                                                                          req.headers()
                                                                                  .get(HeaderNames.COOKIE)
                                                                                  .allValues())
                                                            : "");
                            res.send(req.content().as(String.class));
                        })
                        .put("/capture-writer-final", (req, res) -> {
                            WRITER_FINAL_CAPTURE.set(new CapturedWriterFinalHeaders(
                                    req.headers().first(HeaderNames.HOST).orElse(null),
                                    req.headers().contains(HeaderNames.COOKIE)
                                            ? String.join("; ", req.headers().get(HeaderNames.COOKIE).allValues())
                                            : null,
                                    req.content().as(String.class)));
                            res.send();
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
        CROSS_ORIGIN_BODY_CAPTURE.set(null);
        CROSS_ORIGIN_PROTOCOL_CAPTURE.set(null);
        CROSS_ORIGIN_METHOD_CAPTURE.set(null);
        CROSS_ORIGIN_CONTENT_TYPE_CAPTURE.set(null);
        CROSS_ORIGIN_EXPECT_CAPTURE.set(null);
        CROSS_ORIGIN_AUTHORITY_CAPTURE.set(null);
        WRITER_CAPTURE.set(null);
        WRITER_FINAL_CAPTURE.set(null);
        PRESERVED_WRITER_CAPTURE.set(null);
        DISCARDED_WRITER_CAPTURE.set(null);
        PATH_COOKIE_CAPTURE.set(null);
    }

    @Test
    void stripsAuthorizationOnCrossOriginRedirect() {
        try (Http2ClientResponse response = newClient().get("/redirect/cross-origin").request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.proxyAuthorization(), is(nullValue()));
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
        assertThat(captured.proxyAuthorization(), is(PROXY_AUTHORIZATION));
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
        assertThat(captured.proxyAuthorization(), is(PROXY_AUTHORIZATION));
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

    @Test
    void rejectsBufferedEntityOnCrossOrigin307Redirect() {
        rejectBufferedEntityOnCrossOriginRedirect("/redirect/cross-origin-keep-method");
    }

    @Test
    void rejectsBufferedEntityOnCrossOrigin308Redirect() {
        rejectBufferedEntityOnCrossOriginRedirect("/redirect/cross-origin-keep-method-308");
    }

    @Test
    void rejectsBufferedQueryOnCrossOriginRedirect() {
        for (String redirectPath : List.of("/redirect/cross-origin-query-301", "/redirect/cross-origin-query-302")) {
            resetCapturedHeaders();
            Http2Client client = newClient(true, true);
            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> client.method(Method.QUERY)
                                                                       .uri(redirectPath)
                                                                       .header(HeaderNames.CONTENT_TYPE, "application/sql")
                                                                       .submit(requestBodyBytes()));
                assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));
                assertThat(CROSS_ORIGIN_CAPTURE.get(), is(nullValue()));
                assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void followsCrossOriginQueryWithEntityWhenEnabled() {
        for (String redirectPath : List.of("/redirect/cross-origin-query-301", "/redirect/cross-origin-query-302")) {
            resetCapturedHeaders();
            Http2Client client = newClient(true, true, true);
            try {
                try (Http2ClientResponse response = client.method(Method.QUERY)
                        .uri(redirectPath)
                        .header(HeaderNames.CONTENT_TYPE, "application/sql")
                        .submit(requestBodyBytes())) {
                    assertThat(response.status(), is(Status.OK_200));
                }

                CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
                assertThat(captured, is(notNullValue()));
                assertThat(captured.authorization(), is(nullValue()));
                assertThat(captured.proxyAuthorization(), is(nullValue()));
                assertThat(captured.apiKey(), is(nullValue()));
                assertThat(CROSS_ORIGIN_METHOD_CAPTURE.get(), is(Method.QUERY));
                assertThat(CROSS_ORIGIN_CONTENT_TYPE_CAPTURE.get(), is("application/sql"));
                assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(REQUEST_BODY));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void rejectsBufferedEntityWhenH2cUpgradeRedirectsCrossOrigin() {
        rejectBufferedEntityWhenH2cUpgradeRedirectsCrossOrigin("/redirect/cross-origin-keep-method");
    }

    @Test
    void rejectsBufferedEntityWhenH2cUpgradeRedirectsCrossOrigin308() {
        rejectBufferedEntityWhenH2cUpgradeRedirectsCrossOrigin("/redirect/cross-origin-keep-method-308");
    }

    @Test
    void rejectsBufferedEntityWhenH2cFallback307RedirectsCrossOrigin() throws Exception {
        rejectBufferedEntityWhenH2cFallbackRedirectsCrossOrigin(307);
    }

    @Test
    void rejectsBufferedEntityWhenH2cFallback308RedirectsCrossOrigin() throws Exception {
        rejectBufferedEntityWhenH2cFallbackRedirectsCrossOrigin(308);
    }

    @Test
    void sanitizesPreparedWriterHeadersBeforeCrossOriginReplay() {
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + trustedServer.port())
                .protocolConfig(it -> it.priorKnowledge(true))
                .followCrossOriginEntityRedirects(true)
                .mediaContext(writerMediaContext(false))
                .build();
        try (Http2ClientResponse response = client.put("/redirect/cross-origin-entity")
                .submit(new WriterEntity("payload"))) {
            assertThat(response.status(), is(Status.OK_200));
        } finally {
            client.closeResource();
        }

        CapturedWriterHeaders captured = WRITER_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.cookie(), is(nullValue()));
        assertThat(captured.writerMetadata(), is("preserved"));
        assertThat(captured.contentType(), is("text/plain"));
        assertThat(captured.body(), is("payload"));
    }

    @Test
    void appliesWriterFinalAuthorityBeforeSelectingCookies() {
        CookieStore cookieStore = new CookieManager().getCookieStore();
        HttpCookie originalCookie = new HttpCookie("original-manager", "must-not-leak");
        originalCookie.setPath("/");
        cookieStore.add(URI.create("http://127.0.0.1:" + trustedServer.port()), originalCookie);
        HttpCookie writerCookie = new HttpCookie("writer-manager", "selected");
        writerCookie.setPath("/");
        cookieStore.add(URI.create("http://" + WRITER_AUTHORITY), writerCookie);
        WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                .automaticStoreEnabled(true)
                .cookieStore(cookieStore));
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + trustedServer.port())
                .protocolConfig(it -> it.priorKnowledge(true))
                .cookieManager(cookieManager)
                .mediaContext(writerMediaContext(true))
                .build();

        try (Http2ClientResponse response = client.put("/capture-writer-final")
                .submit(new WriterEntity("payload"))) {
            assertThat(response.status(), is(Status.OK_200));
        } finally {
            client.closeResource();
        }

        CapturedWriterFinalHeaders captured = WRITER_FINAL_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.host(), is(WRITER_AUTHORITY));
        assertThat(captured.cookie(), not(containsString("original-manager")));
        assertThat(captured.cookie(), containsString("writer-cookie=secret"));
        assertThat(captured.cookie(), containsString("writer-manager"));
        assertThat(captured.body(), is("payload"));
    }

    @Test
    void removesWriterHeadersWhenRedirectChangesRequestToGet() {
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + trustedServer.port())
                .protocolConfig(it -> it.priorKnowledge(true))
                .addService(new WriterDefaultService())
                .mediaContext(writerMediaContext(false))
                .build();

        try (Http2ClientResponse response = client.put("/redirect/writer-preserve")
                .submit(new WriterEntity("payload"))) {
            assertThat(response.status(), is(Status.OK_200));
        } finally {
            client.closeResource();
        }

        assertThat(PRESERVED_WRITER_CAPTURE.get(), is("writer-final"));
        assertThat(DISCARDED_WRITER_CAPTURE.get(), is("final-default"));
    }

    @Test
    void redirectRebuildsPathCookiesWithoutReplayingSourceStoreCookie() {
        CookieStore cookieStore = new CookieManager().getCookieStore();
        HttpCookie sourceCookie = new HttpCookie("source-manager", "must-not-leak");
        sourceCookie.setPath("/source");
        cookieStore.add(URI.create("http://127.0.0.1:" + trustedServer.port()), sourceCookie);
        HttpCookie targetCookie = new HttpCookie("target-manager", "selected");
        targetCookie.setPath("/other");
        cookieStore.add(URI.create("http://127.0.0.1:" + trustedServer.port()), targetCookie);
        WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                .automaticStoreEnabled(true)
                .cookieStore(cookieStore));
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + trustedServer.port())
                .protocolConfig(it -> it.priorKnowledge(true))
                .cookieManager(cookieManager)
                .mediaContext(writerMediaContext(false))
                .build();

        try (Http2ClientResponse response = client.put("/source/path-cookie-redirect")
                .header(HeaderNames.COOKIE, "user-cookie=kept")
                .submit(new WriterEntity("payload"))) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("payload"));
        } finally {
            client.closeResource();
        }

        String cookies = PATH_COOKIE_CAPTURE.get();
        assertThat(cookies, not(containsString("source-manager")));
        assertThat(cookies, containsString("target-manager"));
        assertThat(cookies, containsString("user-cookie=kept"));
        assertThat(cookies, containsString("writer-cookie=secret"));
    }

    @Test
    void nonInstanceWriterRedirectRebuildsPathCookiesWithoutReplayingSourceStoreCookie() {
        CookieStore cookieStore = new CookieManager().getCookieStore();
        HttpCookie sourceCookie = new HttpCookie("source-manager", "must-not-leak");
        sourceCookie.setPath("/source");
        cookieStore.add(URI.create("http://127.0.0.1:" + trustedServer.port()), sourceCookie);
        HttpCookie targetCookie = new HttpCookie("target-manager", "selected");
        targetCookie.setPath("/other");
        cookieStore.add(URI.create("http://127.0.0.1:" + trustedServer.port()), targetCookie);
        WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                .automaticStoreEnabled(true)
                .cookieStore(cookieStore));
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + trustedServer.port())
                .protocolConfig(it -> it.priorKnowledge(true))
                .cookieManager(cookieManager)
                .mediaContext(writerMediaContext(false, false))
                .build();

        try (Http2ClientResponse response = client.put("/source/path-cookie-redirect")
                .header(HeaderNames.COOKIE, "user-cookie=kept")
                .header(HeaderNames.CONTENT_LENGTH, "7")
                .submit(new WriterEntity("payload"))) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("payload"));
        } finally {
            client.closeResource();
        }

        String cookies = PATH_COOKIE_CAPTURE.get();
        assertThat(cookies, not(containsString("source-manager")));
        assertThat(cookies, containsString("target-manager"));
        assertThat(cookies, containsString("user-cookie=kept"));
        assertThat(cookies, containsString("writer-cookie=secret"));
    }

    private static void rejectBufferedEntityWhenH2cFallbackRedirectsCrossOrigin(int redirectStatus) throws Exception {
        try (RedirectingHttp1Server firstHop = new RedirectingHttp1Server(redirectStatus)) {
            Http2Client client = newClient(firstHop.port(), true, true, null, false);
            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> client.put("/token")
                                                                       .submit(requestBodyBytes()));
                assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));
            } finally {
                client.closeResource();
            }
        }
        assertThat(CROSS_ORIGIN_CAPTURE.get(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    @Test
    void followsH2cFallbackBuffered307RedirectWithEntityWhenEnabled() throws Exception {
        followsH2cFallbackBufferedRedirectWithEntityWhenEnabled(307);
    }

    @Test
    void followsH2cFallbackBuffered308RedirectWithEntityWhenEnabled() throws Exception {
        followsH2cFallbackBufferedRedirectWithEntityWhenEnabled(308);
    }

    private static void followsH2cFallbackBufferedRedirectWithEntityWhenEnabled(int redirectStatus) throws Exception {
        try (RedirectingHttp1Server firstHop = new RedirectingHttp1Server(redirectStatus)) {
            Http2Client client = newClient(firstHop.port(), true, true, true, false);
            try (Http2ClientResponse response = client.put("/token")
                    .submit(requestBodyBytes())) {
                assertThat(response.status(), is(Status.OK_200));
            } finally {
                client.closeResource();
            }
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(REQUEST_BODY));
    }

    private static void rejectBufferedEntityWhenH2cUpgradeRedirectsCrossOrigin(String redirectPath) {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                       () -> newClient(true, true, null, false)
                                                               .put(redirectPath)
                                                               .submit(requestBodyBytes()));
        assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));
        assertThat(CROSS_ORIGIN_CAPTURE.get(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    @Test
    void followsH2cUpgradeRedirectsCrossOriginWithoutEntity() {
        try (Http2ClientResponse response = newClient(true, true, null, false)
                .get("/redirect/cross-origin")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    @Test
    void h2cUpgradeRedirectDoesNotCacheHttp1Fallback() throws Exception {
        try (RedirectingHttp1Server firstHop = new RedirectingHttp1Server(302, false, 2)) {
            Http2Client client = newClient(firstHop.port(), true, true, null, false);
            try {
                try (Http2ClientResponse response = client.get("/token").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                resetCapturedHeaders();
                try (Http2ClientResponse response = client.get("/token").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
            } finally {
                client.closeResource();
            }
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    @Test
    void followsH2cUpgrade307RedirectWithEmptyStringEntityByDefault() {
        followsH2cUpgradeRedirectWithEmptyStringEntityByDefault("/redirect/cross-origin-keep-method");
    }

    @Test
    void followsH2cUpgrade308RedirectWithEmptyStringEntityByDefault() {
        followsH2cUpgradeRedirectWithEmptyStringEntityByDefault("/redirect/cross-origin-keep-method-308");
    }

    private static void followsH2cUpgradeRedirectWithEmptyStringEntityByDefault(String redirectPath) {
        try (Http2ClientResponse response = newClient(true, true, null, false)
                .put(redirectPath)
                .submit("")) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(""));
    }

    @Test
    void followsH2cUpgradeRedirectWithEntityWhenEnabled() {
        followsH2cUpgradeRedirectWithEntityWhenEnabled("/redirect/cross-origin-keep-method");
    }

    @Test
    void followsH2cUpgradeRedirectWithEntityWhenEnabled308() {
        followsH2cUpgradeRedirectWithEntityWhenEnabled("/redirect/cross-origin-keep-method-308");
    }

    private static void followsH2cUpgradeRedirectWithEntityWhenEnabled(String redirectPath) {
        try (Http2ClientResponse response = newClient(true, true, true, false)
                .put(redirectPath)
                .submit(requestBodyBytes())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(REQUEST_BODY));
    }

    @Test
    void rejectsOutputStreamEntityWhenH2cUpgradeRedirectsCrossOrigin() {
        rejectOutputStreamEntityWhenH2cUpgradeRedirectsCrossOrigin("/redirect/cross-origin-keep-method");
    }

    @Test
    void rejectsOutputStreamEntityWhenH2cUpgradeRedirectsCrossOrigin308() {
        rejectOutputStreamEntityWhenH2cUpgradeRedirectsCrossOrigin("/redirect/cross-origin-keep-method-308");
    }

    @Test
    void rejectsOutputStreamEntityWhenH2cFallback307RedirectsCrossOrigin() throws Exception {
        rejectOutputStreamEntityWhenH2cFallbackRedirectsCrossOrigin(307);
    }

    @Test
    void rejectsOutputStreamEntityWhenH2cFallback308RedirectsCrossOrigin() throws Exception {
        rejectOutputStreamEntityWhenH2cFallbackRedirectsCrossOrigin(308);
    }

    @Test
    void rejectsOneShotEntityAfterFallback307() throws Exception {
        rejectOneShotEntityAfterFallbackRedirect(307);
    }

    @Test
    void rejectsOneShotEntityAfterFallback308() throws Exception {
        rejectOneShotEntityAfterFallbackRedirect(308);
    }

    @Test
    void rejectsH2cFallbackOutputStream307RedirectWithUnprovenEmptyEntityByDefault() throws Exception {
        rejectsH2cFallbackOutputStreamRedirectWithUnprovenEmptyEntityByDefault(307);
    }

    @Test
    void rejectsH2cFallbackOutputStream308RedirectWithUnprovenEmptyEntityByDefault() throws Exception {
        rejectsH2cFallbackOutputStreamRedirectWithUnprovenEmptyEntityByDefault(308);
    }

    @Test
    void followsH2cFallbackOutputStream302RedirectWithMaxRedirectsOne() throws Exception {
        try (RedirectingHttp1Server firstHop = new RedirectingHttp1Server(302)) {
            Http2Client client = newClient(firstHop.port(), true, true, null, false);
            try (Http2ClientResponse response = client.put("/token")
                         .maxRedirects(1)
                         .outputStream(OutputStream::close)) {
                assertThat(response.status(), is(Status.OK_200));
            } finally {
                client.closeResource();
            }
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
        assertThat(CROSS_ORIGIN_PROTOCOL_CAPTURE.get(), is("2.0"));
    }

    @Test
    void h2cFallbackOutputStreamRedirectHonorsRequestExpectContinueOverride() throws Exception {
        followsH2cFallbackOutputStreamRedirectWithEntityWhenEnabled(307, true, false, false);
    }

    @Test
    void doesNotSendOutputStreamBodyAfterRedirectTargetFinalResponse() {
        doesNotSendOutputStreamBodyAfterRedirectTargetFinalResponse(307);
        doesNotSendOutputStreamBodyAfterRedirectTargetFinalResponse(308);
    }

    private static void doesNotSendOutputStreamBodyAfterRedirectTargetFinalResponse(int redirectStatus) {
        AtomicInteger dataFrames = new AtomicInteger();
        MockHttp2Server finalResponseServer = MockHttp2Server.builder()
                .onHeaders((ctx, streamId, headers, unused, encoder) -> {
                    Http2Headers responseHeaders =
                            new DefaultHttp2Headers().status(HttpResponseStatus.OK.codeAsText())
                                    .add("content-length", "2");
                    encoder.writeHeaders(ctx, streamId, responseHeaders, 0, false, ctx.newPromise());
                    encoder.writeData(ctx,
                                      streamId,
                                      Unpooled.wrappedBuffer("OK".getBytes(StandardCharsets.UTF_8)),
                                      0,
                                      true,
                                      ctx.newPromise());
                    ctx.flush();
                })
                .onData((ctx, streamId, data, padding, endOfStream, encoder) -> {
                    dataFrames.incrementAndGet();
                    return data.readableBytes() + padding;
                })
                .build();
        WebServer firstHop = WebServer.builder()
                .host("127.0.0.1")
                .port(-1)
                .routing(rules -> rules.put("/redirect/final-response", (req, res) -> {
                    res.status(Status.create(redirectStatus))
                            .header(HeaderNames.LOCATION,
                                    "http://localhost:" + finalResponseServer.port() + "/final-response")
                            .send();
                }))
                .build()
                .start();
        Http2Client client = newClient(firstHop.port(), true, true, true, true, true);
        try (Http2ClientResponse response = client.put("/redirect/final-response")
                .maxRedirects(1)
                .outputStream(it -> {
                    it.write(requestBodyBytes());
                    it.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("OK"));
        } finally {
            client.closeResource();
            firstHop.stop();
            finalResponseServer.shutdown();
        }

        assertThat(dataFrames.get(), is(0));
    }

    private static void rejectOutputStreamEntityWhenH2cUpgradeRedirectsCrossOrigin(String redirectPath) {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                       () -> newClient(true, true, null, false)
                                                               .put(redirectPath)
                                                               .outputStream(it -> {
                                                                   it.write(requestBodyBytes());
                                                                   it.close();
                                                               }));
        assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));
        assertThat(CROSS_ORIGIN_CAPTURE.get(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    private static void rejectOutputStreamEntityWhenH2cFallbackRedirectsCrossOrigin(int redirectStatus) throws Exception {
        try (RedirectingHttp1Server firstHop = new RedirectingHttp1Server(redirectStatus)) {
            Http2Client client = newClient(firstHop.port(), true, true, null, false);
            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> client.put("/token")
                                                                       .outputStream(it -> {
                                                                           it.write(requestBodyBytes());
                                                                           it.close();
                                                                       }));
                assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));
            } finally {
                client.closeResource();
            }
        }
        assertThat(CROSS_ORIGIN_CAPTURE.get(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    private static void rejectOneShotEntityAfterFallbackRedirect(int redirectStatus) throws Exception {
        AtomicInteger invocations = new AtomicInteger();
        try (RedirectingHttp1Server firstHop = new RedirectingHttp1Server(redirectStatus, true)) {
            Http2Client client = newClient(firstHop.port(), true, true, null, false);
            try {
                // Prime the connection so the redirected request uses the already-established HTTP/1 fallback path.
                try (Http2ClientResponse response = client.get("/prime-http1").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> client.put("/token")
                                                                       .sendExpectContinue(false)
                                                                       .outputStream(it -> {
                                                                           if (invocations.getAndIncrement() == 0) {
                                                                               it.write(requestBodyBytes());
                                                                           }
                                                                           it.close();
                                                                       }));
                assertThat(exception.getMessage(),
                           containsString("Cannot replay a one-shot request body after redirect status "
                                                  + redirectStatus));
            } finally {
                client.closeResource();
            }
        }
        assertThat(invocations.get(), is(1));
        assertThat(CROSS_ORIGIN_CAPTURE.get(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    private static void rejectsH2cFallbackOutputStreamRedirectWithUnprovenEmptyEntityByDefault(int redirectStatus)
            throws Exception {
        try (RedirectingHttp1Server firstHop = new RedirectingHttp1Server(redirectStatus)) {
            Http2Client client = newClient(firstHop.port(), true, true, null, false);
            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> client.put("/token")
                                                                       .maxRedirects(1)
                                                                       .outputStream(OutputStream::close));
                assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));
            } finally {
                client.closeResource();
            }
        }

        assertThat(CROSS_ORIGIN_CAPTURE.get(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    private static void followsH2cFallbackOutputStreamRedirectWithEntityWhenEnabled(int redirectStatus,
                                                                                   boolean clientSendExpectContinue,
                                                                                   Boolean requestSendExpectContinue,
                                                                                   Boolean expectedExpectHeader)
            throws Exception {
        try (RedirectingHttp1Server firstHop = new RedirectingHttp1Server(redirectStatus)) {
            Http2Client client = newClient(firstHop.port(), true, true, true, false, clientSendExpectContinue);
            var request = client.put("/token");
            request.maxRedirects(1);
            if (requestSendExpectContinue != null) {
                request.sendExpectContinue(requestSendExpectContinue);
            }
            try (Http2ClientResponse response = request.outputStream(it -> {
                it.write(requestBodyBytes());
                it.close();
            })) {
                assertThat(response.status(), is(Status.OK_200));
            } finally {
                client.closeResource();
            }
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(REQUEST_BODY));
        assertThat(CROSS_ORIGIN_PROTOCOL_CAPTURE.get(), is("2.0"));
        if (expectedExpectHeader != null) {
            assertThat(CROSS_ORIGIN_EXPECT_CAPTURE.get(), is(expectedExpectHeader));
        }
    }

    @Test
    void rejectsOutputStreamEntityOnCrossOrigin307Redirect() {
        rejectOutputStreamEntityOnCrossOriginRedirect("/redirect/cross-origin-keep-method");
    }

    @Test
    void rejectsOutputStreamEntityOnCrossOrigin308Redirect() {
        rejectOutputStreamEntityOnCrossOriginRedirect("/redirect/cross-origin-keep-method-308");
    }

    @Test
    void rejectsAlreadySentOutputStreamEntityOnCrossOrigin307Redirect() {
        rejectAlreadySentOutputStreamEntityOnCrossOriginRedirect("/redirect/cross-origin-keep-method");
    }

    @Test
    void rejectsAlreadySentOutputStreamEntityOnCrossOrigin308Redirect() {
        rejectAlreadySentOutputStreamEntityOnCrossOriginRedirect("/redirect/cross-origin-keep-method-308");
    }

    @Test
    void followsOutputStream307RedirectWithZeroLengthWriteByDefault() {
        followsOutputStreamRedirectWithZeroLengthWriteByDefault("/redirect/cross-origin-keep-method");
    }

    @Test
    void followsOutputStream308RedirectWithZeroLengthWriteByDefault() {
        followsOutputStreamRedirectWithZeroLengthWriteByDefault("/redirect/cross-origin-keep-method-308");
    }

    @Test
    void followsOutputStream302RedirectWithMaxRedirectsOne() {
        try (Http2ClientResponse response = newClient(true, true, null, true, false)
                .put("/redirect/cross-origin-change-method")
                .maxRedirects(1)
                .sendExpectContinue(false)
                .outputStream(it -> {
                    it.write(requestBodyBytes());
                    it.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    @Test
    void followsExpectContinueOutputStream307RedirectWithEntityWhenEnabled() {
        followsExpectContinueOutputStreamRedirectWithEntityWhenEnabled("/redirect/cross-origin-keep-method");
    }

    @Test
    void followsExpectContinueOutputStream308RedirectWithEntityWhenEnabled() {
        followsExpectContinueOutputStreamRedirectWithEntityWhenEnabled("/redirect/cross-origin-keep-method-308");
    }

    @Test
    void followsCrossOrigin307WithEmptyStringEntityByDefault() {
        followsCrossOriginRedirectWithEmptyStringEntityByDefault("/redirect/cross-origin-keep-method");
    }

    @Test
    void followsCrossOrigin308WithEmptyStringEntityByDefault() {
        followsCrossOriginRedirectWithEmptyStringEntityByDefault("/redirect/cross-origin-keep-method-308");
    }

    private static void followsCrossOriginRedirectWithEmptyStringEntityByDefault(String redirectPath) {
        try (Http2ClientResponse response = newClient(true, true)
                .put(redirectPath)
                .submit("")) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(""));
    }

    @Test
    void followsCrossOrigin307WithEntityWhenEnabled() {
        followsCrossOriginRedirectWithEntityWhenEnabled("/redirect/cross-origin-keep-method");
    }

    @Test
    void followsCrossOrigin308WithEntityWhenEnabled() {
        followsCrossOriginRedirectWithEntityWhenEnabled("/redirect/cross-origin-keep-method-308");
    }

    @Test
    void redirectRegeneratesAuthorityForTarget() {
        try (Http2ClientResponse response = newClient()
                .put("/redirect/cross-origin-keep-method")
                .header(io.helidon.http.http2.Http2Headers.AUTHORITY_NAME, "stale.example")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(CROSS_ORIGIN_AUTHORITY_CAPTURE.get(), is("localhost:" + redirectTargetServer.port()));
    }

    private static MediaContext writerMediaContext(boolean overrideAuthority) {
        return writerMediaContext(overrideAuthority, true);
    }

    private static MediaContext writerMediaContext(boolean overrideAuthority, boolean supportsInstanceWriter) {
        EntityWriter<WriterEntity> writer = new EntityWriter<>() {
            @Override
            public boolean supportsInstanceWriter() {
                return supportsInstanceWriter;
            }

            @Override
            public InstanceWriter instanceWriter(GenericType<WriterEntity> type,
                                                   WriterEntity object,
                                                   WritableHeaders<?> requestHeaders) {
                if (!supportsInstanceWriter) {
                    throw new AssertionError("Instance writer must not be requested");
                }
                prepareHeaders(requestHeaders);
                byte[] bytes = object.value().getBytes(StandardCharsets.UTF_8);
                return new InstanceWriter() {
                    @Override
                    public OptionalLong contentLength() {
                        return OptionalLong.of(bytes.length);
                    }

                    @Override
                    public boolean alwaysInMemory() {
                        return true;
                    }

                    @Override
                    public void write(OutputStream stream) {
                        throw new AssertionError("Always-in-memory writer must not stream");
                    }

                    @Override
                    public byte[] instanceBytes() {
                        return bytes;
                    }
                };
            }

            @Override
            public void write(GenericType<WriterEntity> type,
                              WriterEntity object,
                              OutputStream outputStream,
                              Headers requestHeaders,
                              WritableHeaders<?> responseHeaders) {
                throw new AssertionError("Server writer must not be used");
            }

            @Override
            public void write(GenericType<WriterEntity> type,
                              WriterEntity object,
                              OutputStream outputStream,
                              WritableHeaders<?> requestHeaders) {
                if (!supportsInstanceWriter) {
                    prepareHeaders(requestHeaders);
                }
                try (outputStream) {
                    outputStream.write(object.value().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            private void prepareHeaders(WritableHeaders<?> requestHeaders) {
                requestHeaders.set(HeaderNames.AUTHORIZATION, "writer-secret");
                if (overrideAuthority) {
                    requestHeaders.set(HeaderNames.HOST, WRITER_AUTHORITY);
                }
                requestHeaders.add(HeaderNames.COOKIE, "writer-cookie=secret");
                requestHeaders.set(WRITER_HEADER, "preserved");
                requestHeaders.set(WRITER_OVERRIDE_HEADER, "writer-final");
                requestHeaders.set(HeaderNames.CONTENT_TYPE, "text/plain");
            }
        };
        MediaSupport support = new MediaSupport() {
            @Override
            public String name() {
                return "writer-header-test";
            }

            @Override
            public String type() {
                return "writer-header-test";
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
                return new WriterResponse<>(SupportLevel.SUPPORTED, () -> (EntityWriter<T>) writer);
            }
        };
        return MediaContext.builder()
                .registerDefaults(true)
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(support)
                .build();
    }

    private static Http2Client newClient() {
        return newClient(false, true);
    }

    private static Http2Client newClient(boolean stripApiKeyOnRedirect, boolean filterRedirectHeaders) {
        return newClient(stripApiKeyOnRedirect, filterRedirectHeaders, null);
    }

    private static Http2Client newClient(boolean stripApiKeyOnRedirect,
                                         boolean filterRedirectHeaders,
                                         boolean followCrossOriginEntityRedirects) {
        return newClient(stripApiKeyOnRedirect,
                         filterRedirectHeaders,
                         Boolean.valueOf(followCrossOriginEntityRedirects),
                         true);
    }

    private static Http2Client newClient(boolean stripApiKeyOnRedirect,
                                         boolean filterRedirectHeaders,
                                         Boolean followCrossOriginEntityRedirects) {
        return newClient(stripApiKeyOnRedirect, filterRedirectHeaders, followCrossOriginEntityRedirects, true);
    }

    private static Http2Client newClient(boolean stripApiKeyOnRedirect,
                                         boolean filterRedirectHeaders,
                                         Boolean followCrossOriginEntityRedirects,
                                         boolean priorKnowledge) {
        return newClient(stripApiKeyOnRedirect,
                         filterRedirectHeaders,
                         followCrossOriginEntityRedirects,
                         priorKnowledge,
                         false);
    }

    private static Http2Client newClient(boolean stripApiKeyOnRedirect,
                                         boolean filterRedirectHeaders,
                                         Boolean followCrossOriginEntityRedirects,
                                         boolean priorKnowledge,
                                         boolean sendExpectContinue) {
        return newClient(trustedServer.port(),
                         stripApiKeyOnRedirect,
                         filterRedirectHeaders,
                         followCrossOriginEntityRedirects,
                         priorKnowledge,
                         sendExpectContinue);
    }

    private static Http2Client newClient(int port,
                                         boolean stripApiKeyOnRedirect,
                                         boolean filterRedirectHeaders,
                                         Boolean followCrossOriginEntityRedirects,
                                         boolean priorKnowledge) {
        return newClient(port,
                         stripApiKeyOnRedirect,
                         filterRedirectHeaders,
                         followCrossOriginEntityRedirects,
                         priorKnowledge,
                         false);
    }

    private static Http2Client newClient(int port,
                                         boolean stripApiKeyOnRedirect,
                                         boolean filterRedirectHeaders,
                                         Boolean followCrossOriginEntityRedirects,
                                         boolean priorKnowledge,
                                         boolean sendExpectContinue) {
        var builder = Http2Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + port)
                .protocolConfig(it -> it.priorKnowledge(priorKnowledge))
                .shareConnectionCache(false)
                .addHeader(API_KEY_HEADER, API_KEY)
                .addService(new AuthorizationService())
                .filterRedirectHeaders(filterRedirectHeaders);
        if (sendExpectContinue) {
            builder.sendExpectContinue(true);
        }
        if (followCrossOriginEntityRedirects != null) {
            builder.followCrossOriginEntityRedirects(followCrossOriginEntityRedirects);
        }
        if (stripApiKeyOnRedirect) {
            builder.addRedirectSensitiveHeader(API_KEY_HEADER_CONFIG_NAME);
        }
        return builder.build();
    }

    private static byte[] requestBodyBytes() {
        return REQUEST_BODY.getBytes(StandardCharsets.UTF_8);
    }

    private static void rejectBufferedEntityOnCrossOriginRedirect(String redirectPath) {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                       () -> newClient(true, true)
                                                               .put(redirectPath)
                                                               .submit(requestBodyBytes()));
        assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));
        assertThat(CROSS_ORIGIN_CAPTURE.get(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    private static void rejectOutputStreamEntityOnCrossOriginRedirect(String redirectPath) {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                       () -> newClient(true, true)
                                                               .put(redirectPath)
                                                               .outputStream(it -> {
                                                                   it.write(requestBodyBytes());
                                                                   it.close();
                                                               }));
        assertThat(exception.getMessage(), is(BLOCKED_REDIRECT_MESSAGE));
        assertThat(CROSS_ORIGIN_CAPTURE.get(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    private static void rejectAlreadySentOutputStreamEntityOnCrossOriginRedirect(String redirectPath) {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                       () -> newClient(true, true)
                                                               .put(redirectPath)
                                                               .sendExpectContinue(false)
                                                               .outputStream(it -> {
                                                                   it.write(requestBodyBytes());
                                                                   it.close();
                                                               }));
        assertThat(exception.getMessage(),
                   containsString("HTTP/2 cannot replay a one-shot request entity after it was sent"));
        assertThat(CROSS_ORIGIN_CAPTURE.get(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(nullValue()));
    }

    private static void followsOutputStreamRedirectWithZeroLengthWriteByDefault(String redirectPath) {
        try (Http2ClientResponse response = newClient(true, true, null, true, true)
                .put(redirectPath)
                .maxRedirects(1)
                .outputStream(it -> {
                    it.write(new byte[0]);
                    it.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(""));
    }

    private static void followsExpectContinueOutputStreamRedirectWithEntityWhenEnabled(String redirectPath) {
        try (Http2ClientResponse response = newClient(true, true, true, true, true)
                .put(redirectPath)
                .maxRedirects(1)
                .outputStream(it -> {
                    it.write(requestBodyBytes());
                    it.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(REQUEST_BODY));
        assertThat(CROSS_ORIGIN_EXPECT_CAPTURE.get(), is(true));
    }

    private static void followsCrossOriginRedirectWithEntityWhenEnabled(String redirectPath) {
        try (Http2ClientResponse response = newClient(true, true, true)
                .put(redirectPath)
                .submit(requestBodyBytes())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        CapturedHeaders captured = CROSS_ORIGIN_CAPTURE.get();
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(CROSS_ORIGIN_BODY_CAPTURE.get(), is(REQUEST_BODY));
    }

    private record AuthorizationService() implements WebClientService {
        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            request.headers().set(HeaderNames.AUTHORIZATION, AUTHORIZATION);
            request.headers().set(HeaderNames.PROXY_AUTHORIZATION, PROXY_AUTHORIZATION);
            return chain.proceed(request);
        }
    }

    private static CapturedHeaders capturedHeaders(ServerRequest request) {
        return new CapturedHeaders(request.headers().first(HeaderNames.AUTHORIZATION).orElse(null),
                                   request.headers().first(HeaderNames.PROXY_AUTHORIZATION).orElse(null),
                                   request.headers().first(API_KEY_HEADER).orElse(null));
    }

    private record WriterDefaultService() implements WebClientService {
        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            String path = request.uri().toUri().getPath();
            String defaultValue = path.equals("/redirect/writer-preserve") ? "source-default" : "final-default";
            request.headers().setIfAbsent(HeaderValues.create(WRITER_OVERRIDE_HEADER, defaultValue));
            return chain.proceed(request);
        }
    }

    private record CapturedHeaders(String authorization, String proxyAuthorization, String apiKey) {
    }

    private static final class RedirectingHttp1Server implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        private RedirectingHttp1Server(int redirectStatus) throws Exception {
            this(redirectStatus, false);
        }

        private RedirectingHttp1Server(int redirectStatus, boolean primeHttp1Fallback) throws Exception {
            this(redirectStatus, primeHttp1Fallback, 1);
        }

        private RedirectingHttp1Server(int redirectStatus,
                                       boolean primeHttp1Fallback,
                                       int redirectCount) throws Exception {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            thread = new Thread(() -> serve(redirectStatus, primeHttp1Fallback, redirectCount),
                                "h2c-fallback-redirect-" + serverSocket.getLocalPort());
            thread.setDaemon(true);
            thread.start();
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private void serve(int redirectStatus, boolean primeHttp1Fallback, int redirectCount) {
            try {
                if (primeHttp1Fallback) {
                    serveOkUpgradeFallback();
                }
                for (int i = 0; i < redirectCount; i++) {
                    serveRedirect(redirectStatus, !primeHttp1Fallback);
                }
                done.complete(null);
            } catch (SocketException e) {
                if (!serverSocket.isClosed()) {
                    done.completeExceptionally(e);
                }
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        }

        private void serveOkUpgradeFallback() throws Exception {
            try (Socket socket = serverSocket.accept()) {
                String requestHeaders = readHeaders(socket.getInputStream()).toLowerCase(Locale.ROOT);
                assertThat(requestHeaders.contains("\r\nupgrade: h2c\r\n"), is(true));
                assertThat(requestHeaders.contains("\r\nhttp2-settings:"), is(true));
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\n"
                                                        + "Content-Length: 0\r\n"
                                                        + "Connection: close\r\n"
                                                        + "\r\n")
                                                       .getBytes(StandardCharsets.US_ASCII));
            }
        }

        private void serveRedirect(int redirectStatus, boolean expectUpgrade) throws Exception {
            try (Socket socket = serverSocket.accept()) {
                String requestHeaders = readHeaders(socket.getInputStream()).toLowerCase(Locale.ROOT);
                assertThat(requestHeaders.contains("\r\nupgrade: h2c\r\n"), is(expectUpgrade));
                if (expectUpgrade) {
                    assertThat(requestHeaders.contains("\r\nhttp2-settings:"), is(true));
                }
                String capturePath = redirectStatus == 302 ? "/capture" : "/capture-body";
                socket.getOutputStream().write(("HTTP/1.1 " + redirectStatus + " "
                                                        + redirectReasonPhrase(redirectStatus) + "\r\n"
                                                        + "Location: http://localhost:" + redirectTargetServer.port()
                                                        + capturePath + "\r\n"
                                                        + "Content-Length: 0\r\n"
                                                        + "Connection: close\r\n"
                                                        + "\r\n")
                                                       .getBytes(StandardCharsets.US_ASCII));
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            done.get(5, TimeUnit.SECONDS);
        }

        private static String readHeaders(InputStream inputStream) throws Exception {
            StringBuilder headers = new StringBuilder();
            int previous3 = -1;
            int previous2 = -1;
            int previous1 = -1;
            int current;
            while ((current = inputStream.read()) != -1) {
                headers.append((char) current);
                if (previous3 == '\r' && previous2 == '\n' && previous1 == '\r' && current == '\n') {
                    return headers.toString();
                }
                previous3 = previous2;
                previous2 = previous1;
                previous1 = current;
            }
            return headers.toString();
        }

        private static String redirectReasonPhrase(int redirectStatus) {
            return switch (redirectStatus) {
                case 302 -> "Found";
                case 307 -> "Temporary Redirect";
                case 308 -> "Permanent Redirect";
                default -> throw new IllegalArgumentException("Unexpected redirect status: " + redirectStatus);
            };
        }
    }

    private record WriterEntity(String value) {
    }

    private record CapturedWriterHeaders(String authorization,
                                         String cookie,
                                         String writerMetadata,
                                         String contentType,
                                         String body) {
    }

    private record CapturedWriterFinalHeaders(String host, String cookie, String body) {
    }
}
