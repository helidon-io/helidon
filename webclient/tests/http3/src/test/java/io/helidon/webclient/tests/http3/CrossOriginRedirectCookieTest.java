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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class CrossOriginRedirectCookieTest {
    private static final String FALLBACK_RAW_QUERY = "encoded=%2Fvalue&repeat=one&repeat=two";

    @Test
    void pathOnlyServiceRewriteSelectsCookiesForFinalPath() throws Exception {
        AtomicReference<List<String>> capturedCookies = new AtomicReference<>();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/actual/capture", (request, response) -> {
                    capturedCookies.set(request.headers().get(HeaderNames.COOKIE).allValues());
                    response.send("captured");
                }))) {
            URI baseUri = URI.create(environment.baseUri());
            WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                    .automaticStoreEnabled(true));
            HttpCookie nominal = new HttpCookie("nominal", "wrong");
            nominal.setPath("/nominal");
            nominal.setVersion(0);
            cookieManager.getCookieStore().add(baseUri, nominal);
            HttpCookie actual = new HttpCookie("actual", "value");
            actual.setPath("/actual");
            actual.setVersion(0);
            cookieManager.getCookieStore().add(baseUri, actual);
            WebClient client = strictWebClientBuilder()
                    .baseUri(baseUri)
                    .tls(environment.clientTlsHttp3())
                    .cookieManager(cookieManager)
                    .addService((chain, request) -> {
                        if ("/nominal/capture".equals(request.uri().path().path())) {
                            request.uri().resolve(URI.create(environment.baseUri() + "/actual/capture"));
                        }
                        return chain.proceed(request);
                    })
                    .build();
            try {
                try (HttpClientResponse response = client.get("/nominal/capture").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
            } finally {
                client.closeResource();
            }
        }

        assertThat(cookieValues(capturedCookies.get()), contains("actual=value"));
    }

    @Test
    void queryOnlyServiceRewritePreservesCookieOrderAndFlags() throws Exception {
        HeaderName rewriteQuery = HeaderNames.create("x-rewrite-query");
        AtomicReference<List<String>> baselineCookies = new AtomicReference<>();
        AtomicReference<List<String>> rewrittenCookies = new AtomicReference<>();
        AtomicReference<CookieHeaderSnapshot> baselineHeader = new AtomicReference<>();
        AtomicReference<CookieHeaderSnapshot> rewrittenHeader = new AtomicReference<>();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/capture", (request, response) -> {
                    AtomicReference<List<String>> target = "rewritten=true".equals(request.query().rawValue())
                            ? rewrittenCookies
                            : baselineCookies;
                    target.set(request.headers().get(HeaderNames.COOKIE).allValues());
                    response.send("captured");
                }))) {
            URI baseUri = URI.create(environment.baseUri());
            WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                    .automaticStoreEnabled(true)
                    .putDefaultCookie("default", "value"));
            HttpCookie stored = new HttpCookie("stored", "value");
            stored.setPath("/");
            stored.setVersion(0);
            cookieManager.getCookieStore().add(baseUri, stored);
            WebClient client = strictWebClientBuilder()
                    .baseUri(baseUri)
                    .tls(environment.clientTlsHttp3())
                    .cookieManager(cookieManager)
                    .addService((chain, request) -> {
                        boolean rewrite = request.headers().contains(rewriteQuery);
                        if (rewrite) {
                            request.uri().resolve(URI.create(environment.baseUri() + "/capture?rewritten=true"));
                        }
                        List<String> serviceCookies = new ArrayList<>(request.headers()
                                                                             .get(HeaderNames.COOKIE)
                                                                             .allValues());
                        serviceCookies.add("service=value");
                        request.headers().set(HeaderValues.create(HeaderNames.COOKIE,
                                                                  true,
                                                                  true,
                                                                  serviceCookies.toArray(String[]::new)));
                        var response = chain.proceed(request);
                        Header cookie = request.headers().get(HeaderNames.COOKIE);
                        AtomicReference<CookieHeaderSnapshot> target = rewrite ? rewrittenHeader : baselineHeader;
                        target.set(new CookieHeaderSnapshot(cookie.allValues(),
                                                            cookie.changing(),
                                                            cookie.sensitive()));
                        return response;
                    })
                    .build();
            try {
                try (HttpClientResponse response = client.get("/capture")
                        .queryParam("baseline", "true")
                        .header(HeaderValues.create(HeaderNames.COOKIE, true, true, "explicit=value"))
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                try (HttpClientResponse response = client.get("/capture")
                        .queryParam("original", "true")
                        .header(HeaderValues.create(HeaderNames.COOKIE, true, true, "explicit=value"))
                        .header(HeaderValues.create(rewriteQuery, "true"))
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
            } finally {
                client.closeResource();
            }
        }

        List<String> expected = List.of("explicit=value", "stored=value", "default=value", "service=value");
        assertThat(cookieValues(baselineHeader.get().values()), is(expected));
        assertThat(cookieValues(rewrittenHeader.get().values()), is(expected));
        assertThat(cookieValues(baselineCookies.get()), is(expected));
        assertThat(cookieValues(rewrittenCookies.get()), is(expected));
        assertThat(baselineHeader.get().changing(), is(true));
        assertThat(baselineHeader.get().sensitive(), is(true));
        assertThat(rewrittenHeader.get().changing(), is(true));
        assertThat(rewrittenHeader.get().sensitive(), is(true));
    }

    @Test
    void directTcpFallbackProcessesCookiesAndResponseOnce() {
        AtomicReference<List<String>> capturedCookies = new AtomicReference<>();
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        WebServer server = WebServer.builder()
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .routing(routing -> routing.post("/fallback", (request, response) -> {
                    capturedCookies.set(request.headers().get(HeaderNames.COOKIE).allValues());
                    capturedQuery.set(request.query().rawValue());
                    request.content().as(byte[].class);
                    response.header(HeaderNames.SET_COOKIE, "response=once; Path=/").send("fallback-ok");
                }))
                .build()
                .start();
        try {
            URI baseUri = URI.create("http://localhost:" + server.port());
            CountingCookieStore cookieStore = new CountingCookieStore();
            HttpCookie stored = new HttpCookie("stored", "value");
            stored.setPath("/");
            stored.setVersion(0);
            cookieStore.add(baseUri, stored);
            int initialAdds = cookieStore.additions();
            AtomicInteger serviceInvocations = new AtomicInteger();
            WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                    .automaticStoreEnabled(true)
                    .cookieStore(cookieStore)
                    .putDefaultCookie("default", "value"));
            Http3Client client = Http3Client.builder()
                    .baseUri(baseUri)
                    .shareConnectionCache(false)
                    .servicesDiscoverServices(false)
                    .proxy(Proxy.noProxy())
                    .filterRedirectHeaders(false)
                    .cookieManager(cookieManager)
                    .addService((chain, request) -> {
                        serviceInvocations.incrementAndGet();
                        request.headers().add(HeaderNames.COOKIE, "service=value");
                        return chain.proceed(request);
                    })
                    .build();
            try {
                try (Http3ClientResponse response = client.post("/fallback?" + FALLBACK_RAW_QUERY)
                        .header(HeaderNames.COOKIE, "explicit=value")
                        .skipUriEncoding(true)
                        .submit("payload")) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.as(String.class), is("fallback-ok"));
                }
            } finally {
                client.closeResource();
            }

            assertCookiesExactlyOnce(capturedCookies.get());
            assertThat(capturedQuery.get(), is(FALLBACK_RAW_QUERY));
            assertThat(serviceInvocations.get(), is(1));
            assertThat(cookieStore.additions(), is(initialAdds + 1));
        } finally {
            server.stop();
        }
    }

    @Test
    void versionTcpFallbackProcessesCookiesAndResponseOnce() throws Exception {
        AtomicInteger http3RequestCount = new AtomicInteger();
        AtomicInteger http1RequestCount = new AtomicInteger();
        AtomicBoolean fallbackSawExpect = new AtomicBoolean();
        AtomicReference<byte[]> fallbackBody = new AtomicReference<>();
        AtomicReference<List<String>> capturedCookies = new AtomicReference<>();
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        CompletableFuture<Void> fallbackObserved = new CompletableFuture<>();

        try (Http3RawTestServer server = Http3RawTestServer.create((request, connection, streamId, stream) -> {
                 http3RequestCount.incrementAndGet();
                 try {
                     stream.requestBodyInputStream().readAllBytes();
                 } catch (IOException e) {
                     throw new UncheckedIOException("Failed to read the HTTP/3 request body.", e);
                 }
                 ((QuicConnectionImpl) connection).scheduleStopSendingFrame(streamId,
                                                                            Http3ErrorCode.VERSION_FALLBACK.code());
                 fallbackObserved.orTimeout(10, TimeUnit.SECONDS).join();
                 return null;
             })) {
            Http3TlsSupport.Http3TlsMaterials tlsMaterials = Http3TlsSupport.load();
            int port = URI.create(server.baseUri()).getPort();
            WebServer fallbackServer = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(port)
                    .bindingsDiscoverServices(false)
                    .addBinding(TcpTransportConfig.create())
                    .protocolsDiscoverServices(false)
                    .tls(tlsMaterials.serverTls())
                    .addProtocol(Http1Config.create())
                    .routing(routing -> routing.post("/fallback", (request, response) -> {
                        http1RequestCount.incrementAndGet();
                        fallbackSawExpect.set(request.headers().contains(HeaderValues.EXPECT_100));
                        capturedCookies.set(request.headers().get(HeaderNames.COOKIE).allValues());
                        capturedQuery.set(request.query().rawValue());
                        fallbackBody.set(request.content().as(byte[].class));
                        response.header(HeaderNames.SET_COOKIE, "response=once; Path=/").send("fallback-ok");
                        fallbackObserved.complete(null);
                    }))
                    .build()
                    .start();
            try {
                URI baseUri = URI.create(server.baseUri());
                CountingCookieStore cookieStore = new CountingCookieStore();
                HttpCookie stored = new HttpCookie("stored", "value");
                stored.setPath("/");
                stored.setVersion(0);
                cookieStore.add(baseUri, stored);
                int initialAdds = cookieStore.additions();
                AtomicInteger serviceInvocations = new AtomicInteger();
                WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                        .automaticStoreEnabled(true)
                        .cookieStore(cookieStore)
                        .putDefaultCookie("default", "value"));
                Http3Client client = Http3Client.builder()
                        .baseUri(baseUri)
                        .shareConnectionCache(false)
                        .servicesDiscoverServices(false)
                        .proxy(Proxy.noProxy())
                        .tls(tlsMaterials.clientTls())
                        .filterRedirectHeaders(false)
                        .cookieManager(cookieManager)
                        .addService((chain, request) -> {
                            serviceInvocations.incrementAndGet();
                            request.headers().add(HeaderNames.COOKIE, "service=value");
                            return chain.proceed(request);
                        })
                        .build();
                try {
                    try (Http3ClientResponse response = client.post("/fallback?" + FALLBACK_RAW_QUERY)
                            .header(HeaderNames.COOKIE, "explicit=value")
                            .skipUriEncoding(true)
                            .sendExpectContinue(true)
                            .readContinueTimeout(Duration.ofMillis(250))
                            .readTimeout(Duration.ofSeconds(5))
                            .submit("payload")) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                        assertThat(response.as(String.class), is("fallback-ok"));
                    }
                } finally {
                    client.closeResource();
                }

                assertCookiesExactlyOnce(capturedCookies.get());
                assertThat(capturedQuery.get(), is(FALLBACK_RAW_QUERY));
                assertThat(http3RequestCount.get(), is(1));
                assertThat(http1RequestCount.get(), is(1));
                assertThat(fallbackSawExpect.get(), is(true));
                assertThat(fallbackBody.get(), is("payload".getBytes(StandardCharsets.UTF_8)));
                assertThat(serviceInvocations.get(), is(1));
                assertThat(cookieStore.additions(), is(initialAdds + 1));
            } finally {
                fallbackServer.stop();
            }
        }
    }

    @Test
    void rebuildsCookiesUsingServiceFinalHost() throws Exception {
        AtomicReference<List<String>> targetCookies = new AtomicReference<>();
        AtomicReference<List<String>> directTargetCookies = new AtomicReference<>();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/set-route-cookie", (_, res) -> res.header(HeaderNames.SET_COOKIE, "route=wrong; Path=/").send())
                .get("/set-source-cookie", (_, res) -> res.header(HeaderNames.SET_COOKIE,
                                                                  "source=secret; Path=/").send())
                .get("/set-target-cookie", (_, res) -> res.header(HeaderNames.SET_COOKIE, "target=ok; Path=/").send())
                .get("/redirect", (_, res) -> res.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, "/capture")
                        .send())
                .get("/capture", (req, res) -> {
                    targetCookies.set(req.headers().get(HeaderNames.COOKIE).allValues());
                    res.send("captured");
                })
                .get("/capture-direct", (req, res) -> {
                    directTargetCookies.set(req.headers().get(HeaderNames.COOKIE).allValues());
                    res.send("captured");
                }))) {
            int port = URI.create(environment.baseUri()).getPort();
            String sourceAuthority = "source.invalid:" + port;
            String targetAuthority = "target.invalid:" + port;
            WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                    .automaticStoreEnabled(true)
                    .putDefaultCookie("default", "secret"));
            Http3Client client = strictClientBuilder()
                    .baseUri(environment.baseUri())
                    .tls(environment.clientTlsHttp3())
                    .cookieManager(cookieManager)
                    .addService((chain, request) -> {
                        String path = request.uri().path().path();
                        if ("/capture-direct".equals(path)) {
                            Header cookie = request.headers().get(HeaderNames.COOKIE);
                            List<String> cookies = new ArrayList<>(cookie.allValues());
                            Collections.reverse(cookies);
                            cookies.addFirst("service=value");
                            request.headers().set(HeaderValues.create(HeaderNames.COOKIE,
                                                                      cookie.changing(),
                                                                      cookie.sensitive(),
                                                                      String.join("; ", cookies)));
                        }
                        if (!"/set-route-cookie".equals(path)) {
                            request.headers().set(HeaderNames.HOST,
                                                  "/set-source-cookie".equals(path) || "/redirect".equals(path)
                                                          ? sourceAuthority
                                                          : targetAuthority);
                        }
                        return chain.proceed(request);
                    })
                    .build();
            try {
                try (Http3ClientResponse response = client.get("/set-route-cookie")
                        .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                try (Http3ClientResponse response = client.get("/set-source-cookie")
                        .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                try (Http3ClientResponse response = client.get("/set-target-cookie")
                        .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                try (Http3ClientResponse response = client.get("/capture-direct")
                        .header(HeaderNames.COOKIE, "explicit=value")
                        .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
                try (Http3ClientResponse response = client.get("/redirect")
                        .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                        .request()) {
                    assertThat(response.status(), is(Status.OK_200));
                }
            } finally {
                client.closeResource();
            }
        }

        assertThat(cookieValues(targetCookies.get()), contains("target=ok"));
        assertThat(cookieValues(directTargetCookies.get()),
                   containsInAnyOrder("service=value", "explicit=value", "target=ok", "default=secret"));
    }

    @Test
    void rebuildsOnlyTargetScopedCookiesAfterCrossingOrigin() throws Exception {
        AtomicReference<String> firstTargetCookies = new AtomicReference<>();
        AtomicReference<String> secondTargetCookies = new AtomicReference<>();

        try (TestEnvironment target = TestEnvironment.createSharedListener(routing -> routing
                .get("/set-target-cookie", (_, res) -> res.header(HeaderNames.SET_COOKIE, "target=ok; Path=/").send())
                .get("/redirect/same-origin", (req, res) -> {
                    firstTargetCookies.set(req.headers().first(HeaderNames.COOKIE).orElse(null));
                    res.status(Status.FOUND_302)
                            .header(HeaderNames.LOCATION, "/capture")
                            .send();
                })
                .get("/capture", (req, res) -> {
                    secondTargetCookies.set(req.headers().first(HeaderNames.COOKIE).orElse(null));
                    res.send("captured");
                }))) {
            String targetBaseUri = "https://target.invalid:" + URI.create(target.baseUri()).getPort();
            try (TestEnvironment source = TestEnvironment.createSharedListener(routing -> routing
                    .get("/redirect", (_, res) -> res.status(Status.FOUND_302)
                            .header(HeaderNames.SET_COOKIE, "source=secret; Path=/")
                            .header(HeaderNames.LOCATION, targetBaseUri + "/redirect/same-origin")
                            .send()))) {
                String sourceBaseUri = "https://source.invalid:" + URI.create(source.baseUri()).getPort();
                WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                        .automaticStoreEnabled(true)
                        .putDefaultCookie("default", "secret"));
                Http3Client client = strictClientBuilder()
                        .baseUri(sourceBaseUri)
                        .dnsResolver((host, lookup) -> InetAddress.getLoopbackAddress())
                        .tls(source.clientTlsHttp3())
                        .cookieManager(cookieManager)
                        .build();
                try {
                    try (Http3ClientResponse response = client.get(targetBaseUri + "/set-target-cookie")
                            .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                            .request()) {
                        assertThat(response.status(), is(Status.OK_200));
                    }

                    try (Http3ClientResponse response = client.get("/redirect")
                            .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                            .request()) {
                        assertThat(response.status(), is(Status.OK_200));
                        assertThat(response.as(String.class), is("captured"));
                    }
                } finally {
                    client.closeResource();
                }
            }
        }

        assertThat(firstTargetCookies.get(), is("target=ok"));
        assertThat(secondTargetCookies.get(), is("target=ok"));
    }

    private static void assertCookiesExactlyOnce(List<String> headerValues) {
        assertThat(cookieValues(headerValues),
                   containsInAnyOrder("explicit=value", "stored=value", "default=value", "service=value"));
    }

    private static List<String> cookieValues(List<String> headerValues) {
        return headerValues.stream()
                .flatMap(value -> List.of(value.split(";\\s*")).stream())
                .toList();
    }

    private static final class CountingCookieStore implements CookieStore {
        private final CookieStore delegate = new CookieManager().getCookieStore();
        private final AtomicInteger additions = new AtomicInteger();

        @Override
        public void add(URI uri, HttpCookie cookie) {
            additions.incrementAndGet();
            delegate.add(uri, cookie);
        }

        @Override
        public List<HttpCookie> get(URI uri) {
            return delegate.get(uri);
        }

        @Override
        public List<HttpCookie> getCookies() {
            return delegate.getCookies();
        }

        @Override
        public List<URI> getURIs() {
            return delegate.getURIs();
        }

        @Override
        public boolean remove(URI uri, HttpCookie cookie) {
            return delegate.remove(uri, cookie);
        }

        @Override
        public boolean removeAll() {
            return delegate.removeAll();
        }

        private int additions() {
            return additions.get();
        }
    }

    private record CookieHeaderSnapshot(List<String> values, boolean changing, boolean sensitive) {
        private CookieHeaderSnapshot {
            values = List.copyOf(values);
        }
    }
}
