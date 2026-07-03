/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.unix.domain.socket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.socket.TlsNioSocket;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.ConnectedSocketChannelInfo;
import io.helidon.webclient.api.ConnectedSocketInfo;
import io.helidon.webclient.api.ConnectionListener;
import io.helidon.webclient.api.UnixDomainSocketClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test UNIX domain socket support (both server and client).
 */
@ServerTest
public class UnixDomainSocketTest {
    private static final String LOGICAL_HOST = "service.example";
    private static final String LOGICAL_AUTHORITY = LOGICAL_HOST + ":443";
    private static final String LOGICAL_BASE_URI = "https://" + LOGICAL_HOST;
    private static final String LOGICAL_PLAIN_BASE_URI = "http://127.0.0.1:1";
    private static final String LOGICAL_PLAIN_AUTHORITY = "127.0.0.1:1";

    private static Path socketPath;

    @SetUpServer
    public static void setUpServer(WebServerConfig.Builder builder) {
        socketPath = socketPath("helidon-socket");

        builder.bindAddress(UnixDomainSocketAddress.of(socketPath));
    }

    @SetUpRoute
    public static void setUpRoute(HttpRules rules) {
        rules.get("/test", (req, res) -> res.send("Hello World!"));
        rules.get("/redirect-http1", (req, res) -> res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "/redirected-http1")
                .send());
        rules.get("/redirected-http1", (req, res) -> res.send(req.authority()));
        rules.get("/redirect-http1-cross-origin", (req, res) -> res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, req.query().get("target"))
                .send());
        rules.route(Http2Route.route(Method.GET, "/redirect-http2", (req, res) -> res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, "/redirected-http2")
                .send()));
        rules.route(Http2Route.route(Method.GET, "/redirected-http2", (req, res) -> res.send(req.authority())));
        rules.route(Http2Route.route(Method.GET, "/redirect-http2-cross-origin", (req, res) -> res.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, req.query().get("target"))
                .send()));
    }

    @Test
    public void test() {
        WebClient webClient = WebClient.create();
        var address = UnixDomainSocketAddress.of(socketPath);
        var response = webClient.get()
                .address(address)
                .path("/test")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("Hello World!"));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void http1UnixSocketSameOriginRedirectPreservesTransportAddress() {
        var address = UnixDomainSocketAddress.of(socketPath);
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_PLAIN_BASE_URI)
                .build();
        try {
            assertResponse(http1Client.get()
                                   .address(address)
                                   .path("/redirect-http1")
                                   .request(String.class),
                           LOGICAL_PLAIN_AUTHORITY);
        } finally {
            http1Client.closeResource();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void http2UnixSocketSameOriginRedirectPreservesTransportAddress() {
        var address = UnixDomainSocketAddress.of(socketPath);
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_PLAIN_BASE_URI)
                .protocolConfig(it -> it.priorKnowledge(true))
                .build();
        try {
            assertResponse(http2Client.get()
                                   .address(address)
                                   .path("/redirect-http2")
                                   .request(String.class),
                           LOGICAL_PLAIN_AUTHORITY);
        } finally {
            http2Client.closeResource();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void http1UnixSocketBaseAddressSameOriginRedirectPreservesTransportAddress() {
        var address = UnixDomainSocketAddress.of(socketPath);
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_PLAIN_BASE_URI)
                .baseAddress(address)
                .build();
        try {
            assertResponse(http1Client.get()
                                   .path("/redirect-http1")
                                   .request(String.class),
                           LOGICAL_PLAIN_AUTHORITY);
        } finally {
            http1Client.closeResource();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void http2UnixSocketBaseAddressSameOriginRedirectPreservesTransportAddress() {
        var address = UnixDomainSocketAddress.of(socketPath);
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_PLAIN_BASE_URI)
                .baseAddress(address)
                .protocolConfig(it -> it.priorKnowledge(true))
                .build();
        try {
            assertResponse(http2Client.get()
                                   .path("/redirect-http2")
                                   .request(String.class),
                           LOGICAL_PLAIN_AUTHORITY);
        } finally {
            http2Client.closeResource();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void http1UnixSocketCrossOriginRedirectDropsTransportAddress() {
        var address = UnixDomainSocketAddress.of(socketPath);
        WebServer targetServer = startPlainServer("HTTP/1 TCP target", false);
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_PLAIN_BASE_URI)
                .build();
        try {
            assertResponse(http1Client.get()
                                   .address(address)
                                   .path("/redirect-http1-cross-origin")
                                   .queryParam("target", "http://127.0.0.1:" + targetServer.port() + "/target")
                                   .request(String.class),
                           "HTTP/1 TCP target");
        } finally {
            http1Client.closeResource();
            targetServer.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void http1UnixSocketBaseAddressCrossOriginRedirectDropsTransportAddress() {
        var address = UnixDomainSocketAddress.of(socketPath);
        WebServer targetServer = startPlainServer("HTTP/1 TCP target", false);
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_PLAIN_BASE_URI)
                .baseAddress(address)
                .build();
        try {
            assertResponse(http1Client.get()
                                   .path("/redirect-http1-cross-origin")
                                   .queryParam("target", "http://127.0.0.1:" + targetServer.port() + "/redirect-again")
                                   .request(String.class),
                           "HTTP/1 TCP target");
        } finally {
            http1Client.closeResource();
            targetServer.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void http2UnixSocketBaseAddressCrossOriginRedirectDropsTransportAddress() {
        var address = UnixDomainSocketAddress.of(socketPath);
        WebServer targetServer = startPlainServer("HTTP/2 TCP target", true);
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_PLAIN_BASE_URI)
                .baseAddress(address)
                .protocolConfig(it -> it.priorKnowledge(true))
                .build();
        try {
            assertResponse(http2Client.get()
                                   .path("/redirect-http2-cross-origin")
                                   .queryParam("target", "http://127.0.0.1:" + targetServer.port() + "/redirect-again")
                                   .request(String.class),
                           "HTTP/2 TCP target");
        } finally {
            http2Client.closeResource();
            targetServer.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void http2UnixSocketCrossOriginRedirectDropsTransportAddress() {
        var address = UnixDomainSocketAddress.of(socketPath);
        WebServer targetServer = startPlainServer("HTTP/2 TCP target", true);
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_PLAIN_BASE_URI)
                .protocolConfig(it -> it.priorKnowledge(true))
                .build();
        try {
            assertResponse(http2Client.get()
                                   .address(address)
                                   .path("/redirect-http2-cross-origin")
                                   .queryParam("target", "http://127.0.0.1:" + targetServer.port() + "/target")
                                   .request(String.class),
                           "HTTP/2 TCP target");
        } finally {
            http2Client.closeResource();
            targetServer.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testTls() {
        Path path = newSocketPath("helidon-tls-socket");
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
        WebServer server = startTlsServer(address, "Hello Secure World!", "Hello Secure HTTP/2!");
        WebClient webClient = WebClient.builder()
                .shareConnectionCache(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .build();
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .build();
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .build();
        try {
            assertResponse(webClient.get()
                                   .address(address)
                                   .path("/h2")
                                   .request(String.class),
                           "Hello Secure HTTP/2!",
                           LOGICAL_HOST);
            assertResponse(http1Client.get()
                                   .address(address)
                                   .keepAlive(false)
                                   .path("/test")
                                   .request(String.class),
                           "Hello Secure World!",
                           LOGICAL_HOST);
            assertResponse(http2Client.get()
                                   .address(address)
                                   .path("/h2")
                                   .request(String.class),
                           "Hello Secure HTTP/2!",
                           LOGICAL_HOST);
        } finally {
            http2Client.closeResource();
            http1Client.closeResource();
            webClient.closeResource();
            server.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void tlsUnixSocketPreservesLogicalAuthority() {
        Path path = newSocketPath("helidon-tls-logical-authority");
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
        WebServer server = startTlsAuthorityServer(address);
        WebClient webClient = WebClient.builder()
                .shareConnectionCache(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .build();
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .build();
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .build();
        try {
            assertResponse(webClient.get()
                                   .address(address)
                                   .path("/h2")
                                   .request(String.class),
                           LOGICAL_AUTHORITY,
                           LOGICAL_HOST);
            assertResponse(http1Client.get()
                                   .address(address)
                                   .keepAlive(false)
                                   .path("/test")
                                   .request(String.class),
                           LOGICAL_AUTHORITY,
                           LOGICAL_HOST);
            assertResponse(http2Client.get()
                                   .address(address)
                                   .path("/h2")
                                   .request(String.class),
                           LOGICAL_AUTHORITY,
                           LOGICAL_HOST);
        } finally {
            http2Client.closeResource();
            http1Client.closeResource();
            webClient.closeResource();
            server.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void udsConnectionCachesUseSocketPathWithLogicalAuthority() {
        UnixDomainSocketAddress firstAddress = UnixDomainSocketAddress.of(newSocketPath("helidon-tls-cache-first"));
        UnixDomainSocketAddress secondAddress = UnixDomainSocketAddress.of(newSocketPath("helidon-tls-cache-second"));
        WebServer firstServer = startTlsServer(firstAddress, "First HTTP/1", "First HTTP/2");
        WebServer secondServer = startTlsServer(secondAddress, "Second HTTP/1", "Second HTTP/2");
        WebClient webClient = WebClient.builder()
                .shareConnectionCache(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .build();
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .build();
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .build();
        try {
            assertResponse(webClient.get()
                                   .address(firstAddress)
                                   .path("/h2")
                                   .request(String.class),
                           "First HTTP/2",
                           LOGICAL_HOST);
            assertResponse(webClient.get()
                                   .address(secondAddress)
                                   .path("/h2")
                                   .request(String.class),
                           "Second HTTP/2",
                           LOGICAL_HOST);
            assertResponse(http1Client.get()
                                   .address(firstAddress)
                                   .path("/test")
                                   .request(String.class),
                           "First HTTP/1",
                           LOGICAL_HOST);
            assertResponse(http1Client.get()
                                   .address(secondAddress)
                                   .path("/test")
                                   .request(String.class),
                           "Second HTTP/1",
                           LOGICAL_HOST);
            assertResponse(http2Client.get()
                                   .address(firstAddress)
                                   .path("/h2")
                                   .request(String.class),
                           "First HTTP/2",
                           LOGICAL_HOST);
            assertResponse(http2Client.get()
                                   .address(secondAddress)
                                   .path("/h2")
                                   .request(String.class),
                           "Second HTTP/2",
                           LOGICAL_HOST);
        } finally {
            http2Client.closeResource();
            http1Client.closeResource();
            webClient.closeResource();
            secondServer.stop();
            firstServer.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void http1TlsUnixSocketReusesConnection() {
        Path path = newSocketPath("helidon-tls-http1-cache");
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
        AtomicInteger connectCount = new AtomicInteger();
        AtomicReference<SocketChannel> connectedChannel = new AtomicReference<>();
        WebServer server = startTlsServer(address, "Hello Secure World!", "Hello Secure HTTP/2!");
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .connectionListener(new ConnectionListener() {
                    @Override
                    public void socketConnected(ConnectedSocketInfo socketInfo) {
                    }

                    @Override
                    public void socketChannelConnected(ConnectedSocketChannelInfo socketInfo) {
                        connectedChannel.set(socketInfo.socketChannel());
                        connectCount.incrementAndGet();
                    }
                })
                .build();
        try {
            assertResponse(http1Client.get()
                                   .address(address)
                                   .path("/test")
                                   .request(String.class),
                           "Hello Secure World!",
                           LOGICAL_HOST);
            assertResponse(http1Client.get()
                                   .address(address)
                                   .path("/test")
                                   .request(String.class),
                           "Hello Secure World!",
                           LOGICAL_HOST);

            assertThat(connectedChannel.get(), notNullValue());
            assertThat(connectCount.get(), is(1));
        } finally {
            http1Client.closeResource();
            server.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void http1TlsUnixSocketDiscardsRemotelyClosedConnection() throws Exception {
        Path path = newSocketPath("helidon-tls-http1-stale-cache");
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
        CountDownLatch firstConnectionClosed = new CountDownLatch(1);
        AtomicInteger connectCount = new AtomicInteger();
        RawTlsHttp1Server server = rawTlsHttp1Server(address, firstConnectionClosed);
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri(LOGICAL_BASE_URI)
                .tls(clientTls())
                .connectionListener(new ConnectionListener() {
                    @Override
                    public void socketConnected(ConnectedSocketInfo socketInfo) {
                    }

                    @Override
                    public void socketChannelConnected(ConnectedSocketChannelInfo socketInfo) {
                        connectCount.incrementAndGet();
                    }
                })
                .build();
        try {
            assertResponse(http1Client.get()
                                   .address(address)
                                   .path("/test")
                                   .request(String.class),
                           "First response",
                           LOGICAL_HOST);
            assertThat(firstConnectionClosed.await(5, TimeUnit.SECONDS), is(true));
            assertResponse(http1Client.get()
                                   .address(address)
                                   .path("/test")
                                   .request(String.class),
                           "Second response",
                           LOGICAL_HOST);

            assertThat(connectCount.get(), is(2));
            server.await();
        } finally {
            http1Client.closeResource();
            server.close();
            Files.deleteIfExists(path);
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void stalledTlsUnixHandshakeHonorsReadTimeout() throws Exception {
        Path path = newSocketPath("helidon-stalled-tls-socket");
        AtomicReference<SocketChannel> connectedChannel = new AtomicReference<>();
        WebClient client = WebClient.builder()
                .readTimeout(Duration.ofMillis(100))
                .connectionListener(new ConnectionListener() {
                    @Override
                    public void socketConnected(ConnectedSocketInfo socketInfo) {
                    }

                    @Override
                    public void socketChannelConnected(ConnectedSocketChannelInfo socketInfo) {
                        connectedChannel.set(socketInfo.socketChannel());
                    }
                })
                .build();
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(path));
            CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                try (SocketChannel socket = server.accept()) {
                    while (socket.read(buffer) >= 0) {
                        buffer.clear();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            UncheckedIOException thrown = assertThrows(
                    UncheckedIOException.class,
                    () -> UnixDomainSocketClientConnection.create(client,
                                                                  Tls.builder().build(),
                                                                  List.of(Http1Client.PROTOCOL_ID),
                                                                  UnixDomainSocketAddress.of(path),
                                                                  LOGICAL_HOST,
                                                                  443,
                                                                  it -> false,
                                                                  it -> {
                                                                  })
                            .connect());

            assertThat(thrown.getCause(), instanceOf(SocketTimeoutException.class));
            SocketChannel channel = connectedChannel.get();
            assertThat(channel, notNullValue());
            assertThat(channel.isOpen(), is(false));

            accepted.get(5, TimeUnit.SECONDS);
        } finally {
            client.closeResource();
            Files.deleteIfExists(path);
        }
    }

    private static void assertResponse(ClientResponseTyped<String> response, String expectedEntity) {
        try {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.entity(), is(expectedEntity));
        } finally {
            response.close();
        }
    }

    private static void assertResponse(ClientResponseTyped<String> response, String expectedEntity, String expectedHost) {
        try {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.lastEndpointUri().host(), is(expectedHost));
            assertThat(response.entity(), is(expectedEntity));
        } finally {
            response.close();
        }
    }

    private static WebServer startTlsServer(UnixDomainSocketAddress address, String http1Response, String http2Response) {
        return WebServer.builder()
                .bindAddress(address)
                .tls(serverTls())
                .routing(rules -> {
                    rules.get("/test", (req, res) -> res.send(http1Response));
                    rules.route(Http2Route.route(Method.GET, "/h2", (req, res) -> res.send(http2Response)));
                })
                .build()
                .start();
    }

    private static WebServer startPlainServer(String response, boolean http2) {
        return WebServer.builder()
                .host("127.0.0.1")
                .port(http2 ? -1 : 0)
                .routing(rules -> {
                    if (http2) {
                        rules.route(Http2Route.route(Method.GET, "/redirect-again", (req, res) -> res.status(Status.FOUND_302)
                                .header(HeaderNames.LOCATION, "/target")
                                .send()));
                        rules.route(Http2Route.route(Method.GET, "/target", (req, res) -> res.send(response)));
                    } else {
                        rules.get("/redirect-again", (req, res) -> res.status(Status.FOUND_302)
                                .header(HeaderNames.LOCATION, "/target")
                                .send());
                        rules.get("/target", (req, res) -> res.send(response));
                    }
                })
                .build()
                .start();
    }

    private static RawTlsHttp1Server rawTlsHttp1Server(UnixDomainSocketAddress address,
                                                       CountDownLatch firstConnectionClosed) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(address);
        AtomicReference<SocketChannel> accepted = new AtomicReference<>();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                handleRawTlsHttp1Request(server, accepted, "First response", firstConnectionClosed);
                handleRawTlsHttp1Request(server, accepted, "Second response", null);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                try {
                    server.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        return new RawTlsHttp1Server(server, accepted, future);
    }

    private static void handleRawTlsHttp1Request(ServerSocketChannel server,
                                                 AtomicReference<SocketChannel> accepted,
                                                 String entity,
                                                 CountDownLatch connectionClosed) throws IOException {
        try (SocketChannel channel = server.accept()) {
            accepted.set(channel);
            TlsNioSocket socket = TlsNioSocket.server(channel,
                                                      serverTls().sslContext().createSSLEngine(),
                                                      "listener",
                                                      "server");
            socket.handshake();
            readRawHttp1Headers(socket);
            byte[] entityBytes = entity.getBytes(US_ASCII);
            socket.write(BufferData.create("HTTP/1.1 200 OK\r\n"
                                                   + "Content-Length: " + entityBytes.length + "\r\n"
                                                   + "\r\n"));
            socket.write(BufferData.create(entityBytes));
            socket.close();
        } finally {
            accepted.set(null);
            if (connectionClosed != null) {
                connectionClosed.countDown();
            }
        }
    }

    private record RawTlsHttp1Server(ServerSocketChannel channel,
                                     AtomicReference<SocketChannel> accepted,
                                     CompletableFuture<Void> future) implements AutoCloseable {
        void await() throws Exception {
            future.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            if (future.isDone()) {
                return;
            }
            channel.close();
            SocketChannel acceptedChannel = accepted.get();
            if (acceptedChannel != null) {
                acceptedChannel.close();
            }
            try {
                await();
            } catch (ExecutionException e) {
                if (!closedByTest(e)) {
                    throw e;
                }
            }
        }

        private static boolean closedByTest(ExecutionException e) {
            Throwable cause = e.getCause();
            return cause instanceof UncheckedIOException unchecked
                    && unchecked.getCause() instanceof ClosedChannelException;
        }
    }

    private static void readRawHttp1Headers(TlsNioSocket socket) {
        StringBuilder request = new StringBuilder();
        while (request.indexOf("\r\n\r\n") < 0) {
            byte[] data = socket.get();
            if (data == null) {
                fail("Connection closed before reading HTTP/1 headers");
            }
            request.append(new String(data, US_ASCII));
        }
    }

    private static WebServer startTlsAuthorityServer(UnixDomainSocketAddress address) {
        return WebServer.builder()
                .bindAddress(address)
                .tls(serverTls())
                .routing(rules -> {
                    rules.get("/test", (req, res) -> res.send(req.authority()));
                    rules.route(Http2Route.route(Method.GET, "/h2", (req, res) -> res.send(req.authority())));
                })
                .build()
                .start();
    }

    private static Path socketPath(String name) {
        String base = System.getProperty("java.io.tmpdir") + "/" + name;
        String suffix = ".sock";

        for (int i = 0; i < 100; i++) {
            String tryit = base + (i == 0 ? "" : String.valueOf(i)) + suffix;
            Path path = Paths.get(tryit);
            if (!Files.exists(path)) {
                return path;
            }
        }

        fail("Failed to find a free UNIX domain socket path. Tried 100 possibilities for " + base + suffix);
        throw new IllegalStateException("Unreachable");
    }

    private static Path newSocketPath(String name) {
        return socketPath(name);
    }

    private static Tls serverTls() {
        Keys privateKeyConfig = Keys.builder()
                .keystore(store -> store
                        .passphrase("changeit")
                        .keystore(Resource.create("server.p12")))
                .build();

        return Tls.builder()
                .privateKey(privateKeyConfig.privateKey().orElseThrow())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();
    }

    private static Tls clientTls() {
        return Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("changeit")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
    }
}
