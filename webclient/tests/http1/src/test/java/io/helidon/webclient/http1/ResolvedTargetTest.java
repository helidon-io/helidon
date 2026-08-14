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

package io.helidon.webclient.http1;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

@ServerTest
class ResolvedTargetTest {
    private final int plainPort;
    private final int tlsPort;
    private final int finalTlsPort;

    ResolvedTargetTest(WebServer server) {
        plainPort = server.port();
        tlsPort = server.port("https");
        finalTlsPort = server.port("https-final");
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        Keys keys = Keys.builder()
                .keystore(keystore -> keystore.keystore(Resource.create("server.p12"))
                        .passphrase("password"))
                .build();
        Tls tls = Tls.builder()
                .privateKey(keys.privateKey().get())
                .privateKeyCertChain(keys.certChain())
                .build();
        HttpRouting.Builder routing = HttpRouting.builder()
                .get("/connection-target", (req, res) -> {
                    res.send(req.requestedUri().host() + "|" + req.socketId());
                })
                .get("/generic-connection-target", (req, res) -> {
                    res.send("bootstrap|" + req.socketId());
                })
                .post("/proxy-route-first", (req, res) -> {
                    res.status(Status.TEMPORARY_REDIRECT_307)
                            .header(HeaderNames.LOCATION, "/proxy-route-second")
                            .send();
                })
                .post("/proxy-route-second", (req, res) -> {
                    res.status(Status.PERMANENT_REDIRECT_308)
                            .header(HeaderNames.LOCATION, "/proxy-route-echo")
                            .send();
                })
                .post("/proxy-route-echo", (req, res) -> {
                    res.send(req.content().as(String.class));
                });

        serverBuilder.port(-1)
                .host("localhost")
                .putSocket("https", socket -> socket.port(-1)
                        .host("localhost")
                        .tls(tls))
                .putSocket("https-final", socket -> socket.port(-1)
                        .host("localhost")
                        .tls(tls))
                .routing(routing)
                .routing("https", routing.copy())
                .routing("https-final", HttpRouting.builder()
                        .get("/generic-connection-target", (req, res) -> {
                            res.send("final|" + req.socketId());
                        }));
    }

    @Test
    void genericClientRetargetsAfterAlpnHttp1Discovery() {
        WebClient client = WebClient.builder()
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .baseUri("https://localhost:" + tlsPort + "/generic-connection-target")
                .tls(Tls.builder().trustAll(true).build())
                .addService((chain, request) -> {
                    request.uri().port(finalTlsPort);
                    return chain.proceed(request);
                })
                .build();

        try {
            String first = client.get().request().as(String.class);
            String reused = client.get().request().as(String.class);

            assertThat(first, startsWith("final|"));
            assertThat(reused, is(first));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void finalServiceAuthoritySeparatesAndReusesConnections() {
        Http1Client client = Http1Client.builder()
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .baseUri("http://bootstrap.example:" + plainPort + "/connection-target")
                .addService((chain, request) -> {
                    request.uri().host("transport.example");
                    request.headers().set(HeaderNames.HOST,
                                          request.properties().get("logical-host") + ":" + plainPort);
                    return chain.proceed(request);
                })
                .build();

        try {
            String first = client.get().property("logical-host", "first.example").request().as(String.class);
            String second = client.get().property("logical-host", "second.example").request().as(String.class);
            String reused = client.get().property("logical-host", "first.example").request().as(String.class);

            assertThat(first.substring(0, first.indexOf('|')), is("first.example"));
            assertThat(second.substring(0, second.indexOf('|')), is("second.example"));
            assertThat(reused.substring(reused.indexOf('|')), is(first.substring(first.indexOf('|'))));
            assertThat(second.substring(second.indexOf('|')), not(is(first.substring(first.indexOf('|')))));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void tlsReloadRetiresPreviousTargetConnection() {
        Tls tls = Tls.builder().trustAll(true).build();
        Http1Client client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri("https://localhost:" + tlsPort + "/connection-target")
                .tls(tls)
                .build();

        try {
            String first = client.get().request().as(String.class);
            tls.reload(TlsMaterial.builder().trustAll(true).build());
            String reloaded = client.get().request().as(String.class);

            assertThat(reloaded.substring(reloaded.indexOf('|')), not(is(first.substring(first.indexOf('|')))));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void oneOffRequestAfterTlsReloadCannotReuseStaleConnection() {
        Tls tls = Tls.builder().trustAll(true).build();
        Http1Client client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri("https://localhost:" + tlsPort + "/connection-target")
                .tls(tls)
                .build();

        try {
            String first = client.get().request().as(String.class);
            tls.reload(TlsMaterial.builder().trustAll(true).build());
            String oneOff = client.get().keepAlive(false).request().as(String.class);
            String cached = client.get().request().as(String.class);
            String reused = client.get().request().as(String.class);

            String firstConnection = first.substring(first.indexOf('|'));
            String oneOffConnection = oneOff.substring(oneOff.indexOf('|'));
            String cachedConnection = cached.substring(cached.indexOf('|'));
            assertThat(oneOffConnection, not(is(firstConnection)));
            assertThat(cachedConnection, not(is(firstConnection)));
            assertThat(cachedConnection, not(is(oneOffConnection)));
            assertThat(reused.substring(reused.indexOf('|')), is(cachedConnection));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void sameOriginExpectContinueRedirectProbesRetainSelectedProxyRoute() {
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

        Http1Client client = Http1Client.builder()
                .shareConnectionCache(false)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .baseUri("http://localhost:" + plainPort)
                .proxy(systemProxy)
                .build();
        String entity = "selected proxy route";

        try {
            try (Http1ClientResponse response = client.post("/proxy-route-first")
                    .maxRedirects(2)
                    .sendExpectContinue(true)
                    .outputStream(outputStream -> {
                        outputStream.write(entity.getBytes(StandardCharsets.UTF_8));
                        outputStream.close();
                })) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.as(String.class), is(entity));
            }
            assertThat(selectorInvocations.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void usesLogicalTlsPeerIdentityAfterDnsResolution() throws UnknownHostException {
        InetAddress physicalAddress = InetAddress.getByAddress("physical.invalid",
                                                               new byte[] {127, 0, 0, 1});
        assertThat(physicalAddress.getHostName(), is("physical.invalid"));

        Tls tls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        Http1Client client = Http1Client.builder()
                .shareConnectionCache(false)
                .dnsResolver((_, _) -> physicalAddress)
                .baseUri("https://localhost:" + tlsPort + "/connection-target")
                .tls(tls)
                .build();

        try {
            assertThat(client.get().request().as(String.class), startsWith("localhost|"));
        } finally {
            client.closeResource();
        }
    }
}
