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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http3.Http3Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class Http3AltSvcNegativeCacheTest {
    private static final String HELLO = "Hello";
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofMillis(250);
    private static final HeaderName ALT_USED = HeaderNames.create("Alt-Used");

    @Test
    void shouldPreserveNegativeCacheAfterSameTargetAltSvcRefresh() throws Exception {
        int port = freePort();

        try (FixedPortEnvironment environment = FixedPortEnvironment.http1Only(port, Set.of(1, 4));
             UdpSink udpSink = new UdpSink(port)) {
            WebClient client = newGenericClient(environment.baseUri(),
                                                environment.clientTls(),
                                                Http3ClientProtocolConfig.builder()
                                                        .initialResponseTimeout(HANDSHAKE_TIMEOUT)
                                                        .handshakeTimeout(HANDSHAKE_TIMEOUT)
                                                        .build());

            try {
                try (HttpClientResponse firstResponse = client.get("/hello").request()) {
                    assertThat(firstResponse.status(), is(Status.OK_200));
                    assertThat(firstResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(firstResponse.headers().contains(HeaderNames.ALT_SVC), is(true));
                    assertThat(firstResponse.entity().as(String.class), is(HELLO));
                }
                assertThat(udpSink.receivedCountAfterIdle(), is(0));

                try (HttpClientResponse secondResponse = client.get("/hello").request()) {
                    assertThat(secondResponse.status(), is(Status.OK_200));
                    assertThat(secondResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(secondResponse.entity().as(String.class), is(HELLO));
                }
                int afterFailedUpgrade = udpSink.receivedCountAfterIdle();
                assertThat(afterFailedUpgrade, greaterThan(0));
                assertThat(environment.altUsedReceived.get(), is(false));

                try (HttpClientResponse thirdResponse = client.get("/hello").request()) {
                    assertThat(thirdResponse.status(), is(Status.OK_200));
                    assertThat(thirdResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(thirdResponse.headers().contains(HeaderNames.ALT_SVC), is(false));
                    assertThat(thirdResponse.entity().as(String.class), is(HELLO));
                }
                assertThat(udpSink.receivedCountAfterIdle(), is(afterFailedUpgrade));

                try (HttpClientResponse fourthResponse = client.get("/hello").request()) {
                    assertThat(fourthResponse.status(), is(Status.OK_200));
                    assertThat(fourthResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(fourthResponse.headers().contains(HeaderNames.ALT_SVC), is(true));
                    assertThat(fourthResponse.entity().as(String.class), is(HELLO));
                }
                assertThat(udpSink.receivedCountAfterIdle(), is(afterFailedUpgrade));

                try (HttpClientResponse fifthResponse = client.get("/hello").request()) {
                    assertThat(fifthResponse.status(), is(Status.OK_200));
                    assertThat(fifthResponse.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(fifthResponse.entity().as(String.class), is(HELLO));
                }
                assertThat(udpSink.receivedCountAfterIdle(), is(afterFailedUpgrade));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldIsolateDiscoveryStateBetweenPrivateConnectionCaches() throws Exception {
        int port = freePort();
        Tls clientTls;

        try (FixedPortEnvironment environment = FixedPortEnvironment.http1Only(port, Set.of(1));
             UdpSink udpSink = new UdpSink(port)) {
            clientTls = environment.clientTls();

            WebClient client = newGenericClient(environment.baseUri(),
                                                clientTls,
                                                Http3ClientProtocolConfig.builder()
                                                        .initialResponseTimeout(HANDSHAKE_TIMEOUT)
                                                        .handshakeTimeout(HANDSHAKE_TIMEOUT)
                                                        .build());

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
                assertThat(udpSink.receivedCountAfterIdle(), greaterThan(0));
            } finally {
                client.closeResource();
            }
        }

        try (FixedPortEnvironment environment = FixedPortEnvironment.shared(port)) {
            Http3Client priorKnowledgeClient = Http3Client.builder()
                    .baseUri(environment.baseUri())
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .tls(clientTls)
                    .protocolConfig(Http3ClientProtocolConfig.builder()
                                            .priorKnowledge(true)
                                            .build())
                    .build();

            WebClient client = newGenericClient(environment.baseUri(),
                                                clientTls,
                                                Http3ClientProtocolConfig.builder()
                                                        .initialResponseTimeout(HANDSHAKE_TIMEOUT)
                                                        .handshakeTimeout(HANDSHAKE_TIMEOUT)
                                                        .build());

            try {
                try (Http3ClientResponse response = priorKnowledgeClient.get("/hello").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is(HELLO));
                }

                try (HttpClientResponse response = client.get("/hello").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is(HELLO));
                }
            } finally {
                client.closeResource();
                priorKnowledgeClient.closeResource();
            }
        }
    }

    private static WebClient newGenericClient(String baseUri, Tls tls, Http3ClientProtocolConfig protocolConfig) {
        return WebClient.builder()
                .baseUri(baseUri)
                .shareConnectionCache(false)
                .keepAlive(false)
                .proxy(Proxy.noProxy())
                .tls(tls)
                .altSvc(ClientAltSvcConfig.create())
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(protocolConfig)
                .build();
    }

    private static int freePort() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, loopback);
             DatagramSocket ignored = new DatagramSocket(new InetSocketAddress(loopback, serverSocket.getLocalPort()))) {
            return serverSocket.getLocalPort();
        }
    }

    private static final class FixedPortEnvironment implements AutoCloseable {
        private final WebServer server;
        private final Http3TlsSupport.Http3TlsMaterials tlsMaterials;
        private final AtomicBoolean altUsedReceived;

        private FixedPortEnvironment(WebServer server,
                                     Http3TlsSupport.Http3TlsMaterials tlsMaterials,
                                     AtomicBoolean altUsedReceived) {
            this.server = server;
            this.tlsMaterials = tlsMaterials;
            this.altUsedReceived = altUsedReceived;
        }

        private static FixedPortEnvironment http1Only(int port, Set<Integer> altSvcResponses) throws Exception {
            Http3TlsSupport.Http3TlsMaterials tlsMaterials = Http3TlsSupport.load();
            AtomicBoolean altUsedReceived = new AtomicBoolean();

            WebServer server = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(port)
                    .bindingsDiscoverServices(false)
                    .addBinding(TcpTransportConfig.create())
                    .protocolsDiscoverServices(false)
                    .tls(tlsMaterials.serverTls())
                    .addProtocol(Http1Config.create())
                    .routing(routing(port, altSvcResponses, altUsedReceived))
                    .build()
                    .start();

            return new FixedPortEnvironment(server, tlsMaterials, altUsedReceived);
        }

        private static FixedPortEnvironment shared(int port) throws Exception {
            Http3TlsSupport.Http3TlsMaterials tlsMaterials = Http3TlsSupport.load();
            AtomicBoolean altUsedReceived = new AtomicBoolean();

            WebServer server = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(port)
                    .protocolsDiscoverServices(false)
                    .tls(tlsMaterials.serverTls())
                    .addProtocol(Http1Config.create())
                    .addProtocol(Http3Config.create())
                    .routing(routing(port, Set.of(), altUsedReceived))
                    .build()
                    .start();

            return new FixedPortEnvironment(server, tlsMaterials, altUsedReceived);
        }

        private static HttpRouting.Builder routing(int port,
                                                   Set<Integer> altSvcResponses,
                                                   AtomicBoolean altUsedReceived) {
            AtomicInteger requestCounter = new AtomicInteger();
            return HttpRouting.builder()
                    .get("/hello", (req, res) -> {
                        if (req.headers().contains(ALT_USED)) {
                            altUsedReceived.set(true);
                        }
                        int requestIndex = requestCounter.incrementAndGet();
                        if (altSvcResponses.contains(requestIndex)) {
                            res.header(HeaderNames.ALT_SVC, altSvcHeader(port));
                        }
                        res.send(HELLO);
                    });
        }

        private static String altSvcHeader(int port) {
            return "h3=\":%d\"".formatted(port);
        }

        private String baseUri() {
            return "https://localhost:" + server.port();
        }

        private Tls clientTls() {
            return tlsMaterials.clientTls();
        }

        @Override
        public void close() {
            server.stop();
        }
    }

    private static final class UdpSink implements AutoCloseable {
        private final DatagramSocket socket;
        private final AtomicInteger receivedCount = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicReference<CountDownLatch> idleWaiter = new AtomicReference<>();
        private final Thread receiverThread;

        private UdpSink(int port) throws Exception {
            this.socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            this.socket.setSoTimeout(100);
            this.receiverThread = Thread.ofPlatform()
                    .name("http3-alt-svc-udp-sink-" + port)
                    .start(this::receiveLoop);
        }

        private int receivedCountAfterIdle() {
            CountDownLatch idle = new CountDownLatch(1);
            if (!idleWaiter.compareAndSet(null, idle)) {
                throw new IllegalStateException("UDP idle wait already in progress.");
            }
            try {
                if (!idle.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for UDP sink to become idle.");
                }
                return receivedCount.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for UDP sink to become idle.", e);
            } finally {
                idleWaiter.compareAndSet(idle, null);
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                socket.close();
                try {
                    receiverThread.join(Duration.ofSeconds(5).toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while stopping UDP sink.", e);
                }
            }
        }

        private void receiveLoop() {
            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (!closed.get()) {
                try {
                    socket.receive(packet);
                    receivedCount.incrementAndGet();
                } catch (SocketTimeoutException _) {
                    CountDownLatch idle = idleWaiter.getAndSet(null);
                    if (idle != null) {
                        idle.countDown();
                    }
                } catch (SocketException ignored) {
                    if (!closed.get()) {
                        throw new IllegalStateException("UDP sink closed unexpectedly.", ignored);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("UDP sink failed while receiving datagrams.", e);
                }
            }
        }
    }
}
