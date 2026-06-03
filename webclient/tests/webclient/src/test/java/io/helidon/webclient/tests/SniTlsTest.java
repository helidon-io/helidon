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

package io.helidon.webclient.tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SniTlsTest {
    private static final String MESSAGE = "SNI works";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void explicitSniReachesTlsServer() throws Exception {
        CapturedExchange exchange = exchange(clientTls(), client -> client.get()
                .keepAlive(false)
                .sni(it -> it.mode(SniMode.EXPLICIT).host("explicit.example"))
                .requestEntity(String.class));

        assertThat(exchange.response(), is(MESSAGE));
        assertThat(exchange.serverNames(), is(List.of("explicit.example")));
    }

    @Test
    void requestSniOverridesClientSni() throws Exception {
        CapturedExchange exchange = exchange(clientTls(),
                                             builder -> builder.sni(it -> it.mode(SniMode.EXPLICIT).host("client.example")),
                                             client -> client.get()
                                                     .keepAlive(false)
                                                     .sni(it -> it.mode(SniMode.EXPLICIT).host("request.example"))
                                                     .requestEntity(String.class));

        assertThat(exchange.response(), is(MESSAGE));
        assertThat(exchange.serverNames(), is(List.of("request.example")));
    }

    @Test
    void noFirstClassSniPreservesRawTlsServerNames() throws Exception {
        CapturedExchange exchange = exchange(clientTlsWithRawSni(), client -> client.get()
                .keepAlive(false)
                .requestEntity(String.class));

        assertThat(exchange.response(), is(MESSAGE));
        assertThat(exchange.serverNames(), is(List.of("explicit.example")));
    }

    @Test
    void disabledSniClearsRawTlsServerNames() throws Exception {
        CapturedExchange exchange = exchange(clientTlsWithRawSni(), client -> client.get()
                .keepAlive(false)
                .sni(it -> it.mode(SniMode.DISABLED))
                .requestEntity(String.class));

        assertThat(exchange.response(), is(MESSAGE));
        assertThat(exchange.serverNames(), is(List.of()));
    }

    @Test
    void hostHeaderSniReachesHttp1TlsServer() throws Exception {
        CapturedExchange exchange = exchange(clientTls(), client -> client.get()
                .keepAlive(false)
                .header(HeaderValues.create(HeaderNames.HOST, "host-header.example"))
                .sni(it -> it.mode(SniMode.HOST_HEADER))
                .requestEntity(String.class));

        assertThat(exchange.response(), is(MESSAGE));
        assertThat(exchange.serverNames(), is(List.of("host-header.example")));
    }

    @Test
    void hostHeaderSniSeparatesHttp1ConnectionCache() throws Exception {
        try (KeepAliveTlsServer server = KeepAliveTlsServer.start(2)) {
            Http1Client client = Http1Client.builder()
                    .baseUri("https://localhost:" + server.port())
                    .tls(clientTls())
                    .build();
            try {
                String firstResponse = client.get()
                        .header(HeaderValues.create(HeaderNames.HOST, "first.example"))
                        .sni(it -> it.mode(SniMode.HOST_HEADER))
                        .requestEntity(String.class);
                String secondResponse = client.get()
                        .header(HeaderValues.create(HeaderNames.HOST, "second.example"))
                        .sni(it -> it.mode(SniMode.HOST_HEADER))
                        .requestEntity(String.class);

                CapturedConnections connections = server.awaitConnections();
                assertThat(firstResponse, is(MESSAGE));
                assertThat(secondResponse, is(MESSAGE));
                assertThat(connections.serverNames(), is(List.of(List.of("first.example"), List.of("second.example"))));
            } finally {
                client.closeResource();
            }
        }
    }

    private static CapturedExchange exchange(Tls clientTls, RequestInvoker requestInvoker) throws Exception {
        return exchange(clientTls, builder -> { }, requestInvoker);
    }

    private static CapturedExchange exchange(Tls clientTls,
                                             Consumer<Http1ClientConfig.Builder> clientCustomizer,
                                             RequestInvoker requestInvoker) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = serverSocket()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            CompletableFuture<List<String>> serverNames = CompletableFuture.supplyAsync(() -> handleRequest(server), executor);
            Http1ClientConfig.Builder builder = Http1Client.builder()
                    .baseUri("https://localhost:" + server.getLocalPort())
                    .tls(clientTls);
            clientCustomizer.accept(builder);
            Http1Client client = builder.build();
            try {
                String response = requestInvoker.invoke(client);
                return new CapturedExchange(response, serverNames.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            } finally {
                client.closeResource();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static SSLServerSocket serverSocket() {
        return serverTls().createServerSocket();
    }

    private static List<String> handleRequest(SSLServerSocket server) {
        try (SSLSocket socket = (SSLSocket) server.accept()) {
            socket.startHandshake();
            List<String> serverNames = requestedServerNames(socket);

            readHeaders(socket);
            byte[] entity = MESSAGE.getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream()
                    .write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Length: " + entity.length + "\r\n"
                            + "Connection: close\r\n\r\n")
                                   .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(entity);
            socket.getOutputStream().flush();
            return serverNames;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void readHeaders(SSLSocket socket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        readHeaders(reader);
    }

    private static boolean readHeaders(BufferedReader reader) throws IOException {
        String line;
        boolean foundRequest = false;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // consume request headers
            foundRequest = true;
        }
        return foundRequest;
    }

    private static List<String> requestedServerNames(SSLSocket socket) {
        SSLSession session = socket.getSession();
        if (session instanceof ExtendedSSLSession extended) {
            return extended.getRequestedServerNames()
                    .stream()
                    .map(SniTlsTest::serverName)
                    .toList();
        }
        return List.of();
    }

    private static String serverName(SNIServerName serverName) {
        if (serverName instanceof SNIHostName hostName) {
            return hostName.getAsciiName();
        }
        return serverName.toString();
    }

    private static Tls serverTls() {
        return Tls.builder()
                .privateKey(key -> key
                        .keystore(store -> store
                                .passphrase("password")
                                .keystore(Resource.create("server.p12"))))
                .privateKeyCertChain(key -> key
                        .keystore(store -> store
                                .trustStore(true)
                                .passphrase("password")
                                .keystore(Resource.create("server.p12"))))
                .build();
    }

    private static Tls clientTls() {
        return Tls.builder()
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
    }

    private static Tls clientTlsWithRawSni() {
        SSLParameters parameters = new SSLParameters();
        parameters.setEndpointIdentificationAlgorithm("");
        parameters.setServerNames(List.of(new SNIHostName("explicit.example")));

        return Tls.builder()
                .sslParameters(parameters)
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
    }

    private interface RequestInvoker {
        String invoke(Http1Client client);
    }

    private record CapturedExchange(String response, List<String> serverNames) {
    }

    private record CapturedConnections(List<List<String>> serverNames) {
    }

    private static final class KeepAliveTlsServer implements AutoCloseable {
        private final SSLServerSocket server;
        private final int expectedRequestCount;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final List<List<String>> serverNames = new CopyOnWriteArrayList<>();
        private final List<SSLSocket> sockets = new CopyOnWriteArrayList<>();
        private final CompletableFuture<CapturedConnections> captured = new CompletableFuture<>();

        private KeepAliveTlsServer(SSLServerSocket server, int expectedRequestCount) {
            this.server = server;
            this.expectedRequestCount = expectedRequestCount;
            executor.submit(this::accept);
        }

        static KeepAliveTlsServer start(int expectedRequestCount) throws IOException {
            SSLServerSocket server = serverSocket();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return new KeepAliveTlsServer(server, expectedRequestCount);
        }

        int port() {
            return server.getLocalPort();
        }

        CapturedConnections awaitConnections() throws Exception {
            return captured.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            server.close();
            for (SSLSocket socket : sockets) {
                socket.close();
            }
            executor.shutdownNow();
        }

        private void accept() {
            try {
                while (!captured.isDone()) {
                    SSLSocket socket = (SSLSocket) server.accept();
                    sockets.add(socket);
                    executor.submit(() -> handle(socket));
                }
            } catch (SocketException e) {
                if (!captured.isDone()) {
                    captured.completeExceptionally(e);
                }
            } catch (IOException e) {
                captured.completeExceptionally(e);
            }
        }

        private void handle(SSLSocket socket) {
            try (SSLSocket accepted = socket;
                    BufferedReader reader = new BufferedReader(new InputStreamReader(accepted.getInputStream(),
                                                                                     StandardCharsets.US_ASCII));
                    OutputStream output = accepted.getOutputStream()) {

                accepted.startHandshake();
                serverNames.add(requestedServerNames(accepted));
                while (!captured.isDone() && readHeaders(reader)) {
                    int currentCount = requestCount.incrementAndGet();
                    boolean lastResponse = currentCount >= expectedRequestCount;
                    writeResponse(output, lastResponse);
                    if (lastResponse) {
                        captured.complete(new CapturedConnections(List.copyOf(serverNames)));
                    }
                }
            } catch (Throwable t) {
                captured.completeExceptionally(t);
            }
        }

        private void writeResponse(OutputStream output, boolean close) throws IOException {
            byte[] entity = MESSAGE.getBytes(StandardCharsets.UTF_8);
            output.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Length: " + entity.length + "\r\n"
                    + "Connection: " + (close ? "close" : "keep-alive") + "\r\n\r\n")
                                 .getBytes(StandardCharsets.US_ASCII));
            output.write(entity);
            output.flush();
        }
    }
}
