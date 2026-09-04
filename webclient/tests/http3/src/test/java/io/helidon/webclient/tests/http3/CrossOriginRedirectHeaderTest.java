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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class CrossOriginRedirectHeaderTest {
    private static final HeaderName API_KEY_HEADER = HeaderNames.create("X-Api-Key");
    private static final HeaderName REQUEST_HEADER = HeaderNames.create("X-Request-Value");
    private static final String AUTHORIZATION = "Bearer secret-token";
    private static final String API_KEY = "key-0xDEADBEEF";
    private static final String COOKIE = "session=secret";
    private static final String PROXY_AUTHORIZATION = "Basic proxy-secret";

    private static final AtomicReference<CapturedHeaders> SAME_ORIGIN_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<CapturedHeaders> CROSS_ORIGIN_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<CapturedHeaders> FOREIGN_SAME_ORIGIN_CAPTURE = new AtomicReference<>();
    private static final AtomicReference<CapturedHeaders> FALLBACK_CAPTURE = new AtomicReference<>();

    private static TestEnvironment trustedEnvironment;
    private static TestEnvironment redirectTargetEnvironment;
    private static WebServer fallbackTargetServer;

    @BeforeAll
    static void beforeAll() throws Exception {
        fallbackTargetServer = WebServer.builder()
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .routing(rules -> rules.get("/capture", (req, res) -> {
                    FALLBACK_CAPTURE.set(capturedHeaders(req));
                    res.send("captured");
                }))
                .build()
                .start();

        redirectTargetEnvironment = TestEnvironment.createSharedListener(rules -> rules
                .get("/capture", (req, res) -> {
                    CROSS_ORIGIN_CAPTURE.set(capturedHeaders(req));
                    res.send("captured");
                })
                .get("/redirect/same-origin", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, "/capture-after-cross-origin")
                        .send())
                .get("/capture-after-cross-origin", (req, res) -> {
                    FOREIGN_SAME_ORIGIN_CAPTURE.set(capturedHeaders(req));
                    res.send("captured");
                }));

        trustedEnvironment = TestEnvironment.createSharedListener(rules -> rules
                .get("/redirect/cross-origin", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, redirectTargetEnvironment.baseUri() + "/capture")
                        .send())
                .get("/redirect/same-origin", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, "/capture")
                        .send())
                .get("/redirect/service-rewrite", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, "/service-rewrite-target")
                        .send())
                .get("/redirect/cross-then-same-origin", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION,
                                redirectTargetEnvironment.baseUri() + "/redirect/same-origin")
                        .send())
                .get("/redirect/tcp-fallback", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION,
                                "http://localhost:" + fallbackTargetServer.port() + "/capture")
                        .send())
                .get("/capture", (req, res) -> {
                    SAME_ORIGIN_CAPTURE.set(capturedHeaders(req));
                    res.send("captured");
                }));
    }

    @AfterAll
    static void afterAll() {
        if (trustedEnvironment != null) {
            trustedEnvironment.close();
        }
        if (redirectTargetEnvironment != null) {
            redirectTargetEnvironment.close();
        }
        if (fallbackTargetServer != null) {
            fallbackTargetServer.stop();
        }
    }

    @BeforeEach
    void resetCapturedHeaders() {
        SAME_ORIGIN_CAPTURE.set(null);
        CROSS_ORIGIN_CAPTURE.set(null);
        FOREIGN_SAME_ORIGIN_CAPTURE.set(null);
        FALLBACK_CAPTURE.set(null);
    }

    @Test
    void stripsFinalizedSensitiveHeadersOnCrossOriginRedirect() {
        Http3Client client = newClient(true);
        try {
            try (Http3ClientResponse response = client.get("/redirect/cross-origin").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }

        assertStripped(CROSS_ORIGIN_CAPTURE.get());
    }

    @Test
    void preservesFinalizedSensitiveHeadersOnSameOriginRedirect() {
        Http3Client client = newClient(true);
        try {
            try (Http3ClientResponse response = client.get("/redirect/same-origin").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }

        assertRetained(SAME_ORIGIN_CAPTURE.get());
    }

    @Test
    void preservesPerRequestHeadersOnSameOriginRedirect() {
        Http3Client client = newClient(true);
        try {
            try (Http3ClientResponse response = client.get("/redirect/same-origin")
                    .header(REQUEST_HEADER, "request-value")
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
            }
        } finally {
            client.closeResource();
        }

        assertThat(SAME_ORIGIN_CAPTURE.get().requestValue(), is("request-value"));
    }

    @Test
    void doesNotRestoreSensitiveHeadersAfterCrossingOrigin() {
        Http3Client client = newClient(true);
        try {
            try (Http3ClientResponse response = client.get("/redirect/cross-then-same-origin").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }

        assertStripped(FOREIGN_SAME_ORIGIN_CAPTURE.get());
    }

    @Test
    void genericWebClientStripsFinalizedSensitiveHeadersOnCrossOriginRedirect() {
        WebClient client = newWebClient();
        try {
            try (HttpClientResponse response = client.get("/redirect/cross-origin").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }

        assertStripped(CROSS_ORIGIN_CAPTURE.get());
    }

    @Test
    void genericWebClientDoesNotRestoreSensitiveHeadersAfterCrossingOrigin() {
        WebClient client = newWebClient();
        try {
            try (HttpClientResponse response = client.get("/redirect/cross-then-same-origin").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }

        assertStripped(FOREIGN_SAME_ORIGIN_CAPTURE.get());
    }

    @Test
    void stripsSensitiveHeadersAfterRedirectServiceRewritesOrigin() {
        WebClientService service = (chain, request) -> {
            request.headers().set(HeaderNames.AUTHORIZATION, AUTHORIZATION);
            if ("/service-rewrite-target".equals(request.uri().path().path())) {
                request.uri().resolve(URI.create(redirectTargetEnvironment.baseUri() + "/capture"));
            }
            return chain.proceed(request);
        };
        Http3Client client = strictClientBuilder()
                .baseUri(trustedEnvironment.baseUri())
                .tls(trustedEnvironment.clientTlsHttp3())
                .addHeader(API_KEY_HEADER, API_KEY)
                .addHeader(HeaderNames.COOKIE, COOKIE)
                .addHeader(HeaderNames.PROXY_AUTHORIZATION, PROXY_AUTHORIZATION)
                .addRedirectSensitiveHeader(API_KEY_HEADER)
                .addService(service)
                .build();
        try {
            try (Http3ClientResponse response = client.get("/redirect/service-rewrite").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }

        assertStripped(CROSS_ORIGIN_CAPTURE.get());
    }

    @Test
    void typedClientUsesFinalHostAndKeepsActualSendSnapshot() {
        String sourceAuthority = "source.invalid:" + URI.create(trustedEnvironment.baseUri()).getPort();
        String targetAuthority = "target.invalid:" + URI.create(trustedEnvironment.baseUri()).getPort();
        WebClientService service = (chain, request) -> {
            boolean redirectSource = "/redirect/same-origin".equals(request.uri().path().path());
            request.headers().set(HeaderNames.AUTHORIZATION, AUTHORIZATION);
            request.headers().set(HeaderNames.HOST, redirectSource ? sourceAuthority : targetAuthority);
            WebClientServiceResponse response = chain.proceed(request);
            if (redirectSource) {
                request.headers().set(HeaderNames.HOST, targetAuthority);
            }
            return response;
        };
        Http3Client client = strictClientBuilder()
                .baseUri(trustedEnvironment.baseUri())
                .tls(trustedEnvironment.clientTlsHttp3())
                .addHeader(API_KEY_HEADER, API_KEY)
                .addHeader(HeaderNames.COOKIE, COOKIE)
                .addHeader(HeaderNames.PROXY_AUTHORIZATION, PROXY_AUTHORIZATION)
                .addRedirectSensitiveHeader(API_KEY_HEADER)
                .addService(service)
                .build();
        try {
            try (Http3ClientResponse response = client.get("/redirect/same-origin")
                    .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }

        CapturedHeaders captured = SAME_ORIGIN_CAPTURE.get();
        assertStripped(captured);
        assertThat(captured.host(), is(targetAuthority));
    }

    @Test
    void genericClientUsesFinalHostForRedirectBoundary() {
        String sourceAuthority = "source.invalid:" + URI.create(trustedEnvironment.baseUri()).getPort();
        String targetAuthority = "target.invalid:" + URI.create(trustedEnvironment.baseUri()).getPort();
        WebClientService service = (chain, request) -> {
            request.headers().set(HeaderNames.AUTHORIZATION, AUTHORIZATION);
            request.headers().set(HeaderNames.HOST,
                                  "/redirect/same-origin".equals(request.uri().path().path())
                                          ? sourceAuthority
                                          : targetAuthority);
            return chain.proceed(request);
        };
        WebClient client = strictWebClientBuilder()
                .baseUri(trustedEnvironment.baseUri())
                .tls(trustedEnvironment.clientTlsHttp3())
                .addHeader(API_KEY_HEADER, API_KEY)
                .addHeader(HeaderNames.COOKIE, COOKIE)
                .addHeader(HeaderNames.PROXY_AUTHORIZATION, PROXY_AUTHORIZATION)
                .addRedirectSensitiveHeader(API_KEY_HEADER)
                .addService(service)
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .build();
        try {
            try (HttpClientResponse response = client.get("/redirect/same-origin")
                    .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }

        CapturedHeaders captured = SAME_ORIGIN_CAPTURE.get();
        assertStripped(captured);
        assertThat(captured.host(), is(targetAuthority));
    }

    @Test
    void preservesSensitiveHeadersWhenFinalHostRemainsSame() {
        String authority = "source.invalid:" + URI.create(trustedEnvironment.baseUri()).getPort();
        WebClientService service = (chain, request) -> {
            request.headers().set(HeaderNames.AUTHORIZATION, AUTHORIZATION);
            request.headers().set(HeaderNames.HOST, authority);
            return chain.proceed(request);
        };
        Http3Client client = strictClientBuilder()
                .baseUri(trustedEnvironment.baseUri())
                .tls(trustedEnvironment.clientTlsHttp3())
                .addHeader(API_KEY_HEADER, API_KEY)
                .addHeader(HeaderNames.COOKIE, COOKIE)
                .addHeader(HeaderNames.PROXY_AUTHORIZATION, PROXY_AUTHORIZATION)
                .addRedirectSensitiveHeader(API_KEY_HEADER)
                .addService(service)
                .build();
        try {
            try (Http3ClientResponse response = client.get("/redirect/same-origin")
                    .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
            }
        } finally {
            client.closeResource();
        }

        CapturedHeaders captured = SAME_ORIGIN_CAPTURE.get();
        assertRetained(captured);
        assertThat(captured.host(), is(authority));
    }

    @Test
    void stripsFinalizedSensitiveHeadersBeforeTcpFallback() {
        AtomicInteger serviceInvocations = new AtomicInteger();
        Http3Client client = Http3Client.builder()
                .baseUri(trustedEnvironment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(trustedEnvironment.clientTlsHttp3())
                .addHeader(API_KEY_HEADER, API_KEY)
                .addHeader(HeaderNames.COOKIE, COOKIE)
                .addHeader(HeaderNames.PROXY_AUTHORIZATION, PROXY_AUTHORIZATION)
                .addRedirectSensitiveHeader(API_KEY_HEADER)
                .addService(new AuthorizationService(serviceInvocations))
                .build();
        try {
            try (Http3ClientResponse response = client.get("/redirect/tcp-fallback").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }

        assertStripped(FALLBACK_CAPTURE.get());
        assertThat(serviceInvocations.get(), is(2));
    }

    @Test
    void preservesSensitiveHeadersWhenFilteringIsDisabled() {
        Http3Client client = newClient(false);
        try {
            try (Http3ClientResponse response = client.get("/redirect/cross-origin").request()) {
                assertThat(response.status(), is(Status.OK_200));
            }
        } finally {
            client.closeResource();
        }

        assertRetained(CROSS_ORIGIN_CAPTURE.get());
    }

    private static Http3Client newClient(boolean filterRedirectHeaders) {
        return strictClientBuilder()
                .baseUri(trustedEnvironment.baseUri())
                .tls(trustedEnvironment.clientTlsHttp3())
                .addHeader(API_KEY_HEADER, API_KEY)
                .addHeader(HeaderNames.COOKIE, COOKIE)
                .addHeader(HeaderNames.PROXY_AUTHORIZATION, PROXY_AUTHORIZATION)
                .addRedirectSensitiveHeader(API_KEY_HEADER)
                .addService(new AuthorizationService())
                .filterRedirectHeaders(filterRedirectHeaders)
                .build();
    }

    private static WebClient newWebClient() {
        return strictWebClientBuilder()
                .baseUri(trustedEnvironment.baseUri())
                .tls(trustedEnvironment.clientTlsHttp3())
                .addHeader(API_KEY_HEADER, API_KEY)
                .addHeader(HeaderNames.COOKIE, COOKIE)
                .addHeader(HeaderNames.PROXY_AUTHORIZATION, PROXY_AUTHORIZATION)
                .addRedirectSensitiveHeader(API_KEY_HEADER)
                .addService(new AuthorizationService())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .build();
    }

    private static void assertStripped(CapturedHeaders captured) {
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(nullValue()));
        assertThat(captured.apiKey(), is(nullValue()));
        assertThat(captured.cookie(), is(nullValue()));
        assertThat(captured.proxyAuthorization(), is(nullValue()));
    }

    private static void assertRetained(CapturedHeaders captured) {
        assertThat(captured, is(notNullValue()));
        assertThat(captured.authorization(), is(AUTHORIZATION));
        assertThat(captured.apiKey(), is(API_KEY));
        assertThat(captured.cookie(), is(COOKIE));
        assertThat(captured.proxyAuthorization(), is(PROXY_AUTHORIZATION));
    }

    private static CapturedHeaders capturedHeaders(ServerRequest request) {
        return new CapturedHeaders(request.headers().first(HeaderNames.AUTHORIZATION).orElse(null),
                                   request.headers().first(API_KEY_HEADER).orElse(null),
                                   request.headers().first(HeaderNames.COOKIE).orElse(null),
                                   request.headers().first(HeaderNames.PROXY_AUTHORIZATION).orElse(null),
                                   request.headers().first(HeaderNames.HOST).orElse(null),
                                   request.headers().first(REQUEST_HEADER).orElse(null));
    }

    private static final class AuthorizationService implements WebClientService {
        private final AtomicInteger invocations;

        private AuthorizationService() {
            this(new AtomicInteger());
        }

        private AuthorizationService(AtomicInteger invocations) {
            this.invocations = invocations;
        }

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            invocations.incrementAndGet();
            request.headers().set(HeaderNames.AUTHORIZATION, AUTHORIZATION);
            return chain.proceed(request);
        }
    }

    private record CapturedHeaders(String authorization,
                                   String apiKey,
                                   String cookie,
                                   String proxyAuthorization,
                                   String host,
                                   String requestValue) {
    }
}
