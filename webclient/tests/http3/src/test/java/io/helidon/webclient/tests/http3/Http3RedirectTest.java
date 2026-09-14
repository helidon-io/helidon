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

package io.helidon.webclient.tests.http3;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3RedirectTest {
    private static final String REDIRECT_PATH = "/redirect";
    private static final String TARGET_PATH = "/target";
    private static final String PAYLOAD_CONTENT_TYPE = "application/octet-stream";
    private static final byte[] PAYLOAD = "payload".getBytes(StandardCharsets.UTF_8);

    @Test
    void shouldReplayMaterializedBodyAfterTemporaryRedirect() throws Exception {
        assertMaterializedBodyReplayed(Method.POST, Status.TEMPORARY_REDIRECT_307);
    }

    @Test
    void shouldReplayMaterializedBodyAfterPermanentRedirect() throws Exception {
        assertMaterializedBodyReplayed(Method.POST, Status.PERMANENT_REDIRECT_308);
    }

    @Test
    void shouldReplayQueryBodyAfterMovedPermanently() throws Exception {
        assertMaterializedBodyReplayed(Method.QUERY, Status.MOVED_PERMANENTLY_301);
    }

    @Test
    void shouldReplayQueryBodyAfterFound() throws Exception {
        assertMaterializedBodyReplayed(Method.QUERY, Status.FOUND_302);
    }

    @Test
    void shouldUseMediaWriterContentTypeForQuery() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .route(Method.QUERY, TARGET_PATH, (request, response) -> {
                    contentType.set(request.headers().first(HeaderNames.CONTENT_TYPE).orElse(null));
                    response.send(request.content().as(String.class));
                }))) {
            Http3Client client = newClient(environment);
            try (Http3ClientResponse response = client.method(Method.QUERY)
                    .uri(TARGET_PATH)
                    .submit("payload")) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.as(String.class), is("payload"));
            } finally {
                client.closeResource();
            }
        }
        assertThat(contentType.get(), startsWith("text/plain"));
    }

    @Test
    void shouldRejectQueryWithoutContentTypeAfterMediaPreparation() throws Exception {
        AtomicInteger targetCount = new AtomicInteger();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .route(Method.QUERY, TARGET_PATH, (_, response) -> {
                    targetCount.incrementAndGet();
                    response.send();
                }))) {
            Http3Client client = strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .servicesDiscoverServices(false)
                    .tls(environment.clientTlsHttp3())
                    .addService((chain, request) -> {
                        request.headers().remove(HeaderNames.CONTENT_TYPE);
                        return chain.proceed(request);
                    })
                    .build();
            try {
                IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                               () -> client.method(Method.QUERY)
                                                                       .uri(TARGET_PATH)
                                                                       .submit("payload"));
                assertThat(failure.getMessage(), is("Content-Type header is required for method 'QUERY'"));
            } finally {
                client.closeResource();
            }
        }
        assertThat(targetCount.get(), is(0));
    }

    @Test
    void shouldRewritePostToGetWithoutStaleEntityHeadersAfter302() throws Exception {
        assertRewrittenToGet(executeRedirect(Method.POST, 302, false));
    }

    @Test
    void shouldRewritePostToGetWithoutStaleEntityHeadersAfter303() throws Exception {
        assertRewrittenToGet(executeRedirect(Method.POST, 303, false));
    }

    @Test
    void shouldRewriteQueryToGetWithoutStaleEntityHeadersAfter303() throws Exception {
        assertRewrittenToGet(executeRedirect(Method.QUERY, 303, false));
    }

    @Test
    void genericServiceHandoffRewritesPostToGetWithoutStaleEntityHeadersAfter302() throws Exception {
        assertRewrittenToGet(executeRedirect(Method.POST, 302, true));
    }

    @Test
    void genericServiceHandoffRewritesPostToGetWithoutStaleEntityHeadersAfter303() throws Exception {
        assertRewrittenToGet(executeRedirect(Method.POST, 303, true));
    }

    @Test
    void shouldPreservePostBodyAndExpectAfter307() throws Exception {
        assertPostPreserved(executeRedirect(Method.POST, 307, false));
    }

    @Test
    void shouldPreservePostBodyAndExpectAfter308() throws Exception {
        assertPostPreserved(executeRedirect(Method.POST, 308, false));
    }

    private static void assertRewrittenToGet(RedirectCaptures captures) {

        assertRewrittenGet(captures.serviceRequest());
        assertRewrittenGet(captures.wireRequest());
        assertThat(captures.wireRequest().body(), is(new byte[0]));
        assertThat(captures.serviceInvocations(), is(2));
    }

    private static void assertPostPreserved(RedirectCaptures captures) {
        assertThat(captures.serviceRequest().method(), is(Method.POST));
        assertThat(captures.serviceRequest().contentLength(), is(Integer.toString(PAYLOAD.length)));
        assertThat(captures.serviceRequest().transferEncoding(), is(false));
        assertThat(captures.wireRequest().method(), is(Method.POST));
        assertThat(captures.wireRequest().contentLength(), is(Integer.toString(PAYLOAD.length)));
        assertThat(captures.wireRequest().transferEncoding(), is(false));
        assertThat(captures.wireRequest().expectContinue(), is(true));
        assertThat(captures.wireRequest().body(), is(PAYLOAD));
        assertThat(captures.serviceInvocations(), is(2));
        assertRepresentationHeadersPreserved(captures.serviceRequest());
        assertRepresentationHeadersPreserved(captures.wireRequest());
    }

    @Test
    void shouldRejectOneShotBodyAfterTemporaryRedirect() throws Exception {
        assertOneShotBodyRejected(Method.POST, Status.TEMPORARY_REDIRECT_307);
    }

    @Test
    void shouldRejectOneShotBodyAfterPermanentRedirect() throws Exception {
        assertOneShotBodyRejected(Method.POST, Status.PERMANENT_REDIRECT_308);
    }

    @Test
    void shouldRejectOneShotQueryBodyAfterMovedPermanently() throws Exception {
        assertOneShotBodyRejected(Method.QUERY, Status.MOVED_PERMANENTLY_301);
    }

    @Test
    void shouldRejectOneShotQueryBodyAfterFound() throws Exception {
        assertOneShotBodyRejected(Method.QUERY, Status.FOUND_302);
    }

    @Test
    void shouldRejectCrossOriginMaterializedBodyAfterTemporaryRedirect() throws Exception {
        assertCrossOriginMaterializedBodyRejected(Method.POST, Status.TEMPORARY_REDIRECT_307, true);
    }

    @Test
    void shouldRejectCrossOriginMaterializedBodyAfterPermanentRedirect() throws Exception {
        assertCrossOriginMaterializedBodyRejected(Method.POST, Status.PERMANENT_REDIRECT_308, true);
    }

    @Test
    void shouldRejectCrossOriginQueryBodyAfterMovedPermanently() throws Exception {
        assertCrossOriginMaterializedBodyRejected(Method.QUERY, Status.MOVED_PERMANENTLY_301, true);
    }

    @Test
    void shouldRejectCrossOriginQueryBodyAfterFound() throws Exception {
        assertCrossOriginMaterializedBodyRejected(Method.QUERY, Status.FOUND_302, true);
    }

    @Test
    void shouldRejectCrossOriginMaterializedBodyWhenHeaderFilteringIsDisabled() throws Exception {
        assertCrossOriginMaterializedBodyRejected(Method.POST, Status.TEMPORARY_REDIRECT_307, false);
    }

    @Test
    void shouldAllowCrossOriginMaterializedBodyAfterTemporaryRedirectWhenEnabled() throws Exception {
        assertCrossOriginMaterializedBodyAllowed(Method.POST, Status.TEMPORARY_REDIRECT_307);
    }

    @Test
    void shouldAllowCrossOriginMaterializedBodyAfterPermanentRedirectWhenEnabled() throws Exception {
        assertCrossOriginMaterializedBodyAllowed(Method.POST, Status.PERMANENT_REDIRECT_308);
    }

    @Test
    void shouldAllowCrossOriginQueryBodyAfterMovedPermanentlyWhenEnabled() throws Exception {
        assertCrossOriginMaterializedBodyAllowed(Method.QUERY, Status.MOVED_PERMANENTLY_301);
    }

    @Test
    void shouldAllowCrossOriginQueryBodyAfterFoundWhenEnabled() throws Exception {
        assertCrossOriginMaterializedBodyAllowed(Method.QUERY, Status.FOUND_302);
    }

    @Test
    void shouldRejectCrossOriginEntityWhenRedirectServiceRewritesTarget() throws Exception {
        AtomicInteger targetCount = new AtomicInteger();
        try (TestEnvironment target = TestEnvironment.createSharedListener(routing -> routing
                .post(TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    res.send(req.content().as(byte[].class));
                }))) {
            try (TestEnvironment source = TestEnvironment.createSharedListener(routing -> routing
                    .post(REDIRECT_PATH, (req, res) -> {
                        req.content().as(byte[].class);
                        res.status(Status.TEMPORARY_REDIRECT_307)
                                .header(HeaderNames.LOCATION, TARGET_PATH)
                                .send();
                    }))) {
                Http3Client client = strictClientBuilder()
                        .baseUri(source.baseUri())
                        .tls(source.clientTlsHttp3())
                        .addService((chain, request) -> {
                            if (TARGET_PATH.equals(request.uri().path().path())) {
                                request.uri().resolve(URI.create(target.baseUri() + TARGET_PATH));
                            }
                            return chain.proceed(request);
                        })
                        .build();
                try {
                    IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                                  () -> client.post(REDIRECT_PATH).submit(PAYLOAD));
                    assertThat(failure.getMessage(), is("Cross-origin redirect with request entity is disabled."));
                } finally {
                    client.closeResource();
                }
            }
        }

        assertThat(targetCount.get(), is(0));
    }

    @Test
    void shouldRejectEntityWhenServiceChangesOnlyFinalHost() throws Exception {
        AtomicInteger targetCount = new AtomicInteger();
        AtomicReference<CompletableFuture<WebClientServiceRequest>> targetWhenSent = new AtomicReference<>();
        AtomicReference<CompletableFuture<WebClientServiceResponse>> targetWhenComplete = new AtomicReference<>();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .post(REDIRECT_PATH, (req, res) -> {
                    req.content().as(byte[].class);
                    res.status(Status.TEMPORARY_REDIRECT_307)
                            .header(HeaderNames.LOCATION, TARGET_PATH)
                            .send();
                })
                .post(TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    res.send(req.content().as(byte[].class));
                }))) {
            int port = URI.create(environment.baseUri()).getPort();
            Http3Client client = strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .addService((chain, request) -> {
                        if (TARGET_PATH.equals(request.uri().path().path())) {
                            targetWhenSent.set(request.whenSent().toCompletableFuture());
                            targetWhenComplete.set(request.whenComplete().toCompletableFuture());
                        }
                        request.headers().set(HeaderNames.HOST,
                                              REDIRECT_PATH.equals(request.uri().path().path())
                                                      ? "source.invalid:" + port
                                                      : "target.invalid:" + port);
                        return chain.proceed(request);
                    })
                    .build();
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                              () -> client.post(REDIRECT_PATH)
                                                                      .sni(sni -> sni.mode(SniMode.EXPLICIT)
                                                                              .host("localhost"))
                                                                      .submit(PAYLOAD));
                assertThat(failure.getMessage(), is("Cross-origin redirect with request entity is disabled."));
            } finally {
                client.closeResource();
            }
        }

        assertThat(targetCount.get(), is(0));
        assertThat(targetWhenSent.get().isCompletedExceptionally(), is(true));
        assertThat(targetWhenComplete.get().isCompletedExceptionally(), is(true));
    }

    @Test
    void genericClientRejectsEntityWhenServiceChangesOnlyFinalHost() throws Exception {
        AtomicInteger targetCount = new AtomicInteger();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .post(REDIRECT_PATH, (req, res) -> {
                    req.content().as(byte[].class);
                    res.status(Status.TEMPORARY_REDIRECT_307)
                            .header(HeaderNames.LOCATION, TARGET_PATH)
                            .send();
                })
                .post(TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    res.send(req.content().as(byte[].class));
                }))) {
            int port = URI.create(environment.baseUri()).getPort();
            WebClient client = strictWebClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .addService((chain, request) -> {
                        request.headers().set(HeaderNames.HOST,
                                              REDIRECT_PATH.equals(request.uri().path().path())
                                                      ? "source.invalid:" + port
                                                      : "target.invalid:" + port);
                        return chain.proceed(request);
                    })
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolPreference(Http1Client.PROTOCOL_ID)
                    .build();
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                              () -> client.post(REDIRECT_PATH)
                                                                      .sni(sni -> sni.mode(SniMode.EXPLICIT)
                                                                              .host("localhost"))
                                                                      .submit(PAYLOAD));
                assertThat(failure.getMessage(), is("Cross-origin redirect with request entity is disabled."));
            } finally {
                client.closeResource();
            }
        }

        assertThat(targetCount.get(), is(0));
    }

    @Test
    void syntheticRedirectRetainsSourceSecurityTarget() throws Exception {
        AtomicInteger targetCount = new AtomicInteger();
        AtomicReference<CompletableFuture<WebClientServiceRequest>> sourceWhenSent = new AtomicReference<>();
        AtomicReference<CompletableFuture<WebClientServiceResponse>> sourceWhenComplete = new AtomicReference<>();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .post(TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    res.send(req.content().as(byte[].class));
                }))) {
            int port = URI.create(environment.baseUri()).getPort();
            Http3Client client = strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .addService((chain, request) -> {
                        if ("/synthetic-redirect".equals(request.uri().path().path())) {
                            sourceWhenSent.set(request.whenSent().toCompletableFuture());
                            sourceWhenComplete.set(request.whenComplete().toCompletableFuture());
                            request.headers().set(HeaderNames.HOST, "source.invalid:" + port);
                            WritableHeaders<?> responseHeaders = WritableHeaders.create();
                            responseHeaders.set(HeaderValues.create(HeaderNames.LOCATION, TARGET_PATH));
                            return WebClientServiceResponse.builder()
                                    .serviceRequest(request)
                                    .whenComplete(new CompletableFuture<>())
                                    .connection(() -> { })
                                    .status(Status.TEMPORARY_REDIRECT_307)
                                    .headers(ClientResponseHeaders.create(responseHeaders))
                                    .build();
                        }
                        request.headers().set(HeaderNames.HOST, "target.invalid:" + port);
                        return chain.proceed(request);
                    })
                    .build();
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                              () -> client.post("/synthetic-redirect")
                                                                      .sni(sni -> sni.mode(SniMode.EXPLICIT)
                                                                              .host("localhost"))
                                                                      .submit(PAYLOAD));
                assertThat(failure.getMessage(), is("Cross-origin redirect with request entity is disabled."));
            } finally {
                client.closeResource();
            }
        }

        assertThat(targetCount.get(), is(0));
        assertThat(sourceWhenSent.get().isDone(), is(true));
        assertThat(sourceWhenSent.get().isCompletedExceptionally(), is(false));
        assertThat(sourceWhenComplete.get().isDone(), is(true));
        assertThat(sourceWhenComplete.get().isCompletedExceptionally(), is(false));
    }

    @Test
    void reusableGenericRequestDoesNotRetainRedirectTaint() throws Exception {
        AtomicInteger sourceCount = new AtomicInteger();
        AtomicInteger targetCount = new AtomicInteger();
        try (TestEnvironment target = TestEnvironment.createSharedListener(routing -> routing
                .post(TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    res.send(req.content().as(byte[].class));
                }))) {
            try (TestEnvironment source = TestEnvironment.createSharedListener(routing -> routing
                    .post(REDIRECT_PATH, (req, res) -> {
                        req.content().as(byte[].class);
                        if (sourceCount.getAndIncrement() == 0) {
                            res.status(Status.TEMPORARY_REDIRECT_307)
                                    .header(HeaderNames.LOCATION, target.baseUri() + TARGET_PATH)
                                    .send();
                        } else {
                            res.send(PAYLOAD);
                        }
                    }))) {
                WebClient client = strictWebClientBuilder()
                        .baseUri(source.baseUri())
                        .tls(source.clientTlsHttp3())
                        .addProtocolPreference(Http3Client.PROTOCOL_ID)
                        .addProtocolPreference(Http1Client.PROTOCOL_ID)
                        .build();
                try {
                    HttpClientRequest request = client.post(REDIRECT_PATH);
                    IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                                  () -> request.submit(PAYLOAD));
                    assertThat(failure.getMessage(), is("Cross-origin redirect with request entity is disabled."));
                    try (HttpClientResponse response = request.submit(PAYLOAD)) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.as(byte[].class), is(PAYLOAD));
                    }
                } finally {
                    client.closeResource();
                }
            }
        }

        assertThat(sourceCount.get(), is(2));
        assertThat(targetCount.get(), is(0));
    }

    @Test
    void shouldRejectGenericWebClientCrossOriginEntityReplay() throws Exception {
        AtomicInteger targetCount = new AtomicInteger();
        try (TestEnvironment target = TestEnvironment.createSharedListener(routing -> routing
                .post(TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    res.send(req.content().as(byte[].class));
                }))) {
            try (TestEnvironment source = TestEnvironment.createSharedListener(routing -> routing
                    .post(REDIRECT_PATH, (req, res) -> {
                        req.content().as(byte[].class);
                        res.status(Status.TEMPORARY_REDIRECT_307)
                                .header(HeaderNames.LOCATION, target.baseUri() + TARGET_PATH)
                                .send();
                    }))) {
                WebClient client = strictWebClientBuilder()
                        .baseUri(source.baseUri())
                        .tls(source.clientTlsHttp3())
                        .addProtocolPreference(Http3Client.PROTOCOL_ID)
                        .addProtocolPreference(Http1Client.PROTOCOL_ID)
                        .build();
                try {
                    IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                                  () -> client.post(REDIRECT_PATH).submit(PAYLOAD));
                    assertThat(failure.getMessage(), is("Cross-origin redirect with request entity is disabled."));
                } finally {
                    client.closeResource();
                }
            }
        }

        assertThat(targetCount.get(), is(0));
    }

    @Test
    void shouldAllowEmptyEntityAcrossOriginsByDefault() throws Exception {
        AtomicInteger targetCount = new AtomicInteger();
        try (TestEnvironment target = TestEnvironment.createSharedListener(routing -> routing
                .post(TARGET_PATH, (_, res) -> {
                    targetCount.incrementAndGet();
                    res.send();
                }))) {
            try (TestEnvironment source = TestEnvironment.createSharedListener(routing -> routing
                    .post(REDIRECT_PATH, (_, res) -> {
                        res.status(Status.TEMPORARY_REDIRECT_307)
                                .header(HeaderNames.LOCATION, target.baseUri() + TARGET_PATH)
                                .send();
                    }))) {
                Http3Client client = newClient(source);
                try {
                    try (Http3ClientResponse response = client.post(REDIRECT_PATH).submit(new byte[0])) {
                        assertThat(response.status(), is(Status.OK_200));
                    }
                } finally {
                    client.closeResource();
                }
            }
        }

        assertThat(targetCount.get(), is(1));
    }

    @Test
    void shouldStillRejectCrossOriginOneShotBodyWhenEntityReplayIsEnabled() throws Exception {
        AtomicInteger producerCount = new AtomicInteger();
        AtomicInteger targetCount = new AtomicInteger();
        try (TestEnvironment target = TestEnvironment.createSharedListener(routing -> routing
                .post(TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    res.send(req.content().as(byte[].class));
                }))) {
            try (TestEnvironment source = TestEnvironment.createSharedListener(routing -> routing
                    .post(REDIRECT_PATH, (req, res) -> {
                        req.content().as(byte[].class);
                        res.status(Status.TEMPORARY_REDIRECT_307)
                                .header(HeaderNames.LOCATION, target.baseUri() + TARGET_PATH)
                                .send();
                    }))) {
                Http3Client client = strictClientBuilder()
                        .baseUri(source.baseUri())
                        .tls(source.clientTlsHttp3())
                        .followCrossOriginEntityRedirects(true)
                        .build();
                try {
                    IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                                  () -> client.post(REDIRECT_PATH)
                                                                          .outputStream(outputStream -> {
                                                                              producerCount.incrementAndGet();
                                                                              outputStream.write(PAYLOAD);
                                                                              outputStream.close();
                                                                          }));
                    assertThat(failure.getMessage(),
                               is("HTTP/3 cannot replay a one-shot request body after redirect status 307."));
                } finally {
                    client.closeResource();
                }
            }
        }

        assertThat(producerCount.get(), is(1));
        assertThat(targetCount.get(), is(0));
    }

    @Test
    void shouldCountOnlyFollowedRedirectsAgainstBudget() throws Exception {
        AtomicInteger firstRedirectCount = new AtomicInteger();
        AtomicInteger redirectCount = new AtomicInteger();
        AtomicInteger targetCount = new AtomicInteger();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/redirect/two", (_, res) -> {
                    firstRedirectCount.incrementAndGet();
                    res.status(Status.FOUND_302)
                            .header(HeaderNames.LOCATION, REDIRECT_PATH)
                            .send();
                })
                .get(REDIRECT_PATH, (_, res) -> {
                    redirectCount.incrementAndGet();
                    res.status(Status.FOUND_302)
                            .header(HeaderNames.LOCATION, "target")
                            .send();
                })
                .get(TARGET_PATH, (_, res) -> {
                    targetCount.incrementAndGet();
                    res.send("target");
                }))) {
            Http3Client client = newClient(environment);
            try {
                try (Http3ClientResponse response = client.get(TARGET_PATH)
                        .maxRedirects(0)
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is("target"));
                }
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                              () -> client.get(REDIRECT_PATH)
                                                                      .maxRedirects(0)
                                                                      .request());
                assertThat(failure.getMessage(), is("Maximum number of request redirections (0) reached."));
                assertThat(redirectCount.get(), is(1));
                assertThat(targetCount.get(), is(1));

                try (Http3ClientResponse response = client.get(REDIRECT_PATH)
                        .maxRedirects(1)
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is("target"));
                }

                failure = assertThrows(IllegalStateException.class,
                                       () -> client.get("/redirect/two")
                                               .maxRedirects(1)
                                               .request());
                assertThat(failure.getMessage(), is("Maximum number of request redirections (1) reached."));
            } finally {
                client.closeResource();
            }
        }

        assertThat(firstRedirectCount.get(), is(1));
        assertThat(redirectCount.get(), is(3));
        assertThat(targetCount.get(), is(2));
    }

    @Test
    void shouldResolveRedirectUriReferencesAgainstActualEndpoint() throws Exception {
        String rawQuery = "encoded=%2Fvalue&repeat=one&repeat=two";
        String rawPathTarget = "/capture/a%2Fb/%25/%2E;name=a%2Fb";
        String inheritedQueryPath = "/nested/inherit%2Fquery/%25";
        String inheritedFragmentPath = "/nested/inherit%2Ffragment/%25";
        AtomicInteger fragmentPathRequests = new AtomicInteger();
        AtomicReference<String> unexpectedTarget = new AtomicReference<>();
        try (TestEnvironment authorityTarget = TestEnvironment.createSharedListener(routing -> routing
                .get(TARGET_PATH, (_, res) -> res.send("authority")))) {
            int authorityTargetPort = URI.create(authorityTarget.baseUri()).getPort();
            try (TestEnvironment source = TestEnvironment.createSharedListener(routing -> routing
                    .get("/nested/query", (req, res) -> {
                        if (req.query().first("next").isPresent()) {
                            res.send(req.query().rawValue());
                        } else {
                            res.status(Status.FOUND_302)
                                    .header(HeaderNames.LOCATION, "?next=2")
                                    .send();
                        }
                    })
                    .get("/nested/raw-query", (req, res) -> {
                        if (req.query().rawValue().isEmpty()) {
                            res.status(Status.FOUND_302)
                                    .header(HeaderNames.LOCATION, "?" + rawQuery)
                                    .send();
                        } else {
                            res.send(req.query().rawValue());
                        }
                    })
                    .get("/nested/path-source", (_, res) -> res.status(Status.FOUND_302)
                            .header(HeaderNames.LOCATION, "path-target")
                            .send())
                    .get("/nested/path-target", (_, res) -> res.send("path"))
                    .get("/encoded-path-source", (_, res) -> res.status(Status.FOUND_302)
                            .header(HeaderNames.LOCATION, rawPathTarget)
                            .send())
                    .get("/authority-source", (_, res) -> res.status(Status.FOUND_302)
                            .header(HeaderNames.LOCATION,
                                    "//localhost:" + authorityTargetPort + TARGET_PATH)
                            .send())
                    .any((req, res) -> {
                        String rawPath = req.path().rawPath();
                        if (rawPathTarget.equals(rawPath)) {
                            res.send(rawPath + '|' + req.path().path());
                            return;
                        }
                        if (inheritedQueryPath.equals(rawPath)) {
                            if (req.query().rawValue().isEmpty()) {
                                res.status(Status.FOUND_302)
                                        .header(HeaderNames.LOCATION, "?next=%2F")
                                        .send();
                            } else {
                                res.send(rawPath + '?' + req.query().rawValue());
                            }
                            return;
                        }
                        if (inheritedFragmentPath.equals(rawPath)) {
                            if (fragmentPathRequests.getAndIncrement() == 0) {
                                res.status(Status.FOUND_302)
                                        .header(HeaderNames.LOCATION, "#next")
                                        .send();
                            } else {
                                res.send(rawPath);
                            }
                            return;
                        }
                        unexpectedTarget.set(req.path().path() + '?' + req.query().rawValue());
                        res.status(Status.NOT_FOUND_404).send();
                    }))) {
                Http3Client client = newClient(source);
                try {
                    try (Http3ClientResponse response = client.get("/nested/query")
                            .queryParam("old", "1")
                            .request()) {
                        assertThat("Unexpected redirect target: " + unexpectedTarget.get(),
                                   response.status(),
                                   is(Status.OK_200));
                        assertThat(response.as(String.class), is("next=2"));
                    }
                    try (Http3ClientResponse response = client.get("/nested/raw-query")
                            .skipUriEncoding(true)
                            .request()) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.as(String.class), is(rawQuery));
                    }
                    try (Http3ClientResponse response = client.get("/nested/path-source").request()) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.as(String.class), is("path"));
                    }
                    try (Http3ClientResponse response = client.get("/encoded-path-source").request()) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.as(String.class),
                                   is(rawPathTarget + "|/capture/a/b/%/."));
                        assertThat(response.lastEndpointUri().path().rawPath(), is(rawPathTarget));
                    }
                    try (Http3ClientResponse response = client.get(inheritedQueryPath)
                            .skipUriEncoding(true)
                            .request()) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.as(String.class),
                                   is(inheritedQueryPath + "?next=%2F"));
                        assertThat(response.lastEndpointUri().path().rawPath(), is(inheritedQueryPath));
                    }
                    try (Http3ClientResponse response = client.get(inheritedFragmentPath)
                            .skipUriEncoding(true)
                            .request()) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.as(String.class), is(inheritedFragmentPath));
                        assertThat(response.lastEndpointUri().path().rawPath(), is(inheritedFragmentPath));
                        assertThat(response.lastEndpointUri().fragment().rawValue(), is("next"));
                    }
                    try (Http3ClientResponse response = client.get("/authority-source").request()) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.as(String.class), is("authority"));
                    }
                } finally {
                    client.closeResource();
                }
            }
        }
    }

    @Test
    void shouldResolveRelativeRedirectAgainstServiceFinalEndpoint() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/actual/source", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, "target")
                        .send())
                .get("/actual/target", (_, res) -> res.send("actual"))
                .get("/nominal/target", (_, res) -> res.send("nominal")))) {
            Http3Client client = strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .addService((chain, request) -> {
                        if ("/nominal/source".equals(request.uri().path().path())) {
                            request.uri().resolve(URI.create(environment.baseUri() + "/actual/source"));
                        }
                        return chain.proceed(request);
                    })
                    .build();
            try {
                try (Http3ClientResponse response = client.get("/nominal/source").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is("actual"));
                    assertThat(response.lastEndpointUri().path().path(), is("/actual/target"));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void serviceMutationAfterProceedDoesNotChangeSentEndpointOrRedirectBase() throws Exception {
        AtomicInteger lateTargetCount = new AtomicInteger();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/actual/source", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, "target")
                        .send())
                .get("/actual/target", (_, res) -> res.send("actual"))
                .get("/late/target", (_, res) -> {
                    lateTargetCount.incrementAndGet();
                    res.send("late");
                }))) {
            Http3Client client = strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .addService((chain, request) -> {
                        if ("/nominal/source".equals(request.uri().path().path())) {
                            request.uri().resolve(URI.create(environment.baseUri() + "/actual/source"));
                        }
                        WebClientServiceResponse response = chain.proceed(request);
                        if ("/actual/source".equals(request.uri().path().path())) {
                            request.uri().resolve(URI.create(environment.baseUri() + "/late/source"));
                        }
                        return response;
                    })
                    .build();
            try {
                try (Http3ClientResponse response = client.get("/nominal/source").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is("actual"));
                    assertThat(response.lastEndpointUri().path().path(), is("/actual/target"));
                }
            } finally {
                client.closeResource();
            }
        }

        assertThat(lateTargetCount.get(), is(0));
    }

    @Test
    void shouldApplyRedirectFragmentInheritanceWithoutSendingFragment() throws Exception {
        AtomicReference<String> wireFragment = new AtomicReference<>();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/fragment/inherit", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, "target")
                        .send())
                .get("/fragment/replace", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, "target#replacement")
                        .send())
                .get("/fragment/clear", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, "target#")
                        .send())
                .get("/fragment/target", (req, res) -> {
                    wireFragment.set(req.prologue().fragment().hasValue()
                                             ? req.prologue().fragment().rawValue()
                                             : null);
                    res.send(req.path().path());
                }))) {
            Http3Client client = newClient(environment);
            try {
                try (Http3ClientResponse response = client.get("/fragment/inherit")
                        .fragment("original")
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.as(String.class), is("/fragment/target"));
                    assertThat(response.lastEndpointUri().fragment().value(), is("original"));
                    assertThat(wireFragment.get(), is(nullValue()));
                }
                try (Http3ClientResponse response = client.get("/fragment/replace")
                        .fragment("original")
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.lastEndpointUri().fragment().value(), is("replacement"));
                    assertThat(wireFragment.get(), is(nullValue()));
                }
                try (Http3ClientResponse response = client.get("/fragment/clear")
                        .fragment("original")
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.lastEndpointUri().fragment().value(), is(""));
                    assertThat(wireFragment.get(), is(nullValue()));
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldNotSendRedirectFragmentOnTcpFallback() throws Exception {
        AtomicReference<String> wireFragment = new AtomicReference<>();
        WebServer target = WebServer.builder()
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .routing(routing -> routing.get(TARGET_PATH, (req, res) -> {
                    wireFragment.set(req.prologue().fragment().hasValue()
                                             ? req.prologue().fragment().rawValue()
                                             : null);
                    res.send(req.path().path());
                }))
                .build()
                .start();
        try (TestEnvironment source = TestEnvironment.createSharedListener(routing -> routing
                .get(REDIRECT_PATH, (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION,
                                "http://localhost:" + target.port() + TARGET_PATH + "#logical")
                        .send()))) {
            Http3Client client = Http3Client.builder()
                    .baseUri(source.baseUri())
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .tls(source.clientTlsHttp3())
                    .build();
            try {
                try (Http3ClientResponse response = client.get(REDIRECT_PATH).request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.as(String.class), is(TARGET_PATH));
                    assertThat(response.lastEndpointUri().fragment().value(), is("logical"));
                }
            } finally {
                client.closeResource();
            }
        } finally {
            target.stop();
        }

        assertThat(wireFragment.get(), is(nullValue()));
    }

    @Test
    void shouldRejectOneShotBodyAfterTemporaryRedirectOnTcpFallback() {
        AtomicInteger redirectCount = new AtomicInteger();
        AtomicInteger targetCount = new AtomicInteger();
        AtomicInteger producerCount = new AtomicInteger();
        WebServer server = WebServer.builder()
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .routing(routing -> routing
                        .post(REDIRECT_PATH, (req, res) -> {
                            redirectCount.incrementAndGet();
                            req.content().as(byte[].class);
                            res.status(Status.TEMPORARY_REDIRECT_307)
                                    .header(HeaderNames.LOCATION, TARGET_PATH)
                                    .send();
                        })
                        .get(TARGET_PATH, (_, res) -> {
                            targetCount.incrementAndGet();
                            res.send();
                        })
                        .post(TARGET_PATH, (_, res) -> {
                            targetCount.incrementAndGet();
                            res.send();
                        }))
                .build()
                .start();
        Http3Client client = Http3Client.builder()
                .baseUri("http://localhost:" + server.port())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .build();

        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                          () -> client.post(REDIRECT_PATH)
                                                                  .outputStream(outputStream -> {
                                                                      producerCount.incrementAndGet();
                                                                      outputStream.write(PAYLOAD);
                                                                      outputStream.close();
                                                                  }));
            assertThat(failure.getMessage(),
                       is("HTTP/3 cannot replay a one-shot request body after redirect status 307."));
        } finally {
            client.closeResource();
            server.stop();
        }

        assertThat(producerCount.get(), is(1));
        assertThat(redirectCount.get(), is(1));
        assertThat(targetCount.get(), is(0));
    }

    @Test
    void shouldPreserveExplicitHttp3SelectionAndSniAcrossAuthorityRedirect() throws Exception {
        try (TestEnvironment target = TestEnvironment.createSharedListener(
                routing -> routing.get(TARGET_PATH, (_, res) -> res.send("target")))) {
            String targetUri = "https://target.invalid:"
                    + URI.create(target.baseUri()).getPort()
                    + TARGET_PATH;
            try (TestEnvironment source = TestEnvironment.createSharedListener(
                    routing -> routing.get(REDIRECT_PATH, (_, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                            .header(HeaderNames.LOCATION, targetUri)
                            .send()))) {
                String sourceUri = "https://source.invalid:" + URI.create(source.baseUri()).getPort();
                WebClient client = strictWebClientBuilder()
                        .baseUri(sourceUri)
                        .dnsResolver((host, lookup) -> InetAddress.getLoopbackAddress())
                        .tls(source.clientTls())
                        .addProtocolPreference(Http3Client.PROTOCOL_ID)
                        .addProtocolPreference(Http1Client.PROTOCOL_ID)
                        .build();

                try {
                    try (HttpClientResponse response = client.get(REDIRECT_PATH)
                            .sni(it -> it.mode(SniMode.EXPLICIT).host("localhost"))
                            .protocolId(Http3Client.PROTOCOL_ID)
                            .request()) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                        assertThat(response.entity().as(String.class), is("target"));
                    }
                } finally {
                    client.closeResource();
                }
            }
        }
    }

    @Test
    void genericCrossOriginRedirectReevaluatesSelectedProxyRoute() throws Exception {
        AtomicInteger proxyRequestCount = new AtomicInteger();
        AtomicInteger targetRequestCount = new AtomicInteger();
        try (TestEnvironment target = TestEnvironment.createSharedListener(routing -> routing
                .get(TARGET_PATH, (_, res) -> {
                    targetRequestCount.incrementAndGet();
                    res.send("target");
                }))) {
            String targetUri = "https://target.invalid:"
                    + URI.create(target.baseUri()).getPort()
                    + TARGET_PATH;
            WebServer proxyServer = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(0)
                    .routing(routing -> routing.get(REDIRECT_PATH, (_, res) -> {
                        proxyRequestCount.incrementAndGet();
                        res.status(Status.FOUND_302)
                                .header(HeaderNames.LOCATION, targetUri)
                                .send();
                    }))
                    .build()
                    .start();
            try {
                Proxy proxy = Proxy.builder()
                        .host("localhost")
                        .port(proxyServer.port())
                        .addNoProxy("target.invalid")
                        .build();
                WebClient client = strictWebClientBuilder()
                        .baseUri("http://source.invalid")
                        .servicesDiscoverServices(false)
                        .dnsResolver((host, lookup) -> InetAddress.getLoopbackAddress())
                        .proxy(proxy)
                        .tls(target.clientTls())
                        .addProtocolPreference(Http3Client.PROTOCOL_ID)
                        .addProtocolPreference(Http1Client.PROTOCOL_ID)
                        .build();
                try {
                    try (HttpClientResponse response = client.get(REDIRECT_PATH)
                            .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                            .request()) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                        assertThat(response.as(String.class), is("target"));
                    }
                } finally {
                    client.closeResource();
                }
            } finally {
                proxyServer.stop();
            }
        }

        assertThat(proxyRequestCount.get(), is(1));
        assertThat(targetRequestCount.get(), is(1));
    }

    private static void assertMaterializedBodyReplayed(Method method, Status redirectStatus) throws Exception {
        AtomicInteger redirectCount = new AtomicInteger();
        AtomicInteger targetCount = new AtomicInteger();
        AtomicReference<byte[]> redirectBody = new AtomicReference<>();
        AtomicReference<byte[]> targetBody = new AtomicReference<>();
        AtomicReference<String> targetContentType = new AtomicReference<>();

        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .route(method, REDIRECT_PATH, (req, res) -> {
                    redirectCount.incrementAndGet();
                    redirectBody.set(req.content().as(byte[].class));
                    res.status(redirectStatus)
                            .header(HeaderNames.LOCATION, "target")
                            .send();
                })
                .route(method, TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    byte[] body = req.content().as(byte[].class);
                    targetBody.set(body);
                    targetContentType.set(req.headers().first(HeaderNames.CONTENT_TYPE).orElse(null));
                    res.send(body);
                }))) {
            Http3Client client = newClient(environment);
            try {
                try (Http3ClientResponse response = client.method(method)
                        .uri(REDIRECT_PATH)
                        .header(HeaderNames.CONTENT_TYPE, PAYLOAD_CONTENT_TYPE)
                        .submit(PAYLOAD)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.as(byte[].class), is(PAYLOAD));
                }
            } finally {
                client.closeResource();
            }
        }

        assertThat(redirectCount.get(), is(1));
        assertThat(targetCount.get(), is(1));
        assertThat(redirectBody.get(), is(PAYLOAD));
        assertThat(targetBody.get(), is(PAYLOAD));
        assertThat(targetContentType.get(), is(PAYLOAD_CONTENT_TYPE));
    }

    private static RedirectCaptures executeRedirect(Method method, int statusCode, boolean genericClient) throws Exception {
        Status redirectStatus = switch (statusCode) {
            case 302 -> Status.FOUND_302;
            case 303 -> Status.SEE_OTHER_303;
            case 307 -> Status.TEMPORARY_REDIRECT_307;
            case 308 -> Status.PERMANENT_REDIRECT_308;
            default -> throw new IllegalArgumentException("Unsupported redirect status: " + statusCode);
        };
        AtomicInteger serviceInvocations = new AtomicInteger();
        AtomicReference<CapturedRedirectRequest> serviceRequest = new AtomicReference<>();
        AtomicReference<CapturedRedirectRequest> wireRequest = new AtomicReference<>();

        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .route(method, REDIRECT_PATH, (request, response) -> {
                    request.content().as(byte[].class);
                    response.status(redirectStatus)
                            .header(HeaderNames.LOCATION, TARGET_PATH)
                            .send();
                })
                .get(TARGET_PATH, (request, response) -> captureRedirectRequest(request, response, wireRequest))
                .post(TARGET_PATH, (request, response) -> captureRedirectRequest(request, response, wireRequest)))) {
            WebClientService service = (chain, request) -> {
                serviceInvocations.incrementAndGet();
                if (TARGET_PATH.equals(request.uri().path().path())) {
                    String contentLength = request.headers().first(HeaderNames.CONTENT_LENGTH).orElse(null);
                    boolean transferEncoding = request.headers().contains(HeaderNames.TRANSFER_ENCODING);
                    serviceRequest.set(new CapturedRedirectRequest(request.method(),
                                                                   contentLength,
                                                                   transferEncoding,
                                                                   request.headers().contains(HeaderValues.EXPECT_100),
                                                                   request.headers().first(HeaderNames.CONTENT_TYPE)
                                                                           .orElse(null),
                                                                   request.headers().first(HeaderNames.CONTENT_ENCODING)
                                                                           .orElse(null),
                                                                   request.headers().first(HeaderNames.CONTENT_LANGUAGE)
                                                                           .orElse(null),
                                                                   request.headers().first(HeaderNames.CONTENT_LOCATION)
                                                                           .orElse(null),
                                                                   new byte[0]));
                    request.headers().remove(HeaderNames.TRANSFER_ENCODING);
                    if (request.method() == Method.GET) {
                        request.headers().remove(HeaderNames.EXPECT);
                        if (contentLength != null && !"0".equals(contentLength)) {
                            request.headers().remove(HeaderNames.CONTENT_LENGTH);
                        }
                    }
                } else {
                    // Transfer-Encoding is retained on the reusable request so the redirect copy must remove it.
                    request.headers().remove(HeaderNames.TRANSFER_ENCODING);
                }
                return chain.proceed(request);
            };

            if (genericClient) {
                WebClient client = strictWebClientBuilder()
                        .baseUri(environment.baseUri())
                        .servicesDiscoverServices(false)
                        .tls(environment.clientTls())
                        .addService(service)
                        .addProtocolPreference(Http3Client.PROTOCOL_ID)
                        .addProtocolPreference(Http1Client.PROTOCOL_ID)
                        .build();
                try {
                    try (HttpClientResponse response = client.method(method)
                            .uri(REDIRECT_PATH)
                            .protocolId(Http3Client.PROTOCOL_ID)
                            .header(HeaderNames.CONTENT_TYPE, PAYLOAD_CONTENT_TYPE)
                            .header(HeaderNames.CONTENT_ENCODING, "identity")
                            .header(HeaderNames.CONTENT_LANGUAGE, "en")
                            .header(HeaderNames.CONTENT_LOCATION, "/representation")
                            .header(HeaderNames.TRANSFER_ENCODING, "chunked")
                            .header(HeaderValues.EXPECT_100)
                            .sendExpectContinue(true)
                            .readContinueTimeout(Duration.ofMillis(250))
                            .submit(PAYLOAD)) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    }
                } finally {
                    client.closeResource();
                }
            } else {
                Http3Client client = strictClientBuilder()
                        .baseUri(environment.baseUri())
                        .servicesDiscoverServices(false)
                        .tls(environment.clientTlsHttp3())
                        .addService(service)
                        .build();
                try {
                    try (Http3ClientResponse response = client.method(method)
                            .uri(REDIRECT_PATH)
                            .header(HeaderNames.CONTENT_TYPE, PAYLOAD_CONTENT_TYPE)
                            .header(HeaderNames.CONTENT_ENCODING, "identity")
                            .header(HeaderNames.CONTENT_LANGUAGE, "en")
                            .header(HeaderNames.CONTENT_LOCATION, "/representation")
                            .header(HeaderNames.TRANSFER_ENCODING, "chunked")
                            .header(HeaderValues.EXPECT_100)
                            .sendExpectContinue(true)
                            .readContinueTimeout(Duration.ofMillis(250))
                            .submit(PAYLOAD)) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    }
                } finally {
                    client.closeResource();
                }
            }
        }

        return new RedirectCaptures(serviceRequest.get(), wireRequest.get(), serviceInvocations.get());
    }

    private static void captureRedirectRequest(ServerRequest request,
                                               ServerResponse response,
                                               AtomicReference<CapturedRedirectRequest> capture) {
        byte[] body = request.content().hasEntity() ? request.content().as(byte[].class) : new byte[0];
        capture.set(new CapturedRedirectRequest(request.prologue().method(),
                                                request.headers().first(HeaderNames.CONTENT_LENGTH).orElse(null),
                                                request.headers().contains(HeaderNames.TRANSFER_ENCODING),
                                                request.headers().contains(HeaderValues.EXPECT_100),
                                                request.headers().first(HeaderNames.CONTENT_TYPE).orElse(null),
                                                request.headers().first(HeaderNames.CONTENT_ENCODING).orElse(null),
                                                request.headers().first(HeaderNames.CONTENT_LANGUAGE).orElse(null),
                                                request.headers().first(HeaderNames.CONTENT_LOCATION).orElse(null),
                                                body));
        response.send("target");
    }

    private static void assertRewrittenGet(CapturedRedirectRequest request) {
        assertThat(request.method(), is(Method.GET));
        assertThat(request.contentLength(), anyOf(nullValue(), is("0")));
        assertThat(request.transferEncoding(), is(false));
        assertThat(request.expectContinue(), is(false));
        assertThat(request.contentType(), is(nullValue()));
        assertThat(request.contentEncoding(), is(nullValue()));
        assertThat(request.contentLanguage(), is(nullValue()));
        assertThat(request.contentLocation(), is(nullValue()));
    }

    private static void assertRepresentationHeadersPreserved(CapturedRedirectRequest request) {
        assertThat(request.contentType(), is(PAYLOAD_CONTENT_TYPE));
        assertThat(request.contentEncoding(), is("identity"));
        assertThat(request.contentLanguage(), is("en"));
        assertThat(request.contentLocation(), is("/representation"));
    }

    private static void assertOneShotBodyRejected(Method method, Status redirectStatus) throws Exception {
        AtomicInteger redirectCount = new AtomicInteger();
        AtomicInteger targetCount = new AtomicInteger();
        AtomicInteger producerCount = new AtomicInteger();
        AtomicReference<byte[]> redirectBody = new AtomicReference<>();

        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .route(method, REDIRECT_PATH, (req, res) -> {
                    redirectCount.incrementAndGet();
                    redirectBody.set(req.content().as(byte[].class));
                    res.status(redirectStatus)
                            .header(HeaderNames.LOCATION, "target")
                            .send();
                })
                .route(method, TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    res.send(req.content().as(byte[].class));
                }))) {
            Http3Client client = newClient(environment);
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                              () -> client.method(method)
                                                                      .uri(REDIRECT_PATH)
                                                                      .header(HeaderNames.CONTENT_TYPE, PAYLOAD_CONTENT_TYPE)
                                                                      .outputStream(outputStream -> {
                                                                          producerCount.incrementAndGet();
                                                                          outputStream.write(PAYLOAD);
                                                                          outputStream.close();
                                                                      }));
                assertThat(failure.getMessage(),
                           is("HTTP/3 cannot replay a one-shot request body after redirect status "
                                      + redirectStatus.code() + "."));
            } finally {
                client.closeResource();
            }
        }

        assertThat(producerCount.get(), is(1));
        assertThat(redirectCount.get(), is(1));
        assertThat(targetCount.get(), is(0));
        assertThat(redirectBody.get(), is(PAYLOAD));
    }

    private static void assertCrossOriginMaterializedBodyRejected(Method method,
                                                                  Status redirectStatus,
                                                                  boolean filterRedirectHeaders) throws Exception {
        AtomicInteger redirectCount = new AtomicInteger();
        AtomicInteger targetCount = new AtomicInteger();
        try (TestEnvironment target = TestEnvironment.createSharedListener(routing -> routing
                .route(method, TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    res.send(req.content().as(byte[].class));
                }))) {
            try (TestEnvironment source = TestEnvironment.createSharedListener(routing -> routing
                    .route(method, REDIRECT_PATH, (req, res) -> {
                        redirectCount.incrementAndGet();
                        req.content().as(byte[].class);
                        res.status(redirectStatus)
                                .header(HeaderNames.LOCATION, target.baseUri() + TARGET_PATH)
                                .send();
                    }))) {
                Http3Client client = strictClientBuilder()
                        .baseUri(source.baseUri())
                        .tls(source.clientTlsHttp3())
                        .filterRedirectHeaders(filterRedirectHeaders)
                        .build();
                try {
                    IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                                  () -> client.method(method)
                                                                          .uri(REDIRECT_PATH)
                                                                          .header(HeaderNames.CONTENT_TYPE,
                                                                                  PAYLOAD_CONTENT_TYPE)
                                                                          .submit(PAYLOAD));
                    assertThat(failure.getMessage(), is("Cross-origin redirect with request entity is disabled."));
                } finally {
                    client.closeResource();
                }
            }
        }

        assertThat(redirectCount.get(), is(1));
        assertThat(targetCount.get(), is(0));
    }

    private static void assertCrossOriginMaterializedBodyAllowed(Method method, Status redirectStatus) throws Exception {
        AtomicInteger redirectCount = new AtomicInteger();
        AtomicInteger targetCount = new AtomicInteger();
        AtomicReference<String> targetContentType = new AtomicReference<>();
        try (TestEnvironment target = TestEnvironment.createSharedListener(routing -> routing
                .route(method, TARGET_PATH, (req, res) -> {
                    targetCount.incrementAndGet();
                    targetContentType.set(req.headers().first(HeaderNames.CONTENT_TYPE).orElse(null));
                    res.send(req.content().as(byte[].class));
                }))) {
            try (TestEnvironment source = TestEnvironment.createSharedListener(routing -> routing
                    .route(method, REDIRECT_PATH, (req, res) -> {
                        redirectCount.incrementAndGet();
                        req.content().as(byte[].class);
                        res.status(redirectStatus)
                                .header(HeaderNames.LOCATION, target.baseUri() + TARGET_PATH)
                                .send();
                    }))) {
                Http3Client client = strictClientBuilder()
                        .baseUri(source.baseUri())
                        .tls(source.clientTlsHttp3())
                        .followCrossOriginEntityRedirects(true)
                        .build();
                try {
                    try (Http3ClientResponse response = client.method(method)
                            .uri(REDIRECT_PATH)
                            .header(HeaderNames.CONTENT_TYPE, PAYLOAD_CONTENT_TYPE)
                            .submit(PAYLOAD)) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.as(byte[].class), is(PAYLOAD));
                    }
                } finally {
                    client.closeResource();
                }
            }
        }

        assertThat(redirectCount.get(), is(1));
        assertThat(targetCount.get(), is(1));
        assertThat(targetContentType.get(), is(PAYLOAD_CONTENT_TYPE));
    }

    private static Http3Client newClient(TestEnvironment environment) {
        return strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .build();
    }

    private record RedirectCaptures(CapturedRedirectRequest serviceRequest,
                                    CapturedRedirectRequest wireRequest,
                                    int serviceInvocations) {
    }

    private record CapturedRedirectRequest(Method method,
                                           String contentLength,
                                           boolean transferEncoding,
                                           boolean expectContinue,
                                           String contentType,
                                           String contentEncoding,
                                           String contentLanguage,
                                           String contentLocation,
                                           byte[] body) {
    }
}
