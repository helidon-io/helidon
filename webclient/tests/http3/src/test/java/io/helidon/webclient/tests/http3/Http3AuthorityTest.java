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

import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.http3.Http3Client;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class Http3AuthorityTest {
    private static final HeaderName AUTHORITY = HeaderNames.create(":authority");

    @Test
    void directClientUsesConfiguredAuthorityForCookiesAndSni() throws Exception {
        assertConfiguredAuthority(false);
    }

    @Test
    void genericClientUsesConfiguredAuthorityForCookiesAndSni() throws Exception {
        assertConfiguredAuthority(true);
    }

    @Test
    void directClientUsesServiceAuthorityForRedirectIsolation() throws Exception {
        assertServiceAuthorityRedirect(false);
    }

    @Test
    void genericClientUsesServiceAuthorityForRedirectIsolation() throws Exception {
        assertServiceAuthorityRedirect(true);
    }

    private static void assertConfiguredAuthority(boolean genericClient) throws Exception {
        AtomicReference<String> receivedHost = new AtomicReference<>();
        AtomicReference<List<String>> receivedCookies = new AtomicReference<>();
        AtomicReference<List<String>> provisionalCookies = new AtomicReference<>();
        AtomicReference<Header> sentHost = new AtomicReference<>();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/capture", (request, response) -> {
                    receivedHost.set(request.headers().get(HeaderNames.HOST).get());
                    receivedCookies.set(request.headers().get(HeaderNames.COOKIE).allValues());
                    response.header(HeaderNames.SET_COOKIE, "response=stored; Path=/").send("captured");
                }))) {
            int port = URI.create(environment.baseUri()).getPort();
            URI routeUri = URI.create("https://route.invalid:" + port);
            URI authorityUri = URI.create("https://localhost:" + port);
            String authority = authorityUri.getAuthority();
            WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                    .automaticStoreEnabled(true));
            storeCookie(cookieManager, routeUri, "route", "wrong");
            storeCookie(cookieManager, authorityUri, "authority", "selected");
            ClientRequestHeaders configured = ClientRequestHeaders.create(WritableHeaders.create());
            configured.set(HeaderNames.HOST, "configured.invalid:" + port);
            configured.set(HeaderValues.create(AUTHORITY, true, true, authority));
            WebClient client = strictWebClientBuilder()
                    .baseUri(routeUri)
                    .servicesDiscoverServices(false)
                    .dnsResolver((_, _) -> InetAddress.getLoopbackAddress())
                    .sni(sni -> sni.mode(SniMode.HOST_HEADER))
                    .tls(environment.clientTlsHttp3())
                    .cookieManager(cookieManager)
                    .addService((chain, request) -> {
                        provisionalCookies.set(request.headers().get(HeaderNames.COOKIE).allValues());
                        request.headers().set(HeaderNames.HOST, "service.invalid:" + port);
                        var response = chain.proceed(request);
                        sentHost.set(request.headers().get(HeaderNames.HOST));
                        return response;
                    })
                    .build();
            Http3Client http3Client = client.client(Http3Client.PROTOCOL);
            try {
                ClientRequest<?> request = genericClient
                        ? client.get("/capture").protocolId(Http3Client.PROTOCOL_ID)
                        : http3Client.get("/capture");
                request.headers(configured);
                try (HttpClientResponse response = request.request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.as(String.class), is("captured"));
                }

                assertThat(receivedHost.get(), is(authority));
                assertThat(provisionalCookies.get(), containsInAnyOrder("authority=selected"));
                assertThat(receivedCookies.get(), containsInAnyOrder("authority=selected"));
                assertThat(sentHost.get().get(), is(authority));
                assertThat(sentHost.get().changing(), is(true));
                assertThat(sentHost.get().sensitive(), is(true));
                assertThat(configured.get(AUTHORITY).get(), is(authority));
                assertThat(configured.get(HeaderNames.HOST).get(), is("configured.invalid:" + port));
                assertThat(cookieManager.getCookieStore().get(authorityUri).stream().map(HttpCookie::getName).toList(),
                           containsInAnyOrder("authority", "response"));
                assertThat(cookieManager.getCookieStore().get(routeUri).stream().map(HttpCookie::getName).toList(),
                           containsInAnyOrder("route"));
            } finally {
                http3Client.closeResource();
                client.closeResource();
            }
        }
    }

    private static void assertServiceAuthorityRedirect(boolean genericClient) throws Exception {
        AtomicReference<String> receivedHost = new AtomicReference<>();
        AtomicReference<List<String>> receivedCookies = new AtomicReference<>();
        AtomicReference<Boolean> receivedAuthorization = new AtomicReference<>();
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/redirect", (_, response) -> response.status(Status.FOUND_302)
                        .header(HeaderNames.LOCATION, "/capture")
                        .send())
                .get("/capture", (request, response) -> {
                    receivedHost.set(request.headers().get(HeaderNames.HOST).get());
                    receivedCookies.set(request.headers().get(HeaderNames.COOKIE).allValues());
                    receivedAuthorization.set(request.headers().contains(HeaderNames.AUTHORIZATION));
                    response.send("captured");
                }))) {
            int port = URI.create(environment.baseUri()).getPort();
            URI sourceUri = URI.create("https://source.invalid:" + port);
            URI targetUri = URI.create("https://target.invalid:" + port);
            WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                    .automaticStoreEnabled(true)
                    .putDefaultCookie("default", "secret"));
            storeCookie(cookieManager, sourceUri, "source", "secret");
            storeCookie(cookieManager, targetUri, "target", "selected");
            WebClient client = strictWebClientBuilder()
                    .baseUri(environment.baseUri())
                    .servicesDiscoverServices(false)
                    .dnsResolver((_, _) -> InetAddress.getLoopbackAddress())
                    .sni(sni -> sni.mode(SniMode.EXPLICIT).host("localhost"))
                    .tls(environment.clientTlsHttp3())
                    .cookieManager(cookieManager)
                    .addService((chain, request) -> {
                        boolean source = "/redirect".equals(request.uri().path().path());
                        request.headers().set(HeaderNames.AUTHORIZATION, "Bearer secret");
                        request.headers().set(AUTHORITY, source ? sourceUri.getAuthority() : targetUri.getAuthority());
                        var response = chain.proceed(request);
                        if (source) {
                            request.headers().set(AUTHORITY, targetUri.getAuthority());
                        }
                        return response;
                    })
                    .build();
            Http3Client http3Client = client.client(Http3Client.PROTOCOL);
            try {
                ClientRequest<?> request = genericClient
                        ? client.get("/redirect").protocolId(Http3Client.PROTOCOL_ID)
                        : http3Client.get("/redirect");
                request.header(HeaderNames.HOST, "configured.invalid:" + port);
                try (HttpClientResponse response = request.request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.as(String.class), is("captured"));
                }

                assertThat(receivedHost.get(), is(targetUri.getAuthority()));
                assertThat(receivedCookies.get(), containsInAnyOrder("target=selected"));
                assertThat(receivedAuthorization.get(), is(false));
            } finally {
                http3Client.closeResource();
                client.closeResource();
            }
        }
    }

    private static void storeCookie(WebClientCookieManager cookieManager, URI uri, String name, String value) {
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setVersion(0);
        cookie.setPath("/");
        cookieManager.getCookieStore().add(uri, cookie);
    }
}
