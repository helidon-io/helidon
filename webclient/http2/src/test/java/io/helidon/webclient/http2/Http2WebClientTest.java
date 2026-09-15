/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.helidon.common.LazyValue;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Direction;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.Initiator;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2LoggingFrameListener;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.ResolvedClientTarget;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientTransportObserverProvider;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Route;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.http.HeaderNames.USER_AGENT;
import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.HEAD;
import static io.helidon.http.Method.POST;
import static io.helidon.http.Method.PUT;
import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.LOCAL_CLOSE;
import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Handshake.NONE;
import static io.helidon.http.HttpTransportObserver.Initiator.LOCAL;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_2;
import static io.helidon.http.HttpTransportObserver.Role.CLIENT;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.RESET;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ServerTest
class Http2WebClientTest {

    private static final HeaderName CLIENT_CUSTOM_HEADER_NAME = HeaderNames.create("client-custom-header");
    private static final HeaderName SERVER_CUSTOM_HEADER_NAME = HeaderNames.create("server-custom-header");
    private static final HeaderName SERVER_HEADER_FROM_PARAM_NAME = HeaderNames.create("header-from-param");
    private static final HeaderName CLIENT_USER_AGENT_HEADER_NAME = HeaderNames.create("client-user-agent");
    private static final HeaderName REDIRECT_ATTEMPT_HEADER = HeaderNames.create("redirect-attempt");
    private static final String CLIENT_SEND_LOGGER_NAME = Http2LoggingFrameListener.class.getName() + ".cl-send";
    private static final String FINAL_TLS_SOCKET = "final-https";
    private static ExecutorService executorService;
    private static int plainPort;
    private static int tlsPort;
    private static int finalTlsPort;
    private static final AtomicReference<CompletableFuture<Void>> RESET_RESPONSE_RELEASE =
            new AtomicReference<>(CompletableFuture.completedFuture(null));
    private static Supplier<Http2Client> localCacheClient = () -> Http2Client.builder()
            .shareConnectionCache(false)
            .connectTimeout(Duration.ofMinutes(10))
            .baseUri("http://localhost:" + plainPort + "/versionspecific")
            .build();
    private static final Supplier<Http2Client> globalCacheClient = () -> Http2Client.builder()
            .shareConnectionCache(true)
            .connectTimeout(Duration.ofMinutes(10))
            .baseUri("http://localhost:" + plainPort + "/versionspecific")
            .build();
    private static final Supplier<Http2Client> priorKnowledgeClient = () -> Http2Client.builder()
            .shareConnectionCache(false)
            .connectTimeout(Duration.ofMinutes(10))
            .protocolConfig(pc -> pc.priorKnowledge(true))
            .baseUri("http://localhost:" + plainPort + "/versionspecific")
            .build();
    private static final Supplier<Http2Client> upgradeClient = () -> Http2Client.builder()
            .shareConnectionCache(false)
            .baseUri("http://localhost:" + plainPort + "/versionspecific")
            .build();
    private static final Supplier<Http2Client> tlsClient = () -> Http2Client.builder()
            .shareConnectionCache(false)
            .baseUri("https://localhost:" + tlsPort + "/versionspecific")
            .tls(clientTls())
            .build();

