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

package io.helidon.webserver.tests.http3;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.http.Header;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ControlStreamListener;
import io.helidon.http.http3.Http3ControlStreamSupport;
import io.helidon.http.http3.Http3GoAway;
import io.helidon.http.http3.Http3PeerCriticalStreams;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3Settings;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.http.http3.Http3StreamType;
import io.helidon.quic.QuicClientConnection;
import io.helidon.quic.QuicClientRuntime;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientConfig;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http3.Http3Config;
import io.helidon.webserver.quic.QuicTransportConfig;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;

final class Http3TestSupport {
    static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final char[] PASSWORD = "password".toCharArray();

    private Http3TestSupport() {
    }

    static Tls serverTls(String keystoreResource) throws Exception {
        Keys keys = serverKeys(keystoreResource);

        return Tls.builder()
                .privateKey(keys.privateKey().orElseThrow())
                .privateKeyCertChain(keys.certChain())
                .build();
    }

    static TlsMaterial serverTlsMaterial(String keystoreResource) throws Exception {
        Keys keys = serverKeys(keystoreResource);

        return TlsMaterial.builder()
                .privateKey(keys.privateKey().orElseThrow())
                .privateKeyCertChain(keys.certChain())
                .build();
    }

    static SSLContext clientSslContext(String trustStoreResource) throws Exception {
        KeyStore trustStore = loadStore(trustStoreResource);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
        return clientContext;
    }

    static Tls clientTls(String trustStoreResource) {
        return Tls.builder()
                .trust(trust -> trust.keystore(store -> store
                        .passphrase("password")
                        .trustStore(true)
                        .keystore(Resource.create(trustStoreResource))))
                .applicationProtocols(List.of(Http3Client.PROTOCOL_ID))
                .enabledProtocols(List.of("TLSv1.3"))
                .build();
    }

    static Http3ClientConfig.Builder strictClientBuilder() {
        return strictClientBuilder(Http3ClientProtocolConfig.builder());
    }

    static Http3ClientConfig.Builder strictClientBuilder(Http3ClientProtocolConfig.Builder protocolConfig) {
        return Http3Client.builder()
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .protocolConfig(protocolConfig.priorKnowledge(true).build());
    }

    static HttpClient http1Client(SSLContext sslContext) {
        return HttpClient.newBuilder()
                .version(HTTP_1_1)
                .connectTimeout(TIMEOUT)
                .proxy(ProxySelector.of(null))
                .sslContext(sslContext)
                .build();
    }

    static HttpClient http3Client(SSLContext sslContext) {
        return HttpClient.newBuilder()
                .version(HTTP_3)
                .connectTimeout(TIMEOUT)
                .proxy(ProxySelector.of(null))
                .sslContext(sslContext)
                .build();
    }

    static HttpRequest http1Get(int port, String path) {
        return HttpRequest.newBuilder(URI.create("https://localhost:" + port + path))
                .version(HTTP_1_1)
                .timeout(TIMEOUT)
                .GET()
                .build();
    }

    static HttpRequest http3Get(int port, String path) {
        return HttpRequest.newBuilder(URI.create("https://localhost:" + port + path))
                .version(HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                .timeout(TIMEOUT)
                .GET()
                .build();
    }

    static TestEnvironment sharedListener(Consumer<HttpRouting.Builder> routing) throws Exception {
        return sharedListener(Http1Config.create(), routing);
    }

    static TestEnvironment sharedListener(Consumer<WebServerConfig.Builder> customizer,
                                          Consumer<HttpRouting.Builder> routing) throws Exception {
        return createServer(builder -> {
            builder.addProtocol(Http1Config.create())
                    .addProtocol(Http3Config.create())
                    .routing(routing);
            customizer.accept(builder);
        });
    }

    static TestEnvironment sharedListener(Http1Config http1Config,
                                          Consumer<HttpRouting.Builder> routing) throws Exception {
        return sharedListener(http1Config, Http3Config.create(), routing);
    }

    static TestEnvironment sharedListener(Http1Config http1Config,
                                          Http3Config http3Config,
                                          Consumer<HttpRouting.Builder> routing) throws Exception {
        return createServer(builder -> builder.addProtocol(http1Config)
                .addProtocol(http3Config)
                .routing(routing));
    }

    static TestEnvironment sharedListener(InetAddress address,
                                          Consumer<HttpRouting.Builder> routing) throws Exception {
        return sharedListener(address, Http1Config.create(), routing);
    }

    static TestEnvironment sharedListener(InetAddress address,
                                          Http1Config http1Config,
                                          Consumer<HttpRouting.Builder> routing) throws Exception {
        return sharedListener(address, http1Config, Http3Config.create(), routing);
    }

    static TestEnvironment sharedListener(InetAddress address,
                                          Http1Config http1Config,
                                          Http3Config http3Config,
                                          Consumer<HttpRouting.Builder> routing) throws Exception {
        return createServer(address, builder -> builder.addProtocol(http1Config)
                .addProtocol(http3Config)
                .routing(routing));
    }

    static TestEnvironment tcpOnlyListener(Consumer<HttpRouting.Builder> routing) throws Exception {
        return createServer(builder -> builder.bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.create())
                .addProtocol(Http1Config.create())
                .routing(routing));
    }

