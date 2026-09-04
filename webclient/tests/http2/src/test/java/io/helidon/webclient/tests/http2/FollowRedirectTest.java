/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.GenericType;
import io.helidon.http.ClientResponseHeaders;
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
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Status.INTERNAL_SERVER_ERROR_500;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class FollowRedirectTest {
    private static final StringBuilder BUFFER = new StringBuilder();
    private static final String PATH_COOKIE = "pathOnly=redirect-secret";
    private static final String QUERY_ACCEPT = "application/json";
    private static final String QUERY_CONTENT_TYPE = "application/sql";
    private static final String QUERY_ENTITY = "select * from example";
    private static final String QUERY_LANGUAGE = "en";
    private static final HeaderName REDIRECT_HEADER = HeaderNames.create("X-Redirect-Test");
    private static final AtomicReference<String> REDIRECT_SOURCE_COOKIE = new AtomicReference<>();
    private static final AtomicReference<String> REDIRECT_TARGET_COOKIE = new AtomicReference<>();
    private static final AtomicInteger ENTITY_TARGET_REQUESTS = new AtomicInteger();
    private static final AtomicReference<String> STRING_TARGET_CONTENT_TYPE = new AtomicReference<>();
    private static volatile CountDownLatch lifecycleUploadReceived = new CountDownLatch(0);
    private static volatile CountDownLatch lifecycleResponseRelease = new CountDownLatch(0);
    private static WebServer http1Target;
    private final URI uri;
    private final Http2Client webClient;

    FollowRedirectTest(URI uri) {
        this.uri = uri;
        this.webClient = Http2Client.builder()
                .baseUri(uri)
                .cookieManager(WebClientCookieManager.builder().automaticStoreEnabled(true).build())
                .build();
    }

    @BeforeAll
    static void startHttp1Target() {
        http1Target = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.create())
                .protocolsDiscoverServices(false)
                .addProtocol(Http1Config.create())
                .routing(rules -> rules.get("/fallback/probe", (req, res) -> res.send("ready"))
                        .put("/fallback/target", (req, res) -> res.send(req.content().as(String.class))))
                .build()
                .start();
    }

    @AfterAll
    static void stopHttp1Target() {
        if (http1Target != null) {
            http1Target.stop();
        }
    }

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.route(Method.QUERY,
                     "/queryRedirect301",
                     (_, res) -> queryRedirect(res, Status.MOVED_PERMANENTLY_301, "/queryRedirectTarget"))
                .route(Method.QUERY,
                       "/queryRedirect302",
                       (_, res) -> queryRedirect(res, Status.FOUND_302, "/queryRedirectTarget"))
                .route(Method.QUERY,
                       "/queryRedirect303",
                       (_, res) -> queryRedirect(res, Status.SEE_OTHER_303, "/queryRedirectGetTarget"))
                .route(Method.QUERY,
                       "/queryRedirectHeaders301",
                       (_, res) -> queryRedirect(res, Status.MOVED_PERMANENTLY_301, "/queryRedirectHeadersTarget"))
                .route(Method.QUERY,
                       "/queryRedirectHeaders302",
                       (_, res) -> queryRedirect(res, Status.FOUND_302, "/queryRedirectHeadersTarget"))
                .route(Method.QUERY, "/queryRedirectTarget", FollowRedirectTest::queryRedirectTarget)
                .route(Method.QUERY, "/queryRedirectHeadersTarget", FollowRedirectTest::queryRedirectHeadersTarget)
                .get("/queryRedirectGetTarget", FollowRedirectTest::queryRedirectGetTarget)
                .route(Method.PUT, "/infiniteRedirect", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/infiniteRedirect2")
                    .send();
        }).route(Method.PUT, "/infiniteRedirect2", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/infiniteRedirect")
                    .send();
        }).route(Method.PUT, "/redirect", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/plain")
                    .send();
        }).route(Method.PUT, "/redirectKeepMethodThenGet", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/redirectNoEntityAfterKeepMethod")
                    .send();
        }).route(Method.PUT, "/redirectKeepMethodThenRedirectAfterUpload", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/redirectAfterUploadDelayed")
                    .send();
        }).route(Method.PUT, "/redirectNoEntity", (req, res) -> {
            res.status(Status.FOUND_302)
                    .header(HeaderNames.LOCATION, "/plain")
                    .send();
        }).route(Method.PUT, "/redirectNoEntityAfterKeepMethod", (req, res) -> {
            res.status(Status.FOUND_302)
                    .header(HeaderNames.LOCATION, "/delayedPlain")
                    .send();
        }).route(Method.PUT, "/redirectAfterUploadDelayed", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                while (in.read(buffer) > 0) {
                    // Do nothing and just drain the entity.
                }
                res.status(Status.SEE_OTHER_303)
                        .header(HeaderNames.LOCATION, "/delayedPlain")
                        .send();
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Method.GET, "/source/prime", (req, res) -> {
            res.header(HeaderNames.SET_COOKIE, PATH_COOKIE + "; Path=/source")
                    .send();
        }).route(Method.PUT, "/source/bounce", (req, res) -> {
            REDIRECT_SOURCE_COOKIE.set(req.headers().contains(HeaderNames.COOKIE)
                                               ? req.headers().get(HeaderNames.COOKIE).values()
                                               : null);
            res.status(Status.create(308, "Custom Permanent Redirect"))
                    .header(HeaderNames.LOCATION, "/target/collect")
                    .send();
        }).route(Method.PUT, "/target/collect", (req, res) -> {
            REDIRECT_TARGET_COOKIE.set(req.headers().contains(HeaderNames.COOKIE)
                                               ? req.headers().get(HeaderNames.COOKIE).values()
                                               : null);
            if (!req.headers().first(REDIRECT_HEADER).orElse("").equals("preserve")) {
                res.status(Status.BAD_REQUEST_400).send("Custom header was not preserved");
                return;
            }
            String contentType = req.headers().contentType().orElseThrow().mediaType().text();
            res.send(contentType + ":" + req.content().as(String.class));
        }).route(Method.GET, "/redirectDropEntity", (req, res) -> {
            res.status(Status.FOUND_302)
                    .header(HeaderNames.LOCATION, "/afterDropEntity")
                    .send();
        }).route(Method.GET, "/afterDropEntity", (req, res) -> {
            if (req.content().hasEntity()
                    || req.headers().contains(HeaderNames.CONTENT_TYPE)) {
                res.status(Status.BAD_REQUEST_400).send("Entity metadata was preserved");
                return;
            }
            if (!req.headers().first(REDIRECT_HEADER).orElse("").equals("preserve")) {
                res.status(Status.BAD_REQUEST_400).send("Custom header was not preserved");
                return;
            }
            res.send("GET without entity metadata");
        }).route(Method.PUT, "/plain", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    BUFFER.append("\n").append(new String(buffer, 0, read));
                }
                res.send("Test data:" + BUFFER);
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Method.PUT, "/redirectAfterUpload", (req, res) -> {
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    BUFFER.append("\n").append(new String(buffer, 0, read));
                }
                res.status(Status.SEE_OTHER_303)
                        .header(HeaderNames.LOCATION, "/afterUpload")
                        .send();
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Method.PUT, "/cleanup/start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/cleanup/after-upload")
                .send()
        ).route(Method.PUT, "/cleanup/after-upload", (req, res) -> {
            req.content().as(String.class);
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/plain")
                    .send();
        }).route(Method.GET, "/afterUpload", (req, res) -> {
            res.send("Upload completed!" + BUFFER);
        }).route(Method.GET, "/plain", (req, res) -> {
            res.send("GET plain endpoint reached");
        }).route(Method.GET, "/delayedPlain", (req, res) -> {
            TimeUnit.MILLISECONDS.sleep(250);
            res.send("GET delayed endpoint reached");
        }).route(Method.PUT, "/close", (req, res) -> {
            byte[] buffer = new byte[10];
            try (InputStream in = req.content().inputStream()) {
                in.read(buffer);
                throw new RuntimeException("BOOM!");
            } catch (IOException e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Method.PUT, "/wait", (req, res) -> {
            TimeUnit.MILLISECONDS.sleep(500);
            try (InputStream in = req.content().inputStream()) {
                byte[] buffer = new byte[128];
                while (in.read(buffer) > 0) {
                    //Do nothing and just drain the entity
                }
                res.send("Request did not timeout");
            } catch (Exception e) {
                res.status(INTERNAL_SERVER_ERROR_500)
                        .send(e.getMessage());
            }
        }).route(Method.PUT, "/lifecycle/start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/lifecycle/hop")
                .send()
        ).route(Method.PUT, "/lifecycle/hop", (req, res) -> {
            req.content().as(String.class);
            res.status(Status.PERMANENT_REDIRECT_308)
                    .header(HeaderNames.LOCATION, "/lifecycle/final")
                    .send();
        }).route(Method.PUT, "/lifecycle/final", (req, res) -> {
            String entity = req.content().as(String.class);
            lifecycleUploadReceived.countDown();
            if (!lifecycleResponseRelease.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release lifecycle response");
            }
            res.send(entity);
        }).route(Method.PUT, "/lifecycle/fail-start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/lifecycle/fail")
                .send()
        ).route(Method.GET, "/redirect/ftp", (req, res) -> res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "ftp://example.com/file")
                .send()
        ).route(Method.PUT, "/entity/expect-start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/entity/target")
                .send()
        ).route(Method.PUT, "/entity/post-start", (req, res) -> {
            req.content().as(String.class);
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/entity/target")
                    .send();
        }).route(Method.PUT, "/entity/target", (req, res) -> {
            ENTITY_TARGET_REQUESTS.incrementAndGet();
            res.send(req.content().as(String.class));
        }).route(Method.PUT, "/entity/string-start", (req, res) -> {
            req.content().as(String.class);
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, "/entity/string-target")
                    .send();
        }).route(Method.PUT, "/entity/string-target", (req, res) -> {
            STRING_TARGET_CONTENT_TYPE.set(req.headers().first(HeaderNames.CONTENT_TYPE).orElse(null));
            res.send(req.content().as(String.class));
        }).route(Method.PUT, "/fallback/start", (req, res) -> {
            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION,
                            "http://127.0.0.1:" + http1Target.port() + "/fallback/target")
                    .send();
        }).route(Method.PUT, "/cookie/start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/cookie/intermediate")
                .send()
        ).route(Method.PUT, "/cookie/intermediate", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/cookie/final")
                .header(HeaderNames.SET_COOKIE, "intermediate=kept; Path=/")
                .send()
        ).route(Method.PUT, "/cookie/final", (req, res) -> {
            req.content().as(String.class);
            res.header(HeaderNames.SET_COOKIE, "final=blocked; Path=/")
                    .send("done");
        }).route(Method.PUT, "/cookie/loop-start", (req, res) -> res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "/cookie/loop-intermediate")
                .send()
        ).route(Method.GET, "/cookie/loop-intermediate", (req, res) -> res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "/cookie/loop-final")
                .header(HeaderNames.SET_COOKIE, "loop=kept; Path=/")
                .send()
        ).route(Method.GET, "/cookie/loop-final", (req, res) -> res.send("loop-done")
        ).route(Method.GET, "/cookie/echo", (req, res) -> res.send(
                req.headers().contains(HeaderNames.COOKIE)
                        ? String.join("; ", req.headers().get(HeaderNames.COOKIE).allValues())
                        : "none")
        );
    }

    @AfterEach
    void clearBuffer() {
        BUFFER.setLength(0);
        REDIRECT_SOURCE_COOKIE.set(null);
        REDIRECT_TARGET_COOKIE.set(null);
    }

    @Test
    void testOutputStreamFollowRedirect() {
        String expected = """
                Test data:
                0123456789
                0123456789
                0123456789""";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirect")
                .readContinueTimeout(Duration.ofMillis(200))
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void testOutputStreamEntityNotKept() {
        String expected = "GET plain endpoint reached";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirectNoEntity")
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void queryRedirectPreservesBufferedEntity() {
        for (int redirectStatus : new int[] {301, 302}) {
            try (Http2ClientResponse response = webClient.method(Method.QUERY)
                    .uri("/queryRedirect" + redirectStatus)
                    .header(HeaderNames.CONTENT_TYPE, QUERY_CONTENT_TYPE)
                    .submit(QUERY_ENTITY)) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.as(String.class), is(Method.QUERY + ":" + QUERY_CONTENT_TYPE + ":" + QUERY_ENTITY));
            }
        }
    }

    @Test
    void queryRedirectPreservesRequestHeaders() {
        for (int redirectStatus : new int[] {301, 302}) {
            try (Http2ClientResponse response = webClient.method(Method.QUERY)
                    .uri("/queryRedirectHeaders" + redirectStatus)
                    .header(HeaderNames.ACCEPT, QUERY_ACCEPT)
                    .header(HeaderNames.CONTENT_LANGUAGE, QUERY_LANGUAGE)
                    .header(HeaderNames.CONTENT_TYPE, QUERY_CONTENT_TYPE)
                    .submit(QUERY_ENTITY)) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.as(String.class),
                           is(Method.QUERY + ":" + QUERY_ACCEPT + ":" + QUERY_LANGUAGE + ":" + QUERY_ENTITY));
            }
        }
    }

    @Test
    void querySubmitUsesMediaWriterContentType() {
        try (Http2ClientResponse response = webClient.method(Method.QUERY)
                .uri("/queryRedirectTarget")
                .submit(QUERY_ENTITY)) {
            String result = response.as(String.class);
            assertThat(result, containsString(Method.QUERY + ":text/plain"));
            assertThat(result, containsString(":" + QUERY_ENTITY));
        }
    }

    @Test
    void querySubmitRejectsMissingContentType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                           () -> webClient.method(Method.QUERY)
                                                                   .uri("/queryRedirectTarget")
                                                                   .submit(QUERY_ENTITY.getBytes(StandardCharsets.UTF_8)));

        assertThat(exception.getMessage(), containsString("Content-Type header is required"));
    }

    @Test
    void queryRedirectPreservesOutputStreamEntity() {
        for (int redirectStatus : new int[] {301, 302}) {
            try (Http2ClientResponse response = webClient.method(Method.QUERY)
                    .uri("/queryRedirect" + redirectStatus)
                    .header(HeaderNames.CONTENT_TYPE, QUERY_CONTENT_TYPE)
                    .outputStream(output -> {
                        output.write(QUERY_ENTITY.getBytes(StandardCharsets.UTF_8));
                        output.close();
                    })) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.as(String.class), is(Method.QUERY + ":" + QUERY_CONTENT_TYPE + ":" + QUERY_ENTITY));
            }
        }
    }

    @Test
    void querySeeOtherRedirectChangesToGetAndDropsEntity() {
        try (Http2ClientResponse response = webClient.method(Method.QUERY)
                .uri("/queryRedirect303")
                .header(HeaderNames.CONTENT_TYPE, QUERY_CONTENT_TYPE)
                .submit(QUERY_ENTITY)) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("GET without entity metadata"));
        }
    }

    @Test
    void queryRedirectCompletesEveryServiceRequest() {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        Http2Client client = Http2Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .addService((chain, request) -> {
                    requests.incrementAndGet();
                    request.whenComplete().thenRun(completions::incrementAndGet);
                    return chain.proceed(request);
                })
                .build();
        try {
            Http2ClientResponse response = client.method(Method.QUERY)
                    .uri("/queryRedirect302")
                    .header(HeaderNames.CONTENT_TYPE, QUERY_CONTENT_TYPE)
                    .submit(QUERY_ENTITY);
            assertThat(requests.get(), is(2));
            assertThat(completions.get(), is(1));
            response.close();
            assertThat(completions.get(), is(2));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testReadTimeoutPreservedAcrossMixedRedirects() {
        String expected = "GET delayed endpoint reached";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirectKeepMethodThenGet")
                .readContinueTimeout(Duration.ofMillis(50))
                .readTimeout(Duration.ofSeconds(1))
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void testReadTimeoutPreservedAcrossRedirectAfterUpload() {
        String expected = "GET delayed endpoint reached";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirectKeepMethodThenRedirectAfterUpload")
                .readContinueTimeout(Duration.ofMillis(50))
                .readTimeout(Duration.ofSeconds(1))
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void methodPreservingRedirectReselectsCookiesForTargetPath() {
        try (Http2ClientResponse response = webClient.get()
                .path("/source/prime")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        try (Http2ClientResponse response = webClient.put()
                .path("/source/bounce")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(REDIRECT_HEADER, "preserve")
                .submit("entity")) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("text/plain:entity"));
        }

        assertThat(REDIRECT_SOURCE_COOKIE.get(), containsString(PATH_COOKIE));
        assertThat(REDIRECT_TARGET_COOKIE.get(), is(nullValue()));
    }

    @Test
    void sameMethodRedirectDropsEntityHeaders() {
        try (Http2ClientResponse response = webClient.get()
                .path("/redirectDropEntity")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .header(REDIRECT_HEADER, "preserve")
                .submit("entity")) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("GET without entity metadata"));
        }
    }

    @Test
    void testEmptyOutputStreamWithRedirectAfter() {
        assertEmptyOutputStreamWithRedirectAfter();
    }

    @Test
    void testEntityThenEmptyOutputStreamWithRedirectAfter() {
        assertEntityOutputStreamWithRedirectAfter();
        clearBuffer();
        assertEmptyOutputStreamWithRedirectAfter();
    }

    @Test
    void testEntityOutputStreamWithRedirectAfter() {
        assertEntityOutputStreamWithRedirectAfter();
    }

    @Test
    void testOutputStreamEntityNotKeptIntercepted() {
        String expected = "GET plain endpoint reached";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirectNoEntity")
                .outputStream(it -> {
                    try {
                        it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                        it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                        it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                        it.close();
                    } catch (Exception ignore) {
                    }
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    @Test
    void chainedPostBodyRedirectFailureReleasesCurrentStream() {
        Http2Client client = Http2Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .build();

        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client.put()
                    .path("/cleanup/start")
                    .outputStream(output -> {
                        output.write("payload".getBytes(StandardCharsets.UTF_8));
                        output.close();
                    }));
            assertThat(failure.getMessage(),
                       is("HTTP/2 cannot replay a one-shot request entity after it was sent; redirect status was 307."));
            try (Http2ClientResponse response = client.get().path("/plain").request()) {
                assertThat(response.entity().as(String.class), is("GET plain endpoint reached"));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testMaxNumberOfRedirections() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> webClient.put()
                .path("/infiniteRedirect")
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                }));
        assertThat(exception.getMessage(), is("Maximum number of request redirections (10) reached."));
    }

    @Test
    void test100ContinueTimeout() {
        // the webclient just starts sending entity (that is the reason for the timeout, for servers that may not send continue)
        ClientResponseTyped<String> http2ClientResponse = webClient.put()
                .path("/wait")
                .readContinueTimeout(Duration.ofMillis(200))
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                }, String.class);

        assertThat(http2ClientResponse.entity(), is("Request did not timeout"));
    }

    @Test
    void servicedOutputStreamRedirectPreservesEveryHopLifecycleOnCallerThread() throws Exception {
        LifecycleRecorder service = new LifecycleRecorder(null);
        Http2Client client = Http2Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .readContinueTimeout(Duration.ofMillis(50))
                .addService(service)
                .build();
        lifecycleUploadReceived = new CountDownLatch(1);
        lifecycleResponseRelease = new CountDownLatch(1);

        try {
            CompletableFuture<Http2ClientResponse> responseFuture = CompletableFuture.supplyAsync(() -> client
                    .put()
                    .path("/lifecycle/start")
                    .outputStream(output -> {
                        output.write("payload".getBytes(StandardCharsets.UTF_8));
                        assertThat(service.requests().containsKey("/lifecycle/hop"), is(false));
                        output.close();
                    }));
            assertThat(lifecycleUploadReceived.await(5, TimeUnit.SECONDS), is(true));
            RequestLifecycle source = service.requests().get("/lifecycle/start");
            RequestLifecycle target = service.requests().get("/lifecycle/final");
            assertThat("source whenSent must complete after the final upload, before response headers",
                       source.whenSent().isDone(),
                       is(true));
            assertThat(target.whenSent().isDone(), is(true));
            assertThat(target.whenComplete().isDone(), is(false));
            assertThat(responseFuture.isDone(), is(false));
            lifecycleResponseRelease.countDown();

            try (Http2ClientResponse response = responseFuture.get(5, TimeUnit.SECONDS)) {
                assertThat(response.entity().as(String.class), is("payload"));
            }

            RequestLifecycle hop = service.requests().get("/lifecycle/hop");
            assertThat(hop.responseStatus(), is(Status.PERMANENT_REDIRECT_308));
            assertThat(target.responseStatus(), is(Status.OK_200));
            assertThat(service.requests().keySet(),
                       is(Set.of("/lifecycle/start", "/lifecycle/hop", "/lifecycle/final")));
            assertThat(service.events().indexOf("/lifecycle/start:sent")
                               < service.events().indexOf("/lifecycle/final:response"),
                       is(true));
            for (RequestLifecycle lifecycle : service.requests().values()) {
                assertThat(lifecycle.thread(), is(source.thread()));
                assertThat(lifecycle.whenSent().isDone(), is(true));
                assertThat(lifecycle.whenComplete().isDone(), is(true));
            }
        } finally {
            lifecycleResponseRelease.countDown();
            lifecycleUploadReceived = new CountDownLatch(0);
            lifecycleResponseRelease = new CountDownLatch(0);
            client.closeResource();
        }
    }

    @Test
    void servicedOutputStreamRedirectPropagatesTargetServiceFailure() {
        LifecycleRecorder service = new LifecycleRecorder("/lifecycle/fail");
        Http2Client client = Http2Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .addService(service)
                .build();

        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client.put()
                    .path("/lifecycle/fail-start")
                    .outputStream(output -> {
                        output.write("payload".getBytes(StandardCharsets.UTF_8));
                        output.close();
                    }));
            assertThat(failure.getMessage(), is("simulated target service failure"));
            RequestLifecycle target = service.requests().get("/lifecycle/fail");
            assertThat(target.whenSent().isCompletedExceptionally(), is(true));
            assertThat(target.whenComplete().isCompletedExceptionally(), is(true));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void servicedOutputStreamRedirectEnforcesInMemoryLimit() {
        LifecycleRecorder service = new LifecycleRecorder(null);
        Http2Client client = Http2Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .maxInMemoryEntity(4)
                .addService(service)
                .build();

        try {
            UncheckedIOException failure = assertThrows(UncheckedIOException.class, () -> client.put()
                    .path("/lifecycle/start")
                    .outputStream(output -> output.write("large".getBytes(StandardCharsets.UTF_8))));
            assertThat(failure.getCause().getMessage(),
                       containsString("configured in-memory limit of 4 bytes"));
            assertThat(service.requests().containsKey("/lifecycle/hop"), is(false));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void expectRedirectClaimsStreamingEntityOnlyAtTarget() {
        AtomicInteger writerInvocations = new AtomicInteger();
        ENTITY_TARGET_REQUESTS.set(0);
        Http2Client client = Http2Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .readContinueTimeout(Duration.ofSeconds(2))
                .mediaContext(streamingMediaContext(writerInvocations))
                .build();

        try (Http2ClientResponse response = client.put()
                .path("/entity/expect-start")
                .submit(new StreamingEntity("payload"))) {
            assertThat(response.entity().as(String.class), is("payload"));
        } finally {
            client.closeResource();
        }

        assertThat(writerInvocations.get(), is(1));
        assertThat(ENTITY_TARGET_REQUESTS.get(), is(1));
    }

    @Test
    void materializedStringWriterHeadersSurviveMethodPreservingRedirect() {
        STRING_TARGET_CONTENT_TYPE.set(null);
        Http2Client client = Http2Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .build();

        try (Http2ClientResponse response = client.put()
                .path("/entity/string-start")
                .submit("payload")) {
            assertThat(response.entity().as(String.class), is("payload"));
        } finally {
            client.closeResource();
        }

        assertThat(STRING_TARGET_CONTENT_TYPE.get(), containsString("text/plain"));
    }

    @Test
    void postBodyRedirectRejectsStreamingEntityBeforeTargetRequest() {
        AtomicInteger writerInvocations = new AtomicInteger();
        ENTITY_TARGET_REQUESTS.set(0);
        Http2Client client = Http2Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .sendExpectContinue(false)
                .mediaContext(streamingMediaContext(writerInvocations))
                .build();

        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client.put()
                    .path("/entity/post-start")
                    .submit(new StreamingEntity("payload")));
            assertThat(failure.getMessage(), is("HTTP/2 cannot replay a one-shot request entity after redirect status 307."));
        } finally {
            client.closeResource();
        }

        assertThat(writerInvocations.get(), is(1));
        assertThat(ENTITY_TARGET_REQUESTS.get(), is(0));
    }

    @Test
    void servicedBufferedRedirectCanNegotiateHttp1AtTarget() {
        LifecycleRecorder service = new LifecycleRecorder(null);
        Http2Client client = Http2Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .followCrossOriginEntityRedirects(true)
                .protocolConfig(protocol -> protocol.priorKnowledge(false))
                .addService(service)
                .build();

        try (Http2ClientResponse response = client.get()
                .uri("http://127.0.0.1:" + http1Target.port() + "/fallback/probe")
                .request()) {
            assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
            assertThat(response.entity().as(String.class), is("ready"));
        }

        try (Http2ClientResponse response = client.put()
                .path("/fallback/start")
                .outputStream(output -> {
                    output.write("payload".getBytes(StandardCharsets.UTF_8));
                    output.close();
                })) {
            assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
            assertThat(response.entity().as(String.class), is("payload"));
        } finally {
            client.closeResource();
        }

        assertThat(service.requests().get("/fallback/start").sentProtocolId(), is(Http2Client.PROTOCOL_ID));
        assertThat(service.requests().get("/fallback/start").protocolId(), is(Http1Client.PROTOCOL_ID));
        assertThat(service.requests().containsKey("/fallback/target"), is(true));
    }

    @Test
    void writerProducedEntityCannotUseFailedH2cUpgradeResponse() {
        Http2Client client = Http2Client.builder()
                .baseUri("http://127.0.0.1:" + http1Target.port())
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(protocol -> protocol.priorKnowledge(false))
                .mediaContext(emptyStringWriterMediaContext())
                .build();

        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                          () -> client.put("/fallback/target").submit(""));
            assertThat(failure.getMessage(), containsString("failed h2c upgrade response"));
            assertThat(failure.getMessage(), containsString("request with an entity"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void rejectsNonHttpRedirectScheme() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                         () -> webClient.get("/redirect/ftp").request());

        assertThat(failure.getMessage(), containsString("Not supported scheme ftp"));
    }

    @Test
    void outerServiceControlsFinalRedirectCookiesWhileIntermediateCookiesAreStored() {
        Http2Client client = Http2Client.builder()
                .baseUri(uri)
                .servicesDiscoverServices(false)
                .sendExpectContinue(true)
                .cookieManager(WebClientCookieManager.create(config -> config.automaticStoreEnabled(true)))
                .addService(new FinalCookieRemovingService())
                .build();

        try {
            try (Http2ClientResponse response = client.put().path("/cookie/start").outputStream(output -> {
                output.write("payload".getBytes(StandardCharsets.UTF_8));
                output.close();
            })) {
                assertThat(response.entity().as(String.class), is("done"));
            }
            try (Http2ClientResponse response = client.put().path("/cookie/loop-start").outputStream(output -> {
                output.write("payload".getBytes(StandardCharsets.UTF_8));
                output.close();
            })) {
                assertThat(response.entity().as(String.class), is("loop-done"));
            }
            try (Http2ClientResponse response = client.get().path("/cookie/echo").request()) {
                String cookies = response.entity().as(String.class);
                assertThat(cookies, containsString("intermediate=kept"));
                assertThat(cookies, containsString("loop=kept"));
                assertThat(cookies.contains("final=blocked"), is(false));
            }
        } finally {
            client.closeResource();
        }
    }

    private static void queryRedirect(ServerResponse response, Status status, String target) {
        response.status(status)
                .header(HeaderNames.LOCATION, target)
                .send();
    }

    private static void queryRedirectTarget(ServerRequest request, ServerResponse response) {
        String contentType = request.headers().get(HeaderNames.CONTENT_TYPE).get();
        response.send(request.prologue().method() + ":" + contentType + ":" + request.content().as(String.class));
    }

    private static void queryRedirectHeadersTarget(ServerRequest request, ServerResponse response) {
        String accept = request.headers().get(HeaderNames.ACCEPT).get();
        String contentLanguage = request.headers().get(HeaderNames.CONTENT_LANGUAGE).get();
        response.send(request.prologue().method()
                              + ":" + accept
                              + ":" + contentLanguage
                              + ":" + request.content().as(String.class));
    }

    private static void queryRedirectGetTarget(ServerRequest request, ServerResponse response) {
        if (request.content().hasEntity() || request.headers().contains(HeaderNames.CONTENT_TYPE)) {
            response.status(Status.BAD_REQUEST_400).send("Entity metadata was preserved");
            return;
        }
        response.send("GET without entity metadata");
    }

    private static MediaContext streamingMediaContext(AtomicInteger writerInvocations) {
        EntityWriter<StreamingEntity> writer = new EntityWriter<>() {
            @Override
            public boolean supportsInstanceWriter() {
                return true;
            }

            @Override
            public InstanceWriter instanceWriter(GenericType<StreamingEntity> type,
                                                   StreamingEntity object,
                                                   WritableHeaders<?> requestHeaders) {
                requestHeaders.set(HeaderNames.CONTENT_TYPE, "text/plain");
                return new InstanceWriter() {
                    @Override
                    public OptionalLong contentLength() {
                        return OptionalLong.empty();
                    }

                    @Override
                    public boolean alwaysInMemory() {
                        return false;
                    }

                    @Override
                    public void write(OutputStream stream) {
                        writerInvocations.incrementAndGet();
                        try (stream) {
                            stream.write(object.value().getBytes(StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }

                    @Override
                    public byte[] instanceBytes() {
                        throw new AssertionError("Streaming entity must not be materialized");
                    }
                };
            }

            @Override
            public void write(GenericType<StreamingEntity> type,
                              StreamingEntity object,
                              OutputStream outputStream,
                              Headers requestHeaders,
                              WritableHeaders<?> responseHeaders) {
                throw new AssertionError("Server writer must not be used");
            }

            @Override
            public void write(GenericType<StreamingEntity> type,
                              StreamingEntity object,
                              OutputStream outputStream,
                              WritableHeaders<?> requestHeaders) {
                throw new AssertionError("InstanceWriter must be used");
            }
        };
        MediaSupport support = new MediaSupport() {
            @Override
            public String name() {
                return "streaming-test";
            }

            @Override
            public String type() {
                return "streaming-test";
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
                return new WriterResponse<>(SupportLevel.SUPPORTED, () -> (EntityWriter<T>) writer);
            }
        };
        return MediaContext.builder()
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(support)
                .build();
    }

    private static MediaContext emptyStringWriterMediaContext() {
        EntityWriter<String> writer = new EntityWriter<>() {
            @Override
            public boolean supportsInstanceWriter() {
                return true;
            }

            @Override
            public InstanceWriter instanceWriter(GenericType<String> type,
                                                   String object,
                                                   WritableHeaders<?> requestHeaders) {
                byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
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
            public void write(GenericType<String> type,
                              String object,
                              OutputStream outputStream,
                              Headers requestHeaders,
                              WritableHeaders<?> responseHeaders) {
                throw new AssertionError("Server writer must not be used");
            }

            @Override
            public void write(GenericType<String> type,
                              String object,
                              OutputStream outputStream,
                              WritableHeaders<?> requestHeaders) {
                throw new AssertionError("InstanceWriter must be used");
            }
        };
        MediaSupport support = new MediaSupport() {
            @Override
            public String name() {
                return "empty-string-writer-test";
            }

            @Override
            public String type() {
                return "empty-string-writer-test";
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
                return new WriterResponse<>(SupportLevel.SUPPORTED, () -> (EntityWriter<T>) writer);
            }
        };
        return MediaContext.builder()
                .registerDefaults(false)
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(support)
                .build();
    }

    private void assertEmptyOutputStreamWithRedirectAfter() {
        String expected = "Upload completed!";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirectAfterUpload")
                .outputStream(OutputStream::close)) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    private void assertEntityOutputStreamWithRedirectAfter() {
        String expected = """
                Upload completed!
                0123456789
                0123456789
                0123456789""";
        try (Http2ClientResponse response = webClient.put()
                .path("/redirectAfterUpload")
                .outputStream(it -> {
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.write("0123456789".getBytes(StandardCharsets.UTF_8));
                    it.close();
                })) {
            assertThat(response.entity().as(String.class), is(expected));
        }
    }

    private static final class LifecycleRecorder implements WebClientService {
        private final String failingPath;
        private final Map<String, RequestLifecycle> requests = new ConcurrentHashMap<>();
        private final List<String> events = new CopyOnWriteArrayList<>();

        private LifecycleRecorder(String failingPath) {
            this.failingPath = failingPath;
        }

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            String path = request.uri().toUri().getPath();
            RequestLifecycle lifecycle = new RequestLifecycle(request.whenSent().toCompletableFuture(),
                                                              request.whenComplete().toCompletableFuture(),
                                                              Thread.currentThread());
            requests.put(path, lifecycle);
            lifecycle.whenSent().whenComplete((_, _) -> {
                lifecycle.sentProtocolId(request.protocolId());
                events.add(path + ":sent");
            });
            lifecycle.whenComplete().whenComplete((_, _) -> events.add(path + ":complete"));
            if (path.equals(failingPath)) {
                throw new IllegalStateException("simulated target service failure");
            }
            WebClientServiceResponse response = chain.proceed(request);
            lifecycle.responseStatus(response.status());
            lifecycle.protocolId(request.protocolId());
            events.add(path + ":response");
            return response;
        }

        private Map<String, RequestLifecycle> requests() {
            return requests;
        }

        private List<String> events() {
            return events;
        }
    }

    private record FinalCookieRemovingService() implements WebClientService {
        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            String path = request.uri().toUri().getPath();
            WebClientServiceResponse response = chain.proceed(request);
            if (path.equals("/cookie/intermediate") || path.equals("/cookie/loop-intermediate")) {
                request.headers().set(HeaderNames.create("X-Service-Mutated"), "true");
                return response;
            }
            if (!path.equals("/cookie/start")) {
                return response;
            }
            WritableHeaders<?> headers = WritableHeaders.create(response.headers());
            headers.remove(HeaderNames.SET_COOKIE);
            return WebClientServiceResponse.builder(response)
                    .headers(ClientResponseHeaders.create(headers))
                    .build();
        }
    }

    private static final class RequestLifecycle {
        private final CompletableFuture<WebClientServiceRequest> whenSent;
        private final CompletableFuture<WebClientServiceResponse> whenComplete;
        private final Thread thread;
        private volatile Status responseStatus;
        private volatile String sentProtocolId;
        private volatile String protocolId;

        private RequestLifecycle(CompletableFuture<WebClientServiceRequest> whenSent,
                                 CompletableFuture<WebClientServiceResponse> whenComplete,
                                 Thread thread) {
            this.whenSent = whenSent;
            this.whenComplete = whenComplete;
            this.thread = thread;
        }

        private CompletableFuture<WebClientServiceRequest> whenSent() {
            return whenSent;
        }

        private CompletableFuture<WebClientServiceResponse> whenComplete() {
            return whenComplete;
        }

        private Thread thread() {
            return thread;
        }

        private Status responseStatus() {
            return responseStatus;
        }

        private void responseStatus(Status responseStatus) {
            this.responseStatus = responseStatus;
        }

        private String protocolId() {
            return protocolId;
        }

        private String sentProtocolId() {
            return sentProtocolId;
        }

        private void sentProtocolId(String sentProtocolId) {
            this.sentProtocolId = sentProtocolId;
        }

        private void protocolId(String protocolId) {
            this.protocolId = protocolId;
        }
    }

    private record StreamingEntity(String value) {
    }

}
