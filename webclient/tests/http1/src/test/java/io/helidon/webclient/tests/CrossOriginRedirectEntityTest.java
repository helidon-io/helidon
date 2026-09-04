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

package io.helidon.webclient.tests;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrossOriginRedirectEntityTest {
    private static final String BODY = "authorization-code=secret";
    private static final String BLOCKED_MESSAGE = "Cross-origin redirect with request entity is disabled.";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final HeaderName REQUEST_VALUE = HeaderNames.create("X-Request-Value");

    private static final AtomicReference<CapturedRequest> SAME_ORIGIN_REQUEST = new AtomicReference<>();
    private static final AtomicReference<CapturedRequest> CROSS_ORIGIN_REQUEST = new AtomicReference<>();

    private static WebServer sourceServer;
    private static WebServer targetServer;

    @BeforeAll
    static void beforeAll() {
        targetServer = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .routing(rules -> rules.post("/capture", (req, res) -> {
                    CROSS_ORIGIN_REQUEST.set(capture(req));
                    res.send("captured");
                }))
                .build()
                .start();

        sourceServer = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .routing(rules -> rules
                        .post("/redirect307", (req, res) -> redirect(req, res, Status.TEMPORARY_REDIRECT_307))
                        .post("/redirect308", (req, res) -> redirect(req, res, Status.PERMANENT_REDIRECT_308))
                        .post("/expect-redirect307", (_, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                                .header(HeaderNames.LOCATION,
                                        "http://127.0.0.1:" + targetServer.port() + "/capture")
                                .send())
                        .post("/same-origin", (req, res) -> {
                            req.content().as(String.class);
                            res.status(Status.TEMPORARY_REDIRECT_307)
                                    .header(HeaderNames.LOCATION, "/capture")
                                    .send();
                        })
                        .post("/uri-rewrite", (req, res) -> {
                            req.content().as(String.class);
                            res.status(Status.TEMPORARY_REDIRECT_307)
                                    .header(HeaderNames.LOCATION, "/uri-capture")
                                    .send();
                        })
                        .post("/capture", (req, res) -> {
                            SAME_ORIGIN_REQUEST.set(capture(req));
                            res.send("captured");
                        })
                        .post("/uri-capture", (req, res) -> {
                            SAME_ORIGIN_REQUEST.set(capture(req));
                            res.send("captured");
                        })
                        .get("/fragment", (_, res) -> res.send("fragment-free")))
                .build()
                .start();
    }

    @AfterAll
    static void afterAll() {
        if (sourceServer != null) {
            sourceServer.stop();
        }
        if (targetServer != null) {
            targetServer.stop();
        }
    }

    @BeforeEach
    void resetCaptures() {
        SAME_ORIGIN_REQUEST.set(null);
        CROSS_ORIGIN_REQUEST.set(null);
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void rejectsMaterializedEntityByDefault(int status) {
        Http1Client client = newClient(false, true);
        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                            () -> client.post("/redirect" + status)
                                                                    .readTimeout(TIMEOUT)
                                                                    .submit(BODY));
            assertThat(exception.getMessage(), is(BLOCKED_MESSAGE));
            assertThat(CROSS_ORIGIN_REQUEST.get(), is(nullValue()));
        } finally {
            client.closeResource();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    @Timeout(20)
    void allowsMaterializedEntityWithOptIn(int status) {
        Http1Client client = newClient(true, true);
        try (Http1ClientResponse response = client.post("/redirect" + status)
                .readTimeout(TIMEOUT)
                .submit(BODY)) {
            assertThat(response.status(), is(Status.OK_200));
        } finally {
            client.closeResource();
        }

        CapturedRequest captured = CROSS_ORIGIN_REQUEST.get();
        assertThat(captured.method(), is(Method.POST));
        assertThat(captured.body(), is(BODY));
    }

    @Test
    @Timeout(20)
    void disablingHeaderFilteringDoesNotEnableEntityReplay() {
        Http1Client client = newClient(false, false);
        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                            () -> client.post("/redirect307")
                                                                    .readTimeout(TIMEOUT)
                                                                    .submit(BODY));
            assertThat(exception.getMessage(), is(BLOCKED_MESSAGE));
            assertThat(CROSS_ORIGIN_REQUEST.get(), is(nullValue()));
        } finally {
            client.closeResource();
        }
    }

    @Test
    @Timeout(20)
    void doesNotReinvokeConsumedOutputStreamHandlerEvenWithOptIn() {
        AtomicInteger invocations = new AtomicInteger();
        Http1Client client = newClient(true, true);
        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                            () -> client.post("/redirect307")
                                                                    .sendExpectContinue(false)
                                                                    .readTimeout(TIMEOUT)
                                                                    .outputStream(output -> {
                                                                        invocations.incrementAndGet();
                                                                        output.write(BODY.getBytes(StandardCharsets.UTF_8));
                                                                        output.close();
                                                                    }));
            assertThat(exception.getMessage(),
                       is("Cannot replay a one-shot request body after redirect status 307."));
            assertThat(invocations.get(), is(1));
            assertThat(CROSS_ORIGIN_REQUEST.get(), is(nullValue()));
        } finally {
            client.closeResource();
        }
    }

    @Test
    @Timeout(20)
    void followsEmptyOutputStreamRedirect() {
        AtomicInteger invocations = new AtomicInteger();
        Http1Client client = newClient(false, true);
        try (Http1ClientResponse response = client.post("/redirect307")
                .sendExpectContinue(false)
                .readTimeout(TIMEOUT)
                .outputStream(output -> {
                    invocations.incrementAndGet();
                    output.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.lastEndpointUri().port(), is(targetServer.port()));
        } finally {
            client.closeResource();
        }

        assertThat(invocations.get(), is(1));
        CapturedRequest captured = CROSS_ORIGIN_REQUEST.get();
        assertThat(captured.method(), is(Method.POST));
        assertThat(captured.body(), is(""));
    }

    @Test
    @Timeout(20)
    void followsExpectContinueRedirectWithoutReinvokingHandler() {
        AtomicInteger invocations = new AtomicInteger();
        Http1Client client = newClient(true, true);
        try (Http1ClientResponse response = client.post("/expect-redirect307")
                .sendExpectContinue(true)
                .readContinueTimeout(TIMEOUT)
                .readTimeout(TIMEOUT)
                .outputStream(output -> {
                    invocations.incrementAndGet();
                    output.write(BODY.getBytes(StandardCharsets.UTF_8));
                    output.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.lastEndpointUri().port(), is(targetServer.port()));
        } finally {
            client.closeResource();
        }

        assertThat(invocations.get(), is(1));
        CapturedRequest captured = CROSS_ORIGIN_REQUEST.get();
        assertThat(captured.method(), is(Method.POST));
        assertThat(captured.body(), is(BODY));
    }

    @Test
    @Timeout(20)
    void appliesPolicyBeforeSendingExpectContinueBodyToRedirectTarget() {
        AtomicInteger invocations = new AtomicInteger();
        Http1Client client = newClient(false, true);
        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                            () -> client.post("/expect-redirect307")
                                                                    .sendExpectContinue(true)
                                                                    .readContinueTimeout(TIMEOUT)
                                                                    .readTimeout(TIMEOUT)
                                                                    .outputStream(output -> {
                                                                        invocations.incrementAndGet();
                                                                        output.write(BODY.getBytes(StandardCharsets.UTF_8));
                                                                        output.close();
                                                                    }));
            assertThat(exception.getMessage(), is(BLOCKED_MESSAGE));
            assertThat(invocations.get(), is(1));
            assertThat(CROSS_ORIGIN_REQUEST.get(), is(nullValue()));
        } finally {
            client.closeResource();
        }
    }

    @Test
    @Timeout(20)
    void preservesExplicitHostAndOrdinaryHeadersOnSameOriginRedirect() {
        Http1Client client = newClient(false, true);
        try (Http1ClientResponse response = client.post("/same-origin")
                .header(HeaderNames.HOST, "virtual.example")
                .header(REQUEST_VALUE, "preserved")
                .readTimeout(TIMEOUT)
                .submit(BODY)) {
            assertThat(response.status(), is(Status.OK_200));
        } finally {
            client.closeResource();
        }

        CapturedRequest captured = SAME_ORIGIN_REQUEST.get();
        assertThat(captured.host(), is("virtual.example"));
        assertThat(captured.requestValue(), is("preserved"));
        assertThat(captured.body(), is(BODY));
    }

    @Test
    @Timeout(20)
    void regeneratesHostWhenUriOriginChanges() {
        Http1Client client = newClient(true, true);
        try (Http1ClientResponse response = client.post("/redirect307")
                .header(HeaderNames.HOST, "virtual.example")
                .readTimeout(TIMEOUT)
                .submit(BODY)) {
            assertThat(response.status(), is(Status.OK_200));
        } finally {
            client.closeResource();
        }

        CapturedRequest captured = CROSS_ORIGIN_REQUEST.get();
        assertThat(captured.host(), is(not("virtual.example")));
        assertThat(captured.host(), is("127.0.0.1:" + targetServer.port()));
    }

    @Test
    @Timeout(20)
    void rejectsEntityWhenServiceChangesOnlyHost() {
        Http1Client client = Http1Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + sourceServer.port())
                .addService(new HostRewriteService())
                .build();
        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                            () -> client.post("/same-origin")
                                                                    .readTimeout(TIMEOUT)
                                                                    .submit(BODY));
            assertThat(exception.getMessage(), is(BLOCKED_MESSAGE));
            assertThat(SAME_ORIGIN_REQUEST.get(), is(nullValue()));
        } finally {
            client.closeResource();
        }
    }

    @Test
    @Timeout(20)
    void rejectsEntityWhenServiceChangesRedirectUriOrigin() {
        Http1Client client = Http1Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + sourceServer.port())
                .addService(new UriRewriteService())
                .build();
        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                            () -> client.post("/uri-rewrite")
                                                                    .readTimeout(TIMEOUT)
                                                                    .submit(BODY));
            assertThat(exception.getMessage(), is(BLOCKED_MESSAGE));
            assertThat(SAME_ORIGIN_REQUEST.get(), is(nullValue()));
        } finally {
            client.closeResource();
        }
    }

    @Test
    @Timeout(20)
    void doesNotSendFragmentInRequestTarget() {
        Http1Client client = newClient(false, true);
        try (Http1ClientResponse response = client.get()
                .uri(URI.create("/fragment?key=value#client-only"))
                .readTimeout(TIMEOUT)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity().as(String.class), is("fragment-free"));
            assertThat(response.lastEndpointUri().fragment().value(), is("client-only"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    @Timeout(20)
    void completesOriginalAndDecoratedServiceResponseLifecycles() {
        AtomicReference<CompletionStage<WebClientServiceResponse>> originalCompletion = new AtomicReference<>();
        CompletableFuture<WebClientServiceResponse> decoratedCompletion = new CompletableFuture<>();
        Http1Client client = Http1Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + sourceServer.port())
                .addService((chain, request) -> {
                    originalCompletion.set(request.whenComplete());
                    return WebClientServiceResponse.builder(chain.proceed(request))
                            .whenComplete(decoratedCompletion)
                            .build();
                })
                .build();
        try (Http1ClientResponse response = client.get("/fragment").request()) {
            assertThat(response.status(), is(Status.OK_200));
        } finally {
            client.closeResource();
        }

        assertThat(originalCompletion.get().toCompletableFuture().isDone(), is(true));
        assertThat(decoratedCompletion.isDone(), is(true));
    }

    private static Http1Client newClient(boolean allowCrossOriginEntityReplay, boolean filterRedirectHeaders) {
        return Http1Client.builder()
                .servicesDiscoverServices(false)
                .baseUri("http://127.0.0.1:" + sourceServer.port())
                .followCrossOriginEntityRedirects(allowCrossOriginEntityReplay)
                .filterRedirectHeaders(filterRedirectHeaders)
                .build();
    }

    private static void redirect(ServerRequest request, ServerResponse response, Status status) {
        request.content().as(String.class);
        response.status(status)
                .header(HeaderNames.LOCATION, "http://127.0.0.1:" + targetServer.port() + "/capture")
                .send();
    }

    private static CapturedRequest capture(ServerRequest request) {
        String body = request.content().hasEntity() ? request.content().as(String.class) : "";
        return new CapturedRequest(request.prologue().method(),
                                   request.headers().get(HeaderNames.HOST).get(),
                                   request.headers().first(REQUEST_VALUE).orElse(null),
                                   body);
    }

    private record HostRewriteService() implements WebClientService {
        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            String host = request.uri().path().path().equals("/capture") ? "target.example" : "source.example";
            request.headers().set(HeaderNames.HOST, host);
            return chain.proceed(request);
        }
    }

    private record UriRewriteService() implements WebClientService {
        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            if (request.uri().path().path().equals("/uri-capture")) {
                request.uri().host("localhost");
            }
            return chain.proceed(request);
        }
    }

    private record CapturedRequest(Method method, String host, String requestValue, String body) {
    }
}