    Http2WebClientTest(WebServer server) {
        plainPort = server.port();
        tlsPort = server.port("https");
        finalTlsPort = server.port(FINAL_TLS_SOCKET);
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

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        serverBuilder.useNio(false);
        executorService = Executors.newFixedThreadPool(10);

        Keys privateKeyConfig =
                Keys.builder()
                        .keystore(keystore -> keystore.keystore(Resource.create("server.p12"))
                                .passphrase("password"))
                        .build();

        Tls tls = Tls.builder()
                .privateKey(privateKeyConfig.privateKey().get())
                .privateKeyCertChain(privateKeyConfig.certChain())
                .build();

        HttpRouting.Builder router = HttpRouting.builder()
                .get("/", (req, res) -> res.send("Hello world!"))
                .get("/redirect-to-http", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                        .header(HeaderNames.LOCATION, "http://localhost:" + plainPort + "/versionspecific")
                        .send())
                .post("/redirect-post-to-http", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                        .header(HeaderNames.LOCATION, "http://localhost:" + plainPort + "/redirected-post")
                        .send())
                .post("/redirect-same-origin-entity", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                        .header(HeaderNames.LOCATION, "/redirected-entity")
                        .send())
                .post("/redirect-consumed-entity", (req, res) -> {
                    req.content().as(String.class);
                    res.status(Status.TEMPORARY_REDIRECT_307)
                            .header(HeaderNames.LOCATION, "/redirected-entity")
                            .send();
                })
                .post("/redirect-cross-origin-entity", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                        .header(HeaderNames.LOCATION, "http://target.example:" + plainPort + "/redirected-entity")
                        .send())
                .post("/redirect-host-only-entity", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                        .header(HeaderNames.LOCATION, "/redirected-entity")
                        .send())
                .post("/output-stream-redirect/start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                        .header(HeaderNames.LOCATION, "next")
                        .send())
                .post("/output-stream-redirect/next", (req, res) -> res.status(Status.PERMANENT_REDIRECT_308)
                        .header(HeaderNames.LOCATION, "final")
                        .send())
                .post("/output-stream-redirect/final", (req, res) -> res.send("final redirect target"))
                .post("/output-stream-expect/start", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                        .header(HeaderNames.LOCATION,
                                "http://target.example:" + plainPort + "/output-stream-expect/target")
                        .send())
                .post("/output-stream-expect/target", (req, res) -> res.header(HeaderNames.SET_COOKIE,
                                                                               "target=once; Path=/")
                        .send(req.content().as(String.class)))
                .post("/synthetic-output-target", (req, res) -> res.send(req.content().as(String.class)))
                .route(HEAD, "/redirect-head-to-http", (req, res) -> res.status(Status.TEMPORARY_REDIRECT_307)
                        .header(HeaderNames.LOCATION, "http://localhost:" + plainPort + "/redirected-head")
                        .send())
                .route(Http1Route.route(GET, "/versionspecific", (req, res) -> res.send("HTTP/1.1 route")))
                .route(Http1Route.route(HEAD, "/redirected-head", (req, res) -> res.status(Status.NO_CONTENT_204).send()))
                .route(Http1Route.route(POST,
                                        "/redirected-post",
                                        (req, res) -> res.send("HTTP/1.1 " + req.content().as(String.class))))
                .route(Http2Route.route(GET, "/versionspecific", (req, res) -> {
                    res.header(CLIENT_USER_AGENT_HEADER_NAME,
                               new String[] {req.headers().get(USER_AGENT).get()});
                    res.header(SERVER_HEADER_FROM_PARAM_NAME,
                               new String[] {req.query().get("custQueryParam")});
                    res.send("HTTP/2 route");
                }))
                .route(Http2Route.route(GET,
                                        "/connection-target",
                                        (req, res) -> res.send(req.requestedUri().host() + "|" + req.socketId())))
                .route(Http2Route.route(GET,
                                        "/authority-cookies",
                                        (req, res) -> {
                                            String cookies = req.headers().first(HeaderNames.COOKIE).orElse("");
                                            res.send(req.requestedUri().host() + "|" + cookies + "|" + req.socketId());
                                        }))
                .route(Http2Route.route(GET,
                                        "/generic-retarget",
                                        (req, res) -> res.send("bootstrap|" + req.socketId())))
                .route(Http2Route.route(PUT, "/versionspecific", (req, res) -> {
                    res.header(SERVER_CUSTOM_HEADER_NAME,
                               new String[] {req.headers().get(CLIENT_CUSTOM_HEADER_NAME).get()});
                    res.header(CLIENT_USER_AGENT_HEADER_NAME,
                               new String[] {req.headers().get(USER_AGENT).get()});
                    res.header(SERVER_HEADER_FROM_PARAM_NAME,
                               new String[] {req.query().get("custQueryParam")});
                    res.send("PUT " + req.content().as(String.class));
                }))
                .route(Http2Route.route(POST, "/versionspecific", (req, res) -> {
                    res.header(SERVER_CUSTOM_HEADER_NAME,
                               new String[] {req.headers().get(CLIENT_CUSTOM_HEADER_NAME).get()});
                    res.header(CLIENT_USER_AGENT_HEADER_NAME,
                               new String[] {req.headers().get(USER_AGENT).get()});
                    res.header(SERVER_HEADER_FROM_PARAM_NAME,
                               new String[] {req.query().get("custQueryParam")});
                    res.send("POST " + req.content().as(String.class));
                }))
                .route(Http2Route.route(POST, "/redirected-entity", (req, res) -> res.send(
                        req.headers().first(HeaderNames.HOST).orElse("missing-host")
                                + "|"
                                + req.headers().first(HeaderNames.AUTHORIZATION).orElse("missing-authorization")
                                + "|"
                                + (req.content().hasEntity() ? req.content().as(String.class) : ""))))
                .route(Http2Route.route(GET, "/versionspecific/h2streaming", (req, res) -> {
                    res.status(Status.OK_200);
                    String execId = req.query().get("execId");
                    try (OutputStream os = res.outputStream()) {
                        for (int i = 0; i < 5; i++) {
                            os.write(String.format(execId + "BAF%03d", i).getBytes());
                            Thread.sleep(10);
                        }
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }))
                .route(Http2Route.route(GET, "/versionspecific/observer-reset", (req, res) -> {
                    try (OutputStream output = res.outputStream()) {
                        output.write(1);
                        output.flush();
                        RESET_RESPONSE_RELEASE.get().join();
                    } catch (IOException ignored) {
                        // Expected when the client resets the response stream.
                    }
                }))
                .route(Http2Route.route(GET, "/versionspecific/h2streaming-headers", (req, res) -> {
                    res.status(Status.OK_200);
                    int phase = req.headers()
                            .first(HeaderNames.create("phase"))
                            .map(Integer::parseInt)
                            .orElse(0);
                    for (int i = phase; i < 1000 + phase; i++) {
                        res.headers().add(HeaderNames.create("test" + i), "test" + i);
                    }
                    String execId = req.query().get("execId");
                    try (OutputStream os = res.outputStream()) {
                        for (int i = 0; i < 5; i++) {
                            os.write(String.format(execId + "BAF%03d", i).getBytes());
                            Thread.sleep(10);
                        }
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }));

        HttpRouting.Builder finalTlsRouter = HttpRouting.builder()
                .route(Http2Route.route(GET,
                                        "/generic-retarget",
                                        (req, res) -> res.send("final|" + req.socketId())));

        serverBuilder
                .port(-1)
                .host("localhost")
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(Http2Config.builder()
                                                                    .initialWindowSize(10)
                                                                    .build())
                                               .build())
                .putSocket("https", builder -> builder.port(-1)
                        .host("localhost")
                        .tls(tls)
                        .connectionOptions(connectionOptions -> connectionOptions
                                .socketReceiveBufferSize(4096))
                        .backlog(8192)
                )
                .putSocket(FINAL_TLS_SOCKET, builder -> builder.port(-1)
                        .host("localhost")
                        .tls(tls))
                .routing(router)
                // we want the same routing on the other socket
                .routing("https", router.copy())
                .routing(FINAL_TLS_SOCKET, finalTlsRouter);
    }

    static Stream<Arguments> clientTypes() {
        return Stream.of(
                Arguments.of("localConnectionCache", LazyValue.create(() -> localCacheClient.get())),
                Arguments.of("globalConnectionCache", globalCacheClient),
                Arguments.of("priorKnowledge", priorKnowledgeClient),
                Arguments.of("upgrade", upgradeClient),
                Arguments.of("tls", tlsClient)
        );
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        executorService.shutdown();
        if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
    }

    @Test
    void genericClientWithServiceNegotiatesHttp2AfterServices() {
        RecordingTransportService observer = new RecordingTransportService();
        List<String> sentProtocols = new CopyOnWriteArrayList<>();
        AtomicInteger completedRequests = new AtomicInteger();
        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .baseUri("https://localhost:" + tlsPort + "/versionspecific")
                .tls(clientTls())
                .addService((chain, request) -> {
                    request.whenSent().thenAccept(sent -> sentProtocols.add(sent.protocolId()));
                    request.whenComplete().thenRun(completedRequests::incrementAndGet);
                    return chain.proceed(request);
                })
                .addService(observer)
                .build();

        try {
            for (int i = 0; i < 2; i++) {
                try (HttpClientResponse response = client.get()
                        .queryParam("custQueryParam", "service")
                        .request()) {
                    assertThat(response.protocolId(), is(Http2Client.PROTOCOL_ID));
                    assertThat(response.entity().as(String.class), is("HTTP/2 route"));
                }
            }

            assertThat(sentProtocols, contains(Http2Client.PROTOCOL_ID, Http2Client.PROTOCOL_ID));
            assertThat(completedRequests.get(), is(2));
            assertThat(observer.connections.size(), is(1));
            assertThat(observer.connections.getFirst().streams.size(), is(2));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void typedClientShortCircuitUsesFinalizedEndpointAndClosesReturnedResource() {
        URI endpoint = URI.create("https://short-circuit.example:8443/resource");
        AtomicInteger resourceCloses = new AtomicInteger();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .baseUri(endpoint)
                .addService((_, request) -> WebClientServiceResponse.builder()
                        .serviceRequest(request)
                        .whenComplete(new CompletableFuture<>())
                        .connection(resourceCloses::incrementAndGet)
                        .status(Status.OK_200)
                        .headers(ClientResponseHeaders.create(WritableHeaders.create()))
                        .build())
                .build();

        try {
            Http2ClientResponse response = client.get().request();
            assertThat(response.lastEndpointUri().toUri(), is(endpoint));
            assertThat(response.status(), is(Status.OK_200));
            response.close();
            response.close();
            assertThat(resourceCloses.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void typedClientClosesDecoratedAndRawResponseResourcesOnce() {
        RecordingTransportService observer = new RecordingTransportService();
        AtomicInteger decoratedResourceCloses = new AtomicInteger();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(protocol -> protocol.priorKnowledge(true))
                .baseUri("http://localhost:" + plainPort + "/versionspecific")
                .addService((chain, request) -> WebClientServiceResponse.builder(chain.proceed(request))
                        .connection(decoratedResourceCloses::incrementAndGet)
                        .build())
                .addService(observer)
                .build();

        try {
            try (Http2ClientResponse response = client.get()
                    .queryParam("custQueryParam", "lifecycle")
                    .request()) {
                assertThat(response.entity().as(String.class), is("HTTP/2 route"));
            }
            assertThat(decoratedResourceCloses.get(), is(1));
            assertThat(observer.connections.size(), is(1));
            assertThat(observer.connections.getFirst().streams.size(), is(1));
            assertThat(observer.connections.getFirst().streams.getFirst().closeCount.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void servicePostProcessingFailureClosesRawResponseOnce() {
        RecordingTransportService observer = new RecordingTransportService();
        IllegalStateException expected = new IllegalStateException("simulated service post-processing failure");
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(protocol -> protocol.priorKnowledge(true))
                .baseUri("http://localhost:" + plainPort + "/versionspecific")
                .addService((chain, request) -> {
                    chain.proceed(request);
                    throw expected;
                })
                .addService(observer)
                .build();

        try {
            IllegalStateException actual = assertThrows(
                    IllegalStateException.class,
                    () -> client.get()
                            .queryParam("custQueryParam", "post-processing")
                            .request());
            assertThat(actual, is(expected));
            assertThat(observer.connections.size(), is(1));
            assertThat(observer.connections.getFirst().streams.size(), is(1));
            assertThat(observer.connections.getFirst().streams.getFirst().closeCount.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void syntheticSeeOtherSkipsUnclaimedOutputStreamHandler() {
        AtomicInteger handlerInvocations = new AtomicInteger();
        AtomicInteger redirectResourceCloses = new AtomicInteger();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(protocol -> protocol.priorKnowledge(true))
                .baseUri("http://localhost:" + plainPort)
                .addService((chain, request) -> {
                    if (request.uri().toUri().getPath().equals("/synthetic-output-303")) {
                        WritableHeaders<?> headers = WritableHeaders.create();
                        headers.add(HeaderNames.LOCATION, "/versionspecific?custQueryParam=synthetic");
                        return WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(redirectResourceCloses::incrementAndGet)
                                .status(Status.SEE_OTHER_303)
                                .headers(ClientResponseHeaders.create(headers))
                                .build();
                    }
                    return chain.proceed(request);
                })
                .build();

        try (Http2ClientResponse response = client.post("/synthetic-output-303")
                .followRedirects(true)
                .outputStream(output -> {
                    handlerInvocations.incrementAndGet();
                    output.write("payload".getBytes(StandardCharsets.UTF_8));
                    output.close();
                })) {
            assertThat(response.as(String.class), is("HTTP/2 route"));
            assertThat(handlerInvocations.get(), is(0));
            assertThat(redirectResourceCloses.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void syntheticTemporaryRedirectClaimsOutputStreamHandlerAtTargetOnce() {
        AtomicInteger handlerInvocations = new AtomicInteger();
        AtomicInteger redirectResourceCloses = new AtomicInteger();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(protocol -> protocol.priorKnowledge(true))
                .baseUri("http://localhost:" + plainPort)
                .addService((chain, request) -> {
                    if (request.uri().toUri().getPath().equals("/synthetic-output-307")) {
                        WritableHeaders<?> headers = WritableHeaders.create();
                        headers.add(HeaderNames.LOCATION, "/synthetic-output-target");
                        return WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(redirectResourceCloses::incrementAndGet)
                                .status(Status.TEMPORARY_REDIRECT_307)
                                .headers(ClientResponseHeaders.create(headers))
                                .build();
                    }
                    return chain.proceed(request);
                })
                .build();

        try (Http2ClientResponse response = client.post("/synthetic-output-307")
                .followRedirects(true)
                .outputStream(output -> {
                    handlerInvocations.incrementAndGet();
                    output.write("payload".getBytes(StandardCharsets.UTF_8));
                    output.close();
                })) {
            assertThat(response.as(String.class), is("payload"));
            assertThat(handlerInvocations.get(), is(1));
            assertThat(redirectResourceCloses.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void crossOriginSyntheticRedirectDoesNotReuseExplicitConnection() {
        ClientConnection sourceConnection = mock(ClientConnection.class);
        AtomicInteger handlerInvocations = new AtomicInteger();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(protocol -> protocol.priorKnowledge(true))
                .addService((chain, request) -> {
                    if (request.uri().toUri().getPath().equals("/cross-origin-source")) {
                        WritableHeaders<?> headers = WritableHeaders.create();
                        headers.add(HeaderNames.LOCATION,
                                    "http://localhost:" + plainPort + "/versionspecific?custQueryParam=cross-origin");
                        return WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(() -> {
                                })
                                .status(Status.SEE_OTHER_303)
                                .headers(ClientResponseHeaders.create(headers))
                                .build();
                    }
                    return chain.proceed(request);
                })
                .build();

        try (Http2ClientResponse response = client.post("http://source.invalid/cross-origin-source")
                .connection(sourceConnection)
                .followRedirects(true)
                .outputStream(output -> {
                    handlerInvocations.incrementAndGet();
                    output.close();
                })) {
            assertThat(response.as(String.class), is("HTTP/2 route"));
            assertThat(handlerInvocations.get(), is(0));
            verifyNoMoreInteractions(sourceConnection);
        } finally {
            client.closeResource();
        }
    }

    @Test
    void authorityOnlySyntheticRedirectDoesNotReuseExplicitConnection() {
        ClientConnection sourceConnection = mock(ClientConnection.class);
        AtomicInteger handlerInvocations = new AtomicInteger();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(protocol -> protocol.priorKnowledge(true))
                .addService((chain, request) -> {
                    if (request.uri().toUri().getPath().equals("/authority-only-source")) {
                        request.headers().set(Http2Headers.AUTHORITY_NAME, "alternate.example:" + plainPort);
                        WritableHeaders<?> headers = WritableHeaders.create();
                        headers.add(HeaderNames.LOCATION, "/versionspecific?custQueryParam=authority-only");
                        return WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(() -> {
                                })
                                .status(Status.SEE_OTHER_303)
                                .headers(ClientResponseHeaders.create(headers))
                                .build();
                    }
                    return chain.proceed(request);
                })
                .build();

        try (Http2ClientResponse response = client.post("http://localhost:" + plainPort + "/authority-only-source")
                .connection(sourceConnection)
                .followRedirects(true)
                .outputStream(output -> {
                    handlerInvocations.incrementAndGet();
                    output.close();
                })) {
            assertThat(response.as(String.class), is("HTTP/2 route"));
            assertThat(handlerInvocations.get(), is(0));
            verifyNoMoreInteractions(sourceConnection);
        } finally {
            client.closeResource();
        }
    }

    @Test
    void syntheticThenEarlyExpectRedirectHonorsMaxOne() {
        AtomicInteger handlerInvocations = new AtomicInteger();
        Http2Client client = syntheticBeforeExpectClient();

        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client
                    .post("/synthetic-before-expect")
                    .followRedirects(true)
                    .maxRedirects(1)
                    .outputStream(output -> {
                        handlerInvocations.incrementAndGet();
                        output.write("streamed body".getBytes(StandardCharsets.UTF_8));
                        output.close();
                    }));
            assertThat(failure.getMessage(), containsString("Maximum number of request redirections (1) reached"));
            assertThat(handlerInvocations.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void syntheticThenEarlyExpectRedirectHonorsMaxTwo() {
        AtomicInteger handlerInvocations = new AtomicInteger();
        Http2Client client = syntheticBeforeExpectClient();

        try (Http2ClientResponse response = client.post("/synthetic-before-expect")
                .followRedirects(true)
                .maxRedirects(2)
                .outputStream(output -> {
                    handlerInvocations.incrementAndGet();
                    output.write("streamed body".getBytes(StandardCharsets.UTF_8));
                    output.close();
                })) {
            assertThat(response.as(String.class), is("streamed body"));
            assertThat(handlerInvocations.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void genericPlaintextClientWithServiceDoesNotAttemptH2c() {
        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .baseUri("http://localhost:" + plainPort + "/versionspecific")
                .addService((chain, request) -> chain.proceed(request))
                .build();

        try {
            try (HttpClientResponse response = client.get().request()) {
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is("HTTP/1.1 route"));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void retainsConnectionsForAlternatingTargets() {
        RecordingTransportService observer = new RecordingTransportService();
        Http2Client client = Http2Client.builder()
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .addService(observer)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(protocol -> protocol.priorKnowledge(true))
                .baseUri("http://a.example:" + plainPort + "/versionspecific")
                .build();

        try {
            try (Http2ClientResponse response = client.get()
                    .queryParam("custQueryParam", "a-first")
                    .request()) {
                assertThat(response.entity().as(String.class), is("HTTP/2 route"));
            }
            try (Http2ClientResponse response = client.get("http://b.example:" + plainPort + "/versionspecific")
                    .queryParam("custQueryParam", "b")
                    .request()) {
                assertThat(response.entity().as(String.class), is("HTTP/2 route"));
            }
            try (Http2ClientResponse response = client.get()
                    .queryParam("custQueryParam", "a-second")
                    .request()) {
                assertThat(response.entity().as(String.class), is("HTTP/2 route"));
            }

            assertThat(observer.connections.size(), is(2));
            assertThat(observer.connections.stream().map(it -> it.streams.size()).toList(), contains(2, 1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void servicedTlsNegotiatorDoesNotAttemptH2cAfterRedirect() {
        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .baseUri("https://localhost:" + tlsPort + "/redirect-to-http")
                .tls(clientTls())
                .addService((chain, request) -> chain.proceed(request))
                .build();

        try {
            try (HttpClientResponse response = client.get().followRedirects(true).request()) {
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.lastEndpointUri().scheme(), is("http"));
                assertThat(response.entity().as(String.class), is("HTTP/1.1 route"));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void servicedTlsNegotiatorBlocksCrossOriginMaterializedEntityByDefault() {
        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .baseUri("https://localhost:" + tlsPort + "/redirect-post-to-http")
                .tls(clientTls())
                .addService((chain, request) -> chain.proceed(request))
                .build();

        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> client.post().followRedirects(true).submit("body"));
            assertThat(failure.getMessage(), containsString("Cross-origin redirect with request entity is disabled"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void servicedTlsNegotiatorReplaysMaterializedEntityAfterRedirectWhenEnabled() {
        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .followCrossOriginEntityRedirects(true)
                .baseUri("https://localhost:" + tlsPort + "/redirect-post-to-http")
                .tls(clientTls())
                .addService((chain, request) -> chain.proceed(request))
                .build();

        try {
            try (HttpClientResponse response = client.post().followRedirects(true).submit("body")) {
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.lastEndpointUri().scheme(), is("http"));
                assertThat(response.entity().as(String.class), is("HTTP/1.1 body"));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void directClientBlocksCrossOriginMaterializedEntityByDefault() {
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://source.example:" + plainPort + "/redirect-cross-origin-entity")
                .build();

        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> client.post().followRedirects(true).submit("body"));
            assertThat(failure.getMessage(), containsString("Cross-origin redirect with request entity is disabled"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void directClientReplaysCrossOriginMaterializedEntityWhenEnabled() {
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .followCrossOriginEntityRedirects(true)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://source.example:" + plainPort + "/redirect-cross-origin-entity")
                .build();

        try {
            try (Http2ClientResponse response = client.post()
                    .followRedirects(true)
                    .header(HeaderNames.HOST, "virtual.example:" + plainPort)
                    .header(HeaderNames.AUTHORIZATION, "Bearer redirect-secret")
                    .submit("body")) {
                assertThat(response.as(String.class),
                           is("target.example:" + plainPort + "|missing-authorization|body"));
            }
        } finally {
            client.closeResource();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void directClientPreservesExplicitAuthorityOnSameOriginRedirect(boolean pseudoheader) {
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://source.example:" + plainPort + "/redirect-same-origin-entity")
                .build();

        try {
            try (Http2ClientResponse response = client.post()
                    .followRedirects(true)
                    .header(pseudoheader ? Http2Headers.AUTHORITY_NAME : HeaderNames.HOST,
                            "virtual.example:" + plainPort)
                    .submit("body")) {
                assertThat(response.as(String.class),
                           is("virtual.example:" + plainPort + "|missing-authorization|body"));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void configuredAuthorityKeepsPrecedenceOverServiceHost() {
        AtomicInteger serviceCalls = new AtomicInteger();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://source.example:" + plainPort + "/connection-target")
                .addService((chain, request) -> {
                    assertThat(request.headers().get(Http2Headers.AUTHORITY_NAME).get(),
                               is("configured.example:" + plainPort));
                    request.headers().set(HeaderValues.create(HeaderNames.HOST, "service.example:" + plainPort));
                    serviceCalls.incrementAndGet();
                    return chain.proceed(request);
                })
                .build();

        try {
            Http2ClientRequest request = client.get()
                    .header(Http2Headers.AUTHORITY_NAME, "configured.example:" + plainPort);
            String first = responseBody(request);
            String second = responseBody(request);

            assertThat(first, startsWith("configured.example|"));
            assertThat(second, startsWith("configured.example|"));
            assertThat(connectionId(second), is(connectionId(first)));
            assertThat(serviceCalls.get(), is(2));
            assertThat(request.headers().get(Http2Headers.AUTHORITY_NAME).get(), is("configured.example:" + plainPort));
            assertThat(request.headers().contains(HeaderNames.HOST), is(false));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void serviceAuthoritySelectsFinalCookiesAndConnectionTarget() {
        CookieStore cookieStore = new CookieManager().getCookieStore();
        HttpCookie sourceCookie = new HttpCookie("source", "private");
        sourceCookie.setPath("/");
        sourceCookie.setVersion(0);
        cookieStore.add(URI.create("http://source.example:" + plainPort), sourceCookie);
        HttpCookie targetCookie = new HttpCookie("target", "selected");
        targetCookie.setPath("/");
        targetCookie.setVersion(0);
        cookieStore.add(URI.create("http://target.example:" + plainPort), targetCookie);
        AtomicReference<String> finalAuthority = new AtomicReference<>("target.example:" + plainPort);
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://source.example:" + plainPort + "/authority-cookies")
                .cookieManager(WebClientCookieManager.create(config -> config
                        .automaticStoreEnabled(true)
                        .cookieStore(cookieStore)))
                .addService((chain, request) -> {
                    request.headers().set(HeaderValues.create(Http2Headers.AUTHORITY_NAME, finalAuthority.get()));
                    return chain.proceed(request);
                })
                .build();

        try {
            String first = responseBody(client.get());
            String reused = responseBody(client.get());
            finalAuthority.set("source.example:" + plainPort);
            String source = responseBody(client.get());

            assertThat(first, startsWith("target.example|"));
            assertThat(first, containsString("target=selected"));
            assertThat(first, not(containsString("source=")));
            assertThat(connectionId(reused), is(connectionId(first)));
            assertThat(source, startsWith("source.example|"));
            assertThat(source, containsString("source=private"));
            assertThat(source, not(containsString("target=")));
            assertThat(connectionId(source), not(is(connectionId(first))));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void directClientUsesServiceFinalHostForRedirectPolicy() {
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://localhost:" + plainPort + "/redirect-host-only-entity")
                .addService((chain, request) -> {
                    String host = request.uri().toUri().getPath().equals("/redirected-entity")
                            ? "target.example:"
                            : "source.example:";
                    request.headers().set(HeaderValues.create(HeaderNames.HOST, host + plainPort));
                    return chain.proceed(request);
                })
                .build();

        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> client.post().followRedirects(true).submit("body"));
            assertThat(failure.getMessage(), containsString("Cross-origin redirect with request entity is disabled"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void directClientUsesServiceFinalAuthorityForRedirectPolicy() {
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://localhost:" + plainPort + "/redirect-host-only-entity")
                .addService((chain, request) -> {
                    String authority = request.uri().toUri().getPath().equals("/redirected-entity")
                            ? "target.example:"
                            : "source.example:";
                    request.headers().set(HeaderValues.create(Http2Headers.AUTHORITY_NAME, authority + plainPort));
                    return chain.proceed(request);
                })
                .build();

        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> client.post().followRedirects(true).submit("body"));
            assertThat(failure.getMessage(), containsString("Cross-origin redirect with request entity is disabled"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void redirectPolicyFailureCompletesServiceLifecycle() throws Exception {
        AtomicReference<CompletionStage<WebClientServiceRequest>> whenSent = new AtomicReference<>();
        AtomicReference<CompletionStage<WebClientServiceResponse>> whenComplete = new AtomicReference<>();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://source.example:" + plainPort + "/redirect-cross-origin-entity")
                .addService((chain, request) -> {
                    if (request.uri().toUri().getPath().equals("/redirected-entity")) {
                        whenSent.set(request.whenSent());
                        whenComplete.set(request.whenComplete());
                    }
                    return chain.proceed(request);
                })
                .build();

        try {
            assertThrows(IllegalStateException.class,
                         () -> client.post().followRedirects(true).submit("body"));
            ExecutionException sentFailure = assertThrows(
                    ExecutionException.class,
                    () -> whenSent.get().toCompletableFuture().get(5, TimeUnit.SECONDS));
            ExecutionException completeFailure = assertThrows(
                    ExecutionException.class,
                    () -> whenComplete.get().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertThat(sentFailure.getCause().getMessage(),
                       containsString("Cross-origin redirect with request entity is disabled"));
            assertThat(completeFailure.getCause().getMessage(),
                       containsString("Cross-origin redirect with request entity is disabled"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void outputStreamEntityIsNotReinvokedAfterItWasSent() {
        AtomicInteger handlerInvocations = new AtomicInteger();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://localhost:" + plainPort + "/redirect-consumed-entity")
                .build();

        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> client.post()
                    .followRedirects(true)
                    .outputStream(output -> {
                        handlerInvocations.incrementAndGet();
                        output.write("body".getBytes(StandardCharsets.UTF_8));
                        output.close();
                    }));
            assertThat(failure.getMessage(), containsString("after it was sent"));
            assertThat(handlerInvocations.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void emptyOutputStreamFollowsMethodPreservingRedirect() {
        AtomicInteger handlerInvocations = new AtomicInteger();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://localhost:" + plainPort + "/redirect-same-origin-entity")
                .build();

        try {
            try (Http2ClientResponse response = client.post()
                    .followRedirects(true)
                    .outputStream(output -> {
                        handlerInvocations.incrementAndGet();
                        output.close();
                    })) {
                assertThat(response.as(String.class),
                           is("localhost:" + plainPort + "|missing-authorization|"));
            }
            assertThat(handlerInvocations.get(), is(1));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void outputStreamRedirectExposesFinalRequestAndResolvesRelativeChain() {
        AtomicReference<WebClientServiceRequest> responseRequest = new AtomicReference<>();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://localhost:" + plainPort + "/output-stream-redirect/start")
                .addService((chain, request) -> {
                    request.headers().set(REDIRECT_ATTEMPT_HEADER, request.uri().toUri().getPath());
                    WebClientServiceResponse response = chain.proceed(request);
                    responseRequest.set(response.serviceRequest());
                    return response;
                })
                .build();

        try {
            try (Http2ClientResponse response = client.post()
                    .followRedirects(true)
                    .outputStream(OutputStream::close)) {
                assertThat(response.as(String.class), is("final redirect target"));
                assertThat(response.lastEndpointUri().toUri().getPath(), is("/output-stream-redirect/final"));
            }

            WebClientServiceRequest finalRequest = responseRequest.get();
            assertThat(finalRequest.uri().toUri().getPath(), is("/output-stream-redirect/final"));
            assertThat(finalRequest.headers().get(REDIRECT_ATTEMPT_HEADER).get(),
                       is("/output-stream-redirect/final"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void expectRedirectExposesTargetLifecycleAndStoresFinalCookiesOnce() throws Exception {
        URI sourceUri = URI.create("http://source.example:" + plainPort + "/output-stream-expect/start");
        URI targetUri = URI.create("http://target.example:" + plainPort + "/output-stream-expect/target");
        CountingCookieStore cookieStore = new CountingCookieStore();
        WebClientCookieManager cookieManager = WebClientCookieManager.create(config -> config
                .automaticStoreEnabled(true)
                .cookieStore(cookieStore));
        AtomicReference<CompletionStage<WebClientServiceRequest>> whenSent = new AtomicReference<>();
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .sendExpectContinue(true)
                .readContinueTimeout(Duration.ofMillis(250))
                .readTimeout(Duration.ofSeconds(5))
                .followCrossOriginEntityRedirects(true)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(it -> it.priorKnowledge(true))
                .cookieManager(cookieManager)
                .baseUri(sourceUri)
                .addService((chain, request) -> {
                    if (request.uri().toUri().getPath().equals("/output-stream-expect/start")) {
                        whenSent.set(request.whenSent());
                    }
                    return chain.proceed(request);
                })
                .build();

        try {
            try (Http2ClientResponse response = client.post()
                    .followRedirects(true)
                    .outputStream(output -> {
                        output.write("streamed body".getBytes(StandardCharsets.UTF_8));
                        output.close();
                    })) {
                assertThat(response.as(String.class), is("streamed body"));
                assertThat(response.lastEndpointUri().toUri(), is(targetUri));
            }

            WebClientServiceRequest sentRequest = whenSent.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(sentRequest.uri().toUri(), is(targetUri));
            assertThat(sentRequest.headers().get(HeaderNames.HOST).get(), is("target.example:" + plainPort));
            assertThat(cookieStore.additions(), is(1));
            assertThat(cookieStore.get(targetUri)
                               .stream()
                               .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                               .toList(),
                       contains("target=once"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void servicedTlsNegotiatorRedirectsHeadWithoutCreatingAnEntity() {
        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .baseUri("https://localhost:" + tlsPort + "/redirect-head-to-http")
                .tls(clientTls())
                .addService((chain, request) -> chain.proceed(request))
                .build();

        try {
            try (HttpClientResponse response = client.head().followRedirects(true).request()) {
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.lastEndpointUri().scheme(), is("http"));
                assertThat(response.status(), is(Status.NO_CONTENT_204));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void servicedHttp1EmptyEntityCompletesLifecycleWhenEntityIsAccessed() throws Exception {
        CompletableFuture<Void> serviceComplete = new CompletableFuture<>();
        WebClient client = WebClient.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .baseUri("http://localhost:" + plainPort + "/redirected-head")
                .addService((chain, request) -> {
                    request.whenComplete().thenRun(() -> serviceComplete.complete(null));
                    return WebClientServiceResponse.builder(chain.proceed(request)).build();
                })
                .build();

        try {
            try (HttpClientResponse response = client.head().request()) {
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.status(), is(Status.NO_CONTENT_204));
                assertThat(response.entity().hasEntity(), is(false));
                serviceComplete.get(5, TimeUnit.SECONDS);
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void observesHttp2TransportLifecycle() {
        RecordingTransportService observer = new RecordingTransportService();
        CompletableFuture<Void> resetRelease = new CompletableFuture<>();
        RESET_RESPONSE_RELEASE.set(resetRelease);
        Http2Client client = Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .protocolConfig(it -> it.priorKnowledge(true))
                .baseUri("http://localhost:" + plainPort + "/versionspecific")
                .addService(observer)
                .build();

        try {
            try (Http2ClientResponse response = client.get()
                    .queryParam("custQueryParam", "observer")
                    .request()) {
                assertThat(response.as(String.class), is("HTTP/2 route"));
            }
            try (Http2ClientResponse response = client.get("/observer-reset").request()) {
                assertThat(response.status(), is(Status.OK_200));
            }
            assertThat(observer.registrationCloses.get(), is(0));
        } finally {
            resetRelease.complete(null);
            RESET_RESPONSE_RELEASE.set(CompletableFuture.completedFuture(null));
            client.closeResource();
        }
        client.closeResource();

        assertThat(observer.registrationOpens.get(), is(1));
        assertThat(observer.registrationCloses.get(), is(1));
        assertThat(observer.completionRequests.get(), is(1));
        assertThat(observer.connectionClosedBeforeRegistration, is(true));
        assertThat(observer.connections.size(), is(1));

        RecordingConnection connection = observer.connections.getFirst();
        assertThat(connection.role, is(CLIENT));
        assertThat(connection.transport, is(TRANSPORT_TCP));
        assertThat(connection.handshake, is(NONE));
        assertThat(connection.protocols, is(List.of(PROTOCOL_HTTP_2)));
        assertThat(connection.outcomes, is(List.of(LOCAL_CLOSE)));
        assertThat(connection.streams.size(), is(2));
        assertThat(connection.streams.stream()
                           .allMatch(it -> it.direction == BIDIRECTIONAL && it.initiator == LOCAL),
                   is(true));
        assertThat(connection.streams.stream().map(it -> it.outcome.get()).toList(),
                   is(List.of(COMPLETED, RESET)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void clientGet(String name, Supplier<Http2Client> client) {
        try (Http2ClientResponse response = client.get()
                .get()
                .queryParam("custQueryParam", "test-get")
                .request()) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("HTTP/2 route"));
            assertThat(response.headers().get(CLIENT_USER_AGENT_HEADER_NAME).get(),
                       is(Http2ClientRequestImpl.USER_AGENT_HEADER.get()));
            assertThat(response.headers().get(SERVER_HEADER_FROM_PARAM_NAME).get(),
                       is("test-get"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void clientPut(String clientType, Supplier<Http2Client> client) {
        String payload = clientType + " payload";
        String custHeaderValue = clientType + " header value";

        try (Http2ClientResponse response = client.get()
                .method(PUT)
                .queryParam("custQueryParam", "test-put")
                .header(CLIENT_CUSTOM_HEADER_NAME, custHeaderValue)
                .submit(payload)) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("PUT " + payload));
            assertThat(response.headers().get(CLIENT_USER_AGENT_HEADER_NAME).get(),
                       is(Http2ClientRequestImpl.USER_AGENT_HEADER.get()));
            assertThat(response.headers().get(SERVER_CUSTOM_HEADER_NAME).get(),
                       is(custHeaderValue));
            assertThat(response.headers().get(SERVER_HEADER_FROM_PARAM_NAME).get(),
                       is("test-put"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void clientPost(String clientType, Supplier<Http2Client> client) {
        String payload = clientType + " payload";
        String custHeaderValue = clientType + " header value";

        try (Http2ClientResponse response = client.get()
                .method(POST)
                .queryParam("custQueryParam", "test-post")
                .header(CLIENT_CUSTOM_HEADER_NAME, custHeaderValue)
                .submit(payload)) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("POST " + payload));
            assertThat(response.headers().get(CLIENT_USER_AGENT_HEADER_NAME).get(),
                       is(Http2ClientRequestImpl.USER_AGENT_HEADER.get()));
            assertThat(response.headers().get(SERVER_CUSTOM_HEADER_NAME).get(),
                       is(custHeaderValue));
            assertThat(response.headers().get(SERVER_HEADER_FROM_PARAM_NAME).get(),
                       is("test-post"));
        }
    }

    @Test
    void sharedCachedConnectionUsesCurrentClientStreamLogConfig() {
        Logger logger = Logger.getLogger(CLIENT_SEND_LOGGER_NAME);
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        List<String> messages = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.FINER);

        try {
            String baseUri = "http://127.0.0.1:" + plainPort + "/versionspecific";
            Http2Client unsafeClient = Http2Client.builder()
                    .shareConnectionCache(true)
                    .baseUri(baseUri)
                    .protocolConfig(it -> it.priorKnowledge(true)
                            .log(log -> log.unsafeRawData(true)))
                    .build();
            try (Http2ClientResponse response = unsafeClient.method(POST)
                    .queryParam("custQueryParam", "warmup")
                    .header(CLIENT_CUSTOM_HEADER_NAME, "warmup")
                    .submit("warmup")) {
                assertThat(response.status(), is(Status.OK_200));
            }
            messages.clear();

            Http2Client safeClient = Http2Client.builder()
                    .shareConnectionCache(true)
                    .baseUri(baseUri)
                    .protocolConfig(it -> it.priorKnowledge(true))
                    .build();
            try (Http2ClientResponse response = safeClient.method(POST)
                    .queryParam("custQueryParam", "submit")
                    .header(CLIENT_CUSTOM_HEADER_NAME, "submit")
                    .header(HeaderNames.AUTHORIZATION, "Bearer safe-client-token")
                    .submit("safe-client-body")) {
                assertThat(response.status(), is(Status.OK_200));
            }

            String submitMessages = String.join("\n", messages);
            assertThat(submitMessages, containsString(":method: POST"));
            assertThat(submitMessages, containsString(":path: /versionspecific"));
            assertThat(submitMessages, containsString("Authorization: <redacted>"));
            assertThat(submitMessages, not(containsString("Bearer safe-client-token")));
            assertThat(submitMessages, not(containsString("safe-client-body")));
            messages.clear();

            try (Http2ClientResponse response = safeClient.method(POST)
                    .queryParam("custQueryParam", "output-stream")
                    .header(CLIENT_CUSTOM_HEADER_NAME, "output-stream")
                    .header(HeaderNames.AUTHORIZATION, "Bearer safe-stream-token")
                    .outputStream(out -> {
                        out.write("safe-stream-body".getBytes(StandardCharsets.UTF_8));
                        out.close();
                    })) {
                assertThat(response.status(), is(Status.OK_200));
            }

            String outputStreamMessages = String.join("\n", messages);
            assertThat(outputStreamMessages, containsString(":method: POST"));
            assertThat(outputStreamMessages, containsString(":path: /versionspecific"));
            assertThat(outputStreamMessages, containsString("Authorization: <redacted>"));
            assertThat(outputStreamMessages, not(containsString("Bearer safe-stream-token")));
            assertThat(outputStreamMessages, not(containsString("safe-stream-body")));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void multiplexParallelStreamsGet(String clientType, Supplier<Http2Client> client)
            throws ExecutionException, InterruptedException, TimeoutException {

        Http2Client http2Client = client.get();
        Consumer<Integer> callable = id -> {
            try (Http2ClientResponse response = http2Client
                    .get("/h2streaming")
                    .queryParam("execId", id.toString())
                    .request()
            ) {

                InputStream is = response.inputStream();
                for (int i = 0; ; i++) {
                    byte[] bytes = is.readNBytes("0BAF000".getBytes().length);
                    if (bytes.length == 0) {
                        break;
                    }
                    String message = new String(bytes);
                    assertThat(message, is(String.format(id + "BAF%03d", i)));
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> callable.accept(1), executorService)
                , CompletableFuture.runAsync(() -> callable.accept(2), executorService)
                , CompletableFuture.runAsync(() -> callable.accept(3), executorService)
                , CompletableFuture.runAsync(() -> callable.accept(4), executorService)
        ).get(5, TimeUnit.MINUTES);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("clientTypes")
    void multiplexParallelStreamsGetWithUniqueHeaders(String clientType, Supplier<Http2Client> client)
            throws ExecutionException, InterruptedException, TimeoutException {

        Http2Client http2Client = client.get();
        Consumer<Integer> callable = id -> {
            try (Http2ClientResponse response = http2Client
                    .get("/h2streaming-headers")
                    .queryParam("execId", id.toString())
                    .header(HeaderNames.create("phase"), String.valueOf(id * 1000))
                    .request()
            ) {

                InputStream is = response.inputStream();
                for (int i = 0; ; i++) {
                    byte[] bytes = is.readNBytes("0BAF000".getBytes().length);
                    if (bytes.length == 0) {
                        break;
                    }
                    String message = new String(bytes);
                    assertThat(message, is(String.format(id + "BAF%03d", i)));
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        CompletableFuture.allOf(
                IntStream.range(1, 6)
                        .boxed()
                        .map(i -> CompletableFuture
                                .runAsync(() -> callable.accept(i), executorService))
                        .toList()
                        .toArray(new CompletableFuture[0])
        ).get(5, TimeUnit.MINUTES);
    }

    @Test
    void serviceFinalTargetsSeparateAndReuseH2cConnections() {
        Http2Client client = Http2Client.builder()
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .baseUri("http://bootstrap.example:" + plainPort + "/connection-target")
                .addService((chain, request) -> {
                    request.uri().host(request.properties().get("target-host"));
                    return chain.proceed(request);
                })
                .build();

        try {
            String first = responseBody(client.get().property("target-host", "first.example"));
            String second = responseBody(client.get().property("target-host", "second.example"));
            String reused = responseBody(client.get().property("target-host", "first.example"));

            assertThat(host(first), is("first.example"));
            assertThat(host(second), is("second.example"));
            assertThat(host(reused), is("first.example"));
            assertThat(connectionId(reused), is(connectionId(first)));
            assertThat(connectionId(second), not(is(connectionId(first))));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void genericClientRetargetsAfterAlpnProtocolDiscovery() {
        WebClient client = WebClient.builder()
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .protocolPreference(List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID))
                .baseUri("https://localhost:" + tlsPort + "/generic-retarget")
                .tls(Tls.builder()
                             .trust(trust -> trust
                                     .keystore(store -> store
                                             .passphrase("password")
                                             .trustStore(true)
                                             .keystore(Resource.create("client.p12"))))
                             .build())
                .addService((chain, request) -> {
                    request.uri().port(finalTlsPort);
                    return chain.proceed(request);
                })
                .build();

        try {
            String first = client.get().requestEntity(String.class);
            String reused = client.get().requestEntity(String.class);

            assertThat(first, startsWith("final|"));
            assertThat(reused, startsWith("final|"));
            assertThat(connectionId(reused), is(connectionId(first)));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void reconnectUsesCurrentAddressBoundNoProxyTarget() throws IOException {
        AtomicInteger resolutions = new AtomicInteger();
        List<ResolvedClientTarget> targets = new CopyOnWriteArrayList<>();
        List<Socket> sockets = new CopyOnWriteArrayList<>();
        Proxy proxy = spy(Proxy.builder()
                                  .host("proxy.example")
                                  .port(8181)
                                  .addNoProxy("192.0.2.1")
                                  .addNoProxy("192.0.2.2")
                                  .build());
        doAnswer(invocation -> {
            ResolvedClientTarget target = invocation.getArgument(1);
            Socket socket = new Socket(InetAddress.ofLiteral("127.0.0.1"), plainPort);
            targets.add(target);
            sockets.add(socket);
            return socket;
        }).when(proxy).tcpSocket(any(), any(), any());

        Http2Client client = Http2Client.builder()
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .dnsResolver((_, _) -> resolutions.getAndIncrement() == 0
                        ? InetAddress.ofLiteral("192.0.2.1")
                        : InetAddress.ofLiteral("192.0.2.2"))
                .proxy(proxy)
                .baseUri("http://rotating.example:" + plainPort + "/versionspecific")
                .protocolConfig(pc -> pc.priorKnowledge(true)
                        .ping(true)
                        .pingTimeout(Duration.ofSeconds(1)))
                .build();

        try {
            assertThat(responseBody(client.get().queryParam("custQueryParam", "first")), is("HTTP/2 route"));
            assertThat(sockets.size(), is(1));
            assertThat(resolutions.get(), is(1));
            assertThat(responseBody(client.get().queryParam("custQueryParam", "reused")), is("HTTP/2 route"));
            assertThat(sockets.size(), is(1));
            assertThat(targets.size(), is(1));
            assertThat(resolutions.get(), is(1));

            sockets.get(0).close();
            assertThat(responseBody(client.get().queryParam("custQueryParam", "reconnected")), is("HTTP/2 route"));

            assertThat(targets.size(), is(2));
            assertThat(resolutions.get(), is(2));
            ResolvedClientTarget first = targets.get(0);
            ResolvedClientTarget second = targets.get(1);
            assertThat(second.logicalTarget(), is(first.logicalTarget()));
            assertThat(first.peerAddress().getAddress(), is(InetAddress.ofLiteral("192.0.2.1")));
            assertThat(second.peerAddress().getAddress(), is(InetAddress.ofLiteral("192.0.2.2")));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void tlsReloadRetiresPreviousTargetConnection() {
        Tls tls = Tls.builder().trustAll(true).build();
        Http2Client client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri("https://localhost:" + tlsPort + "/connection-target")
                .tls(tls)
                .build();

        try {
            String first = responseBody(client.get());
            tls.reload(TlsMaterial.builder().trustAll(true).build());
            String reloaded = responseBody(client.get());

            assertThat(connectionId(reloaded), not(is(connectionId(first))));
        } finally {
            client.closeResource();
        }
    }

    private static String responseBody(Http2ClientRequest request) {
        try (Http2ClientResponse response = request.request()) {
            return response.as(String.class);
        }
    }

    private static String host(String responseBody) {
        return responseBody.substring(0, responseBody.indexOf('|'));
    }

    private static String connectionId(String responseBody) {
        return responseBody.substring(responseBody.lastIndexOf('|') + 1);
    }

    private static Http2Client syntheticBeforeExpectClient() {
        return Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .sendExpectContinue(true)
                .readContinueTimeout(Duration.ofMillis(250))
                .readTimeout(Duration.ofSeconds(5))
                .followCrossOriginEntityRedirects(true)
                .dnsResolver((_, _) -> InetAddress.ofLiteral("127.0.0.1"))
                .protocolConfig(protocol -> protocol.priorKnowledge(true))
                .baseUri("http://source.example:" + plainPort)
                .addService((chain, request) -> {
                    if (request.uri().toUri().getPath().equals("/synthetic-before-expect")) {
                        WritableHeaders<?> headers = WritableHeaders.create();
                        headers.add(HeaderNames.LOCATION, "/output-stream-expect/start");
                        return WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(() -> { })
                                .status(Status.TEMPORARY_REDIRECT_307)
                                .headers(ClientResponseHeaders.create(headers))
                                .build();
                    }
                    return chain.proceed(request);
                })
                .build();
    }

    private static final class CountingCookieStore implements CookieStore {
        private final CookieStore delegate = new CookieManager().getCookieStore();
        private final AtomicInteger additions = new AtomicInteger();

        @Override
        public void add(URI uri, HttpCookie cookie) {
            additions.incrementAndGet();
            delegate.add(uri, cookie);
        }

        @Override
        public List<HttpCookie> get(URI uri) {
            return delegate.get(uri);
        }

        @Override
        public List<HttpCookie> getCookies() {
            return delegate.getCookies();
        }

        @Override
        public List<URI> getURIs() {
            return delegate.getURIs();
        }

        @Override
        public boolean remove(URI uri, HttpCookie cookie) {
            return delegate.remove(uri, cookie);
        }

        @Override
        public boolean removeAll() {
            return delegate.removeAll();
        }

        private int additions() {
            return additions.get();
        }
    }

    private static final class RecordingTransportService
            implements WebClientService, WebClientTransportObserverProvider {
        private final AtomicInteger registrationOpens = new AtomicInteger();
        private final AtomicInteger registrationCloses = new AtomicInteger();
        private final AtomicInteger completionRequests = new AtomicInteger();
        private final List<RecordingConnection> connections = new CopyOnWriteArrayList<>();
        private volatile boolean connectionClosedBeforeRegistration;

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            return chain.proceed(request);
        }

        @Override
        public Registration openTransportObserver() {
            registrationOpens.incrementAndGet();
            return new Registration() {
                @Override
                public HttpTransportObserver observer() {
                    return RecordingTransportService.this::connectionOpened;
                }

                @Override
                public void close() {
                    connectionClosedBeforeRegistration = !connections.isEmpty()
                            && connections.stream().allMatch(it -> !it.outcomes.isEmpty());
                    registrationCloses.incrementAndGet();
                }

                @Override
                public CompletionStage<Void> completion() {
                    completionRequests.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
            };
        }

        private ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
            RecordingConnection connection = new RecordingConnection(role, transport, handshake);
            connections.add(connection);
            return connection;
        }
    }

    private static final class RecordingConnection implements ConnectionObservation {
        private final Role role;
        private final String transport;
        private final Handshake handshake;
        private final List<String> protocols = new CopyOnWriteArrayList<>();
        private final List<RecordingStream> streams = new CopyOnWriteArrayList<>();
        private final List<ConnectionOutcome> outcomes = new CopyOnWriteArrayList<>();

        private RecordingConnection(Role role, String transport, Handshake handshake) {
            this.role = role;
            this.transport = transport;
            this.handshake = handshake;
        }

        @Override
        public HandshakeObservation handshakeStarted() {
            return HandshakeObservation.noop();
        }

        @Override
        public void protocolSelected(String protocol) {
            protocols.add(protocol);
        }

        @Override
        public StreamObservation streamOpened(Direction direction, Initiator initiator) {
            RecordingStream stream = new RecordingStream(direction, initiator);
            streams.add(stream);
            return stream;
        }

        @Override
        public void close(ConnectionOutcome outcome) {
            outcomes.add(outcome);
        }
    }

    private static final class RecordingStream implements StreamObservation {
        private final Direction direction;
        private final Initiator initiator;
        private final AtomicReference<StreamOutcome> outcome = new AtomicReference<>();
        private final AtomicInteger closeCount = new AtomicInteger();

        private RecordingStream(Direction direction, Initiator initiator) {
            this.direction = direction;
            this.initiator = initiator;
        }

        @Override
        public void close(StreamOutcome outcome) {
            closeCount.incrementAndGet();
            this.outcome.compareAndSet(null, outcome);
        }
    }
}
