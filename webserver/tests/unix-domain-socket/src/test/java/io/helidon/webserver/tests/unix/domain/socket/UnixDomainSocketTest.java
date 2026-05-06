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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
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
    private static Path socketPath;

    @SetUpServer
    public static void setUpServer(WebServerConfig.Builder builder) {
        socketPath = socketPath("helidon-socket");

        builder.bindAddress(UnixDomainSocketAddress.of(socketPath));
    }

    @SetUpRoute
    public static void setUpRoute(HttpRules rules) {
        rules.get("/test", (req, res) -> res.send("Hello World!"));
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
    public void testTls() {
        Path path = newSocketPath("helidon-tls-socket");
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
        WebServer server = startTlsServer(address, "Hello Secure World!", "Hello Secure HTTP/2!");
        WebClient webClient = WebClient.builder()
                .shareConnectionCache(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .baseUri("https://localhost")
                .tls(clientTls())
                .build();
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri("https://localhost")
                .tls(clientTls())
                .build();
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri("https://localhost")
                .tls(clientTls())
                .build();
        try {
            assertResponse(webClient.get()
                                   .address(address)
                                   .path("/h2")
                                   .request(String.class),
                           "Hello Secure HTTP/2!");
            assertResponse(http1Client.get()
                                   .address(address)
                                   .keepAlive(false)
                                   .path("/test")
                                   .request(String.class),
                           "Hello Secure World!");
            assertResponse(http2Client.get()
                                   .address(address)
                                   .path("/h2")
                                   .request(String.class),
                           "Hello Secure HTTP/2!");
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
                .baseUri("https://localhost")
                .tls(clientTls())
                .build();
        Http1Client http1Client = Http1Client.builder()
                .shareConnectionCache(false)
                .baseUri("https://localhost")
                .tls(clientTls())
                .build();
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri("https://localhost")
                .tls(clientTls())
                .build();
        try {
            assertResponse(webClient.get()
                                   .address(firstAddress)
                                   .path("/h2")
                                   .request(String.class),
                           "First HTTP/2");
            assertResponse(webClient.get()
                                   .address(secondAddress)
                                   .path("/h2")
                                   .request(String.class),
                           "Second HTTP/2");
            assertResponse(http1Client.get()
                                   .address(firstAddress)
                                   .path("/test")
                                   .request(String.class),
                           "First HTTP/1");
            assertResponse(http1Client.get()
                                   .address(secondAddress)
                                   .path("/test")
                                   .request(String.class),
                           "Second HTTP/1");
            assertResponse(http2Client.get()
                                   .address(firstAddress)
                                   .path("/h2")
                                   .request(String.class),
                           "First HTTP/2");
            assertResponse(http2Client.get()
                                   .address(secondAddress)
                                   .path("/h2")
                                   .request(String.class),
                           "Second HTTP/2");
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
                                                                  "localhost",
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
        try {
            Path path = Files.createTempFile(name, ".sock");
            Files.delete(path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
