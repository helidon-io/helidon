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
package io.helidon.webclient.http2;

import java.io.OutputStream;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.AltSvcHeader;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ServerTest
class Http2AltSvcTest {
    private static final String ALTERNATIVE_SOCKET = "alternative";
    private static final String REDIRECT_TARGET_SOCKET = "redirect-target";
    private static final String STALE_REUSE_SOCKET = "stale-reuse";
    private static final String ZERO_STREAM_SOCKET = "zero-stream";
    private static final String ABSENT_HEADER = "-";
    private static final AtomicInteger ZERO_STREAM_REQUESTS = new AtomicInteger();

    private static int originPort;
    private static int alternativePort;
    private static int redirectTargetPort;
    private static int staleReusePort;
    private static int zeroStreamPort;

    Http2AltSvcTest(WebServer server) {
        originPort = server.port();
        alternativePort = server.port(ALTERNATIVE_SOCKET);
        redirectTargetPort = server.port(REDIRECT_TARGET_SOCKET);
        staleReusePort = server.port(STALE_REUSE_SOCKET);
        zeroStreamPort = server.port(ZERO_STREAM_SOCKET);
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        Tls serverTls = serverTls();
        HttpRouting.Builder originRouting = HttpRouting.builder()
                .get("/{operation}", Http2AltSvcTest::originResponse);
        HttpRouting.Builder alternativeRouting = HttpRouting.builder()
                .get("/{operation}", Http2AltSvcTest::alternativeResponse);
        HttpRouting.Builder zeroStreamRouting = HttpRouting.builder()
                .get("/{operation}", Http2AltSvcTest::zeroStreamResponse);
        HttpRouting.Builder redirectTargetRouting = HttpRouting.builder()
                .get("/{operation}", Http2AltSvcTest::redirectTargetResponse);
        HttpRouting.Builder staleReuseRouting = HttpRouting.builder()
                .get("/{operation}", Http2AltSvcTest::alternativeResponse);

        serverBuilder
                .host("localhost")
                .port(-1)
                .tls(serverTls)
                .protocolsDiscoverServices(false)
                .addConnectionSelector(Http1ConnectionSelector.builder()
                                               .config(Http1Config.create())
                                               .build())
                .putSocket(ALTERNATIVE_SOCKET, socket -> socket
                        .host("localhost")
                        .port(-1)
                        .tls(serverTls)
                        .protocolsDiscoverServices(false)
                        .addConnectionSelector(Http2ConnectionSelector.builder()
                                                       .http2Config(Http2Config.create())
                                                       .build()))
                .putSocket(ZERO_STREAM_SOCKET, socket -> socket
                        .host("localhost")
                        .port(-1)
                        .tls(serverTls)
                        .protocolsDiscoverServices(false)
                        .addConnectionSelector(Http2ConnectionSelector.builder()
                                                       .http2Config(Http2Config.builder()
                                                                            .maxConcurrentStreams(0)
                                                                            .build())
                                                       .build()))
                .putSocket(REDIRECT_TARGET_SOCKET, socket -> socket
                        .host("localhost")
                        .port(-1)
                        .tls(serverTls)
                        .protocolsDiscoverServices(false)
                        .addConnectionSelector(Http2ConnectionSelector.builder()
                                                       .http2Config(Http2Config.create())
                                                       .build()))
                .putSocket(STALE_REUSE_SOCKET, socket -> socket
                        .host("localhost")
                        .port(-1)
                        .tls(serverTls)
                        .protocolsDiscoverServices(false)
                        .addConnectionSelector(Http2ConnectionSelector.builder()
                                                       .http2Config(Http2Config.builder()
                                                                            .maxConcurrentStreams(1)
                                                                            .build())
                                                       .build()))
                .routing(originRouting)
                .routing(ALTERNATIVE_SOCKET, alternativeRouting)
                .routing(ZERO_STREAM_SOCKET, zeroStreamRouting)
                .routing(REDIRECT_TARGET_SOCKET, redirectTargetRouting)
                .routing(STALE_REUSE_SOCKET, staleReuseRouting);
    }

