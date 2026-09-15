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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.common.Size;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.configurable.Resource;
import io.helidon.common.socket.SocketContext;
import io.helidon.common.tls.Tls;
import io.helidon.common.pki.Keys;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3ControlStreamListener;
import io.helidon.http.http3.Http3ControlStreamSupport;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3GoAway;
import io.helidon.http.http3.Http3MessageReader;
import io.helidon.http.http3.Http3PeerCriticalStreams;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3Settings;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.http.http3.Http3StreamType;
import io.helidon.http.sse.SseEvent;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.quic.QuicClientConnection;
import io.helidon.quic.QuicClientRuntime;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamException;
import io.helidon.quic.stream.QuicStreamReader;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.AltSvc;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.quic.QuicTransportConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.metrics.MetricsObserver;
import io.helidon.webserver.sse.SseSink;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static io.helidon.webserver.http3.Http3ClientTestSupport.strictClientBuilder;
import static io.helidon.webserver.http3.RawHttp3TestServer.requestBodyInputStream;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3WebServerIT {
    private static final char[] KEY_PASSWORD = "changeit".toCharArray();
    private static final String SERVER_KEYSTORE = "io/helidon/webserver/http3/server-keystore.p12";
    private static final String CLIENT_TRUSTSTORE = "io/helidon/webserver/http3/client-truststore.p12";
    // Match the receiver minimum so STREAM_DATA_BLOCKED does not expand the window until the application reads.
    private static final int NON_READING_STREAM_CREDIT = 16_384;
    private static final HeaderName PREMATURE_BODY_NAME = HeaderNames.create("x-premature-body");
    private static final Header BEFORE_TRAILER_HEADER = HeaderValues.create("before-trailer", "before-value");
    private static final Header TEST_TRAILER_HEADER = HeaderValues.create("test-trailer", "trailer-value");
    private static final Header STREAM_RESULT_OK = HeaderValues.create("stream-result", "OK");
    private static final Header STREAM_RESULT_KABOOM = HeaderValues.create("stream-result", "Kaboom!");

    @Test
    void shouldRouteHelidonHttp3Client() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create()) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse getResponse = client.get(environment.uri("/hello").toString()).request();
                 Http3ClientResponse postResponse = client.post(environment.uri("/echo").toString())
                         .header(HeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8")
                         .submit("payload")) {
                assertThat(getResponse.status().code(), equalTo(200));
                assertThat(getResponse.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(getResponse.as(String.class), equalTo("GET /hello"));

                assertThat(postResponse.status().code(), equalTo(200));
                assertThat(postResponse.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(postResponse.as(String.class), equalTo("payload"));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRouteJdkHttp3Client() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpRequest request = HttpRequest.newBuilder(environment.uri("/hello"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.version(), equalTo(HTTP_3));
            assertThat(response.body(), equalTo("GET /hello"));
        }
    }

    @Test
    void shouldAcceptLargeJdkHttp3RequestHeaders() throws Exception {
        HeaderName largeHeader = HeaderNames.create("x-large");
        String largeValue = "x".repeat(4_096);
        try (TestEnvironment environment = TestEnvironment.create(Http3Config.builder()
                                                                     .maxFieldSectionSize(16_384)
                                                                     .buildPrototype(),
                                                                 routing -> routing.get("/large-headers", (req, res) ->
                res.send(Integer.toString(req.headers().first(largeHeader).orElseThrow().length()))));
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpRequest.Builder request = HttpRequest.newBuilder(environment.uri("/large-headers"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET()
                    .header(largeHeader.defaultCase(), largeValue);
            for (int i = 0; i < 16; i++) {
                request.header("x-fill-" + i, "y".repeat(256));
            }

            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.version(), equalTo(HTTP_3));
            assertThat(response.body(), equalTo(Integer.toString(largeValue.length())));
        }
    }

    @Test
    void shouldResetRequestStreamWhenRequestHeadersExceedConfiguredMaxHeadersSize() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(Http3Config.builder()
                                                                     .maxHeadersSize(128)
                                                                     .buildPrototype(),
                                                                 routing -> routing.get("/hello", (req, res) -> res.send("hello")));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();

            requestStream.writer()
                    .scheduleForWriting(BufferData.create(rawHeadersFrame(List.of(
                            HeaderValues.create(HeaderNames.createFromLowercase(":method"), "GET"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":scheme"), "https"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":authority"), "localhost"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":path"), "/hello"),
                            HeaderValues.create("x-big", "x".repeat(200))))),
                                        true);

            assertThat(waitFor(() -> requestStream.stream().rcvErrorCode() == Http3ErrorCode.MESSAGE_ERROR.code(),
                               Duration.ofSeconds(10)),
                       equalTo(true));
        }
    }

    @Test
    void shouldRejectMalformedRequestValuesWhenHeaderValidationIsDisabled() throws Exception {
        AtomicBoolean routed = new AtomicBoolean();
        Http3Config config = Http3Config.builder()
                .validateRequestHeaders(false)
                .validateResponseHeaders(false)
                .buildPrototype();
        try (TestEnvironment environment = TestEnvironment.create(config,
                                                                   routing -> routing
                                                                           .get("/alive", (req, res) -> res.send("alive"))
                                                                           .get("/malformed", (req, res) -> {
                                                                               routed.set(true);
                                                                               res.send();
                                                                           }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            requestStream.writer()
                    .scheduleForWriting(BufferData.create(rawHeadersFrame(List.of(
                            HeaderValues.create(HeaderNames.createFromLowercase(":method"), "GET"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":scheme"), "https"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":authority"), "localhost"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":path"), "/malformed"),
                            HeaderValues.create("x-malformed", "before\nafter")))),
                                        true);

            assertThat("Malformed request field must reset its stream with H3_MESSAGE_ERROR",
                       waitFor(() -> requestStream.stream().rcvErrorCode() == Http3ErrorCode.MESSAGE_ERROR.code(),
                               Duration.ofSeconds(10)),
                       equalTo(true));
            assertThat(routed.get(), equalTo(false));
            DecodedResponse followUp = client.get(environment.uri("/alive"));
            assertThat(followUp.status(), equalTo(200));
            assertThat(new String(followUp.body(), StandardCharsets.UTF_8), equalTo("alive"));
        }
    }

    @Test
    void shouldRejectMalformedResponseValuesWhenHeaderValidationIsDisabled() throws Exception {
        try (RawTestEnvironment environment = RawTestEnvironment.create(serverStream -> {
                 serverStream.writeResponseHeaders(200,
                                                   headers(HeaderValues.create("x-malformed", "before\nafter")),
                                                   true);
                 return Optional.empty();
             })) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .protocolConfig(Http3ClientProtocolConfig.builder()
                                            .priorKnowledge(true)
                                            .validateRequestHeaders(false)
                                            .validateResponseHeaders(false)
                                            .build())
                    .build();
            try {
                RuntimeException failure = assertThrows(RuntimeException.class, () -> {
                    try (Http3ClientResponse response = client.get(environment.uri("/malformed").toString()).request()) {
                        response.as(String.class);
                    }
                });

                Http3ProtocolException protocolFailure = Http3ProtocolException.find(failure)
                        .orElseThrow(() -> new AssertionError("Missing local HTTP/3 response validation failure", failure));
                assertThat(protocolFailure.errorCode(), equalTo(Http3ErrorCode.MESSAGE_ERROR));
                assertThat(protocolFailure.scope(), equalTo(Http3ProtocolException.Scope.STREAM));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRejectGeneratingMalformedRequestValuesWhenHeaderValidationIsDisabled() throws Exception {
        AtomicBoolean routed = new AtomicBoolean();
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.get("/malformed", (req, res) -> {
                 routed.set(true);
                 res.send();
             }))) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .protocolConfig(Http3ClientProtocolConfig.builder()
                                            .priorKnowledge(true)
                                            .validateRequestHeaders(false)
                                            .validateResponseHeaders(false)
                                            .build())
                    .build();
            try {
                RuntimeException failure = assertThrows(RuntimeException.class, () -> {
                    try (Http3ClientResponse response = client.get(environment.uri("/malformed").toString())
                            .header(HeaderValues.create("x-malformed", "before\nafter"))
                            .request()) {
                        response.as(String.class);
                    }
                });

                Throwable validationFailure = failure;
                while (validationFailure.getCause() != null) {
                    validationFailure = validationFailure.getCause();
                }
                assertInstanceOf(IllegalArgumentException.class, validationFailure);
                assertThat(validationFailure.getMessage(),
                           containsString("header value is invalid for header 'x-malformed'"));
                assertThat(routed.get(), equalTo(false));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRejectGeneratingMalformedResponseValuesWhenHeaderValidationIsDisabled() throws Exception {
        CompletableFuture<IllegalArgumentException> rejection = new CompletableFuture<>();
        AtomicBoolean sentAtRejection = new AtomicBoolean();
        Http3Config config = Http3Config.builder()
                .validateRequestHeaders(false)
                .validateResponseHeaders(false)
                .buildPrototype();
        try (TestEnvironment environment = TestEnvironment.create(config,
                                                                   routing -> routing.get("/malformed", (req, res) -> {
                                                                       res.header(HeaderValues.create("x-malformed",
                                                                                                     "before\nafter"));
                                                                       try {
                                                                           res.send();
                                                                           rejection.completeExceptionally(new AssertionError(
                                                                                   "Malformed response field was written"));
                                                                       } catch (IllegalArgumentException failure) {
                                                                           sentAtRejection.set(res.isSent());
                                                                           rejection.complete(failure);
                                                                           throw failure;
                                                                       }
                                                                   }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            requestStream.writer().scheduleForWriting(BufferData.create(client.encodeRequestHeaders(requestStream,
                                                                                                    environment.uri("/malformed"),
                                                                                                    "GET",
                                                                                                    headers())),
                                                      true);

            IllegalArgumentException failure = rejection.get(10, TimeUnit.SECONDS);
            assertThat(failure.getMessage(), containsString("header value is invalid for header 'x-malformed'"));
            assertThat(sentAtRejection.get(), equalTo(false));
        }
    }

    @Test
    void shouldNotAcceptHttp1OnUdpOnlyListener() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_1_1)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(1))
                     .build()) {
            HttpRequest request = HttpRequest.newBuilder(environment.uri("/hello"))
                    .version(HTTP_1_1)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            assertThrows(IOException.class, () -> client.send(request, HttpResponse.BodyHandlers.ofString()));
        }
    }

    @Test
    void shouldStreamJdkHttp3ClientResponse() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.get("/stream", (req, res) -> {
            try (OutputStream outputStream = res.outputStream()) {
                outputStream.write("part-1".getBytes(StandardCharsets.UTF_8));
                     outputStream.write("/".getBytes(StandardCharsets.UTF_8));
                     outputStream.write("part-2".getBytes(StandardCharsets.UTF_8));
                 } catch (IOException e) {
                     throw new UncheckedIOException(e);
                 }
             }));
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpRequest request = HttpRequest.newBuilder(environment.uri("/stream"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.version(), equalTo(HTTP_3));
            assertThat(response.body(), equalTo("part-1/part-2"));
        }
    }

    @Test
    void shouldStreamLargeHelidonHttp3ClientResponse() throws Exception {
        String chunk = "0123456789abcdef".repeat(256);
        int repetitions = 64;
        String expected = chunk.repeat(repetitions);

        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.get("/stream-large", (req, res) -> {
                 try (OutputStream outputStream = res.outputStream()) {
                     byte[] chunkBytes = chunk.getBytes(StandardCharsets.UTF_8);
                     for (int i = 0; i < repetitions; i++) {
                         outputStream.write(chunkBytes);
                     }
                 } catch (IOException e) {
                     throw new UncheckedIOException(e);
                 }
             }))) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse response = client.get(environment.uri("/stream-large").toString()).request()) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), equalTo(expected));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldStreamHelidonHttp3ClientResponseBeforeEof() throws Exception {
        CountDownLatch firstChunkWritten = new CountDownLatch(1);
        CountDownLatch continueResponse = new CountDownLatch(1);

        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.get("/stream-client", (req, res) -> {
                 res.header(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                 try (OutputStream outputStream = res.outputStream()) {
                     outputStream.write("part-1".getBytes(StandardCharsets.UTF_8));
                     outputStream.flush();
                     firstChunkWritten.countDown();
                     if (!continueResponse.await(5, TimeUnit.SECONDS)) {
                         throw new IllegalStateException("Timed out waiting for client to consume streamed HTTP/3 response.");
                     }
                     outputStream.write("/part-2".getBytes(StandardCharsets.UTF_8));
                     res.trailers().add(TEST_TRAILER_HEADER);
                 } catch (IOException e) {
                     throw new UncheckedIOException(e);
                 } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                     throw new IllegalStateException("Interrupted while waiting for streamed HTTP/3 response consumption.", e);
                 }
             }))) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse response = client.get(environment.uri("/stream-client").toString()).request()) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(firstChunkWritten.await(5, TimeUnit.SECONDS), equalTo(true));

                InputStream inputStream = response.inputStream();
                assertThat(new String(inputStream.readNBytes(6), StandardCharsets.UTF_8), equalTo("part-1"));

                continueResponse.countDown();

                assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8), equalTo("/part-2"));
                assertThat(response.trailers().contains(TEST_TRAILER_HEADER), equalTo(true));
            } finally {
                continueResponse.countDown();
                client.closeResource();
            }
        }
    }

    @Test
    void shouldContinueServingAfterClientCancelsStreamingResponse() throws Exception {
        CountDownLatch firstChunkWritten = new CountDownLatch(1);
        CountDownLatch continueResponse = new CountDownLatch(1);
        CountDownLatch handlerCompleted = new CountDownLatch(1);

        try (TestEnvironment environment = TestEnvironment.create(routing -> routing
                .get("/cancel-response", (req, res) -> {
                    try (OutputStream outputStream = res.outputStream()) {
                        outputStream.write("part-1".getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        firstChunkWritten.countDown();
                        if (!continueResponse.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to cancel streamed HTTP/3 response.");
                        }
                        outputStream.write("/part-2".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting to cancel streamed HTTP/3 response.", e);
                    } finally {
                        handlerCompleted.countDown();
                    }
                })
                .get("/hello", (req, res) -> res.send("hello")));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream cancelledRequest = client.openRequestStream();
            Http3MessageReader reader = client.responseReader(cancelledRequest, Method.GET);
            cancelledRequest.writer()
                    .scheduleForWriting(BufferData.create(client.encodeRequestHeaders(cancelledRequest,
                                                                                    environment.uri("/cancel-response"),
                                                                                    "GET",
                                                                                    headers())),
                                        true);

            Http3MessageReader.ResponseHead responseHead = reader.readResponseHead(_ -> {
            });
            assertThat(responseHead.status().code(), equalTo(200));
            assertThat(firstChunkWritten.await(5, TimeUnit.SECONDS), equalTo(true));
            assertThat(new String(reader.readEntityDataWithTrailers(32), StandardCharsets.UTF_8), equalTo("part-1"));

            cancelledRequest.stream().requestStopSending(Http3ErrorCode.REQUEST_CANCELLED.code());
            reader.close();
            continueResponse.countDown();

            assertThat(handlerCompleted.await(5, TimeUnit.SECONDS), equalTo(true));

            LowLevelHttp3Client.RequestStream secondRequest = client.openRequestStream();
            CompletableFuture<byte[]> secondResponseFuture = readAll(secondRequest.stream());
            secondRequest.writer()
                    .scheduleForWriting(BufferData.create(client.encodeRequestHeaders(secondRequest,
                                                                                    environment.uri("/hello"),
                                                                                    "GET",
                                                                                    headers())),
                                        true);

            DecodedResponse secondResponse = client.decodeResponse(secondRequest, secondResponseFuture.get(10, TimeUnit.SECONDS));
            assertThat(secondResponse.status(), equalTo(200));
            assertThat(new String(secondResponse.body(), StandardCharsets.UTF_8), equalTo("hello"));
        } finally {
            continueResponse.countDown();
        }
    }

    @Test
    void shouldBoundSlowResponseAndAllowOtherStreamsToProgress() throws Exception {
        int dispatchWindowSize = 64;
        int initialStreamCredit = NON_READING_STREAM_CREDIT;
        int responseSize = 65_536;
        byte[] expectedBody = new byte[responseSize];
        byte[] responseChunk = new byte[dispatchWindowSize];
        AtomicInteger acceptedBodyBytes = new AtomicInteger();
        CountDownLatch slowHandlerCompleted = new CountDownLatch(1);
        Http3Config http3Config = Http3Config.builder()
                .responseDispatchWindowSize(dispatchWindowSize)
                .buildPrototype();
        QuicConfig quicConfig = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .initialMaxStreamData(initialStreamCredit)
                .buildPrototype();

        try (TestEnvironment environment = TestEnvironment.create(http3Config, routing -> routing
                .get("/slow-window", (req, res) -> {
                    try (OutputStream outputStream = res.outputStream()) {
                        for (int i = 0; i < responseSize / responseChunk.length; i++) {
                            outputStream.write(responseChunk);
                            acceptedBodyBytes.addAndGet(responseChunk.length);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } finally {
                        slowHandlerCompleted.countDown();
                    }
                })
                .get("/fast-window", (req, res) -> res.send("fast")));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment, quicConfig)) {
            LowLevelHttp3Client.RequestStream slowRequest = client.openRequestStream();
            slowRequest.writer()
                    .scheduleForWriting(BufferData.create(client.encodeRequestHeaders(slowRequest,
                                                                                    environment.uri("/slow-window"),
                                                                                    "GET",
                                                                                    headers())),
                                        true);

            assertThat(waitFor(() -> acceptedBodyBytes.get() >= initialStreamCredit / 2,
                               Duration.ofSeconds(10)),
                       equalTo(true));
            assertThat(waitFor(() -> slowHandlerCompleted.getCount() == 0
                                       || acceptedBodyBytes.get() > initialStreamCredit + dispatchWindowSize,
                               Duration.ofSeconds(2)),
                       equalTo(false));
            assertThat(acceptedBodyBytes.get(), lessThanOrEqualTo(initialStreamCredit + dispatchWindowSize));

            LowLevelHttp3Client.RequestStream fastRequest = client.openRequestStream();
            Http3MessageReader fastReader = client.responseReader(fastRequest, Method.GET);
            fastRequest.writer()
                    .scheduleForWriting(BufferData.create(client.encodeRequestHeaders(fastRequest,
                                                                                    environment.uri("/fast-window"),
                                                                                    "GET",
                                                                                    headers())),
                                        true);

            assertThat(fastReader.readResponseHead(_ -> {
            }).status().code(), equalTo(200));
            assertThat(new String(readMessageBody(fastReader), StandardCharsets.UTF_8), equalTo("fast"));

            Http3MessageReader slowReader = client.responseReader(slowRequest, Method.GET);
            assertThat(slowReader.readResponseHead(_ -> {
            }).status().code(), equalTo(200));
            assertArrayEquals(expectedBody, readMessageBody(slowReader));
            assertThat(slowHandlerCompleted.await(5, TimeUnit.SECONDS), equalTo(true));
        }
    }

    @Test
    void shouldBoundBufferedResponseUntilClientReads() throws Exception {
        int initialStreamCredit = NON_READING_STREAM_CREDIT;
        byte[] expectedBody = new byte[65_536];
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch handlerCompleted = new CountDownLatch(1);
        AtomicInteger dataFrameCount = new AtomicInteger();
        AtomicLong largestDataFrame = new AtomicLong();
        Http3Config http3Config = Http3Config.builder()
                .responseDispatchWindowSize(4_096)
                .buildPrototype();
        QuicConfig quicConfig = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .initialMaxStreamData(initialStreamCredit)
                .buildPrototype();

        try (TestEnvironment environment = TestEnvironment.create(http3Config, routing -> routing
                .get("/buffered-window", (req, res) -> {
                    handlerStarted.countDown();
                    try {
                        res.send(expectedBody);
                    } finally {
                        handlerCompleted.countDown();
                    }
                }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment, quicConfig)) {
            LowLevelHttp3Client.RequestStream request = client.openRequestStream();
            request.writer()
                    .scheduleForWriting(BufferData.create(client.encodeRequestHeaders(request,
                                                                                    environment.uri("/buffered-window"),
                                                                                    "GET",
                                                                                    headers())),
                                        true);

            assertThat(handlerStarted.await(5, TimeUnit.SECONDS), equalTo(true));
            assertThat(handlerCompleted.await(2, TimeUnit.SECONDS), equalTo(false));
            assertThat(request.stream().dataReceived(), lessThanOrEqualTo((long) initialStreamCredit));

            Http3MessageReader reader = client.responseReader(request, Method.GET, new Http3FrameListener() {
                @Override
                public void frameHeader(SocketContext context,
                                        long streamId,
                                        long frameType,
                                        long frameLength,
                                        int encodedLength) {
                    if (frameType == Http3Protocol.FRAME_DATA) {
                        dataFrameCount.incrementAndGet();
                        largestDataFrame.accumulateAndGet(frameLength, Math::max);
                    }
                }
            });
            assertThat(reader.readResponseHead(_ -> {
            }).status().code(), equalTo(200));
            assertArrayEquals(expectedBody, readMessageBody(reader));
            assertThat(handlerCompleted.await(5, TimeUnit.SECONDS), equalTo(true));
            assertThat(dataFrameCount.get(), greaterThan(1));
            assertThat(largestDataFrame.get(), lessThanOrEqualTo(4_096L));
        }
    }

    @Test
    void shouldUnblockBackpressuredResponseWhenClientStopsReceiving() throws Exception {
        int dispatchWindowSize = 64;
        int initialStreamCredit = NON_READING_STREAM_CREDIT;
        int responseSize = 65_536;
        byte[] responseChunk = new byte[dispatchWindowSize];
        AtomicInteger acceptedBodyBytes = new AtomicInteger();
        CountDownLatch handlerCompleted = new CountDownLatch(1);
        Http3Config http3Config = Http3Config.builder()
                .responseDispatchWindowSize(dispatchWindowSize)
                .buildPrototype();
        QuicConfig quicConfig = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .initialMaxStreamData(initialStreamCredit)
                .buildPrototype();

        try (TestEnvironment environment = TestEnvironment.create(http3Config, routing -> routing
                .get("/cancel-window", (req, res) -> {
                    try (OutputStream outputStream = res.outputStream()) {
                        for (int i = 0; i < responseSize / responseChunk.length; i++) {
                            outputStream.write(responseChunk);
                            acceptedBodyBytes.addAndGet(responseChunk.length);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } finally {
                        handlerCompleted.countDown();
                    }
                }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment, quicConfig)) {
            LowLevelHttp3Client.RequestStream request = client.openRequestStream();
            request.writer()
                    .scheduleForWriting(BufferData.create(client.encodeRequestHeaders(request,
                                                                                    environment.uri("/cancel-window"),
                                                                                    "GET",
                                                                                    headers())),
                                        true);

            assertThat(waitFor(() -> acceptedBodyBytes.get() >= initialStreamCredit / 2,
                               Duration.ofSeconds(10)),
                       equalTo(true));
            assertThat(waitFor(() -> handlerCompleted.getCount() == 0
                                       || acceptedBodyBytes.get() > initialStreamCredit + dispatchWindowSize,
                               Duration.ofSeconds(2)),
                       equalTo(false));

            request.stream().requestStopSending(Http3ErrorCode.REQUEST_CANCELLED.code());

            assertThat(handlerCompleted.await(5, TimeUnit.SECONDS), equalTo(true));
            assertThat(acceptedBodyBytes.get(), lessThan(responseSize));
        }
    }

    @Test
    void shouldStreamHelidonHttp3SseSinkResponseBeforeEof() throws Exception {
        CountDownLatch firstEventWritten = new CountDownLatch(1);
        CountDownLatch continueResponse = new CountDownLatch(1);

        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.get("/sse", (req, res) -> {
                 try (SseSink sink = res.sink(SseSink.TYPE)) {
                     sink.emit(SseEvent.create("hello"));
                     firstEventWritten.countDown();
                     if (!continueResponse.await(5, TimeUnit.SECONDS)) {
                         throw new IllegalStateException("Timed out waiting for client to consume streamed HTTP/3 SSE response.");
                     }
                     sink.emit(SseEvent.create("world"));
                 } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                     throw new IllegalStateException("Interrupted while waiting for streamed HTTP/3 SSE response consumption.", e);
                 }
             }))) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();
            try (Http3ClientResponse response = client.get(environment.uri("/sse").toString())
                    .header(HeaderNames.ACCEPT, "text/event-stream")
                    .request()) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(response.headers().get(HeaderNames.CONTENT_TYPE).get(), equalTo("text/event-stream"));
                assertThat(firstEventWritten.await(5, TimeUnit.SECONDS), equalTo(true));

                InputStream inputStream = response.inputStream();
                assertThat(new String(inputStream.readNBytes("data:hello\n\n".length()), StandardCharsets.UTF_8),
                           equalTo("data:hello\n\n"));

                continueResponse.countDown();

                assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8),
                           equalTo("data:world\n\n"));
            } finally {
                continueResponse.countDown();
                client.closeResource();
            }
        }
    }

    @Test
    void shouldCloseHttp3SseOutputWrapperBeforeResponseCommit() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> {
                 routing.get("/sse-filter-close", (req, res) -> {
                     res.streamFilter(output -> new FilterOutputStream(output) {
                         @Override
                         public void close() throws IOException {
                             write("data:close\n\n".getBytes(StandardCharsets.UTF_8));
                             super.close();
                         }
                     });
                     try (SseSink sink = res.sink(SseSink.TYPE)) {
                         sink.emit(SseEvent.create("hello"));
                     }
                 });
             });
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            DecodedResponse response = client.get(environment.uri("/sse-filter-close"));

            assertThat(response.status(), equalTo(200));
            assertThat(new String(response.body(), StandardCharsets.UTF_8),
                       equalTo("data:hello\n\ndata:close\n\n"));
        }
    }

    @Test
    void shouldRespondBeforeHttp3RequestEof() throws Exception {
        CountDownLatch routeRead = new CountDownLatch(1);
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.post("/stream-request", (req, res) -> {
                 try {
                     InputStream inputStream = req.content().inputStream();
                     byte[] prefix = inputStream.readNBytes(4);
                     routeRead.countDown();
                     res.send(new String(prefix, StandardCharsets.UTF_8));
                 } catch (IOException e) {
                     throw new UncheckedIOException(e);
                 }
             }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());
            client.sendHeaders(requestStream, environment.uri("/stream-request"), 8);
            client.sendData(requestStream, "ping".getBytes(StandardCharsets.UTF_8), false);

            assertThat(routeRead.await(5, TimeUnit.SECONDS), equalTo(true));
            DecodedResponse response = client.decodeResponse(requestStream, responseFuture.get(10, TimeUnit.SECONDS));
            assertThat(response.status(), equalTo(200));
            assertThat(new String(response.body(), StandardCharsets.UTF_8), equalTo("ping"));

            client.sendData(requestStream, "tail".getBytes(StandardCharsets.UTF_8), true);
        }
    }

    @Test
    void shouldContinueServingAfterClientCancelsRemainingRequestBody() throws Exception {
        CountDownLatch routeRead = new CountDownLatch(1);
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing
                .post("/stream-request", (req, res) -> {
                    try {
                        InputStream inputStream = req.content().inputStream();
                        byte[] prefix = inputStream.readNBytes(4);
                        routeRead.countDown();
                        res.send(new String(prefix, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .post("/echo", (req, res) -> res.send(req.content().as(String.class))));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream firstRequest = client.openRequestStream();
            CompletableFuture<byte[]> firstResponseFuture = readAll(firstRequest.stream());
            client.sendHeaders(firstRequest, environment.uri("/stream-request"), 8);
            client.sendData(firstRequest, "ping".getBytes(StandardCharsets.UTF_8), false);

            assertThat(routeRead.await(5, TimeUnit.SECONDS), equalTo(true));
            DecodedResponse firstResponse = client.decodeResponse(firstRequest, firstResponseFuture.get(10, TimeUnit.SECONDS));
            assertThat(firstResponse.status(), equalTo(200));
            assertThat(new String(firstResponse.body(), StandardCharsets.UTF_8), equalTo("ping"));

            firstRequest.writer().reset(Http3ErrorCode.REQUEST_CANCELLED.code());

            LowLevelHttp3Client.RequestStream secondRequest = client.openRequestStream();
            CompletableFuture<byte[]> secondResponseFuture = readAll(secondRequest.stream());
            client.sendHeaders(secondRequest, environment.uri("/echo"), 2);
            client.sendData(secondRequest, "ok".getBytes(StandardCharsets.UTF_8), true);

            DecodedResponse secondResponse = client.decodeResponse(secondRequest, secondResponseFuture.get(10, TimeUnit.SECONDS));
            assertThat(secondResponse.status(), equalTo(200));
            assertThat(new String(secondResponse.body(), StandardCharsets.UTF_8), equalTo("ok"));
        }
    }

    @Test
    void shouldNotRespond500WhenClientCancelsWhileHandlerReadsRequestBody() throws Exception {
        CountDownLatch routeReading = new CountDownLatch(1);
        CountDownLatch routeCancelled = new CountDownLatch(1);
        AtomicReference<RuntimeException> routeFailure = new AtomicReference<>();
        try (TestEnvironment environment = TestEnvironment.create(Http3Config.create(), 1, routing -> routing
                .post("/blocked", (req, res) -> {
                    routeReading.countDown();
                    try {
                        res.send(req.content().as(String.class));
                    } catch (RuntimeException e) {
                        routeFailure.set(e);
                        routeCancelled.countDown();
                        throw e;
                    }
                })
                .post("/echo", (req, res) -> res.send(req.content().as(String.class))));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream cancelledRequest = client.openRequestStream();
            CompletableFuture<byte[]> cancelledResponse = readAll(cancelledRequest.stream());
            client.sendHeaders(cancelledRequest, environment.uri("/blocked"), 8);
            client.sendData(cancelledRequest, "ping".getBytes(StandardCharsets.UTF_8), false);

            assertThat(routeReading.await(5, TimeUnit.SECONDS), equalTo(true));
            cancelledRequest.writer().reset(Http3ErrorCode.REQUEST_CANCELLED.code());
            assertThat(routeCancelled.await(5, TimeUnit.SECONDS), equalTo(true));
            assertThat(routeFailure.get().getClass(), equalTo(QuicStreamException.class));
            ExecutionException cancellation = assertThrows(
                    ExecutionException.class,
                    () -> cancelledResponse.get(5, TimeUnit.SECONDS));
            assertThat(cancellation.getCause().getClass(), equalTo(QuicStreamException.class));

            LowLevelHttp3Client.RequestStream secondRequest = client.openRequestStream();
            CompletableFuture<byte[]> secondResponse = readAll(secondRequest.stream());
            client.sendHeaders(secondRequest, environment.uri("/echo"), 2);
            client.sendData(secondRequest, "ok".getBytes(StandardCharsets.UTF_8), true);

            DecodedResponse response = client.decodeResponse(secondRequest, secondResponse.get(10, TimeUnit.SECONDS));
            assertThat(response.status(), equalTo(200));
            assertThat(new String(response.body(), StandardCharsets.UTF_8), equalTo("ok"));
        }
    }

    @Test
    void shouldReuseDynamicQpackAcrossRepeatedResponsesOnSameConnection() throws Exception {
        HeaderName qpackUser = HeaderNames.create("x-qpack-user");
        HeaderName qpackEnv = HeaderNames.create("x-qpack-env");
        HeaderName qpackCluster = HeaderNames.create("x-qpack-cluster");
        String userValue = "alpha-user-1234567890";
        String envValue = "dev-eu-central-1";
        String clusterValue = "shared-http3-connection";
        Headers repeatedResponseHeaders = headers(HeaderValues.create(qpackUser, userValue),
                                                  HeaderValues.create(qpackEnv, envValue),
                                                  HeaderValues.create(qpackCluster, clusterValue));

        try (TestEnvironment environment = TestEnvironment.create(routing -> routing
                .get("/hello", (req, res) -> res.send("hello"))
                .get("/dynamic-qpack", (req, res) -> {
                    repeatedResponseHeaders.forEach(header -> res.headers().add(header));
                    res.send("dynamic");
                }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            DecodedResponse warmupResponse = client.get(environment.uri("/hello"));
            DecodedResponse firstResponse = client.get(environment.uri("/dynamic-qpack"));
            DecodedResponse secondResponse = client.get(environment.uri("/dynamic-qpack"));
            DecodedResponse thirdResponse = client.get(environment.uri("/dynamic-qpack"));

            assertThat(warmupResponse.status(), equalTo(200));
            assertThat(new String(warmupResponse.body(), StandardCharsets.UTF_8), equalTo("hello"));

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
    void shouldSend100ContinueBeforeHttp3RequestBody() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.post("/expect-continue", (req, res) -> {
                 assertThat(req.headers().contains(HeaderValues.EXPECT_100), equalTo(true));
                 res.send(req.content().as(String.class));
             }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            Http3MessageReader reader = client.responseReader(requestStream, Method.POST);

            client.sendHeaders(requestStream,
                               environment.uri("/expect-continue"),
                               4,
                               headers(HeaderValues.create(HeaderNames.EXPECT, "100-continue")));

            Http3MessageReader.ResponseHead finalResponse = reader.readResponseHead(informational -> {
                assertThat(informational.status().code(), equalTo(100));
                client.sendData(requestStream, "ping".getBytes(StandardCharsets.UTF_8), true);
            });
            assertThat(finalResponse.status().code(), equalTo(200));
            assertThat(new String(readMessageBody(reader), StandardCharsets.UTF_8), equalTo("ping"));
        }
    }

    @Test
    void shouldUseReadableEntityBufferForStreamedHttp3Request() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.post("/buffered-echo", (req, res) -> {
                 req.content().buffer();
                 String firstRead = req.content().as(String.class);
                 try {
                     String secondRead = new String(req.content().inputStream().readAllBytes(), StandardCharsets.UTF_8);
                     res.send(firstRead + "/" + secondRead);
                 } catch (IOException e) {
                     throw new UncheckedIOException(e);
                 }
             }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());

            client.sendHeaders(requestStream, environment.uri("/buffered-echo"), 7);
            client.sendData(requestStream, "payload".getBytes(StandardCharsets.UTF_8), false);
            client.sendTrailers(requestStream,
                                headers(HeaderValues.create("test-trailer", "trailer-value")),
                                true);

            DecodedResponse response = client.decodeResponse(requestStream, responseFuture.get(10, TimeUnit.SECONDS));
            assertThat(response.status(), equalTo(200));
            assertThat(new String(response.body(), StandardCharsets.UTF_8), equalTo("payload/payload"));
        }
    }

    @Test
    void shouldApplyHttp3BufferedEntityLimit() throws Exception {
        Http3Config config = Http3Config.builder()
                .maxBufferedEntitySize(Size.create(4, Size.Unit.BYTE))
                .buildPrototype();
        try (TestEnvironment environment = TestEnvironment.create(config,
                                                                   routing -> routing
                                                                           .error(IllegalStateException.class,
                                                                                  (req, res, throwable) -> res.status(413)
                                                                                          .send())
                                                                           .post("/buffer-limit", (req, res) -> {
                                                                               req.content().buffer();
                                                                               res.send(req.content().as(String.class));
                                                                           }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());

            client.sendHeaders(requestStream, environment.uri("/buffer-limit"), 7);
            client.sendData(requestStream, "payload".getBytes(StandardCharsets.UTF_8), true);

            DecodedResponse response = client.decodeResponse(requestStream, responseFuture.get(10, TimeUnit.SECONDS));
            assertThat(response.status(), equalTo(413));
        }
    }

    @Test
    void shouldResetInvalidRequestTargetsAndKeepConnectionOpen() throws Exception {
        AtomicBoolean routed = new AtomicBoolean();
        Http3Config config = Http3Config.builder()
                .validatePath(true)
                .buildPrototype();
        try (TestEnvironment environment = TestEnvironment.create(config,
                                                                   routing -> routing
                                                                           .get("/alive", (req, res) -> res.send("alive"))
                                                                           .any((req, res) -> {
                                                                               routed.set(true);
                                                                               res.send();
                                                                           }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            for (RequestTarget requestTarget : List.of(new RequestTarget("GET", "boards/"),
                                                       new RequestTarget("GET", "?q=1"),
                                                       new RequestTarget("GET", "/ok?q=%GG"),
                                                       new RequestTarget("GET", "http://example/a"),
                                                       new RequestTarget("GET", "*"),
                                                       new RequestTarget("OPTIONS", "*?q=1"),
                                                       new RequestTarget("GET", "/boards/#fragment"),
                                                       new RequestTarget("GET", "/invalid[path"))) {
                LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
                requestStream.writer()
                        .scheduleForWriting(BufferData.create(rawRequestHeadersFrame(requestTarget.method(),
                                                                                     requestTarget.target())),
                                            true);

                assertThat(requestTarget.toString(),
                           waitFor(() -> requestStream.stream().rcvErrorCode() == Http3ErrorCode.MESSAGE_ERROR.code(),
                                   Duration.ofSeconds(10)),
                           equalTo(true));
                DecodedResponse followUp = client.get(environment.uri("/alive"));
                assertThat(followUp.status(), equalTo(200));
                assertThat(new String(followUp.body(), StandardCharsets.UTF_8), equalTo("alive"));
            }
            assertThat(routed.get(), equalTo(false));
        }
    }

    @Test
    void shouldRouteOptionsAsteriskRequestTarget() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.any((req, res) -> {
                 var path = req.prologue().uriPath();
                 res.send(path.rawPath() + '|' + path.path() + '|' + path.absolute().path());
             }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());
            requestStream.writer()
                    .scheduleForWriting(BufferData.create(rawRequestHeadersFrame("OPTIONS", "*")), true);

            DecodedResponse response = client.decodeResponse(requestStream, responseFuture.get(10, TimeUnit.SECONDS));
            assertThat(response.status(), equalTo(200));
            assertThat(new String(response.body(), StandardCharsets.UTF_8), equalTo("*|*|/"));
        }
    }

    @Test
    void shouldReturnNotImplementedForClassicConnectAndKeepConnectionOpen() throws Exception {
        AtomicBoolean routed = new AtomicBoolean();
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing
                .get("/alive", (req, res) -> res.send("alive"))
                .any((req, res) -> {
                    routed.set(true);
                    res.send();
                }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            DecodedResponse connect = client.request(environment.uri("/"), "CONNECT", headers());

            assertThat(connect.status(), equalTo(501));
            assertThat(routed.get(), equalTo(false));

            LowLevelHttp3Client.RequestStream zeroPort = client.openRequestStream();
            zeroPort.writer()
                    .scheduleForWriting(BufferData.create(rawConnectHeadersFrame("localhost:0")), true);
            assertThat(waitFor(() -> zeroPort.stream().rcvErrorCode() == Http3ErrorCode.MESSAGE_ERROR.code(),
                               Duration.ofSeconds(10)),
                       equalTo(true));

            DecodedResponse followUp = client.get(environment.uri("/alive"));
            assertThat(followUp.status(), equalTo(200));
            assertThat(new String(followUp.body(), StandardCharsets.UTF_8), equalTo("alive"));
        }
    }

    @Test
    void shouldRouteInvalidPathWhenValidationIsDisabled() throws Exception {
        Http3Config config = Http3Config.builder()
                .validatePath(false)
                .buildPrototype();
        try (TestEnvironment environment = TestEnvironment.create(config,
                                                                   routing -> routing.any((req, res) ->
                                                                           res.send(req.prologue()
                                                                                            .uriPath()
                                                                                            .rawPath())));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());

            requestStream.writer()
                    .scheduleForWriting(BufferData.create(rawRequestHeadersFrame("/invalid[path")), true);

            DecodedResponse response = client.decodeResponse(requestStream, responseFuture.get(10, TimeUnit.SECONDS));
            assertThat(response.status(), equalTo(200));
            assertThat(new String(response.body(), StandardCharsets.UTF_8), equalTo("/invalid[path"));
        }
    }

    @Test
    void shouldRouteRelativeRequestTargetWhenValidationIsDisabled() throws Exception {
        Http3Config config = Http3Config.builder()
                .validatePath(false)
                .buildPrototype();
        try (TestEnvironment environment = TestEnvironment.create(config,
                                                                   routing -> routing.any((req, res) ->
                                                                           res.send(req.prologue()
                                                                                            .uriPath()
                                                                                            .rawPath())));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());

            requestStream.writer()
                    .scheduleForWriting(BufferData.create(rawRequestHeadersFrame("relative[target")), true);

            DecodedResponse response = client.decodeResponse(requestStream, responseFuture.get(10, TimeUnit.SECONDS));
            assertThat(response.status(), equalTo(200));
            assertThat(new String(response.body(), StandardCharsets.UTF_8), equalTo("relative[target"));
        }
    }

    @Test
    void shouldRejectHttp3RequestWhenContentLengthDoesNotMatchStreamedEntity() throws Exception {
        AtomicBoolean applicationErrorHandlerInvoked = new AtomicBoolean();
        try (TestEnvironment environment = TestEnvironment.create(Http3Config.create(),
                                                                  1,
                                                                  routing -> {
                                                                      routing.error(Throwable.class,
                                                                                    (req, res, throwable) -> {
                                                                                        applicationErrorHandlerInvoked.set(
                                                                                                true);
                                                                                        res.status(500).send();
                                                                                    });
                                                                      routing.post("/content-length-mismatch", (req, res) -> {
                                                                          res.send(req.content().as(String.class));
                                                                      });
                                                                      routing.get("/alive", (req, res) -> res.send("alive"));
                                                                  });
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();

            client.sendHeaders(requestStream, environment.uri("/content-length-mismatch"), 7);
            client.sendData(requestStream, "pay".getBytes(StandardCharsets.UTF_8), true);

            assertThat(waitFor(() -> requestStream.stream().rcvErrorCode() == Http3ErrorCode.MESSAGE_ERROR.code(),
                               Duration.ofSeconds(10)),
                       equalTo(true));

            DecodedResponse followUp = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (followUp == null || followUp.status() != 200) {
                followUp = client.get(environment.uri("/alive"));
                if (System.nanoTime() >= deadline) {
                    break;
                }
                if (followUp.status() != 200) {
                    Thread.sleep(10);
                }
            }
            assertThat(followUp.status(), equalTo(200));
            assertThat(new String(followUp.body(), StandardCharsets.UTF_8), equalTo("alive"));
            assertThat(applicationErrorHandlerInvoked.get(), equalTo(false));
        }
    }

    @Test
    void shouldNotInspectUnconsumedDataWhenRequestContentLengthIsZero() throws Exception {
        CountDownLatch handlerInvoked = new CountDownLatch(1);
        try (TestEnvironment environment = TestEnvironment.create(Http3Config.create(),
                                                                  1,
                                                                  routing -> routing.post("/zero-length", (req, res) -> {
                                                                      handlerInvoked.countDown();
                                                                      res.send();
                                                                  }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());

            client.sendHeaders(requestStream, environment.uri("/zero-length"), 0);
            client.sendData(requestStream, new byte[] {'x'}, true);

            DecodedResponse response = client.decodeResponse(requestStream, responseFuture.get(10, TimeUnit.SECONDS));
            assertThat(response.status(), equalTo(200));
            assertThat(handlerInvoked.getCount(), equalTo(0L));
        }
    }

    @Test
    void shouldRouteZeroLengthRequestBeforeFin() throws Exception {
        CountDownLatch handlerInvoked = new CountDownLatch(1);
        try (TestEnvironment environment = TestEnvironment.create(Http3Config.create(),
                                                                  1,
                                                                  routing -> routing.post("/delayed-fin", (req, res) -> {
                                                                      handlerInvoked.countDown();
                                                                      res.send();
                                                                  }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            CompletableFuture<byte[]> responseFuture = readAll(requestStream.stream());

            client.sendHeaders(requestStream, environment.uri("/delayed-fin"), 0);

            assertThat(handlerInvoked.await(5, TimeUnit.SECONDS), equalTo(true));
            DecodedResponse response = client.decodeResponse(requestStream, responseFuture.get(10, TimeUnit.SECONDS));
            assertThat(response.status(), equalTo(200));
            assertThat(waitFor(requestStream.stream()::stopSendingReceived, Duration.ofSeconds(10)), equalTo(true));
        }
    }

    @Test
    void shouldCloseConnectionWhenDataPrecedesHeadersOnRequestStream() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();

            requestStream.writer()
                    .scheduleForWriting(BufferData.create(Http3Protocol.encodeDataFrame("body".getBytes(StandardCharsets.UTF_8))),
                                        true);

            QuicTermination terminationCause = client.connection.whenTerminated().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertThat(terminationCause.layer(), equalTo(QuicTermination.Layer.APPLICATION));
            assertThat(terminationCause.errorCode().orElseThrow(), equalTo(Http3ErrorCode.FRAME_UNEXPECTED.code()));
        }
    }

    @Test
    void shouldResetMalformedRequestStreamWhenPseudoHeaderIsDuplicated() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();

            requestStream.writer()
                    .scheduleForWriting(BufferData.create(rawHeadersFrame(List.of(
                            HeaderValues.create(HeaderNames.createFromLowercase(":method"), "GET"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":scheme"), "https"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":authority"), "localhost"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":path"), "/hello"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":path"), "/duplicate")))),
                                        true);

            assertThat(waitFor(() -> requestStream.stream().rcvErrorCode() == Http3ErrorCode.MESSAGE_ERROR.code(),
                               Duration.ofSeconds(10)),
                       equalTo(true));
        }
    }

    @Test
    void shouldCloseConnectionWhenPeerControlStreamDoesNotStartWithSettings() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.createUnprimed(environment)) {
            QuicSenderStream controlStream = client.connection.openNewLocalUniStream(Duration.ofSeconds(5))
                    .get(10, TimeUnit.SECONDS);
            Http3StreamSupport.writeAll(controlStream, malformedControlStream(), true, client.connection);

            QuicTermination terminationCause = client.connection.whenTerminated().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertThat(terminationCause.layer(), equalTo(QuicTermination.Layer.APPLICATION));
            assertThat(terminationCause.errorCode().orElseThrow(), equalTo(Http3ErrorCode.MISSING_SETTINGS.code()));
        }
    }

    @Test
    void shouldCloseConnectionWhenPeerControlStreamContainsDuplicateSettings() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.createUnprimed(environment)) {
            QuicSenderStream controlStream = client.connection.openNewLocalUniStream(Duration.ofSeconds(5))
                    .get(10, TimeUnit.SECONDS);
            Http3StreamSupport.writeAll(controlStream, duplicateSettingsControlStream(), true, client.connection);

            QuicTermination terminationCause = client.connection.whenTerminated().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertThat(terminationCause.layer(), equalTo(QuicTermination.Layer.APPLICATION));
            assertThat(terminationCause.errorCode().orElseThrow(), equalTo(Http3ErrorCode.SETTINGS_ERROR.code()));
        }
    }

    @Test
    void shouldCloseConnectionWhenPeerOpensDuplicateCriticalStream() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create()) {
            for (Http3StreamType streamType : List.of(Http3StreamType.CONTROL,
                                                      Http3StreamType.QPACK_ENCODER,
                                                      Http3StreamType.QPACK_DECODER)) {
                try (LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
                    QuicSenderStream duplicate = client.connection.openNewLocalUniStream(Duration.ofSeconds(5))
                            .get(10, TimeUnit.SECONDS);
                    byte[] preamble = streamType == Http3StreamType.CONTROL
                            ? Http3Protocol.controlStreamPreamble(Http3Settings.create(0, 0))
                            : Http3Protocol.qpackUniStreamPreamble(streamType);
                    Http3StreamSupport.writeAll(duplicate, preamble, false, client.connection, streamType);

                    QuicTermination terminationCause = client.connection.whenTerminated().toCompletableFuture()
                            .get(10, TimeUnit.SECONDS);

                    assertThat(terminationCause.layer(), equalTo(QuicTermination.Layer.APPLICATION));
                    assertThat(terminationCause.errorCode().orElseThrow(), equalTo(Http3ErrorCode.STREAM_CREATION_ERROR.code()));
                }
            }
        }
    }

    @Test
    void shouldCloseConnectionWhenPeerControlStreamContainsReservedHttp2Setting() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.createUnprimed(environment)) {
            QuicSenderStream controlStream = client.connection.openNewLocalUniStream(Duration.ofSeconds(5))
                    .get(10, TimeUnit.SECONDS);
            Http3StreamSupport.writeAll(controlStream, reservedHttp2SettingControlStream(), true, client.connection);

            QuicTermination terminationCause = client.connection.whenTerminated().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertThat(terminationCause.layer(), equalTo(QuicTermination.Layer.APPLICATION));
            assertThat(terminationCause.errorCode().orElseThrow(), equalTo(Http3ErrorCode.SETTINGS_ERROR.code()));
        }
    }

    @Test
    void shouldCloseConnectionWhenPeerGoAwayIdentifierIncreases() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            byte[] first = Http3Protocol.goAwayFrame(Http3GoAway.pushId(10));
            byte[] decreased = Http3Protocol.goAwayFrame(Http3GoAway.pushId(9));
            byte[] equal = Http3Protocol.goAwayFrame(Http3GoAway.pushId(9));
            byte[] increased = Http3Protocol.goAwayFrame(Http3GoAway.pushId(10));
            BufferData controlData = BufferData.create(first.length
                                                               + decreased.length
                                                               + equal.length
                                                               + increased.length);
            controlData.write(first);
            controlData.write(decreased);
            controlData.write(equal);
            controlData.write(increased);
            client.controlWriter.scheduleForWriting(controlData, false);

            QuicTermination terminationCause = client.connection.whenTerminated().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertThat(terminationCause.layer(), equalTo(QuicTermination.Layer.APPLICATION));
            assertThat(terminationCause.errorCode().orElseThrow(), equalTo(Http3ErrorCode.ID_ERROR.code()));
        }
    }

    @Test
    void shouldCloseConnectionWhenPeerDoesNotAllowRequiredCriticalStreams() throws Exception {
        QuicConfig quicConfig = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxUniStreams(2)
                .buildPrototype();
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment, quicConfig)) {
            QuicTermination terminationCause = client.connection.whenTerminated().toCompletableFuture()
                    .get(20, TimeUnit.SECONDS);

            assertThat(terminationCause.layer(), equalTo(QuicTermination.Layer.APPLICATION));
            assertThat(terminationCause.errorCode().orElseThrow(), equalTo(Http3ErrorCode.STREAM_CREATION_ERROR.code()));
        }
    }

    @Test
    void shouldCloseConnectionWhenDataPrecedesHeadersAtMinimumCriticalStreamLimit() throws Exception {
        QuicConfig quicConfig = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxUniStreams(3)
                .buildPrototype();
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment, quicConfig)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();

            requestStream.writer()
                    .scheduleForWriting(BufferData.create(Http3Protocol.encodeDataFrame("body".getBytes(StandardCharsets.UTF_8))),
                                        true);

            QuicTermination terminationCause = client.connection.whenTerminated().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertThat(terminationCause.layer(), equalTo(QuicTermination.Layer.APPLICATION));
            assertThat(terminationCause.errorCode().orElseThrow(), equalTo(Http3ErrorCode.FRAME_UNEXPECTED.code()));
        }
    }

    @Test
    void shouldResetMalformedRequestStreamWhenPseudoHeaderIsDuplicatedAtMinimumCriticalStreamLimit() throws Exception {
        QuicConfig quicConfig = QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .maxUniStreams(3)
                .buildPrototype();
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment, quicConfig)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();

            requestStream.writer()
                    .scheduleForWriting(BufferData.create(rawHeadersFrame(List.of(
                            HeaderValues.create(HeaderNames.createFromLowercase(":method"), "GET"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":scheme"), "https"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":authority"), "localhost"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":path"), "/hello"),
                            HeaderValues.create(HeaderNames.createFromLowercase(":path"), "/duplicate")))),
                                        true);

            assertThat(waitFor(() -> requestStream.stream().rcvErrorCode() == Http3ErrorCode.MESSAGE_ERROR.code(),
                               Duration.ofSeconds(10)),
                       equalTo(true));
        }
    }

    @Test
    void shouldCloseConnectionWhenCancelPushIsReceivedOnRequestStream() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();

            requestStream.writer()
                    .scheduleForWriting(BufferData.create(requestCancelPushFrame()), true);

            QuicTermination terminationCause = client.connection.whenTerminated().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertThat(terminationCause.layer(), equalTo(QuicTermination.Layer.APPLICATION));
            assertThat(terminationCause.errorCode().orElseThrow(), equalTo(Http3ErrorCode.FRAME_UNEXPECTED.code()));
        }
    }

    @Test
    void shouldCloseConnectionWhenRequestHeadersContainInvalidStaticTableIndex() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create();
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();

            requestStream.writer()
                    .scheduleForWriting(BufferData.create(requestHeadersFrame(invalidStaticIndexHeadersPayload())), true);

            QuicTermination terminationCause = client.connection.whenTerminated().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertThat(terminationCause.layer(), equalTo(QuicTermination.Layer.APPLICATION));
            assertThat(terminationCause.errorCode().orElseThrow(), equalTo(Http3ErrorCode.QPACK_DECOMPRESSION_FAILED.code()));
        }
    }

    @Test
    void shouldReturnFinalResponseWithout100ContinueWhenHttp3RouteFailsBeforeReadingEntity() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing
                 .error(IllegalStateException.class, (req, res, throwable) -> res.status(500).send(throwable.getMessage()))
                 .post("/expect-continue-fail", (req, res) -> {
                     throw new IllegalStateException("boom");
                 }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            Http3MessageReader reader = client.responseReader(requestStream, Method.POST);

            client.sendHeaders(requestStream,
                               environment.uri("/expect-continue-fail"),
                               4,
                               headers(HeaderValues.create(HeaderNames.EXPECT, "100-continue")));

            Http3MessageReader.ResponseHead finalResponse = reader.readResponseHead(_ -> {
            });
            assertThat(finalResponse.status().code(), equalTo(500));
            assertThat(new String(readMessageBody(reader), StandardCharsets.UTF_8), equalTo("boom"));
            assertThat(requestStream.stream().futureSendingCompletion().get(10, TimeUnit.SECONDS).isReset(), equalTo(true));
            assertThat(requestStream.stream().stopSendingReceived(), equalTo(true));
        }
    }

    @Test
    void shouldWaitFor100ContinueBeforeSendingBufferedHttp3RequestBody() throws Exception {
        try (RawTestEnvironment environment = RawTestEnvironment.create(serverStream -> {
                 if (!"/client-continue".equals(serverStream.request().path().orElseThrow())) {
                     return Optional.of(rawText(404, "Not Found"));
                 }

                 InputStream inputStream = requestBodyInputStream(serverStream);
                 CompletableFuture<Integer> firstByte = firstByte(inputStream);
                 boolean prematureBody = bodyArrivedBefore(firstByte, 150);

                 serverStream.writeResponseHeaders(100, headers(), false);
                 byte[] entity = readEntity(inputStream, firstByte);
                 serverStream.writeResponseHeaders(200,
                                                   headers(HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                                               "text/plain; charset=utf-8"),
                                                           HeaderValues.create(PREMATURE_BODY_NAME,
                                                                               Boolean.toString(prematureBody))),
                                                   false);
                 serverStream.writeData(entity, 0, entity.length, true);
                 return Optional.empty();
             })) {
            Http3Client client = rawHttp3Client(environment);
            try (Http3ClientResponse response = client.post(environment.uri("/client-continue").toString())
                    .sendExpectContinue(true)
                    .readContinueTimeout(Duration.ofSeconds(1))
                    .submit("payload")) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(response.headers().get(PREMATURE_BODY_NAME).get(), equalTo("false"));
                assertThat(response.as(String.class), equalTo("payload"));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldTimeoutWaitingFor100ContinueAndSendBufferedHttp3RequestBody() throws Exception {
        try (RawTestEnvironment environment = RawTestEnvironment.create(serverStream -> {
                 if (!"/client-continue-timeout".equals(serverStream.request().path().orElseThrow())) {
                     return Optional.of(rawText(404, "Not Found"));
                 }
                 return Optional.of(rawText(200,
                                                new String(readRequestBody(requestBodyInputStream(serverStream)),
                                                           StandardCharsets.UTF_8)));
             })) {
            Http3Client client = rawHttp3Client(environment);
            try (Http3ClientResponse response = client.post(environment.uri("/client-continue-timeout").toString())
                    .header(HeaderValues.EXPECT_100)
                    .readContinueTimeout(Duration.ofMillis(50))
                    .readTimeout(Duration.ofSeconds(2))
                    .submit("payload")) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(response.as(String.class), equalTo("payload"));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldStopBufferedHttp3RequestBodyAfterFinalNon100Response() throws Exception {
        try (RawTestEnvironment environment = RawTestEnvironment.create((connection, serverStream) -> {
                 return switch (serverStream.request().path().orElseThrow()) {
                 case "/socket-id" -> Optional.of(rawText(200, connection.childSocketId()));
                 case "/client-continue-reject" -> {
                     boolean prematureBody = bodyArrivedBefore(
                             firstByte(requestBodyInputStream(serverStream)),
                             150);
                     yield Optional.of(rawResponse(418,
                                                   headers(HeaderValues.create(PREMATURE_BODY_NAME,
                                                                               Boolean.toString(prematureBody))),
                                                   new byte[0]));
                 }
                 default -> Optional.of(rawText(404, "Not Found"));
                 };
             })) {
            Http3Client client = rawHttp3Client(environment);
            try {
                String firstSocketId;
                ClientResponseTyped<String> firstResponse = client.get(environment.uri("/socket-id").toString())
                        .request(String.class);
                try {
                    firstSocketId = firstResponse.entity();
                } finally {
                    firstResponse.close();
                }

                try (Http3ClientResponse response = client.post(environment.uri("/client-continue-reject").toString())
                        .sendExpectContinue(true)
                        .readContinueTimeout(Duration.ofSeconds(1))
                        .submit("payload")) {
                    assertThat(response.status().code(), equalTo(418));
                    assertThat(response.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                    assertThat(response.headers().get(PREMATURE_BODY_NAME).get(), equalTo("false"));
                }

                ClientResponseTyped<String> secondResponse = client.get(environment.uri("/socket-id").toString())
                        .request(String.class);
                try {
                    assertThat(secondResponse.entity(), equalTo(firstSocketId));
                } finally {
                    secondResponse.close();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldExposeHelidonHttp3ResponseTrailers() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.get("/trailers", (req, res) -> {
                 res.header(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                 res.trailers().add(TEST_TRAILER_HEADER);
                 res.send("payload");
             }))) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse response = client.get(environment.uri("/trailers").toString()).request()) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.as(String.class), equalTo("payload"));
                assertThat(response.headers().values(HeaderNames.TRAILER), containsInAnyOrder("test-trailer"));
                assertThat(response.trailers().contains(TEST_TRAILER_HEADER), equalTo(true));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldValidateMaterializedHttp3ResponseContentLength() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing
                .get("/length-match", (req, res) -> res.header(HeaderNames.CONTENT_LENGTH, "4").send("test"))
                .get("/length-mismatch", (req, res) -> res.header(HeaderNames.CONTENT_LENGTH, "5").send("test")))) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse matching = client.get(environment.uri("/length-match").toString()).request();
                 Http3ClientResponse mismatching = client.get(environment.uri("/length-mismatch").toString()).request()) {
                assertThat(matching.status().code(), equalTo(200));
                assertThat(matching.as(String.class), equalTo("test"));
                assertThat(mismatching.status().code(), equalTo(500));
                assertThat(mismatching.as(String.class), equalTo("Internal Server Error"));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRejectInformationalStatusAsFinalResponse() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing
                .get("/informational-materialized", (req, res) -> res.status(101).send())
                .get("/informational-streaming", (req, res) -> {
                    res.status(103);
                    try (OutputStream _ = res.outputStream()) {
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }))) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse materialized = client.get(
                    environment.uri("/informational-materialized").toString()).request();
                 Http3ClientResponse streaming = client.get(
                         environment.uri("/informational-streaming").toString()).request()) {
                assertThat(materialized.status().code(), equalTo(500));
                assertThat(streaming.status().code(), equalTo(500));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldResetStreamForPostCommitResponseContentLengthMismatch() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.get("/stream-underflow", (req, res) -> {
                 res.header(HeaderNames.CONTENT_LENGTH, "7");
                 try (OutputStream outputStream = res.outputStream()) {
                     outputStream.write("pay".getBytes(StandardCharsets.UTF_8));
                 } catch (IOException e) {
                     throw new UncheckedIOException(e);
                 }
             }));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream requestStream = client.openRequestStream();
            requestStream.writer()
                    .scheduleForWriting(BufferData.create(client.encodeRequestHeaders(requestStream,
                                                                                    environment.uri("/stream-underflow"),
                                                                                    "GET",
                                                                                    headers())),
                                        true);

            assertThat(waitFor(() -> requestStream.stream().rcvErrorCode() == Http3ErrorCode.INTERNAL_ERROR.code(),
                               Duration.ofSeconds(10)),
                       equalTo(true));
        }
    }

    @Test
    void shouldAbortStreamForPostCommitRouteFailure() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(Http3Config.create(),
                                                                  1,
                                                                  routing -> {
                                                                      routing.post("/post-commit-failure", (req, res) -> {
                                                                          try {
                                                                              OutputStream outputStream = res.outputStream();
                                                                              outputStream.write("partial".getBytes(
                                                                                      StandardCharsets.UTF_8));
                                                                              outputStream.flush();
                                                                          } catch (IOException e) {
                                                                              throw new UncheckedIOException(e);
                                                                          }
                                                                          throw new IllegalStateException(
                                                                                  "post-commit failure");
                                                                      });
                                                                      routing.get("/alive", (req, res) -> res.send("alive"));
                                                                  });
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            LowLevelHttp3Client.RequestStream failedRequest = client.openRequestStream();
            client.sendHeaders(failedRequest,
                               environment.uri("/post-commit-failure"),
                               7,
                               headers(HeaderValues.create(HeaderNames.EXPECT, "100-continue")));
            client.sendData(failedRequest, "pay".getBytes(StandardCharsets.UTF_8), false);

            boolean aborted = waitFor(() -> failedRequest.stream().rcvErrorCode()
                    == Http3ErrorCode.INTERNAL_ERROR.code()
                    && failedRequest.stream().stopSendingReceived()
                    && failedRequest.stream().sndErrorCode() == Http3ErrorCode.INTERNAL_ERROR.code(),
                                      Duration.ofSeconds(10));
            assertThat("receiveCode=" + failedRequest.stream().rcvErrorCode()
                               + ", stopSending=" + failedRequest.stream().stopSendingReceived()
                               + ", sendCode=" + failedRequest.stream().sndErrorCode(),
                       aborted,
                       equalTo(true));

            DecodedResponse followUp = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (followUp == null || followUp.status() != 200) {
                followUp = client.get(environment.uri("/alive"));
                if (System.nanoTime() >= deadline) {
                    break;
                }
                if (followUp.status() != 200) {
                    Thread.sleep(10);
                }
            }
            assertThat(followUp.status(), equalTo(200));
            assertThat(new String(followUp.body(), StandardCharsets.UTF_8), equalTo("alive"));
        }
    }

    @Test
    void shouldSuppressHttp3ContentForHeadAndBodylessStatuses() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing
                .head("/head", (req, res) -> res.header(HeaderNames.CONTENT_LENGTH, "7").send())
                .head("/head-metadata", (req, res) -> res.header(HeaderNames.CONTENT_LENGTH, "9").send())
                .head("/head-trailers", (req, res) -> {
                    res.header(HeaderNames.CONTENT_LENGTH, "7");
                    res.header(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                    res.trailers().add(TEST_TRAILER_HEADER);
                    res.send();
                })
                .get("/no-content", (req, res) -> res.status(204).send())
                .get("/reset-content", (req, res) -> {
                    res.status(205);
                    res.header(HeaderNames.CONTENT_LENGTH, "0");
                    res.header(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                    res.trailers().add(TEST_TRAILER_HEADER);
                    res.send();
                })
                .get("/not-modified", (req, res) -> res.status(304)
                        .header(HeaderNames.CONTENT_LENGTH, "7")
                        .send()));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            DecodedResponse head = client.request(environment.uri("/head"), "HEAD", headers());
            DecodedResponse headMetadata = client.request(environment.uri("/head-metadata"), "HEAD", headers());
            DecodedResponse headTrailers = client.request(environment.uri("/head-trailers"), "HEAD", headers());
            DecodedResponse noContent = client.get(environment.uri("/no-content"));
            DecodedResponse resetContent = client.get(environment.uri("/reset-content"));
            DecodedResponse notModified = client.get(environment.uri("/not-modified"));

            assertThat(head.status(), equalTo(200));
            assertThat(head.headers().first(HeaderNames.CONTENT_LENGTH).orElseThrow(), equalTo("7"));
            assertThat(head.body().length, equalTo(0));
            assertThat(headMetadata.status(), equalTo(200));
            assertThat(headMetadata.headers().first(HeaderNames.CONTENT_LENGTH).orElseThrow(), equalTo("9"));
            assertThat(headMetadata.body().length, equalTo(0));
            assertThat(headTrailers.body().length, equalTo(0));
            assertThat(headTrailers.trailers().contains(TEST_TRAILER_HEADER), equalTo(true));
            assertThat(noContent.status(), equalTo(204));
            assertThat(noContent.headers().contains(HeaderNames.CONTENT_LENGTH), equalTo(false));
            assertThat(noContent.body().length, equalTo(0));
            assertThat(resetContent.status(), equalTo(205));
            assertThat(resetContent.headers().first(HeaderNames.CONTENT_LENGTH).orElseThrow(), equalTo("0"));
            assertThat(resetContent.body().length, equalTo(0));
            assertThat(resetContent.trailers().contains(TEST_TRAILER_HEADER), equalTo(false));
            assertThat(notModified.status(), equalTo(304));
            assertThat(notModified.headers().first(HeaderNames.CONTENT_LENGTH).orElseThrow(), equalTo("7"));
            assertThat(notModified.body().length, equalTo(0));
        }
    }

    @Test
    void shouldNotCanonicalizeCaseSensitiveRequestMethod() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.any((req, res) -> res.send("routed")));
             LowLevelHttp3Client client = LowLevelHttp3Client.create(environment)) {
            DecodedResponse response = client.request(environment.uri("/method"), "connect", headers());

            assertThat(response.status(), equalTo(501));
            assertThat(new String(response.body(), StandardCharsets.UTF_8), equalTo("Not Implemented"));
        }
    }

    @Test
    void shouldExposeHelidonHttp3StreamResultTrailer() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.get("/stream-result", (req, res) -> {
                 try (OutputStream outputStream = res.outputStream()) {
                     outputStream.write("payload".getBytes(StandardCharsets.UTF_8));
                     res.streamResult("Kaboom!");
                 } catch (IOException e) {
                     throw new UncheckedIOException(e);
                 }
             }))) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse response = client.get(environment.uri("/stream-result").toString()).request()) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.as(String.class), equalTo("payload"));
                assertThat(response.headers().values(HeaderNames.TRAILER), containsInAnyOrder("stream-result"));
                assertThat(response.trailers().contains(STREAM_RESULT_KABOOM), equalTo(true));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldExposeDefaultStreamResultTrailerOnStreamedResponse() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing.get("/stream-default", (req, res) -> {
                 res.headers().add(HeaderNames.TRAILER, BEFORE_TRAILER_HEADER.name());
                 res.headers().add(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                 res.beforeTrailers(trailers -> trailers.add(BEFORE_TRAILER_HEADER));
                 try (OutputStream outputStream = res.outputStream()) {
                     outputStream.write("payload".getBytes(StandardCharsets.UTF_8));
                     res.trailers().add(TEST_TRAILER_HEADER);
                 } catch (IOException e) {
                     throw new UncheckedIOException(e);
                 }
             }))) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse response = client.get(environment.uri("/stream-default").toString()).request()) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.as(String.class), equalTo("payload"));
                assertThat(response.headers().values(HeaderNames.TRAILER),
                           containsInAnyOrder("before-trailer", "test-trailer", "stream-result"));
                assertThat(response.trailers().contains(BEFORE_TRAILER_HEADER), equalTo(true));
                assertThat(response.trailers().contains(TEST_TRAILER_HEADER), equalTo(true));
                assertThat(response.trailers().contains(STREAM_RESULT_OK), equalTo(true));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldRejectUndeclaredHttp3ResponseTrailers() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing
                .error(IllegalStateException.class, (req, res, throwable) -> res.status(500).send(throwable.getMessage()))
                .get("/trailers-no-declaration", (req, res) -> {
                    res.trailers().add(TEST_TRAILER_HEADER);
                    res.send("payload");
                }))) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse response = client.get(environment.uri("/trailers-no-declaration").toString()).request()) {
                assertThat(response.status().code(), equalTo(500));
                assertThat(response.as(String.class),
                           equalTo("Trailers are supported only when response headers have trailer names definition "
                                           + "'Trailer: <trailer-name>'"));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldFailWhenNoHttp3TrailersAreExpected() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create()) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try (Http3ClientResponse response = client.get(environment.uri("/hello").toString()).request()) {
                assertThat(response.status().code(), equalTo(200));
                assertThat(response.as(String.class), equalTo("GET /hello"));
                IllegalStateException exception = assertThrows(IllegalStateException.class, response::trailers);
                assertThat(exception.getMessage(), equalTo("No trailers are expected."));
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldReturnServerErrorWhenHttp3RouteFailsBeforeSending() throws Exception {
        try (TestEnvironment environment = TestEnvironment.create(routing -> routing
                 .error(IllegalStateException.class, (req, res, throwable) -> res.status(500).send(throwable.getMessage()))
                 .get("/fail", (req, res) -> {
                     throw new IllegalStateException("boom");
                 }));
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpRequest request = HttpRequest.newBuilder(environment.uri("/fail"))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), equalTo(500));
            assertThat(response.version(), equalTo(HTTP_3));
            assertThat(response.body(), equalTo("boom"));
        }
    }

    @Test
    void shouldShareSingleListenerBetweenHttp1AndHttp3() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListener();
             HttpClient http1Client = HttpClient.newBuilder()
                     .version(HTTP_1_1)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build();
             HttpClient http3Client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpResponse<String> http1Response = http1Client.send(HttpRequest.newBuilder(environment.uri("/hello"))
                                                                          .version(HTTP_1_1)
                                                                          .GET()
                                                                          .build(),
                                                                  HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> http3Response = http3Client.send(HttpRequest.newBuilder(environment.uri("/hello"))
                                                                          .version(HTTP_3)
                                                                          .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                                                                          .GET()
                                                                          .build(),
                                                                  HttpResponse.BodyHandlers.ofString());

            assertThat(http1Response.statusCode(), equalTo(200));
            assertThat(http1Response.version(), equalTo(HTTP_1_1));
            assertThat(http1Response.body(), equalTo("shared"));

            assertThat(http3Response.statusCode(), equalTo(200));
            assertThat(http3Response.version(), equalTo(HTTP_3));
            assertThat(http3Response.body(), equalTo("shared"));
        }
    }

    @Test
    void shouldExposeVendorRequestCountsByWireProtocolOnSharedListener() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListenerWithMetrics();
             HttpClient http1Client = HttpClient.newBuilder()
                     .version(HTTP_1_1)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpResponse<String> http1Response = http1Client.send(HttpRequest.newBuilder(environment.uri("/hello"))
                                                                          .version(HTTP_1_1)
                                                                          .GET()
                                                                          .build(),
                                                                  HttpResponse.BodyHandlers.ofString());

            assertThat(http1Response.statusCode(), equalTo(200));
            assertThat(http1Response.version(), equalTo(HTTP_1_1));
            assertThat(http1Response.body(), equalTo("shared"));

            Http3Client http3Client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();
            try (Http3ClientResponse http3Response = http3Client.get(environment.uri("/hello").toString()).request()) {
                assertThat(http3Response.status().code(), equalTo(200));
                assertThat(http3Response.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                assertThat(http3Response.as(String.class), equalTo("shared"));
            } finally {
                http3Client.closeResource();
            }

            HttpResponse<String> metricsResponse = http1Client.send(HttpRequest.newBuilder(environment.uri(
                                                                                     "/observe/metrics",
                                                                                     "scope=vendor&name=requests.count"))
                                                                             .version(HTTP_1_1)
                                                                             .header(HeaderNames.ACCEPT.defaultCase(),
                                                                                     "application/json")
                                                                             .GET()
                                                                             .build(),
                                                                     HttpResponse.BodyHandlers.ofString());

            assertThat(metricsResponse.statusCode(), equalTo(200));
            assertThat(metricsResponse.version(), equalTo(HTTP_1_1));
            JsonObject vendorMeters = Json.createReader(new StringReader(metricsResponse.body()))
                    .readObject();
            assertThat(vendorMeters.getJsonNumber("requests.count").intValue(), equalTo(3));
        }
    }

    @Test
    void shouldPublishHttp3TransportMetricsToConfiguredRegistry() throws Exception {
        MeterRegistry meterRegistry = MeterRegistry.create();
        try {
            try (TestEnvironment environment = TestEnvironment.createSharedListener(InetAddress.getLoopbackAddress(),
                                                                                     Http1Config.create(),
                                                                                     Http3Config.create(),
                                                                                     true,
                                                                                     meterRegistry)) {
                Http3Client http3Client = strictClientBuilder()
                        .tls(environment.clientTls())
                        .build();
                try (Http3ClientResponse http3Response = http3Client.get(environment.uri("/hello").toString()).request()) {
                    assertThat(http3Response.status().code(), equalTo(200));
                    assertThat(http3Response.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                    assertThat(http3Response.as(String.class), equalTo("shared"));
                } finally {
                    http3Client.closeResource();
                }

                Map<String, String> connectionTags = Map.of("role", "server",
                                                            "transport", "quic",
                                                            "protocol", "http/3");
                Map<String, String> handshakeTags = Map.of("role", "server",
                                                           "transport", "quic",
                                                           "handshake", "quic-tls",
                                                           "outcome", "success");
                Map<String, String> streamTags = Map.of("role", "server",
                                                        "protocol", "http/3",
                                                        "direction", "bidi",
                                                        "initiator", "remote");
                boolean observed = waitFor(() -> counterMeters(meterRegistry,
                                                               "http.connections.established",
                                                               connectionTags) > 0
                                && counterMeters(meterRegistry, "http.handshakes", handshakeTags) > 0
                                && counterMeters(meterRegistry, "http.streams.opened", streamTags) > 0,
                                           Duration.ofSeconds(10));
                assertThat("HTTP/3 transport counter meters: connection="
                                   + counterMeters(meterRegistry, "http.connections.established", connectionTags)
                                   + ", handshake=" + counterMeters(meterRegistry, "http.handshakes", handshakeTags)
                                   + ", stream=" + counterMeters(meterRegistry, "http.streams.opened", streamTags)
                                   + ", meters=" + meterRegistry.meters().stream()
                                           .map(meter -> meter.id().name() + meter.id().tagsMap())
                                           .sorted()
                                           .toList(),
                           observed,
                           equalTo(true));
                assertThat(counterMeters(meterRegistry, "http.connections.established", connectionTags), equalTo(1L));
                assertThat(counterMeters(meterRegistry, "http.handshakes", handshakeTags), equalTo(1L));
                assertThat(counterMeters(meterRegistry, "http.streams.opened", streamTags), equalTo(1L));
                assertThat(meterRegistry.meters()
                                   .stream()
                                   .filter(meter -> meter.id().name().equals("http.streams.opened"))
                                   .noneMatch(meter -> "uni".equals(meter.id().tagsMap().get("direction"))
                                           || "local".equals(meter.id().tagsMap().get("initiator"))),
                           equalTo(true));
            }
            assertThat(waitFor(() -> meterRegistry.meters()
                                               .stream()
                                               .map(meter -> meter.id().name())
                                               .noneMatch(name -> name.startsWith("http.connections.")
                                                       || name.startsWith("http.handshakes")
                                                       || name.startsWith("http.streams.")),
                               Duration.ofSeconds(10)),
                       equalTo(true));
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void shouldAdvertiseAltSvcOnSharedHttp1ResponseUsingResolvedPort() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListenerWithAltSvc();
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_1_1)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(environment.uri("/hello"))
                                                                .version(HTTP_1_1)
                                                                .GET()
                                                                .build(),
                                                        HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.version(), equalTo(HTTP_1_1));
            assertThat(response.headers()
                               .firstValue(HeaderNames.ALT_SVC.defaultCase())
                               .orElse(null),
                       equalTo(expectedAltSvc(environment.securePort)));
            assertThat(response.body(), equalTo("shared"));
        }
    }

    @Test
    void shouldNotAdvertiseAltSvcOnSharedHttp1NotFound() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListenerWithAltSvc();
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_1_1)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(environment.uri("/missing"))
                                                                .version(HTTP_1_1)
                                                                .GET()
                                                                .build(),
                                                        HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode(), equalTo(404));
            assertThat(response.version(), equalTo(HTTP_1_1));
            assertThat(response.headers().firstValue(HeaderNames.ALT_SVC.defaultCase()).isPresent(), equalTo(false));
        }
    }

    @Test
    void shouldShareSingleNamedListenerBetweenHttp1AndHttp3() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createNamedSharedListener();
             HttpClient http1Client = HttpClient.newBuilder()
                     .version(HTTP_1_1)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build();
             HttpClient http3Client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpResponse<String> http1Response = http1Client.send(HttpRequest.newBuilder(environment.uri("/hello"))
                                                                          .version(HTTP_1_1)
                                                                          .GET()
                                                                          .build(),
                                                                  HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> http3Response = http3Client.send(HttpRequest.newBuilder(environment.uri("/hello"))
                                                                          .version(HTTP_3)
                                                                          .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                                                                          .GET()
                                                                          .build(),
                                                                  HttpResponse.BodyHandlers.ofString());

            assertThat(environment.securePort > 0, equalTo(true));
            assertThat(http1Response.statusCode(), equalTo(200));
            assertThat(http1Response.version(), equalTo(HTTP_1_1));
            assertThat(http1Response.body(), equalTo("named-shared"));

            assertThat(http3Response.statusCode(), equalTo(200));
            assertThat(http3Response.version(), equalTo(HTTP_3));
            assertThat(http3Response.body(), equalTo("named-shared"));
        }
    }

    @Test
    void shouldAdvertiseAltSvcOnNamedSharedHttp1ResponseUsingResolvedPort() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createNamedSharedListenerWithAltSvc();
             HttpClient client = HttpClient.newBuilder()
                     .version(HTTP_1_1)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(environment.uri("/hello"))
                                                                .version(HTTP_1_1)
                                                                .GET()
                                                                .build(),
                                                        HttpResponse.BodyHandlers.ofString());

            assertThat(environment.securePort > 0, equalTo(true));
            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.version(), equalTo(HTTP_1_1));
            assertThat(response.headers()
                               .firstValue(HeaderNames.ALT_SVC.defaultCase())
                               .orElse(null),
                       equalTo(expectedAltSvc(environment.securePort)));
            assertThat(response.body(), equalTo("named-shared"));
        }
    }

    @Test
    void shouldShareIpv6LoopbackListenerBetweenHttp1AndHttp3() throws Exception {
        Assumptions.assumeTrue(ipv6LoopbackAvailable(), "IPv6 loopback is not available.");

        InetAddress ipv6Loopback = InetAddress.getByName("::1");
        try (TestEnvironment environment = TestEnvironment.createSharedListener(ipv6Loopback);
             HttpClient http1Client = HttpClient.newBuilder()
                     .version(HTTP_1_1)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build();
             HttpClient http3Client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            URI secureUri = new URI("https", null, ipv6Loopback.getHostAddress(), environment.securePort, "/hello", null, null);

            HttpResponse<String> http1Response = http1Client.send(HttpRequest.newBuilder(secureUri)
                                                                          .version(HTTP_1_1)
                                                                          .GET()
                                                                          .build(),
                                                                  HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> http3Response = http3Client.send(HttpRequest.newBuilder(secureUri)
                                                                          .version(HTTP_3)
                                                                          .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                                                                          .GET()
                                                                          .build(),
                                                                  HttpResponse.BodyHandlers.ofString());

            assertThat(http1Response.statusCode(), equalTo(200));
            assertThat(http1Response.version(), equalTo(HTTP_1_1));
            assertThat(http1Response.body(), equalTo("shared"));

            assertThat(http3Response.statusCode(), equalTo(200));
            assertThat(http3Response.version(), equalTo(HTTP_3));
            assertThat(http3Response.body(), equalTo("shared"));
        }
    }

    @Test
    void shouldUseFirstMatchingRouteOrderForHttp3Route() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListener();
             HttpClient http1Client = HttpClient.newBuilder()
                     .version(HTTP_1_1)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build();
             HttpClient http3Client = HttpClient.newBuilder()
                     .version(HTTP_3)
                     .sslContext(environment.clientSslContext())
                     .proxy(noProxySelector())
                     .connectTimeout(Duration.ofSeconds(10))
                     .build()) {
            HttpResponse<String> http3SharedFirst = http3Client.send(HttpRequest.newBuilder(environment.uri("/shared-first"))
                                                                             .version(HTTP_3)
                                                                             .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                                                                             .GET()
                                                                             .build(),
                                                                     HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> http3Http3First = http3Client.send(HttpRequest.newBuilder(environment.uri("/http3-first"))
                                                                            .version(HTTP_3)
                                                                            .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                                                                            .GET()
                                                                            .build(),
                                                                    HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> http1Http3First = http1Client.send(HttpRequest.newBuilder(environment.uri("/http3-first"))
                                                                            .version(HTTP_1_1)
                                                                            .GET()
                                                                            .build(),
                                                                    HttpResponse.BodyHandlers.ofString());

            assertThat(http3SharedFirst.statusCode(), equalTo(200));
            assertThat(http3SharedFirst.body(), equalTo("shared-first"));

            assertThat(http3Http3First.statusCode(), equalTo(200));
            assertThat(http3Http3First.body(), equalTo("http3-first"));

            assertThat(http1Http3First.statusCode(), equalTo(200));
            assertThat(http1Http3First.version(), equalTo(HTTP_1_1));
            assertThat(http1Http3First.body(), equalTo("shared-after-http3"));
        }
    }

    @Test
    void shouldWaitForInflightHttp3RequestDuringStop() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseRequest = new CountDownLatch(1);
        CountDownLatch stopCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> stopFailure = new AtomicReference<>();

        WebServer server = WebServer.builder()
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .featuresDiscoverServices(false)
                .protocolsDiscoverServices(false)
                .shutdownGracePeriod(Duration.ofSeconds(2))
                .tls(serverTls())
                .addProtocol(Http3Config.create())
                .routing(routing -> routing.get("/slow", (req, res) -> {
                    requestStarted.countDown();
                    try {
                        if (!releaseRequest.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to release HTTP/3 request.");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting to release HTTP/3 request.", e);
                    }
                    res.send("slow");
                }))
                .build()
                .start();

        Thread stopThread = null;
        try (HttpClient client = HttpClient.newBuilder()
                .version(HTTP_3)
                .sslContext(clientSslContext())
                .proxy(noProxySelector())
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {
            HttpRequest request = HttpRequest.newBuilder(new URI("https", null, "localhost", server.port(), "/slow", null, null))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET()
                    .build();

            CompletableFuture<HttpResponse<String>> responseFuture =
                    client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            assertThat(requestStarted.await(10, TimeUnit.SECONDS), equalTo(true));

            stopThread = Thread.ofVirtual().start(() -> {
                try {
                    server.stop();
                } catch (Throwable t) {
                    stopFailure.set(t);
                } finally {
                    stopCompleted.countDown();
                }
            });

            assertThat(stopCompleted.await(250, TimeUnit.MILLISECONDS), equalTo(false));

            releaseRequest.countDown();

            HttpResponse<String> response = responseFuture.get(10, TimeUnit.SECONDS);
            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.version(), equalTo(HTTP_3));
            assertThat(response.body(), equalTo("slow"));

            assertThat(stopCompleted.await(10, TimeUnit.SECONDS), equalTo(true));
            if (stopFailure.get() != null) {
                throw new AssertionError("server.stop() failed", stopFailure.get());
            }
        } finally {
            releaseRequest.countDown();
            if (stopThread != null) {
                stopThread.join(TimeUnit.SECONDS.toMillis(10));
            }
            if (server.isRunning()) {
                server.stop();
            }
        }
    }

    @Test
    void shouldLearnAltSvcAndMigrateGenericWebClientToHttp3() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListenerWithAltSvc()) {
            WebClient client = newGenericClient(environment, Http3ClientProtocolConfig.builder().build());

            try {
                ClientResponseTyped<String> firstResponse = client.get(environment.uri("/hello").toString())
                        .request(String.class);
                try {
                    assertThat(firstResponse.status().code(), equalTo(200));
                    assertThat(firstResponse.protocolId(), equalTo(Http1Client.PROTOCOL_ID));
                    assertThat(firstResponse.headers().contains(HeaderNames.ALT_SVC), equalTo(true));
                    assertThat(firstResponse.entity(), equalTo("shared"));
                } finally {
                    firstResponse.close();
                }

                ClientResponseTyped<String> secondResponse = client.get(environment.uri("/hello").toString())
                        .request(String.class);
                try {
                    assertThat(secondResponse.status().code(), equalTo(200));
                    assertThat(secondResponse.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                    assertThat(secondResponse.entity(), equalTo("shared"));
                } finally {
                    secondResponse.close();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldReuseQuicConnectionForTypedHttp3Client() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListener()) {
            Http3Client client = strictClientBuilder()
                    .tls(environment.clientTls())
                    .build();

            try {
                ClientResponseTyped<String> firstResponse = client.get(environment.uri("/socket-id").toString())
                        .request(String.class);
                String firstSocketId;
                try {
                    assertThat(firstResponse.status().code(), equalTo(200));
                    assertThat(firstResponse.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                    firstSocketId = firstResponse.entity();
                } finally {
                    firstResponse.close();
                }

                ClientResponseTyped<String> secondResponse = client.get(environment.uri("/socket-id").toString())
                        .request(String.class);
                try {
                    assertThat(secondResponse.status().code(), equalTo(200));
                    assertThat(secondResponse.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                    assertThat(secondResponse.entity(), equalTo(firstSocketId));
                } finally {
                    secondResponse.close();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldIgnoreAltSvcWhenDisabledOnGenericWebClient() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListenerWithAltSvc()) {
            WebClient client = newGenericClient(environment,
                                                Http3ClientProtocolConfig.builder()
                                                        .build(),
                                                ClientAltSvcConfig.builder()
                                                        .enabled(false)
                                                        .build());

            try {
                ClientResponseTyped<String> firstResponse = client.get(environment.uri("/hello").toString())
                        .request(String.class);
                try {
                    assertThat(firstResponse.status().code(), equalTo(200));
                    assertThat(firstResponse.protocolId(), equalTo(Http1Client.PROTOCOL_ID));
                    assertThat(firstResponse.headers().contains(HeaderNames.ALT_SVC), equalTo(true));
                    assertThat(firstResponse.entity(), equalTo("shared"));
                } finally {
                    firstResponse.close();
                }

                ClientResponseTyped<String> secondResponse = client.get(environment.uri("/hello").toString())
                        .request(String.class);
                try {
                    assertThat(secondResponse.status().code(), equalTo(200));
                    assertThat(secondResponse.protocolId(), equalTo(Http1Client.PROTOCOL_ID));
                    assertThat(secondResponse.entity(), equalTo("shared"));
                } finally {
                    secondResponse.close();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldUseKnownGoodHttp3AfterExplicitGenericRequest() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSharedListener()) {
            WebClient client = newGenericClient(environment, Http3ClientProtocolConfig.builder().build());

            try {
                ClientResponseTyped<String> explicitResponse = client.get(environment.uri("/socket-id").toString())
                        .protocolId(Http3Client.PROTOCOL_ID)
                        .request(String.class);
                String socketId;
                try {
                    assertThat(explicitResponse.status().code(), equalTo(200));
                    assertThat(explicitResponse.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                    socketId = explicitResponse.entity();
                } finally {
                    explicitResponse.close();
                }

                ClientResponseTyped<String> discoveredResponse = client.get(environment.uri("/socket-id").toString())
                        .request(String.class);
                try {
                    assertThat(discoveredResponse.status().code(), equalTo(200));
                    assertThat(discoveredResponse.protocolId(), equalTo(Http3Client.PROTOCOL_ID));
                    assertThat(discoveredResponse.entity(), equalTo(socketId));
                } finally {
                    discoveredResponse.close();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldFallbackTypedHttp3ClientToHttp1OnPlaintext() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createPlainAndSecure()) {
            Http3Client client = Http3Client.builder()
                    .shareConnectionCache(false)
                    .baseUri(environment.httpUri("/hello").toString())
                    .proxy(io.helidon.webclient.api.Proxy.noProxy())
                    .build();

            try {
                ClientResponseTyped<String> response = client.get().request(String.class);
                try {
                    assertThat(response.status().code(), equalTo(200));
                    assertThat(response.protocolId(), equalTo(Http1Client.PROTOCOL_ID));
                    assertThat(response.entity(), equalTo("GET /hello"));
                } finally {
                    response.close();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldFallbackTypedHttp3ClientToHttp1OnSecureHttp1OnlyEndpoint() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSecureHttp1Only()) {
            Http3Client client = Http3Client.builder()
                    .shareConnectionCache(false)
                    .proxy(io.helidon.webclient.api.Proxy.noProxy())
                    .tls(environment.clientTlsNoAlpn())
                    .protocolConfig(Http3ClientProtocolConfig.builder()
                                            .initialResponseTimeout(Duration.ofSeconds(1))
                                            .handshakeTimeout(Duration.ofSeconds(1))
                                            .build())
                    .build();

            try {
                ClientResponseTyped<String> response = client.get(environment.uri("/hello").toString()).request(String.class);
                try {
                    assertThat(response.status().code(), equalTo(200));
                    assertThat(response.protocolId(), equalTo(Http1Client.PROTOCOL_ID));
                    assertThat(response.entity(), equalTo("GET /hello"));
                } finally {
                    response.close();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldFallbackExplicitHttp3RequestToHttp1OnPlaintext() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createPlainAndSecure()) {
            WebClient client = WebClient.builder()
                    .shareConnectionCache(false)
                    .proxy(io.helidon.webclient.api.Proxy.noProxy())
                    .addProtocolConfig(Http3ClientProtocolConfig.builder().build())
                    .build();

            try {
                ClientResponseTyped<String> response = client.get(environment.httpUri("/hello").toString())
                        .protocolId(Http3Client.PROTOCOL_ID)
                        .request(String.class);
                try {
                    assertThat(response.status().code(), equalTo(200));
                    assertThat(response.protocolId(), equalTo(Http1Client.PROTOCOL_ID));
                    assertThat(response.entity(), equalTo("GET /hello"));
                } finally {
                    response.close();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldFallbackExplicitHttp3RequestToHttp1OnSecureHttp1OnlyEndpoint() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createSecureHttp1Only()) {
            WebClient client = newGenericClient(environment,
                                                Http3ClientProtocolConfig.builder()
                                                        .initialResponseTimeout(Duration.ofSeconds(1))
                                                        .handshakeTimeout(Duration.ofSeconds(1))
                                                        .build());

            try {
                ClientResponseTyped<String> response = client.get(environment.uri("/hello").toString())
                        .protocolId(Http3Client.PROTOCOL_ID)
                        .request(String.class);
                try {
                    assertThat(response.status().code(), equalTo(200));
                    assertThat(response.protocolId(), equalTo(Http1Client.PROTOCOL_ID));
                    assertThat(response.entity(), equalTo("GET /hello"));
                } finally {
                    response.close();
                }
            } finally {
                client.closeResource();
            }
        }
    }

    @Test
    void shouldFailPlaintextHttp3RequestWhenPriorKnowledgeIsEnabled() throws Exception {
        try (TestEnvironment environment = TestEnvironment.createPlainAndSecure()) {
            Http3Client client = Http3Client.builder()
                    .shareConnectionCache(false)
                    .baseUri(environment.httpUri("/hello").toString())
                    .proxy(io.helidon.webclient.api.Proxy.noProxy())
                    .protocolConfig(Http3ClientProtocolConfig.builder()
                                            .priorKnowledge(true)
                                            .build())
                    .build();

            try {
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                              () -> client.get().request());
                assertThat(exception.getMessage(),
                           equalTo("HTTP/3 priorKnowledge is enabled, but this request cannot use HTTP/3."));
            } finally {
                client.closeResource();
            }
        }
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

    private static long counterMeters(MeterRegistry meterRegistry, String name, Map<String, String> tags) {
        return meterRegistry.meters()
                .stream()
                .filter(Counter.class::isInstance)
                .map(Counter.class::cast)
                .filter(counter -> counter.id().name().equals(name))
                .filter(counter -> counter.id().tagsMap().equals(tags))
                .count();
    }

    private static String expectedAltSvc(int port) {
        return "h3=\"" + ":" + port + "\"";
    }

    private static boolean ipv6LoopbackAvailable() {
        try {
            InetAddress loopback = InetAddress.getByName("::1");
            try (ServerSocketChannel tcp = ServerSocketChannel.open(StandardProtocolFamily.INET6);
                 DatagramChannel udp = DatagramChannel.open(StandardProtocolFamily.INET6)) {
                tcp.bind(new InetSocketAddress(loopback, 0));
                udp.bind(new InetSocketAddress(loopback, 0));
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static WebClient newGenericClient(TestEnvironment environment, Http3ClientProtocolConfig protocolConfig) {
        return newGenericClient(environment, protocolConfig, ClientAltSvcConfig.create());
    }

    private static WebClient newGenericClient(TestEnvironment environment,
                                              Http3ClientProtocolConfig protocolConfig,
                                              ClientAltSvcConfig altSvcConfig) {
        return WebClient.builder()
                .shareConnectionCache(false)
                .proxy(io.helidon.webclient.api.Proxy.noProxy())
                .tls(environment.clientTlsNoAlpn())
                .altSvc(altSvcConfig)
                .addProtocolPreference(Http3Client.PROTOCOL_ID)
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .addProtocolConfig(protocolConfig)
                .build();
    }

    private static Http3Client rawHttp3Client(RawTestEnvironment environment) {
        return strictClientBuilder()
                .tls(environment.clientTls())
                .build();
    }

    private static byte[] readMessageBody(Http3MessageReader reader) {
        BufferData output = BufferData.growing(256);
        for (;;) {
            byte[] bytes = reader.readEntityDataWithTrailers(64);
            if (bytes.length == 0) {
                return output.readBytes();
            }
            output.write(bytes);
        }
    }

    private static byte[] readRequestBody(InputStream inputStream) {
        try (inputStream) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
            return decodeResponseHead(qpackStream, headersPayload);
        } finally {
            qpackStream.complete();
        }
    }

    private static DecodedResponseHead decodeResponseHead(Http3QpackContext.Stream qpackStream,
                                                          byte[] headersPayload) {
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
    }

    private static DecodedResponse decodeResponse(Http3QpackContext qpackContext, long streamId, byte[] bytes) {
        BufferData buffer = BufferData.create(bytes);
        long frameType = VariableLengthEncoder.decode(buffer);
        long frameLength = VariableLengthEncoder.decode(buffer);
        if (frameType != Http3Protocol.FRAME_HEADERS || frameLength < 0 || frameLength > buffer.available()) {
            throw new IllegalStateException("Malformed HTTP/3 response message.");
        }
        byte[] headersPayload = new byte[(int) frameLength];
        buffer.read(headersPayload);
        Http3QpackContext.Stream qpackStream = qpackContext.openStream(streamId);
        try {
            DecodedResponseHead head = decodeResponseHead(qpackStream, headersPayload);
            BufferData body = BufferData.growing(256);
            WritableHeaders<?> trailers = WritableHeaders.create();
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
                } else if (nextType == Http3Protocol.FRAME_HEADERS) {
                    qpackStream.decodeHeaders(BufferData.create(payload), -1).forEach(trailers::add);
                }
            }
            return new DecodedResponse(head.status(), head.headers(), headersPayload.length, body.readBytes(), trailers);
        } finally {
            qpackStream.complete();
        }
    }

    private static byte[] encodeRequestHeaders(Http3QpackContext qpackContext,
                                               long streamId,
                                               URI uri,
                                               String method,
                                               Headers headers) {
        return Http3Protocol.encodeRequestHeaders(qpackContext, streamId, uri, method, headers);
    }

    private static byte[] encodeResponse(int status, Headers headers, byte[] body) {
        byte[] entity = body == null ? new byte[0] : body;
        WritableHeaders<?> writable = WritableHeaders.create(headers);
        if (!writable.contains(HeaderNames.CONTENT_LENGTH)) {
            writable.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, entity.length));
        }
        byte[] headersFrame = Http3Protocol.encodeResponseHeaders(status, writable);
        byte[] dataFrame = entity.length > 0 ? Http3Protocol.encodeDataFrame(entity) : null;
        BufferData output = BufferData.create(headersFrame.length + (dataFrame == null ? 0 : dataFrame.length));
        output.write(headersFrame);
        if (entity.length > 0) {
            output.write(dataFrame);
        }
        return output.readBytes();
    }

    private static Http3Handler.BufferedResponse rawResponse(int status, Headers headers, byte[] body) {
        return Http3Handler.BufferedResponse.create(status, headers, body);
    }

    private static Http3Handler.BufferedResponse rawText(int status, String body) {
        return Http3Handler.BufferedResponse.text(status, body);
    }

    private static CompletableFuture<Integer> firstByte(InputStream inputStream) {
        CompletableFuture<Integer> firstByte = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try {
                firstByte.complete(inputStream.read());
            } catch (Throwable t) {
                firstByte.completeExceptionally(t);
            }
        });
        return firstByte;
    }

    private static boolean bodyArrivedBefore(CompletableFuture<Integer> firstByte, long timeoutMillis) {
        try {
            return firstByte.get(timeoutMillis, TimeUnit.MILLISECONDS) >= 0;
        } catch (TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the HTTP/3 request body", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed while waiting for the HTTP/3 request body", e.getCause());
        }
    }

    private static byte[] readEntity(InputStream inputStream, CompletableFuture<Integer> firstByte) {
        try {
            int first = firstByte.get(5, TimeUnit.SECONDS);
            byte[] remaining = inputStream.readAllBytes();
            if (first < 0) {
                return remaining;
            }
            byte[] entity = new byte[remaining.length + 1];
            entity[0] = (byte) first;
            System.arraycopy(remaining, 0, entity, 1, remaining.length);
            return entity;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading the HTTP/3 request body", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed while reading the HTTP/3 request body", e);
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

    private static byte[] rawHeadersFrame(List<Header> headers) {
        BufferData payload = BufferData.growing(256);
        writePrefixedInteger(payload, 8, 0, 0);
        writePrefixedInteger(payload, 7, 0, 0);
        for (Header header : headers) {
            for (String value : header.allValues()) {
                writeString(payload, 3, 0b0010_0000, header.headerName().lowerCase());
                writeString(payload, 7, 0, value);
            }
        }
        BufferData output = BufferData.create(VariableLengthEncoder.encodedSize(Http3Protocol.FRAME_HEADERS)
                                                     + VariableLengthEncoder.encodedSize(payload.available())
                                                     + payload.available());
        VariableLengthEncoder.encode(output, Http3Protocol.FRAME_HEADERS);
        VariableLengthEncoder.encode(output, payload.available());
        output.write(payload);
        return output.readBytes();
    }

    private static byte[] rawRequestHeadersFrame(String path) {
        return rawRequestHeadersFrame("GET", path);
    }

    private static byte[] rawRequestHeadersFrame(String method, String path) {
        return rawHeadersFrame(List.of(
                HeaderValues.create(HeaderNames.createFromLowercase(":method"), method),
                HeaderValues.create(HeaderNames.createFromLowercase(":scheme"), "https"),
                HeaderValues.create(HeaderNames.createFromLowercase(":authority"), "localhost"),
                HeaderValues.create(HeaderNames.createFromLowercase(":path"), path)));
    }

    private static byte[] rawConnectHeadersFrame(String authority) {
        return rawHeadersFrame(List.of(
                HeaderValues.create(HeaderNames.createFromLowercase(":method"), "CONNECT"),
                HeaderValues.create(HeaderNames.createFromLowercase(":authority"), authority)));
    }

    private static byte[] requestHeadersFrame(byte[] payload) {
        BufferData output = BufferData.create(VariableLengthEncoder.encodedSize(Http3Protocol.FRAME_HEADERS)
                                                     + VariableLengthEncoder.encodedSize(payload.length)
                                                     + payload.length);
        VariableLengthEncoder.encode(output, Http3Protocol.FRAME_HEADERS);
        VariableLengthEncoder.encode(output, payload.length);
        output.write(payload);
        return output.readBytes();
    }

    private static byte[] invalidStaticIndexHeadersPayload() {
        BufferData payload = BufferData.growing(16);
        writePrefixedInteger(payload, 8, 0, 0);
        writePrefixedInteger(payload, 7, 0, 0);
        payload.write(qpackIndexedStaticFieldLine(200));
        return payload.readBytes();
    }

    private static byte[] qpackIndexedStaticFieldLine(long index) {
        BufferData output = BufferData.growing(16);
        writePrefixedInteger(output, 6, 0b1100_0000, index);
        return output.readBytes();
    }

    private static byte[] requestCancelPushFrame() {
        BufferData output = BufferData.create(VariableLengthEncoder.encodedSize(Http3Protocol.FRAME_CANCEL_PUSH)
                                                     + VariableLengthEncoder.encodedSize(0));
        VariableLengthEncoder.encode(output, Http3Protocol.FRAME_CANCEL_PUSH);
        VariableLengthEncoder.encode(output, 0);
        return output.readBytes();
    }

    private static byte[] malformedControlStream() {
        byte[] goAway = Http3Protocol.goAwayFrame(Http3GoAway.pushId(0));
        BufferData output = BufferData.create(VariableLengthEncoder.encodedSize(Http3StreamType.CONTROL.code())
                                                     + goAway.length);
        VariableLengthEncoder.encode(output, Http3StreamType.CONTROL.code());
        output.write(goAway);
        return output.readBytes();
    }

    private static byte[] duplicateSettingsControlStream() {
        BufferData payload = BufferData.growing(16);
        writeSetting(payload, 0x06, 4_096);
        writeSetting(payload, 0x06, 8_192);
        return controlStreamWithSettingsPayload(payload);
    }

    private static byte[] reservedHttp2SettingControlStream() {
        BufferData payload = BufferData.growing(16);
        writeSetting(payload, 0x02, 1);
        return controlStreamWithSettingsPayload(payload);
    }

    private static byte[] controlStreamWithSettingsPayload(BufferData payload) {
        byte[] payloadBytes = payload.readBytes();
        BufferData output = BufferData.create(VariableLengthEncoder.encodedSize(Http3StreamType.CONTROL.code())
                                                      + VariableLengthEncoder.encodedSize(Http3Protocol.FRAME_SETTINGS)
                                                      + VariableLengthEncoder.encodedSize(payloadBytes.length)
                                                      + payloadBytes.length);
        VariableLengthEncoder.encode(output, Http3StreamType.CONTROL.code());
        VariableLengthEncoder.encode(output, Http3Protocol.FRAME_SETTINGS);
        VariableLengthEncoder.encode(output, payloadBytes.length);
        output.write(payloadBytes);
        return output.readBytes();
    }

    private static void writeSetting(BufferData output, long id, long value) {
        VariableLengthEncoder.encode(output, id);
        VariableLengthEncoder.encode(output, value);
    }

    private static void writeString(BufferData output,
                                    int prefixBits,
                                    int leadingBits,
                                    String value) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        writePrefixedInteger(output, prefixBits, leadingBits, bytes.length);
        output.write(bytes);
    }

    private static void writePrefixedInteger(BufferData output,
                                             int prefixBits,
                                             int leadingBits,
                                             long value) {
        int mask = (1 << prefixBits) - 1;
        if (value < mask) {
            output.write(leadingBits | (int) value);
            return;
        }
        output.write(leadingBits | mask);
        long remaining = value - mask;
        while (remaining >= 128) {
            output.write((int) ((remaining & 0x7f) | 0x80));
            remaining >>>= 7;
        }
        output.write((int) remaining);
    }

    private static final class LowLevelHttp3Client implements AutoCloseable {
        private static final Duration STREAM_OPEN_TIMEOUT = Duration.ofSeconds(5);
        private static final long LOCAL_QPACK_MAX_TABLE_CAPACITY = 4096;
        private static final int LOCAL_QPACK_BLOCKED_STREAMS = 16;

        private final ExecutorService executor;
        private final QuicClientRuntime client;
        private final QuicClientConnection connection;
        private final Http3QpackContext qpackContext;
        private final QuicStreamWriter controlWriter;

        private LowLevelHttp3Client(ExecutorService executor,
                                    QuicClientRuntime client,
                                    QuicClientConnection connection,
                                    Http3QpackContext qpackContext,
                                    QuicStreamWriter controlWriter) {
            this.executor = executor;
            this.client = client;
            this.connection = connection;
            this.qpackContext = qpackContext;
            this.controlWriter = controlWriter;
        }

        private static LowLevelHttp3Client create(TestEnvironment environment) throws Exception {
            return create(environment,
                          QuicConfig.builder()
                                  .availableVersions(List.of(QuicVersion.QUIC_V1))
                                  .buildPrototype(),
                          true);
        }

        private static LowLevelHttp3Client createUnprimed(TestEnvironment environment) throws Exception {
            return create(environment,
                          QuicConfig.builder()
                                  .availableVersions(List.of(QuicVersion.QUIC_V1))
                                  .buildPrototype(),
                          false);
        }

        private static LowLevelHttp3Client create(TestEnvironment environment,
                                                  QuicConfig quicConfig) throws Exception {
            return create(environment, quicConfig, true);
        }

        private static LowLevelHttp3Client create(TestEnvironment environment,
                                                  QuicConfig quicConfig,
                                                  boolean primeCriticalStreams) throws Exception {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            QuicClientRuntime client = QuicClientRuntime.builder()
                    .executor(executor)
                    .quicConfig(quicConfig)
                    .tls(environment.clientTls())
                    .build();
            Http3QpackContext qpackContext = createQpackContext();
            QuicClientConnection connection = createConnection(client, environment.securePort, qpackContext);
            try {
                connection.startHandshake().get(20, TimeUnit.SECONDS);
                QuicStreamWriter controlWriter = primeCriticalStreams
                        ? primeControlStreams(connection, qpackContext)
                        : null;
                return new LowLevelHttp3Client(executor, client, connection, qpackContext, controlWriter);
            } catch (Exception | Error failure) {
                try (LowLevelHttp3Client _ = new LowLevelHttp3Client(executor, client, connection, qpackContext, null)) {
                    throw failure;
                }
            }
        }

        private static Http3QpackContext createQpackContext() {
            return Http3QpackContext.create(LOCAL_QPACK_MAX_TABLE_CAPACITY,
                                            LOCAL_QPACK_BLOCKED_STREAMS,
                                            16_384,
                                            _ -> {
                                            });
        }

        private static QuicClientConnection createConnection(QuicClientRuntime client,
                                                               int securePort,
                                                               Http3QpackContext qpackContext) {
            InetSocketAddress peerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), securePort);
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
                                                          }
                                                      })
                            .completion().exceptionally(throwable -> null);
                    return true;
                }
                return false;
            });
            return connection;
        }

        private static QuicStreamWriter primeControlStreams(QuicConnection connection,
                                                            Http3QpackContext qpackContext) throws Exception {
            QuicStreamWriter controlWriter = openAndPrimeUniStream(
                    connection,
                    Http3Protocol.controlStreamPreamble(Http3Settings.create(LOCAL_QPACK_MAX_TABLE_CAPACITY,
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
            return controlWriter;
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

        private byte[] encodeRequestHeaders(RequestStream requestStream, URI uri, String method, Headers headers) {
            return Http3WebServerIT.encodeRequestHeaders(qpackContext, requestStream.stream().streamId(), uri, method, headers);
        }

        private Http3MessageReader responseReader(RequestStream requestStream, Method requestMethod) {
            return responseReader(requestStream, requestMethod, Http3FrameListener.create(List.of()));
        }

        private Http3MessageReader responseReader(RequestStream requestStream,
                                                  Method requestMethod,
                                                  Http3FrameListener frameListener) {
            return Http3MessageReader.response(requestStream.stream(),
                                               qpackContext,
                                               connection,
                                               requestMethod,
                                               16_384,
                                               frameListener);
        }

        private DecodedResponseHead decodeResponseHead(RequestStream requestStream, byte[] headersPayload) {
            return Http3WebServerIT.decodeResponseHead(qpackContext, requestStream.stream().streamId(), headersPayload);
        }

        private DecodedResponse decodeResponse(RequestStream requestStream, byte[] bytes) {
            return Http3WebServerIT.decodeResponse(qpackContext, requestStream.stream().streamId(), bytes);
        }

        private void sendHeaders(RequestStream requestStream, URI uri, int contentLength) {
            sendHeaders(requestStream, uri, contentLength, headers());
        }

        private void sendHeaders(RequestStream requestStream,
                                 URI uri,
                                 int contentLength,
                                 Headers additionalHeaders) {
            WritableHeaders<?> requestHeaders = WritableHeaders.create();
            requestHeaders.add(HeaderValues.create(HeaderNames.CONTENT_LENGTH, contentLength));
            requestHeaders.add(HeaderValues.create(HeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8"));
            additionalHeaders.forEach(requestHeaders::add);
            requestStream.writer()
                    .scheduleForWriting(BufferData.create(encodeRequestHeaders(requestStream, uri, "POST", requestHeaders)),
                                        false);
        }

        private void sendData(RequestStream requestStream, byte[] data, boolean last) {
            requestStream.writer().scheduleForWriting(BufferData.create(Http3Protocol.encodeDataFrame(data)), last);
        }

        private void sendTrailers(RequestStream requestStream, Headers trailers, boolean last) {
            requestStream.writer()
                    .scheduleForWriting(BufferData.create(Http3Protocol.encodeHeadersFrame(qpackContext,
                                                                                         requestStream.stream().streamId(),
                                                                                         trailers)),
                                        last);
        }

        @Override
        public void close() throws Exception {
            try {
                qpackContext.close(new IllegalStateException("HTTP/3 test client closed"));
            } finally {
                try {
                    client.close();
                } finally {
                    executor.close();
                }
            }
        }

        private record RequestStream(QuicBidiStream stream, QuicStreamWriter writer) {
        }
    }

    private record DecodedResponse(int status,
                                   Headers headers,
                                   int headersPayloadLength,
                                   byte[] body,
                                   Headers trailers) {
        private DecodedResponse {
            headers = WritableHeaders.create(headers);
            trailers = WritableHeaders.create(trailers);
        }
    }

    private record RequestTarget(String method, String target) {
    }

    private record DecodedResponseHead(int status, Headers headers) {
        private DecodedResponseHead {
            headers = WritableHeaders.create(headers);
        }
    }

    private static final class TestEnvironment implements AutoCloseable {
        private final ExecutorService executor;
        private final SSLContext clientSslContext;
        private final WebServer server;
        private final int securePort;
        private final int plainPort;

        private TestEnvironment(ExecutorService executor,
                                SSLContext clientSslContext,
                                WebServer server,
                                int securePort,
                                int plainPort) {
            this.executor = executor;
            this.clientSslContext = clientSslContext;
            this.server = server;
            this.securePort = securePort;
            this.plainPort = plainPort;
        }

        private static TestEnvironment create() throws Exception {
            return create(routing -> routing
                    .get("/hello", (req, res) -> res.send(req.prologue().method().text()
                                                                  + " "
                                                                  + req.prologue().uriPath().rawPath()))
                    .post("/echo", (req, res) -> res.send(req.content().as(String.class))));
        }

        private static TestEnvironment create(Consumer<HttpRouting.Builder> routingConsumer) throws Exception {
            return create(Http3Config.create(), routingConsumer);
        }

        private static TestEnvironment create(Http3Config http3Config,
                                              Consumer<HttpRouting.Builder> routingConsumer) throws Exception {
            return create(http3Config, -1, routingConsumer);
        }

        private static TestEnvironment create(Http3Config http3Config,
                                              int maxConcurrentRequests,
                                              Consumer<HttpRouting.Builder> routingConsumer) throws Exception {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            WebServerConfig.Builder builder = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(0)
                    .featuresDiscoverServices(false)
                    .protocolsDiscoverServices(false)
                    .tls(serverTls())
                    .addProtocol(http3Config)
                    .routing(routingConsumer);
            if (maxConcurrentRequests >= 0) {
                builder.maxConcurrentRequests(maxConcurrentRequests);
            }
            WebServer server = builder.build()
                    .start();

            return new TestEnvironment(executor, Http3WebServerIT.clientSslContext(), server, server.port(), -1);
        }

        private static TestEnvironment createSharedListener() throws Exception {
            return createSharedListener(Http1Config.create(), Http3Config.create());
        }

        private static TestEnvironment createSharedListener(InetAddress address) throws Exception {
            return createSharedListener(address, Http1Config.create(), Http3Config.create());
        }

        private static TestEnvironment createSharedListenerWithAltSvc() throws Exception {
            return createSharedListener(Http1Config.builder()
                                                    .altSvc(AltSvc.builder().build())
                                                    .build(),
                                        Http3Config.create());
        }

        private static TestEnvironment createSharedListenerWithMetrics() throws Exception {
            return createSharedListener(InetAddress.getLoopbackAddress(), Http1Config.create(), Http3Config.create(), true);
        }

        private static TestEnvironment createSharedListener(Http1Config http1Config) throws Exception {
            return createSharedListener(http1Config, Http3Config.create());
        }

        private static TestEnvironment createSharedListener(Http1Config http1Config, Http3Config http3Config)
                throws Exception {
            return createSharedListener(InetAddress.getLoopbackAddress(), http1Config, http3Config, false);
        }

        private static TestEnvironment createSharedListener(InetAddress address, Http1Config http1Config) throws Exception {
            return createSharedListener(address, http1Config, Http3Config.create(), false);
        }

        private static TestEnvironment createSharedListener(InetAddress address,
                                                            Http1Config http1Config,
                                                            Http3Config http3Config) throws Exception {
            return createSharedListener(address, http1Config, http3Config, false);
        }

        private static TestEnvironment createSharedListener(InetAddress address,
                                                            Http1Config http1Config,
                                                            boolean metricsEnabled) throws Exception {
            return createSharedListener(address, http1Config, Http3Config.create(), metricsEnabled);
        }

        private static TestEnvironment createSharedListener(InetAddress address,
                                                            Http1Config http1Config,
                                                            Http3Config http3Config,
                                                            boolean metricsEnabled) throws Exception {
            return createSharedListener(address, http1Config, http3Config, metricsEnabled, null);
        }

        private static TestEnvironment createSharedListener(InetAddress address,
                                                            Http1Config http1Config,
                                                            Http3Config http3Config,
                                                            boolean metricsEnabled,
                                                            MeterRegistry meterRegistry) throws Exception {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            var listenerBuilder = ListenerConfig.builder()
                    .address(address)
                    .port(0)
                    .bindingsDiscoverServices(false);
            QuicTransportConfig.create().addTo(listenerBuilder);

            var serverBuilder = WebServer.builder()
                    .from(listenerBuilder)
                    .addBinding(TcpTransportConfig.create())
                    .featuresDiscoverServices(false)
                    .protocolsDiscoverServices(false)
                    .tls(serverTls())
                    .addProtocol(http1Config)
                    .addProtocol(http3Config)
                    .routing(routing -> routing
                            .get("/hello", (req, res) -> res.send("shared"))
                            .get("/socket-id", (req, res) -> res.send(req.socketId()))
                            .route(HttpRoute.builder()
                                           .methods(Method.GET)
                                           .path("/shared-first")
                                           .handler((req, res) -> res.send("shared-first"))
                                           .build())
                            .route(Http3Route.route(Method.GET,
                                                    "/shared-first",
                                                    (req, res) -> res.send("http3-after-shared")))
                            .route(Http3Route.route(Method.GET,
                                                    "/http3-first",
                                                    (req, res) -> res.send("http3-first")))
                            .route(HttpRoute.builder()
                                           .methods(Method.GET)
                                           .path("/http3-first")
                                           .handler((req, res) -> res.send("shared-after-http3"))
                                           .build()));
            if (metricsEnabled) {
                serverBuilder.addFeature(ObserveFeature.just(meterRegistry == null
                                                                     ? MetricsObserver.create()
                                                                     : MetricsObserver.builder()
                                                                             .meterRegistry(meterRegistry)
                                                                             .build()));
            }

            WebServer server = serverBuilder.build().start();

            return new TestEnvironment(executor, Http3WebServerIT.clientSslContext(), server, server.port(), -1);
        }

        private static TestEnvironment createNamedSharedListener() throws Exception {
            return createNamedSharedListener(Http1Config.create());
        }

        private static TestEnvironment createNamedSharedListenerWithAltSvc() throws Exception {
            return createNamedSharedListener(Http1Config.builder()
                                                       .altSvc(AltSvc.builder().build())
                                                       .build());
        }

        private static TestEnvironment createNamedSharedListener(Http1Config http1Config) throws Exception {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            Tls serverTls = serverTls();

            WebServer server = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(0)
                    .featuresDiscoverServices(false)
                    .protocolsDiscoverServices(false)
                    .putSocket("shared", listener -> listener
                            .protocolsDiscoverServices(false)
                            .port(0)
                            .tls(serverTls)
                            .addProtocol(http1Config)
                            .addProtocol(Http3Config.builder()
                                                 .name("shared")
                                                 .buildPrototype())
                            .routing(routing -> routing.get("/hello", (req, res) -> res.send("named-shared"))))
                    .build()
                    .start();

            return new TestEnvironment(executor, Http3WebServerIT.clientSslContext(), server, server.port("shared"), -1);
        }

        private static TestEnvironment createSecureHttp1Only() throws Exception {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            WebServer server = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(0)
                    .featuresDiscoverServices(false)
                    .protocolsDiscoverServices(false)
                    .tls(serverTls())
                    .addProtocol(Http1Config.create())
                    .routing(routing -> routing
                            .get("/hello", (req, res) -> res.send(req.prologue().method().text()
                                                                          + " "
                                                                          + req.prologue().uriPath().rawPath()))
                            .post("/echo", (req, res) -> res.send(req.content().as(String.class))))
                    .build()
                    .start();

            return new TestEnvironment(executor, Http3WebServerIT.clientSslContext(), server, server.port(), -1);
        }

        private static TestEnvironment createPlainAndSecure() throws Exception {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            WebServer server = WebServer.builder()
                    .address(InetAddress.getLoopbackAddress())
                    .port(0)
                    .featuresDiscoverServices(false)
                    .protocolsDiscoverServices(false)
                    .tls(serverTls())
                    .addProtocol(Http1Config.create())
                    .addProtocol(Http3Config.create())
                    .routing(routing -> routing
                            .get("/hello", (req, res) -> res.send(req.prologue().method().text()
                                                                          + " "
                                                                          + req.prologue().uriPath().rawPath()))
                            .post("/echo", (req, res) -> res.send(req.content().as(String.class))))
                    .putSocket("plain", listener -> listener
                            .protocolsDiscoverServices(false)
                            .port(0)
                            .addProtocol(Http1Config.create())
                            .routing(routing -> routing
                                    .get("/hello", (req, res) -> res.send(req.prologue().method().text()
                                                                                  + " "
                                                                                  + req.prologue().uriPath().rawPath()))
                                    .post("/echo", (req, res) -> res.send(req.content().as(String.class)))))
                    .build()
                    .start();

            return new TestEnvironment(executor,
                                       Http3WebServerIT.clientSslContext(),
                                       server,
                                       server.port(),
                                       server.port("plain"));
        }

        private ExecutorService executor() {
            return executor;
        }

        private SSLContext clientSslContext() {
            return clientSslContext;
        }

        private Tls clientTls() {
            return Http3WebServerIT.clientTls();
        }

        private Tls clientTlsNoAlpn() {
            return Http3WebServerIT.clientTlsNoAlpn();
        }

        private URI uri(String path) throws Exception {
            return new URI("https", null, "localhost", securePort, path, null, null);
        }

        private URI uri(String path, String query) throws Exception {
            return new URI("https", null, "localhost", securePort, path, query, null);
        }

        private URI httpUri(String path) throws Exception {
            if (plainPort < 0) {
                throw new IllegalStateException("Plaintext listener is not configured.");
            }
            return new URI("http", null, "localhost", plainPort, path, null, null);
        }

        @Override
        public void close() {
            try {
                server.stop();
            } finally {
                executor.close();
            }
        }
    }

    private static final class RawTestEnvironment implements AutoCloseable {
        private final ExecutorService executor;
        private final SSLContext clientSslContext;
        private final RawHttp3TestServer server;
        private final int securePort;

        private RawTestEnvironment(ExecutorService executor,
                                   SSLContext clientSslContext,
                                   RawHttp3TestServer server,
                                   int securePort) {
            this.executor = executor;
            this.clientSslContext = clientSslContext;
            this.server = server;
            this.securePort = securePort;
        }

        private static RawTestEnvironment create(Http3Handler handler) throws Exception {
            return create((_, stream) -> handler.handle(stream));
        }

        private static RawTestEnvironment create(RawHttp3TestServer.ConnectionHandler handler) throws Exception {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            RawHttp3TestServer server = RawHttp3TestServer.create(executor,
                                                                  new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                                                                  serverTls(),
                                                                  List.of(QuicVersion.QUIC_V1),
                                                                  handler);

            return new RawTestEnvironment(executor,
                                          clientSslContext(),
                                          server,
                                          server.localAddress().getPort());
        }

        private Tls clientTls() {
            return Http3WebServerIT.clientTls();
        }

        private URI uri(String path) throws Exception {
            return new URI("https", null, "localhost", securePort, path, null, null);
        }

        @Override
        public void close() {
            try {
                server.close();
            } finally {
                executor.close();
            }
        }
    }

    private static Headers headers(Header... headers) {
        WritableHeaders<?> writable = WritableHeaders.create();
        for (Header header : headers) {
            writable.add(header);
        }
        return writable;
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

    private static Tls clientTlsNoAlpn() {
        return Tls.builder()
                .trust(trust -> trust.keystore(store -> store
                        .passphrase(new String(KEY_PASSWORD))
                        .trustStore(true)
                        .keystore(Resource.create(CLIENT_TRUSTSTORE))))
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
        try (InputStream stream = Http3WebServerIT.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource: " + resourceName);
            }
            keyStore.load(stream, KEY_PASSWORD);
        }
        return keyStore;
    }
}