    static TestEnvironment udpOnlyListener(Consumer<HttpRouting.Builder> routing) throws Exception {
        return udpOnlyListener(Http3Config.create(), routing);
    }

    static TestEnvironment udpOnlyListener(Http3Config http3Config,
                                           Consumer<HttpRouting.Builder> routing) throws Exception {
        return createServer(builder -> {
            builder.bindingsDiscoverServices(false)
                    .addBinding(TcpTransportConfig.builder()
                                        .enabled(false)
                                        .build())
                    .addProtocol(http3Config)
                    .routing(routing);
            QuicTransportConfig.create().addTo(builder);
        });
    }

    static boolean ipv6LoopbackAvailable() {
        try {
            InetAddress loopback = InetAddress.getByName("::1");
            try (ServerSocketChannel tcp = ServerSocketChannel.open(StandardProtocolFamily.INET6);
                 DatagramChannel udp = DatagramChannel.open(StandardProtocolFamily.INET6)) {
                tcp.bind(new InetSocketAddress(loopback, 0));
                udp.bind(new InetSocketAddress(loopback, 0));
                return true;
            }
        } catch (IOException _) {
            return false;
        }
    }

    private static Keys serverKeys(String keystoreResource) {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .passphrase("password")
                        .keystore(Resource.create(keystoreResource)))
                .build();
        return keys;
    }

    private static TestEnvironment createServer(Consumer<WebServerConfig.Builder> customizer) throws Exception {
        return createServer(InetAddress.getLoopbackAddress(), customizer);
    }

    private static TestEnvironment createServer(InetAddress address,
                                                Consumer<WebServerConfig.Builder> customizer) throws Exception {
        Tls serverTls = serverTls("server.p12");
        WebServerConfig.Builder builder = WebServer.builder()
                .address(address)
                .port(0)
                .protocolsDiscoverServices(false)
                .tls(serverTls);
        customizer.accept(builder);

        WebServer server = builder.build().start();

        if (!server.isRunning() || server.port() < 0) {
            server.stop();
            throw new IllegalStateException("Failed to start HTTP/3 test server");
        }

        return new TestEnvironment(server,
                                   serverTls,
                                   clientSslContext("client.p12"),
                                   clientTls("client.p12"),
                                   address.getHostAddress());
    }

    private static CompletableFuture<byte[]> readAll(QuicReceiverStream stream) {
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        BufferData output = BufferData.growing(256);
        QuicStreamReader[] holder = new QuicStreamReader[1];
        SequentialScheduler scheduler = SequentialScheduler.lockingScheduler(() -> {
            try {
                QuicStreamReader reader = holder[0];
                for (;;) {
                    Optional<BufferData> next = reader.poll();
                    if (next.isEmpty()) {
                        return;
                    }
                    BufferData buffer = next.orElseThrow();
                    if (buffer == QuicStreamReader.EOF) {
                        result.complete(output.readBytes());
                        return;
                    }
                    output.write(buffer);
                }
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        holder[0] = stream.connectReader(scheduler);
        holder[0].start();
        return result;
    }

    private static DecodedResponseHead decodeResponseHead(Http3QpackContext qpackContext,
                                                          long streamId,
                                                          byte[] headersPayload) {
        Http3QpackContext.Stream qpackStream = qpackContext.openStream(streamId);
        try {
            Headers decodedHeaders = qpackStream.decodeHeaders(BufferData.create(headersPayload), -1);
            int status = -1;
            WritableHeaders<?> headers = WritableHeaders.create();
            for (Header header : decodedHeaders) {
                if (header.headerName().lowerCase().equals(":status")) {
                    status = Integer.parseInt(header.get());
                } else {
                    headers.add(header);
                }
            }
            if (status < 0) {
                throw new IllegalArgumentException("Missing :status pseudo-header");
            }
            return new DecodedResponseHead(status, headers);
        } finally {
            qpackStream.complete();
        }
    }

    private static DecodedResponse decodeResponse(Http3QpackContext qpackContext, long streamId, byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long frameType = VariableLengthEncoder.decode(buffer);
        long frameLength = VariableLengthEncoder.decode(buffer);
        if (frameType != Http3Protocol.FRAME_HEADERS || frameLength < 0 || frameLength > buffer.remaining()) {
            throw new IllegalStateException("Malformed HTTP/3 response message.");
        }
        byte[] headersPayload = new byte[(int) frameLength];
        buffer.get(headersPayload);
        DecodedResponseHead responseHead = decodeResponseHead(qpackContext, streamId, headersPayload);
        BufferData body = BufferData.growing(256);
        while (buffer.hasRemaining()) {
            long nextType = VariableLengthEncoder.decode(buffer);
            long nextLength = VariableLengthEncoder.decode(buffer);
            if (nextLength < 0 || nextLength > buffer.remaining()) {
                throw new IllegalStateException("Malformed HTTP/3 response frame.");
            }
            byte[] payload = new byte[(int) nextLength];
            buffer.get(payload);
            if (nextType == Http3Protocol.FRAME_DATA) {
                body.write(payload);
            }
        }
        return new DecodedResponse(responseHead.status(), responseHead.headers(), headersPayload.length, body.readBytes());
    }

    private static byte[] encodeRequestHeaders(Http3QpackContext qpackContext,
                                               long streamId,
                                               URI uri,
                                               String method,
                                               Headers headers) {
        return Http3Protocol.encodeRequestHeaders(qpackContext, streamId, uri, method, headers);
    }

    private static KeyStore loadStore(String resourceName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Http3TestSupport.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource: " + resourceName);
            }
            keyStore.load(stream, PASSWORD);
        }
        return keyStore;
    }

    static final class LowLevelHttp3Client implements AutoCloseable {
        private static final long LOCAL_QPACK_MAX_TABLE_CAPACITY = 4096;
        private static final int LOCAL_QPACK_BLOCKED_STREAMS = 16;

        private final URI baseUri;
        private final ExecutorService executor;
        private final QuicClientRuntime client;
        private final QuicClientConnection connection;
        private final Http3QpackContext qpackContext;

        private LowLevelHttp3Client(URI baseUri,
                                    ExecutorService executor,
                                    QuicClientRuntime client,
                                    QuicClientConnection connection,
                                    Http3QpackContext qpackContext) {
            this.baseUri = baseUri;
            this.executor = executor;
            this.client = client;
            this.connection = connection;
            this.qpackContext = qpackContext;
        }

        static LowLevelHttp3Client create(TestEnvironment environment) throws Exception {
            return create(environment, Optional.empty());
        }

        static LowLevelHttp3Client create(TestEnvironment environment,
                                          List<SNIServerName> serverNames) throws Exception {
            return create(environment, Optional.of(List.copyOf(serverNames)));
        }

        DecodedResponse get(String path) throws Exception {
            return get(baseUri.resolve(path));
        }

        DecodedResponse get(URI uri) throws Exception {
            return request(uri, "GET", WritableHeaders.create());
        }

        DecodedResponse request(URI uri, String method, Headers headers) throws Exception {
            return request(connection, qpackContext, uri, method, headers);
        }

        @Override
        public void close() throws Exception {
            try {
                client.close();
            } finally {
                executor.close();
            }
        }

        private static LowLevelHttp3Client create(TestEnvironment environment,
                                                  Optional<List<SNIServerName>> serverNames) throws Exception {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            QuicClientRuntime client = QuicClientRuntime.builder()
                    .executor(executor)
                    .quicConfig(QuicConfig.builder()
                                        .availableVersions(List.of(QuicVersion.QUIC_V1))
                                        .buildPrototype())
                    .tls(environment.clientTls())
                    .build();
            Http3QpackContext qpackContext = Http3QpackContext.create(LOCAL_QPACK_MAX_TABLE_CAPACITY,
                                                                      LOCAL_QPACK_BLOCKED_STREAMS,
                                                                      16_384,
                                                                      _ -> {
                                                                      });

            InetSocketAddress peer = new InetSocketAddress(InetAddress.getByName(environment.host), environment.port());
            QuicClientConnection connection = serverNames.isEmpty()
                    ? client.createConnection(peer,
                                              peer.getHostString(),
                                              peer.getPort(),
                                              new String[] {Http3Client.PROTOCOL_ID})
                    : client.createConnection(peer,
                                              environment.host,
                                              environment.port(),
                                              new String[] {Http3Client.PROTOCOL_ID},
                                              serverNames.orElseThrow());
            Http3PeerCriticalStreams peerCriticalStreams = Http3PeerCriticalStreams.create();
            connection.addRemoteStreamListener(stream -> {
                if (stream instanceof QuicReceiverStream receiver && !(stream instanceof QuicBidiStream)) {
                    Http3ControlStreamSupport.observe(receiver,
                                                      qpackContext,
                                                      peerCriticalStreams,
                                                      connection,
                                                      controlStreamListener(qpackContext))
                            .completion().exceptionally(_ -> null);
                    return true;
                }
                return false;
            });
            connection.startHandshake().get(20, TimeUnit.SECONDS);
            primeControlStreams(connection, qpackContext);
            return new LowLevelHttp3Client(environment.uri("/"), executor, client, connection, qpackContext);
        }

        private static DecodedResponse request(QuicConnection connection,
                                               Http3QpackContext qpackContext,
                                               URI uri,
                                               String method,
                                               Headers headers) throws Exception {
            RequestStream requestStream = openRequestStream(connection);
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());
            requestStream.writer()
                    .scheduleForWriting(BufferData.create(encodeRequestHeaders(qpackContext,
                                                                              requestStream.stream().streamId(),
                                                                              uri,
                                                                              method,
                                                                              headers)),
                                        true);
            return decodeResponse(qpackContext, requestStream.stream().streamId(), responseFuture.get(10, TimeUnit.SECONDS));
        }

        private static void primeControlStreams(QuicConnection connection,
                                                Http3QpackContext qpackContext) throws Exception {
            openAndPrimeUniStream(connection,
                                  Http3Protocol.controlStreamPreamble(
                                          Http3Settings.create(LOCAL_QPACK_MAX_TABLE_CAPACITY,
                                                               LOCAL_QPACK_BLOCKED_STREAMS)),
                                  Http3StreamType.CONTROL);
            QuicStreamWriter encoderWriter = openAndPrimeUniStream(connection,
                                                                   Http3Protocol.qpackUniStreamPreamble(
                                                                           Http3StreamType.QPACK_ENCODER),
                                                                   Http3StreamType.QPACK_ENCODER);
            qpackContext.encoderInstructionsSender(bytes -> encoderWriter.scheduleForWriting(BufferData.create(bytes), false));
            QuicStreamWriter decoderWriter = openAndPrimeUniStream(connection,
                                                                   Http3Protocol.qpackUniStreamPreamble(
                                                                           Http3StreamType.QPACK_DECODER),
                                                                   Http3StreamType.QPACK_DECODER);
            qpackContext.decoderInstructionsSender(bytes -> decoderWriter.scheduleForWriting(BufferData.create(bytes), false));
        }

        private static Http3ControlStreamListener controlStreamListener(Http3QpackContext qpackContext) {
            return new Http3ControlStreamListener() {
                @Override
                public void onSettings(Http3Settings settings) {
                    qpackContext.peerSettings(settings.qpackMaxTableCapacity(), settings.qpackBlockedStreams());
                }

                @Override
                public void onGoAway(Http3GoAway goAway) {
                }
            };
        }

        private static QuicStreamWriter openAndPrimeUniStream(QuicConnection connection,
                                                              byte[] payload,
                                                              Http3StreamType streamType) throws Exception {
            QuicSenderStream stream = connection.openNewLocalUniStream(TIMEOUT)
                    .get(10, TimeUnit.SECONDS);
            QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream, connection, streamType);
            writer.scheduleForWriting(BufferData.create(payload), false);
            return writer;
        }

        private static RequestStream openRequestStream(QuicConnection connection) throws Exception {
            QuicBidiStream stream = connection.openNewLocalBidiStream(TIMEOUT)
                    .get(10, TimeUnit.SECONDS);
            return new RequestStream(stream, Http3StreamSupport.connectWriter(stream, connection));
        }

        private QuicClientConnection createConnection(String host,
                                                      int port,
                                                      Http3QpackContext qpackContext) throws Exception {
            InetSocketAddress peerAddress = new InetSocketAddress(InetAddress.getByName(host), port);
            QuicClientConnection newConnection = client.createConnection(peerAddress,
                                                                         peerAddress.getHostString(),
                                                                         peerAddress.getPort(),
                                                                         new String[] {Http3Client.PROTOCOL_ID});
            Http3PeerCriticalStreams peerCriticalStreams = Http3PeerCriticalStreams.create();
            newConnection.addRemoteStreamListener(stream -> {
                if (stream instanceof QuicReceiverStream receiver && !(stream instanceof QuicBidiStream)) {
                    Http3ControlStreamSupport.observe(receiver,
                                                      qpackContext,
                                                      peerCriticalStreams,
                                                      newConnection,
                                                      controlStreamListener(qpackContext))
                            .completion().exceptionally(_ -> null);
                    return true;
                }
                return false;
            });
            return newConnection;
        }

        private record RequestStream(QuicBidiStream stream, QuicStreamWriter writer) {
        }

    }

    static record DecodedResponse(int status, Headers headers, int headersPayloadLength, byte[] body) {
        DecodedResponse {
            headers = WritableHeaders.create(headers);
        }
    }

    private record DecodedResponseHead(int status, Headers headers) {
        private DecodedResponseHead {
            headers = WritableHeaders.create(headers);
        }
    }

    static final class TestEnvironment implements AutoCloseable {
        private final WebServer server;
        private final Tls serverTls;
        private final SSLContext clientSslContext;
        private final Tls clientTls;
        private final String host;

        private TestEnvironment(WebServer server, Tls serverTls, SSLContext clientSslContext, Tls clientTls, String host) {
            this.server = server;
            this.serverTls = serverTls;
            this.clientSslContext = clientSslContext;
            this.clientTls = clientTls;
            this.host = host;
        }

        WebServer server() {
            return server;
        }

        int port() {
            return server.port();
        }

        SSLContext clientSslContext() {
            return clientSslContext;
        }

        Tls serverTls() {
            return serverTls;
        }

        String baseUri() {
            return uri("/").toString();
        }

        URI uri(String path) {
            return URI.create("https://" + hostForUri() + ":" + server.port() + path);
        }

        HttpRequest http1Get(String path) {
            return HttpRequest.newBuilder(uri(path))
                    .version(HTTP_1_1)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
        }

        HttpRequest http3Get(String path) {
            return HttpRequest.newBuilder(uri(path))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
        }

        Tls clientTls() {
            return clientTls;
        }

        LowLevelHttp3Client lowLevelHttp3Client() throws Exception {
            return LowLevelHttp3Client.create(this);
        }

        HttpClient http1Client() {
            return Http3TestSupport.http1Client(clientSslContext);
        }

        HttpClient http3Client() {
            return Http3TestSupport.http3Client(clientSslContext);
        }

        @Override
        public void close() {
            server.stop();
        }

        private String hostForUri() {
            return host.contains(":") ? "[" + host + "]" : host;
        }
    }
}
