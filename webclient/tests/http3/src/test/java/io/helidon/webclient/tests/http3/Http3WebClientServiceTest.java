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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.common.tls.Tls;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http3.Http3RawTestServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictWebClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3WebClientServiceTest {
    private static final String PROXY_HOST = "localhost";
    private static final HeaderName SERVICE_HEADER = HeaderNames.create("x-client-service");
    private static final String SERVICE_HEADER_VALUE = "from-service";
    private static final HeaderName FALLBACK_TRAILER = HeaderNames.create("x-fallback-trailer");
    private static final String FALLBACK_TRAILER_VALUE = "fallback-value";
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration EARLY_COMPLETION_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofMillis(250);
    private static final String SERVER_KEYSTORE = "server.p12";
    private static final String CLIENT_TRUSTSTORE = "client.p12";
    private static final char[] KEY_PASSWORD = "password".toCharArray();

    private HttpProxy httpProxy;
    private int proxyPort;
    private TestEnvironment environment;
    private CountDownLatch earlyResponseDispatched;
    private AtomicInteger serviceRequestCount;

    @BeforeEach
    void beforeEach() throws Exception {
        earlyResponseDispatched = new CountDownLatch(1);
        serviceRequestCount = new AtomicInteger();
        environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/service", (req, res) -> {
                    serviceRequestCount.incrementAndGet();
                    String value = req.headers().first(SERVICE_HEADER).orElse(null);
                    if (SERVICE_HEADER_VALUE.equals(value)) {
                        res.send(value);
                    } else {
                        res.status(Status.BAD_REQUEST_400).send();
                    }
                })
                .get("/fragment", (req, res) -> res.send(req.prologue().fragment().hasValue()
                                                                ? req.prologue().fragment().rawValue()
                                                                : "no-fragment"))
                .post("/when-sent", (req, res) -> {
                    try (InputStream inputStream = req.content().inputStream();
                         OutputStream outputStream = res.outputStream()) {
                        byte[] firstByte = inputStream.readNBytes(1);
                        if (firstByte.length != 1) {
                            throw new IllegalStateException("Expected the first request byte before responding.");
                        }
                        outputStream.write(firstByte);
                        outputStream.flush();
                        earlyResponseDispatched.countDown();
                        inputStream.transferTo(outputStream);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));
        httpProxy = new HttpProxy(0);
        httpProxy.start();
        proxyPort = httpProxy.connectedPort();
    }

    @AfterEach
    void afterEach() {
        if (httpProxy != null) {
            httpProxy.stop();
        }
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void shouldInvokeServiceOnDirectTypedHttp3Request() {
        ProtocolTrackingService service = new ProtocolTrackingService();
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .addService(service)
                .build();

        try {
            try (Http3ClientResponse response = client.get("/service").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(SERVICE_HEADER_VALUE));
            }
        } finally {
            client.closeResource();
        }

        assertThat(service.responseProtocolId(), is(Http3Client.PROTOCOL_ID));
        assertThat(service.requestProtocolId(), is(Http3Client.PROTOCOL_ID));
        assertThat(service.whenSentProtocolId(), is(Http3Client.PROTOCOL_ID));
        assertThat(service.whenCompleteProtocolId(), is(Http3Client.PROTOCOL_ID));
        assertThat(httpProxy.counter(), is(0));
    }

    @Test
    void shouldUpdateRequestHeadersAfterDispatch() {
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .addService(new ProtocolTrackingService())
                .build();

        try {
            var request = client.get("/service");
            try (Http3ClientResponse response = request.request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(SERVICE_HEADER_VALUE));
            }

            assertThat(request.headers().first(SERVICE_HEADER).orElse(null), is(SERVICE_HEADER_VALUE));
            assertThat(request.headers().contentLength(), is(OptionalLong.of(0)));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldNotSendUnencodedFragment() {
        String fragment = "super fragment#&?/";
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .build();

        try {
            try (Http3ClientResponse response = client.get("/fragment")
                    .skipUriEncoding(true)
                    .fragment(fragment)
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), is("no-fragment"));
                assertThat(response.lastEndpointUri().fragment().value(), is(fragment));
            }
        } finally {
            client.closeResource();
        }
    }

    @Test
    void shouldReturnShortCircuitServiceResponseWithoutTransportSelection() {
        WebClientService service = (chain, request) -> WebClientServiceResponse.builder()
                .serviceRequest(request)
                .whenComplete(new CompletableFuture<>())
                .connection(() -> { })
                .status(Status.ACCEPTED_202)
                .headers(ClientResponseHeaders.create(WritableHeaders.create()))
                .build();
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .addService(service)
                .build();

        try {
            try (Http3ClientResponse response = client.get("/service").request()) {
                assertThat(response.status(), is(Status.ACCEPTED_202));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            }
        } finally {
            client.closeResource();
        }

        assertThat(serviceRequestCount.get(), is(0));
    }

    @Test
    void shouldFallbackToServiceRewrittenPlaintextEndpoint() {
        AtomicInteger targetRequests = new AtomicInteger();
        WebServer target = WebServer.builder()
                .port(0)
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.create())
                .protocolsDiscoverServices(false)
                .addProtocol(Http1Config.create())
                .routing(routing -> routing.get("/service", (req, res) -> {
                    targetRequests.incrementAndGet();
                    res.send(req.headers().first(SERVICE_HEADER).orElse("missing"));
                }))
                .build()
                .start();
        URI targetUri = URI.create("http://localhost:" + target.port() + "/service");
        WebClientService service = (chain, request) -> {
            request.uri().resolve(targetUri);
            request.headers().set(HeaderValues.create(SERVICE_HEADER, SERVICE_HEADER_VALUE));
            return chain.proceed(request);
        };
        Http3Client client = Http3Client.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(Proxy.noProxy())
                .tls(environment.clientTlsHttp3())
                .addService(service)
                .build();

        try {
            try (Http3ClientResponse response = client.get("/service").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(SERVICE_HEADER_VALUE));
                assertThat(response.lastEndpointUri().toUri(), is(targetUri));
            }
        } finally {
            client.closeResource();
            target.stop();
        }

        assertThat(targetRequests.get(), is(1));
        assertThat(serviceRequestCount.get(), is(0));
    }

    @Test
    void shouldPublishFallbackWhenSentBeforeDelayedResponseHead() throws Exception {
        byte[] payload = "fallback request body".getBytes(StandardCharsets.UTF_8);
        CountDownLatch bodyReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        LifecycleTrackingService service = new LifecycleTrackingService();
        WebServer target = WebServer.builder()
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.create())
                .protocolsDiscoverServices(false)
                .addProtocol(Http1Config.create())
                .routing(routing -> routing.post("/delayed-response", (request, response) -> {
                    assertThat(Arrays.equals(request.content().as(byte[].class), payload), is(true));
                    bodyReceived.countDown();
                    try {
                        if (!releaseResponse.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                            throw new IllegalStateException("Timed out waiting to send the fallback response.");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while delaying the fallback response.", e);
                    }
                    response.send("fallback-ok");
                }))
                .build()
                .start();
        Http3Client client = Http3Client.builder()
                .baseUri("http://localhost:" + target.port())
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .proxy(Proxy.noProxy())
                .addService(service)
                .build();

        try {
            CompletableFuture<String> responseBody = CompletableFuture.supplyAsync(() -> {
                try (Http3ClientResponse response = client.post("/delayed-response").submit(payload)) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    return response.as(String.class);
                }
            });

            assertThat(bodyReceived.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
            assertThat(service.sent.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).protocolId(),
                       is(Http1Client.PROTOCOL_ID));
            assertThat("submit must still await the delayed response head", responseBody.isDone(), is(false));
            assertThat("whenComplete must still await the response", service.completed.isDone(), is(false));

            releaseResponse.countDown();
            assertThat(responseBody.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is("fallback-ok"));
            assertThat(service.completed.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                               .serviceRequest()
                               .protocolId(),
                       is(Http1Client.PROTOCOL_ID));
        } finally {
            releaseResponse.countDown();
            client.closeResource();
            target.stop();
        }
    }

    @Test
    void shouldKeepFallbackWhenSentSuccessfulAfterResponseHeadFailure() throws Exception {
        byte[] payload = "fallback request body".getBytes(StandardCharsets.UTF_8);
        CountDownLatch bodyReceived = new CountDownLatch(1);
        CountDownLatch closeConnection = new CountDownLatch(1);
        LifecycleTrackingService service = new LifecycleTrackingService();

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
                try (Socket socket = serverSocket.accept()) {
                    InputStream inputStream = socket.getInputStream();
                    ByteArrayOutputStream head = new ByteArrayOutputStream();
                    int matched = 0;
                    while (matched < 4) {
                        int next = inputStream.read();
                        if (next < 0) {
                            throw new IllegalStateException("Connection closed before the request head was complete.");
                        }
                        head.write(next);
                        matched = switch (matched) {
                            case 0 -> next == '\r' ? 1 : 0;
                            case 1 -> next == '\n' ? 2 : next == '\r' ? 1 : 0;
                            case 2 -> next == '\r' ? 3 : 0;
                            case 3 -> next == '\n' ? 4 : 0;
                            default -> throw new IllegalStateException("Unexpected delimiter state.");
                        };
                    }

                    int contentLength = -1;
                    for (String line : head.toString(StandardCharsets.ISO_8859_1).split("\\r\\n")) {
                        if (line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
                            contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
                            break;
                        }
                    }
                    if (contentLength < 0) {
                        throw new IllegalStateException("Fallback request did not contain Content-Length.");
                    }
                    byte[] received = inputStream.readNBytes(contentLength);
                    if (!Arrays.equals(received, payload)) {
                        throw new IllegalStateException("Unexpected fallback request body.");
                    }
                    bodyReceived.countDown();
                    if (!closeConnection.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                        throw new IllegalStateException("Timed out waiting to close the fallback connection.");
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed while serving the fallback request.", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while serving the fallback request.", e);
                }
            });
            Http3Client client = Http3Client.builder()
                    .baseUri("http://localhost:" + serverSocket.getLocalPort())
                    .shareConnectionCache(false)
                    .servicesDiscoverServices(false)
                    .proxy(Proxy.noProxy())
                    .addService(service)
                    .build();

            try {
                CompletableFuture<String> responseBody = CompletableFuture.supplyAsync(() -> {
                    try (Http3ClientResponse response = client.post("/late-failure").submit(payload)) {
                        return response.as(String.class);
                    }
                });

                assertThat(bodyReceived.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThat(service.sent.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).protocolId(),
                           is(Http1Client.PROTOCOL_ID));
                assertThat("submit must still await the response head", responseBody.isDone(), is(false));
                assertThat("whenComplete must still await the response", service.completed.isDone(), is(false));

                closeConnection.countDown();
                assertThrows(ExecutionException.class,
                             () -> responseBody.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                assertThrows(ExecutionException.class,
                             () -> service.completed.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                serverTask.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(service.sent.isCompletedExceptionally(), is(false));
            } finally {
                closeConnection.countDown();
                client.closeResource();
            }
        }
    }

    @Test
    void shouldPublishFallbackWhenSentOnlyAfterStreamingBodyCloses() throws Exception {
        byte[] firstChunk = "first chunk".getBytes(StandardCharsets.UTF_8);
        byte[] remainder = " and remainder".getBytes(StandardCharsets.UTF_8);
        byte[] expected = "first chunk and remainder".getBytes(StandardCharsets.UTF_8);
        CountDownLatch producerBlocked = new CountDownLatch(1);
        CountDownLatch releaseProducer = new CountDownLatch(1);
        CountDownLatch bodyReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        LifecycleTrackingService service = new LifecycleTrackingService();
        WebServer target = WebServer.builder()
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.create())
                .protocolsDiscoverServices(false)
                .addProtocol(Http1Config.create())
                .routing(routing -> routing.post("/streaming-fallback", (request, response) -> {
                    assertThat(Arrays.equals(request.content().as(byte[].class), expected), is(true));
                    bodyReceived.countDown();
                    try {
                        if (!releaseResponse.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                            throw new IllegalStateException("Timed out waiting to send the streaming response.");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while delaying the streaming response.", e);
                    }
                    response.send("streaming-ok");
                }))
                .build()
                .start();
        Http3Client client = Http3Client.builder()
                .baseUri("http://localhost:" + target.port())
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .proxy(Proxy.noProxy())
                .addService(service)
                .build();

        try {
            CompletableFuture<String> responseBody = CompletableFuture.supplyAsync(() -> {
                try (Http3ClientResponse response = client.post("/streaming-fallback")
                        .outputStream(outputStream -> {
                            try (outputStream) {
                                outputStream.write(firstChunk);
                                outputStream.flush();
                                producerBlocked.countDown();
                                try {
                                    if (!releaseProducer.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                                        throw new IllegalStateException(
                                                "Timed out waiting to finish the request body.");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException("Interrupted while streaming the request body.", e);
                                }
                                outputStream.write(remainder);
                            }
                        })) {
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    return response.as(String.class);
                }
            });

            assertThat(producerBlocked.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
            assertThat("whenSent must remain incomplete before the streaming body closes",
                       service.sent.isDone(),
                       is(false));

            releaseProducer.countDown();
            assertThat(bodyReceived.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
            assertThat(service.sent.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).protocolId(),
                       is(Http1Client.PROTOCOL_ID));
            assertThat(responseBody.isDone(), is(false));
            assertThat(service.completed.isDone(), is(false));

            releaseResponse.countDown();
            assertThat(responseBody.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is("streaming-ok"));
        } finally {
            releaseProducer.countDown();
            releaseResponse.countDown();
            client.closeResource();
            target.stop();
        }
    }

    @Test
    void shouldFailFallbackWhenSentWhenStreamingBodyFails() throws Exception {
        LifecycleTrackingService service = new LifecycleTrackingService();
        WebServer target = WebServer.builder()
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .bindingsDiscoverServices(false)
                .addBinding(TcpTransportConfig.create())
                .protocolsDiscoverServices(false)
                .addProtocol(Http1Config.create())
                .routing(routing -> routing.post("/streaming-failure", (request, response) -> {
                    request.content().as(byte[].class);
                    response.send("unexpected");
                }))
                .build()
                .start();
        Http3Client client = Http3Client.builder()
                .baseUri("http://localhost:" + target.port())
                .shareConnectionCache(false)
                .servicesDiscoverServices(false)
                .proxy(Proxy.noProxy())
                .addService(service)
                .build();

        try {
            assertThrows(RuntimeException.class,
                         () -> client.post("/streaming-failure").outputStream(outputStream -> {
                             outputStream.write("partial".getBytes(StandardCharsets.UTF_8));
                             throw new IOException("simulated streaming body failure");
                         }));
            assertThrows(ExecutionException.class,
                         () -> service.sent.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThrows(ExecutionException.class,
                         () -> service.completed.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            assertThat(service.sent.isCompletedExceptionally(), is(true));
        } finally {
            client.closeResource();
            target.stop();
        }
    }

    @Test
    void shouldInvokeServiceOnFallbackExplicitHttp3Request() {
        ProtocolTrackingService service = new ProtocolTrackingService();
        WebClient client = WebClient.builder()
                .baseUri(environment.baseUri())
                .shareConnectionCache(false)
                .proxy(proxy())
                .tls(environment.clientTls())
                .addService(service)
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(Http3ClientProtocolConfig.create())
                .build();

        try {
            try (HttpClientResponse response = client.get("/service")
                    .protocolId(Http3Client.PROTOCOL_ID)
                    .request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(SERVICE_HEADER_VALUE));
            }
        } finally {
            client.closeResource();
        }

        assertThat(service.responseProtocolId(), is(Http1Client.PROTOCOL_ID));
        assertThat(service.requestProtocolId(), is(Http3Client.PROTOCOL_ID));
        assertThat(service.whenSentProtocolId(), is(Http1Client.PROTOCOL_ID));
        assertThat(service.whenCompleteProtocolId(), is(Http1Client.PROTOCOL_ID));
        assertThat(httpProxy.counter(), is(1));
    }

    @Test
    void shouldExposePendingProtocolBeforeGenericSelection() {
        ProtocolTrackingService service = new ProtocolTrackingService();
        WebClient client = strictWebClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .addService(service)
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .build();

        try {
            try (HttpClientResponse response = client.get("/service").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                assertThat(response.entity().as(String.class), is(SERVICE_HEADER_VALUE));
            }
        } finally {
            client.closeResource();
        }

        assertThat(service.requestProtocolId(), is("any"));
        assertThat(service.responseProtocolId(), is(Http3Client.PROTOCOL_ID));
        assertThat(service.whenSentProtocolId(), is(Http3Client.PROTOCOL_ID));
        assertThat(service.whenCompleteProtocolId(), is(Http3Client.PROTOCOL_ID));
    }

    @Test
    void shouldExposeTrailersThroughDecoratedTransportFailureFallback() throws Exception {
        List<CompletableFuture<ClientResponseTrailers>> rawTrailers = new CopyOnWriteArrayList<>();
        WebClientService service = (chain, request) -> {
            request.headers().set(HeaderValues.create(SERVICE_HEADER, SERVICE_HEADER_VALUE));
            WebClientServiceResponse response = chain.proceed(request);
            assertThat(response.trailers().isCompletedExceptionally(), is(false));
            rawTrailers.add(response.trailers());
            return WebClientServiceResponse.builder(response)
                    .trailers(response.trailers().thenApply(trailers -> trailers))
                    .build();
        };
        try (Http1OnlyTlsEnvironment http1OnlyEnvironment = Http1OnlyTlsEnvironment.create()) {
            WebClient webClient = WebClient.builder()
                    .baseUri(http1OnlyEnvironment.baseUri())
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .tls(http1OnlyEnvironment.clientTls())
                    .altSvc(ClientAltSvcConfig.create())
                    .addService(service)
                    .addProtocolPreference(Http3Client.PROTOCOL_ID)
                    .addProtocolPreference(Http1Client.PROTOCOL_ID)
                    .addProtocolConfig(Http3ClientProtocolConfig.builder()
                                               .initialResponseTimeout(HANDSHAKE_TIMEOUT)
                                               .handshakeTimeout(HANDSHAKE_TIMEOUT)
                                               .build())
                    .build();

            try {
                try (HttpClientResponse response = webClient.get("/service").request()) {
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.headers().contains(HeaderNames.TRAILER), is(true));
                    assertThat(response.entity().as(String.class), is(SERVICE_HEADER_VALUE));
                    assertThat(response.trailers().get(FALLBACK_TRAILER).get(), is(FALLBACK_TRAILER_VALUE));
                    assertThat(rawTrailers.get(0).get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                                       .get(FALLBACK_TRAILER).get(),
                               is(FALLBACK_TRAILER_VALUE));
                    assertThat(rawTrailers.size(), is(1));
                }
                try (HttpClientResponse response = webClient.get("/service").request()) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.headers().contains(HeaderNames.TRAILER), is(true));
                    assertThat(response.entity().as(String.class), is(SERVICE_HEADER_VALUE));
                    assertThat(rawTrailers.size(), is(2));
                    assertThat(rawTrailers.get(1).get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                                       .get(FALLBACK_TRAILER).get(),
                               is(FALLBACK_TRAILER_VALUE));
                    assertThat(response.trailers().get(FALLBACK_TRAILER).get(), is(FALLBACK_TRAILER_VALUE));
                }
            } finally {
                webClient.closeResource();
            }

            assertThat(http1OnlyEnvironment.http1GetCount(), is(2));
        }
    }

    @Test
    void shouldPublishRawFallbackAltSvcBeforeStandaloneServiceUnwinds() throws Exception {
        int http3Port = URI.create(environment.baseUri()).getPort();
        try (Http1OnlyTlsEnvironment http1OnlyEnvironment = Http1OnlyTlsEnvironment.create(http3Port)) {
            URI fallbackUri = URI.create(http1OnlyEnvironment.baseUri() + "/service");
            AtomicBoolean outerRequest = new AtomicBoolean(true);
            AtomicReference<Http3Client> clientRef = new AtomicReference<>();
            AtomicReference<String> nestedProtocol = new AtomicReference<>();
            WebClientService service = (chain, request) -> {
                request.uri().resolve(fallbackUri);
                request.headers().set(HeaderValues.create(SERVICE_HEADER, SERVICE_HEADER_VALUE));
                boolean issueNestedRequest = outerRequest.compareAndSet(true, false);
                WebClientServiceResponse response = chain.proceed(request);
                if (issueNestedRequest) {
                    try (Http3ClientResponse nested = clientRef.get().get("/service").request()) {
                        nestedProtocol.set(nested.protocolId());
                        assertThat(nested.entity().as(String.class), is(SERVICE_HEADER_VALUE));
                    }
                }
                WritableHeaders<?> visibleHeaders = WritableHeaders.create(response.headers());
                visibleHeaders.remove(HeaderNames.ALT_SVC);
                return WebClientServiceResponse.builder(response)
                        .headers(ClientResponseHeaders.create(visibleHeaders))
                        .build();
            };
            Http3Client client = Http3Client.builder()
                    .baseUri(environment.baseUri())
                    .shareConnectionCache(false)
                    .servicesDiscoverServices(false)
                    .proxy(Proxy.noProxy())
                    .tls(http1OnlyEnvironment.clientTls())
                    .altSvc(ClientAltSvcConfig.create())
                    .addService(service)
                    .build();
            clientRef.set(client);

            try {
                try (Http3ClientResponse response = client.get("/service").request()) {
                    assertThat(response.protocolId(), is(Http1Client.PROTOCOL_ID));
                    assertThat(response.headers().contains(HeaderNames.ALT_SVC), is(false));
                    assertThat(response.entity().as(String.class), is(SERVICE_HEADER_VALUE));
                }
                assertThat(nestedProtocol.get(), is(Http3Client.PROTOCOL_ID));
                assertThat(http1OnlyEnvironment.http1GetCount(), is(1));
                assertThat(serviceRequestCount.get(), is(1));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldCompleteWhenSentOnlyAfterStreamingRequestFinIsDispatched() throws Exception {
        byte[] firstChunk = "first chunk".getBytes(StandardCharsets.UTF_8);
        byte[] remainder = " and remainder".getBytes(StandardCharsets.UTF_8);
        String expectedBody = new String(firstChunk, StandardCharsets.UTF_8)
                + new String(remainder, StandardCharsets.UTF_8);
        CountDownLatch producerBlocked = new CountDownLatch(1);
        CountDownLatch remainderAllowed = new CountDownLatch(1);
        WhenSentTrackingService service = new WhenSentTrackingService();
        Http3Client client = strictClientBuilder()
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .addService(service)
                .build();

        try {
            CompletableFuture<String> responseBody = CompletableFuture.supplyAsync(() -> {
                try (Http3ClientResponse response = client.post("/when-sent").outputStream(outputStream -> {
                    try (outputStream) {
                        outputStream.write(firstChunk);
                        outputStream.flush();
                        producerBlocked.countDown();
                        try {
                            if (!remainderAllowed.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                                throw new IllegalStateException("Timed out waiting to send the request remainder.");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while waiting to send the request remainder.", e);
                        }
                        outputStream.write(remainder);
                    }
                })) {
                    assertThat(response.status(), is(Status.OK_200));
                    assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
                    return response.entity().as(String.class);
                }
            });

            assertThat("The request producer should block before dispatching the remainder and FIN",
                       producerBlocked.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                       is(true));
            assertThat("The server should dispatch a normal response head while the upload remains active",
                       earlyResponseDispatched.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                       is(true));
            assertThat("whenSent must remain incomplete after the response head but before request FIN",
                       service.sentObserved.await(EARLY_COMPLETION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                       is(false));

            remainderAllowed.countDown();

            assertThat(responseBody.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(expectedBody));
            assertThat(service.sent.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).protocolId(),
                       is(Http3Client.PROTOCOL_ID));
        } finally {
            remainderAllowed.countDown();
            client.closeResource();
        }
    }

    @Test
    void shouldPublishWhenSentAfterCompleteHttp3Attempt() throws Exception {
        byte[] payload = "request payload".getBytes(StandardCharsets.UTF_8);
        AtomicInteger attemptCount = new AtomicInteger();
        CountDownLatch firstAttemptRejected = new CountDownLatch(1);
        CountDownLatch secondAttemptReceived = new CountDownLatch(1);
        CountDownLatch allowSecondResponse = new CountDownLatch(1);
        WhenSentTrackingService service = new WhenSentTrackingService();

        try (Http3RawTestServer server = Http3RawTestServer.create((_, _, _, stream) -> {
                 try (InputStream inputStream = stream.requestBodyInputStream()) {
                     byte[] received = inputStream.readAllBytes();
                     if (!Arrays.equals(received, payload)) {
                         throw new IllegalStateException("Unexpected request body on HTTP/3 attempt.");
                     }
                 } catch (IOException e) {
                     throw new UncheckedIOException("Failed to read the HTTP/3 request body.", e);
                 }

                 if (attemptCount.incrementAndGet() == 1) {
                     stream.reset(Http3ErrorCode.REQUEST_REJECTED.code());
                     firstAttemptRejected.countDown();
                     return null;
                 }

                 secondAttemptReceived.countDown();
                 try {
                     if (!allowSecondResponse.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                         throw new IllegalStateException("Timed out waiting to complete the selected HTTP/3 attempt.");
                     }
                 } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                     throw new IllegalStateException("Interrupted while waiting to complete the HTTP/3 attempt.", e);
                 }
                 return Http3RawTestServer.text(Status.OK_200.code(), "retry-ok");
             })) {
            Http3Client client = strictClientBuilder()
                    .baseUri(server.baseUri())
                    .tls(server.clientTlsHttp3())
                    .addService(service)
                    .build();

            try {
                CompletableFuture<String> responseBody = CompletableFuture.supplyAsync(() -> {
                    try (Http3ClientResponse response = client.post("/retry").submit(payload)) {
                        assertThat(response.status(), is(Status.OK_200));
                        return response.as(String.class);
                    }
                });

                assertThat(firstAttemptRejected.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThat(secondAttemptReceived.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is(true));
                assertThat("whenSent must publish after the first attempt has sent all request bytes",
                           service.sentObserved.await(EARLY_COMPLETION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                           is(true));

                allowSecondResponse.countDown();
                assertThat(responseBody.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), is("retry-ok"));
                assertThat(service.sent.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).protocolId(),
                           is(Http3Client.PROTOCOL_ID));
                assertThat(attemptCount.get(), is(2));
            } finally {
                allowSecondResponse.countDown();
                client.closeResource();
            }
        }
    }

    private Proxy proxy() {
        return Proxy.builder()
                .type(Proxy.ProxyType.HTTP)
                .host(PROXY_HOST)
                .port(proxyPort)
                .build();
    }

    private static final class ProtocolTrackingService implements WebClientService {
        private final List<String> requestProtocolIds = new CopyOnWriteArrayList<>();
        private final List<String> whenSentProtocolIds = new CopyOnWriteArrayList<>();
        private final List<String> whenCompleteProtocolIds = new CopyOnWriteArrayList<>();
        private final List<String> responseProtocolIds = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Throwable> callbackFailure = new CompletableFuture<>();

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            requestProtocolIds.add(request.protocolId());
            request.headers().set(HeaderValues.create(SERVICE_HEADER, SERVICE_HEADER_VALUE));
            request.whenSent()
                    .thenApply(WebClientServiceRequest::protocolId)
                    .whenComplete((protocolId, throwable) -> complete(whenSentProtocolIds, protocolId, throwable));
            request.whenComplete()
                    .thenApply(response -> response.serviceRequest().protocolId())
                    .whenComplete((protocolId, throwable) -> complete(whenCompleteProtocolIds, protocolId, throwable));

            WebClientServiceResponse response = chain.proceed(request);
            responseProtocolIds.add(response.serviceRequest().protocolId());
            return response;
        }

        private String responseProtocolId() {
            return responseProtocolIds(1).get(0);
        }

        private String requestProtocolId() {
            return requestProtocolIds(1).get(0);
        }

        private String whenSentProtocolId() {
            return whenSentProtocolIds(1).get(0);
        }

        private String whenCompleteProtocolId() {
            return whenCompleteProtocolIds(1).get(0);
        }

        private List<String> responseProtocolIds(int expected) {
            return await(responseProtocolIds, expected, "service response protocol ids");
        }

        private List<String> requestProtocolIds(int expected) {
            return await(requestProtocolIds, expected, "service request protocol ids");
        }

        private List<String> whenSentProtocolIds(int expected) {
            return await(whenSentProtocolIds, expected, "service whenSent protocol ids");
        }

        private List<String> whenCompleteProtocolIds(int expected) {
            return await(whenCompleteProtocolIds, expected, "service whenComplete protocol ids");
        }

        private void complete(List<String> target, String value, Throwable throwable) {
            if (throwable == null) {
                target.add(value);
            } else {
                callbackFailure.complete(throwable);
            }
        }

        private static <T> T await(CompletableFuture<T> stage) {
            try {
                return stage.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to await service callback.", e);
            }
        }

        private List<String> await(List<String> values, int expected, String description) {
            long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
            while (values.size() < expected && System.nanoTime() < deadline) {
                if (callbackFailure.isDone()) {
                    throw new IllegalStateException("Failed while awaiting " + description + ".",
                                                    await(callbackFailure));
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while awaiting " + description + ".", e);
                }
            }
            if (values.size() < expected) {
                throw new IllegalStateException("Timed out waiting for " + description + ".");
            }
            return List.copyOf(values);
        }
    }

    private static final class WhenSentTrackingService implements WebClientService {
        private final CompletableFuture<WebClientServiceRequest> sent = new CompletableFuture<>();
        private final CountDownLatch sentObserved = new CountDownLatch(1);

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            request.whenSent().whenComplete((serviceRequest, throwable) -> {
                if (throwable == null) {
                    sent.complete(serviceRequest);
                } else {
                    sent.completeExceptionally(throwable);
                }
                sentObserved.countDown();
            });
            return chain.proceed(request);
        }
    }

    private static final class LifecycleTrackingService implements WebClientService {
        private final CompletableFuture<WebClientServiceRequest> sent = new CompletableFuture<>();
        private final CompletableFuture<WebClientServiceResponse> completed = new CompletableFuture<>();

        @Override
        public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
            request.whenSent().whenComplete((serviceRequest, throwable) -> {
                if (throwable == null) {
                    sent.complete(serviceRequest);
                } else {
                    sent.completeExceptionally(throwable);
                }
            });
            request.whenComplete().whenComplete((serviceResponse, throwable) -> {
                if (throwable == null) {
                    completed.complete(serviceResponse);
                } else {
                    completed.completeExceptionally(throwable);
                }
            });
            return chain.proceed(request);
        }
    }

    private static final class Http1OnlyTlsEnvironment implements AutoCloseable {
        private final WebServer server;
        private final Http3TlsSupport.Http3TlsMaterials tlsMaterials;
        private final AtomicInteger getCount;

        private Http1OnlyTlsEnvironment(WebServer server, Http3TlsSupport.Http3TlsMaterials tlsMaterials, AtomicInteger getCount) {
            this.server = server;
            this.tlsMaterials = tlsMaterials;
            this.getCount = getCount;
        }

        private static Http1OnlyTlsEnvironment create() throws Exception {
            return create(-1);
        }

        private static Http1OnlyTlsEnvironment create(int advertisedHttp3Port) throws Exception {
            Http3TlsSupport.Http3TlsMaterials tlsMaterials = Http3TlsSupport.load();
            AtomicInteger getCount = new AtomicInteger();

            WebServer server = WebServer.builder()
                    .port(0)
                    .bindingsDiscoverServices(false)
                    .addBinding(TcpTransportConfig.create())
                    .protocolsDiscoverServices(false)
                    .tls(tlsMaterials.serverTls())
                    .addProtocol(Http1Config.create())
                    .routing(routing -> routing.get("/service", (req, res) -> {
                        getCount.incrementAndGet();
                        String value = req.headers().first(SERVICE_HEADER).orElse(null);
                        if (SERVICE_HEADER_VALUE.equals(value)) {
                            int alternativePort = advertisedHttp3Port > 0
                                    ? advertisedHttp3Port
                                    : req.localPeer().port();
                            res.header(HeaderNames.ALT_SVC,
                                       "h3=\":" + alternativePort + "\"; ma=60");
                            res.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                            res.header(HeaderNames.TRAILER, FALLBACK_TRAILER.defaultCase());
                            res.trailers().add(HeaderValues.create(FALLBACK_TRAILER, FALLBACK_TRAILER_VALUE));
                            res.send(value);
                        } else {
                            res.status(Status.BAD_REQUEST_400).send();
                        }
                    }))
                    .build()
                    .start();

            return new Http1OnlyTlsEnvironment(server, tlsMaterials, getCount);
        }

        private String baseUri() {
            return "https://localhost:" + server.port();
        }

        private Tls clientTls() {
            return tlsMaterials.clientTls();
        }

        private int http1GetCount() {
            return getCount.get();
        }

        @Override
        public void close() {
            server.stop();
        }
    }
}