    @Test
    void learnsAndReusesAlternativeWithOriginIdentity() {
        WebClient client = client(clientTls(), true, false);

        try {
            Observation learned = request(client, "/learn");
            Observation firstAlternative = request(client, "/reuse");
            Observation reusedAlternative = request(client, "/reuse");

            assertOrigin(learned);
            assertAlternative(firstAlternative);
            assertAlternative(reusedAlternative);
            assertThat(reusedAlternative.socketId(), is(firstAlternative.socketId()));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void dnsCaseVariantsShareAdvertisementButKeepConnectionPoolsDistinct() {
        WebClient client = client(clientTls(), true, false);
        String upperOrigin = "https://LOCALHOST:" + originPort;
        String lowerOrigin = "https://localhost:" + originPort;

        try {
            Observation learned = requestUri(client, upperOrigin + "/learn");
            Observation lowerAlternative = requestUri(client, lowerOrigin + "/reuse");
            Observation upperAlternative = requestUri(client, upperOrigin + "/reuse");
            Observation lowerReused = requestUri(client, lowerOrigin + "/reuse");
            Observation upperReused = requestUri(client, upperOrigin + "/reuse");

            assertThat(learned.responseProtocol(), is(Http1Client.PROTOCOL_ID));
            assertThat(lowerAlternative.responseProtocol(), is(Http2Client.PROTOCOL_ID));
            assertThat(upperAlternative.responseProtocol(), is(Http2Client.PROTOCOL_ID));
            assertThat(lowerAlternative.socketId(), not(is(upperAlternative.socketId())));
            assertThat(lowerReused.socketId(), is(lowerAlternative.socketId()));
            assertThat(upperReused.socketId(), is(upperAlternative.socketId()));

            Observation cleared = requestUri(client, lowerOrigin + "/clear");
            assertThat(cleared.responseProtocol(), is(Http2Client.PROTOCOL_ID));
            assertThat(requestUri(client, upperOrigin + "/reuse").responseProtocol(), is(Http1Client.PROTOCOL_ID));
            assertThat(requestUri(client, lowerOrigin + "/reuse").responseProtocol(), is(Http1Client.PROTOCOL_ID));

            assertThat(requestUri(client, lowerOrigin + "/learn").responseProtocol(), is(Http1Client.PROTOCOL_ID));
            Observation reverseAlternative = requestUri(client, upperOrigin + "/reuse");
            assertThat(reverseAlternative.responseProtocol(), is(Http2Client.PROTOCOL_ID));
            assertThat(reverseAlternative.socketId(), not(is(upperAlternative.socketId())));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void configuredNoProxyPolicyDoesNotUseAlternativeWithAuthorityOverride() {
        Proxy proxy = Proxy.builder()
                .host("unused-proxy.invalid")
                .port(8181)
                .addNoProxy("localhost")
                .addNoProxy("127.0.0.1")
                .build();
        WebClient client = WebClient.builder()
                .baseUri("https://localhost:" + originPort)
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .altSvc(ClientAltSvcConfig.create())
                .addProtocolConfig(Http2ClientProtocolConfig.create())
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .proxy(proxy)
                .tls(clientTls())
                .build();

        try {
            assertOrigin(request(client, "/learn"));
            assertOrigin(request(client, "/reuse"));

            String authority = "Alt-Origin.EXAMPLE:" + originPort;
            Observation learnedOverride = requestWithHost(client, "/learn", authority);
            Observation reusedOverride = requestWithHost(client, "/reuse", authority);

            assertThat(learnedOverride.responseProtocol(), is(Http1Client.PROTOCOL_ID));
            assertThat(reusedOverride.responseProtocol(), is(Http1Client.PROTOCOL_ID));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void protocolSpecificClientLearnsWithPrivateCache() {
        Http2Client client = Http2Client.create(builder -> builder
                .baseUri("https://localhost:" + originPort)
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .altSvc(ClientAltSvcConfig.create())
                .proxy(Proxy.noProxy())
                .tls(clientTls()));

        try {
            assertOrigin(request(client, "/learn"));
            assertAlternative(request(client, "/reuse"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void zeroStreamAlternativeFallsBackBeforeSendingRequest() {
        ZERO_STREAM_REQUESTS.set(0);
        WebClient client = client(clientTls(), true, false);

        try {
            assertOrigin(request(client, "/learn-zero-stream"));
            assertOrigin(request(client, "/zero-stream-fallback"));
            assertOrigin(request(client, "/zero-stream-fallback"));
            assertThat(ZERO_STREAM_REQUESTS.get(), is(0));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void staleReusableAlternativeReleasesReservedStreamAndRemainsTrackedUntilClose() {
        Tls tls = clientTls();
        Http2Client client = Http2Client.create(builder -> builder
                .baseUri("https://localhost:" + originPort)
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .proxy(Proxy.noProxy())
                .tls(tls));
        Http2ClientImpl http2Client = (Http2ClientImpl) client;
        ClientConnectionTarget originTarget = ClientConnectionTarget.create(
                ConnectionKey.create("https",
                                     "localhost",
                                     originPort,
                                     tls,
                                     (_, _) -> InetAddress.getLoopbackAddress(),
                                     DnsAddressLookup.IPV4,
                                     Proxy.noProxy()),
                "https");
        WritableHeaders<?> responseHeaders = WritableHeaders.create();
        responseHeaders.set(HeaderNames.ALT_SVC, advertisement(staleReusePort, 3600));

        try (Http2AltSvcCache alternatives = Http2AltSvcCache.create(_ -> { })) {
            Instant observedAt = Instant.now();
            AltSvcHeader advertisement = AltSvcHeader.create(ClientResponseHeaders.create(responseHeaders),
                                                             observedAt)
                    .orElseThrow();
            alternatives.record(originTarget, advertisement, true, false, observedAt);
            Http2AltSvcCache.Selection selection = alternatives.select(originTarget, false, _ -> false);

            AtomicInteger phase = new AtomicInteger();
            AtomicInteger currentChecks = new AtomicInteger();
            AtomicBoolean reservationAttempted = new AtomicBoolean();
            Http2ClientConnectionHandler handler = new Http2ClientConnectionHandler(1, _ -> switch (phase.get()) {
                case 0 -> true;
                case 1 -> currentChecks.getAndIncrement() == 0;
                case 2 -> currentChecks.getAndIncrement() == 0 || reservationAttempted.get();
                default -> false;
            });
            Http2ClientRequestImpl request = mock(Http2ClientRequestImpl.class);
            when(request.readTimeout()).thenAnswer(_ -> {
                reservationAttempted.set(true);
                return Duration.ofSeconds(1);
            });
            Http1FallbackHandler fallbackHandler = new Http1FallbackHandler(new CompletableFuture<>(), _ -> null, true);
            try {
                Http2ConnectionAttemptResult established = handler.newAlternativeStream(http2Client,
                                                                                         selection,
                                                                                         request,
                                                                                         fallbackHandler);
                established.stream().close();

                phase.set(1);
                currentChecks.set(0);
                reservationAttempted.set(false);
                AlternativeConnectionException stale = assertThrows(
                        AlternativeConnectionException.class,
                        () -> handler.newAlternativeStream(http2Client, selection, request, fallbackHandler));

                assertThat(stale.reason(), is(AlternativeConnectionException.Reason.STALE));
                assertThat(currentChecks.get(), is(2));
                assertThat(reservationAttempted.get(), is(true));

                phase.set(2);
                currentChecks.set(0);
                reservationAttempted.set(false);
                Http2ConnectionAttemptResult recovered = handler.newAlternativeStream(http2Client,
                                                                                       selection,
                                                                                       request,
                                                                                       fallbackHandler);

                assertThat(currentChecks.get(), is(2));
                assertThat(reservationAttempted.get(), is(true));
                handler.retire(selection);
                handler.close();

                Http2Headers requestHeaders = Http2Headers.create(WritableHeaders.create())
                        .method(Method.GET)
                        .scheme("https")
                        .path("/")
                        .authority("localhost");
                assertThrows(IllegalStateException.class,
                             () -> recovered.stream().writeHeaders(requestHeaders, true));
            } finally {
                handler.close();
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void privateHttp1FallbackUsesConfiguredKeepAlive() {
        ZERO_STREAM_REQUESTS.set(0);
        WebClient client = WebClient.builder()
                .baseUri("https://localhost:" + originPort)
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .altSvc(ClientAltSvcConfig.create())
                .addProtocolConfig(Http2ClientProtocolConfig.create())
                .addProtocolConfig(Http1ClientProtocolConfig.builder()
                                           .defaultKeepAlive(false)
                                           .build())
                .proxy(Proxy.noProxy())
                .tls(clientTls())
                .build();

        try {
            assertOrigin(request(client, "/learn-zero-stream"));

            Observation firstFallback = request(client, "/configured-h1-keep-alive");
            Observation secondFallback = request(client, "/configured-h1-keep-alive");

            assertOrigin(firstFallback);
            assertOrigin(secondFallback);
            assertThat(secondFallback.socketId(), not(is(firstFallback.socketId())));
            assertThat(ZERO_STREAM_REQUESTS.get(), is(0));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void http1OutputStreamRedirectPublishesFinalClearLast() {
        WebClient client = client(clientTls(), true, false);

        try {
            assertOrigin(outputStream(client, "/h1-output-redirect"));
            assertOrigin(request(client, "/reuse"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void privateHttp1FallbackOutputStreamRedirectPublishesFinalClearLast() {
        Http2Client client = Http2Client.create(builder -> builder
                .baseUri("https://localhost:" + originPort)
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .altSvc(ClientAltSvcConfig.create())
                .proxy(Proxy.noProxy())
                .tls(clientTls()));

        try {
            assertOrigin(outputStream(client, "/h1-output-redirect"));
            assertOrigin(request(client, "/reuse"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void http2OutputStreamRedirectPublishesIntermediateAdvertisement() {
        Http2Client client = Http2Client.create(builder -> builder
                .baseUri("https://localhost:" + alternativePort)
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .altSvc(ClientAltSvcConfig.create())
                .proxy(Proxy.noProxy())
                .tls(clientTls()));

        try {
            Observation redirected = outputStream(client, "/h2-output-redirect");
            assertThat(redirected.responseProtocol(), is(Http2Client.PROTOCOL_ID));
            assertThat(redirected.serverProtocol(), is(Http2Client.PROTOCOL_ID));
            assertThat(redirected.authority(), is("localhost:" + alternativePort));
            assertThat(redirected.altUsed(), is("localhost:" + redirectTargetPort));

            Observation alternative = request(client, "/redirect-reuse");
            assertThat(alternative.responseProtocol(), is(Http2Client.PROTOCOL_ID));
            assertThat(alternative.serverProtocol(), is(Http2Client.PROTOCOL_ID));
            assertThat(alternative.authority(), is("localhost:" + alternativePort));
            assertThat(alternative.altUsed(), is("localhost:" + redirectTargetPort));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void disabledAltSvcStaysOnOrigin() {
        WebClient client = client(clientTls(), false, false);

        try {
            assertOrigin(request(client, "/learn"));
            assertOrigin(request(client, "/reuse"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void absentAltSvcStaysOnOrigin() {
        WebClient client = clientWithoutAltSvc(clientTls(), false);

        try {
            assertOrigin(request(client, "/learn"));
            assertOrigin(request(client, "/reuse"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void absentAltSvcDoesNotDispatchProtocolResponse() {
        CaptureResult result = captureProtocolResponse(Optional.empty());

        assertThat(result.notificationCount(), is(0));
        assertThat(result.h2SelectionEnabled(), is(false));
        assertThat(result.h2AlternativeAvailable(), is(false));
    }

    @Test
    void disabledAltSvcDoesNotDispatchProtocolResponse() {
        ClientAltSvcConfig altSvc = ClientAltSvcConfig.builder()
                .enabled(false)
                .build();
        CaptureResult result = captureProtocolResponse(Optional.of(altSvc));

        assertThat(result.notificationCount(), is(0));
        assertThat(result.h2SelectionEnabled(), is(false));
        assertThat(result.h2AlternativeAvailable(), is(false));
    }

    @Test
    void h3OnlyAltSvcDispatchesWithoutTeachingHttp2() {
        ClientAltSvcConfig altSvc = ClientAltSvcConfig.builder()
                .addProtocol("h3")
                .build();
        CaptureResult result = captureProtocolResponse(Optional.of(altSvc));

        assertThat(result.notificationCount(), is(1));
        assertThat(result.h2SelectionEnabled(), is(false));
        assertThat(result.h2AlternativeAvailable(), is(false));
    }

    @Test
    void addressBoundProxyBypassDoesNotUseAlternative() {
        Proxy proxy = Proxy.builder()
                .host("unused-proxy.invalid")
                .port(8181)
                .addNoProxy("127.0.0.1")
                .build();
        WebClient client = WebClient.builder()
                .baseUri("https://localhost:" + originPort)
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .altSvc(ClientAltSvcConfig.create())
                .addProtocolConfig(Http2ClientProtocolConfig.builder().build())
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .proxy(proxy)
                .tls(clientTls())
                .build();

        try {
            assertOrigin(request(client, "/learn"));
            assertOrigin(request(client, "/reuse"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void clearRetiresAlternative() {
        WebClient client = client(clientTls(), true, false);

        try {
            assertOrigin(request(client, "/learn-clear"));
            assertAlternative(request(client, "/clear"));
            assertOrigin(request(client, "/after-clear"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void mixedClearRetiresAlternative() {
        WebClient client = client(clientTls(), true, false);

        try {
            assertOrigin(request(client, "/learn-clear"));
            assertAlternative(request(client, "/mixed-clear"));
            assertOrigin(request(client, "/after-mixed-clear"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void misdirectedResponseInvalidatesWithoutRelearning() {
        WebClient client = client(clientTls(), true, false);

        try {
            assertOrigin(request(client, "/learn-misdirected"));
            Observation misdirected = request(client, "/misdirected");

            assertAlternativeIdentity(misdirected);
            assertThat(misdirected.status(), is(Status.MISDIRECTED_REQUEST_421));
            assertOrigin(request(client, "/after-misdirected"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void zeroMaximumAgeReusesEstablishedAlternative() {
        WebClient client = client(clientTls(), true, false);

        try {
            assertOrigin(request(client, "/learn-zero-age"));
            Observation zeroAge = request(client, "/zero-age");
            Observation reused = request(client, "/after-zero-age");

            assertAlternative(zeroAge);
            assertAlternative(reused);
            assertThat(reused.socketId(), is(zeroAge.socketId()));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void tlsReloadRequiresFreshOriginBootstrapAndAlternativeConnection() {
        Tls tls = clientTls();
        WebClient client = client(tls, true, false);

        try {
            assertOrigin(request(client, "/tls-reload"));
            Observation firstAlternative = request(client, "/reuse");

            tls.reload(clientTls());

            assertOrigin(request(client, "/tls-reload"));
            Observation reloadedAlternative = request(client, "/reuse");

            assertAlternative(firstAlternative);
            assertAlternative(reloadedAlternative);
            assertThat(reloadedAlternative.socketId(), not(is(firstAlternative.socketId())));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void privateCachesDoNotShareLearnedAlternative() {
        Tls tls = clientTls();
        WebClient learningClient = client(tls, true, false);
        WebClient isolatedClient = client(tls, true, false);

        try {
            assertOrigin(request(learningClient, "/learn-private"));
            assertOrigin(request(isolatedClient, "/private-isolation"));
            assertAlternative(request(learningClient, "/private-isolation"));
        } finally {
            learningClient.closeResource();
            isolatedClient.closeResource();
        }
    }

    @Test
    void sharedCacheSurvivesClosingLearningClient() {
        Tls tls = clientTls();
        WebClient learningClient = client(tls, true, true);
        WebClient reusingClient = client(tls, true, true);

        try {
            assertOrigin(request(learningClient, "/learn-shared"));
            learningClient.closeResource();

            Observation firstAlternative = request(reusingClient, "/shared-reuse");
            Observation reusedAlternative = request(reusingClient, "/shared-reuse");

            assertAlternative(firstAlternative);
            assertAlternative(reusedAlternative);
            assertThat(reusedAlternative.socketId(), is(firstAlternative.socketId()));
        } finally {
            learningClient.closeResource();
            reusingClient.closeResource();
        }
    }

    @Test
    void disabledAndRestrictedClientsDoNotConsumeSharedAlternative() {
        Tls tls = clientTls();
        WebClient learningClient = client(tls, true, true);
        WebClient disabledClient = client(tls, false, true);
        WebClient restrictedClient = client(tls,
                                            ClientAltSvcConfig.builder().addProtocol("h3").build(),
                                            true);

        try {
            assertOrigin(request(learningClient, "/learn-shared-policy"));
            assertOrigin(request(disabledClient, "/shared-policy"));
            assertOrigin(request(restrictedClient, "/shared-policy"));
            assertAlternative(request(learningClient, "/shared-policy"));
        } finally {
            learningClient.closeResource();
            disabledClient.closeResource();
            restrictedClient.closeResource();
        }
    }

    private static CaptureResult captureProtocolResponse(Optional<ClientAltSvcConfig> altSvc) {
        Http2ClientConfig.Builder configBuilder = Http2ClientConfig.builder()
                .baseUri("https://localhost:" + alternativePort)
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .proxy(Proxy.noProxy())
                .tls(clientTls());
        altSvc.ifPresent(configBuilder::altSvc);
        Http2ClientConfig clientConfig = configBuilder.buildPrototype();
        WebClient webClient = WebClient.create(builder -> builder.from(clientConfig));
        List<WebClientProtocolResponse> notifications = new ArrayList<>();
        Http2ClientImpl client = new Http2ClientImpl(webClient, clientConfig) {
            @Override
            public void responseReceived(WebClientProtocolResponse response) {
                notifications.add(response);
                super.responseReceived(response);
            }
        };

        try {
            request(client, "/capture-policy");
            boolean alternativeAvailable = notifications.stream()
                    .findFirst()
                    .map(response -> client.connectionCache()
                            .currentAlternative(response.target().logicalTarget(), false) != null)
                    .orElse(false);
            return new CaptureResult(notifications.size(), client.altSvcEnabled(), alternativeAvailable);
        } finally {
            client.closeResource();
            webClient.closeResource();
        }
    }

    private static WebClient client(Tls tls, boolean altSvcEnabled, boolean shareConnectionCache) {
        return client(tls,
                      ClientAltSvcConfig.builder().enabled(altSvcEnabled).build(),
                      shareConnectionCache);
    }

    private static WebClient client(Tls tls,
                                    ClientAltSvcConfig altSvc,
                                    boolean shareConnectionCache) {
        return WebClient.builder()
                .baseUri("https://localhost:" + originPort)
                .shareConnectionCache(shareConnectionCache)
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .altSvc(altSvc)
                .addProtocolConfig(Http2ClientProtocolConfig.create())
                .proxy(Proxy.noProxy())
                .tls(tls)
                .build();
    }

    private static WebClient clientWithoutAltSvc(Tls tls, boolean shareConnectionCache) {
        return WebClient.builder()
                .baseUri("https://localhost:" + originPort)
                .shareConnectionCache(shareConnectionCache)
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .addProtocolConfig(Http2ClientProtocolConfig.create())
                .proxy(Proxy.noProxy())
                .tls(tls)
                .build();
    }

    private static Tls clientTls() {
        return Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
    }

    private static Tls serverTls() {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .keystore(Resource.create("server.p12"))
                        .passphrase("password"))
                .build();
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setSNIMatchers(List.of(SNIHostName.createSNIMatcher("localhost")));

        return Tls.builder()
                .privateKey(keys)
                .privateKeyCertChain(keys)
                .sslParameters(sslParameters)
                .build();
    }

    private static void originResponse(ServerRequest request, ServerResponse response) {
        String operation = request.path().pathParameters().get("operation");
        switch (operation) {
        case "learn", "learn-clear", "learn-misdirected", "learn-zero-age", "learn-private", "learn-shared",
                "learn-shared-policy", "tls-reload" -> response.header(HeaderNames.ALT_SVC,
                                                                         advertisement(alternativePort, 3600));
        case "learn-zero-stream" -> response.header(HeaderNames.ALT_SVC, advertisement(zeroStreamPort, 3600));
        case "h1-output-redirect" -> response
                .status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/h1-output-final")
                .header(HeaderNames.ALT_SVC, advertisement(alternativePort, 3600));
        case "h1-output-final" -> response.header(HeaderNames.ALT_SVC, "clear");
        case "configured-h1-keep-alive" -> response
                .header(HeaderNames.ALT_SVC, advertisement(zeroStreamPort, 3600));
        default -> {
        }
        }
        sendObservation(request, response, Http1Client.PROTOCOL_ID);
    }

    private static void alternativeResponse(ServerRequest request, ServerResponse response) {
        String operation = request.path().pathParameters().get("operation");
        switch (operation) {
        case "capture-policy" -> response.header(HeaderNames.ALT_SVC,
                                                   "h2=\":%1$d\"; ma=3600, h3=\":%1$d\"; ma=3600"
                                                           .formatted(redirectTargetPort));
        case "clear" -> response.header(HeaderNames.ALT_SVC, "clear");
        case "mixed-clear" -> response.header(HeaderNames.ALT_SVC,
                                                advertisement(alternativePort, 3600) + ", clear");
        case "zero-age" -> response.header(HeaderNames.ALT_SVC, advertisement(alternativePort, 0));
        case "misdirected" -> response
                .status(Status.MISDIRECTED_REQUEST_421)
                .header(HeaderNames.ALT_SVC, advertisement(alternativePort, 3600));
        case "h2-output-redirect" -> response
                .status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, "/h2-output-final")
                .header(HeaderNames.ALT_SVC, advertisement(redirectTargetPort, 3600));
        default -> {
        }
        }
        sendObservation(request, response, Http2Client.PROTOCOL_ID);
    }

    private static void zeroStreamResponse(ServerRequest request, ServerResponse response) {
        ZERO_STREAM_REQUESTS.incrementAndGet();
        sendObservation(request, response, Http2Client.PROTOCOL_ID);
    }

    private static void redirectTargetResponse(ServerRequest request, ServerResponse response) {
        sendObservation(request, response, Http2Client.PROTOCOL_ID);
    }

    private static void sendObservation(ServerRequest request, ServerResponse response, String serverProtocol) {
        String altUsed = request.headers().first(HeaderNames.ALT_USED).orElse(ABSENT_HEADER);
        String sniHost = request.sniRequestedHost().orElse(ABSENT_HEADER);
        response.send(serverProtocol
                              + "|" + request.requestedUri().authority()
                              + "|" + altUsed
                              + "|" + sniHost
                              + "|" + request.socketId());
    }

    private static String advertisement(int port, long maxAgeSeconds) {
        return "h2=\":%d\"; ma=%d".formatted(port, maxAgeSeconds);
    }

    private static Observation request(WebClient client, String path) {
        try (HttpClientResponse response = client.get(path).request()) {
            return observation(response);
        }
    }

    private static Observation request(Http2Client client, String path) {
        try (HttpClientResponse response = client.get(path).request()) {
            return observation(response);
        }
    }

    private static Observation requestUri(WebClient client, String uri) {
        try (HttpClientResponse response = client.get().uri(uri).request()) {
            return observation(response);
        }
    }

    private static Observation requestWithHost(WebClient client, String path, String authority) {
        try (HttpClientResponse response = client.get(path)
                .header(HeaderNames.HOST, authority)
                .request()) {
            return observation(response);
        }
    }

    private static Observation outputStream(WebClient client, String path) {
        try (HttpClientResponse response = client.get(path).outputStream(OutputStream::close)) {
            return observation(response);
        }
    }

    private static Observation outputStream(Http2Client client, String path) {
        try (HttpClientResponse response = client.get(path).outputStream(OutputStream::close)) {
            return observation(response);
        }
    }

    private static Observation observation(HttpClientResponse response) {
        String[] fields = response.as(String.class).split("\\|", -1);
        assertThat("server observation field count", fields.length, is(5));
        return new Observation(response.status(),
                               response.protocolId(),
                               fields[0],
                               fields[1],
                               fields[2],
                               fields[3],
                               fields[4]);
    }

    private static void assertOrigin(Observation observation) {
        assertThat(observation.status(), is(Status.OK_200));
        assertThat(observation.responseProtocol(), is(Http1Client.PROTOCOL_ID));
        assertThat(observation.serverProtocol(), is(Http1Client.PROTOCOL_ID));
        assertThat(observation.authority(), is("localhost:" + originPort));
        assertThat(observation.altUsed(), is(ABSENT_HEADER));
    }

    private static void assertAlternative(Observation observation) {
        assertThat(observation.status(), is(Status.OK_200));
        assertAlternativeIdentity(observation);
    }

    private static void assertAlternativeIdentity(Observation observation) {
        assertThat(observation.responseProtocol(), is(Http2Client.PROTOCOL_ID));
        assertThat(observation.serverProtocol(), is(Http2Client.PROTOCOL_ID));
        assertThat(observation.authority(), is("localhost:" + originPort));
        assertThat(observation.altUsed(), is("localhost:" + alternativePort));
    }

    private record CaptureResult(int notificationCount,
                                 boolean h2SelectionEnabled,
                                 boolean h2AlternativeAvailable) {
    }

    private record Observation(Status status,
                               String responseProtocol,
                               String serverProtocol,
                               String authority,
                               String altUsed,
                               String sniHost,
                               String socketId) {
    }
}
