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

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webserver.http.AltSvc;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class Http3AltSvcUpgradeTest {
    private static final String HELLO = "Hello";
    private static final HeaderName ALT_USED = HeaderNames.create("Alt-Used");
    private static final HeaderName ALT_USED_ECHO = HeaderNames.create("Alt-Used-Echo");
    private static final HeaderName SOCKET_ID = HeaderNames.create("Socket-Id");

    private TestEnvironment environment;

    @BeforeEach
    void beforeEach() throws Exception {
        environment = TestEnvironment.createSharedListener(Http1Config.builder()
                                                               .altSvc(AltSvc.builder().build())
                                                               .build(),
                                                           routing -> routing.get("/hello", (req, res) -> {
                                                               req.headers().first(ALT_USED)
                                                                       .ifPresent(value -> res.header(ALT_USED_ECHO, value));
                                                               res.header(SOCKET_ID, req.socketId());
                                                               res.send(HELLO);
                                                           }));
    }

    @AfterEach
    void afterEach() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void shouldUpgradeGenericClientToHttp3AfterAltSvcIsLearned() {
        WebClient client = newClient(Http3ClientProtocolConfig.create(),
                                     ClientAltSvcConfig.builder()
                                             .protocols(Set.of(Http3Client.PROTOCOL_ID))
                                             .build());

        try {
            try (HttpClientResponse firstResponse = client.get("/hello").request()) {
                assertThat(firstResponse.status(), is(Status.OK_200));
                assertThat(firstResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(firstResponse.headers().contains(HeaderNames.ALT_SVC), is(true));
                assertThat(firstResponse.headers().contains(ALT_USED_ECHO), is(false));
                assertThat(firstResponse.entity().as(String.class), is(HELLO));
            }

            try (HttpClientResponse secondResponse = client.get("/hello").request()) {
                assertThat(secondResponse.status(), is(Status.OK_200));
                assertThat(secondResponse.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(secondResponse.headers().first(ALT_USED_ECHO).orElseThrow(),
                           is("localhost:" + URI.create(environment.baseUri()).getPort()));
                assertThat(secondResponse.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldUpgradeServicedGenericClientAfterAltSvcIsLearned() {
        WebClient client = WebClient.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .keepAlive(false)
                .proxy(Proxy.noProxy())
                .tls(environment.clientTls())
                .altSvc(ClientAltSvcConfig.create())
                .addService((chain, request) -> chain.proceed(request))
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(Http3ClientProtocolConfig.create())
                .build();

        try {
            try (HttpClientResponse firstResponse = client.get("/hello").request()) {
                assertThat(firstResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(firstResponse.headers().contains(HeaderNames.ALT_SVC), is(true));
                assertThat(firstResponse.entity().as(String.class), is(HELLO));
            }

            try (HttpClientResponse secondResponse = client.get("/hello").request()) {
                assertThat(secondResponse.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(secondResponse.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldIgnoreAltSvcWhenNotConfigured() {
        WebClient client = clientBuilder(Http3ClientProtocolConfig.create(), environment.clientTls()).build();

        assertAltSvcIgnored(client);
    }

    @Test
    void shouldIgnoreAltSvcWhenDisabled() {
        WebClient client = newClient(Http3ClientProtocolConfig.create(),
                                     ClientAltSvcConfig.builder()
                                             .enabled(false)
                                             .build());

        assertAltSvcIgnored(client);
    }

    @Test
    void shouldIgnoreAltSvcWhenHttp3IsNotAllowed() {
        WebClient client = newClient(Http3ClientProtocolConfig.create(),
                                     ClientAltSvcConfig.builder()
                                             .protocols(Set.of("H3"))
                                             .build());

        assertAltSvcIgnored(client);
    }

    @Test
    void shouldNotShareLearnedAlternativeWithProtocolRestrictedClient() {
        Tls tls = environment.clientTls();
        WebClient enabled = clientBuilder(Http3ClientProtocolConfig.create(), tls, true)
                .altSvc(ClientAltSvcConfig.create())
                .build();
        WebClient restricted = clientBuilder(Http3ClientProtocolConfig.create(), tls, true)
                .altSvc(ClientAltSvcConfig.builder()
                                .protocols(Set.of("h2"))
                                .build())
                .build();

        try {
            try (HttpClientResponse first = enabled.get("/hello").request();
                 HttpClientResponse second = enabled.get("/hello").request();
                 HttpClientResponse restrictedResponse = restricted.get("/hello").request()) {
                assertThat(first.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(restrictedResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
            }
        } finally {
            restricted.closeResource();
            enabled.closeResource();
        }
    }

    @Test
    void shouldApplyAltSvcClearFromHttp3Response() {
        replaceEnvironment(Http1Config.builder()
                                   .altSvc(AltSvc.builder().build())
                                   .build(),
                           routing -> routing.get("/hello", (req, res) -> {
                               if ("3".equals(req.prologue().protocolVersion())) {
                                   res.header(HeaderNames.ALT_SVC, "clear");
                               }
                               res.send(HELLO);
                           }));
        WebClient client = newClient(Http3ClientProtocolConfig.create());

        try {
            HttpClientRequest request = client.get("/hello");
            try (HttpClientResponse first = request.request();
                 HttpClientResponse second = request.request();
                 HttpClientResponse third = request.request()) {
                assertThat(first.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(second.headers().first(HeaderNames.ALT_SVC).orElseThrow(), is("clear"));
                assertThat(third.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(third.headers().contains(ALT_USED_ECHO), is(false));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldApplyRawHttp3ClearBeforeManagedServiceUnwinds() {
        replaceEnvironment(Http1Config.builder()
                                   .altSvc(AltSvc.builder().build())
                                   .build(),
                           routing -> routing.get("/hello", (req, res) -> {
                               if ("3".equals(req.prologue().protocolVersion())) {
                                   res.header(HeaderNames.ALT_SVC, "clear");
                               }
                               res.send(HELLO);
                           }));
        AtomicReference<WebClient> clientRef = new AtomicReference<>();
        AtomicReference<String> nestedProtocol = new AtomicReference<>();
        WebClient client = clientBuilder(Http3ClientProtocolConfig.create(), environment.clientTls())
                .altSvc(ClientAltSvcConfig.create())
                .addService((chain, request) -> {
                    WebClientServiceResponse response = chain.proceed(request);
                    if (response.headers().first(HeaderNames.ALT_SVC).filter("clear"::equals).isPresent()) {
                        try (HttpClientResponse nested = clientRef.get().get("/hello").request()) {
                            nestedProtocol.set(nested.protocolId());
                            assertThat(nested.entity().as(String.class), is(HELLO));
                        }
                    }
                    WritableHeaders<?> visibleHeaders = WritableHeaders.create(response.headers());
                    visibleHeaders.remove(HeaderNames.ALT_SVC);
                    return WebClientServiceResponse.builder(response)
                            .headers(ClientResponseHeaders.create(visibleHeaders))
                            .build();
                })
                .build();
        clientRef.set(client);

        try {
            try (HttpClientResponse first = client.get("/hello").request();
                 HttpClientResponse second = client.get("/hello").request()) {
                assertThat(first.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(first.headers().contains(HeaderNames.ALT_SVC), is(false));
                assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(second.headers().contains(HeaderNames.ALT_SVC), is(false));
            }
            assertThat(nestedProtocol.get(), is(Http1Client.PROTOCOL_ID));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldReuseEstablishedAlternativeAfterAdvertisementExpires() {
        replaceEnvironment(Http1Config.builder()
                                   .altSvc(AltSvc.builder()
                                                   .maxAge(Duration.ofSeconds(30))
                                                   .build())
                                   .build(),
                           routing -> routing.get("/hello", (req, res) -> {
                               if ("3".equals(req.prologue().protocolVersion())) {
                                   int port = URI.create(environment.baseUri()).getPort();
                                   res.header(HeaderNames.ALT_SVC, "h3=\":" + port + "\"; ma=0");
                               }
                               res.send(HELLO);
                           }));
        WebClient client = newClient(Http3ClientProtocolConfig.create());

        try {
            try (HttpClientResponse first = client.get("/hello").request();
                 HttpClientResponse second = client.get("/hello").request();
                 HttpClientResponse third = client.get("/hello").request()) {
                assertThat(first.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(third.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldRemoveAlternativeAndIgnoreAdvertisementOnMisdirectedResponse() {
        replaceEnvironment(Http1Config.builder()
                                   .altSvc(AltSvc.builder().build())
                                   .build(),
                           routing -> routing.get("/hello", (req, res) -> {
                               if ("3".equals(req.prologue().protocolVersion())) {
                                   res.status(Status.MISDIRECTED_REQUEST_421)
                                           .header(HeaderNames.ALT_SVC, "h3=\":443\"")
                                           .send();
                               } else {
                                   res.send(HELLO);
                               }
                           }));
        WebClient client = newClient(Http3ClientProtocolConfig.create());

        try {
            try (HttpClientResponse first = client.get("/hello").request();
                 HttpClientResponse second = client.get("/hello").request();
                 HttpClientResponse third = client.get("/hello").request()) {
                assertThat(first.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(second.status(), is(Status.MISDIRECTED_REQUEST_421));
                assertThat(third.protocolId(), is(Http1Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldRelearnAlternativeAfterSameTlsInstanceReloads() {
        Tls tls = environment.clientTls();
        WebClient client = newClient(Http3ClientProtocolConfig.create(), tls);

        try {
            String oldSocketId;
            try (HttpClientResponse first = client.get("/hello").request();
                 HttpClientResponse second = client.get("/hello").request()) {
                assertThat(first.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(second.protocolId(), is(Http3Client.PROTOCOL_ID));
                oldSocketId = second.headers().first(SOCKET_ID).orElseThrow();
            }

            tls.reload(TlsMaterial.builder().trustAll(true).build());

            try (HttpClientResponse third = client.get("/hello").request();
                 HttpClientResponse fourth = client.get("/hello").request()) {
                assertThat(third.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(third.headers().contains(HeaderNames.ALT_SVC), is(true));
                assertThat(fourth.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(fourth.headers().first(SOCKET_ID).orElseThrow(), not(oldSocketId));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldAssociateAltSvcWithRedirectedEndpoint() throws Exception {
        Http1Config advertised = Http1Config.builder()
                .altSvc(AltSvc.builder().build())
                .build();
        try (TestEnvironment target = TestEnvironment.createSharedListener(
                advertised,
                routing -> routing.get("/hello", (req, res) -> res.send(HELLO)))) {
            URI targetUri = URI.create(target.baseUri() + "/hello");
            replaceEnvironment(Http1Config.create(), routing -> routing
                    .get("/hello", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                            .header(HeaderNames.LOCATION, targetUri.toString())
                            .send())
                    .get("/source", (req, res) -> res.send(HELLO)));
            WebClient client = WebClient.builder()
                    .baseUri(environment.baseUri())
                    .shareConnectionCache(false)
                    .keepAlive(false)
                    .proxy(Proxy.noProxy())
                    .tls(environment.clientTls())
                    .altSvc(ClientAltSvcConfig.create())
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolPreference(Http1Client.PROTOCOL_ID)
                    .addProtocolConfig(Http3ClientProtocolConfig.create())
                    .build();
            String sourceAuthority = URI.create(environment.baseUri()).getAuthority();
            HttpClientRequest redirectRequest = client.get("/hello")
                    .header(HeaderNames.HOST, sourceAuthority);

            try {
                try (HttpClientResponse redirected = redirectRequest.request()) {
                    assertThat(redirected.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(redirected.lastEndpointUri().toUri(), is(targetUri));
                }
                assertThat(redirectRequest.headers().first(HeaderNames.HOST).orElseThrow(), is(sourceAuthority));
                try (HttpClientResponse source = client.get("/source").request();
                     HttpClientResponse redirectedAgain = redirectRequest.request();
                     HttpClientResponse learned = client.get(targetUri.toString()).request()) {
                    assertThat(source.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(redirectedAgain.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(redirectedAgain.lastEndpointUri().toUri(), is(targetUri));
                    assertThat(learned.protocolId(), is(Http3Client.PROTOCOL_ID));
                }
                assertThat(redirectRequest.headers().first(HeaderNames.HOST).orElseThrow(), is(sourceAuthority));
            } finally {
                client.closeResource();
            }
        }
    }

    private void assertAltSvcIgnored(WebClient client) {
        try {
            try (HttpClientResponse firstResponse = client.get("/hello").request()) {
                assertThat(firstResponse.status(), is(Status.OK_200));
                assertThat(firstResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(firstResponse.headers().contains(HeaderNames.ALT_SVC), is(true));
                assertThat(firstResponse.entity().as(String.class), is(HELLO));
            }

            try (HttpClientResponse secondResponse = client.get("/hello").request()) {
                assertThat(secondResponse.status(), is(Status.OK_200));
                assertThat(secondResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(secondResponse.entity().as(String.class), is(HELLO));
            }
        } finally {
            client.closeResource();
        }
    }

    private WebClient newClient(Http3ClientProtocolConfig protocolConfig) {
        return newClient(protocolConfig, environment.clientTls());
    }

    private WebClient newClient(Http3ClientProtocolConfig protocolConfig, Tls tls) {
        return newClient(protocolConfig, tls, ClientAltSvcConfig.create());
    }

    private WebClient newClient(Http3ClientProtocolConfig protocolConfig, ClientAltSvcConfig altSvcConfig) {
        return newClient(protocolConfig, environment.clientTls(), altSvcConfig);
    }

    private WebClient newClient(Http3ClientProtocolConfig protocolConfig, Tls tls, ClientAltSvcConfig altSvcConfig) {
        return clientBuilder(protocolConfig, tls)
                .altSvc(altSvcConfig)
                .build();
    }

    private WebClientConfig.Builder clientBuilder(Http3ClientProtocolConfig protocolConfig, Tls tls) {
        return clientBuilder(protocolConfig, tls, false);
    }

    private WebClientConfig.Builder clientBuilder(Http3ClientProtocolConfig protocolConfig,
                                                   Tls tls,
                                                   boolean shareConnectionCache) {
        return WebClient.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(shareConnectionCache)
                .keepAlive(false)
                .proxy(Proxy.noProxy())
                .tls(tls)
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(protocolConfig);
    }

    private void replaceEnvironment(Http1Config http1Config, Consumer<HttpRouting.Builder> routing) {
        environment.close();
        try {
            environment = TestEnvironment.createSharedListener(http1Config, routing);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to replace the test environment", e);
        }
    }
}
