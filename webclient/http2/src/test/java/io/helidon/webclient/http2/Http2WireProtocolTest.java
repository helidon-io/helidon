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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.Proxy.ProxyType;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.UpgradeResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.http2.Http2Upgrader;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

@ServerTest
class Http2WireProtocolTest {
    private static final String HTTP1_SOCKET = "http1";

    private static int http2Port;
    private static int http1Port;

    Http2WireProtocolTest(WebServer server) {
        http2Port = server.port();
        http1Port = server.port(HTTP1_SOCKET);
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        Http2Config http2Config = Http2Config.create();
        HttpRouting.Builder http2Routing = HttpRouting.builder()
                .route(Http2Route.route(GET, "/h2", (req, res) -> res.send("h2")))
                .route(Http2Route.route(GET, "/forward-proxy", (req, res) -> {
                    var requestedUri = req.requestedUri();
                    res.send(requestedUri.scheme()
                                     + "|" + requestedUri.authority()
                                     + "|" + requestedUri.path().path());
                }))
                .route(Http2Route.route(POST, "/expect-redirect", (req, res) -> {
                    if (req.headers().containsToken(HeaderValues.EXPECT_100)) {
                        res.status(Status.SEE_OTHER_303)
                                .header(HeaderNames.LOCATION, "http://localhost:" + http1Port + "/redirected")
                                .send();
                    } else {
                        res.status(Status.BAD_REQUEST_400).send();
                    }
                }))
                .route(Http2Route.route(POST, "/redirect", (req, res) -> {
                    req.content().as(String.class);
                    res.status(Status.SEE_OTHER_303)
                            .header(HeaderNames.LOCATION, "http://localhost:" + http1Port + "/redirected")
                            .send();
                }))
                .route(Http2Route.route(POST, "/proxy-route-first", (req, res) -> {
                    if (req.headers().containsToken(HeaderValues.EXPECT_100)) {
                        res.status(Status.TEMPORARY_REDIRECT_307)
                                .header(HeaderNames.LOCATION, "/proxy-route-second")
                                .send();
                    } else {
                        res.status(Status.BAD_REQUEST_400).send();
                    }
                }))
                .route(Http2Route.route(POST, "/proxy-route-second", (req, res) -> {
                    if (req.headers().containsToken(HeaderValues.EXPECT_100)) {
                        res.status(Status.PERMANENT_REDIRECT_308)
                                .header(HeaderNames.LOCATION, "/proxy-route-echo")
                                .send();
                    } else {
                        res.status(Status.BAD_REQUEST_400).send();
                    }
                }))
                .route(Http2Route.route(POST, "/proxy-route-echo", (req, res) -> {
                    if (req.headers().containsToken(HeaderValues.EXPECT_100)) {
                        res.send(req.content().as(String.class));
                    } else {
                        res.status(Status.BAD_REQUEST_400).send();
                    }
                }));
        HttpRouting.Builder http1Routing = HttpRouting.builder()
                .route(Http1Route.route(GET, "/fallback", (req, res) -> res.send("http1")))
                .route(Http1Route.route(GET, "/redirected", (req, res) -> res.send("redirected")));

        serverBuilder
                .host("localhost")
                .port(-1)
                .protocolsDiscoverServices(false)
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(http2Config)
                                               .build())
                .addConnectionSelector(Http1ConnectionSelector.builder()
                                               .config(Http1Config.create())
                                               .addUpgrader(Http2Upgrader.create(http2Config))
                                               .build())
                .putSocket(HTTP1_SOCKET, socket -> socket
                        .host("localhost")
                        .port(-1)
                        .protocolsDiscoverServices(false)
                        .addConnectionSelector(Http1ConnectionSelector.builder()
                                                       .config(Http1Config.create())
                                                       .build()))
                .routing(http2Routing)
                .routing(HTTP1_SOCKET, http1Routing);
    }

    @Test
    void priorKnowledgeResponseAndServiceReportH2() throws Exception {
        ProtocolObservations observations = new ProtocolObservations();
        Http2Client client = Http2Client.builder()
                .baseUri("http://localhost:" + http2Port)
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(it -> it.priorKnowledge(true))
                .addService(observations)
                .build();
        try (Http2ClientResponse response = client.get("/h2").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("h2"));
            assertThat(response.protocolId(), is(Http2Client.PROTOCOL_ID));
            assertThat(observations.protocolsAfterProceed(), contains(Http2Client.PROTOCOL_ID));
            assertThat(observations.protocolsWhenSent(), contains(Http2Client.PROTOCOL_ID));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void priorKnowledgeHttp2UsesForwardProxyPeer() {
        String proxyHost = "h2-forward-proxy.invalid";
        Proxy proxy = Proxy.builder()
                .type(ProxyType.HTTP)
                .host(proxyHost)
                .port(http2Port)
                .build();
        Http2Client client = Http2Client.builder()
                .baseUri("http://unresolvable.invalid:8181")
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .dnsResolver((host, _) -> {
                    if (!proxyHost.equals(host)) {
                        throw new AssertionError("Logical origin must not be resolved: " + host);
                    }
                    return InetAddress.ofLiteral("127.0.0.1");
                })
                .proxy(proxy)
                .protocolConfig(it -> it.priorKnowledge(true))
                .build();

        try (Http2ClientResponse response = client.get("/forward-proxy").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.protocolId(), is(Http2Client.PROTOCOL_ID));
            assertThat(response.as(String.class),
                       is("http|unresolvable.invalid:8181|/forward-proxy"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void failedH2cUpgradeResponseAndServiceReportHttp11() throws Exception {
        ProtocolObservations observations = new ProtocolObservations();
        Http2Client client = Http2Client.builder()
                .baseUri("http://localhost:" + http1Port)
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .addService(observations)
                .build();
        try (Http2ClientResponse response = client.get("/fallback").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("http1"));
            assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
            assertThat(observations.protocolsAfterProceed(), contains(Http1Client.PROTOCOL_ID));
            assertThat(observations.protocolsWhenSent(), contains(Http1Client.PROTOCOL_ID));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void directHttp1UpgradeClearsSelectedProxyRoute() {
        Proxy proxy = Proxy.noProxy();
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + http1Port)
                .shareConnectionCache(false)
                .proxy(proxy)
                .build();
        Http1ClientRequest request = client.get("/fallback");
        FullClientRequest<?> fullRequest = (FullClientRequest<?>) request;
        fullRequest.selectedProxyRoute(proxy.effectiveRoute("http", "localhost", http1Port, false));

        UpgradeResponse response = request.upgrade("h2c");
        try {
            assertThat(response.isUpgraded(), is(false));
            assertThat(response.response().status(), is(Status.OK_200));
            assertThat(fullRequest.selectedProxyRoute().isEmpty(), is(true));
        } finally {
            response.response().close();
            client.closeResource();
        }
    }

    @Test
    void outputStreamRedirectReportsFinalProtocol() throws Exception {
        ProtocolObservations observations = new ProtocolObservations();
        byte[] entity = "request entity".getBytes(StandardCharsets.UTF_8);
        Http2Client client = Http2Client.builder()
                .baseUri("http://localhost:" + http2Port)
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .followRedirects(true)
                .addService(observations)
                .build();
        try (Http2ClientResponse response = client.post("/redirect")
                .header(HeaderNames.CONTENT_LENGTH, String.valueOf(entity.length))
                .outputStream(out -> {
                    out.write(entity);
                    out.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("redirected"));
            assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
            assertThat(observations.protocolsAfterProceed(),
                       contains(Http1Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID));
            assertThat(observations.protocolsWhenSent(),
                       contains(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void expectContinueRedirectReportsFinalProtocol() throws Exception {
        ProtocolObservations observations = new ProtocolObservations();
        byte[] entity = "request entity".getBytes(StandardCharsets.UTF_8);
        Http2Client client = Http2Client.builder()
                .baseUri("http://localhost:" + http2Port)
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .followRedirects(true)
                .addService(observations)
                .build();
        try (Http2ClientResponse response = client.post("/expect-redirect")
                .sendExpectContinue(true)
                .outputStream(out -> {
                    out.write(entity);
                    out.close();
                })) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("redirected"));
            assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
            assertThat(observations.protocolsAfterProceed(),
                       contains(Http1Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID));
            assertThat(observations.protocolsWhenSent(),
                       contains(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void multiHopExpectContinueOutputStreamRedirectRetainsSelectedSystemProxyRoute() {
        AtomicInteger selectorInvocations = new AtomicInteger();
        ProxySelector countingSelector = new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                selectorInvocations.incrementAndGet();
                return List.of(java.net.Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException failure) {
            }
        };

        Proxy systemProxy;
        ProxySelector originalSelector = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(countingSelector);
            systemProxy = Proxy.create();
        } finally {
            ProxySelector.setDefault(originalSelector);
        }

        Http2Client client = Http2Client.builder()
                .baseUri("http://localhost:" + http2Port)
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .followRedirects(true)
                .protocolConfig(it -> it.priorKnowledge(true))
                .proxy(systemProxy)
                .build();
        String entity = "selected proxy route";

        try {
            try (Http2ClientResponse response = client.post("/proxy-route-first")
                    .maxRedirects(2)
                    .sendExpectContinue(true)
                    .outputStream(outputStream -> {
                        outputStream.write(entity.getBytes(StandardCharsets.UTF_8));
                        outputStream.close();
                    })) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.as(String.class), is(entity));
                assertThat(response.protocolId(), is(Http2Client.PROTOCOL_ID));
            }
            assertThat(selectorInvocations.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    private static final class ProtocolObservations implements WebClientService {
        private final List<String> protocolsAfterProceed = new ArrayList<>();
        private final List<CompletableFuture<String>> protocolsWhenSent = new ArrayList<>();

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            protocolsWhenSent.add(request.whenSent()
                                          .thenApply(WebClientServiceRequest::protocolId)
                                          .toCompletableFuture());
            WebClientServiceResponse response = chain.proceed(request);
            protocolsAfterProceed.add(request.protocolId());
            return response;
        }

        List<String> protocolsAfterProceed() {
            return List.copyOf(protocolsAfterProceed);
        }

        List<String> protocolsWhenSent() throws InterruptedException, ExecutionException, TimeoutException {
            List<String> result = new ArrayList<>(protocolsWhenSent.size());
            for (CompletableFuture<String> protocol : protocolsWhenSent) {
                result.add(protocol.get(10, TimeUnit.SECONDS));
            }
            return result;
        }
    }
}
