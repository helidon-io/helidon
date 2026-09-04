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

package io.helidon.webserver.http3;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
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
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientResponse;

import org.junit.jupiter.api.Test;

import static io.helidon.webserver.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webserver.http3.RawHttp3TestServer.requestBodyInputStream;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;

class Http3ServerIT {
    private static final char[] KEY_PASSWORD = "changeit".toCharArray();
    private static final String SERVER_KEYSTORE = "io/helidon/webserver/http3/server-keystore.p12";
    private static final String CLIENT_TRUSTSTORE = "io/helidon/webserver/http3/client-truststore.p12";
    private static final Header TEST_TRAILER_HEADER = HeaderValues.create("test-trailer", "trailer-value");

    @Test
    void shouldServeHelidonHttp3Client() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create()) {
            URI uri = environment.uri("/helidon");
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse response = client.get(uri.toString()).request()) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), equalTo("GET /helidon"));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldServeJdkHttp3Client() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpRequest request = HttpRequest.newBuilder(environment.uri("/jdk"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.version(), equalTo(HTTP_3));
            assertThat(response.body(), equalTo("GET /jdk"));
        }
    }

    @Test
    void shouldBufferDirectHandlerBodyOnDemand() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(stream -> {
                 byte[] body = requestBody(requestBodyInputStream(stream));
                 return Optional.of(Http3Handler.BufferedResponse.text(200,
                                                                       new String(body, StandardCharsets.UTF_8)));
             });
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpRequest request = HttpRequest.newBuilder(environment.uri("/buffered"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .POST(HttpRequest.BodyPublishers.ofString("pingtail"))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.version(), equalTo(HTTP_3));
            assertThat(response.body(), equalTo("pingtail"));
        }
    }

    @Test
    void shouldUseStreamOwnerForBufferedResponse() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(stream -> {
                 return Optional.of(Http3Handler.BufferedResponse.text(200, "owned-writer"));
             });
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpRequest request = HttpRequest.newBuilder(environment.uri("/writer"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.body(), equalTo("owned-writer"));
        }
    }

    @Test
    void shouldRespondBeforeDirectHandlerRequestEof() throws Exception {
        CountDownLatch handlerRead = new CountDownLatch(1);
        try (TestEnvironment environment = TestEnvironment.create(stream -> {
                 try (InputStream inputStream = requestBodyInputStream(stream)) {
                     byte[] prefix = inputStream.readNBytes(4);
                     handlerRead.countDown();
                     return Optional.of(Http3Handler.BufferedResponse.text(200,
                                                                           new String(prefix, StandardCharsets.UTF_8)));
                 } catch (IOException e) {
                    throw new UncheckedIOException(e);
                 }
             });
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());
            client.sendHeaders(requestStream, environment.uri("/stream-request"), 8);
            client.sendData(requestStream, "ping".getBytes(StandardCharsets.UTF_8), false);

            assertThat(handlerRead.await(5, TimeUnit.SECONDS), equalTo(true));
            DecodedResponse response = client.decodeResponse(requestStream, responseFuture.get(10, TimeUnit.SECONDS));
            assertThat(response.status(), equalTo(200));
            assertThat(new String(response.body(), StandardCharsets.UTF_8), equalTo("ping"));
            assertThat(requestStream.stream().futureSendingCompletion().get(10, TimeUnit.SECONDS).isReset(), equalTo(true));
            assertThat(requestStream.stream().stopSendingReceived(), equalTo(true));
        }
    }

    @Test
    void shouldServeStreamedDirectHandler() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(stream -> {
                 stream.writeResponseHeaders(200,
                                             headers(HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                                         "text/plain; charset=utf-8")),
                                             false);
                 byte[] method = "GET ".getBytes(StandardCharsets.UTF_8);
                 stream.writeData(method, 0, method.length, false);
                 byte[] path = stream.request().path().orElseThrow().getBytes(StandardCharsets.UTF_8);
                 stream.writeData(path, 0, path.length, true);
                 return Optional.empty();
             });
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpRequest request = HttpRequest.newBuilder(environment.uri("/streamed"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.version(), equalTo(HTTP_3));
            assertThat(response.body(), equalTo("GET /streamed"));
        }
    }

    @Test
    void shouldServeDirectHandlerTrailers() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(stream -> {
                 stream.writeResponseHeaders(200,
                                             headers(HeaderValues.create(HeaderNames.TRAILER,
                                                                         TEST_TRAILER_HEADER.name())),
                                             false);
                 byte[] body = "GET /trailers".getBytes(StandardCharsets.UTF_8);
                 stream.writeData(body, 0, body.length, false);
                 stream.writeTrailers(headers(TEST_TRAILER_HEADER), true);
                 return Optional.empty();
             })) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse response = client.get(environment.uri("/trailers").toString()).request()) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), equalTo("GET /trailers"));
                assertThat(response.headers().values(HeaderNames.TRAILER), containsInAnyOrder("test-trailer"));
                assertThat(response.trailers().contains(TEST_TRAILER_HEADER), equalTo(true));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldReuseDynamicQpackAcrossRepeatedDirectResponsesOnSameConnection() throws Exception {
        HeaderName qpackUser = HeaderNames.create("x-qpack-user");
        HeaderName qpackEnv = HeaderNames.create("x-qpack-env");
        HeaderName qpackCluster = HeaderNames.create("x-qpack-cluster");
        String userValue = "alpha-user-1234567890";
        String envValue = "dev-eu-central-1";
        String clusterValue = "shared-http3-connection";
        Headers repeatedResponseHeaders = headers(HeaderValues.create(qpackUser, userValue),
                                                  HeaderValues.create(qpackEnv, envValue),
                                                  HeaderValues.create(qpackCluster, clusterValue));

        try (TestEnvironment environment = TestEnvironment.create(stream -> {
                 if ("/dynamic-qpack".equals(stream.request().path().orElseThrow())) {
                     return Optional.of(Http3Handler.BufferedResponse.create(200,
                                                                             repeatedResponseHeaders,
                                                                             "dynamic".getBytes(StandardCharsets.UTF_8)));
                 }
                 return Optional.of(Http3Handler.BufferedResponse.text(200,
                                                                       stream.request().method() + " "
                                                                               + stream.request().path().orElseThrow()));
             });
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            DecodedResponse warmupResponse = client.get(environment.uri("/hello"));
            DecodedResponse firstResponse = client.get(environment.uri("/dynamic-qpack"));
            DecodedResponse secondResponse = client.get(environment.uri("/dynamic-qpack"));
            DecodedResponse thirdResponse = client.get(environment.uri("/dynamic-qpack"));

            assertThat(warmupResponse.status(), equalTo(200));
            assertThat(new String(warmupResponse.body(), StandardCharsets.UTF_8), equalTo("GET /hello"));

            assertThat(firstResponse.status(), equalTo(200));
            assertThat(firstResponse.headers().first(qpackUser).orElseThrow(), equalTo(userValue));
            assertThat(firstResponse.headers().first(qpackEnv).orElseThrow(), equalTo(envValue));
            assertThat(firstResponse.headers().first(qpackCluster).orElseThrow(), equalTo(clusterValue));
            assertThat(new String(firstResponse.body(), StandardCharsets.UTF_8), equalTo("dynamic"));

            assertThat(secondResponse.status(), equalTo(200));
            assertThat(secondResponse.headers().first(qpackUser).orElseThrow(), equalTo(userValue));
            assertThat(new String(secondResponse.body(), StandardCharsets.UTF_8), equalTo("dynamic"));

            assertThat(thirdResponse.status(), equalTo(200));
            assertThat(thirdResponse.headers().first(qpackCluster).orElseThrow(), equalTo(clusterValue));
            assertThat(new String(thirdResponse.body(), StandardCharsets.UTF_8), equalTo("dynamic"));

            assertThat(Math.min(secondResponse.headersPayloadLength(), thirdResponse.headersPayloadLength()),
                       lessThan(firstResponse.headersPayloadLength()));
        }
    }

    @Test
    void shouldRetireClientConnectionAfterServerGoAway() throws Exception {
        AtomicReference<RawHttp3TestServer> serverRef = new AtomicReference<>();
        AtomicBoolean goAwaySent = new AtomicBoolean();

        try (TestEnvironment environment = TestEnvironment.create((connection, stream) -> {
                 if (goAwaySent.compareAndSet(false, true)) {
                     serverRef.get().sendGoAway(connection, Http3GoAway.requestStream(stream.streamId() + 4));
                 }
                 return Optional.of(Http3Handler.BufferedResponse.text(200,
                                                                       connection.childSocketId()));
             }, serverRef)) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse first = client.get(environment.uri("/goaway").toString()).request();
                 Http3ClientResponse second = client.get(environment.uri("/goaway").toString()).request()) {
                assertThat(first.status().code(), equalTo(200));
                assertThat(first.protocolId(), equalTo(Http3Client.PROTOCOL_ID));

                assertThat(second.status().code(), equalTo(200));
                assertThat(second.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(second.as(String.class), not(equalTo(first.as(String.class))));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldSendFinalGoAwayAndRejectNewStreamsDuringGracefulShutdown() throws Exception {
        AtomicReference<RawHttp3TestServer> serverRef = new AtomicReference<>();
        CountDownLatch firstRequestStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        CountDownLatch shutdownCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();
        List<Long> goAwayFrames = new CopyOnWriteArrayList<>();

        Thread shutdownThread = null;
        try (TestEnvironment environment = TestEnvironment.create(stream -> {
                 if ("/slow".equals(stream.request().path().orElseThrow())) {
                     firstRequestStarted.countDown();
                     try {
                         if (!releaseFirstRequest.await(10, TimeUnit.SECONDS)) {
                             throw new IllegalStateException("Timed out waiting to release graceful shutdown request.");
                         }
                     } catch (InterruptedException e) {
                         Thread.currentThread().interrupt();
                         throw new IllegalStateException("Interrupted while waiting to release graceful shutdown request.", e);
                     }
                     return Optional.of(Http3Handler.BufferedResponse.text(200, "slow"));
                 }
                 return Optional.of(Http3Handler.BufferedResponse.text(200, stream.request().path().orElseThrow()));
             }, serverRef);
             LowLevelHttp3Client client = LowLevelHttp3Client.create(
                     environment,
                     goAway -> goAwayFrames.add(goAway.identifier()))) {
            LowLevelHttp3Client.RequestStream firstRequest = client.openRequestStream();
            CompletableFuture<byte[]> firstResponseFuture = readAll(firstRequest.stream());
            client.sendHeaders(firstRequest, environment.uri("/slow"), 0);

            assertThat(firstRequestStarted.await(10, TimeUnit.SECONDS), equalTo(true));

            shutdownThread = Thread.ofVirtual().start(() -> {
                try {
                    serverRef.get().closeGracefully(Duration.ofSeconds(2));
                } catch (Throwable t) {
                    shutdownFailure.set(t);
                } finally {
                    shutdownCompleted.countDown();
                }
            });

            assertThat(waitFor(() -> goAwayFrames.contains(Http3Protocol.MAX_CLIENT_BIDIRECTIONAL_STREAM_ID),
                               Duration.ofSeconds(10)),
                       equalTo(true));

            LowLevelHttp3Client.RequestStream rejectedRequest = client.openRequestStream();
            client.sendHeaders(rejectedRequest, environment.uri("/rejected"), 0);

            assertThat(waitFor(() -> rejectedRequest.stream().rcvErrorCode() == Http3ErrorCode.REQUEST_REJECTED.code(),
                               Duration.ofSeconds(10)),
                       equalTo(true));
            assertThat(shutdownCompleted.await(250, TimeUnit.MILLISECONDS), equalTo(false));

            releaseFirstRequest.countDown();

            DecodedResponse firstResponse = client.decodeResponse(firstRequest, firstResponseFuture.get(10, TimeUnit.SECONDS));
            assertThat(firstResponse.status(), equalTo(200));
            assertThat(new String(firstResponse.body(), StandardCharsets.UTF_8), equalTo("slow"));

            assertThat(shutdownCompleted.await(10, TimeUnit.SECONDS), equalTo(true));
            if (shutdownFailure.get() != null) {
                throw new AssertionError("closeGracefully() failed", shutdownFailure.get());
            }

            assertThat(waitFor(() -> goAwayFrames.size() >= 2, Duration.ofSeconds(10)), equalTo(true));
            assertThat(goAwayFrames,
                       equalTo(List.of(Http3Protocol.MAX_CLIENT_BIDIRECTIONAL_STREAM_ID, 4L)));
        } finally {
            releaseFirstRequest.countDown();
            if (shutdownThread != null) {
                shutdownThread.join(TimeUnit.SECONDS.toMillis(10));
            }
        }
    }

    @Test
    void shouldCancelPartialRequestHeadersAndContinueOtherStreams() throws Exception {
        Duration requestReadTimeout = Duration.ofMillis(200);
        try (TestEnvironment environment = TestEnvironment.create(
                     requestReadTimeout,
                     stream -> Optional.of(Http3Handler.BufferedResponse.text(
                             200,
                             stream.request().path().orElseThrow())));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream stalled = client.openRequestStream();
            stalled.writer().scheduleForWriting(
                    BufferData.create(new byte[] {(byte) Http3Protocol.FRAME_HEADERS}),
                    false);

            assertThat(waitFor(() -> stalled.stream().rcvErrorCode() == Http3ErrorCode.REQUEST_CANCELLED.code(),
                               Duration.ofSeconds(10)),
                       equalTo(true));
            assertThat(waitFor(stalled.stream()::stopSendingReceived, Duration.ofSeconds(10)), equalTo(true));

            DecodedResponse response = client.get(environment.uri("/after-partial-headers"));
            assertThat(response.status(), equalTo(200));
            assertThat(new String(response.body(), StandardCharsets.UTF_8), equalTo("/after-partial-headers"));
        }
    }

    @Test
    void shouldCancelStalledRequestBodyAndContinueOtherStreams() throws Exception {
        Duration requestReadTimeout = Duration.ofMillis(200);
        try (TestEnvironment environment = TestEnvironment.create(requestReadTimeout, stream -> {
                 if ("/stalled-body".equals(stream.request().path().orElseThrow())) {
                     try (InputStream inputStream = requestBodyInputStream(stream)) {
                         inputStream.read();
                         return Optional.of(Http3Handler.BufferedResponse.text(200, "unexpected"));
                     } catch (IOException e) {
                         throw new UncheckedIOException(e);
                     }
                 }
                 return Optional.of(Http3Handler.BufferedResponse.text(200,
                                                                        stream.request().path().orElseThrow()));
             });
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream stalled = client.openRequestStream();
            client.sendHeaders(stalled, environment.uri("/stalled-body"), 1);

            assertThat(waitFor(() -> stalled.stream().rcvErrorCode() == Http3ErrorCode.REQUEST_CANCELLED.code(),
                               Duration.ofSeconds(10)),
                       equalTo(true));
            assertThat(waitFor(stalled.stream()::stopSendingReceived, Duration.ofSeconds(10)), equalTo(true));

            DecodedResponse response = client.get(environment.uri("/after-stalled-body"));
            assertThat(response.status(), equalTo(200));
            assertThat(new String(response.body(), StandardCharsets.UTF_8), equalTo("/after-stalled-body"));
        }
    }

    private static boolean waitFor(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private static ProxySelector noProxySelector() {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            }
        };
    }

    private static final class TestEnvironment implements AutoCloseable {
        private final ExecutorService executor;
        private final SSLContext clientSslContext;
        private final RawHttp3TestServer server;

        private TestEnvironment(ExecutorService executor,
                                SSLContext clientSslContext,
                                RawHttp3TestServer server) {
            this.executor = executor;
            this.clientSslContext = clientSslContext;
            this.server = server;
        }

        private static TestEnvironment create() throws Exception {
            return create(stream ->
                                  Optional.of(Http3Handler.BufferedResponse.text(200,
                                                                                 stream.request().method() + " "
                                                                                         + stream.request().path().orElseThrow())));
        }

        private static TestEnvironment create(Http3Handler handler) throws Exception {
            return create(handler, null);
        }

        private static TestEnvironment create(Duration requestReadTimeout, Http3Handler handler) throws Exception {
            return create((_, stream) -> handler.handle(stream), null, requestReadTimeout);
        }

        private static TestEnvironment create(Http3Handler handler,
                                              AtomicReference<RawHttp3TestServer> serverRef) throws Exception {
            return create((_, stream) -> handler.handle(stream), serverRef);
        }

        private static TestEnvironment create(RawHttp3TestServer.ConnectionHandler handler,
                                              AtomicReference<RawHttp3TestServer> serverRef) throws Exception {
            return create(handler, serverRef, Duration.ZERO);
        }

        private static TestEnvironment create(RawHttp3TestServer.ConnectionHandler handler,
                                              AtomicReference<RawHttp3TestServer> serverRef,
                                              Duration requestReadTimeout) throws Exception {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            RawHttp3TestServer server = RawHttp3TestServer.create(executor,
                                                                  new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                                                  serverTls(),
                                                                  List.of(QuicVersion.QUIC_V1),
                                                                  requestReadTimeout,
                                                                  handler);
            if (serverRef != null) {
                serverRef.set(server);
            }
            return new TestEnvironment(executor, Http3ServerIT.clientSslContext(), server);
        }

        private ExecutorService executor() {
            return executor;
        }

        private SSLContext clientSslContext() {
            return clientSslContext;
        }

        private Tls clientTls() {
            return Http3ServerIT.clientTls();
        }

        private URI uri(String path) throws Exception {
            return new URI("https", null, "localhost", server.localAddress().getPort(), path, null, null);
        }

        @Override
        public void close() throws Exception {
            try {
                server.close();
            } finally {
                executor.close();
            }
        }
    }

    private static final class LowLevelHttp3Client implements AutoCloseable {
        private static final Duration STREAM_OPEN_TIMEOUT = Duration.ofSeconds(5);
        private static final long LOCAL_QPACK_MAX_TABLE_CAPACITY = 4096;
        private static final int LOCAL_QPACK_BLOCKED_STREAMS = 16;

        private final ExecutorService executor;
        private final QuicClientRuntime client;
        private final QuicClientConnection connection;
        private final Http3QpackContext qpackContext;

        private LowLevelHttp3Client(ExecutorService executor,
                                    QuicClientRuntime client,
                                    QuicClientConnection connection,
                                    Http3QpackContext qpackContext) {
            this.executor = executor;
            this.client = client;
            this.connection = connection;
            this.qpackContext = qpackContext;
        }

        private static LowLevelHttp3Client create(TestEnvironment environment) throws Exception {
            return create(environment, _ -> {
            });
        }

        private static LowLevelHttp3Client create(TestEnvironment environment,
                                                  Consumer<Http3GoAway> goAwayConsumer) throws Exception {
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

            InetSocketAddress peerAddress =
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), environment.server.localAddress().getPort());
            QuicClientConnection connection = client.createConnection(peerAddress,
                                                                      peerAddress.getHostString(),
                                                                      peerAddress.getPort(),
                                                                      new String[] {Http3Client.PROTOCOL_ID});
            Http3PeerCriticalStreams peerCriticalStreams = Http3PeerCriticalStreams.create();
            connection.addRemoteStreamListener(stream -> {
                if (stream instanceof QuicReceiverStream receiver && !(stream instanceof QuicBidiStream)) {
                    Http3ControlStreamSupport.observe(receiver,
                                                      qpackContext,
                                                      peerCriticalStreams,
                                                      connection,
                                                      new Http3ControlStreamListener() {
                                                          @Override
                                                          public void onSettings(Http3Settings settings) {
                                                              qpackContext.peerSettings(
                                                                      settings.qpackMaxTableCapacity(),
                                                                      settings.qpackBlockedStreams());
                                                          }

                                                          @Override
                                                          public void onGoAway(Http3GoAway goAway) {
                                                              goAwayConsumer.accept(goAway);
                                                          }
                                                      })
                            .completion().exceptionally(throwable -> null);
                    return true;
                }
                return false;
            });
            connection.startHandshake().get(20, TimeUnit.SECONDS);
            primeControlStreams(connection, qpackContext);
            return new LowLevelHttp3Client(executor, client, connection, qpackContext);
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

        private static QuicStreamWriter openAndPrimeUniStream(QuicConnection connection,
                                                              byte[] payload,
                                                              Http3StreamType streamType) throws Exception {
            QuicSenderStream stream = connection.openNewLocalUniStream(STREAM_OPEN_TIMEOUT)
                    .get(10, TimeUnit.SECONDS);
            QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream, connection, streamType);
            writer.scheduleForWriting(BufferData.create(payload), false);
            return writer;
        }

        private RequestStream openRequestStream() throws Exception {
            QuicBidiStream stream = connection.openNewLocalBidiStream(STREAM_OPEN_TIMEOUT)
                    .get(10, TimeUnit.SECONDS);
            return new RequestStream(stream, Http3StreamSupport.connectWriter(stream, connection));
        }

        private DecodedResponse get(URI uri) throws Exception {
            return request(uri, "GET", headers());
        }

        private DecodedResponse request(URI uri, String method, Headers headers) throws Exception {
            RequestStream requestStream = openRequestStream();
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());
            requestStream.writer()
                    .scheduleForWriting(BufferData.create(encodeRequestHeaders(requestStream, uri, method, headers)), true);
            return decodeResponse(requestStream, responseFuture.get(10, TimeUnit.SECONDS));
        }

        private byte[] encodeRequestHeaders(RequestStream requestStream,
                                            URI uri,
                                            String method,
                                            Headers headers) {
            return Http3ServerIT.encodeRequestHeaders(qpackContext, requestStream.stream().streamId(), uri, method, headers);
        }

        private DecodedResponse decodeResponse(RequestStream requestStream, byte[] bytes) {
            return Http3ServerIT.decodeResponse(qpackContext, requestStream.stream().streamId(), bytes);
        }

        private void sendHeaders(RequestStream requestStream, URI uri, int contentLength) {
            requestStream.writer()
                    .scheduleForWriting(BufferData.create(encodeRequestHeaders(requestStream,
                                                                            uri,
                                                                            "POST",
                                                                            headers(HeaderValues.create(
                                                                                            HeaderNames.CONTENT_LENGTH,
                                                                                            contentLength),
                                                                                    HeaderValues.create(
                                                                                            HeaderNames.CONTENT_TYPE,
                                                                                            "text/plain; charset=utf-8")))),
                                        false);
        }

        private void sendData(RequestStream requestStream, byte[] data, boolean last) {
            requestStream.writer().scheduleForWriting(BufferData.create(Http3Protocol.encodeDataFrame(data)), last);
        }

        @Override
        public void close() throws Exception {
            try {
                client.close();
            } finally {
                executor.close();
            }
        }

        private record RequestStream(QuicBidiStream stream, QuicStreamWriter writer) {
        }
    }

    private static Tls serverTls() throws Exception {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .passphrase(new String(KEY_PASSWORD))
                        .keyAlias("server")
                        .certChainAlias("server")
                        .keystore(Resource.create(SERVER_KEYSTORE)))
                .build();

        return Tls.builder()
                .privateKey(keys.privateKey().orElseThrow())
                .privateKeyCertChain(keys.certChain())
                .build();
    }

    private static Tls clientTls() {
        return Tls.builder()
                .trust(trust -> trust.keystore(store -> store
                        .passphrase(new String(KEY_PASSWORD))
                        .trustStore(true)
                        .keystore(Resource.create(CLIENT_TRUSTSTORE))))
                .applicationProtocols(List.of(Http3Client.PROTOCOL_ID))
                .enabledProtocols(List.of("TLSv1.3"))
                .build();
    }

    private static SSLContext clientSslContext() throws Exception {
        KeyStore trustStore = loadStore(CLIENT_TRUSTSTORE);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
        return clientContext;
    }

    private static KeyStore loadStore(String resourceName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Http3ServerIT.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource: " + resourceName);
            }
            keyStore.load(stream, KEY_PASSWORD);
        }
        return keyStore;
    }

    private static Headers headers(Header... headers) {
        WritableHeaders<?> writable = WritableHeaders.create();
        for (Header header : headers) {
            writable.add(header);
        }
        return writable;
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

    private static DecodedResponse decodeResponse(Http3QpackContext qpackContext,
                                                  long streamId,
                                                  byte[] bytes) {
        BufferData buffer = BufferData.create(bytes);
        long frameType = VariableLengthEncoder.decode(buffer);
        long frameLength = VariableLengthEncoder.decode(buffer);
        if (frameType != Http3Protocol.FRAME_HEADERS || frameLength < 0 || frameLength > buffer.available()) {
            throw new IllegalStateException("Malformed HTTP/3 response message.");
        }
        byte[] headersPayload = new byte[(int) frameLength];
        buffer.read(headersPayload);
        DecodedResponseHead responseHead = decodeResponseHead(qpackContext, streamId, headersPayload);
        BufferData body = BufferData.growing(256);
        while (buffer.available() > 0) {
            long nextType = VariableLengthEncoder.decode(buffer);
            long nextLength = VariableLengthEncoder.decode(buffer);
            if (nextLength < 0 || nextLength > buffer.available()) {
                throw new IllegalStateException("Malformed HTTP/3 response frame.");
            }
            byte[] payload = new byte[(int) nextLength];
            buffer.read(payload);
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

    private static byte[] requestBody(InputStream inputStream) {
        try (inputStream) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record DecodedResponse(int status, Headers headers, int headersPayloadLength, byte[] body) {
        private DecodedResponse {
            headers = WritableHeaders.create(headers);
        }
    }

    private record DecodedResponseHead(int status, Headers headers) {
        private DecodedResponseHead {
            headers = WritableHeaders.create(headers);
        }
    }
}
